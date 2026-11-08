package tv.medusa.app.data.worker

import android.util.Log
import tv.medusa.app.data.remote.ApiConfig
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import tv.medusa.app.data.local.DEFAULT_TMDB_API_KEY
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.JellyfinDao
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.jellyfin.JellyfinApiService
import tv.medusa.app.data.remote.tmdb.TmdbApiService
import tv.medusa.app.data.repository.buildJellyfinAuthHeader
import tv.medusa.app.util.AppForeground
import tv.medusa.app.util.forEachBounded
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enriquecido en segundo plano de metadatos que Jellyfin no entrega en el sync ligero:
 * `overview` + `primaryImageTag` de los ítems que aún no los tienen, y pósteres vía TMDB.
 *
 * Se ejecuta en `WorkManager` (con constraint de red) para no competir con el reproductor y
 * sobrevivir a la muerte del proceso. La lógica proviene de `MediaSyncRepository` (§2.3 de
 * MEMORIA_ANALISIS_RENDIMIENTO.md).
 */
@HiltWorker
class OverviewEnrichmentWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val jellyfinDao: JellyfinDao
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "OverviewEnrichmentWork"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USER_ID = "user_id"
        const val KEY_TOKEN = "token"

        /**
         * Peticiones simultáneas al servidor y pausa entre olas. Con la app delante se baja el ritmo
         * (la mitad en paralelo y pausa más larga) para no competir con lo que el usuario está
         * esperando; en segundo plano se usa el ritmo alto.
         */
        private const val CONCURRENCY_FOREGROUND = 2
        private const val CONCURRENCY_BACKGROUND = 2
        private const val WAVE_PAUSE_FOREGROUND_MS = 3000L
        private const val WAVE_PAUSE_BACKGROUND_MS = 1500L
    }

    override suspend fun doWork(): Result {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val serverUrl = inputData.getString(KEY_SERVER_URL)?.ifBlank { null } ?: prefs.jellyfinServerUrl
        val userId = inputData.getString(KEY_USER_ID)?.ifBlank { null } ?: prefs.jellyfinUserId
        val token = inputData.getString(KEY_TOKEN)?.ifBlank { null } ?: prefs.jellyfinAccessToken

        if (serverUrl.isBlank() || userId.isBlank() || token.isBlank()) {
            return Result.success()
        }

        if (AppForeground.isPlaybackActive) {
            Log.d("OverviewEnrichmentWorker", "Reproducción activa en primer plano: pausando enriquecido")
            return Result.success()
        }

        Log.i("OverviewEnrichmentWorker", "OverviewEnrichmentWorker STARTING for serverUrl=$serverUrl userId=$userId")

        return try {
            enrich(serverUrl = serverUrl, userId = userId, token = token, tmdbApiKey = prefs.tmdbApiKey)
            Log.i("OverviewEnrichmentWorker", "OverviewEnrichmentWorker FINISHED successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.w("OverviewEnrichmentWorker", "Enriquecido interrumpido: ${e.message}", e)
            Result.success()
        }
    }

    private suspend fun enrich(serverUrl: String, userId: String, token: String, tmdbApiKey: String) {
        val tmdbApi = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
        val tmdbKey = tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY

        // FASE 1: Completar pósteres faltantes vía TMDB (Ultra-rápido)
        var totalPostersEnriched = 0
        while (totalPostersEnriched < 5000) {
            if (AppForeground.isPlaybackActive) break
            val yielding = AppForeground.isVisible
            val tmdbConcurrency = if (yielding) 4 else 8
            val missingPosters = jellyfinDao.getItemsMissingPoster(limit = 40)
            if (missingPosters.isEmpty()) break

            Log.i("OverviewEnrichmentWorker", "FASE 1 (Pósteres TMDB): Procesando lote de ${missingPosters.size} ítems sin póster (Total: $totalPostersEnriched)")

            var batchUpdated = 0
            missingPosters.forEachBounded(tmdbConcurrency) { media ->
                val tmdbLong = media.tmdbId?.toLongOrNull()
                val posterPath: String? = if (tmdbLong != null && tmdbLong > 0) {
                    val isTv = media.type.equals("Series", ignoreCase = true)
                    runCatching {
                        if (isTv) {
                            tmdbApi.getTvDetails(seriesId = tmdbLong, apiKey = tmdbKey).posterPath
                        } else {
                            tmdbApi.getMovieDetails(movieId = tmdbLong, apiKey = tmdbKey).posterPath
                        }
                    }.getOrNull()
                } else null

                if (!posterPath.isNullOrBlank()) {
                    jellyfinDao.updatePrimaryImageTag(media.id, "tmdb:$posterPath")
                } else {
                    jellyfinDao.updatePrimaryImageTag(media.id, "tmdb:none")
                }
                batchUpdated++
            }
            totalPostersEnriched += batchUpdated
            if (batchUpdated == 0) break
            delay(if (yielding) 200L else 50L)
        }

        // FASE 2: Completar descripciones faltantes en español vía TMDB
        var totalOverviewsEnriched = 0
        while (totalOverviewsEnriched < 5000) {
            if (AppForeground.isPlaybackActive) break
            val yielding = AppForeground.isVisible
            val tmdbConcurrency = if (yielding) 4 else 8
            val missingOverviews = jellyfinDao.getItemsMissingOverview(limit = 40)
            if (missingOverviews.isEmpty()) break

            Log.i("OverviewEnrichmentWorker", "FASE 2 (Descripciones TMDB): Procesando lote de ${missingOverviews.size} ítems sin sinopsis (Total: $totalOverviewsEnriched)")

            var batchUpdated = 0
            missingOverviews.forEachBounded(tmdbConcurrency) { media ->
                val tmdbLong = media.tmdbId?.toLongOrNull()
                val tmdbOverview: String? = if (tmdbLong != null && tmdbLong > 0) {
                    val isTv = media.type.equals("Series", ignoreCase = true)
                    runCatching {
                        if (isTv) {
                            tmdbApi.getTvDetails(seriesId = tmdbLong, apiKey = tmdbKey, language = "es-ES").overview
                        } else {
                            tmdbApi.getMovieDetails(movieId = tmdbLong, apiKey = tmdbKey, language = "es-ES").overview
                        }
                    }.getOrNull()
                } else null

                if (!tmdbOverview.isNullOrBlank()) {
                    jellyfinDao.updateOverview(media.id, tmdbOverview)
                } else {
                    jellyfinDao.updateOverview(media.id, "Sin descripción disponible.")
                }
                batchUpdated++
            }
            totalOverviewsEnriched += batchUpdated
            if (batchUpdated == 0) break
            delay(if (yielding) 200L else 50L)
        }

        // FASE 3: Obtener descripciones y portadas primarias de Jellyfin (LAN)
        val api = NetworkClientFactory.createJellyfinService(serverUrl, JellyfinApiService::class.java)
        val authHeader = buildJellyfinAuthHeader(token = token)
        val overviewBatchSize = 40
        val missingIds = jellyfinDao.getIdsMissingOverview(limit = 120)
        if (missingIds.isNotEmpty()) {
            val concurrency = if (AppForeground.isVisible) CONCURRENCY_FOREGROUND else CONCURRENCY_BACKGROUND
            missingIds.chunked(overviewBatchSize).forEachBounded(concurrency) { batch ->
                val idsParam = batch.joinToString(",")
                val response = runCatching {
                    api.getLibraryItems(
                        authHeader = authHeader,
                        userId = userId,
                        ids = idsParam,
                        fields = "Overview,PrimaryImageTag",
                        limit = overviewBatchSize,
                        enableTotalRecordCount = false
                    )
                }.getOrNull()

                if (response != null && response.items.isNotEmpty()) {
                    for (item in response.items) {
                        if (!item.overview.isNullOrBlank()) {
                            jellyfinDao.updateOverview(item.id, item.overview)
                        }
                        val posterTag = item.primaryImageTag ?: item.imageTags?.get("Primary")
                        if (!posterTag.isNullOrBlank()) {
                            jellyfinDao.updatePrimaryImageTag(item.id, posterTag)
                        }
                    }
                }
            }
        }
    }
}
