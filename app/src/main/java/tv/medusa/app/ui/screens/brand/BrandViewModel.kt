package tv.medusa.app.ui.screens.brand

import android.util.Log
import android.content.Context
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
import tv.medusa.app.domain.usecase.CollectionIndexUseCase
import tv.medusa.app.data.worker.CollectionIndexScheduler
import tv.medusa.app.util.toOptimizedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        val allItems: List<MediaItem> = emptyList(),
        val allCount: Int = 0,
        val focusedItem: MediaItem? = null,
        val format: MediaFormatFilter = MediaFormatFilter.ALL,
        val hideWatched: Boolean = false,
        val showAll: Boolean = false,
        val hasMoreAll: Boolean = false,
        val isLoadingAll: Boolean = false,
        val fromIndex: Boolean = false
    ) : BrandUiState
    data class Error(val message: String) : BrandUiState
}

@HiltViewModel
class BrandViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase,
    private val collectionIndexUseCase: CollectionIndexUseCase
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 60
        private const val LANDING_ROW_SIZE = 20
        private const val INDEX_POLL_ATTEMPTS = 20
        private const val INDEX_POLL_DELAY_MS = 1_500L

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
    val hideWatched: StateFlow<Boolean> = userPreferencesRepository.hideWatched

    private val _uiState = MutableStateFlow<BrandUiState>(BrandUiState.Loading)
    val uiState: StateFlow<BrandUiState> = _uiState.asStateFlow()

    private var allRawSections: List<HomeSection> = emptyList()
    private var currentFormat = MediaFormatFilter.ALL

    init {
        viewModelScope.launch {
            userPreferencesRepository.hideWatched.collect { hide ->
                val state = _uiState.value as? BrandUiState.Success ?: return@collect
                if (state.hideWatched != hide) {
                    if (state.fromIndex) {
                        loadFromIndex(
                            brand = state.brand,
                            key = CollectionIndexUseCase.platformKey(state.brand.id),
                            hide = hide,
                            format = currentFormat,
                            showAll = state.showAll
                        )
                    } else {
                        val (filteredSections, filteredItems) = filterSectionsAndItems(allRawSections, currentFormat, hide)
                        _uiState.value = state.copy(
                            sections = filteredSections,
                            allItems = filteredItems,
                            allCount = filteredItems.size,
                            focusedItem = filteredSections.firstOrNull()?.items?.firstOrNull() ?: filteredItems.firstOrNull(),
                            hideWatched = hide
                        )
                    }
                }
            }
        }
    }

    fun openAll() {
        val state = _uiState.value as? BrandUiState.Success ?: return
        _uiState.value = state.copy(showAll = true)
    }

    fun closeAll() {
        val state = _uiState.value as? BrandUiState.Success ?: return
        _uiState.value = state.copy(showAll = false)
    }

    fun setFormat(format: MediaFormatFilter) {
        currentFormat = format
        val state = _uiState.value
        if (state is BrandUiState.Success) {
            if (state.fromIndex) {
                viewModelScope.launch {
                    loadFromIndex(
                        brand = state.brand,
                        key = CollectionIndexUseCase.platformKey(state.brand.id),
                        hide = state.hideWatched,
                        format = format,
                        showAll = state.showAll
                    )
                }
                return
            }
            val (filteredSections, filteredItems) = filterSectionsAndItems(allRawSections, format, state.hideWatched)
            _uiState.value = state.copy(
                sections = filteredSections,
                allItems = filteredItems,
                allCount = filteredItems.size,
                focusedItem = filteredSections.firstOrNull()?.items?.firstOrNull() ?: filteredItems.firstOrNull(),
                format = format
            )
        }
    }

    fun toggleHideWatched() {
        viewModelScope.launch {
            userPreferencesRepository.toggleHideWatched()
        }
    }

    private fun filterSectionsAndItems(
        rawSections: List<HomeSection>,
        format: MediaFormatFilter,
        hideWatched: Boolean = userPreferencesRepository.hideWatched.value
    ): Pair<List<HomeSection>, List<MediaItem>> {
        val filteredSections = when (format) {
            MediaFormatFilter.ALL -> rawSections.mapNotNull { section ->
                val items = if (hideWatched) section.items.filter { !it.isPlayed } else section.items
                if (items.isNotEmpty()) section.copy(items = items) else null
            }
            MediaFormatFilter.MOVIES -> rawSections.mapNotNull { section ->
                val filteredItems = section.items.filter { it.type.equals("Movie", ignoreCase = true) && (!hideWatched || !it.isPlayed) }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
            MediaFormatFilter.SERIES -> rawSections.mapNotNull { section ->
                val filteredItems = section.items.filter {
                    (it.type.equals("Series", ignoreCase = true) || it.type.equals("Episode", ignoreCase = true)) && (!hideWatched || !it.isPlayed)
                }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
        }.distinctBy { it.title }

        val allRawItems = rawSections.flatMap { it.items }.distinctBy { it.id }
        val filteredAllItems = when (format) {
            MediaFormatFilter.ALL -> allRawItems.filter { !hideWatched || !it.isPlayed }
            MediaFormatFilter.MOVIES -> allRawItems.filter { it.type.equals("Movie", ignoreCase = true) && (!hideWatched || !it.isPlayed) }
            MediaFormatFilter.SERIES -> allRawItems.filter { (it.type.equals("Series", ignoreCase = true) || it.type.equals("Episode", ignoreCase = true)) && (!hideWatched || !it.isPlayed) }
        }

        return Pair(filteredSections, filteredAllItems)
    }

    fun loadBrandFeed(brandId: String, forceRefresh: Boolean = false) {
        if (forceRefresh) {
            brandCache.remove(brandId)
        }
        val cached = brandCache[brandId]
        // Si la marca es Trakt y solo tiene 1 sección (probablemente tendencias pre-login), no usamos la caché
        val isStaleTraktCache = brandId.equals("trakt", ignoreCase = true) && cached != null && cached.sections.size <= 1
        if (cached != null && cached.sections.isNotEmpty() && !isStaleTraktCache && !cached.fromIndex) {
            allRawSections = cached.sections
            val (filteredSections, filteredItems) = filterSectionsAndItems(allRawSections, currentFormat)
            _uiState.value = cached.copy(
                sections = filteredSections,
                allItems = filteredItems,
                allCount = filteredItems.size,
                focusedItem = filteredSections.firstOrNull()?.items?.firstOrNull() ?: filteredItems.firstOrNull(),
                format = currentFormat,
                showAll = false
            )
            return
        } else {
            _uiState.value = BrandUiState.Loading
        }

        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val effectiveApiKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
            val effectiveRegion = prefs.watchRegion.takeIf { it.isNotBlank() } ?: ApiConfig.TMDB_WATCH_REGION

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
                // 1. Índice persistente: si ya está construido, la pantalla carga al instante
                //    el catálogo COMPLETO de la plataforma (sin red y paginado).
                val indexKey = CollectionIndexUseCase.platformKey(brand.id)
                val hideNow = userPreferencesRepository.hideWatched.value
                if (loadFromIndex(brand, indexKey, hideNow, currentFormat, showAll = false)) {
                    return@launch
                }

                // 2. Primera apertura: construir solo esta plataforma en background mientras
                //    se pinta el fallback en vivo. Al terminar, la pantalla se refresca sola.
                CollectionIndexScheduler.enqueuePlatformNow(context, brand.id)

                val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)

                val tmdbSectionOrder = listOf("En Tendencia", "Novedades y Estrenos", "Series Destacadas", "Cine Aclamado")

                val updateUiAndCache = {
                    val currentSnapshot = synchronized(sections) {
                        dedupeAndOrderSections(sections, tmdbSectionOrder)
                    }
                    allRawSections = currentSnapshot
                    val (filteredSections, filteredItems) = filterSectionsAndItems(allRawSections, currentFormat)
                    val currentFocused = (_uiState.value as? BrandUiState.Success)?.focusedItem
                    val currentShowAll = (_uiState.value as? BrandUiState.Success)?.showAll ?: false
                    val successState = BrandUiState.Success(
                        brand = brand,
                        sections = filteredSections,
                        allItems = filteredItems,
                        allCount = filteredItems.size,
                        focusedItem = currentFocused ?: filteredSections.firstOrNull()?.items?.firstOrNull() ?: filteredItems.firstOrNull(),
                        format = currentFormat,
                        showAll = currentShowAll
                    )
                    _uiState.value = successState
                    brandCache[brandId] = successState.copy(sections = allRawSections, format = MediaFormatFilter.ALL)
                }

                Log.d("BrandVM", "=== Loading brand feed: id=${brand.id}, name=${brand.name}, providerId=${brand.providerId}, region=$effectiveRegion ===")

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
                            Log.d("BrandVM", "[${brand.id}] TMDB Trending raw: ${trendingMovies.size} movies, ${trendingTv.size} tv")
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
                            Log.d("BrandVM", "[${brand.id}] En Tendencia local matches (${displayItems.size}): ${displayItems.map { "${it.title} [${it.type}]" }}")
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
                            Log.d("BrandVM", "[${brand.id}] Series Destacadas matches (${displayItems.size}): ${displayItems.map { "${it.title} [${it.type}]" }}")
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
                            Log.d("BrandVM", "[${brand.id}] Cine Aclamado matches (${displayItems.size}): ${displayItems.map { "${it.title} [${it.type}]" }}")
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
                dedupeAndOrderSections(
                    sections,
                    listOf("En Tendencia", "Series Destacadas", "Cine Aclamado")
                )
            }
            val hide = userPreferencesRepository.hideWatched.value
            val (filteredSections, filteredItems) = filterSectionsAndItems(allRawSections, currentFormat, hide)
            val currentFocused = (_uiState.value as? BrandUiState.Success)?.focusedItem
            val currentShowAll = (_uiState.value as? BrandUiState.Success)?.showAll ?: false
            val successState = BrandUiState.Success(
                brand = brand,
                sections = filteredSections,
                allItems = filteredItems,
                allCount = filteredItems.size,
                focusedItem = currentFocused ?: filteredSections.firstOrNull()?.items?.firstOrNull() ?: filteredItems.firstOrNull(),
                format = currentFormat,
                hideWatched = hide,
                showAll = currentShowAll
            )
            if (sections.isNotEmpty()) {
                brandCache[brandId] = successState.copy(sections = allRawSections, format = MediaFormatFilter.ALL)
            }
            _uiState.value = successState
            if (brand.id != "trakt" && !successState.fromIndex) {
                pollIndexUntilReady(
                    brand = brand,
                    key = CollectionIndexUseCase.platformKey(brand.id),
                    hide = hide
                )
            }
        }
    }

    /**
     * Ordena las filas de una plataforma y elimina duplicados cruzados por id y por título
     * normalizado. La primera fila del orden se queda con el título; las siguientes lo omiten.
     */
    private fun dedupeAndOrderSections(
        sections: List<HomeSection>,
        sectionOrder: List<String>
    ): List<HomeSection> {
        val sorted = sections.sortedBy { section ->
            val index = sectionOrder.indexOf(section.title)
            if (index != -1) index else Int.MAX_VALUE
        }
        val seenIds = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()
        return sorted.mapNotNull { section ->
            val cleanItems = section.items.filter { item ->
                seenIds.add(item.id) && seenTitles.add(normalizeTitle(item.title))
            }
            if (cleanItems.isNotEmpty()) section.copy(items = cleanItems) else null
        }
    }

    private fun normalizeTitle(title: String): String =
        title.trim().lowercase().replace(Regex("[^a-z0-9áéíóúüñ]"), "")

    private fun typeForFormat(format: MediaFormatFilter): String? = when (format) {
        MediaFormatFilter.ALL -> null
        MediaFormatFilter.MOVIES -> "Movie"
        MediaFormatFilter.SERIES -> "Series"
    }

    /**
     * Carga la plataforma desde el índice persistente: sin red, paginada y con el catálogo
     * completo que haya en la biblioteca. Devuelve false si todavía no hay índice.
     */
    private suspend fun loadFromIndex(
        brand: BrandInfo,
        key: String,
        hide: Boolean,
        format: MediaFormatFilter,
        showAll: Boolean
    ): Boolean {
        val total = collectionIndexUseCase.countJoined(key, null, hide)
        if (total <= 0) return false

        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val type = typeForFormat(format)

        fun JellyfinMediaEntity.toItem(): MediaItem =
            toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)

        val sections = buildList {
            val seenIds = mutableSetOf<String>()
            val seenTitles = mutableSetOf<String>()

            fun List<JellyfinMediaEntity>.distinctNew(): List<JellyfinMediaEntity> =
                filter { item ->
                    seenIds.add(item.id) && seenTitles.add(normalizeTitle(item.title))
                }

            // 1. En Tendencia: los títulos con mayor tracción y tendencia de la plataforma
            val trending = collectionIndexUseCase.top(key, LANDING_ROW_SIZE, type, hide).distinctNew()
            if (trending.isNotEmpty()) {
                val title = when (format) {
                    MediaFormatFilter.MOVIES -> "Películas en Tendencia"
                    MediaFormatFilter.SERIES -> "Series en Tendencia"
                    MediaFormatFilter.ALL -> "En Tendencia"
                }
                add(HomeSection(title, trending.map { it.toItem() }))
            }

            // 2. Novedades y Estrenos: orden cronológico estricto por fecha de estreno (sin repetir)
            val recent = collectionIndexUseCase.recent(key, LANDING_ROW_SIZE * 2, type, hide)
                .distinctNew()
                .take(LANDING_ROW_SIZE)
            if (recent.isNotEmpty()) {
                val title = when (format) {
                    MediaFormatFilter.MOVIES -> "Novedades en Cine"
                    MediaFormatFilter.SERIES -> "Novedades en Series"
                    MediaFormatFilter.ALL -> "Novedades y Estrenos"
                }
                add(HomeSection(title, recent.map { it.toItem() }))
            }

            // 3. Series Destacadas (si la vista incluye series, sin repetir lo que ya está en Tendencia o Novedades)
            if (type == null || type == "Series") {
                val series = collectionIndexUseCase.top(key, LANDING_ROW_SIZE * 2, "Series", hide)
                    .distinctNew()
                    .take(LANDING_ROW_SIZE)
                if (series.isNotEmpty()) {
                    add(HomeSection("Series Destacadas", series.map { it.toItem() }))
                }
            }

            // 4. Cine Aclamado: obras con alta puntuación crítica (communityRating DESC >= 6.8f, sin repetir)
            if (type == null || type == "Movie") {
                val acclaimed = collectionIndexUseCase.topRated(key, LANDING_ROW_SIZE * 2, "Movie", hide)
                    .filter { (it.communityRating ?: 0f) >= 6.8f }
                    .distinctNew()
                    .take(LANDING_ROW_SIZE)
                if (acclaimed.isNotEmpty()) {
                    add(HomeSection("Cine Aclamado", acclaimed.map { it.toItem() }))
                }
            }
        }

        val filteredTotal = collectionIndexUseCase.countJoined(key, type, hide)
        val firstPage = collectionIndexUseCase.page(key, 0, PAGE_SIZE, type, hide)
            .map { it.toItem() }
        val success = BrandUiState.Success(
            brand = brand,
            sections = sections,
            allItems = firstPage,
            allCount = filteredTotal,
            focusedItem = sections.firstOrNull()?.items?.firstOrNull() ?: firstPage.firstOrNull(),
            format = format,
            hideWatched = hide,
            showAll = showAll,
            hasMoreAll = firstPage.size < filteredTotal,
            isLoadingAll = false,
            fromIndex = true
        )
        allRawSections = sections
        _uiState.value = success
        brandCache[brand.id] = success
        return true
    }

    /**
     * Si la plataforma aún no tenía índice, se lanza su construcción y se refresca la pantalla
     * en cuanto está lista (sin que el usuario tenga que salir y volver a entrar).
     */
    private suspend fun pollIndexUntilReady(brand: BrandInfo, key: String, hide: Boolean) {
        repeat(INDEX_POLL_ATTEMPTS) {
            delay(INDEX_POLL_DELAY_MS)
            if (!viewModelScope.isActive) return
            if (collectionIndexUseCase.countJoined(key, null, hide) > 0) {
                brandCache.remove(brand.id)
                if (loadFromIndex(brand, key, hide, currentFormat, showAll = false)) return
            }
        }
    }

    fun loadMoreAll() {
        val state = _uiState.value as? BrandUiState.Success ?: return
        if (!state.fromIndex || state.isLoadingAll || !state.hasMoreAll) return
        viewModelScope.launch {
            val current = _uiState.value as? BrandUiState.Success ?: return@launch
            _uiState.value = current.copy(isLoadingAll = true)
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val key = CollectionIndexUseCase.platformKey(current.brand.id)
            val type = typeForFormat(current.format)
            val hide = current.hideWatched
            val page = collectionIndexUseCase.page(
                key = key,
                offset = current.allItems.size,
                limit = PAGE_SIZE,
                type = type,
                hideWatched = hide
            )
            val items = page.map {
                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
            }
            val merged = (current.allItems + items).distinctBy { it.id }
            val total = collectionIndexUseCase.countJoined(key, type, hide)
            _uiState.value = current.copy(
                allItems = merged,
                allCount = total,
                hasMoreAll = merged.size < total,
                isLoadingAll = false
            )
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
