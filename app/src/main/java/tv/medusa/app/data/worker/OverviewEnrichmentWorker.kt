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

        return try {
            enrich(serverUrl = serverUrl, userId = userId, token = token, tmdbApiKey = prefs.tmdbApiKey)
            Result.success()
        } catch (e: Exception) {
            // Best-effort: no se reintenta (equivalente al comportamiento previo en memoria).
            Log.w("OverviewEnrichmentWorker", "Enriquecido interrumpido: ${e.message}")
            Result.success()
        }
    }

    private suspend fun enrich(serverUrl: String, userId: String, token: String, tmdbApiKey: String) {
        val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
        val authHeader = buildJellyfinAuthHeader(token = token)

        // Overviews: Jellyfin (LAN). Se piden olas de batchSize*concurrency ids y se lanzan varios
        // lotes en paralelo (antes: secuencial con delay(1200ms) fijo entre lotes).
        //
        // Con la app abierta se va más despacio (menos peticiones en paralelo y más pausa entre olas):
        // el enriquecido compite con las consultas que el usuario está esperando y el servidor doméstico
        // suele ir justo. En segundo plano recupera el ritmo alto.
        val overviewBatchSize = 40
        val processed = AtomicInteger(0)
        var processedCount = 0
        while (processedCount < 10000) {
            val yielding = AppForeground.isVisible
            val concurrency = if (yielding) CONCURRENCY_FOREGROUND else CONCURRENCY_BACKGROUND
            val missingIds = jellyfinDao.getIdsMissingOverview(limit = overviewBatchSize * concurrency)
            if (missingIds.isEmpty()) break

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
                    processed.addAndGet(batch.size)
                }
            }

            val advanced = processed.get()
            // Sin progreso (servidor sin respuesta) se corta; evita esperas inútiles.
            if (advanced == processedCount) break
            processedCount = advanced
            if (processedCount < 10000) delay(if (yielding) WAVE_PAUSE_FOREGROUND_MS else WAVE_PAUSE_BACKGROUND_MS)
        }

        // Pósteres faltantes vía TMDB: peticiones independientes → varias en paralelo (mismo criterio
        // que arriba: con la app delante, menos).
        val tmdbApi = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
        val tmdbKey = tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY

        val tmdbConcurrency = if (AppForeground.isVisible) CONCURRENCY_FOREGROUND else CONCURRENCY_BACKGROUND
        val missingPosters = jellyfinDao.getItemsMissingPoster(limit = 40)
        missingPosters.forEachBounded(tmdbConcurrency) { media ->
            val tmdbLong = media.tmdbId?.toLongOrNull() ?: return@forEachBounded
            val isTv = media.type.equals("Series", ignoreCase = true)
            val posterPath: String? = runCatching {
                if (isTv) {
                    tmdbApi.getTvDetails(seriesId = tmdbLong, apiKey = tmdbKey).posterPath
                } else {
                    tmdbApi.getMovieDetails(movieId = tmdbLong, apiKey = tmdbKey).posterPath
                }
            }.getOrNull()

            if (!posterPath.isNullOrBlank()) {
                jellyfinDao.updatePrimaryImageTag(media.id, "tmdb:$posterPath")
            }
        }
    }
}
