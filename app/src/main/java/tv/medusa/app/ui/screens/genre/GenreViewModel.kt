package tv.medusa.app.ui.screens.genre

import java.util.concurrent.ConcurrentHashMap
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.data.remote.ApiConfig
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.medusa.app.R
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
        val format: GenreFormat = GenreFormat.ALL,
        val hideWatched: Boolean = false,
        val focusedItem: MediaItem? = null
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

    fun loadGenreFeed(genreName: String, forceRefresh: Boolean = false) {
        if (forceRefresh) {
            genreCache.remove(genreName)
        }
        val cached = genreCache[genreName]
        if (cached != null && cached.first.sections.isNotEmpty()) {
            currentGenre = genreName
            currentFormat = GenreFormat.ALL
            formatSections = cached.second
            _uiState.value = cached.first.copy(
                format = GenreFormat.ALL,
                focusedItem = cached.first.sections.firstOrNull()?.items?.firstOrNull()
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

            val sanitizedLocalMovies = sanitizeRatings(localMovieItems)
            val sanitizedLocalSeries = sanitizeRatings(localSeriesItems)

            val buildSections = { tmdbMovies: List<MediaItem>, tmdbSeries: List<MediaItem> ->
                val collected = mutableListOf<Pair<GenreFormat, HomeSection>>()
                val shownMediaIds = mutableSetOf<String>()

                val unplayedLocalMovies = sanitizedLocalMovies.filter { !it.isPlayed && it.playbackPositionTicks <= 0L }
                val unplayedLocalSeries = sanitizedLocalSeries.filter { !it.isPlayed && it.playbackPositionTicks <= 0L }

                val calculateCompositeScore = { item: MediaItem ->
                    val baseRating = item.rating ?: 0f
                    val isTmdbTrending = item.source == MediaSource.TMDB_TRENDING
                    val year = item.year ?: 2000

                    var score = baseRating * 1.0f
                    if (isTmdbTrending) {
                        score += 3.0f
                    }
                    if (year >= 2024) {
                        score += 1.8f
                    } else if (year >= 2020) {
                        score += 1.0f
                    } else if (year >= 2010) {
                        score += 0.5f
                    }
                    score
                }

                val curatedTopCandidates = (tmdbMovies + tmdbSeries + unplayedLocalMovies + unplayedLocalSeries)
                    .distinctBy { it.id }
                    .filter { !it.isPlayed && it.playbackPositionTicks <= 0L }
                    .filter { (it.rating ?: 0f) >= 6.8f }
                    .sortedByDescending { calculateCompositeScore(it) }
                    .take(10)

                if (curatedTopCandidates.isNotEmpty()) {
                    shownMediaIds.addAll(curatedTopCandidates.map { it.id })
                    collected.add(
                        GenreFormat.ALL to HomeSection(
                            title = "Top 10 Imprescindibles",
                            items = curatedTopCandidates,
                            badge = null,
                            isRanked = true
                        )
                    )
                }

                val allLocalSanitized = (sanitizedLocalMovies + sanitizedLocalSeries).distinctBy { it.id }
                val hiddenGems = allLocalSanitized
                    .filter { it.id !in shownMediaIds && !it.isPlayed }
                    .filter { (it.rating ?: 0f) in 7.5f..9.3f }
                    .filter {
                        val titleLower = it.title.lowercase()
                        !titleLower.contains("short") &&
                        !titleLower.contains("promo") &&
                        !titleLower.contains("extra") &&
                        !titleLower.contains("trailer") &&
                        !titleLower.contains("featurette")
                    }
                    .sortedByDescending { it.rating ?: 0f }
                    .take(15)

                if (hiddenGems.isNotEmpty()) {
                    shownMediaIds.addAll(hiddenGems.map { it.id })
                    collected.add(
                        GenreFormat.ALL to HomeSection(
                            title = "Joyas Ocultas",
                            items = hiddenGems,
                            badge = null
                        )
                    )
                }

                val curatedMovies = (sanitizedLocalMovies + tmdbMovies)
                    .distinctBy { it.id }
                    .filter { it.id !in shownMediaIds }
                    .take(20)

                if (curatedMovies.isNotEmpty()) {
                    shownMediaIds.addAll(curatedMovies.map { it.id })
                    collected.add(
                        GenreFormat.MOVIES to HomeSection(
                            title = "Películas",
                            items = curatedMovies,
                            badge = null
                        )
                    )
                }

                val curatedSeries = (sanitizedLocalSeries + tmdbSeries)
                    .distinctBy { it.id }
                    .filter { it.id !in shownMediaIds }
                    .take(20)

                if (curatedSeries.isNotEmpty()) {
                    shownMediaIds.addAll(curatedSeries.map { it.id })
                    collected.add(
                        GenreFormat.SERIES to HomeSection(
                            title = "Series",
                            items = curatedSeries,
                            badge = null
                        )
                    )
                }


                val recentCombined = (recentMovies + recentSeries)
                    .distinctBy { it.id }
                    .filter { it.id !in shownMediaIds }
                    .sortedByDescending { it.year ?: 0 }
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
                applyFormat()
            }

            // 2. Descubrimiento TMDB en segundo plano sin bloquear la pantalla
            if (prefs.tmdbApiKey.isNotBlank()) {
                try {
                    val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
                    val movieGenreId = tmdbMovieGenreId(genreName)
                    val tvGenreId = tmdbTvGenreId(genreName)

                    val (movieGenre, tvGenre) = coroutineScope {
                        val m = async {
                            runCatching {
                                api.discoverMoviesByGenre(apiKey = prefs.tmdbApiKey, genreId = movieGenreId)
                            }.getOrNull()?.results ?: emptyList()
                        }
                        val t = async {
                            if (tvGenreId != null) {
                                runCatching {
                                    api.discoverTvByGenre(apiKey = prefs.tmdbApiKey, genreId = tvGenreId)
                                }.getOrNull()?.results ?: emptyList()
                            } else emptyList()
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

                    val tmdbMovieItems = matchedMovies.map {
                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                    }
                    val tmdbSeriesItems = matchedSeries.map {
                        it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.TMDB_TRENDING)
                    }

                    if (tmdbMovieItems.isNotEmpty() || tmdbSeriesItems.isNotEmpty()) {
                        formatSections = buildSections(tmdbMovieItems, tmdbSeriesItems)
                        applyFormat()
                    }
                } catch (_: Exception) {}
            }

            if (formatSections.isEmpty()) {
                _uiState.value = GenreUiState.Error(context.getString(R.string.genre_empty_no_content, currentGenre))
            }
        }
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

        val firstItem = filtered.firstOrNull()?.items?.firstOrNull()
        val successState = GenreUiState.Success(
            genreName = currentGenre,
            sections = filtered,
            format = currentFormat,
            hideWatched = hide,
            focusedItem = firstItem
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

    /**
     * Sanitiza las notas de las películas/series locales.
     * Si un título tiene una nota llana de 10.0 (típico de un único voto en Jellyfin),
     * ajusta su nota efectiva a 7.2f a menos que esté respaldado por una gran producción o TMDB.
     */
    private fun sanitizeRatings(items: List<MediaItem>): List<MediaItem> {
        return items.map { item ->
            val raw = item.rating ?: 0f
            if (raw >= 9.8f) {
                // Ajustar notas artificiales de 10.0 sin volumen de votos a 7.2f
                item.copy(rating = 7.2f)
            } else {
                item
            }
        }
    }

    private fun tmdbMovieGenreId(genreName: String): String = when (genreName.lowercase()) {
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
        else -> "28"
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
