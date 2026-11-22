package tv.medusa.app.data.repository

import android.util.Log
import java.time.Instant
import java.time.temporal.ChronoUnit
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import tv.medusa.app.R
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.JellyfinDao
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.local.db.TmdbVoteCacheDao
import tv.medusa.app.data.local.db.preserveKnownLastPlayedDate
import tv.medusa.app.data.local.db.preserveKnownLastPlayedDates
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.jellyfin.JellyfinApiService
import tv.medusa.app.data.remote.jellyfin.JellyfinAuthRequest
import tv.medusa.app.data.remote.jellyfin.JellyfinItemsResponse
import tv.medusa.app.data.worker.JellyfinSyncWorker
import tv.medusa.app.data.worker.OverviewEnrichmentWorker
import tv.medusa.app.util.forEachBounded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    private val accessPolicy: MediaAccessPolicy = MediaAccessPolicy(userPreferencesRepository),
    private val appContext: Context? = null
) {
    /** Resuelve una cadena localizada; vacía si no hay contexto (p. ej. en tests unitarios). */
    private fun str(id: Int, vararg args: Any): String =
        appContext?.getString(id, *args).orEmpty()

    companion object {
        private val _syncProgress = MutableStateFlow(SyncProgress())
        val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
        private val syncMutex = Mutex()
        private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // El feed de la Home y el sync de fondo piden el quick init casi a la vez (el segundo lo
        // invoca como primer paso de `syncJellyfinLibrary`). Estas dos guardas evitan repetir las
        // mismas consultas contra el servidor mientras son recientes o están en curso.
        private val quickInitMutex = Mutex()
        @Volatile private var quickInitCompletedAt: Long = 0L
        private const val QUICK_INIT_FRESH_WINDOW_MS = 60_000L

        /**
         * Versión del algoritmo de sync. Súbela al cambiar la forma de recorrer el catálogo:
         * las instalaciones con una versión anterior se resincronizan enteras una vez (arregla
         * catálogos truncados por bugs previos de paginación).
         */
        const val SYNC_ALGORITHM_VERSION = 4

        /** Tope de páginas por vista: evita bucles si el servidor ignora `StartIndex`. */
        private const val MAX_PAGES_PER_VIEW = 1000

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
                        message = context.getString(R.string.sync_background)
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun startBackgroundOverviewEnrichment(serverUrl: String, userId: String, token: String) {
        if (serverUrl.isBlank() || userId.isBlank() || token.isBlank()) return
        // No competir con el sync de catálogo. En un servidor lento sus páginas ya rozan el
        // timeout; si además el enriquecido lanza olas de peticiones en paralelo, las páginas
        // del sync completo fallan y no se inserta nada. La Home lo relanza cuando el sync
        // termina (el progreso deja de estar `isSyncing`).
        if (_syncProgress.value.isSyncing) return
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

        // Unique work con REPLACE: re-lanza el enriquecido con los criterios de parada y lotes optimizados.
        WorkManager.getInstance(context).enqueueUniqueWork(
            OverviewEnrichmentWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
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

            // KEEP si no es forzada: evita cancelar un sync completo que ya esté avanzando
            // (p. ej. el de la primera instalación, cuando aún no hay lastSyncTimestamp).
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

    /**
     * Pide la caché local de portada **sin bloquear** a quien la invoca.
     *
     * Antes el feed de la Home esperaba (`await`) al quick init y no construía ninguna sección
     * hasta que terminase: con un catálogo grande eso incluía la consulta de vistos y el detalle
     * de cada serie (decenas de segundos con el servidor ocupado) antes del primer apartado.
     * Ahora se lanza en segundo plano y el feed sigue por su cuenta; cuando el sync de fondo
     * termina, la Home se recarga sola.
     */
    fun primeLocalCache(serverUrl: String, userId: String, token: String, force: Boolean = false) {
        if (serverUrl.isBlank() || userId.isBlank() || token.isBlank()) return
        if (_syncProgress.value.isSyncing) return
        syncScope.launch {
            runCatching {
                if (force || jellyfinDao.getCount() < 10) {
                    syncQuickInit(serverUrl, userId, token, force = force)
                }
            }
        }
    }

    /**
     * Caché local de portada (continuar viendo, recientes, favoritos y vistos).
     *
     * Es *single-flight*: si ya se ejecutó hace menos de [QUICK_INIT_FRESH_WINDOW_MS] o hay otro
     * en curso, devuelve sin volver a pedir nada. Antes el feed de la Home y el arranque del sync
     * de fondo (`syncJellyfinLibrary`) lo lanzaban a la vez y duplicaban estas consultas.
     */
    suspend fun syncQuickInit(
        serverUrl: String,
        userId: String,
        token: String,
        force: Boolean = false
    ): Result<Unit> {
        if (!force && System.currentTimeMillis() - quickInitCompletedAt < QUICK_INIT_FRESH_WINDOW_MS) {
            return Result.success(Unit)
        }
        if (!quickInitMutex.tryLock()) {
            return Result.success(Unit)
        }
        return try {
            runCatching { fetchCoverRows(serverUrl, userId, token) }
                .onSuccess { quickInitCompletedAt = System.currentTimeMillis() }
        } finally {
            quickInitMutex.unlock()
        }
    }

    private suspend fun trySilentReauthenticate(serverUrl: String): Pair<String, String>? {
        val prefs = userPreferencesRepository?.userPreferencesFlow?.firstOrNull() ?: return null
        val username = prefs.jellyfinUsername
        val password = prefs.jellyfinPassword
        if (username.isBlank() || password.isBlank()) {
            Log.w("MediaSyncRepository", "Silent re-auth skipped: username or password not available in preferences")
            return null
        }
        return try {
            Log.i("MediaSyncRepository", "Attempting silent re-authentication with Jellyfin for user '$username'...")
            val api = NetworkClientFactory.createJellyfinService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = "")
            val response = api.authenticateByName(
                authHeader = authHeader,
                request = JellyfinAuthRequest(username = username, pw = password)
            )
            val newToken = response.effectiveToken
            val newUserId = response.effectiveUser.effectiveId
            val authedUser = response.effectiveUser.effectiveName.ifBlank { username }
            if (newToken.isNotBlank()) {
                userPreferencesRepository?.updateJellyfinConfig(
                    serverUrl = serverUrl,
                    userId = newUserId,
                    accessToken = newToken,
                    username = authedUser,
                    password = password
                )
                runCatching {
                    appContext?.let { ctx ->
                        val authedHdr = buildJellyfinAuthHeader(token = newToken)
                        val passthroughMode = userPreferencesRepository?.audioPassthroughMode?.value
                            ?: tv.medusa.app.data.local.AUDIO_PASSTHROUGH_OFF
                        val caps = tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder
                            .createDefaultCapabilities(ctx, passthroughMode)
                        api.postCapabilities(authHeader = authedHdr, capabilities = caps)
                    }
                }
                Log.i("MediaSyncRepository", "Silent re-authentication succeeded! New token acquired.")
                Pair(newToken, newUserId)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("MediaSyncRepository", "Silent re-authentication attempt failed: ${e.message}")
            null
        }
    }

    private suspend fun fetchCoverRows(serverUrl: String, userId: String, token: String) {
        runCatching {
            var activeToken = token
            var activeUserId = userId
            var authHeader = buildJellyfinAuthHeader(token = activeToken)
            val api = NetworkClientFactory.createJellyfinService(serverUrl, JellyfinApiService::class.java)

            var playedEntities: List<JellyfinMediaEntity> = emptyList()

            coroutineScope {
                // 1. Sync Continue Watching (Resume + NextUp Items)
                launch {
                    try {
                        val resumeDeferred = async {
                            val res = runCatching {
                                api.getResumeItems(authHeader = authHeader, userId = activeUserId, limit = 30).items.map { it.toEntity() }
                            }
                            if (res.isFailure && (res.exceptionOrNull() as? retrofit2.HttpException)?.code() == 401) {
                                val reauthed = trySilentReauthenticate(serverUrl)
                                if (reauthed != null) {
                                    activeToken = reauthed.first
                                    activeUserId = reauthed.second
                                    authHeader = buildJellyfinAuthHeader(token = activeToken)
                                    runCatching {
                                        api.getResumeItems(authHeader = authHeader, userId = activeUserId, limit = 30).items.map { it.toEntity() }
                                    }.getOrDefault(emptyList())
                                } else emptyList()
                            } else {
                                res.getOrDefault(emptyList())
                            }
                        }
                        val nextUpDeferred = async {
                            val res = runCatching {
                                api.getNextUpItems(authHeader = authHeader, userId = activeUserId, limit = 30).items.map { it.toEntity() }
                            }
                            if (res.isFailure && (res.exceptionOrNull() as? retrofit2.HttpException)?.code() == 401) {
                                val reauthed = trySilentReauthenticate(serverUrl)
                                if (reauthed != null) {
                                    activeToken = reauthed.first
                                    activeUserId = reauthed.second
                                    authHeader = buildJellyfinAuthHeader(token = activeToken)
                                    runCatching {
                                        api.getNextUpItems(authHeader = authHeader, userId = activeUserId, limit = 30).items.map { it.toEntity() }
                                    }.getOrDefault(emptyList())
                                } else emptyList()
                            } else {
                                res.getOrDefault(emptyList())
                            }
                        }
                        val resumeEntities = resumeDeferred.await()
                        val nextUpEntities = nextUpDeferred.await()
                        // Los capítulos de NextUp vienen sin fecha (todavía no se han reproducido): se
                        // hereda la de su serie para que ordenen bien, y nunca se escribe una fecha
                        // inventada. La fecha de la serie se deduce del máximo de sus capítulos ya
                        // vistos que ya estén en Room: Jellyfin no fecha las series, así que su fila
                        // nunca sirve. `preserveKnownLastPlayedDates` evita pisar con un 0 (el insert
                        // es un REPLACE) la fecha que ya hubiera.
                        val seriesMissingDate = nextUpEntities
                            .mapNotNull { it.seriesId }
                            .distinct()
                            .filter { seriesId -> resumeEntities.none { it.seriesId == seriesId && it.lastPlayedTicks > 0L } }
                        val knownFromDb = if (seriesMissingDate.isEmpty()) {
                            emptyMap()
                        } else {
                            jellyfinDao.getKnownSeriesLastPlayedTicks(seriesMissingDate).associate { it.id to it.lastPlayedTicks }
                        }
                        val allEntities = jellyfinDao.preserveKnownLastPlayedDates(
                            continueWatchingEntitiesToPersist(
                                items = resumeEntities + nextUpEntities,
                                seriesDates = seriesLastPlayedDates(resumeEntities, nextUpEntities, knownFromDb)
                            )
                        )
                        if (allEntities.isNotEmpty()) {
                            jellyfinDao.insertOrUpdateAll(allEntities)
                        }
                    } catch (_: Exception) {}
                }

                // 2. Sync Recent Items (Fast, immediately populates UI with up to 50 items)
                launch {
                    try {
                        val latest = api.getLatestItems(authHeader = authHeader, userId = userId, limit = 50)
                        // Sin pisar fechas ya guardadas: el insert es un REPLACE y un 0 borraría el valor real.
                        val latestEntities = jellyfinDao.preserveKnownLastPlayedDates(latest.map { it.toEntity() })
                        if (latestEntities.isNotEmpty()) {
                            jellyfinDao.insertOrUpdateAll(latestEntities)
                        }
                    } catch (e: Exception) {
                        Log.e("MediaSyncRepository", "Error syncing latest items: ${e.message}", e)
                    }
                }

                // 3. Sync Favorites (Fast, critical)
                launch {
                    try {
                        val seriesFav = async {
                            runCatching { api.getFavoriteItems(authHeader = authHeader, userId = userId, includeItemTypes = "Series") }.getOrNull()?.items ?: emptyList()
                        }
                        val movieFav = async {
                            runCatching { api.getFavoriteItems(authHeader = authHeader, userId = userId, includeItemTypes = "Movie") }.getOrNull()?.items ?: emptyList()
                        }
                        val episodeFav = async {
                            runCatching { api.getFavoriteItems(authHeader = authHeader, userId = userId, includeItemTypes = "Episode") }.getOrNull()?.items ?: emptyList()
                        }
                        val allFavDtos = (seriesFav.await() + movieFav.await() + episodeFav.await()).distinctBy { it.id }
                        val favEntities = jellyfinDao.preserveKnownLastPlayedDates(
                            allFavDtos.map { it.toEntity().copy(isFavorite = true) }
                        )
                        if (favEntities.isNotEmpty()) {
                            jellyfinDao.insertOrUpdateAll(favEntities)
                            val epWithSeries = favEntities.filter { it.type.equals("Episode", ignoreCase = true) && !it.seriesId.isNullOrBlank() }
                            for (ep in epWithSeries) {
                                val sId = ep.seriesId!!
                                jellyfinDao.updateFavoriteStatus(sId, true)
                                if (jellyfinDao.getItemById(sId) == null) {
                                    runCatching {
                                        val seriesDto = api.getItemDetail(authHeader = authHeader, userId = userId, itemId = sId)
                                        jellyfinDao.insertOrUpdate(seriesDto.toEntity().copy(isFavorite = true))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MediaSyncRepository", "Error syncing favorites: ${e.message}", e)
                    }
                }

                // 4. Sync Recently Played items (Fast, marks watched/progress in local DB)
                launch {
                    try {
                        val playedResponse = api.getRecentlyPlayedItems(authHeader = authHeader, userId = userId, limit = 100)
                        playedEntities = jellyfinDao.preserveKnownLastPlayedDates(playedResponse.items.map { it.toEntity() })
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
                // En paralelo acotado: antes era un bucle en serie (N series = N peticiones encadenadas)
                seriesIds.forEachBounded(4) { sId ->
                    try {
                        val seriesDto = api.getItemDetail(authHeader = authHeader, userId = userId, itemId = sId)
                        jellyfinDao.insertOrUpdate(jellyfinDao.preserveKnownLastPlayedDate(seriesDto.toEntity()))
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
                val api = NetworkClientFactory.createJellyfinService(serverUrl, JellyfinApiService::class.java)
                var currentToken = token
                var currentUserId = userId
                var authHeader = buildJellyfinAuthHeader(token = currentToken)

                // Purga entradas caducadas (>24h) de la caché de votos TMDB para que no crezca sin límite.
                runCatching { tmdbVoteCacheDao?.deleteExpired() }

                val currentLocal = jellyfinDao.getCount()

                // Los catálogos truncados por versiones anteriores (paginación que cortaba con
                // páginas más cortas que el límite) se reconstruyen una vez al actualizar: si la
                // versión aplicada es menor que la actual, se fuerza un sync completo.
                val storedSyncVersion = userPreferencesRepository?.getSyncAlgorithmVersion() ?: 0
                val needFullResync = forceFullSync || storedSyncVersion < SYNC_ALGORITHM_VERSION

                val effectiveLastSync = if (needFullResync) null else {
                    lastSyncTimestamp?.ifBlank { null }
                        ?: userPreferencesRepository?.userPreferencesFlow?.firstOrNull()?.jellyfinLastSync?.ifBlank { null }
                        ?: if (currentLocal >= 500) {
                            val now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
                            userPreferencesRepository?.updateJellyfinLastSync(now)
                            now
                        } else null
                }

                // 1. Prioridad: refrescar de inmediato los elementos de portada (< 500ms)
                syncQuickInit(serverUrl, currentUserId, currentToken)

                // 2. Obtener vistas del usuario (Películas, Series, etc.) con re-autenticación automática en 401
                var userViewsResponse = runCatching {
                    api.getUserViews(authHeader = authHeader, userId = currentUserId).items
                }
                if (userViewsResponse.isFailure && (userViewsResponse.exceptionOrNull() as? retrofit2.HttpException)?.code() == 401) {
                    val reauthed = trySilentReauthenticate(serverUrl)
                    if (reauthed != null) {
                        currentToken = reauthed.first
                        currentUserId = reauthed.second
                        authHeader = buildJellyfinAuthHeader(token = currentToken)
                        userViewsResponse = runCatching {
                            api.getUserViews(authHeader = authHeader, userId = currentUserId).items
                        }
                    } else {
                        throw userViewsResponse.exceptionOrNull()!!
                    }
                }
                val userViews = userViewsResponse.getOrDefault(emptyList())

                val mediaViews = userViews.filter { view ->
                    val cType = view.collectionType?.lowercase()
                    cType == "movies" || cType == "tvshows" || cType == "mixed" ||
                        (cType == null && !view.name.orEmpty().contains("playlist", ignoreCase = true) && !view.name.orEmpty().contains("coleccion", ignoreCase = true) && !view.name.orEmpty().contains("boxset", ignoreCase = true))
                }

                if (needFullResync) {
                    userPreferencesRepository?.clearSyncCheckpoint()
                }

                // =========================================================================
                // CAMINO 1: SINCRONIZACIÓN INCREMENTAL (DELTA SYNC)
                // =========================================================================
                // Si el catálogo local ya tiene elementos y una fecha de sincronización previa completa
                if (!needFullResync && currentLocal > 0 && !effectiveLastSync.isNullOrBlank()) {
                    _syncProgress.value = SyncProgress(
                        isSyncing = true,
                        current = currentLocal,
                        total = currentLocal,
                        message = str(R.string.sync_searching_news)
                    )

                    var deltaCount = 0
                    for (view in mediaViews.ifEmpty { listOf(null) }) {
                        val isSeries = view?.collectionType.equals("tvshows", ignoreCase = true) || view?.name.equals("Series", ignoreCase = true)
                        val isMovies = view?.collectionType.equals("movies", ignoreCase = true) || view?.name.equals("Movies", ignoreCase = true) || view?.name.equals("Películas", ignoreCase = true)
                        val itemType = if (isSeries) "Series" else if (isMovies) "Movie" else "Movie,Series"
                        val viewFields = if (isSeries) {
                            "Overview,ProviderIds,PrimaryImageTag,BackdropImageTags,CommunityRating,OfficialRating,Genres,Tags,UserData,RecursiveItemCount"
                        } else {
                            "Overview,ProviderIds,PrimaryImageTag,BackdropImageTags,CommunityRating,OfficialRating,Genres,Tags,UserData"
                        }
                        var deltaOffset = 0
                        var deltaTotal = 0
                        var deltaPages = 0
                        // Una sola lectura de la caché de sinopsis por vista, no por página.
                        val cachedOverviews = jellyfinDao.getCachedOverviews().associateBy { it.id }
                        do {
                            val deltaResponse = runCatching {
                                try {
                                    api.getLibraryItems(
                                        authHeader = authHeader,
                                        userId = currentUserId,
                                        parentId = view?.id,
                                        includeItemTypes = itemType,
                                        fields = viewFields,
                                        recursive = true,
                                        limit = 200,
                                        startIndex = deltaOffset,
                                        minDateLastSaved = effectiveLastSync
                                    )
                                } catch (e: Exception) {
                                    if (e is retrofit2.HttpException && e.code() == 401) {
                                        val reauthed = trySilentReauthenticate(serverUrl)
                                        if (reauthed != null) {
                                            currentToken = reauthed.first
                                            currentUserId = reauthed.second
                                            authHeader = buildJellyfinAuthHeader(token = currentToken)
                                            api.getLibraryItems(
                                                authHeader = authHeader,
                                                userId = currentUserId,
                                                parentId = view?.id,
                                                includeItemTypes = itemType,
                                                fields = viewFields,
                                                recursive = true,
                                                limit = 200,
                                                startIndex = deltaOffset,
                                                minDateLastSaved = effectiveLastSync
                                            )
                                        } else throw e
                                    } else throw e
                                }
                            }.getOrNull()

                            if (deltaTotal == 0 && (deltaResponse?.totalRecordCount ?: 0) > 0) {
                                deltaTotal = deltaResponse!!.totalRecordCount
                            }
                            val deltaItems = deltaResponse?.items ?: emptyList()
                            if (deltaItems.isNotEmpty()) {
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
                                jellyfinDao.insertOrUpdateAll(jellyfinDao.preserveKnownLastPlayedDates(entities))

                                deltaCount += entities.size
                                deltaOffset += deltaItems.size
                            }
                            deltaPages++
                            // Igual que en el sync completo: no damos la página por última solo
                            // porque venga más corta de lo pedido si el total dice que queda más.
                            if (deltaPages > MAX_PAGES_PER_VIEW) break
                            if (!hasMorePages(deltaItems.size, deltaOffset, 200, deltaTotal)) break
                            delay(100L)
                        } while (true)
                    }

                    // La lista de IDs permitidos se siembra desde el catálogo local ya sincronizado.
                    // Solo hace falta reescribirla si el delta trajo novedades: un delta vacío
                    // reemplazaba 33k filas y un fichero de 1,1 MB en cada arranque.
                    if (deltaCount > 0) {
                        accessPolicy.saveAllowedIds(currentUserId, jellyfinDao.getAllIds().toSet())
                    }

                    val nowTimestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
                    userPreferencesRepository?.updateJellyfinLastSync(nowTimestamp)
                    onSyncCompleted?.invoke(nowTimestamp)

                    val updatedLocal = jellyfinDao.getCount()
                    _syncProgress.value = SyncProgress(
                        isSyncing = false,
                        current = updatedLocal,
                        total = updatedLocal,
                        message = if (deltaCount > 0) str(R.string.sync_updated_with_news, deltaCount) else str(R.string.sync_up_to_date, updatedLocal)
                    )
                    return@runCatching
                }

                // =========================================================================
                // CAMINO 2: SINCRONIZACIÓN COMPLETA GLOBAL CON RESUMEN / CHECKPOINTS
                // =========================================================================
                // Al reconstruir el catálogo se parte de una lista de permitidos limpia: si
                // quedara una lista parcial de una ejecución anterior, la UI filtraría por ella
                // y volvería a mostrar solo una parte del catálogo mientras dura el sync.
                accessPolicy.clearAllowedIds(currentUserId)

                val pageSize = 200

                // Leer checkpoint previo si no es forzada desde cero
                val checkpoint = if (!needFullResync) userPreferencesRepository?.getSyncCheckpoint() else null
                val savedViewIndex = checkpoint?.first ?: 0
                val savedOffset = checkpoint?.second ?: 0
                val savedTotalSynced = checkpoint?.third ?: 0

                var totalSynced = if (savedTotalSynced > 0) savedTotalSynced else 0
                var grandTotal = maxOf(currentLocal, totalSynced)

                _syncProgress.value = SyncProgress(
                    isSyncing = true,
                    current = totalSynced,
                    total = grandTotal,
                    message = if (totalSynced > 0) str(R.string.sync_resuming, totalSynced, grandTotal) else str(R.string.sync_progress_zero, grandTotal)
                )

                val viewsToSync = if (mediaViews.isNotEmpty()) mediaViews else listOf(null)
                var cumulativeViewTotal = savedTotalSynced

                for ((viewIndex, view) in viewsToSync.withIndex()) {
                    if (viewIndex < savedViewIndex) {
                        // Vista ya procesada en ejecución anterior
                        continue
                    }

                    val viewId = view?.id
                    val isSeries = view?.collectionType.equals("tvshows", ignoreCase = true) || view?.name.equals("Series", ignoreCase = true)
                    val isMovies = view?.collectionType.equals("movies", ignoreCase = true) || view?.name.equals("Movies", ignoreCase = true) || view?.name.equals("Películas", ignoreCase = true)
                    val itemType = if (isSeries) "Series" else if (isMovies) "Movie" else "Movie,Series"
                    val isRecursive = true // En Jellyfin un UserView contiene subcarpetas/colecciones; recursive = true es necesario
                    val viewFields = if (isSeries) {
                        "Overview,ProviderIds,PrimaryImageTag,BackdropImageTags,CommunityRating,OfficialRating,Genres,Tags,UserData,RecursiveItemCount"
                    } else {
                        "Overview,ProviderIds,PrimaryImageTag,BackdropImageTags,CommunityRating,OfficialRating,Genres,Tags,UserData"
                    }

                    var startIndex = if (viewIndex == savedViewIndex) savedOffset else 0
                    var viewTotal = 0
                    var successfulPages = 0
                    var consecutiveFailedPages = 0

                    do {
                        var response: JellyfinItemsResponse? = null
                        var attempt = 0
                        var reauthAttempts = 0
                        var lastError: Exception? = null

                        while (attempt < 4 && response == null) {
                            try {
                                response = api.getLibraryItems(
                                    authHeader = authHeader,
                                    userId = currentUserId,
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
                                if (e is retrofit2.HttpException && e.code() == 401) {
                                    // Una sola re-autenticación por página: si el token recién
                                    // obtenido también da 401, reintentar en bucle saturaba el
                                    // servidor de logins y dejaba el sync girando sin avanzar.
                                    if (reauthAttempts >= 1) throw e
                                    reauthAttempts++
                                    val reauthed = trySilentReauthenticate(serverUrl)
                                    if (reauthed != null) {
                                        currentToken = reauthed.first
                                        currentUserId = reauthed.second
                                        authHeader = buildJellyfinAuthHeader(token = currentToken)
                                        attempt = 0
                                        continue
                                    }
                                    throw e
                                }
                                if (attempt < 4) delay(1000L * attempt)
                            }
                        }

                        if (response == null) {
                            consecutiveFailedPages++
                            Log.w(
                                "MediaSyncRepository",
                                "Failed page $startIndex for view ${view?.name ?: "root"} (fallo $consecutiveFailedPages): ${lastError?.message}"
                            )
                            // Avanzar de 200 en 200 sin límite cuando una vista no entrega ni una
                            // página convertía el sync completo en un bucle eterno: no se insertaba
                            // nada (solo se veía la caché rápida, ~110 filas) y la barra se quedaba
                            // horas "sincronizando". Ahora se aborta para que WorkManager reintente
                            // más tarde y el usuario vea el error en lugar de una espera infinita.
                            if (successfulPages == 0 || consecutiveFailedPages >= 3) {
                                throw lastError ?: java.io.IOException(
                                    "No se pudo leer la página $startIndex de la vista ${view?.name ?: "raíz"}"
                                )
                            }
                            startIndex += pageSize
                            continue
                        }

                        consecutiveFailedPages = 0
                        successfulPages++

                        if (successfulPages > MAX_PAGES_PER_VIEW) {
                            Log.w(
                                "MediaSyncRepository",
                                "Tope de $MAX_PAGES_PER_VIEW páginas en la vista ${view?.name ?: "raíz"} ($startIndex items); se continúa con la siguiente"
                            )
                            break
                        }

                        val items = response.items
                        if (viewTotal == 0 && response.totalRecordCount > 0) {
                            viewTotal = response.totalRecordCount
                            cumulativeViewTotal += viewTotal
                            grandTotal = maxOf(grandTotal, cumulativeViewTotal)
                        }

                        if (items.isEmpty()) break

                        val entities = jellyfinDao.preserveKnownLastPlayedDates(items.map { it.toEntity() })
                        jellyfinDao.insertOrUpdateAll(entities)

                        startIndex += items.size
                        totalSynced += items.size

                        // Guardar checkpoint en DataStore cada 1.000 ítems o al final de la vista
                        // (evita saturar el bus de I/O y disparar 100+ emisiones de DataStore en TV lenta)
                        if (totalSynced % 1000 == 0 || items.size < pageSize) {
                            userPreferencesRepository?.saveSyncCheckpoint(
                                viewIndex = viewIndex,
                                offset = startIndex,
                                totalSynced = totalSynced
                            )
                        }

                        val effectiveTotal = maxOf(grandTotal, totalSynced)
                        _syncProgress.value = SyncProgress(
                            isSyncing = true,
                            current = totalSynced,
                            total = effectiveTotal,
                            message = str(R.string.sync_progress, totalSynced, effectiveTotal)
                        )

                        // No basta con "página corta = fin": Jellyfin (o un proxy) puede devolver
                        // menos items que los pedidos aunque queden más. Solo paramos de verdad
                        // cuando no queda recorrido según el total, o cuando llegó una página vacía.
                        if (!hasMorePages(items.size, startIndex, pageSize, viewTotal)) break

                        // Pausa de 200ms para mantener el servidor Jellyfin fluido sin congelarse
                        delay(200L)
                    } while (viewTotal == 0 || startIndex < viewTotal)
                }

                userPreferencesRepository?.clearSyncCheckpoint()

                // Sembrar los IDs permitidos desde el catálogo local ya completo: sustituye a la
                // reconciliación paginada contra el servidor y deja la lista siempre coherente
                // con lo que hay en Room (sin listas parciales de ejecuciones anteriores).
                runCatching {
                    val allIds = jellyfinDao.getAllIds()
                    if (allIds.isNotEmpty()) {
                        accessPolicy.saveAllowedIds(currentUserId, allIds.toSet())
                    }
                }

                val nowTimestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
                userPreferencesRepository?.updateJellyfinLastSync(nowTimestamp)
                // Marca la versión de algoritmo aplicada: así no se repite el resync completo.
                runCatching { userPreferencesRepository?.updateSyncAlgorithmVersion(SYNC_ALGORITHM_VERSION) }
                onSyncCompleted?.invoke(nowTimestamp)

                val finalCount = jellyfinDao.getCount()
                _syncProgress.value = SyncProgress(
                    isSyncing = false,
                    current = finalCount,
                    total = finalCount,
                    message = str(R.string.sync_updated_count, finalCount)
                )

                // Encolar enriquecimiento de fondo una vez concluida la sincronización completa del catálogo
                startBackgroundOverviewEnrichment(serverUrl, currentUserId, currentToken)
            }.onFailure { e ->
                Log.e("MediaSyncRepository", "Error during Jellyfin library sync: ${e.message}", e)
                val is401 = (e as? retrofit2.HttpException)?.code() == 401
                val isNetworkError = e is java.net.ConnectException ||
                        e is java.net.SocketTimeoutException ||
                        e is java.net.UnknownHostException ||
                        e is java.io.IOException ||
                        (e.cause is java.net.ConnectException) ||
                        (e.cause is java.net.SocketTimeoutException) ||
                        (e.cause is java.net.UnknownHostException)
                val currentCount = jellyfinDao.getCount()

                if (currentCount > 0 && !is401) {
                    // Si ya tenemos catálogo local y el fallo no es una sesión caducada (401),
                    // no alarmamos al usuario con un error rojo en la barra superior; dejamos el catálogo disponible.
                    _syncProgress.value = SyncProgress(
                        isSyncing = false,
                        isError = false,
                        current = currentCount,
                        total = currentCount,
                        message = str(R.string.sync_up_to_date, currentCount)
                    )
                } else {
                    val errorMessage = if (is401) {
                        str(R.string.sync_session_expired)
                    } else {
                        str(R.string.sync_error_prefix, str(R.string.sync_error_connection))
                    }
                    _syncProgress.value = SyncProgress(
                        isSyncing = false,
                        isError = true,
                        current = currentCount,
                        total = currentCount,
                        message = errorMessage
                    )
                    // Auto-limpiar el error en 6 segundos para que no se quede bloqueado en la barra superior
                    syncScope.launch {
                        delay(6000L)
                        if (_syncProgress.value.isError) {
                            _syncProgress.value = SyncProgress(
                                isSyncing = false,
                                isError = false,
                                current = currentCount,
                                total = currentCount
                            )
                        }
                    }
                }
            }
        } finally {
            syncMutex.unlock()
        }
    }
}

/**
 * ¿Quedan más páginas por recorrer en la vista actual?
 *
 * Jellyfin (o un proxy delante) puede devolver **menos items que los pedidos** aunque queden
 * más por llegar (`Limit` máximo del servidor, `MaxItemsPerPage`, etc.). Dar la vista por
 * terminada con una página corta truncaba el catálogo a las primeras decenas/cientos de
 * títulos (el caso de "solo me aparecen ~110").
 *
 * - Con total conocido ([totalRecordCount] > 0): seguimos mientras quede recorrido.
 * - Sin total: no nos fiamos de la página corta; se hace una petición más y se corta cuando
 *   llegue una página vacía.
 */
internal fun hasMorePages(
    itemsOnPage: Int,
    startIndex: Int,
    pageSize: Int,
    totalRecordCount: Int
): Boolean {
    if (itemsOnPage <= 0) return false
    if (totalRecordCount > 0) return startIndex < totalRecordCount
    return true
}

data class SyncProgress(
    val isSyncing: Boolean = false,
    val isError: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = ""
)
