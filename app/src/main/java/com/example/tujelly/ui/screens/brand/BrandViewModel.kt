package com.example.tujelly.ui.screens.brand

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tujelly.data.local.DEFAULT_TMDB_API_KEY
import com.example.tujelly.data.local.UserPreferencesRepository
import com.example.tujelly.data.remote.NetworkClientFactory
import com.example.tujelly.data.remote.tmdb.TmdbApiService
import com.example.tujelly.data.remote.tmdb.TmdbItemDto
import com.example.tujelly.data.remote.tmdb.toDirectMediaItem
import com.example.tujelly.data.remote.trakt.toDirectMediaItem
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

        fun clearCache(brandId: String? = null) {
            if (brandId != null) {
                brandCache.remove(brandId)
            } else {
                brandCache.clear()
            }
        }
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
        // Si la marca es Trakt y solo tiene 1 sección (probablemente tendencias pre-login), no usamos la caché
        val isStaleTraktCache = brandId.equals("trakt", ignoreCase = true) && cached != null && cached.sections.size <= 1
        if (cached != null && cached.sections.isNotEmpty() && !isStaleTraktCache) {
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

                if (hasToken) {
                    // 1. Tus Listas Personalizadas y Guardadas de Trakt (¡Prioridad máxima!)
                    try {
                        val userListsResult = mediaRepository.getTraktUserLists(
                            traktToken = prefs.traktAccessToken,
                            clientId = traktClientId
                        )
                        val userLists = userListsResult.getOrDefault(emptyList())
                            .filterNot { it.isUserHidden(prefs.hiddenTraktLists) }
                        for (list in userLists) {
                            val listId = list.ids.trakt?.toString() ?: list.ids.slug ?: continue
                            val username = list.user?.username
                            val listItemsResult = mediaRepository.getTraktCustomListItems(
                                traktToken = prefs.traktAccessToken,
                                clientId = traktClientId,
                                listId = listId,
                                username = username
                            )
                            val rawListItems = listItemsResult.getOrDefault(emptyList()).take(40)
                            if (rawListItems.isNotEmpty()) {
                                val matchedListItems = filterToLibraryUseCase.filterTraktItems(
                                    traktItems = rawListItems,
                                    serverUrl = prefs.jellyfinServerUrl,
                                    userId = prefs.jellyfinUserId,
                                    token = prefs.jellyfinAccessToken,
                                    maxCandidates = 40
                                )
                                val displayItems = if (matchedListItems.isNotEmpty()) {
                                    matchedListItems.map {
                                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST)
                                    }
                                } else {
                                    rawListItems.map { it.toDirectMediaItem(MediaSource.TRAKT_WATCHLIST) }
                                }
                                if (displayItems.isNotEmpty()) {
                                    sections.add(
                                        HomeSection(
                                            title = list.name,
                                            items = displayItems,
                                            badge = "LISTA"
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BrandVM", "Error loading Trakt custom lists", e)
                    }

                    // 2. Tu Watchlist de Trakt
                    try {
                        val watchlistResult = mediaRepository.getTraktWatchlist(
                            traktToken = prefs.traktAccessToken,
                            clientId = traktClientId
                        )
                        val rawWatchlist = watchlistResult.getOrDefault(emptyList()).take(30)
                        if (rawWatchlist.isNotEmpty()) {
                            val matchedWatchlist = filterToLibraryUseCase.filterTraktItems(
                                traktItems = rawWatchlist,
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 30
                            )
                            val displayItems = if (matchedWatchlist.isNotEmpty()) {
                                matchedWatchlist.map {
                                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST)
                                }
                            } else {
                                rawWatchlist.map { it.toDirectMediaItem(MediaSource.TRAKT_WATCHLIST) }
                            }
                            if (displayItems.isNotEmpty()) {
                                sections.add(
                                    HomeSection(
                                        title = "Tu Watchlist",
                                        items = displayItems,
                                        badge = "PENDIENTE"
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BrandVM", "Error loading Trakt watchlist", e)
                    }

                    // 3. Recomendaciones Personalizadas de Trakt
                    try {
                        val recsResult = mediaRepository.getTraktRecommendations(
                            traktToken = prefs.traktAccessToken,
                            clientId = traktClientId
                        )
                        val rawRecs = recsResult.getOrDefault(emptyList()).take(30)
                        if (rawRecs.isNotEmpty()) {
                            val matchedRecs = filterToLibraryUseCase.filterTraktItems(
                                traktItems = rawRecs,
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 30
                            )
                            val displayItems = if (matchedRecs.isNotEmpty()) {
                                matchedRecs.map {
                                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION)
                                }
                            } else {
                                rawRecs.map { it.toDirectMediaItem(MediaSource.TRAKT_RECOMMENDATION) }
                            }
                            if (displayItems.isNotEmpty()) {
                                sections.add(
                                    HomeSection(
                                        title = "Recomendaciones para ti",
                                        items = displayItems,
                                        badge = "PARA TI"
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BrandVM", "Error loading Trakt recommendations", e)
                    }
                }

                // 4. Tendencias Globales de Trakt
                try {
                    val trendingResult = mediaRepository.getTraktTrending(clientId = traktClientId)
                    val rawTrending = trendingResult.getOrDefault(emptyList()).take(30)
                    if (rawTrending.isNotEmpty()) {
                        val matchedTrending = filterToLibraryUseCase.filterTraktItems(
                            traktItems = rawTrending,
                            serverUrl = prefs.jellyfinServerUrl,
                            userId = prefs.jellyfinUserId,
                            token = prefs.jellyfinAccessToken,
                            maxCandidates = 30
                        )
                        val displayItems = if (matchedTrending.isNotEmpty()) {
                            matchedTrending.map {
                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION)
                            }
                        } else {
                            rawTrending.map { it.toDirectMediaItem(MediaSource.TRAKT_RECOMMENDATION) }
                        }
                        if (displayItems.isNotEmpty()) {
                            sections.add(
                                HomeSection(
                                    title = "Tendencias en Trakt.tv",
                                    items = displayItems,
                                    badge = "POPULAR"
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BrandVM", "Error loading Trakt trending", e)
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

    private fun com.example.tujelly.data.local.db.JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String, source: MediaSource): MediaItem =
        toOptimizedMediaItem(baseUrl, token, source)
}
