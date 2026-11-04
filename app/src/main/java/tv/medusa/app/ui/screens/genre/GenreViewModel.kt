package tv.medusa.app.ui.screens.genre

import java.util.concurrent.ConcurrentHashMap
import android.util.Log
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.data.remote.ApiConfig
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.medusa.app.R
import tv.medusa.app.data.local.DEFAULT_TMDB_API_KEY
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.tmdb.TmdbApiService
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.usecase.FilterToLibraryUseCase
import tv.medusa.app.util.toOptimizedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias GenreFormat = MediaFormatFilter

sealed interface GenreUiState {
    object Loading : GenreUiState
    data class Success(
        val genreName: String,
        val sections: List<HomeSection>,
        val allItems: List<MediaItem> = emptyList(),
        val allCount: Int = 0,
        val format: GenreFormat = GenreFormat.ALL,
        val hideWatched: Boolean = false,
        val focusedItem: MediaItem? = null,
        val showAll: Boolean = false
    ) : GenreUiState
    data class Error(val message: String) : GenreUiState
}

@HiltViewModel
class GenreViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private val genreCache = ConcurrentHashMap<String, Pair<GenreUiState.Success, List<Pair<GenreFormat, HomeSection>>>>()

        fun clearAllCaches() = genreCache.clear()
    }

    private val _uiState = MutableStateFlow<GenreUiState>(GenreUiState.Loading)
    val uiState: StateFlow<GenreUiState> = _uiState.asStateFlow()

    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome
    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle
    val hideWatched: StateFlow<Boolean> = userPreferencesRepository.hideWatched

    private var formatSections: List<Pair<GenreFormat, HomeSection>> = emptyList()
    private var allRawItems: List<MediaItem> = emptyList()
    private var currentFormat: GenreFormat = GenreFormat.ALL
    private var currentGenre: String = ""

    init {
        viewModelScope.launch {
            userPreferencesRepository.hideWatched.collect { hide ->
                val state = _uiState.value as? GenreUiState.Success ?: return@collect
                if (state.hideWatched != hide) {
                    applyFormat(hide)
                }
            }
        }
    }

    fun openAll() {
        val state = _uiState.value as? GenreUiState.Success ?: return
        _uiState.value = state.copy(showAll = true)
    }

    fun closeAll() {
        val state = _uiState.value as? GenreUiState.Success ?: return
        _uiState.value = state.copy(showAll = false)
    }

    fun loadGenreFeed(genreName: String, forceRefresh: Boolean = false) {
        if (forceRefresh) {
            genreCache.remove(genreName)
        }
        val cached = genreCache[genreName]
        if (cached != null && cached.first.sections.isNotEmpty()) {
            currentGenre = genreName
            currentFormat = GenreFormat.ALL
            formatSections = cached.second
            allRawSectionsUpdate()
            _uiState.value = cached.first.copy(
                format = GenreFormat.ALL,
                focusedItem = cached.first.sections.firstOrNull()?.items?.firstOrNull(),
                showAll = false
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = GenreUiState.Loading
            currentGenre = genreName
            currentFormat = GenreFormat.ALL

            val prefs = userPreferencesRepository.userPreferencesFlow.first()

            // 1. Cargar ítems locales del género en paralelo (<50ms)
            val (localMovies, localSeries, recentMoviesList, recentSeriesList) = coroutineScope {
                val m = async { runCatching { mediaRepository.getMoviesByGenre(genreName, 150) }.getOrDefault(emptyList()) }
                val s = async { runCatching { mediaRepository.getSeriesByGenre(genreName, 150) }.getOrDefault(emptyList()) }
                val rm = async { runCatching { mediaRepository.getRecentMoviesByGenre(genreName, 15) }.getOrDefault(emptyList()) }
                val rs = async { runCatching { mediaRepository.getRecentSeriesByGenre(genreName, 15) }.getOrDefault(emptyList()) }
                listOf(m.await(), s.await(), rm.await(), rs.await())
            }

            val localMovieItems = localMovies.map {
                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
            }
            val localSeriesItems = localSeries.map {
                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
            }
            val recentMovies = recentMoviesList.map {
                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
            }
            val recentSeries = recentSeriesList.map {
                it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
            }

            allRawItems = (localMovieItems + localSeriesItems + recentMovies + recentSeries).distinctBy { it.id }

            val buildSections = { tmdbMovies: List<MediaItem>, tmdbSeries: List<MediaItem> ->
                val collected = mutableListOf<Pair<GenreFormat, HomeSection>>()
                val shownMediaIds = mutableSetOf<String>()
                val shownNormalizedTitles = mutableSetOf<String>()

                val unplayedLocalMovies = localMovieItems.filter { !it.isPlayed && it.playbackPositionTicks <= 0L }
                val unplayedLocalSeries = localSeriesItems.filter { !it.isPlayed && it.playbackPositionTicks <= 0L }

                // 1. TOP 10 IMPRESCINDIBLES (orden canónico Bayesiano y éxitos TMDB de la biblioteca)
                val curatedTopCandidates = (tmdbMovies + tmdbSeries + unplayedLocalMovies + unplayedLocalSeries)
                    .distinctBy { it.id }
                    .filter { !it.isPlayed && it.playbackPositionTicks <= 0L }
                    .take(10)

                if (curatedTopCandidates.isNotEmpty()) {
                    shownMediaIds.addAll(curatedTopCandidates.map { it.id })
                    shownNormalizedTitles.addAll(curatedTopCandidates.map { normalizeTitle(it.title) })
                    collected.add(
                        GenreFormat.ALL to HomeSection(
                            title = "Top 10 Imprescindibles",
                            items = curatedTopCandidates,
                            badge = null,
                            isRanked = true
                        )
                    )
                }

                val allLocal = (localMovieItems + localSeriesItems).distinctBy { it.id }

                // 2. JOYAS OCULTAS: No reproducidas, sin sobre-exposición, metadatos ricos y alta calidad bayesiana
                val hiddenGems = allLocal
                    .filter {
                        it.id !in shownMediaIds &&
                        normalizeTitle(it.title) !in shownNormalizedTitles &&
                        !it.isPlayed &&
                        it.playbackPositionTicks <= 0L
                    }
                    .filter {
                        val hasOverview = !it.overview.isNullOrBlank() && it.overview.length > 30
                        val hasBackdrop = !it.backdropUrl.isNullOrBlank()
                        hasOverview && hasBackdrop
                    }
                    .filter {
                        val titleLower = it.title.lowercase()
                        !titleLower.contains("short") &&
                        !titleLower.contains("promo") &&
                        !titleLower.contains("extra") &&
                        !titleLower.contains("trailer") &&
                        !titleLower.contains("featurette")
                    }
                    .take(15)

                if (hiddenGems.isNotEmpty()) {
                    shownMediaIds.addAll(hiddenGems.map { it.id })
                    shownNormalizedTitles.addAll(hiddenGems.map { normalizeTitle(it.title) })
                    collected.add(
                        GenreFormat.ALL to HomeSection(
                            title = "Joyas Ocultas",
                            items = hiddenGems,
                            badge = null
                        )
                    )
                }

                // 3. PELÍCULAS CURADAS DEL GÉNERO (en orden Bayesiano)
                val curatedMovies = (tmdbMovies + localMovieItems)
                    .distinctBy { it.id }
                    .filter { it.id !in shownMediaIds && normalizeTitle(it.title) !in shownNormalizedTitles }
                    .take(24)

                if (curatedMovies.isNotEmpty()) {
                    shownMediaIds.addAll(curatedMovies.map { it.id })
                    shownNormalizedTitles.addAll(curatedMovies.map { normalizeTitle(it.title) })
                    collected.add(
                        GenreFormat.MOVIES to HomeSection(
                            title = "Películas",
                            items = curatedMovies,
                            badge = null
                        )
                    )
                }

                // 4. SERIES CURADAS DEL GÉNERO (en orden Bayesiano)
                val curatedSeries = (tmdbSeries + localSeriesItems)
                    .distinctBy { it.id }
                    .filter { it.id !in shownMediaIds && normalizeTitle(it.title) !in shownNormalizedTitles }
                    .take(24)

                if (curatedSeries.isNotEmpty()) {
                    shownMediaIds.addAll(curatedSeries.map { it.id })
                    shownNormalizedTitles.addAll(curatedSeries.map { normalizeTitle(it.title) })
                    collected.add(
                        GenreFormat.SERIES to HomeSection(
                            title = "Series",
                            items = curatedSeries,
                            badge = null
                        )
                    )
                }

                // 5. NOVEDADES RECIENTES: orden estricto por fecha exacta de estreno (premiereDateMs) con fallback a año
                val recentCombined = (recentMovies + recentSeries)
                    .distinctBy { it.id }
                    .filter { it.id !in shownMediaIds && normalizeTitle(it.title) !in shownNormalizedTitles }
                    .filter { (it.year ?: 0) >= 1970 }
                    .sortedByDescending { it.premiereDateMs ?: (((it.year ?: 1970).toLong() - 1970L) * 31536000000L) }
                    .take(15)

                if (recentCombined.isNotEmpty()) {
                    collected.add(
                        GenreFormat.ALL to HomeSection(
                            title = "Novedades Recientes",
                            items = recentCombined,
                            badge = null
                        )
                    )
                }
                collected
            }

            // Emisión instantánea si hay contenido local disponible
            val initialSections = buildSections(emptyList(), emptyList())
            if (initialSections.isNotEmpty()) {
                formatSections = initialSections
                allRawSectionsUpdate()
                applyFormat()
            }

            // 2. Descubrimiento TMDB en segundo plano sin bloquear la pantalla
            val effectiveTmdbKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
            val movieGenreId = tmdbMovieGenreId(genreName)
            val tvGenreId = tmdbTvGenreId(genreName)
            // Si el género no está mapeado en TMDB, no inventamos un id (antes caía en Acción y
            // traía recomendaciones incongruentes): nos quedamos solo con el contenido local.
            if (effectiveTmdbKey.isNotBlank() && (movieGenreId != null || tvGenreId != null)) {
                try {
                    val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)

                    val (movieGenre, tvGenre) = coroutineScope {
                        val m = async {
                            if (movieGenreId == null) emptyList()
                            else runCatching {
                                api.discoverMoviesByGenre(apiKey = effectiveTmdbKey, genreId = movieGenreId)
                            }.getOrNull()?.results ?: emptyList()
                        }
                        val t = async {
                            if (tvGenreId == null) emptyList()
                            else runCatching {
                                api.discoverTvByGenre(apiKey = effectiveTmdbKey, genreId = tvGenreId)
                            }.getOrNull()?.results ?: emptyList()
                        }
                        Pair(m.await(), t.await())
                    }

                    val (matchedMovies, matchedSeries) = coroutineScope {
                        val mm = async {
                            filterToLibraryUseCase.filterTmdbItems(
                                tmdbItems = movieGenre.take(40),
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 40
                            )
                        }
                        val ms = async {
                            filterToLibraryUseCase.filterTmdbItems(
                                tmdbItems = tvGenre.take(40),
                                serverUrl = prefs.jellyfinServerUrl,
                                userId = prefs.jellyfinUserId,
                                token = prefs.jellyfinAccessToken,
                                maxCandidates = 40
                            )
                        }
                        Pair(mm.await(), ms.await())
                    }

                    Log.d("GenreVM", "TMDB discover for $genreName: matched ${matchedMovies.size} movies, ${matchedSeries.size} series")

                    val tmdbMovieItems = matchedMovies.map {
                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                    }
                    val tmdbSeriesItems = matchedSeries.map {
                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                    }

                    if (tmdbMovieItems.isNotEmpty() || tmdbSeriesItems.isNotEmpty()) {
                        formatSections = buildSections(tmdbMovieItems, tmdbSeriesItems)
                        allRawItems = (allRawItems + tmdbMovieItems + tmdbSeriesItems).distinctBy { it.id }
                        allRawSectionsUpdate()
                        applyFormat()
                    }
                } catch (e: Exception) {
                    Log.e("GenreVM", "Error enriching with TMDB for genre $genreName", e)
                }
            }

            if (formatSections.isEmpty()) {
                _uiState.value = GenreUiState.Error(context.getString(R.string.genre_empty_no_content, currentGenre))
            }
        }
    }

    private fun allRawSectionsUpdate() {
        allRawItems = (allRawItems + formatSections.flatMap { it.second.items }).distinctBy { it.id }
    }

    fun setFormat(format: GenreFormat) {
        if (currentFormat == format) return
        currentFormat = format
        applyFormat()
    }

    fun toggleHideWatched() {
        viewModelScope.launch {
            userPreferencesRepository.toggleHideWatched()
        }
    }

    private fun applyFormat(hideWatchedOverride: Boolean? = null) {
        if (formatSections.isEmpty()) {
            _uiState.value = GenreUiState.Error(context.getString(R.string.genre_empty_no_content, currentGenre))
            return
        }

        val hide = hideWatchedOverride ?: userPreferencesRepository.hideWatched.value
        val filtered = when (currentFormat) {
            GenreFormat.ALL -> formatSections.mapNotNull { (_, section) ->
                val items = if (hide) section.items.filter { !it.isPlayed } else section.items
                if (items.isNotEmpty()) section.copy(items = items) else null
            }
            GenreFormat.MOVIES -> formatSections.mapNotNull { (format, section) ->
                if (format == GenreFormat.SERIES) return@mapNotNull null
                val movieItems = section.items.filter { it.type.equals("Movie", ignoreCase = true) && (!hide || !it.isPlayed) }
                if (movieItems.isNotEmpty()) section.copy(items = movieItems) else null
            }
            GenreFormat.SERIES -> formatSections.mapNotNull { (format, section) ->
                if (format == GenreFormat.MOVIES) return@mapNotNull null
                val seriesItems = section.items.filter {
                    (it.type.equals("Series", ignoreCase = true) || it.type.equals("Episode", ignoreCase = true)) && (!hide || !it.isPlayed)
                }
                if (seriesItems.isNotEmpty()) section.copy(items = seriesItems) else null
            }
        }

        val filteredAllItems = when (currentFormat) {
            GenreFormat.ALL -> allRawItems.filter { !hide || !it.isPlayed }
            GenreFormat.MOVIES -> allRawItems.filter { it.type.equals("Movie", ignoreCase = true) && (!hide || !it.isPlayed) }
            GenreFormat.SERIES -> allRawItems.filter { (it.type.equals("Series", ignoreCase = true) || it.type.equals("Episode", ignoreCase = true)) && (!hide || !it.isPlayed) }
        }

        val currentShowAll = (_uiState.value as? GenreUiState.Success)?.showAll ?: false
        val firstItem = filtered.firstOrNull()?.items?.firstOrNull() ?: filteredAllItems.firstOrNull()

        val successState = GenreUiState.Success(
            genreName = currentGenre,
            sections = filtered,
            allItems = filteredAllItems,
            allCount = filteredAllItems.size,
            format = currentFormat,
            hideWatched = hide,
            focusedItem = firstItem,
            showAll = currentShowAll
        )
        _uiState.value = successState
        if (formatSections.isNotEmpty() && currentGenre.isNotBlank()) {
            genreCache[currentGenre] = Pair(successState, formatSections)
        }
    }

    fun setFocusedItem(item: MediaItem) {
        val currentState = _uiState.value
        if (currentState is GenreUiState.Success) {
            _uiState.value = currentState.copy(focusedItem = item)
        }
    }

    private fun normalizeTitle(title: String): String =
        title.trim().lowercase().replace(Regex("[^a-z0-9áéíóúüñ]"), "")

    private fun tmdbMovieGenreId(genreName: String): String? = when (genreName.lowercase()) {
        "acción", "accion" -> "28"
        "aventura" -> "12"
        "animación", "animacion" -> "16"
        "comedia" -> "35"
        "crimen" -> "80"
        "documental" -> "99"
        "drama" -> "18"
        "familia", "familiar" -> "10751"
        "fantasía", "fantasia" -> "14"
        "historia" -> "36"
        "terror" -> "27"
        "música", "musica" -> "10402"
        "misterio" -> "9648"
        "romance" -> "10749"
        "ciencia ficción", "ciencia ficcion" -> "878"
        "thriller", "suspense" -> "53"
        "bélica", "belica" -> "10752"
        "western" -> "37"
        else -> null
    }

    private fun tmdbTvGenreId(genreName: String): String? = when (genreName.lowercase()) {
        "acción", "accion", "aventura" -> "10759"
        "animación", "animacion" -> "16"
        "comedia" -> "35"
        "crimen" -> "80"
        "documental" -> "99"
        "drama" -> "18"
        "familia", "familiar" -> "10751"
        "misterio" -> "9648"
        "ciencia ficción", "ciencia ficcion", "fantasía", "fantasia" -> "10765"
        "bélica", "belica" -> "10768"
        "western" -> "37"
        "infantil", "kids" -> "10762"
        else -> null
    }

    private fun JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String, source: MediaSource): MediaItem =
        toOptimizedMediaItem(baseUrl, token, source)
}
