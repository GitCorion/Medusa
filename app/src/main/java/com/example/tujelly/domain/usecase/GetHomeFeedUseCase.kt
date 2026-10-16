package com.example.tujelly.domain.usecase

import com.example.tujelly.data.local.UserPreferences
import com.example.tujelly.data.local.db.HomeSectionCacheDao
import com.example.tujelly.data.local.db.HomeSectionCacheEntity
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import com.example.tujelly.data.repository.MediaRepository
import com.example.tujelly.domain.model.HomeSection
import com.example.tujelly.domain.model.MediaItem
import com.example.tujelly.domain.model.MediaSource
import com.example.tujelly.util.toOptimizedMediaItem
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
    private val homeSectionCacheDao: HomeSectionCacheDao? = null
) {
    companion object {
        @Volatile
        private var cachedHomeFeed: List<HomeSection>? = null
        @Volatile
        private var cachedFeedTimestamp: Long = 0L
        private const val FEED_CACHE_TTL = 15 * 60 * 1000L // 15 minutos

        fun invalidateFeedCache() {
            cachedHomeFeed = null
            cachedFeedTimestamp = 0L
        }

        fun sectionPriority(title: String): Int = when {
            title == "Continuar Viendo" -> 0
            title.contains("Top 10", ignoreCase = true) -> 10
            title == "Añadido recientemente" -> 20
            title.startsWith("Porque viste") -> 30
            title.contains("Netflix", ignoreCase = true) -> 40
            title.contains("Prime", ignoreCase = true) -> 41
            title.contains("Disney", ignoreCase = true) -> 42
            title.contains("Apple", ignoreCase = true) -> 43
            title.contains("Max", ignoreCase = true) -> 44
            title.contains("SkyShowtime", ignoreCase = true) -> 45
            title.contains("Movistar", ignoreCase = true) -> 46
            title.contains("Filmin", ignoreCase = true) -> 47
            title.contains("Crunchyroll", ignoreCase = true) -> 48
            title.startsWith("Populares en ") -> 49
            title.startsWith("Trakt:") -> 60
            title == "Tu Watchlist de Trakt.tv" -> 61
            title == "Recomendado por la comunidad Trakt" -> 62
            title == "Tendencias en Trakt.tv" -> 63
            title == "Grandes Producciones de tu colección" -> 70
            title == "Series Destacadas" -> 80
            title == "Películas Recomendadas" -> 90
            title.startsWith("Recomendados de") -> 100
            title == "Joyas de tu colección" -> 110
            else -> 200
        }
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

        // Quick init: if local DB is empty (< 10 items), populate latest items & favorites in 0.5s
        // Never blocks the feed with a full 36,000 item catalog sync!
        runCatching {
            if (mediaRepository.getLocalCount() < 10 && !MediaRepository.syncProgress.value.isSyncing) {
                mediaRepository.syncQuickInit(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken
                )
            }
        }

        val sections = mutableListOf<HomeSection>()
        val shownMediaIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        val emitFeed: suspend (List<HomeSection>) -> Unit = { list ->
            if (list.isNotEmpty()) {
                val snapshot = list.toList()
                if (snapshot.size >= 4) {
                    cachedHomeFeed = snapshot
                    cachedFeedTimestamp = System.currentTimeMillis()
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
                val newPriority = sectionPriority(cleanSection.title)
                val insertIdx = sections.indexOfFirst { sectionPriority(it.title) > newPriority }
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
                emitFeed(sections)
            }
        }

        // =========================================================================
        // PASO 0: EMITIR CATÁLOGO LOCAL COMPLETO INMEDIATAMENTE (<50ms)
        // El usuario ve de inmediato un inicio completo con 5-7 filas sin esperar a la red.
        // =========================================================================
        val localContinueWatching = runCatching { mediaRepository.getContinueWatchingLocal(20) }.getOrDefault(emptyList())
        val localLatest = runCatching { mediaRepository.getLatestLocal(prefs.jellyfinServerUrl, prefs.jellyfinUserId, 20) }.getOrDefault(emptyList())
        val localTopMovies = mediaRepository.getTopMoviesLocal(30).filterPersonalizedForHeader(emptyList())
        val localTopSeries = mediaRepository.getTopSeriesLocal(30).filterPersonalizedForHeader(emptyList())
        val watchedGenres = runCatching { mediaRepository.getMostWatchedGenres() }.getOrDefault(emptyList())
        val localRecommendedMovies = runCatching { mediaRepository.getRecommendedMovies(excludeIds = emptySet(), limit = 20, genres = watchedGenres) }.getOrDefault(emptyList())
        val topGenre = watchedGenres.firstOrNull() ?: "Acción"
        val localGenrePool = runCatching { mediaRepository.getItemsByGenre(topGenre) }.getOrDefault(emptyList())
        val localTopRated = runCatching { mediaRepository.getTopRatedLocal(20) }.getOrDefault(emptyList())

        if (localContinueWatching.isNotEmpty()) {
            shownMediaIds.addAll(localContinueWatching.map { it.id })
            updateOrAddSection(HomeSection(
                title = "Continuar Viendo",
                items = localContinueWatching.map { it.toOptimizedMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
            ))
        }
        if (localLatest.isNotEmpty()) {
            val freshLatest = localLatest.filter { it.id !in shownMediaIds }
            if (freshLatest.isNotEmpty()) {
                shownMediaIds.addAll(freshLatest.map { it.id })
                updateOrAddSection(HomeSection(
                    title = "Añadido recientemente",
                    items = freshLatest.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localTopMovies.isNotEmpty()) {
            val freshTopMovies = localTopMovies.filter { it.id !in shownMediaIds }
            if (freshTopMovies.isNotEmpty()) {
                shownMediaIds.addAll(freshTopMovies.map { it.id })
                updateOrAddSection(HomeSection(
                    title = "Grandes Producciones de tu colección",
                    items = freshTopMovies.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localTopSeries.isNotEmpty()) {
            val freshTopSeries = localTopSeries.filter { it.id !in shownMediaIds }
            if (freshTopSeries.isNotEmpty()) {
                shownMediaIds.addAll(freshTopSeries.map { it.id })
                updateOrAddSection(HomeSection(
                    title = "Series Destacadas",
                    items = freshTopSeries.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localRecommendedMovies.isNotEmpty()) {
            val freshRec = localRecommendedMovies.filter { it.id !in shownMediaIds }
            if (freshRec.isNotEmpty()) {
                shownMediaIds.addAll(freshRec.map { it.id })
                updateOrAddSection(HomeSection(
                    title = "Películas Recomendadas",
                    items = freshRec.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localGenrePool.isNotEmpty()) {
            val freshGenre = localGenrePool.filter { it.id !in shownMediaIds }
            if (freshGenre.size >= 3) {
                shownMediaIds.addAll(freshGenre.map { it.id })
                updateOrAddSection(HomeSection(
                    title = "Recomendados de $topGenre",
                    items = freshGenre.take(15).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localTopRated.isNotEmpty()) {
            val freshRated = localTopRated.filter { it.id !in shownMediaIds }
            if (freshRated.size >= 3) {
                shownMediaIds.addAll(freshRated.map { it.id })
                updateOrAddSection(HomeSection(
                    title = "Joyas de tu colección",
                    items = freshRated.take(15).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }

        // Emisión inmediata local (<50ms)
        if (sections.isNotEmpty()) emitFeed(sections)

        // =========================================================================
        // PASO 0.5: RESTAURAR SECCIONES CACHEADAS EN ROOM (< 30ms)
        // =========================================================================
        val cachedSectionsRestored = restoreCachedSections(
            prefs = prefs,
            shownMediaIds = shownMediaIds,
            sections = sections
        )
        if (cachedSectionsRestored) emitFeed(sections)

        // =========================================================================
        // ACTUALIZACIÓN CONCURRENTE EN SEGUNDO PLANO (SUPERVISORSCOPE STREAMING)
        // Ninguna fuente bloquea a las demás: Jellyfin, TMDB Plataformas, Top 10 y Trakt
        // se consultan en paralelo y emiten de forma inmediata al UI al completar.
        // =========================================================================
        val effectiveTmdbKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() } ?: com.example.tujelly.data.local.DEFAULT_TMDB_API_KEY
        val effectiveWatchRegion = prefs.watchRegion.takeIf { it.isNotBlank() } ?: "ES"
        var sharedResumeReference: JellyfinMediaEntity? = null
        var sharedRecentReference: JellyfinMediaEntity? = null

        supervisorScope {
            // ---------------------------------------------------------------------
            // JOB 1: JELLYFIN SERVER (Continuar Viendo remoto, Añadidos y Catálogo)
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
                            items = resumeItems.take(15).map { it.toOptimizedMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                        )
                    )
                }

                val latestItems = runCatching {
                    mediaRepository.getLatest(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken).getOrNull() ?: emptyList()
                }.getOrDefault(emptyList()).filter {
                    it.type.equals("Movie", ignoreCase = true) || it.type.equals("Series", ignoreCase = true)
                }
                if (latestItems.isNotEmpty()) {
                    shownMediaIds.addAll(latestItems.map { it.id })
                    postSection(
                        HomeSection(
                            title = "Añadido recientemente",
                            items = latestItems.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                        )
                    )
                }

                val recentlyPlayed = runCatching {
                    mediaRepository.getRecentlyPlayed(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken).getOrNull() ?: emptyList()
                }.getOrDefault(emptyList())
                sharedRecentReference = recentlyPlayed.firstOrNull { it.tmdbId != null }

                // Top servidor curado
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
                            items = curatedMovies.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                        )
                    )
                }

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
                            items = curatedSeries.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                        )
                    )
                }
            }

            // ---------------------------------------------------------------------
            // JOB 2: TOP 10 EN ESPAÑA HOY (TMDB TRENDING DAY CON MEDALLAS 1-10)
            // ---------------------------------------------------------------------
            launch {
                if (effectiveTmdbKey.isNotBlank()) {
                    runCatching {
                        withTimeoutOrNull(4000L) {
                            val dailyTrending = mediaRepository.getTmdbTrendingDay(apiKey = effectiveTmdbKey).getOrDefault(emptyList())
                            if (dailyTrending.isNotEmpty()) {
                                val matchedDaily = filterToLibraryUseCase.filterTmdbItems(
                                    tmdbItems = dailyTrending,
                                    serverUrl = prefs.jellyfinServerUrl,
                                    userId = prefs.jellyfinUserId,
                                    token = prefs.jellyfinAccessToken,
                                    maxCandidates = 30
                                )
                                if (matchedDaily.isNotEmpty()) {
                                    val top10Items = matchedDaily.take(10).mapIndexed { idx, it ->
                                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING).copy(rank = idx + 1)
                                    }
                                    val top10Section = HomeSection(
                                        title = "Top 10 en España hoy",
                                        items = top10Items,
                                        badge = null,
                                        isRanked = true
                                    )
                                    postSection(
                                        top10Section,
                                        cacheKey = "tmdb_trending_day",
                                        itemIds = matchedDaily.map { it.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------------
            // JOB 3: PLATAFORMAS OFICIALES DE STREAMING (CONCURRENTE CON STREAMING)
            // ---------------------------------------------------------------------
            launch {
                if (effectiveTmdbKey.isNotBlank() && prefs.isProUser) {
                    val selected = prefs.selectedPlatforms
                    val activeProviders = (if (selected.isEmpty()) {
                        com.example.tujelly.data.model.SUPPORTED_PLATFORMS
                    } else {
                        com.example.tujelly.data.model.SUPPORTED_PLATFORMS.filter { it.id in selected }
                    }).filter { it.id != "trakt" && it.providerId.isNotBlank() }

                    val semaphore = Semaphore(3)
                    activeProviders.forEach { platform ->
                        launch {
                            semaphore.acquire()
                            try {
                                withTimeoutOrNull(7000L) {
                                    val result = mediaRepository.getTmdbByProvider(
                                        apiKey = effectiveTmdbKey,
                                        providerId = platform.providerId,
                                        region = effectiveWatchRegion
                                    )
                                    val rawTmdbItems = result.getOrDefault(emptyList())
                                    if (rawTmdbItems.isNotEmpty()) {
                                        val matched = filterToLibraryUseCase.filterTmdbItems(
                                            tmdbItems = rawTmdbItems,
                                            serverUrl = prefs.jellyfinServerUrl,
                                            userId = prefs.jellyfinUserId,
                                            token = prefs.jellyfinAccessToken,
                                            maxCandidates = 25
                                        )
                                        val uniqueMatched = matched.distinctBy { it.id }
                                        if (uniqueMatched.isNotEmpty()) {
                                            val platformItems = uniqueMatched.take(20).map {
                                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                                            }
                                            if (platformItems.isNotEmpty()) {
                                                val platformTitle = "Populares en ${platform.name}"
                                                val platformSection = HomeSection(
                                                    title = platformTitle,
                                                    items = platformItems,
                                                    badge = null
                                                )
                                                postSection(
                                                    platformSection,
                                                    cacheKey = "platform_${platform.id}",
                                                    itemIds = platformItems.map { it.id }
                                                )
                                            }
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
                val traktClientId = prefs.traktClientId.ifBlank { com.example.tujelly.data.local.DEFAULT_TRAKT_CLIENT_ID }
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
                                        badge = null
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
                                        badge = null
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
                                                    badge = "TRAKT LISTA"
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
                                        badge = null
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
                if (refItem != null && prefs.tmdbApiKey.isNotBlank()) {
                    runCatching {
                        withTimeoutOrNull(3500L) {
                            val tmdbIdLong = refItem.tmdbId?.toLongOrNull() ?: return@withTimeoutOrNull
                            val isTv = refItem.type.equals("Series", ignoreCase = true) || refItem.type.equals("Episode", ignoreCase = true)
                            val recsResult = mediaRepository.getTmdbRecommendations(apiKey = prefs.tmdbApiKey, tmdbId = tmdbIdLong, isTv = isTv).getOrDefault(emptyList())
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
                                        items = matchedRecs.take(15).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_RECOMMENDATION) }
                                    )
                                    postSection(section, cacheKey = "tmdb_because_watched", itemIds = matchedRecs.map { it.id })
                                }
                            }
                        }
                    }
                }
            }
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
            .sortedByDescending { (_, count, avg) -> avg * 10f + kotlin.math.min(count, 5000) / 500f }
            .map { it.first }

        return if (passed.isNotEmpty()) passed else validMovies.sortedByDescending { it.communityRating ?: 0f }.take(20)
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
            return validSeries.sortedByDescending { it.communityRating ?: 0f }.take(20)
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
            .sortedByDescending { (_, count, avg) -> avg * 10f + kotlin.math.min(count, 5000) / 500f }
            .map { it.first }

        return if (passed.isNotEmpty()) passed else validSeries.sortedByDescending { it.communityRating ?: 0f }.take(20)
    }

    private fun List<JellyfinMediaEntity>.filterPersonalizedForHeader(watchedGenres: List<String>): List<JellyfinMediaEntity> {
        val userGenresLower = watchedGenres.map { it.lowercase().trim() }
        return this.filter { entity ->
            // Strictly exclude played or abandoned in-progress items
            if (entity.isPlayed || entity.playbackPositionTicks > 0L) return@filter false

            // Requisito indispensable para la cabecera: poseer imagen de fondo (backdrop)
            if (entity.backdropImageTag.isNullOrBlank()) return@filter false

            true
        }.sortedByDescending { entity ->
            var score = entity.communityRating ?: 0f
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
                    isRanked = section.isRanked
                )
            )
        }
    }

    /**
     * Restaura secciones cacheadas desde Room (< 30ms).
     * Reconstruye cada sección buscando los IDs en jellyfin_media mediante batch query.
     * Solo restaura secciones cuyo TTL stale no haya expirado (máx 48 horas).
     */
    private suspend fun restoreCachedSections(
        prefs: UserPreferences,
        shownMediaIds: MutableSet<String>,
        sections: MutableList<HomeSection>
    ): Boolean {
        val dao = homeSectionCacheDao ?: return false
        val cached = runCatching { dao.getAllSections() }.getOrDefault(emptyList())
        if (cached.isEmpty()) return false

        var restored = false
        for (entry in cached) {
            // Si supera el límite stale absoluto (48h), purgarlo
            if (entry.isExpired(HomeSectionCacheEntity.TTL_MAX_STALE)) {
                runCatching { dao.delete(entry.sectionKey) }
                continue
            }

            // Verificar que la sección no esté ya presente (evitar duplicados con PASO 0)
            if (sections.any { it.title == entry.title }) continue

            // Reconstruir items desde IDs cacheados (estrictamente deduplicados)
            val ids: List<String> = entry.itemIds.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (ids.isEmpty()) continue

            // Carga masiva en 1 sola consulta SQL
            val idMap: Map<String, JellyfinMediaEntity> = runCatching {
                mediaRepository.getItemsByIds(ids).associateBy { it.id }
            }.getOrDefault(emptyMap())

            // Preservar el orden original y garantizar unicidad
            val entities: List<JellyfinMediaEntity> = ids.mapNotNull { idMap[it] }.distinctBy { it.id }
            if (entities.isEmpty()) continue

            // Determinar el MediaSource correcto según el tipo de sección
            val source = when {
                entry.sectionKey.startsWith("platform_") -> MediaSource.TMDB_TRENDING
                entry.sectionKey.startsWith("tmdb_trending") -> MediaSource.TMDB_TRENDING
                entry.sectionKey.startsWith("tmdb_because") -> MediaSource.TMDB_RECOMMENDATION
                entry.sectionKey == "trakt_recs" -> MediaSource.TRAKT_RECOMMENDATION
                entry.sectionKey == "trakt_trending" -> MediaSource.TRAKT_RECOMMENDATION
                entry.sectionKey == "trakt_watchlist" -> MediaSource.TRAKT_WATCHLIST
                else -> MediaSource.JELLYFIN
            }

            val items: List<MediaItem> = if (entry.isRanked) {
                entities.mapIndexed { idx, entity ->
                    entity.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, source).copy(rank = idx + 1)
                }
            } else {
                entities.map { entity ->
                    entity.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, source)
                }
            }

            val uniqueItems = items.distinctBy { it.id }
            if (uniqueItems.isNotEmpty()) {
                shownMediaIds.addAll(entities.map { it.id })
                val restoredSection = HomeSection(
                    title = entry.title,
                    items = uniqueItems,
                    badge = entry.badge,
                    isRanked = entry.isRanked
                )
                val newPriority = sectionPriority(restoredSection.title)
                val insertIdx = sections.indexOfFirst { sectionPriority(it.title) > newPriority }
                if (insertIdx != -1) {
                    sections.add(insertIdx, restoredSection)
                } else {
                    sections.add(restoredSection)
                }
                restored = true
            }
        }
        return restored
    }
}
