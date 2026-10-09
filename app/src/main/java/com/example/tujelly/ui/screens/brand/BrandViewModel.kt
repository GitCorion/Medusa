package com.example.tujelly.ui.screens.brand

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tujelly.data.local.DEFAULT_TMDB_API_KEY
import com.example.tujelly.data.local.UserPreferencesRepository
import com.example.tujelly.data.remote.NetworkClientFactory
import com.example.tujelly.data.remote.tmdb.TmdbApiService
import com.example.tujelly.data.remote.tmdb.TmdbItemDto
import com.example.tujelly.domain.model.HomeSection
import com.example.tujelly.domain.model.MediaItem
import com.example.tujelly.domain.model.MediaSource
import com.example.tujelly.domain.usecase.FilterToLibraryUseCase
import com.example.tujelly.util.toOptimizedMediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

data class BrandInfo(
    val id: String,
    val name: String,
    val providerId: String,
    val iconRes: Int,
    val iconMonoRes: Int,
    val accentColor: androidx.compose.ui.graphics.Color,
    val gradientStart: androidx.compose.ui.graphics.Color
)

sealed interface BrandUiState {
    object Loading : BrandUiState
    data class Success(
        val brand: BrandInfo,
        val sections: List<HomeSection>,
        val focusedItem: MediaItem? = null,
        val format: com.example.tujelly.domain.model.MediaFormatFilter = com.example.tujelly.domain.model.MediaFormatFilter.ALL
    ) : BrandUiState
    data class Error(val message: String) : BrandUiState
}

class BrandViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val brandCache = ConcurrentHashMap<String, BrandUiState.Success>()
    }

    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val database = com.example.tujelly.data.local.db.AppDatabase.getDatabase(application)
    private val mediaRepository = com.example.tujelly.data.repository.MediaRepository(
        database.jellyfinDao(),
        userPreferencesRepository,
        database.tmdbVoteCacheDao()
    )
    private val filterToLibraryUseCase = FilterToLibraryUseCase(mediaRepository)

    val accentColor: StateFlow<String> = userPreferencesRepository.userPreferencesFlow
        .map { it.accentColor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.tujelly.data.local.ACCENT_CYAN)

    val buttonStyle: StateFlow<String> = userPreferencesRepository.userPreferencesFlow
        .map { it.buttonStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.tujelly.data.local.BUTTON_STYLE_ICONS_ONLY)

    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.isMonochrome }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow<BrandUiState>(BrandUiState.Loading)
    val uiState: StateFlow<BrandUiState> = _uiState.asStateFlow()

    private var allRawSections: List<HomeSection> = emptyList()
    private var currentFormat = com.example.tujelly.domain.model.MediaFormatFilter.ALL

    fun setFormat(format: com.example.tujelly.domain.model.MediaFormatFilter) {
        currentFormat = format
        val state = _uiState.value
        if (state is BrandUiState.Success) {
            val filtered = filterSections(allRawSections, format)
            _uiState.value = state.copy(
                sections = filtered,
                focusedItem = filtered.firstOrNull()?.items?.firstOrNull(),
                format = format
            )
        }
    }

    private fun filterSections(
        rawSections: List<HomeSection>,
        format: com.example.tujelly.domain.model.MediaFormatFilter
    ): List<HomeSection> {
        return when (format) {
            com.example.tujelly.domain.model.MediaFormatFilter.ALL -> rawSections
            com.example.tujelly.domain.model.MediaFormatFilter.MOVIES -> rawSections.mapNotNull { section ->
                val filteredItems = section.items.filter { it.type.equals("Movie", ignoreCase = true) }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
            com.example.tujelly.domain.model.MediaFormatFilter.SERIES -> rawSections.mapNotNull { section ->
                val filteredItems = section.items.filter {
                    it.type.equals("Series", ignoreCase = true) || it.type.equals("Episode", ignoreCase = true)
                }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
        }
    }

    fun loadBrandFeed(brandId: String, forceRefresh: Boolean = false) {
        if (forceRefresh) {
            brandCache.remove(brandId)
        }
        val cached = brandCache[brandId]
        if (cached != null && cached.sections.isNotEmpty()) {
            allRawSections = cached.sections
            val filtered = filterSections(allRawSections, currentFormat)
            _uiState.value = cached.copy(
                sections = filtered,
                focusedItem = filtered.firstOrNull()?.items?.firstOrNull(),
                format = currentFormat
            )
            return
        } else {
            _uiState.value = BrandUiState.Loading
        }

        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val effectiveApiKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
            val effectiveRegion = prefs.watchRegion.takeIf { it.isNotBlank() } ?: "ES"

            val platform = com.example.tujelly.data.model.platformById(brandId)
                ?: com.example.tujelly.data.model.SUPPORTED_PLATFORMS.first()

            val brand = BrandInfo(
                id = platform.id,
                name = platform.name,
                providerId = platform.providerId,
                iconRes = platform.iconRes,
                iconMonoRes = platform.iconMonoRes,
                accentColor = platform.accentColor,
                gradientStart = platform.gradientStart
            )

            val sections = mutableListOf<HomeSection>()

            if (brand.id.equals("trakt", ignoreCase = true)) {
                val traktClientId = prefs.traktClientId.ifBlank { com.example.tujelly.data.local.DEFAULT_TRAKT_CLIENT_ID }
                val hasToken = prefs.traktAccessToken.isNotBlank()

                // 1. Tendencias Globales de Trakt
                try {
                    val trendingResult = mediaRepository.getTraktTrending(clientId = traktClientId)
                    val rawTrending = trendingResult.getOrDefault(emptyList()).take(30)
                    val matchedTrending = filterToLibraryUseCase.filterTraktItems(
                        traktItems = rawTrending,
                        serverUrl = prefs.jellyfinServerUrl,
                        userId = prefs.jellyfinUserId,
                        token = prefs.jellyfinAccessToken,
                        maxCandidates = 30
                    )
                    if (matchedTrending.isNotEmpty()) {
                        sections.add(
                            HomeSection(
                                title = "Tendencias en Trakt.tv",
                                items = matchedTrending.map {
                                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION)
                                },
                                badge = "POPULAR"
                            )
                        )
                    }
                } catch (_: Exception) {}

                if (hasToken) {
                    // 2. Recomendaciones Personalizadas
                    try {
                        val recsResult = mediaRepository.getTraktRecommendations(
                            traktToken = prefs.traktAccessToken,
                            clientId = traktClientId
                        )
                        val rawRecs = recsResult.getOrDefault(emptyList()).take(30)
                        val matchedRecs = filterToLibraryUseCase.filterTraktItems(
                            traktItems = rawRecs,
                            serverUrl = prefs.jellyfinServerUrl,
                            userId = prefs.jellyfinUserId,
                            token = prefs.jellyfinAccessToken,
                            maxCandidates = 30
                        )
                        if (matchedRecs.isNotEmpty()) {
                            sections.add(
                                HomeSection(
                                    title = "Recomendaciones para ti",
                                    items = matchedRecs.map {
                                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION)
                                    },
                                    badge = "PARA TI"
                                )
                            )
                        }
                    } catch (_: Exception) {}

                    // 3. Tu Watchlist
                    try {
                        val watchlistResult = mediaRepository.getTraktWatchlist(
                            traktToken = prefs.traktAccessToken,
                            clientId = traktClientId
                        )
                        val rawWatchlist = watchlistResult.getOrDefault(emptyList()).take(30)
                        val matchedWatchlist = filterToLibraryUseCase.filterTraktItems(
                            traktItems = rawWatchlist,
                            serverUrl = prefs.jellyfinServerUrl,
                            userId = prefs.jellyfinUserId,
                            token = prefs.jellyfinAccessToken,
                            maxCandidates = 30
                        )
                        if (matchedWatchlist.isNotEmpty()) {
                            sections.add(
                                HomeSection(
                                    title = "Tu Watchlist",
                                    items = matchedWatchlist.map {
                                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST)
                                    },
                                    badge = null
                                )
                            )
                        }
                    } catch (_: Exception) {}

                    // 4. Tus Listas Personalizadas de Trakt
                    try {
                        val userListsResult = mediaRepository.getTraktUserLists(
                            traktToken = prefs.traktAccessToken,
                            clientId = traktClientId
                        )
                        val userLists = userListsResult.getOrDefault(emptyList())
                        for (list in userLists) {
                            val listId = list.ids.slug ?: list.ids.trakt?.toString() ?: continue
                            val listItemsResult = mediaRepository.getTraktCustomListItems(
                                traktToken = prefs.traktAccessToken,
                                clientId = traktClientId,
                                listId = listId
                            )
                            val rawListItems = listItemsResult.getOrDefault(emptyList()).take(30)
                            val matchedListItems = filterToLibraryUseCase.filterTraktItems(
                                traktItems = rawListItems,
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 30
                            )
                            if (matchedListItems.isNotEmpty()) {
                                sections.add(
                                    HomeSection(
                                        title = list.name,
                                        items = matchedListItems.map {
                                            it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST)
                                        },
                                        badge = "LISTA"
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            } else {
                val api = NetworkClientFactory.createService("https://api.themoviedb.org/3/", TmdbApiService::class.java)

                try {
                    supervisorScope {
                        val trendingDeferred = async {
                            val (trendingMovies, trendingTv) = coroutineScope {
                                val m = async {
                                    runCatching {
                                        api.discoverMoviesByProvider(apiKey = effectiveApiKey, providerId = brand.providerId, watchRegion = effectiveRegion, page = 1)
                                    }.getOrNull()?.results ?: emptyList()
                                }
                                val t = async {
                                    runCatching {
                                        api.discoverTvByProvider(apiKey = effectiveApiKey, providerId = brand.providerId, watchRegion = effectiveRegion, page = 1)
                                    }.getOrNull()?.results ?: emptyList()
                                }
                                Pair(m.await(), t.await())
                            }
                            val combined = (trendingMovies + trendingTv).take(30)

                            val localMatched = filterToLibraryUseCase.filterTmdbItems(
                                tmdbItems = combined,
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 30
                            )
                            if (localMatched.isNotEmpty()) {
                                localMatched.map {
                                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                                }
                            } else {
                                combined.take(20).map { it.toDirectMediaItem() }
                            }
                        }

                        val seriesDeferred = async {
                            val trendingTv = runCatching {
                                api.discoverTvByProvider(apiKey = effectiveApiKey, providerId = brand.providerId, watchRegion = effectiveRegion, page = 2)
                            }.getOrNull()?.results ?: emptyList()

                            val localMatched = filterToLibraryUseCase.filterTmdbItems(
                                tmdbItems = trendingTv.take(30),
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 30
                            )
                            if (localMatched.isNotEmpty()) {
                                localMatched.map {
                                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                                }
                            } else {
                                trendingTv.take(20).map { it.toDirectMediaItem() }
                            }
                        }

                        val topDeferred = async {
                            val topMovies = runCatching {
                                api.discoverMoviesByProvider(apiKey = effectiveApiKey, providerId = brand.providerId, watchRegion = effectiveRegion, sortBy = "vote_average.desc", voteCountGte = 200, page = 1)
                            }.getOrNull()?.results ?: emptyList()

                            val localMatched = filterToLibraryUseCase.filterTmdbItems(
                                tmdbItems = topMovies.take(30),
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 30
                            )
                            if (localMatched.isNotEmpty()) {
                                localMatched.map {
                                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                                }
                            } else {
                                topMovies.take(20).map { it.toDirectMediaItem() }
                            }
                        }

                        val trendingItems = trendingDeferred.await()
                        val seriesItems = seriesDeferred.await()
                        val topItems = topDeferred.await()

                        if (trendingItems.isNotEmpty()) {
                            sections.add(
                                HomeSection(
                                    title = "En Tendencia",
                                    items = trendingItems,
                                    badge = null
                                )
                            )
                        }

                        if (seriesItems.isNotEmpty()) {
                            sections.add(
                                HomeSection(
                                    title = "Series Destacadas",
                                    items = seriesItems,
                                    badge = null
                                )
                            )
                        }

                        if (topItems.isNotEmpty()) {
                            sections.add(
                                HomeSection(
                                    title = "Cine Aclamado",
                                    items = topItems,
                                    badge = null
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            allRawSections = sections
            val filtered = filterSections(allRawSections, currentFormat)
            val firstItem = filtered.firstOrNull()?.items?.firstOrNull()
            val successState = BrandUiState.Success(
                brand = brand,
                sections = filtered,
                focusedItem = firstItem,
                format = currentFormat
            )
            if (sections.isNotEmpty()) {
                brandCache[brandId] = successState.copy(sections = allRawSections, format = com.example.tujelly.domain.model.MediaFormatFilter.ALL)
            }
            _uiState.value = successState
        }
    }

    fun setFocusedItem(item: MediaItem) {
        val currentState = _uiState.value
        if (currentState is BrandUiState.Success) {
            _uiState.value = currentState.copy(focusedItem = item)
        }
    }

    private fun TmdbItemDto.toDirectMediaItem(): MediaItem {
        val isTv = mediaType?.equals("tv", ignoreCase = true) == true || name != null
        val titleText = title ?: name ?: "Título"
        val poster = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else null
        val backdrop = if (!backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w1280$backdropPath" else null
        val releaseYear = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()

        return MediaItem(
            id = "tmdb-$id",
            title = titleText,
            overview = overview,
            type = if (isTv) "Series" else "Movie",
            posterUrl = poster,
            backdropUrl = backdrop,
            rating = voteAverage,
            year = releaseYear,
            source = MediaSource.TMDB_TRENDING
        )
    }

    private fun com.example.tujelly.data.local.db.JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String, source: MediaSource): MediaItem =
        toOptimizedMediaItem(baseUrl, token, source)
}
