package tv.medusa.app.ui.screens.brand

import android.util.Log
import androidx.compose.ui.graphics.Color
import tv.medusa.app.data.local.DEFAULT_TRAKT_CLIENT_ID
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.model.SUPPORTED_PLATFORMS
import tv.medusa.app.data.model.platformById
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.data.remote.ApiConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.medusa.app.data.local.DEFAULT_TMDB_API_KEY
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.tmdb.TmdbApiService
import tv.medusa.app.data.remote.tmdb.TmdbItemDto
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.usecase.FilterToLibraryUseCase
import tv.medusa.app.util.toOptimizedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class BrandInfo(
    val id: String,
    val name: String,
    val providerId: String,
    val iconRes: Int,
    val iconMonoRes: Int,
    val accentColor: Color,
    val gradientStart: Color
)

sealed interface BrandUiState {
    object Loading : BrandUiState
    data class Success(
        val brand: BrandInfo,
        val sections: List<HomeSection>,
        val focusedItem: MediaItem? = null,
        val format: MediaFormatFilter = MediaFormatFilter.ALL
    ) : BrandUiState
    data class Error(val message: String) : BrandUiState
}

@HiltViewModel
class BrandViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase
) : ViewModel() {

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

    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle
    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome

    private val _uiState = MutableStateFlow<BrandUiState>(BrandUiState.Loading)
    val uiState: StateFlow<BrandUiState> = _uiState.asStateFlow()

    private var allRawSections: List<HomeSection> = emptyList()
    private var currentFormat = MediaFormatFilter.ALL

    fun setFormat(format: MediaFormatFilter) {
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
        format: MediaFormatFilter
    ): List<HomeSection> {
        val result = when (format) {
            MediaFormatFilter.ALL -> rawSections
            MediaFormatFilter.MOVIES -> rawSections.mapNotNull { section ->
                val filteredItems = section.items.filter { it.type.equals("Movie", ignoreCase = true) }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
            MediaFormatFilter.SERIES -> rawSections.mapNotNull { section ->
                val filteredItems = section.items.filter {
                    it.type.equals("Series", ignoreCase = true) || it.type.equals("Episode", ignoreCase = true)
                }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
        }
        return result.distinctBy { it.title }
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

            val platform = platformById(brandId)
                ?: SUPPORTED_PLATFORMS.first()

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
                val traktClientId = prefs.traktClientId.ifBlank { DEFAULT_TRAKT_CLIENT_ID }
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
                                val displayItems = matchedListItems.map {
                                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST)
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
                        Log.e("BrandVM", "Error loading Trakt custom lists", e)
                    }

                    // 2. Tu Watchlist de Trakt
                    try {
                        val watchlistResult = mediaRepository.getTraktWatchlist(
                            traktToken = prefs.traktAccessToken,
                            clientId = traktClientId
                        )
                        val rawWatchlist = watchlistResult.getOrDefault(emptyList()).take(40)
                        if (rawWatchlist.isNotEmpty()) {
                            val matchedWatchlist = filterToLibraryUseCase.filterTraktItems(
                                traktItems = rawWatchlist,
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 40
                            )
                            val displayItems = matchedWatchlist.map {
                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_WATCHLIST)
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
                        Log.e("BrandVM", "Error loading Trakt watchlist", e)
                    }

                    // 3. Recomendaciones Personalizadas de Trakt
                    try {
                        val recsResult = mediaRepository.getTraktRecommendations(
                            traktToken = prefs.traktAccessToken,
                            clientId = traktClientId
                        )
                        val rawRecs = recsResult.getOrDefault(emptyList()).take(40)
                        if (rawRecs.isNotEmpty()) {
                            val matchedRecs = filterToLibraryUseCase.filterTraktItems(
                                traktItems = rawRecs,
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 40
                            )
                            val displayItems = matchedRecs.map {
                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION)
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
                        Log.e("BrandVM", "Error loading Trakt recommendations", e)
                    }
                }

                // 4. Tendencias Globales de Trakt
                try {
                    val trendingResult = mediaRepository.getTraktTrending(clientId = traktClientId)
                    val rawTrending = trendingResult.getOrDefault(emptyList()).take(40)
                    if (rawTrending.isNotEmpty()) {
                        val matchedTrending = filterToLibraryUseCase.filterTraktItems(
                            traktItems = rawTrending,
                            serverUrl = prefs.jellyfinServerUrl,
                            userId = prefs.jellyfinUserId,
                            token = prefs.jellyfinAccessToken,
                            maxCandidates = 40
                        )
                        val displayItems = matchedTrending.map {
                            it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TRAKT_RECOMMENDATION)
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
                    Log.e("BrandVM", "Error loading Trakt trending", e)
                }
            } else {
                val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)

                val tmdbSectionOrder = listOf("En Tendencia", "Series Destacadas", "Cine Aclamado")

                val updateUiAndCache = {
                    val currentSnapshot = synchronized(sections) {
                        sections.sortedBy { s ->
                            val idx = tmdbSectionOrder.indexOf(s.title)
                            if (idx != -1) idx else 999
                        }
                    }
                    allRawSections = currentSnapshot
                    val filtered = filterSections(allRawSections, currentFormat)
                    val currentFocused = (_uiState.value as? BrandUiState.Success)?.focusedItem
                    val successState = BrandUiState.Success(
                        brand = brand,
                        sections = filtered,
                        focusedItem = currentFocused ?: filtered.firstOrNull()?.items?.firstOrNull(),
                        format = currentFormat
                    )
                    _uiState.value = successState
                    brandCache[brandId] = successState.copy(sections = allRawSections, format = MediaFormatFilter.ALL)
                }

                try {
                    supervisorScope {
                        launch {
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
                            val combined = (trendingMovies + trendingTv).take(40)

                            val localMatched = filterToLibraryUseCase.filterTmdbItems(
                                tmdbItems = combined,
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 40
                            )
                            val displayItems = localMatched.map {
                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                            }
                            if (displayItems.isNotEmpty()) {
                                synchronized(sections) {
                                    sections.removeAll { it.title == "En Tendencia" }
                                    sections.add(HomeSection(title = "En Tendencia", items = displayItems))
                                }
                                updateUiAndCache()
                            }
                        }

                        launch {
                            val trendingTv = runCatching {
                                api.discoverTvByProvider(apiKey = effectiveApiKey, providerId = brand.providerId, watchRegion = effectiveRegion, page = 2)
                            }.getOrNull()?.results ?: emptyList()

                            val localMatched = filterToLibraryUseCase.filterTmdbItems(
                                tmdbItems = trendingTv.take(40),
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 40
                            )
                            val displayItems = localMatched.map {
                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                            }
                            if (displayItems.isNotEmpty()) {
                                synchronized(sections) {
                                    sections.removeAll { it.title == "Series Destacadas" }
                                    sections.add(HomeSection(title = "Series Destacadas", items = displayItems))
                                }
                                updateUiAndCache()
                            }
                        }

                        launch {
                            val topMovies = runCatching {
                                api.discoverMoviesByProvider(apiKey = effectiveApiKey, providerId = brand.providerId, watchRegion = effectiveRegion, sortBy = "vote_average.desc", voteCountGte = 200, page = 1)
                            }.getOrNull()?.results ?: emptyList()

                            val localMatched = filterToLibraryUseCase.filterTmdbItems(
                                tmdbItems = topMovies.take(40),
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 40
                            )
                            val displayItems = localMatched.map {
                                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                            }
                            if (displayItems.isNotEmpty()) {
                                synchronized(sections) {
                                    sections.removeAll { it.title == "Cine Aclamado" }
                                    sections.add(HomeSection(title = "Cine Aclamado", items = displayItems))
                                }
                                updateUiAndCache()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            allRawSections = synchronized(sections) {
                sections.sortedBy { s ->
                    val idx = listOf("En Tendencia", "Series Destacadas", "Cine Aclamado").indexOf(s.title)
                    if (idx != -1) idx else 999
                }
            }
            val filtered = filterSections(allRawSections, currentFormat)
            val currentFocused = (_uiState.value as? BrandUiState.Success)?.focusedItem
            val successState = BrandUiState.Success(
                brand = brand,
                sections = filtered,
                focusedItem = currentFocused ?: filtered.firstOrNull()?.items?.firstOrNull(),
                format = currentFormat
            )
            if (sections.isNotEmpty()) {
                brandCache[brandId] = successState.copy(sections = allRawSections, format = MediaFormatFilter.ALL)
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

    private fun JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String, source: MediaSource): MediaItem =
        toOptimizedMediaItem(baseUrl, token, source)
}
