package com.example.tujelly.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.tujelly.data.local.UserPreferencesRepository
import com.example.tujelly.data.local.db.JellyfinDao
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import com.example.tujelly.data.local.db.TmdbVoteCacheDao
import com.example.tujelly.data.remote.NetworkClientFactory
import com.example.tujelly.data.remote.jellyfin.JellyfinApiService
import com.example.tujelly.data.remote.jellyfin.JellyfinItemsResponse
import com.example.tujelly.data.worker.JellyfinSyncWorker
import com.example.tujelly.data.worker.OverviewEnrichmentWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Orquestación de la sincronización del catálogo Jellyfin (quick init, delta sync,
 * sync completo con checkpoints) y del enriquecimiento en segundo plano.
 *
 * Extraído de `MediaRepository`. El progreso ([syncProgress]) es estático para que
 * cualquier ViewModel/pantalla pueda observarlo sin depender de una instancia.
 */
class MediaSyncRepository(
    private val jellyfinDao: JellyfinDao,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val tmdbVoteCacheDao: TmdbVoteCacheDao? = null,
    private val accessPolicy: MediaAccessPolicy = MediaAccessPolicy(userPreferencesRepository)
) {
    companion object {
        private val _syncProgress = MutableStateFlow(SyncProgress())
        val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
        private val syncMutex = Mutex()
        private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateProgress(progress: SyncProgress) {
            _syncProgress.value = progress
        }

        fun checkActiveWorkerSync(context: Context, localCount: Int = 0) {
            try {
                val workInfos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(JellyfinSyncWorker.WORK_NAME)
                    .get()
                val activeWork = workInfos.firstOrNull { !it.state.isFinished }
                if (activeWork != null && !_syncProgress.value.isSyncing) {
                    val grand = maxOf(1000, localCount)
                    _syncProgress.value = SyncProgress(
                        isSyncing = true,
                        current = localCount,
                        total = grand,
                        message = "Sincronizando en segundo plano..."
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun startBackgroundOverviewEnrichment(serverUrl: String, userId: String, token: String) {
        if (serverUrl.isBlank() || userId.isBlank() || token.isBlank()) return
        val context = userPreferencesRepository?.applicationContext ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<OverviewEnrichmentWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    OverviewEnrichmentWorker.KEY_SERVER_URL to serverUrl,
                    OverviewEnrichmentWorker.KEY_USER_ID to userId,
                    OverviewEnrichmentWorker.KEY_TOKEN to token
                )
            )
            .build()

        // Unique work con KEEP: si ya hay un enriquecido encolado o en curso, no se duplica.
        WorkManager.getInstance(context).enqueueUniqueWork(
            OverviewEnrichmentWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun startBackgroundSync(
        serverUrl: String,
        userId: String,
        token: String,
        lastSyncTimestamp: String? = null,
        forceFullSync: Boolean = false,
        context: Context? = null,
        onSyncCompleted: ((String) -> Unit)? = null
    ) {
        if (serverUrl.isBlank() || userId.isBlank() || token.isBlank()) return

        val targetContext = context ?: userPreferencesRepository?.applicationContext
        if (targetContext != null) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                JellyfinSyncWorker.KEY_SERVER_URL to serverUrl,
                JellyfinSyncWorker.KEY_USER_ID to userId,
                JellyfinSyncWorker.KEY_TOKEN to token,
                JellyfinSyncWorker.KEY_FORCE_FULL_SYNC to forceFullSync
            )

            val syncWorkRequest = OneTimeWorkRequestBuilder<JellyfinSyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(targetContext).enqueueUniqueWork(
                JellyfinSyncWorker.WORK_NAME,
                if (forceFullSync) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                syncWorkRequest
            )
        } else {
            if (_syncProgress.value.isSyncing || syncMutex.isLocked) return
            syncScope.launch {
                syncJellyfinLibrary(
                    serverUrl = serverUrl,
                    userId = userId,
                    token = token,
                    lastSyncTimestamp = lastSyncTimestamp,
                    forceFullSync = forceFullSync,
                    onSyncCompleted = { newTs ->
                        onSyncCompleted?.invoke(newTs)
                    }
                )
            }
        }
    }

    suspend fun syncQuickInit(serverUrl: String, userId: String, token: String): Result<Unit> {
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)

            var playedEntities: List<JellyfinMediaEntity> = emptyList()

            coroutineScope {
                // 1. Sync Continue Watching (Resume Items)
                launch {
                    try {
                        val resume = api.getResumeItems(authHeader = authHeader, userId = userId, limit = 30)
                        val resumeEntities = resume.items.map { it.toEntity() }
                        if (resumeEntities.isNotEmpty()) {
                            jellyfinDao.insertOrUpdateAll(resumeEntities)
                        }
                    } catch (_: Exception) {}
                }

                // 2. Sync Recent Items (Fast, immediately populates UI with up to 50 items)
                launch {
                    try {
                        val latest = api.getLatestItems(authHeader = authHeader, userId = userId, limit = 50)
                        val latestEntities = latest.map { it.toEntity() }
                        if (latestEntities.isNotEmpty()) {
                            jellyfinDao.insertOrUpdateAll(latestEntities)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MediaSyncRepository", "Error syncing latest items: ${e.message}", e)
                    }
                }

                // 3. Sync Favorites (Fast, critical)
                launch {
                    try {
                        val favResponse = api.getFavoriteItems(authHeader = authHeader, userId = userId)
                        val favEntities = favResponse.items.map { it.toEntity().copy(isFavorite = true) }
                        if (favEntities.isNotEmpty()) {
                            jellyfinDao.insertOrUpdateAll(favEntities)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MediaSyncRepository", "Error syncing favorites: ${e.message}", e)
                    }
                }

                // 4. Sync Recently Played items (Fast, marks watched/progress in local DB)
                launch {
                    try {
                        val playedResponse = api.getRecentlyPlayedItems(authHeader = authHeader, userId = userId, limit = 100)
                        playedEntities = playedResponse.items.map { it.toEntity() }
                        if (playedEntities.isNotEmpty()) {
                            jellyfinDao.insertOrUpdateAll(playedEntities)
                        }
                    } catch (_: Exception) {}
                }
            }

            // 5. Pre-cache parent series for any synced episodes so full series details exist in Room
            try {
                val seriesIds = playedEntities
                    .filter { it.type.equals("Episode", ignoreCase = true) && !it.seriesId.isNullOrBlank() }
                    .mapNotNull { it.seriesId }
                    .distinct()
                for (sId in seriesIds) {
                    try {
                        val seriesDto = api.getItemDetail(authHeader = authHeader, userId = userId, itemId = sId)
                        jellyfinDao.insertOrUpdate(seriesDto.toEntity())
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun syncJellyfinLibrary(
        serverUrl: String,
        userId: String,
        token: String,
        lastSyncTimestamp: String? = null,
        forceFullSync: Boolean = false,
        onSyncCompleted: (suspend (String) -> Unit)? = null
    ): Result<Unit> {
        if (serverUrl.isBlank() || userId.isBlank() || token.isBlank()) {
            return Result.success(Unit)
        }
        if (!syncMutex.tryLock()) {
            // Sincronización ya en curso: evitar duplicar peticiones y saturar el servidor
            return Result.success(Unit)
        }
        return try {
            runCatching {
                val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
                val authHeader = buildJellyfinAuthHeader(token = token)

                // Purga entradas caducadas (>24h) de la caché de votos TMDB para que no crezca sin límite.
                runCatching { tmdbVoteCacheDao?.deleteExpired() }

                val currentLocal = jellyfinDao.getCount()

                val effectiveLastSync = if (forceFullSync) null else {
                    lastSyncTimestamp?.ifBlank { null }
                        ?: userPreferencesRepository?.userPreferencesFlow?.firstOrNull()?.jellyfinLastSync?.ifBlank { null }
                        ?: if (currentLocal >= 500) {
                            val now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
                            userPreferencesRepository?.updateJellyfinLastSync(now)
                            now
                        } else null
                }

                // 1. Prioridad: refrescar de inmediato los elementos de portada (< 500ms)
                syncQuickInit(serverUrl, userId, token)

                // 2. Obtener vistas del usuario (Películas, Series, etc.)
                val userViews = runCatching {
                    api.getUserViews(authHeader = authHeader, userId = userId).items
                }.getOrDefault(emptyList())

                val mediaViews = userViews.filter { view ->
                    val cType = view.collectionType?.lowercase()
                    cType == "movies" || cType == "tvshows" || (cType == null && !view.name.orEmpty().contains("playlist", ignoreCase = true) && !view.name.orEmpty().contains("coleccion", ignoreCase = true))
                }

                // =========================================================================
                // RECONCILIACIÓN / FILTRADO DE RESTRICCIONES (forceFullSync o primera vez)
                // =========================================================================
                if (forceFullSync || accessPolicy.getAllowedIdsSet() == null) {
                    _syncProgress.value = SyncProgress(
                        isSyncing = true,
                        current = 0,
                        total = currentLocal,
                        message = "Comprobando restricciones parentales..."
                    )
                    runCatching {
                        // Reconciliación paginada: antes se pedían hasta 60.000 ítems de una vez
                        // (respuesta enorme en memoria). Ahora páginas de 500 IDs.
                        val allowedIds = HashSet<String>()
                        val pageLimit = 500
                        var startIndex = 0
                        while (true) {
                            val response = api.getLibraryItems(
                                authHeader = authHeader,
                                userId = userId,
                                includeItemTypes = "Movie,Series",
                                recursive = true,
                                fields = "",
                                limit = pageLimit,
                                startIndex = startIndex,
                                enableTotalRecordCount = false
                            )
                            val items = response.items
                            if (items.isEmpty()) break
                            items.forEach { allowedIds.add(it.id) }
                            if (items.size < pageLimit) break
                            startIndex += items.size
                            delay(50L)
                        }
                        if (allowedIds.isNotEmpty()) {
                            accessPolicy.saveAllowedIds(userId, allowedIds)
                        }
                    }
                    if (forceFullSync) {
                        userPreferencesRepository?.clearSyncCheckpoint()
                    }
                }

                // =========================================================================
                // CAMINO 1: SINCRONIZACIÓN INCREMENTAL (DELTA SYNC)
                // =========================================================================
                // Si el catálogo local ya tiene elementos y una fecha de sincronización previa completa
                if (!forceFullSync && currentLocal > 0 && !effectiveLastSync.isNullOrBlank()) {
                    _syncProgress.value = SyncProgress(
                        isSyncing = true,
                        current = currentLocal,
                        total = currentLocal,
                        message = "Buscando novedades en Jellyfin..."
                    )

                    var deltaCount = 0
                    for (view in mediaViews.ifEmpty { listOf(null) }) {
                        val isSeries = view?.collectionType.equals("tvshows", ignoreCase = true) || view?.name.equals("Series", ignoreCase = true)
                        val viewFields = if (isSeries) {
                            "Overview,ProviderIds,PrimaryImageTag,CommunityRating,OfficialRating,Genres,Tags,UserData,RecursiveItemCount"
                        } else {
                            "Overview,ProviderIds,PrimaryImageTag,CommunityRating,OfficialRating,Genres,Tags,UserData"
                        }
                        var deltaOffset = 0
                        do {
                            val deltaResponse = runCatching {
                                api.getLibraryItems(
                                    authHeader = authHeader,
                                    userId = userId,
                                    parentId = view?.id,
                                    includeItemTypes = if (isSeries) "Series" else "Movie",
                                    fields = viewFields,
                                    recursive = !isSeries,
                                    limit = 200,
                                    startIndex = deltaOffset,
                                    minDateLastSaved = effectiveLastSync
                                )
                            }.getOrNull()

                            val deltaItems = deltaResponse?.items ?: emptyList()
                            if (deltaItems.isNotEmpty()) {
                                val cachedOverviews = jellyfinDao.getCachedOverviews().associateBy { it.id }
                                val entities = deltaItems.map { dto ->
                                    val entity = dto.toEntity()
                                    val cached = cachedOverviews[entity.id]
                                    if (cached != null && entity.overview.isNullOrBlank()) {
                                        entity.copy(
                                            overview = cached.overview,
                                            backdropImageTag = entity.backdropImageTag ?: cached.backdropImageTag
                                        )
                                    } else {
                                        entity
                                    }
                                }
                                jellyfinDao.insertOrUpdateAll(entities)

                                val currentAllowed = accessPolicy.getAllowedIdsSet()?.toMutableSet() ?: mutableSetOf()
                                currentAllowed.addAll(entities.map { it.id })
                                accessPolicy.saveAllowedIds(userId, currentAllowed)

                                deltaCount += entities.size
                                deltaOffset += deltaItems.size
                            }
                            if (deltaItems.size < 200) break
                            delay(100L)
                        } while (true)
                    }

                    val nowTimestamp = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
                    userPreferencesRepository?.updateJellyfinLastSync(nowTimestamp)
                    onSyncCompleted?.invoke(nowTimestamp)

                    val updatedLocal = jellyfinDao.getCount()
                    _syncProgress.value = SyncProgress(
                        isSyncing = false,
                        current = updatedLocal,
                        total = updatedLocal,
                        message = if (deltaCount > 0) "Catálogo actualizado (+$deltaCount novedades)" else "Catálogo al día ($updatedLocal títulos)"
                    )
                    return@runCatching
                }

                // =========================================================================
                // CAMINO 2: SINCRONIZACIÓN COMPLETA GLOBAL CON RESUMEN / CHECKPOINTS
                // =========================================================================
                val pageSize = 200

                // Leer checkpoint previo si no es forzada desde cero
                val checkpoint = if (!forceFullSync) userPreferencesRepository?.getSyncCheckpoint() else null
                val savedViewIndex = checkpoint?.first ?: 0
                val savedOffset = checkpoint?.second ?: 0
                val savedTotalSynced = checkpoint?.third ?: 0

                var totalSynced = if (savedTotalSynced > 0) savedTotalSynced else 0
                var grandTotal = maxOf(currentLocal, totalSynced)

                _syncProgress.value = SyncProgress(
                    isSyncing = true,
                    current = totalSynced,
                    total = grandTotal,
                    message = if (totalSynced > 0) "Reanudando sincronización: $totalSynced / $grandTotal" else "Sincronizando: 0 / $grandTotal"
                )

                val viewsToSync = if (mediaViews.isNotEmpty()) mediaViews else listOf(null)
                var cumulativeViewTotal = 0

                for ((viewIndex, view) in viewsToSync.withIndex()) {
                    if (viewIndex < savedViewIndex) {
                        // Vista ya procesada en ejecución anterior
                        continue
                    }

                    val viewId = view?.id
                    val isSeries = view?.collectionType.equals("tvshows", ignoreCase = true) || view?.name.equals("Series", ignoreCase = true)
                    val itemType = if (isSeries) "Series" else if (view != null) "Movie" else "Movie,Series"
                    val isRecursive = !isSeries // Series son hijas directas del CollectionFolder, Movies pueden estar en subcarpetas
                    val viewFields = if (isSeries) {
                        "Overview,ProviderIds,PrimaryImageTag,CommunityRating,OfficialRating,Genres,Tags,UserData,RecursiveItemCount"
                    } else {
                        "Overview,ProviderIds,PrimaryImageTag,CommunityRating,OfficialRating,Genres,Tags,UserData"
                    }

                    var startIndex = if (viewIndex == savedViewIndex) savedOffset else 0
                    var viewTotal = 0

                    do {
                        var response: JellyfinItemsResponse? = null
                        var attempt = 0
                        var lastError: Exception? = null

                        while (attempt < 4 && response == null) {
                            try {
                                response = api.getLibraryItems(
                                    authHeader = authHeader,
                                    userId = userId,
                                    parentId = viewId,
                                    includeItemTypes = itemType,
                                    fields = viewFields,
                                    recursive = isRecursive,
                                    limit = pageSize,
                                    startIndex = startIndex,
                                    sortBy = "SortName",
                                    sortOrder = "Ascending",
                                    enableTotalRecordCount = (viewTotal == 0)
                                )
                            } catch (e: Exception) {
                                lastError = e
                                attempt++
                                if (e is retrofit2.HttpException && e.code() == 401) throw e
                                if (attempt < 4) delay(1000L * attempt)
                            }
                        }

                        if (response == null) {
                            android.util.Log.w("MediaSyncRepository", "Failed page $startIndex for view ${view?.name ?: "root"}: ${lastError?.message}")
                            // Avanzamos al siguiente bloque para no romper toda la sincronización
                            startIndex += pageSize
                            continue
                        }

                        val items = response.items
                        if (viewTotal == 0 && response.totalRecordCount > 0) {
                            viewTotal = response.totalRecordCount
                            cumulativeViewTotal += viewTotal
                            grandTotal = maxOf(grandTotal, cumulativeViewTotal)
                        }

                        if (items.isEmpty()) break

                        val entities = items.map { it.toEntity() }
                        jellyfinDao.insertOrUpdateAll(entities)

                        startIndex += items.size
                        totalSynced += items.size

                        // Guardar checkpoint en DataStore tras cada bloque persistido
                        userPreferencesRepository?.saveSyncCheckpoint(
                            viewIndex = viewIndex,
                            offset = startIndex,
                            totalSynced = totalSynced
                        )

                        val effectiveTotal = maxOf(grandTotal, totalSynced)
                        _syncProgress.value = SyncProgress(
                            isSyncing = true,
                            current = totalSynced,
                            total = effectiveTotal,
                            message = "Sincronizando: $totalSynced / $effectiveTotal"
                        )

                        if (viewTotal > 0 && startIndex >= viewTotal) break
                        if (items.size < pageSize) break

                        // Pausa de 200ms para mantener el servidor Jellyfin fluido sin congelarse
                        delay(200L)
                    } while (viewTotal == 0 || startIndex < viewTotal)
                }

                userPreferencesRepository?.clearSyncCheckpoint()

                val nowTimestamp = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
                userPreferencesRepository?.updateJellyfinLastSync(nowTimestamp)
                onSyncCompleted?.invoke(nowTimestamp)

                val finalCount = jellyfinDao.getCount()
                _syncProgress.value = SyncProgress(
                    isSyncing = false,
                    current = finalCount,
                    total = finalCount,
                    message = "Catálogo actualizado ($finalCount títulos)"
                )
            }.onFailure { e ->
                android.util.Log.e("MediaSyncRepository", "Error during Jellyfin library sync: ${e.message}", e)
                val is401 = (e as? retrofit2.HttpException)?.code() == 401
                val errorMessage = if (is401) {
                    "Sesión caducada (401). Reconecta en Ajustes"
                } else {
                    "Error al sincronizar: ${e.localizedMessage ?: "Error de conexión"}"
                }
                _syncProgress.value = SyncProgress(
                    isSyncing = false,
                    current = jellyfinDao.getCount(),
                    total = jellyfinDao.getCount(),
                    message = errorMessage
                )
            }
        } finally {
            syncMutex.unlock()
        }
    }
}

data class SyncProgress(
    val isSyncing: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = ""
)
