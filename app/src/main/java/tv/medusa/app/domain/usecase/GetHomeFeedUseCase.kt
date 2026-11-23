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
import tv.medusa.app.domain.model.HomeSectionLayout
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
            title == "Añadido recientemente" -> 20
            title.startsWith("Porque viste") -> 30
            title.startsWith("Para esta") || title.startsWith("Viernes de") ||
            title.startsWith("Sábado de") || title.startsWith("Domingo de") ||
            title.startsWith("Cine para") || title.startsWith("Tarde de") ||
            title.startsWith("Noche de") || title.startsWith("Grandes historias para") -> 35
            title == "Sagas y Colecciones" -> 38
            // Filas de plataformas se generan como "Populares en X"
            title.startsWith("Populares en ") -> 40
            title.startsWith("Trakt:") -> 50
            title == "Tu Watchlist de Trakt.tv" -> 51
            title == "Recomendado por la comunidad Trakt" -> 52
            title == "Tendencias en Trakt.tv" -> 53
            title == "Joyas de tu videoteca" || title.contains("Joyas") -> 60
            title == "Grandes Producciones de tu colección" -> 70
            title == "Series Destacadas" -> 80
            title == "Películas Recomendadas" -> 90
            title == "Para volver a disfrutar" || title.startsWith("Para volver a") -> 95
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
            HomeSectionKind.TIME_CONTEXTUAL -> 35
            HomeSectionKind.FRANCHISES -> 38
            HomeSectionKind.PLATFORM -> 40
            HomeSectionKind.TRAKT -> 50
            HomeSectionKind.HIDDEN_GEMS -> 60
            HomeSectionKind.LIBRARY_TOP_MOVIES -> 70
            HomeSectionKind.LIBRARY_TOP_SERIES -> 80
            HomeSectionKind.RECOMMENDED_MOVIES -> 90
            HomeSectionKind.REWATCH -> 95
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
            val isPlatform = cleanSection.kind == HomeSectionKind.PLATFORM || cleanSection.title.startsWith("Populares en ")
            val idx = sections.indexOfFirst { it.title == cleanSection.title }
            if (idx != -1) {
                sections[idx] = cleanSection
            } else {
                if (isPlatform && sections.any { it.kind == HomeSectionKind.PLATFORM || it.title.startsWith("Populares en ") }) {
                    // Mantener solo 1 plataforma destacada en la portada principal para evitar saturación estilo directorio
                    return
                }
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
        var localBoxSets: List<JellyfinMediaEntity> = emptyList()
        var localHiddenGems: List<JellyfinMediaEntity> = emptyList()
        var localContextualItems: List<JellyfinMediaEntity> = emptyList()

        val dayOfYear = java.time.LocalDate.now().dayOfYear
        val nowTime = java.time.LocalDateTime.now()
        val currentHour = nowTime.hour
        val currentDay = nowTime.dayOfWeek
        val (contextTitle, contextGenre) = when {
            (currentDay == java.time.DayOfWeek.FRIDAY || currentDay == java.time.DayOfWeek.SATURDAY) && (currentHour >= 20 || currentHour < 4) -> {
                Pair("Noche de cine: Adrenalina y Tensión", "Acción")
            }
            (currentDay == java.time.DayOfWeek.SATURDAY || currentDay == java.time.DayOfWeek.SUNDAY) && currentHour in 12..19 -> {
                Pair("Tarde de fin de semana: Animación y Familia", "Animación")
            }
            (currentDay != java.time.DayOfWeek.FRIDAY && currentDay != java.time.DayOfWeek.SATURDAY) && (currentHour >= 21 || currentHour < 4) -> {
                Pair("Cine para terminar el día", "Comedia")
            }
            else -> {
                Pair("Grandes historias para hoy", "Drama")
            }
        }

        coroutineScope {
            val continueWatchingJob = async { runCatching { mediaRepository.getContinueWatchingLocal(20) }.getOrDefault(emptyList()) }
            val recentlyPlayedJob = async { runCatching { mediaRepository.getRecentlyPlayedLocal(20) }.getOrDefault(emptyList()) }
            val latestJob = async { runCatching { mediaRepository.getLatestLocal(prefs.jellyfinServerUrl, prefs.jellyfinUserId, 20) }.getOrDefault(emptyList()) }
            val topMoviesJob = async { runCatching { mediaRepository.getTopMoviesLocal(40) }.getOrDefault(emptyList()) }
            val topSeriesJob = async { runCatching { mediaRepository.getTopSeriesLocal(40) }.getOrDefault(emptyList()) }
            val boxSetsJob = async { runCatching { mediaRepository.getBoxSetsLocal(20) }.getOrDefault(emptyList()) }
            val hiddenGemsJob = async { runCatching { mediaRepository.getHiddenGemsLocal(40) }.getOrDefault(emptyList()) }
            val contextualJob = async { runCatching { mediaRepository.getItemsByGenre(contextGenre) }.getOrDefault(emptyList()) }
            val genresJob = async { runCatching { mediaRepository.getMostWatchedGenres() }.getOrDefault(emptyList()) }
            val newReleasesJob = async { runCatching { mediaRepository.getNewReleasesLocal(20) }.getOrDefault(emptyList()) }
            val recommendedJob = async {
                val genres = genresJob.await()
                runCatching {
                    mediaRepository.getRecommendedMovies(excludeIds = emptySet(), limit = 40, genres = genres)
                }.getOrDefault(emptyList())
            }

            localContinueWatching = continueWatchingJob.await()
            localRecentlyPlayed = recentlyPlayedJob.await()
            localLatest = latestJob.await()
            localTopMovies = topMoviesJob.await()
            localTopSeries = topSeriesJob.await()
            localBoxSets = boxSetsJob.await()
            localHiddenGems = hiddenGemsJob.await()
            localContextualItems = contextualJob.await()
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

        // Fila contextual por hora del día y día de la semana
        val unplayedContext = localContextualItems.filter { !it.isPlayed && it.playbackPositionTicks <= 0L }
        val validContext = if (unplayedContext.size >= 5) unplayedContext else localContextualItems
        if (validContext.size >= 3) {
            val shuffledContext = validContext.shuffled(kotlin.random.Random(dayOfYear + currentHour)).take(15)
            updateOrAddSection(HomeSection(
                title = contextTitle,
                items = shuffledContext.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.TIME_CONTEXTUAL
            ))
        }

        // Sagas y Colecciones (Formato apaisado 16:9 con ClearLogo integrado)
        if (localBoxSets.isNotEmpty()) {
            val boxSetItems = localBoxSets.take(15).map {
                it.toOptimizedMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
            }
            updateOrAddSection(HomeSection(
                title = "Sagas y Colecciones",
                items = boxSetItems,
                kind = HomeSectionKind.FRANCHISES,
                layout = HomeSectionLayout.LANDSCAPE
            ))
        }

        // Joyas ocultas de la videoteca (sin reproducir, con excelente valoración)
        if (localHiddenGems.isNotEmpty()) {
            val shuffledHiddenGems = localHiddenGems.shuffled(kotlin.random.Random(dayOfYear + 33)).take(20)
            updateOrAddSection(HomeSection(
                title = "Joyas de tu videoteca",
                items = shuffledHiddenGems.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                badge = null,
                kind = HomeSectionKind.HIDDEN_GEMS
            ))
        }

        // Barajado diario (Shuffle de la Home) sobre el Top Bayesiano
        if (localTopMovies.isNotEmpty()) {
            val shuffledTopMovies = localTopMovies.shuffled(kotlin.random.Random(dayOfYear)).take(20)
            updateOrAddSection(HomeSection(
                title = "Grandes Producciones de tu colección",
                items = shuffledTopMovies.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.LIBRARY_TOP_MOVIES
            ))
        }
        if (localTopSeries.isNotEmpty()) {
            val shuffledTopSeries = localTopSeries.shuffled(kotlin.random.Random(dayOfYear + 11)).take(20)
            updateOrAddSection(HomeSection(
                title = "Series Destacadas",
                items = shuffledTopSeries.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.LIBRARY_TOP_SERIES
            ))
        }
        if (localRecommendedMovies.isNotEmpty()) {
            val shuffledRecommended = localRecommendedMovies.shuffled(kotlin.random.Random(dayOfYear + 22)).take(20)
            updateOrAddSection(HomeSection(
                title = "Películas Recomendadas",
                items = shuffledRecommended.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
                kind = HomeSectionKind.RECOMMENDED_MOVIES
            ))
        }
        // Fila para cada género popular de la biblioteca del usuario (hasta 4 géneros con shuffle diario)
        val genreCandidates = watchedGenres.take(4)
        for (genre in genreCandidates) {
            val genreItems = runCatching { mediaRepository.getItemsByGenre(genre) }
                .getOrDefault(emptyList())
            val unplayedGenre = genreItems.filter { !it.isPlayed && it.playbackPositionTicks <= 0L }
            val valid = if (unplayedGenre.size >= 5) unplayedGenre else genreItems
            if (valid.size >= 3) {
                val shuffledGenre = valid.shuffled(kotlin.random.Random(dayOfYear + genre.hashCode())).take(20)
                updateOrAddSection(HomeSection(
                    title = "Recomendados de $genre",
                    items = shuffledGenre.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) },
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

            // Las secciones de "Grandes Producciones de tu colección" y "Series Destacadas"
            // ya se calcularon con la fórmula Bayesiana ponderada con TMDB en Room DB.
            shownMediaIds.addAll(localTopMovies.take(20).map { it.id })
            shownMediaIds.addAll(localTopSeries.take(20).map { it.id })

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
                            title = "Top 10 hoy",
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
                        title = "Top 10 hoy",
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
                if (effectiveTmdbKey.isNotBlank()) {
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

                    // Para una experiencia ágil tipo Netflix, seleccionamos 1 plataforma destacada
                    // en la portada (rotativa diaria si no hay selección manual), evitando saturar con 9 filas;
                    // el usuario puede acceder a todas las plataformas en cualquier momento desde los botones de marca superiores.
                    val activeProvidersList = activeProviders.toList()
                    val featuredProviders = if (activeProvidersList.isNotEmpty()) {
                        val selectedIndex = kotlin.math.abs(dayOfYear % activeProvidersList.size)
                        listOf(activeProvidersList[selectedIndex])
                    } else emptyList()

                    val semaphore = Semaphore(2)
                    featuredProviders.forEach { platform ->
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
                // Seleccionar hasta 2 semillas distintas de lo visto recientemente o en reproducción
                val seedCandidates = (localRecentlyPlayed + localContinueWatching)
                    .distinctBy { it.id }
                    .take(2)

                seedCandidates.forEach { seed ->
                    launch {
                        val matchedRecs = mutableListOf<JellyfinMediaEntity>()

                        // 1. Intentar con recomendaciones de TMDB si hay tmdbId y clave configurada
                        if (effectiveTmdbKey.isNotBlank() && seed.tmdbId != null) {
                            runCatching {
                                withTimeoutOrNull(3500L) {
                                    val tmdbIdLong = seed.tmdbId?.toLongOrNull() ?: return@withTimeoutOrNull
                                    val isTv = seed.type.equals("Series", ignoreCase = true) || seed.type.equals("Episode", ignoreCase = true)
                                    val recsResult = mediaRepository.getTmdbRecommendations(
                                        apiKey = effectiveTmdbKey,
                                        tmdbId = tmdbIdLong,
                                        isTv = isTv
                                    ).getOrDefault(emptyList())
                                    if (recsResult.isNotEmpty()) {
                                        val filtered = filterToLibraryUseCase.filterTmdbItems(
                                            tmdbItems = recsResult,
                                            serverUrl = prefs.jellyfinServerUrl,
                                            userId = prefs.jellyfinUserId,
                                            token = prefs.jellyfinAccessToken,
                                            maxCandidates = 25
                                        ).filter { it.id != seed.id }
                                        matchedRecs.addAll(filtered)
                                    }
                                }
                            }
                        }

                        // 2. Si TMDB no devolvió suficientes o no hay red, usar afinidad local por tags y género en Room
                        if (matchedRecs.size < 4) {
                            val tagList = seed.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                            val tag1 = tagList.getOrNull(0) ?: ""
                            val tag2 = tagList.getOrNull(1) ?: ""
                            val genre = seed.genres?.split(",")?.map { it.trim() }?.firstOrNull { it.isNotBlank() } ?: ""
                            if (tag1.isNotBlank() || genre.isNotBlank()) {
                                val localSimilar = runCatching {
                                    mediaRepository.getSimilarByKeywords(tag1, tag2, genre, seed.id, limit = 20)
                                }.getOrDefault(emptyList())
                                for (item in localSimilar) {
                                    if (item.id != seed.id && matchedRecs.none { it.id == item.id }) {
                                        matchedRecs.add(item)
                                    }
                                }
                            }
                        }

                        val uniqueMatched = matchedRecs.distinctBy { it.id }.take(15)
                        if (uniqueMatched.size >= 3) {
                            val section = HomeSection(
                                title = "Porque viste ${seed.title}",
                                items = uniqueMatched.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_RECOMMENDATION) },
                                kind = HomeSectionKind.BECAUSE_YOU_WATCHED
                            )
                            postSection(section, cacheKey = "seed_${seed.id}", itemIds = uniqueMatched.map { it.id })
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
        val identity = "${prefs.jellyfinServerUrl}|${prefs.jellyfinUserId}"
        // El fichero es la vía rápida de arranque; Room queda como respaldo estructurado.
        withContext(Dispatchers.IO) { snapshotFileStore?.write(sections, identity) }
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
                        payload = runCatching {
                            tv.medusa.app.util.CryptoUtils.encrypt(MediaItemSnapshotCodec.encode(section.items))
                        }.getOrDefault("")
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
        val identity = "${prefs.jellyfinServerUrl}|${prefs.jellyfinUserId}"
        val fileSections = withContext(Dispatchers.IO) {
            snapshotFileStore?.read(
                maxSections = if (previewOnly) 4 else Int.MAX_VALUE,
                expectedIdentity = identity
            ).orEmpty()
        }
        if (fileSections.isNotEmpty()) {
            fileSections.forEach { section ->
                val isPlatform = section.kind == HomeSectionKind.PLATFORM || section.title.startsWith("Populares en ")
                if (isPlatform && sections.any { it.kind == HomeSectionKind.PLATFORM || it.title.startsWith("Populares en ") }) {
                    return@forEach
                }
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
        val currentVersion = "${prefs.jellyfinLastSync}|${runCatching { mediaRepository.getLocalCount() }.getOrDefault(0)}"

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
            // El catálogo cambió (sync nueva): descartar la fila en vez de servirla 48 h.
            if (entry.catalogVersion.isNotBlank() && entry.catalogVersion != currentVersion) {
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
                runCatching { entry to MediaItemSnapshotCodec.decode(tv.medusa.app.util.CryptoUtils.decryptStored(payload)) }
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
            if (!isSnapshot && !entry.isRanked && uniqueItems.size < 3) return
            shownMediaIds.addAll(uniqueItems.map { it.id })

            val kind = when {
                entry.sectionKey.startsWith("platform_") -> HomeSectionKind.PLATFORM
                entry.sectionKey.startsWith("tmdb_trending") -> HomeSectionKind.TRENDING
                entry.sectionKey.startsWith("seed_") || entry.sectionKey.startsWith("tmdb_because") -> HomeSectionKind.BECAUSE_YOU_WATCHED
                entry.sectionKey == "local_boxsets" -> HomeSectionKind.FRANCHISES
                entry.sectionKey == "local_hidden_gems" -> HomeSectionKind.HIDDEN_GEMS
                entry.sectionKey.startsWith("trakt_") -> HomeSectionKind.TRAKT
                entry.title.startsWith("Porque viste") -> HomeSectionKind.BECAUSE_YOU_WATCHED
                entry.title == "Sagas y Colecciones" -> HomeSectionKind.FRANCHISES
                entry.title == "Joyas de tu videoteca" -> HomeSectionKind.HIDDEN_GEMS
                entry.title.startsWith("Populares en ") -> HomeSectionKind.PLATFORM
                entry.title.startsWith("Top 10") -> HomeSectionKind.TRENDING
                entry.title == "Últimos estrenos" -> HomeSectionKind.NEW_RELEASES
                entry.title == "Añadido recientemente" -> HomeSectionKind.RECENTLY_ADDED
                entry.title == "Grandes Producciones de tu colección" -> HomeSectionKind.LIBRARY_TOP_MOVIES
                entry.title == "Series Destacadas" -> HomeSectionKind.LIBRARY_TOP_SERIES
                entry.title == "Películas Recomendadas" -> HomeSectionKind.RECOMMENDED_MOVIES
                entry.title.startsWith("Recomendados de") -> HomeSectionKind.GENRE
                else -> HomeSectionKind.CUSTOM
            }
            val layout = when {
                entry.isRanked || kind == HomeSectionKind.TRENDING -> HomeSectionLayout.RANKED
                kind == HomeSectionKind.FRANCHISES || entry.title == "Sagas y Colecciones" -> HomeSectionLayout.LANDSCAPE
                else -> HomeSectionLayout.POSTER
            }

            val isPlatform = kind == HomeSectionKind.PLATFORM || entry.title.startsWith("Populares en ")
            if (isPlatform && sections.any { it.kind == HomeSectionKind.PLATFORM || it.title.startsWith("Populares en ") }) {
                return
            }

            val restoredSection = HomeSection(
                title = entry.title,
                items = uniqueItems,
                badge = entry.badge,
                isRanked = entry.isRanked,
                kind = kind,
                layout = layout
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
