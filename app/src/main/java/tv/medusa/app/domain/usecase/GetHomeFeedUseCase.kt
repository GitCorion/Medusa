package tv.medusa.app.domain.usecase

import android.content.Context
import tv.medusa.app.data.local.HomeSnapshotFileStore
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.medusa.app.data.local.DEFAULT_TMDB_API_KEY
import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.data.local.DEFAULT_TRAKT_CLIENT_ID
import tv.medusa.app.data.model.SUPPORTED_PLATFORMS
import tv.medusa.app.data.local.UserPreferences
import tv.medusa.app.data.local.db.HomeSectionCacheDao
import tv.medusa.app.data.local.db.HomeSectionCacheEntity
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.HomeSectionKind
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.model.MediaItemSnapshotCodec

import tv.medusa.app.util.toOptimizedMediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class GetHomeFeedUseCase(
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase,
    private val homeSectionCacheDao: HomeSectionCacheDao? = null,
    appContext: Context? = null,
    private val collectionIndexUseCase: CollectionIndexUseCase? = null
) {
    private val snapshotFileStore = appContext?.let { HomeSnapshotFileStore(it) }

    @Volatile private var memorySnapshot: List<HomeSection>? = null
    @Volatile private var memorySnapshotAt: Long = 0L
    companion object {
        @Volatile
        private var cachedHomeFeed: List<HomeSection>? = null
        @Volatile
        private var cachedFeedTimestamp: Long = 0L
        @Volatile
        private var memorySnapshot: List<HomeSection>? = null
        @Volatile
        private var memorySnapshotAt: Long = 0L
        private const val FEED_CACHE_TTL = 15 * 60 * 1000L // 15 minutos

        fun invalidateFeedCache() {
            cachedHomeFeed = null
            cachedFeedTimestamp = 0L
        }

        fun clearMemorySnapshot() {
            memorySnapshot = null
            memorySnapshotAt = 0L
        }

        fun sectionPriority(title: String): Int = when {
            title == "Continuar Viendo" -> 0
            title.startsWith("Ciclo ") || title.startsWith("Selección Festiva") ||
            title.startsWith("Temporada de Premios") || title.startsWith("Especial ") -> 1
            // La tendencia del día es la señal más fuerte de "qué ver ahora": va justo después
            // de Continuar Viendo y por encima de las novedades de la biblioteca.
            title.contains("Top 10", ignoreCase = true) -> 2
            title == "Últimos estrenos" -> 5
            title == "Tendencias hoy" -> 10
            title == "Añadido recientemente" -> 20
            title.startsWith("Porque viste") -> 30
            // Todas las filas de plataformas se generan como "Populares en X"; las antiguas
            // ramas por nombre de plataforma eran código muerto.
            title.startsWith("Populares en ") -> 40
            title.startsWith("Trakt:") -> 50
            title == "Tu Watchlist de Trakt.tv" -> 51
            title == "Recomendado por la comunidad Trakt" -> 52
            title == "Tendencias en Trakt.tv" -> 53
            title == "Grandes Producciones de tu colección" -> 70
            title == "Series Destacadas" -> 80
            title == "Películas Recomendadas" -> 90
            title.startsWith("Recomendados de") -> 100
            else -> 200
        }

        /**
         * Prioriza por identidad de sección cuando está definida. Las secciones restauradas de
         * cachés antiguas (sin `kind`) siguen funcionando por título.
         */
        fun sectionPriority(section: HomeSection): Int = when (section.kind) {
            HomeSectionKind.CONTINUE_WATCHING -> 0
            HomeSectionKind.TRENDING -> 2
            HomeSectionKind.NEW_RELEASES -> 5
            HomeSectionKind.RECENTLY_ADDED -> 20
            HomeSectionKind.BECAUSE_YOU_WATCHED -> 30
            HomeSectionKind.PLATFORM -> 40
            HomeSectionKind.TRAKT -> 50
            HomeSectionKind.LIBRARY_TOP_MOVIES -> 70
            HomeSectionKind.LIBRARY_TOP_SERIES -> 80
            HomeSectionKind.RECOMMENDED_MOVIES -> 90
            HomeSectionKind.GENRE -> 100
            HomeSectionKind.CUSTOM -> sectionPriority(section.title)
        }
    }

    suspend fun clearSectionCache() {
        invalidateFeedCache()
        runCatching { homeSectionCacheDao?.clearAll() }
    }

    operator fun invoke(prefs: UserPreferences, forceRefresh: Boolean = false): Flow<List<HomeSection>> = channelFlow {
        if (prefs.jellyfinServerUrl.isBlank() || prefs.jellyfinUserId.isBlank() || prefs.jellyfinAccessToken.isBlank()) {
            send(emptyList())
            return@channelFlow
        }

        // Si tenemos caché en memoria completo reciente (< 5 min), emitir de inmediato y evitar recargas innecesarias
        val cached = cachedHomeFeed
        if (!forceRefresh && cached != null && cached.size >= 4) {
            send(cached)
            if (System.currentTimeMillis() - cachedFeedTimestamp < 5 * 60 * 1000L) {
                return@channelFlow
            }
        }

        if (forceRefresh) {
            // No borrar el snapshot persistente: se sigue mostrando mientras se reconstruye.
            // El nuevo snapshot sustituye al anterior al terminar el feed.
            invalidateFeedCache()
        }

        // Caché local de portada en segundo plano. Antes se esperaba aquí (`await`) y la Home no
        // pintaba ninguna sección hasta que terminase: incluía la consulta de vistos (hasta ~40 s
        // contra un servidor ocupado) y el detalle de cada serie, uno detrás de otro.
        mediaRepository.primeLocalCache(
            serverUrl = prefs.jellyfinServerUrl,
            userId = prefs.jellyfinUserId,
            token = prefs.jellyfinAccessToken,
            force = forceRefresh
        )

        val sections = mutableListOf<HomeSection>()
        val shownMediaIds = ConcurrentHashMap.newKeySet<String>()

        var lastEmitTime = 0L
        val emitFeed: suspend (List<HomeSection>, Boolean) -> Unit = { list, forceImmediate ->
            val now = System.currentTimeMillis()
            if (list.isNotEmpty() && (forceImmediate || now - lastEmitTime >= 300L)) {
                lastEmitTime = now
                val snapshot = list.toList()
                if (snapshot.size >= 4) {
                    cachedHomeFeed = snapshot
                    cachedFeedTimestamp = now
                }
                send(snapshot)
            }
        }


        fun updateOrAddSection(newSection: HomeSection) {
            val uniqueItems = newSection.items.distinctBy { it.id }
            if (uniqueItems.isEmpty()) return
            val cleanSection = if (uniqueItems.size == newSection.items.size) newSection else newSection.copy(items = uniqueItems)
            val idx = sections.indexOfFirst { it.title == cleanSection.title }
            if (idx != -1) {
                sections[idx] = cleanSection
            } else {
                val newPriority = sectionPriority(cleanSection)
                val insertIdx = sections.indexOfFirst { sectionPriority(it) > newPriority }
                if (insertIdx != -1) {
                    sections.add(insertIdx, cleanSection)
                } else {
                    sections.add(cleanSection)
                }
            }
        }

        val feedMutex = Mutex()
        suspend fun postSection(
            newSection: HomeSection,
            cacheKey: String? = null,
            itemIds: List<String> = emptyList()
        ) {
            feedMutex.withLock {
                val uniqueItems = newSection.items.distinctBy { it.id }
                if (uniqueItems.isEmpty()) return@withLock
                val cleanSection = if (uniqueItems.size == newSection.items.size) newSection else newSection.copy(items = uniqueItems)
                updateOrAddSection(cleanSection)
                if (cacheKey != null && itemIds.isNotEmpty()) {
                    persistSection(cacheKey, cleanSection, itemIds.distinct())
                }
                emitFeed(sections, false)
            }
        }

        // =========================================================================
        // PASO 0: RESTAURAR EL SNAPSHOT PERSISTENTE DEL HOME (< 100 ms)
        // Es lo primero que se pinta. Si existe, el usuario ve filas reales incluso
        // tras matar el proceso o reiniciar la TV, sin esperar a Room ni a la red.
        // =========================================================================
        if (!forceRefresh) {
            val cachedSectionsRestored = restoreCachedSections(
                prefs = prefs,
                shownMediaIds = shownMediaIds,
                sections = sections
            )
            if (cachedSectionsRestored) emitFeed(sections, true)
        }

        // =========================================================================
        // PASO 1: CATÁLOGO LOCAL EN PARALELO
        // Antes eran 8 consultas en serie (la primera emisión esperaba a la suma de todas).
        // Ahora cada consulta corre en paralelo y la primera emisión llega antes.
        // =========================================================================
        var localContinueWatching: List<JellyfinMediaEntity> = emptyList()
        var localRecentlyPlayed: List<JellyfinMediaEntity> = emptyList()
        var localLatest: List<JellyfinMediaEntity> = emptyList()
        var localTopMovies: List<JellyfinMediaEntity> = emptyList()
        var localTopSeries: List<JellyfinMediaEntity> = emptyList()
        var watchedGenres: List<String> = emptyList()
        var localRecommendedMovies: List<JellyfinMediaEntity> = emptyList()
        var localNewReleases: List<JellyfinMediaEntity> = emptyList()

        coroutineScope {
            val continueWatchingJob = async { runCatching { mediaRepository.getContinueWatchingLocal(20) }.getOrDefault(emptyList()) }
            val recentlyPlayedJob = async { runCatching { mediaRepository.getRecentlyPlayedLocal(20) }.getOrDefault(emptyList()) }
            val latestJob = async { runCatching { mediaRepository.getLatestLocal(prefs.jellyfinServerUrl, prefs.jellyfinUserId, 20) }.getOrDefault(emptyList()) }
            val topMoviesJob = async { runCatching { mediaRepository.getTopMoviesLocal(30) }.getOrDefault(emptyList()) }
            val topSeriesJob = async { runCatching { mediaRepository.getTopSeriesLocal(30) }.getOrDefault(emptyList()) }
            val genresJob = async { runCatching { mediaRepository.getMostWatchedGenres() }.getOrDefault(emptyList()) }
            val newReleasesJob = async { runCatching { mediaRepository.getNewReleasesLocal(20) }.getOrDefault(emptyList()) }
            val recommendedJob = async {
                val genres = genresJob.await()
                runCatching {
                    mediaRepository.getRecommendedMovies(excludeIds = emptySet(), limit = 20, genres = genres)
                }.getOrDefault(emptyList())
            }

            localContinueWatching = continueWatchingJob.await()
            localRecentlyPlayed = recentlyPlayedJob.await()
            localLatest = latestJob.await()
            localTopMovies = topMoviesJob.await().filterPersonalizedForHeader(emptyList())
            localTopSeries = topSeriesJob.await().filterPersonalizedForHeader(emptyList())
            watchedGenres = genresJob.await()
            localRecommendedMovies = recommendedJob.await()
            localNewReleases = newReleasesJob.await()
        }

        if (localContinueWatching.isNotEmpty()) {
            updateOrAddSection(HomeSection(
                title = "Continuar Viendo",
                items = localContinueWatching.take(20).map { it.toOptimizedMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.CONTINUE_WATCHING
            ))
        }


        // Últimos estrenos: lo más nuevo por año de estreno que hay en la biblioteca. Va arriba,
        // justo después de Continuar Viendo, para que lo recién estrenado no quede enterrado.
        if (localNewReleases.isNotEmpty()) {
            updateOrAddSection(HomeSection(
                title = "Últimos estrenos",
                items = localNewReleases.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.NEW_RELEASES
            ))
        }
        if (localLatest.isNotEmpty()) {
            updateOrAddSection(HomeSection(
                title = "Añadido recientemente",
                items = localLatest.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.RECENTLY_ADDED
            ))
        }
        if (localTopMovies.isNotEmpty()) {
            updateOrAddSection(HomeSection(
                title = "Grandes Producciones de tu colección",
                items = localTopMovies.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.LIBRARY_TOP_MOVIES
            ))
        }
        if (localTopSeries.isNotEmpty()) {
            updateOrAddSection(HomeSection(
                title = "Series Destacadas",
                items = localTopSeries.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.LIBRARY_TOP_SERIES
            ))
        }
        if (localRecommendedMovies.isNotEmpty()) {
            updateOrAddSection(HomeSection(
                title = "Películas Recomendadas",
                items = localRecommendedMovies.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.RECOMMENDED_MOVIES
            ))
        }
        // Fila para cada género popular de la biblioteca del usuario (hasta 4 géneros)
        val genreCandidates = watchedGenres.take(4)
        for (genre in genreCandidates) {
            // El DAO devuelve por nota descendente; eso repetía siempre los mismos clásicos.
            // Reordenamos con el mismo scoring compuesto del resto del feed (nota saneada +
            // recencia + metadatos + afinidad de género) para que la fila respire variedad.
            val genreItems = runCatching { mediaRepository.getItemsByGenre(genre) }
                .getOrDefault(emptyList())
                .filterPersonalizedForHeader(listOf(genre))
            val unplayedGenre = genreItems.filter { !it.isPlayed }
            val valid = if (unplayedGenre.size >= 5) unplayedGenre else genreItems
            if (valid.size >= 3) {
                updateOrAddSection(HomeSection(
                    title = "Recomendados de $genre",
                    items = valid.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                    kind = HomeSectionKind.GENRE
                ))
            }
        }

        // Emisión inmediata local: ya hay filas reales en pantalla.
        if (sections.isNotEmpty()) {
            emitFeed(sections, true)
            persistSnapshot(prefs, sections)
        }

        // =========================================================================
        // ACTUALIZACIÓN CONCURRENTE EN SEGUNDO PLANO (SUPERVISORSCOPE STREAMING)
        // Ninguna fuente bloquea a las demás: Jellyfin, TMDB Plataformas, Top 10 y Trakt
        // se consultan en paralelo y emiten de forma inmediata al UI al completar.
        // =========================================================================
        val effectiveTmdbKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
        val effectiveWatchRegion = prefs.watchRegion.takeIf { it.isNotBlank() } ?: ApiConfig.TMDB_WATCH_REGION
        // Referencias locales (instantáneas) para las secciones personalizadas ("Porque viste X").
        // Antes se rellenaban con sendas consultas remotas dentro de JOB 1, y el trabajo que las
        // usaba arrancaba antes de que llegasen: en frío la sección se quedaba sin referencia.
        var sharedResumeReference: JellyfinMediaEntity? = localContinueWatching.firstOrNull { it.tmdbId != null }
        var sharedRecentReference: JellyfinMediaEntity? = localRecentlyPlayed.firstOrNull { it.tmdbId != null }

        supervisorScope {
            // ---------------------------------------------------------------------
            // JOB 1: JELLYFIN SERVER (Continuar Viendo remoto, Añadidos y Catálogo)
            //
            // Las cuatro consultas van en paralelo a propósito: con un servidor lento, en serie la
            // última sección esperaba a que terminasen las tres anteriores (medido: "Continuar
            // Viendo" sola tardó ~108 s, así que "Añadido recientemente" no podía empezar antes).
            // Como `postSection` está serializado con un mutex y las secciones se ordenan por
            // prioridad al insertarlas, el orden de la Home no depende de cuál llegue antes.
            // ---------------------------------------------------------------------
            launch {
                val resumeItems = runCatching {
                    mediaRepository.getContinueWatching(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken).getOrNull() ?: emptyList()
                }.getOrDefault(emptyList())
                if (resumeItems.isNotEmpty()) {
                    sharedResumeReference = resumeItems.firstOrNull { it.tmdbId != null }
                    shownMediaIds.addAll(resumeItems.map { it.id })
                    postSection(
                        HomeSection(
                            title = "Continuar Viendo",
                            items = resumeItems.take(15).map { it.toOptimizedMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                            kind = HomeSectionKind.CONTINUE_WATCHING
                        )
                    )
                }
            }

            launch {
                val latestItems = runCatching {
                    mediaRepository.getLatest(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken).getOrNull() ?: emptyList()
                }.getOrDefault(emptyList()).filter {
                    it.type.equals("Movie", ignoreCase = true) || it.type.equals("Series", ignoreCase = true)
                }
                val mergedLatest = (latestItems + localLatest).distinctBy { it.id }.take(20)
                if (mergedLatest.isNotEmpty()) {
                    shownMediaIds.addAll(mergedLatest.map { it.id })
                    postSection(
                        HomeSection(
                            title = "Añadido recientemente",
                            items = mergedLatest.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                            kind = HomeSectionKind.RECENTLY_ADDED
                        )
                    )
                }
            }

            // Antes aquí se pedían los "vistos" al servidor solo para elegir la referencia de
            // "Porque viste X": era la consulta más cara del arranque (hasta ~37 s con el
            // servidor ocupado) y su dato ya está en Room (quick init y sync de fondo). Ahora la
            // referencia sale de `getRecentlyPlayedLocal`, así que esa consulta desaparece.
            //
            // Top servidor curado
            launch {
                val topMoviesServer = runCatching {
                    mediaRepository.getTopRatedMoviesServer(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken, limit = 20)
                }.getOrDefault(localTopMovies)
                val curatedMovies = if (prefs.tmdbApiKey.isNotBlank()) {
                    filterHighQualityMovies(topMoviesServer, prefs.tmdbApiKey).filterPersonalizedForHeader(watchedGenres)
                } else {
                    topMoviesServer.filterPersonalizedForHeader(watchedGenres)
                }
                if (curatedMovies.isNotEmpty()) {
                    shownMediaIds.addAll(curatedMovies.map { it.id })
                    postSection(
                        HomeSection(
                            title = "Grandes Producciones de tu colección",
                            items = curatedMovies.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                            kind = HomeSectionKind.LIBRARY_TOP_MOVIES
                        )
                    )
                }
            }

            launch {
                val topSeriesServer = runCatching {
                    mediaRepository.getTopRatedSeriesServer(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken, limit = 20)
                }.getOrDefault(localTopSeries)
                val curatedSeries = if (prefs.tmdbApiKey.isNotBlank()) {
                    filterHighQualitySeries(topSeriesServer, prefs.tmdbApiKey).filterPersonalizedForHeader(watchedGenres)
                } else {
                    topSeriesServer.filterPersonalizedForHeader(watchedGenres)
                }
                if (curatedSeries.isNotEmpty()) {
                    shownMediaIds.addAll(curatedSeries.map { it.id })
                    postSection(
                        HomeSection(
                            title = "Series Destacadas",
                            items = curatedSeries.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                            kind = HomeSectionKind.LIBRARY_TOP_SERIES
                        )
                    )
                }
            }

            // ---------------------------------------------------------------------
            // JOB 2: TOP 10 HOY (TMDB TRENDING DAY CON MEDALLAS 1-10)
            // ---------------------------------------------------------------------
            launch {
                // Top 10 SIEMPRE representa LO MÁS VISTO / TENDENCIA HOY (TMDB Trending Day):
                // Se consulta la API de tendencias diarias de TMDB (películas y series; el
                // endpoint es global, solo fija el idioma) y se filtra únicamente contra los
                // títulos que el usuario TIENE en su biblioteca.
                // El orden se respeta estrictamente por su puesto de tendencia real (#1, #2, #3...).
                // Índice persistente: si ya está construido, el Top 10 se lee de Room sin red.
                val indexedTrending = collectionIndexUseCase
                    ?.top(CollectionIndexUseCase.trendingKey(effectiveWatchRegion), 10)
                    .orEmpty()
                if (indexedTrending.size >= 3) {
                    val items = indexedTrending.mapIndexed { idx, entity ->
                        entity.toMediaItem(
                            prefs.jellyfinServerUrl,
                            prefs.jellyfinAccessToken,
                            MediaSource.TMDB_TRENDING
                        ).copy(rank = idx + 1)
                    }
                    postSection(
                        HomeSection(
                            title = "Tendencias hoy",
                            items = items,
                            badge = null,
                            isRanked = true,
                            kind = HomeSectionKind.TRENDING
                        ),
                        cacheKey = "tmdb_trending_day_pure",
                        itemIds = items.map { it.id }
                    )
                    return@launch
                }

                val trendingMatched = mutableListOf<JellyfinMediaEntity>()
                if (effectiveTmdbKey.isNotBlank()) {
                    runCatching {
                        withTimeoutOrNull(12000L) {
                            // Tendencia global + respaldo regional: el trending de TMDB es global
                            // y en bibliotecas locales puede no cruzar con nada. "Lo más popular
                            // en tu país" garantiza que el Top 10 tenga candidatos reales.
                            val (dailyTrending, regionalPopular) = coroutineScope {
                                val global = async {
                                    mediaRepository
                                        .getTmdbTrendingDay(apiKey = effectiveTmdbKey, maxPages = 10)
                                        .getOrDefault(emptyList())
                                }
                                val regional = async {
                                    mediaRepository
                                        .getTmdbPopularByRegion(
                                            apiKey = effectiveTmdbKey,
                                            region = effectiveWatchRegion,
                                            maxPages = 5
                                        )
                                        .getOrDefault(emptyList())
                                }
                                Pair(global.await(), regional.await())
                            }
                            val candidates = dailyTrending.take(60) + regionalPopular.take(60)
                            if (candidates.isNotEmpty()) {
                                trendingMatched.addAll(
                                    filterToLibraryUseCase.filterTmdbItems(
                                        tmdbItems = candidates,
                                        serverUrl = prefs.jellyfinServerUrl,
                                        userId = prefs.jellyfinUserId,
                                        token = prefs.jellyfinAccessToken,
                                        maxCandidates = 20
                                    )
                                )
                            }
                        }
                    }
                }

                val currentYear = java.time.Year.now().value
                val validTrending = trendingMatched
                    .distinctBy { it.id }
                    .filter {
                        val year = it.productionYear ?: 0
                        !it.tmdbId.isNullOrBlank() &&
                                year in 1950..currentYear &&
                                !it.isPlayed &&
                                it.playbackPositionTicks <= 0L
                    }

                if (validTrending.isNotEmpty()) {
                    val top10Items = validTrending.take(10).mapIndexed { idx, it ->
                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING).copy(rank = idx + 1)
                    }
                    val top10Section = HomeSection(
                        title = "Tendencias hoy",
                        items = top10Items,
                        badge = null,
                        isRanked = true,
                        kind = HomeSectionKind.TRENDING
                    )
                    postSection(
                        top10Section,
                        cacheKey = "tmdb_trending_day_pure",
                        itemIds = top10Items.map { it.id }
                    )
                }
            }

            // ---------------------------------------------------------------------
            // JOB 3: PLATAFORMAS OFICIALES DE STREAMING (CONCURRENTE CON STREAMING)
            // ---------------------------------------------------------------------
            launch {
                if (effectiveTmdbKey.isNotBlank() && prefs.isProUser) {
                    val selected = prefs.selectedPlatforms
                    val activeProviders = (if (selected.isEmpty()) {
                        SUPPORTED_PLATFORMS
                    } else {
                        SUPPORTED_PLATFORMS.filter { it.id in selected }
                    }).filter {
                        it.id != "trakt" &&
                            it.providerId.isNotBlank() &&
                            // Mismo gate que la fila de logos: no pedir catálogo de una
                            // plataforma que no opera en la región del usuario.
                            it.isAvailableIn(effectiveWatchRegion)
                    }

                    val semaphore = Semaphore(3)
                    activeProviders.forEach { platform ->
                        launch {
                            // Índice persistente: catálogo completo ya cruzado con la biblioteca.
                            // Si existe, la fila se pinta sin red y con todos los títulos.
                            val indexed = collectionIndexUseCase
                                ?.top(CollectionIndexUseCase.platformKey(platform.id), 20)
                                .orEmpty()
                            if (indexed.size >= 3) {
                                val platformItems = indexed.map {
                                    it.toMediaItem(
                                        prefs.jellyfinServerUrl,
                                        prefs.jellyfinAccessToken,
                                        MediaSource.TMDB_TRENDING
                                    )
                                }
                                postSection(
                                    HomeSection(
                                        title = "Populares en ${platform.name}",
                                        items = platformItems,
                                        badge = null,
                                        kind = HomeSectionKind.PLATFORM
                                    ),
                                    cacheKey = "platform_${platform.id}",
                                    itemIds = platformItems.map { it.id }
                                )
                                return@launch
                            }

                            semaphore.acquire()
                            try {
                                withTimeoutOrNull(12000L) {
                                    val result = mediaRepository.getTmdbByProvider(
                                        apiKey = effectiveTmdbKey,
                                        providerId = platform.providerId,
                                        region = effectiveWatchRegion,
                                        maxPages = 6
                                    )
                                    val rawTmdbItems = result.getOrDefault(emptyList())
                                    if (rawTmdbItems.isNotEmpty()) {
                                        val matched = filterToLibraryUseCase.filterTmdbItems(
                                            tmdbItems = rawTmdbItems,
                                            serverUrl = prefs.jellyfinServerUrl,
                                            userId = prefs.jellyfinUserId,
                                            token = prefs.jellyfinAccessToken,
                                            maxCandidates = 50
                                        )
                                        val uniqueMatched = matched.distinctBy { it.id }
                                        // Las filas de plataformas SOLO deben contener títulos reales de esa plataforma
                                        // (nunca rellenar con títulos genéricos de la biblioteca para evitar incongruencias como Super Mario en Netflix).
                                        if (uniqueMatched.size >= 3) {
                                            val platformItems = uniqueMatched.take(20).map {
                                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                                            }
                                            val platformTitle = "Populares en ${platform.name}"
                                            val platformSection = HomeSection(
                                                title = platformTitle,
                                                items = platformItems,
                                                badge = null,
                                                kind = HomeSectionKind.PLATFORM
                                            )
                                            postSection(
                                                platformSection,
                                                cacheKey = "platform_${platform.id}",
                                                itemIds = platformItems.map { it.id }
                                            )
                                        }
                                    }
                                }
                            } finally {
                                semaphore.release()
                            }
                        }
                    }

                    // Top 10 OFICIAL de Netflix (semanal, por país, TSV de Tudum).
                    val netflixFilmItems = collectionIndexUseCase
                        ?.top(CollectionIndexUseCase.netflixTop10FilmKey(effectiveWatchRegion), 10)
                        .orEmpty()
                    if (netflixFilmItems.isNotEmpty()) {
                        val items = netflixFilmItems.mapIndexed { idx, entity ->
                            entity.toMediaItem(
                                prefs.jellyfinServerUrl,
                                prefs.jellyfinAccessToken,
                                MediaSource.TMDB_TRENDING
                            ).copy(rank = idx + 1)
                        }
                        postSection(
                            HomeSection(
                                title = "Top 10 Netflix · Películas",
                                items = items,
                                badge = null,
                                isRanked = true,
                                kind = HomeSectionKind.PLATFORM
                            ),
                            cacheKey = "netflix_top10_films",
                            itemIds = items.map { it.id }
                        )
                    }
                    val netflixTvItems = collectionIndexUseCase
                        ?.top(CollectionIndexUseCase.netflixTop10TvKey(effectiveWatchRegion), 10)
                        .orEmpty()
                    if (netflixTvItems.isNotEmpty()) {
                        val items = netflixTvItems.mapIndexed { idx, entity ->
                            entity.toMediaItem(
                                prefs.jellyfinServerUrl,
                                prefs.jellyfinAccessToken,
                                MediaSource.TMDB_TRENDING
                            ).copy(rank = idx + 1)
                        }
                        postSection(
                            HomeSection(
                                title = "Top 10 Netflix · Series",
                                items = items,
                                badge = null,
                                isRanked = true,
                                kind = HomeSectionKind.PLATFORM
                            ),
                            cacheKey = "netflix_top10_tv",
                            itemIds = items.map { it.id }
                        )
                    }
                }
            }

            // ---------------------------------------------------------------------
            // JOB 4: TRAKT.TV (EN PARALELO DESDE EL PRIMER MILISEGUNDO)
            // ---------------------------------------------------------------------
            launch {
                val traktClientId = prefs.traktClientId.ifBlank { DEFAULT_TRAKT_CLIENT_ID }
                if (prefs.traktAccessToken.isNotBlank()) {
                    // Recommendations
                    launch {
                        runCatching {
                            withTimeoutOrNull(4000L) {
                                val traktRecsResult = mediaRepository.getTraktRecommendations(
                                    traktToken = prefs.traktAccessToken,
                                    clientId = traktClientId
                                )
                                val rawTraktRecs = traktRecsResult.getOrDefault(emptyList()).take(40)
                                val matchedTraktRecs = filterToLibraryUseCase.filterTraktItems(
                                    traktItems = rawTraktRecs,
                                    serverUrl = prefs.jellyfinServerUrl,
                                    userId = prefs.jellyfinUserId,
                                    token = prefs.jellyfinAccessToken,
                                    maxCandidates = 20
                                )
                                if (matchedTraktRecs.isNotEmpty()) {
                                    val traktRecSection = HomeSection(
                                        title = "Recomendado por la comunidad Trakt",
                                        items = matchedTraktRecs.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION) },
                                        badge = null,
                                        kind = HomeSectionKind.TRAKT
                                    )
                                    postSection(traktRecSection, cacheKey = "trakt_recs", itemIds = matchedTraktRecs.map { it.id })
                                }
                            }
                        }
                    }

                    // Watchlist
                    launch {
                        runCatching {
                            withTimeoutOrNull(4000L) {
                                val watchlistResult = mediaRepository.getTraktWatchlist(
                                    traktToken = prefs.traktAccessToken,
                                    clientId = traktClientId
                                )
                                val rawWatchlist = watchlistResult.getOrDefault(emptyList()).take(40)
                                val matchedWatchlist = filterToLibraryUseCase.filterTraktItems(
                                    traktItems = rawWatchlist,
                                    serverUrl = prefs.jellyfinServerUrl,
                                    userId = prefs.jellyfinUserId,
                                    token = prefs.jellyfinAccessToken,
                                    maxCandidates = 20
                                )
                                if (matchedWatchlist.isNotEmpty()) {
                                    val watchlistSection = HomeSection(
                                        title = "Tu Watchlist de Trakt.tv",
                                        items = matchedWatchlist.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST) },
                                        badge = null,
                                        kind = HomeSectionKind.TRAKT
                                    )
                                    postSection(watchlistSection, cacheKey = "trakt_watchlist", itemIds = matchedWatchlist.map { it.id })
                                }
                            }
                        }
                    }

                    // User Lists (cada lista de usuario emite en cuanto responde si tiene items en Jellyfin)
                    launch {
                        runCatching {
                            withTimeoutOrNull(5000L) {
                                val userListsResult = mediaRepository.getTraktUserLists(
                                    traktToken = prefs.traktAccessToken,
                                    clientId = traktClientId
                                )
                                val userLists = userListsResult.getOrDefault(emptyList())
                                    .filterNot { it.isUserHidden(prefs.hiddenTraktLists) }
                                    .take(5)

                                userLists.forEach { list ->
                                    launch {
                                        withTimeoutOrNull(3500L) {
                                            val listId = list.ids.trakt?.toString() ?: list.ids.slug ?: return@withTimeoutOrNull
                                            val username = list.user?.username
                                            val listItemsResult = mediaRepository.getTraktCustomListItems(
                                                traktToken = prefs.traktAccessToken,
                                                clientId = traktClientId,
                                                listId = listId,
                                                username = username
                                            )
                                            val rawListItems = listItemsResult.getOrDefault(emptyList()).take(50)
                                            if (rawListItems.isEmpty()) return@withTimeoutOrNull

                                            val matchedListItems = filterToLibraryUseCase.filterTraktItems(
                                                traktItems = rawListItems,
                                                serverUrl = prefs.jellyfinServerUrl,
                                                userId = prefs.jellyfinUserId,
                                                token = prefs.jellyfinAccessToken,
                                                maxCandidates = 30
                                            )

                                            if (matchedListItems.isNotEmpty()) {
                                                val listTitle = "Trakt: ${list.name}"
                                                val traktListSection = HomeSection(
                                                    title = listTitle,
                                                    items = matchedListItems.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST) },
                                                    badge = "TRAKT LISTA",
                                                    kind = HomeSectionKind.TRAKT
                                                )
                                                val listKey = "trakt_list_${list.ids.trakt ?: list.ids.slug ?: list.name.hashCode()}"
                                                postSection(traktListSection, cacheKey = listKey, itemIds = matchedListItems.map { it.id })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Trending
                    launch {
                        runCatching {
                            withTimeoutOrNull(4000L) {
                                val trendingResult = mediaRepository.getTraktTrending(clientId = traktClientId)
                                val rawTrending = trendingResult.getOrDefault(emptyList()).take(40)
                                val matchedTrending = filterToLibraryUseCase.filterTraktItems(
                                    traktItems = rawTrending,
                                    serverUrl = prefs.jellyfinServerUrl,
                                    userId = prefs.jellyfinUserId,
                                    token = prefs.jellyfinAccessToken,
                                    maxCandidates = 20
                                )
                                if (matchedTrending.isNotEmpty()) {
                                    val traktTrendingSection = HomeSection(
                                        title = "Tendencias en Trakt.tv",
                                        items = matchedTrending.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION) },
                                        badge = null,
                                        kind = HomeSectionKind.TRAKT
                                    )
                                    postSection(traktTrendingSection, cacheKey = "trakt_trending", itemIds = matchedTrending.map { it.id })
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------------
            // JOB 5: RECOMENDACIÓN PERSONALIZADA ("PORQUE VISTE X")
            // ---------------------------------------------------------------------
            launch {
                val refItem = sharedResumeReference ?: sharedRecentReference
                // La clave efectiva (la del usuario o la que trae la app): antes se exigía que el
                // usuario hubiese guardado la suya en Ajustes, así que la sección no aparecía nunca.
                if (refItem != null && effectiveTmdbKey.isNotBlank()) {
                    runCatching {
                        withTimeoutOrNull(3500L) {
                            val tmdbIdLong = refItem.tmdbId?.toLongOrNull() ?: return@withTimeoutOrNull
                            val isTv = refItem.type.equals("Series", ignoreCase = true) || refItem.type.equals("Episode", ignoreCase = true)
                            val recsResult = mediaRepository.getTmdbRecommendations(apiKey = effectiveTmdbKey, tmdbId = tmdbIdLong, isTv = isTv).getOrDefault(emptyList())
                            if (recsResult.isNotEmpty()) {
                                val matchedRecs = filterToLibraryUseCase.filterTmdbItems(
                                    tmdbItems = recsResult,
                                    serverUrl = prefs.jellyfinServerUrl,
                                    userId = prefs.jellyfinUserId,
                                    token = prefs.jellyfinAccessToken,
                                    maxCandidates = 25
                                ).filter { it.id != refItem.id }
                                if (matchedRecs.size >= 3) {
                                    val section = HomeSection(
                                        title = "Porque viste ${refItem.title}",
                                        items = matchedRecs.take(15).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_RECOMMENDATION) },
                                        kind = HomeSectionKind.BECAUSE_YOU_WATCHED
                                    )
                                    postSection(section, cacheKey = "tmdb_because_watched", itemIds = matchedRecs.map { it.id })
                                }
                            }
                        }
                    }
                }
            }


        }

        // Emisión final garantizada cuando todos los trabajos en paralelo concluyen
        feedMutex.withLock {
            emitFeed(sections, true)
        }
        // Snapshot final con todas las filas (locales + externas) para el próximo arranque.
        if (sections.isNotEmpty()) {
            persistSnapshot(prefs, sections)
        }

        if (sections.isEmpty()) {
            send(emptyList())
        }
    }

    /**
     * Diversifica una lista de items por década de producción.
     *
     * En vez de devolver los N primeros (que pueden ser todos de la misma época),
     * agrupa por decade = productionYear / 10, toma [maxPerDecade] de cada grupo,
     * y los intercala para que la fila muestre variedad de épocas.
     *
     * Ejemplo con maxPerDecade=2, totalLimit=15, género "Comedia":
     *   Grupo 2020s: Película A, Película B
     *   Grupo 2010s: Película C, Película D
     *   Grupo 1990s: Película E, Película F
     *   Grupo 1970s: Alfredo Landa 1, Alfredo Landa 2  ← solo 2, no 15
     *   Intercalado: A, C, E, Landa1, B, D, F, Landa2
     */
    private fun diversifyByDecade(
        items: List<JellyfinMediaEntity>,
        maxPerDecade: Int = 2,
        totalLimit: Int = 15
    ): List<JellyfinMediaEntity> {
        // Agrupar por decade (year / 10). Items sin año van a un grupo propio al final.
        val byDecade: Map<Int, List<JellyfinMediaEntity>> = items
            .groupBy { it.productionYear?.div(10) ?: -1 }
            .mapValues { (_, group) ->
                // Dentro de cada década: ordenar por nota descendente, tomar máx maxPerDecade
                group.sortedByDescending { it.communityRating ?: 0f }.take(maxPerDecade)
            }

        // Ordenar los grupos de más reciente a más antiguo
        val sortedDecades = byDecade.keys.sortedDescending()
        val buckets = sortedDecades.map { byDecade[it]!! }

        // Intercalar round-robin entre grupos de décadas
        val result = mutableListOf<JellyfinMediaEntity>()
        val maxRound = buckets.maxOfOrNull { it.size } ?: 0
        outer@ for (round in 0 until maxRound) {
            for (bucket in buckets) {
                if (round < bucket.size) {
                    result.add(bucket[round])
                    if (result.size >= totalLimit) break@outer
                }
            }
        }
        return result
    }

    /**
     * Filtra las películas del servidor contra TMDB para descartar títulos con nota
     * alta pero pocos votos (ej. película de nicho con 10 votos familiares y nota 10).
     * Pondera la nota junto con el volumen de votos para dar prioridad a verdaderas producciones.
     */
    private suspend fun filterHighQualityMovies(
        movies: List<JellyfinMediaEntity>,
        tmdbApiKey: String
    ): List<JellyfinMediaEntity> {
        val validMovies = movies.filter { !it.backdropImageTag.isNullOrEmpty() || !it.overview.isNullOrBlank() }
        val candidates = validMovies.filter { it.tmdbId != null }

        if (candidates.isEmpty()) {
            return validMovies.filter {
                val r = it.communityRating ?: 0f
                r in 7.0f..9.5f
            }.take(20)
        }

        val checked = coroutineScope {
            candidates.take(6).map { movie ->
                async {
                    withTimeoutOrNull(800L) {
                        val tmdbId = movie.tmdbId?.toLongOrNull() ?: return@withTimeoutOrNull null
                        val stats = mediaRepository.getTmdbVoteStats(tmdbApiKey, tmdbId, isTv = false)
                            ?: return@withTimeoutOrNull null
                        Triple(movie.copy(communityRating = stats.voteAverage), stats.voteCount, stats.voteAverage)
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val passed = checked
            .filter { (_, count, avg) -> count >= 100 && avg >= 6.5f }
            .sortedByDescending { (_, count, avg) -> avg * 10f + min(count, 5000) / 500f }
            .map { it.first }

        val passedIds = passed.map { it.id }.toSet()
        val remainder = validMovies.filter { it.id !in passedIds }
            .sortedByDescending { sanitizeRating(it.communityRating, !it.overview.isNullOrBlank() && it.overview.length > 30) }
        return (passed + remainder).take(20)
    }

    /**
     * Filtra las series del servidor contra TMDB para descartar títulos con nota
     * adulterada o inflada por poquísimos votos (ej. documentales o telenovelas con 1 voto de 10).
     * Exige una masa crítica de votos (mínimo 50 votos en TMDB y nota >= 6.8),
     * pondera por volumen de votos para dar prioridad a series de prestigio contrastado,
     * y reemplaza la nota local adulterada por la nota real ponderada de TMDB.
     */
    private suspend fun filterHighQualitySeries(
        series: List<JellyfinMediaEntity>,
        tmdbApiKey: String
    ): List<JellyfinMediaEntity> {
        val validSeries = series.filter { !it.backdropImageTag.isNullOrEmpty() || !it.overview.isNullOrBlank() }
        val candidates = validSeries.filter { it.tmdbId != null }

        if (candidates.isEmpty()) {
            return validSeries
                .sortedByDescending { sanitizeRating(it.communityRating, !it.overview.isNullOrBlank() && it.overview.length > 30) }
                .take(20)
        }

        val checked = coroutineScope {
            candidates.take(6).map { s ->
                async {
                    withTimeoutOrNull(800L) {
                        val tmdbId = s.tmdbId?.toLongOrNull() ?: return@withTimeoutOrNull null
                        val stats = mediaRepository.getTmdbVoteStats(tmdbApiKey, tmdbId, isTv = true)
                            ?: return@withTimeoutOrNull null
                        Triple(s.copy(communityRating = stats.voteAverage), stats.voteCount, stats.voteAverage)
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val passed = checked
            .filter { (_, count, avg) -> count >= 30 && avg >= 6.5f }
            .sortedByDescending { (_, count, avg) -> avg * 10f + min(count, 5000) / 500f }
            .map { it.first }

        val passedIds = passed.map { it.id }.toSet()
        val remainder = validSeries.filter { it.id !in passedIds }
            .sortedByDescending { sanitizeRating(it.communityRating, !it.overview.isNullOrBlank() && it.overview.length > 30) }
        return (passed + remainder).take(20)
    }

    private fun sanitizeRating(rating: Float?, hasOverview: Boolean): Float {
        if (rating == null || rating <= 0f) return 6.0f
        return when {
            rating >= 9.8f -> 7.2f
            rating in 8.85f..9.5f && !hasOverview -> 6.8f
            rating >= 8.8f -> 7.4f + (rating - 8.8f) * 0.15f
            else -> rating
        }
    }

    private fun List<JellyfinMediaEntity>.filterPersonalizedForHeader(watchedGenres: List<String>): List<JellyfinMediaEntity> {
        val userGenresLower = watchedGenres.map { it.lowercase().trim() }
        val unplayed = this.filter { !it.isPlayed && it.playbackPositionTicks <= 0L }
        val withBackdrop = unplayed.filter { !it.backdropImageTag.isNullOrBlank() }
        val candidates = when {
            withBackdrop.size >= 5 -> withBackdrop
            unplayed.size >= 5 -> unplayed
            else -> this
        }

        return candidates.sortedByDescending { entity ->
            var score = sanitizeRating(entity.communityRating, !entity.overview.isNullOrBlank() && entity.overview.length > 30)
            val year = entity.productionYear ?: 1990
            val genres = entity.genres?.split(",", ";")?.map { it.lowercase().trim() } ?: emptyList()

            if (userGenresLower.isNotEmpty() && genres.any { it in userGenresLower }) {
                score += 1.5f
            }
            if (year >= 2015) {
                score += 0.8f
            } else if (year >= 2000) {
                score += 0.4f
            }
            score
        }
    }

    private fun JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String, source: MediaSource): MediaItem =
        toOptimizedMediaItem(baseUrl, token, source)

    // =========================================================================
    // PERSISTENT SECTION CACHE HELPERS
    // =========================================================================

    /**
     * Persiste una sección del home feed en Room para que sobreviva a process death.
     * Solo guarda sectionKey, título, IDs matcheados y metadatos (badge, isRanked).
     * Los datos completos de cada item ya están en la tabla jellyfin_media.
     */
    private suspend fun persistSection(
        sectionKey: String,
        section: HomeSection,
        itemIds: List<String>
    ) {
        val dao = homeSectionCacheDao ?: return
        if (itemIds.isEmpty()) return
        runCatching {
            val idsJson = itemIds.joinToString(",")
            dao.upsert(
                HomeSectionCacheEntity(
                    sectionKey = sectionKey,
                    title = section.title,
                    itemIds = idsJson,
                    badge = section.badge,
                    isRanked = section.isRanked,
                    payload = runCatching { MediaItemSnapshotCodec.encode(section.items) }.getOrDefault("")
                )
            )
        }
    }

    /**
     * Guarda el snapshot completo (incluidas las filas locales) para pintar la Home al instante
     * en el siguiente arranque, sin depender de la caché estática del proceso.
     */
    private suspend fun persistSnapshot(
        prefs: UserPreferences,
        sections: List<HomeSection>
    ) {
        val dao = homeSectionCacheDao ?: return
        if (sections.isEmpty()) return
        val version = "${prefs.jellyfinLastSync}|${runCatching { mediaRepository.getLocalCount() }.getOrDefault(0)}"
        // El fichero es la vía rápida de arranque; Room queda como respaldo estructurado.
        withContext(Dispatchers.IO) { snapshotFileStore?.write(sections) }
        memorySnapshot = sections.toList()
        memorySnapshotAt = System.currentTimeMillis()
        runCatching {
            dao.clearAll()
            sections.forEachIndexed { index, section ->
                val ids = section.items.map { it.id }.distinct()
                if (ids.isEmpty()) return@forEachIndexed
                dao.upsert(
                    HomeSectionCacheEntity(
                        sectionKey = "snapshot_${index}_${section.title.hashCode()}",
                        title = section.title,
                        itemIds = ids.joinToString(","),
                        badge = section.badge,
                        isRanked = section.isRanked,
                        sortOrder = index,
                        catalogVersion = version,
                        payload = runCatching { MediaItemSnapshotCodec.encode(section.items) }.getOrDefault("")
                    )
                )
            }
        }
    }

    /** Lectura anticipada del snapshot para que el ViewModel pinte antes de arrancar el feed. */
    suspend fun restoreSnapshot(prefs: UserPreferences): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        restoreCachedSections(prefs, mutableSetOf(), sections, previewOnly = true)
        return sections
    }

    /**
     * Restaura secciones cacheadas desde Room (< 100ms).
     * Reconstruye todas las secciones con una sola consulta batch a jellyfin_media.
     * Solo restaura secciones cuyo TTL stale no haya expirado (máx 48 horas).
     */
    private suspend fun restoreCachedSections(
        prefs: UserPreferences,
        shownMediaIds: MutableSet<String>,
        sections: MutableList<HomeSection>,
        previewOnly: Boolean = false
    ): Boolean {
        // Vía rápida: un único fichero, sin abrir Room ni consultar IDs aleatorios.
        val fileSections = withContext(Dispatchers.IO) {
            snapshotFileStore?.read(if (previewOnly) 4 else Int.MAX_VALUE).orEmpty()
        }
        if (fileSections.isNotEmpty()) {
            fileSections.forEach { section ->
                if (sections.none { it.title == section.title }) {
                    sections.add(section)
                    shownMediaIds.addAll(section.items.map { it.id })
                }
            }
            if (!previewOnly) {
                memorySnapshot = sections.toList()
                memorySnapshotAt = System.currentTimeMillis()
            }
            return sections.isNotEmpty()
        }

        val memo = memorySnapshot
        if (memo != null && System.currentTimeMillis() - memorySnapshotAt < 30_000L) {
            memo.forEach { section ->
                if (sections.none { it.title == section.title }) {
                    sections.add(section)
                    shownMediaIds.addAll(section.items.map { it.id })
                }
            }
            return sections.isNotEmpty()
        }

        val dao = homeSectionCacheDao ?: return false
        val cached = runCatching { dao.getAllSections() }.getOrDefault(emptyList())
        if (cached.isEmpty()) return false

        val rawPayloadEntries = mutableListOf<Pair<HomeSectionCacheEntity, String>>()
        val idFallbackEntries = mutableListOf<Pair<HomeSectionCacheEntity, List<String>>>()
        for (entry in cached) {
            // Purgar claves antiguas de Top 10 y Prime Video para recalcular con los nuevos proveedores
            if (entry.sectionKey.startsWith("tmdb_trending_day") || entry.sectionKey.contains("prime")) {
                runCatching { dao.delete(entry.sectionKey) }
                continue
            }
            if (entry.isExpired(HomeSectionCacheEntity.TTL_MAX_STALE)) {
                runCatching { dao.delete(entry.sectionKey) }
                continue
            }
            if (sections.any { it.title == entry.title }) continue

            val ids = entry.itemIds.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (entry.payload.isNotBlank()) {
                rawPayloadEntries.add(entry to entry.payload)
            } else if (ids.isNotEmpty()) {
                idFallbackEntries.add(entry to ids)
            }
        }
        // Decodificar todos los payloads en un único bloque (evita 15 saltos de contexto y
        // aprovecha mejor el JIT en la TV).
        val payloadEntries = withContext(Dispatchers.Default) {
            rawPayloadEntries.mapNotNull { (entry, payload) ->
                runCatching { entry to MediaItemSnapshotCodec.decode(payload) }
                    .onFailure { android.util.Log.e("HomeVM", "codec fallo ${entry.title}", it) }
                    .getOrNull()
            }
        }
        if (payloadEntries.isEmpty() && idFallbackEntries.isEmpty()) return false

        // Solo las filas antiguas sin payload necesitan reconstruirse desde jellyfin_media.
        val allIds = idFallbackEntries.flatMap { it.second }.distinct()
        val idMap: Map<String, JellyfinMediaEntity> = if (allIds.isEmpty()) {
            emptyMap()
        } else {
            runCatching {
                mediaRepository.getItemsByIds(allIds).associateBy { it.id }
            }.getOrDefault(emptyMap())
        }

        fun restoreSection(entry: HomeSectionCacheEntity, items: List<MediaItem>) {
            val uniqueItems = items.distinctBy { it.id }
            if (uniqueItems.isEmpty()) return
            val isSnapshot = entry.sectionKey.startsWith("snapshot_")
            if (entry.isRanked && uniqueItems.size < 10) return
            if (!isSnapshot && !entry.isRanked && uniqueItems.size < 5) return
            shownMediaIds.addAll(uniqueItems.map { it.id })
            val restoredSection = HomeSection(
                title = entry.title,
                items = uniqueItems,
                badge = entry.badge,
                isRanked = entry.isRanked
            )
            val newPriority = sectionPriority(restoredSection)
            val insertIdx = sections.indexOfFirst { sectionPriority(it) > newPriority }
            if (insertIdx != -1) sections.add(insertIdx, restoredSection) else sections.add(restoredSection)
        }

        payloadEntries.forEach { (entry, items) -> restoreSection(entry, items) }

        for ((entry, ids) in idFallbackEntries) {
            val entities = ids.mapNotNull { idMap[it] }.distinctBy { it.id }
            if (entities.isEmpty()) continue
            val source = when {
                entry.sectionKey.startsWith("platform_") -> MediaSource.TMDB_TRENDING
                entry.sectionKey.startsWith("tmdb_trending") -> MediaSource.TMDB_TRENDING
                entry.sectionKey.startsWith("tmdb_because") -> MediaSource.TMDB_RECOMMENDATION
                entry.sectionKey == "trakt_recs" -> MediaSource.TRAKT_RECOMMENDATION
                entry.sectionKey == "trakt_trending" -> MediaSource.TRAKT_RECOMMENDATION
                entry.sectionKey == "trakt_watchlist" -> MediaSource.TRAKT_WATCHLIST
                entry.title.startsWith("Populares en ") || entry.title.startsWith("Top 10") -> MediaSource.TMDB_TRENDING
                entry.title.startsWith("Porque viste") -> MediaSource.TMDB_RECOMMENDATION
                entry.title.startsWith("Trakt:") ||
                    entry.title == "Tu Watchlist de Trakt.tv" ||
                    entry.title == "Recomendado por la comunidad Trakt" ||
                    entry.title == "Tendencias en Trakt.tv" -> MediaSource.TRAKT_RECOMMENDATION
                else -> MediaSource.JELLYFIN
            }
            val items = if (entry.isRanked) {
                entities.mapIndexed { idx, entity ->
                    entity.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, source).copy(rank = idx + 1)
                }
            } else {
                entities.map { entity ->
                    entity.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, source)
                }
            }
            restoreSection(entry, items)
        }
        return sections.isNotEmpty()
    }


}
