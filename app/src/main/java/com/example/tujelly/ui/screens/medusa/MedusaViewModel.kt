package com.example.tujelly.ui.screens.medusa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.tujelly.data.local.UserPreferencesRepository
import com.example.tujelly.data.local.db.JellyfinDao
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import com.example.tujelly.data.repository.MediaRepository
import com.example.tujelly.domain.model.MediaItem
import com.example.tujelly.domain.usecase.FilterToLibraryUseCase
import com.example.tujelly.util.toOptimizedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MedusaUiState(
    val mosaicTags: List<MedusaMosaicTag> = emptyList(),
    val activeChain: List<MedusaMosaicTag> = emptyList(),
    val matchingMovies: List<MediaItem> = emptyList(),
    val format: MediaFormat = MediaFormat.ALL,
    val showMoviesOverlay: Boolean = false,
    val totalCatalogCount: Int = 0,
    val isLoading: Boolean = true
)

typealias ConstellationUiState = MedusaUiState

@HiltViewModel
class MedusaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val jellyfinDao: JellyfinDao,
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase
) : ViewModel() {

    private val matrixEngine = MedusaTagMatrixEngine()
    private var rawCatalog: List<JellyfinMediaEntity> = emptyList()
    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var lastTmdbCanon: List<JellyfinMediaEntity> = emptyList()
    private var lastTmdbTrending: List<JellyfinMediaEntity> = emptyList()
    private var lastRecentWatched: List<JellyfinMediaEntity> = emptyList()
    private var lastFavorites: List<JellyfinMediaEntity> = emptyList()

    private var filterJob: Job? = null

    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle
    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome

    private val _uiState = MutableStateFlow(MedusaUiState())
    val uiState: StateFlow<MedusaUiState> = _uiState.asStateFlow()

    // Foco y selección aislados del uiState: cambian con cada movimiento del D-pad y antes
    // forzaban recomponer toda la pantalla (raíl de etiquetas + LazyRow) en cada pulsación.
    private val _focusedTag = MutableStateFlow<MedusaMosaicTag?>(null)
    val focusedTag: StateFlow<MedusaMosaicTag?> = _focusedTag.asStateFlow()

    private val _selectedMovie = MutableStateFlow<MediaItem?>(null)
    val selectedMovie: StateFlow<MediaItem?> = _selectedMovie.asStateFlow()

    init {
        TagTranslations.initCache(context)
        viewModelScope.launch {
            TagTranslations.syncRemoteDictionary(context)
        }
        loadData()
    }

    fun loadData() {
        filterJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            serverUrl = prefs.jellyfinServerUrl
            accessToken = prefs.jellyfinAccessToken

            val (catalog, localTop) = coroutineScope {
                val catD = async(Dispatchers.IO) {
                    runCatching { jellyfinDao.getAllCatalog() }.getOrDefault(emptyList())
                }
                val topD = async(Dispatchers.IO) {
                    runCatching { jellyfinDao.getTopRatedLocal(30) }.getOrDefault(emptyList())
                }
                Pair(catD.await(), topD.await())
            }
            rawCatalog = catalog

            val favorites = rawCatalog.filter { it.isFavorite }
            val recentWatched = rawCatalog.filter { it.isPlayed || it.playbackPositionTicks > 0 }

            lastFavorites = favorites
            lastRecentWatched = recentWatched

            val format = _uiState.value.format

            val (initialTags, initialMovies) = withContext(Dispatchers.Default) {
                matrixEngine.initialize(
                    catalog = rawCatalog,
                    tmdbTopRated = localTop,
                    tmdbTrending = localTop,
                    recentWatched = recentWatched,
                    favorites = favorites
                )
                val tags = matrixEngine.generateMosaicTags(emptyList(), format, targetCapacity = 20)
                val movs = matrixEngine.getMatchingMovies(emptyList(), format, serverUrl, accessToken)
                Pair(tags, movs)
            }

            _selectedMovie.value = initialMovies.firstOrNull()
            _uiState.update { current ->
                current.copy(
                    mosaicTags = initialTags,
                    matchingMovies = initialMovies,
                    activeChain = emptyList(),
                    totalCatalogCount = rawCatalog.size,
                    isLoading = false
                )
            }

            // Enriquecer en segundo plano con TMDB si hay conexión sin bloquear la UI
            val tmdbKey = prefs.tmdbApiKey.trim()
            if (tmdbKey.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val (matchedCanon, matchedTrending) = coroutineScope {
                        val cD = async {
                            val canonDto: List<com.example.tujelly.data.remote.tmdb.TmdbItemDto> = runCatching {
                                mediaRepository.getTmdbTopRated(tmdbKey).getOrDefault(emptyList())
                            }.getOrDefault(emptyList())
                            runCatching { filterToLibraryUseCase.filterTmdbItems(canonDto, maxCandidates = 30) }.getOrDefault(emptyList())
                        }
                        val tD = async {
                            val trendingDto: List<com.example.tujelly.data.remote.tmdb.TmdbItemDto> = runCatching {
                                mediaRepository.getTmdbTrendingDay(tmdbKey).getOrDefault(emptyList())
                            }.getOrDefault(emptyList())
                            runCatching { filterToLibraryUseCase.filterTmdbItems(trendingDto, maxCandidates = 30) }.getOrDefault(emptyList())
                        }
                        Pair(cD.await(), tD.await())
                    }

                    if (matchedCanon.isNotEmpty() || matchedTrending.isNotEmpty()) {
                        lastTmdbCanon = matchedCanon
                        lastTmdbTrending = matchedTrending
                        val updatedTags = withContext(Dispatchers.Default) {
                            matrixEngine.initialize(
                                catalog = rawCatalog,
                                tmdbTopRated = matchedCanon.ifEmpty { localTop },
                                tmdbTrending = matchedTrending.ifEmpty { localTop },
                                recentWatched = recentWatched,
                                favorites = favorites
                            )
                            matrixEngine.generateMosaicTags(_uiState.value.activeChain, _uiState.value.format, targetCapacity = 20)
                        }
                        _uiState.update { it.copy(mosaicTags = updatedTags) }
                    }
                }
            }
        }
    }

    /**
     * Aplica el filtrado de forma reactiva y totalmente asíncrona en Dispatchers.Default:
     * 1. Actualiza inmediatamente el estado de la cadena activa para que el mando TV responda a 60 FPS sin latencia.
     * 2. Cancela cualquier cálculo en vuelo previo para evitar saturar la CPU en ráfagas de clicks.
     * 3. Calcula en segundo plano las nuevas etiquetas supervivientes y las películas resultantes.
     */
    private fun applyFilter(
        newChain: List<MedusaMosaicTag>,
        format: MediaFormat,
        updateChainImmediately: Boolean = true
    ) {
        if (updateChainImmediately) {
            _uiState.update { it.copy(activeChain = newChain, format = format) }
        }
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val newTags = matrixEngine.generateMosaicTags(newChain, format, targetCapacity = 20)
            val newMovies = matrixEngine.getMatchingMovies(newChain, format, serverUrl, accessToken)

            _selectedMovie.value = newMovies.firstOrNull()
            _uiState.update { current ->
                current.copy(
                    activeChain = newChain,
                    format = format,
                    mosaicTags = newTags,
                    matchingMovies = newMovies
                )
            }
        }
    }

    /**
     * Alterna la selección de una etiqueta en la Constelación de Medusa:
     * - Si la etiqueta ya estaba en la cadena activa: la desconecta junto con las posteriores.
     * - Si no estaba: la conecta como un filtro "AND" estricto.
     */
    fun toggleTag(tag: MedusaMosaicTag) {
        val currentChain = _uiState.value.activeChain.toMutableList()
        val index = currentChain.indexOfFirst { it.rawTag.equals(tag.rawTag, ignoreCase = true) }

        val newChain = if (index >= 0) {
            // Desconectar esta y las posteriores
            currentChain.take(index)
        } else {
            // Añadir a la cadena de temáticas
            currentChain + tag.copy(isSelected = true)
        }

        applyFilter(newChain, _uiState.value.format)
    }

    fun removeTagFromChain(tag: MedusaMosaicTag) {
        val currentChain = _uiState.value.activeChain
        val newChain = currentChain.filterNot { it.rawTag.equals(tag.rawTag, ignoreCase = true) }
        applyFilter(newChain, _uiState.value.format)
    }

    fun resetChain() {
        _uiState.update { it.copy(showMoviesOverlay = false) }
        applyFilter(emptyList(), _uiState.value.format)
    }

    fun setFormat(format: MediaFormat) {
        if (_uiState.value.format == format) return
        applyFilter(_uiState.value.activeChain, format)
    }

    fun onTagFocused(tag: MedusaMosaicTag) {
        _focusedTag.value = tag
    }

    fun toggleMoviesOverlay() {
        if (_uiState.value.matchingMovies.isEmpty()) return
        val current = _uiState.value.showMoviesOverlay
        val next = !current
        _uiState.update { it.copy(showMoviesOverlay = next) }
        _selectedMovie.value = if (next) _uiState.value.matchingMovies.firstOrNull() else null
    }

    fun selectMovie(movie: MediaItem) {
        _selectedMovie.value = movie
    }

    fun dismissMovieSelection() {
        _selectedMovie.value = null
    }

    /**
     * Tecla ATRÁS:
     * 1. Si está viendo la película seleccionada -> la deselecciona.
     * 2. Si el visor overlay está abierto -> lo cierra.
     * 3. Si hay etiquetas activas conectadas -> desconecta la última (el mosaico se re-expande de forma asíncrona).
     * 4. Si la cadena está vacía -> devuelve false para volver al Home.
     */
    fun onBackPress(): Boolean {
        if (_selectedMovie.value != null) {
            _selectedMovie.value = null
            return true
        }

        if (_uiState.value.showMoviesOverlay) {
            _uiState.update { it.copy(showMoviesOverlay = false) }
            return true
        }

        val chain = _uiState.value.activeChain
        if (chain.isNotEmpty()) {
            val newChain = chain.dropLast(1)
            applyFilter(newChain, _uiState.value.format)
            return true
        }

        return false
    }

    fun surpriseMe(): MediaItem? {
        val currentMatches = _uiState.value.matchingMovies
        if (currentMatches.isNotEmpty()) {
            return currentMatches.filter { !it.isPlayed }.randomOrNull() ?: currentMatches.randomOrNull()
        }
        val pool = rawCatalog.filter { !it.isPlayed && (it.communityRating ?: 0f) >= 7.0f }
        return pool.randomOrNull()?.toOptimizedMediaItem(serverUrl, accessToken)
    }
}
