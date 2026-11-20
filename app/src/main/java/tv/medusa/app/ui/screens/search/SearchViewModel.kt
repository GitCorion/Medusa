package tv.medusa.app.ui.screens.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.medusa.app.R
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.util.toOptimizedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val sections: List<HomeSection> = emptyList(),
    val results: List<MediaItem> = emptyList(),
    val totalHits: Int = 0,
    val focusedItem: MediaItem? = null,
    val statusMessage: String? = null,
    val validNextChars: Set<Char> = emptySet(),
    val suggestions: List<String> = emptyList(),
    val isPredictiveActive: Boolean = true
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle
    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var indexedCatalog: SearchPredictiveEngine.IndexedCatalog = SearchPredictiveEngine.IndexedCatalog.EMPTY
    private var searchJob: Job? = null
    private var predictiveJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val rawTitles = mediaRepository.getAllLocalTitles()
            val indexed = withContext(Dispatchers.Default) {
                SearchPredictiveEngine.IndexedCatalog.build(rawTitles)
            }
            indexedCatalog = indexed
            updatePredictiveState(_uiState.value.query)
        }
    }

    private fun updatePredictiveState(query: String) {
        predictiveJob?.cancel()
        predictiveJob = viewModelScope.launch(Dispatchers.Default) {
            val isPredictive = _uiState.value.isPredictiveActive
            val nextChars = if (isPredictive) {
                SearchPredictiveEngine.findValidNextCharacters(query, indexedCatalog)
            } else {
                SearchPredictiveEngine.ALL_KEYBOARD_CHARS
            }
            val suggestions = if (query.isNotBlank()) {
                SearchPredictiveEngine.generateAutocompleteSuggestions(query, indexedCatalog, limit = 5)
            } else {
                emptyList()
            }
            _uiState.update { current ->
                current.copy(
                    validNextChars = nextChars,
                    suggestions = suggestions
                )
            }
        }
    }

    fun appendChar(c: Char) {
        val newQuery = _uiState.value.query + c
        onQueryChange(newQuery)
    }

    fun onBackspace() {
        val current = _uiState.value.query
        if (current.isNotEmpty()) {
            val newQuery = current.dropLast(1)
            onQueryChange(newQuery)
        }
    }

    fun onClear() {
        onQueryChange("")
    }

    fun selectSuggestion(suggestion: String) {
        onQueryChange(suggestion)
    }

    fun togglePredictive() {
        val newActive = !_uiState.value.isPredictiveActive
        _uiState.update { it.copy(isPredictiveActive = newActive) }
        updatePredictiveState(_uiState.value.query)
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        updatePredictiveState(newQuery)
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            predictiveJob?.cancel()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    sections = emptyList(),
                    results = emptyList(),
                    totalHits = 0,
                    focusedItem = null,
                    statusMessage = null,
                    suggestions = emptyList(),
                    validNextChars = if (it.isPredictiveActive) indexedCatalog.allStartingChars else SearchPredictiveEngine.ALL_KEYBOARD_CHARS
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350) // Debounce adaptado a Android TV y Odroid N2+ para evitar ráfagas de I/O
            val queryClean = newQuery.trim()
            if (queryClean.isBlank()) return@launch

            val prefs = userPreferencesRepository.userPreferencesFlow.first()

            // 1. Capa 1: Búsqueda Local Instantánea en Room DB
            val localResults = withContext(Dispatchers.IO) {
                mediaRepository.searchLocalMedia(queryClean, limit = 50)
            }
            updateUiWithResults(
                results = localResults,
                baseUrl = prefs.jellyfinServerUrl,
                token = prefs.jellyfinAccessToken,
                queryClean = queryClean,
                isLoading = true
            )

            // 2. Capa 2: Búsqueda Remota en Jellyfin para lo que aún no está en la caché
            // local (biblioteca recién añadida o catálogo todavía sin sincronizar).
            if (prefs.jellyfinServerUrl.isNotBlank() && prefs.jellyfinAccessToken.isNotBlank()) {
                val remoteResults = withContext(Dispatchers.IO) {
                    mediaRepository.searchRemoteAndCache(
                        serverUrl = prefs.jellyfinServerUrl,
                        userId = prefs.jellyfinUserId,
                        token = prefs.jellyfinAccessToken,
                        query = queryClean,
                        limit = 30
                    ).getOrDefault(emptyList())
                }

                if (remoteResults.isNotEmpty()) {
                    // 3. Capa 3: Fusión Inteligente y Deduplicación estable
                    // Mantenemos los resultados locales que el usuario ya está viendo al principio
                    // para evitar que se inserten elementos encima mientras navega. Los nuevos remotos se añaden al final.
                    val combinedMap = LinkedHashMap<String, JellyfinMediaEntity>()
                    localResults.forEach { combinedMap[it.id] = it }
                    remoteResults.forEach { remote ->
                        combinedMap[remote.id] = remote // Actualiza metadatos si ya existía (conservando orden) o lo añade al final
                    }

                    val mergedResults = combinedMap.values.toList()
                    updateUiWithResults(
                        results = mergedResults,
                        baseUrl = prefs.jellyfinServerUrl,
                        token = prefs.jellyfinAccessToken,
                        queryClean = queryClean,
                        isLoading = false
                    )
                    return@launch
                }
            }

            // Si no hay resultados remotos o no hay red, mostramos los locales
            updateUiWithResults(
                results = localResults,
                baseUrl = prefs.jellyfinServerUrl,
                token = prefs.jellyfinAccessToken,
                queryClean = queryClean,
                isLoading = false
            )
        }
    }

    private fun updateUiWithResults(
        results: List<JellyfinMediaEntity>,
        baseUrl: String,
        token: String,
        queryClean: String,
        isLoading: Boolean
    ) {
        val allMediaItems = results.map { it.toMediaItem(baseUrl, token) }
        val movies = results.filter { it.type.equals("Movie", ignoreCase = true) }
        val series = results.filter { !it.type.equals("Movie", ignoreCase = true) }

        val sections = mutableListOf<HomeSection>()

        if (movies.isNotEmpty()) {
            sections.add(
                HomeSection(
                    title = "Películas",
                    items = movies.map { it.toMediaItem(baseUrl, token) },
                    badge = null
                )
            )
        }

        if (series.isNotEmpty()) {
            sections.add(
                HomeSection(
                    title = "Series",
                    items = series.map { it.toMediaItem(baseUrl, token) },
                    badge = null
                )
            )
        }

        val totalHits = results.size
        val currentFocused = _uiState.value.focusedItem
        val newFocused = if (currentFocused != null && results.any { it.id == currentFocused.id }) {
            currentFocused
        } else {
            allMediaItems.firstOrNull()
        }

        val statusMessage = when {
            totalHits == 0 && !isLoading -> context.getString(R.string.search_no_results, queryClean)
            totalHits == 0 && isLoading -> context.getString(R.string.search_counter_searching)
            else -> null
        }

        _uiState.update { current ->
            val newFocused = if (current.focusedItem != null && results.any { it.id == current.focusedItem.id }) {
                current.focusedItem
            } else {
                allMediaItems.firstOrNull()
            }
            current.copy(
                isLoading = isLoading,
                sections = sections,
                results = allMediaItems,
                totalHits = totalHits,
                focusedItem = newFocused,
                statusMessage = statusMessage
            )
        }
    }

    fun setFocusedItem(item: MediaItem) {
        _uiState.update { it.copy(focusedItem = item) }
    }

    private fun JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String): MediaItem {
        return toOptimizedMediaItem(baseUrl, token, MediaSource.JELLYFIN)
    }
}
