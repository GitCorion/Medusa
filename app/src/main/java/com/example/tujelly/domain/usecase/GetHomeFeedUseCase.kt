package com.example.tujelly.domain.usecase

import com.example.tujelly.data.local.UserPreferences
import com.example.tujelly.data.local.db.HomeSectionCacheDao
import com.example.tujelly.data.local.db.HomeSectionCacheEntity
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import com.example.tujelly.data.repository.MediaRepository
import com.example.tujelly.domain.model.HomeSection
import com.example.tujelly.domain.model.MediaItem
import com.example.tujelly.domain.model.MediaSource
import com.example.tujelly.data.remote.trakt.toDirectMediaItem
import com.example.tujelly.util.toOptimizedMediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
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
    }

    operator fun invoke(prefs: UserPreferences, forceRefresh: Boolean = false): Flow<List<HomeSection>> = flow {
        if (prefs.jellyfinServerUrl.isBlank() || prefs.jellyfinUserId.isBlank() || prefs.jellyfinAccessToken.isBlank()) {
            emit(emptyList())
            return@flow
        }

        // Si tenemos caché en memoria completo reciente (< 5 min), emitir de inmediato y evitar recargas innecesarias
        val cached = cachedHomeFeed
        if (!forceRefresh && cached != null && cached.size >= 4) {
            emit(cached)
            if (System.currentTimeMillis() - cachedFeedTimestamp < 5 * 60 * 1000L) {
                return@flow
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
        val shownMediaIds = mutableSetOf<String>()

        val emitFeed: suspend (List<HomeSection>) -> Unit = { list ->
            if (list.isNotEmpty()) {
                val snapshot = list.toList()
                if (snapshot.size >= 4) {
                    cachedHomeFeed = snapshot
                    cachedFeedTimestamp = System.currentTimeMillis()
                }
                emit(snapshot)
            }
        }

        fun updateOrAddSection(title: String, newSection: HomeSection, preferredIndex: Int = -1) {
            val idx = sections.indexOfFirst { it.title == title }
            if (idx != -1) {
                sections[idx] = newSection
            } else if (preferredIndex in 0..sections.size) {
                sections.add(preferredIndex, newSection)
            } else {
                sections.add(newSection)
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
            sections.add(HomeSection(
                title = "Continuar Viendo",
                items = localContinueWatching.map { it.toOptimizedMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
            ))
        }
        if (localLatest.isNotEmpty()) {
            val freshLatest = localLatest.filter { it.id !in shownMediaIds }
            if (freshLatest.isNotEmpty()) {
                shownMediaIds.addAll(freshLatest.map { it.id })
                sections.add(HomeSection(
                    title = "Añadido recientemente",
                    items = freshLatest.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localTopMovies.isNotEmpty()) {
            val freshTopMovies = localTopMovies.filter { it.id !in shownMediaIds }
            if (freshTopMovies.isNotEmpty()) {
                shownMediaIds.addAll(freshTopMovies.map { it.id })
                sections.add(HomeSection(
                    title = "Grandes Producciones de tu colección",
                    items = freshTopMovies.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localTopSeries.isNotEmpty()) {
            val freshTopSeries = localTopSeries.filter { it.id !in shownMediaIds }
            if (freshTopSeries.isNotEmpty()) {
                shownMediaIds.addAll(freshTopSeries.map { it.id })
                sections.add(HomeSection(
                    title = "Series Destacadas",
                    items = freshTopSeries.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localRecommendedMovies.isNotEmpty()) {
            val freshRec = localRecommendedMovies.filter { it.id !in shownMediaIds }
            if (freshRec.isNotEmpty()) {
                shownMediaIds.addAll(freshRec.map { it.id })
                sections.add(HomeSection(
                    title = "Películas Recomendadas",
                    items = freshRec.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localGenrePool.isNotEmpty()) {
            val freshGenre = localGenrePool.filter { it.id !in shownMediaIds }
            if (freshGenre.size >= 3) {
                shownMediaIds.addAll(freshGenre.map { it.id })
                sections.add(HomeSection(
                    title = "Recomendados de $topGenre",
                    items = freshGenre.take(15).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }
        if (localTopRated.isNotEmpty()) {
            val freshRated = localTopRated.filter { it.id !in shownMediaIds }
            if (freshRated.size >= 3) {
                shownMediaIds.addAll(freshRated.map { it.id })
                sections.add(HomeSection(
                    title = "Joyas de tu colección",
                    items = freshRated.take(15).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ))
            }
        }

        // Emisión inmediata local (<50ms)
        if (sections.isNotEmpty()) emitFeed(sections)

        // =========================================================================
        // PASO 0.5: RESTAURAR SECCIONES CACHEADAS EN ROOM (< 30ms)
        // Secciones como "Populares en Netflix" que se calcularon en la sesión
        // anterior y fueron persistidas. Esto evita la pantalla vacía tras
        // process death nocturno (Doze mode).
        // =========================================================================
        val cachedSectionsRestored = restoreCachedSections(
            prefs = prefs,
            shownMediaIds = shownMediaIds,
            sections = sections
        )
        if (cachedSectionsRestored) emitFeed(sections)

        // =========================================================================
        // PASO 1: ACTUALIZACIÓN EN SEGUNDO PLANO EN PARALELO
        // =========================================================================
        val topMoviesD: kotlinx.coroutines.Deferred<List<JellyfinMediaEntity>>
        val topSeriesD: kotlinx.coroutines.Deferred<List<JellyfinMediaEntity>>
        val resumeD: kotlinx.coroutines.Deferred<List<JellyfinMediaEntity>>
        val latestD: kotlinx.coroutines.Deferred<List<JellyfinMediaEntity>>
        val recentlyPlayedD: kotlinx.coroutines.Deferred<List<JellyfinMediaEntity>>
        val tmdbTopRatedD: kotlinx.coroutines.Deferred<List<JellyfinMediaEntity>>

        coroutineScope {
            topMoviesD = async {
                runCatching {
                    mediaRepository.getTopRatedMoviesServer(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken, limit = 20)
                }.getOrDefault(localTopMovies)
            }
            topSeriesD = async {
                runCatching {
                    mediaRepository.getTopRatedSeriesServer(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken, limit = 20)
                }.getOrDefault(localTopSeries)
            }
            resumeD = async {
                runCatching {
                    mediaRepository.getContinueWatching(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken).getOrNull() ?: emptyList()
                }.getOrDefault(emptyList())
            }
            latestD = async {
                runCatching {
                    mediaRepository.getLatest(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken).getOrNull() ?: emptyList()
                }.getOrDefault(emptyList()).filter {
                    it.type.equals("Movie", ignoreCase = true) || it.type.equals("Series", ignoreCase = true)
                }
            }
            recentlyPlayedD = async {
                runCatching {
                    mediaRepository.getRecentlyPlayed(prefs.jellyfinServerUrl, prefs.jellyfinUserId, prefs.jellyfinAccessToken).getOrNull() ?: emptyList()
                }.getOrDefault(emptyList())
            }
            tmdbTopRatedD = async {
                if (prefs.tmdbApiKey.isNotBlank()) {
                    runCatching {
                        val topRated = mediaRepository.getTmdbTopRated(prefs.tmdbApiKey).getOrDefault(emptyList())
                        filterToLibraryUseCase.filterTmdbItems(tmdbItems = topRated, maxCandidates = 30)
                    }.getOrDefault(emptyList())
                } else emptyList()
            }
        }

        // Actualizar Continuar Viendo y Añadido Recientemente tan pronto llegan
        val resumeItems = resumeD.await()
        if (resumeItems.isNotEmpty()) {
            shownMediaIds.addAll(resumeItems.map { it.id })
            updateOrAddSection(
                "Continuar Viendo",
                HomeSection(
                    title = "Continuar Viendo",
                    items = resumeItems.take(15).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ),
                preferredIndex = 0
            )
        }

        val latestItems = latestD.await()
        if (latestItems.isNotEmpty()) {
            shownMediaIds.addAll(latestItems.map { it.id })
            val insertIdx = if (sections.any { it.title == "Continuar Viendo" }) 1 else 0
            updateOrAddSection(
                "Añadido recientemente",
                HomeSection(
                    title = "Añadido recientemente",
                    items = latestItems.take(20).map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                ),
                preferredIndex = insertIdx
            )
            emitFeed(sections)
        }

        val topMoviesServer = topMoviesD.await()
        val topSeriesServer = topSeriesD.await()
        val recentlyPlayedItems = recentlyPlayedD.await()
        val tmdbTopRated = tmdbTopRatedD.await()

        val tmdbBlockbusters = tmdbTopRated.filter { it.type.equals("Movie", ignoreCase = true) }
        val tmdbTopSeries = tmdbTopRated.filter { it.type.equals("Series", ignoreCase = true) }

        // 2. Filtrar películas con masa crítica de votos y personalizar según géneros vistos
        val curatedServerMovies = if (prefs.tmdbApiKey.isNotBlank()) {
            filterHighQualityMovies(topMoviesServer, prefs.tmdbApiKey).filterPersonalizedForHeader(watchedGenres)
        } else {
            topMoviesServer.filterPersonalizedForHeader(watchedGenres)
        }
        val curatedTopMovies = (tmdbBlockbusters + curatedServerMovies).distinctBy { it.id }.take(20)

        val curatedServerSeries = if (prefs.tmdbApiKey.isNotBlank()) {
            filterHighQualitySeries(topSeriesServer, prefs.tmdbApiKey).filterPersonalizedForHeader(watchedGenres)
        } else {
            topSeriesServer.filterPersonalizedForHeader(watchedGenres)
        }
        val curatedTopSeries = (tmdbTopSeries + curatedServerSeries).distinctBy { it.id }.take(20)

        if (curatedTopMovies.isNotEmpty()) {
            shownMediaIds.addAll(curatedTopMovies.map { it.id })
            updateOrAddSection(
                "Grandes Producciones de tu colección",
                HomeSection(
                    title = "Grandes Producciones de tu colección",
                    items = curatedTopMovies.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                )
            )
        }
        if (curatedTopSeries.isNotEmpty()) {
            shownMediaIds.addAll(curatedTopSeries.map { it.id })
            updateOrAddSection(
                "Series Destacadas",
                HomeSection(
                    title = "Series Destacadas",
                    items = curatedTopSeries.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN) }
                )
            )
        }
        emitFeed(sections)

        // =========================================================================
        // PASO 2: RECOMENDACIÓN PERSONALIZADA
        // =========================================================================
        val watchedReferenceItem = resumeItems.firstOrNull { it.tmdbId != null }
            ?: recentlyPlayedItems.firstOrNull { it.tmdbId != null }

        var recommendationAdded = false

        if (watchedReferenceItem != null && prefs.tmdbApiKey.isNotBlank()) {
            try {
                val tmdbIdLong = watchedReferenceItem.tmdbId?.toLongOrNull()
                if (tmdbIdLong != null) {
                    val isTv = watchedReferenceItem.type.equals("Series", ignoreCase = true) ||
                            watchedReferenceItem.type.equals("Episode", ignoreCase = true)
                    val recsResult = mediaRepository.getTmdbRecommendations(
                        apiKey = prefs.tmdbApiKey,
                        tmdbId = tmdbIdLong,
                        isTv = isTv
                    ).getOrDefault(emptyList())

                    if (recsResult.isNotEmpty()) {
                        val refGenres = watchedReferenceItem.genres?.split(",", ";")
                            ?.map { it.trim().lowercase() }
                            ?.filter { it.isNotBlank() } ?: emptyList()

                        val matchedRecs = filterToLibraryUseCase.filterTmdbItems(
                            tmdbItems = recsResult,
                            serverUrl = prefs.jellyfinServerUrl,
                            userId = prefs.jellyfinUserId,
                            token = prefs.jellyfinAccessToken,
                            maxCandidates = 30
                        ).filter { candidate ->
                            candidate.id != watchedReferenceItem.id &&
                                    candidate.id !in shownMediaIds &&
                                    (refGenres.isEmpty() || candidate.genres.orEmpty().split(",", ";").any { g -> g.trim().lowercase() in refGenres })
                        }.take(15)

                        val finalRecs = if (matchedRecs.size >= 3) matchedRecs else {
                            filterToLibraryUseCase.filterTmdbItems(
                                tmdbItems = recsResult,
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 30
                            ).filter { candidate ->
                                candidate.id != watchedReferenceItem.id && candidate.id !in shownMediaIds
                            }.take(15)
                        }

                        if (finalRecs.size >= 3) {
                            shownMediaIds.addAll(finalRecs.map { it.id })
                            val becauseSection = HomeSection(
                                title = "Porque viste ${watchedReferenceItem.title}",
                                items = finalRecs.map {
                                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_RECOMMENDATION)
                                },
                                badge = null
                            )
                            updateOrAddSection(
                                "Porque viste ${watchedReferenceItem.title}",
                                becauseSection
                            )
                            persistSection("tmdb_because_watched", becauseSection, finalRecs.map { it.id })
                            emitFeed(sections)
                            recommendationAdded = true
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback inteligente de género si aún no está añadido
        if (!recommendationAdded && !sections.any { it.title.startsWith("Recomendados de") }) {
            try {
                val genrePool = mediaRepository.getItemsByGenre(topGenre)
                    .filter { it.id !in shownMediaIds && (!it.backdropImageTag.isNullOrEmpty() || !it.overview.isNullOrBlank()) }
                val genreItems = diversifyByDecade(genrePool, maxPerDecade = 2, totalLimit = 15)
                if (genreItems.size >= 3) {
                    shownMediaIds.addAll(genreItems.map { it.id })
                    updateOrAddSection(
                        "Recomendados de $topGenre",
                        HomeSection(
                            title = "Recomendados de $topGenre",
                            items = genreItems.map {
                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
                            },
                            badge = null
                        )
                    )
                    emitFeed(sections)
                }
            } catch (_: Exception) {}
        }

        // =====================================================================
        // PASO 3: TOP 10 DE HOY EN ESPAÑA / REGIÓN
        // =====================================================================
        if (prefs.tmdbApiKey.isNotBlank()) {
            try {
                val dailyTrending = mediaRepository.getTmdbTrendingDay(apiKey = prefs.tmdbApiKey)
                    .getOrDefault(emptyList())
                if (dailyTrending.isNotEmpty()) {
                    val matchedDaily = filterToLibraryUseCase.filterTmdbItems(
                        tmdbItems = dailyTrending,
                        serverUrl = prefs.jellyfinServerUrl,
                        userId = prefs.jellyfinUserId,
                        token = prefs.jellyfinAccessToken,
                        maxCandidates = 50
                    )
                    if (matchedDaily.isNotEmpty()) {
                        shownMediaIds.addAll(matchedDaily.map { it.id })
                        val dailyItems = matchedDaily.take(10)
                        val top10Section = HomeSection(
                            title = "Top 10 en España hoy",
                            items = dailyItems.mapIndexed { idx, it ->
                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING).copy(rank = idx + 1)
                            },
                            badge = null,
                            isRanked = true
                        )
                        val insertIdx = if (sections.any { it.title == "Continuar Viendo" }) 1 else 0
                        updateOrAddSection("Top 10 en España hoy", top10Section, preferredIndex = insertIdx)
                        persistSection("tmdb_trending_day", top10Section, dailyItems.map { it.id })
                        emitFeed(sections)
                    }
                }
            } catch (_: Exception) {}

            // =====================================================================
            // PASO 4: PLATAFORMAS OFICIALES DE STREAMING (PARALELIZADO CON TIMEOUT)
            // =====================================================================
            val selected = prefs.selectedPlatforms
            val activeProviders = if (selected.isEmpty()) {
                com.example.tujelly.data.model.SUPPORTED_PLATFORMS.take(3)
            } else {
                com.example.tujelly.data.model.SUPPORTED_PLATFORMS.filter { it.id in selected }
            }

            try {
                supervisorScope {
                    val channel = Channel<Pair<com.example.tujelly.data.model.StreamPlatform, List<JellyfinMediaEntity>>>(Channel.UNLIMITED)
                    val semaphore = Semaphore(2)

                    val workers = activeProviders.map { platform ->
                        launch {
                            runCatching {
                                semaphore.acquire()
                                try {
                                    withTimeoutOrNull(2500L) {
                                        val result = mediaRepository.getTmdbByProvider(
                                            apiKey = prefs.tmdbApiKey,
                                            providerId = platform.providerId,
                                            region = prefs.watchRegion
                                        )
                                        val matched = filterToLibraryUseCase.filterTmdbItems(
                                            tmdbItems = result.getOrDefault(emptyList()),
                                            serverUrl = prefs.jellyfinServerUrl,
                                            userId = prefs.jellyfinUserId,
                                            token = prefs.jellyfinAccessToken,
                                            maxCandidates = 40
                                        )
                                        if (matched.isNotEmpty()) {
                                            channel.send(platform to matched)
                                        }
                                    }
                                } finally {
                                    semaphore.release()
                                }
                            }
                        }
                    }

                    launch {
                        workers.forEach { it.join() }
                        channel.close()
                    }

                    for ((platform, matched) in channel) {
                        val platformTitle = "Populares en ${platform.name}"
                        val existingThisSectionIds = sections.firstOrNull { it.title == platformTitle }?.items?.map { it.id }?.toSet() ?: emptySet()
                        val otherSectionsIds = shownMediaIds - existingThisSectionIds
                        val uniqueMatched = matched.filter { it.id !in otherSectionsIds }
                        if (uniqueMatched.isNotEmpty()) {
                            shownMediaIds.removeAll(existingThisSectionIds)
                            shownMediaIds.addAll(uniqueMatched.map { it.id })
                            val platformItems = uniqueMatched.take(20)
                            val platformSection = HomeSection(
                                title = platformTitle,
                                items = platformItems.map {
                                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                                },
                                badge = null
                            )
                            updateOrAddSection(platformTitle, platformSection)
                            persistSection("platform_${platform.id}", platformSection, platformItems.map { it.id })
                            emitFeed(sections)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // =========================================================================
        // PASO 5: PELÍCULAS RECOMENDADAS (Asegurar presencia si aún no fue agregada)
        // =========================================================================
        if (!sections.any { it.title == "Películas Recomendadas" }) {
            val recommendedMovies = mediaRepository.getRecommendedMovies(
                excludeIds = shownMediaIds,
                limit = 20,
                genres = watchedGenres
            )
            val effectiveRecommended = if (recommendedMovies.isNotEmpty()) {
                recommendedMovies
            } else {
                topMoviesServer.filter { it.id !in shownMediaIds }
            }
            if (effectiveRecommended.isNotEmpty()) {
                shownMediaIds.addAll(effectiveRecommended.map { it.id })
                sections.add(
                    HomeSection(
                        title = "Películas Recomendadas",
                        items = effectiveRecommended.map {
                            it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
                        },
                        badge = null
                    )
                )
                emitFeed(sections)
            }
        }

        // =========================================================================
        // PASO 6: TRAKT.TV (Recomendaciones, Watchlist y Tendencias)
        // =========================================================================
        val traktClientId = prefs.traktClientId.ifBlank { com.example.tujelly.data.local.DEFAULT_TRAKT_CLIENT_ID }
        if (prefs.traktAccessToken.isNotBlank()) {
            // 1. Recommendations
            try {
                val traktRecsResult = mediaRepository.getTraktRecommendations(
                    traktToken = prefs.traktAccessToken,
                    clientId = traktClientId
                )
                val rawTraktRecs = traktRecsResult.getOrDefault(emptyList()).take(20)
                val matchedTraktRecs = filterToLibraryUseCase.filterTraktItems(
                    traktItems = rawTraktRecs,
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    maxCandidates = 20
                )
                if (matchedTraktRecs.isNotEmpty()) {
                    val traktRecTitle = "Recomendado por la comunidad Trakt"
                    val existingTraktIds = sections.firstOrNull { it.title == traktRecTitle }?.items?.map { it.id }?.toSet() ?: emptySet()
                    shownMediaIds.removeAll(existingTraktIds)
                    shownMediaIds.addAll(matchedTraktRecs.map { it.id })
                    val traktRecSection = HomeSection(
                        title = traktRecTitle,
                        items = matchedTraktRecs.map {
                            it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION)
                        },
                        badge = null
                    )
                    updateOrAddSection(traktRecTitle, traktRecSection)
                    persistSection("trakt_recs", traktRecSection, matchedTraktRecs.map { it.id })
                    emitFeed(sections)
                }
            } catch (_: Exception) {}

            // 2. Watchlist
            try {
                val watchlistResult = mediaRepository.getTraktWatchlist(
                    traktToken = prefs.traktAccessToken,
                    clientId = traktClientId
                )
                val rawWatchlist = watchlistResult.getOrDefault(emptyList()).take(20)
                val matchedWatchlist = filterToLibraryUseCase.filterTraktItems(
                    traktItems = rawWatchlist,
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    maxCandidates = 20
                )
                if (matchedWatchlist.isNotEmpty()) {
                    val watchlistTitle = "Tu Watchlist de Trakt.tv"
                    val existingWatchlistIds = sections.firstOrNull { it.title == watchlistTitle }?.items?.map { it.id }?.toSet() ?: emptySet()
                    shownMediaIds.removeAll(existingWatchlistIds)
                    shownMediaIds.addAll(matchedWatchlist.map { it.id })
                    val watchlistSection = HomeSection(
                        title = watchlistTitle,
                        items = matchedWatchlist.map {
                            it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST)
                        },
                        badge = null
                    )
                    updateOrAddSection(watchlistTitle, watchlistSection)
                    persistSection("trakt_watchlist", watchlistSection, matchedWatchlist.map { it.id })
                    emitFeed(sections)
                }
            } catch (_: Exception) {}

            // 2.5. Trakt Custom User Lists (Paralelizado con timeout)
            try {
                val userListsResult = mediaRepository.getTraktUserLists(
                    traktToken = prefs.traktAccessToken,
                    clientId = traktClientId
                )
                val userLists = userListsResult.getOrDefault(emptyList())
                    .filterNot { it.isUserHidden(prefs.hiddenTraktLists) }
                    .take(5)

                val loadedLists = coroutineScope {
                    userLists.map { list ->
                        async {
                            withTimeoutOrNull(4000L) {
                                val listId = list.ids.trakt?.toString() ?: list.ids.slug ?: return@withTimeoutOrNull null
                                val username = list.user?.username
                                val listItemsResult = mediaRepository.getTraktCustomListItems(
                                    traktToken = prefs.traktAccessToken,
                                    clientId = traktClientId,
                                    listId = listId,
                                    username = username
                                )
                                val rawListItems = listItemsResult.getOrDefault(emptyList()).take(30)
                                if (rawListItems.isEmpty()) return@withTimeoutOrNull null
                                list to rawListItems
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                for ((list, rawListItems) in loadedLists) {
                    val matchedListItems = filterToLibraryUseCase.filterTraktItems(
                        traktItems = rawListItems,
                        serverUrl = prefs.jellyfinServerUrl,
                        userId = prefs.jellyfinUserId,
                        token = prefs.jellyfinAccessToken,
                        maxCandidates = 30
                    )
                    val displayItems = if (matchedListItems.isNotEmpty()) {
                        matchedListItems.map {
                            it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST)
                        }
                    } else {
                        rawListItems.map { it.toDirectMediaItem(MediaSource.TRAKT_WATCHLIST) }
                    }

                    if (displayItems.isNotEmpty()) {
                        val listTitle = "Trakt: ${list.name}"
                        val existingListIds = sections.firstOrNull { it.title == listTitle }?.items?.map { it.id }?.toSet() ?: emptySet()
                        shownMediaIds.removeAll(existingListIds)
                        shownMediaIds.addAll(matchedListItems.map { it.id })
                        val traktListSection = HomeSection(
                            title = listTitle,
                            items = displayItems,
                            badge = "TRAKT LISTA"
                        )
                        updateOrAddSection(listTitle, traktListSection)
                        val matchedIds = matchedListItems.map { it.id }
                        if (matchedIds.isNotEmpty()) {
                            val listKey = "trakt_list_${list.ids.trakt ?: list.ids.slug ?: list.name.hashCode()}"
                            persistSection(listKey, traktListSection, matchedIds)
                        }
                    }
                }
                if (loadedLists.isNotEmpty()) {
                    emitFeed(sections)
                }
            } catch (_: Exception) {}
        }

        // 3. Trending Trakt
        try {
            val trendingResult = mediaRepository.getTraktTrending(clientId = traktClientId)
            val rawTrending = trendingResult.getOrDefault(emptyList()).take(20)
            val matchedTrending = filterToLibraryUseCase.filterTraktItems(
                traktItems = rawTrending,
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                maxCandidates = 20
            )
            if (matchedTrending.isNotEmpty()) {
                val traktTrendingTitle = "Tendencias en Trakt.tv"
                val existingTrendingIds = sections.firstOrNull { it.title == traktTrendingTitle }?.items?.map { it.id }?.toSet() ?: emptySet()
                shownMediaIds.removeAll(existingTrendingIds)
                shownMediaIds.addAll(matchedTrending.map { it.id })
                val traktTrendingSection = HomeSection(
                    title = traktTrendingTitle,
                    items = matchedTrending.map {
                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION)
                    },
                    badge = null
                )
                updateOrAddSection(traktTrendingTitle, traktTrendingSection)
                persistSection("trakt_trending", traktTrendingSection, matchedTrending.map { it.id })
                emitFeed(sections)
            }
        } catch (_: Exception) {}

        // Fallback final
        if (sections.isEmpty()) {
            emit(emptyList())
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
            candidates.take(12).map { movie ->
                async {
                    withTimeoutOrNull(2000L) {
                        val tmdbId = movie.tmdbId?.toLongOrNull() ?: return@withTimeoutOrNull null
                        val stats = mediaRepository.getTmdbVoteStats(tmdbApiKey, tmdbId, isTv = false)
                            ?: return@withTimeoutOrNull null
                        Triple(movie.copy(communityRating = stats.voteAverage), stats.voteCount, stats.voteAverage)
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val passed = checked
            .filter { (_, count, avg) -> count >= 150 && avg >= 6.8f }
            .sortedByDescending { (_, count, avg) -> avg * 10f + kotlin.math.min(count, 5000) / 500f }
            .map { it.first }

        return if (passed.isNotEmpty()) passed else validMovies.filter {
            val r = it.communityRating ?: 0f
            r in 7.0f..9.5f
        }.take(20)
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
            return validSeries.filter {
                val r = it.communityRating ?: 0f
                r in 7.0f..9.5f
            }.take(20)
        }

        val checked = coroutineScope {
            candidates.take(12).map { s ->
                async {
                    withTimeoutOrNull(2000L) {
                        val tmdbId = s.tmdbId?.toLongOrNull() ?: return@withTimeoutOrNull null
                        val stats = mediaRepository.getTmdbVoteStats(tmdbApiKey, tmdbId, isTv = true)
                            ?: return@withTimeoutOrNull null
                        Triple(s.copy(communityRating = stats.voteAverage), stats.voteCount, stats.voteAverage)
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val passed = checked
            .filter { (_, count, avg) -> count >= 50 && avg >= 6.8f }
            .sortedByDescending { (_, count, avg) -> avg * 10f + kotlin.math.min(count, 5000) / 500f }
            .map { it.first }

        return if (passed.isNotEmpty()) passed else validSeries.filter {
            val r = it.communityRating ?: 0f
            r in 7.0f..9.5f
        }.take(20)
    }

    private fun List<JellyfinMediaEntity>.filterPersonalizedForHeader(watchedGenres: List<String>): List<JellyfinMediaEntity> {
        val userGenresLower = watchedGenres.map { it.lowercase().trim() }
        return this.filter { entity ->
            // Strictly exclude played or abandoned in-progress items
            if (entity.isPlayed || entity.playbackPositionTicks > 0L) return@filter false

            val year = entity.productionYear
            val genres = entity.genres?.split(",", ";")?.map { it.lowercase().trim() } ?: emptyList()
            val matchesGenre = userGenresLower.isNotEmpty() && genres.any { it in userGenresLower }

            // Filter out old movies (< 1995) unless matching watched genres
            if (year != null && year < 1995 && !matchesGenre) return@filter false

            true
        }.sortedByDescending { entity ->
            var score = entity.communityRating ?: 0f
            val year = entity.productionYear ?: 1990
            val genres = entity.genres?.split(",", ";")?.map { it.lowercase().trim() } ?: emptyList()

            if (userGenresLower.isNotEmpty() && genres.any { it in userGenresLower }) {
                score += 2.0f
            }
            if (year >= 2015) {
                score += 1.5f
            } else if (year >= 2000) {
                score += 0.8f
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

            // Reconstruir items desde IDs cacheados
            val ids: List<String> = entry.itemIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (ids.isEmpty()) continue

            // Carga masiva en 1 sola consulta SQL
            val idMap: Map<String, JellyfinMediaEntity> = runCatching {
                mediaRepository.getItemsByIds(ids).associateBy { it.id }
            }.getOrDefault(emptyMap())

            // Preservar el orden original
            val entities: List<JellyfinMediaEntity> = ids.mapNotNull { idMap[it] }
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

            shownMediaIds.addAll(entities.map { it.id })
            sections.add(
                HomeSection(
                    title = entry.title,
                    items = items,
                    badge = entry.badge,
                    isRanked = entry.isRanked
                )
            )
            restored = true
        }
        return restored
    }
}
