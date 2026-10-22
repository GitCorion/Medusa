package tv.medusa.app.ui.screens.home

import tv.medusa.app.data.local.db.AppDatabase
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.repository.SyncProgress
import tv.medusa.app.domain.model.MediaFormatFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import tv.medusa.app.R
import tv.medusa.app.data.local.ACCENT_CYAN
import tv.medusa.app.data.local.UserPreferences
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.usecase.GetHomeFeedUseCase
import tv.medusa.app.util.toOptimizedMediaItem
import tv.medusa.app.util.AppForeground
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object EmptySettings : HomeUiState
    data class EmptyLibrary(val serverUrl: String) : HomeUiState
    data class Success(
        val sections: List<HomeSection>,
        val genres: List<String> = emptyList(),
        val focusedItem: MediaItem? = null
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val getHomeFeedUseCase: GetHomeFeedUseCase,
    private val database: AppDatabase
) : ViewModel() {

    companion object {
        @Volatile
        private var lastStaticSections: List<HomeSection>? = null
        @Volatile
        private var lastStaticGenres: List<String> = emptyList()
    }

    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome
    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle
    val selectedPlatforms: StateFlow<Set<String>> = userPreferencesRepository.selectedPlatforms
    val isProUser: StateFlow<Boolean> = userPreferencesRepository.isProUser
    val seasonalAudioEnabled: StateFlow<Boolean> = userPreferencesRepository.seasonalAudioEnabled

    val syncProgress: StateFlow<SyncProgress> = MediaRepository.syncProgress
    val localMediaCount: StateFlow<Int> = mediaRepository.getLibraryStatsFlow()
        .map { it.total }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Estado mutado tanto desde el feed (Main) como desde el refresco de "Continuar Viendo"
    // (Dispatchers.IO) → @Volatile para garantizar visibilidad entre hilos.
    @Volatile
    private var lastSections: List<HomeSection>? = lastStaticSections
    @Volatile
    private var lastGenres: List<String> = lastStaticGenres

    /** Recarga pedida mientras el feed actual seguía cargándose (no se puede descartar). */
    private data class PendingReload(val forceRefresh: Boolean, val startSync: Boolean)

    private var isFeedLoading = false
    private var pendingReload: PendingReload? = null
    private var hasObservedPrefs = false
    private var lastObservedLastSync = ""

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val count = mediaRepository.getLocalCount()
            MediaRepository.checkActiveWorkerSync(context, count)
        }
        viewModelScope.launch {
            var isFirstForeground = true
            AppForeground.isForegroundFlow.collect { isForeground ->
                if (isForeground) {
                    if (!isFirstForeground) {
                        refreshContinueWatching()
                        if (_uiState.value is HomeUiState.Error) {
                            loadHomeFeed(forceRefresh = true)
                        }
                    }
                    isFirstForeground = false
                }
            }
        }
        viewModelScope.launch {
            var wasSyncing = false
            MediaRepository.syncProgress.collect { progress ->
                if (progress.isSyncing) {
                    wasSyncing = true
                } else if (wasSyncing && !progress.isSyncing) {
                    wasSyncing = false
                    val prefs = userPreferencesRepository.userPreferencesFlow.first()
                    if (prefs.jellyfinServerUrl.isNotBlank() && prefs.jellyfinAccessToken.isNotBlank() && mediaRepository.getLocalCount() > 0) {
                        GetHomeFeedUseCase.invalidateFeedCache()
                        MediaRepository.invalidateServerCaches()
                        lastStaticSections = null
                        lastSections = null
                        loadFeedForPrefs(prefs, forceRefresh = true, startSync = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            var lastTriggeredCount = 0
            localMediaCount.collect { count ->
                val currentSections = (_uiState.value as? HomeUiState.Success)?.sections ?: emptyList()
                val isSparse = currentSections.isEmpty() || currentSections.sumOf { it.items.size } < 10
                val countJump = count - lastTriggeredCount
                val isSyncing = MediaRepository.syncProgress.value.isSyncing
                // El refresco progresivo no debe limitarse a la instalación inicial: tras un
                // resync completo (p. ej. al subir SYNC_ALGORITHM_VERSION) ya existe una fecha de
                // último sync, así que `isInitialSync` era false y el Home se quedaba congelado
                // mientras se reconstruía el catálogo. Ahora basta con que haya sync activo y un
                // salto relevante de ítems.
                val shouldProgressiveRefresh = isSyncing && (
                    (isSparse && count >= 20 && countJump >= 50) ||
                    (count >= 200 && lastTriggeredCount == 0) ||
                    (countJump >= 500)
                )

                if (shouldProgressiveRefresh && !isFeedLoading) {
                    lastTriggeredCount = count
                    val prefs = userPreferencesRepository.userPreferencesFlow.first()
                    if (prefs.jellyfinServerUrl.isNotBlank() && prefs.jellyfinAccessToken.isNotBlank()) {
                        GetHomeFeedUseCase.invalidateFeedCache()
                        loadFeedForPrefs(prefs, forceRefresh = false, startSync = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                if (prefs.jellyfinServerUrl.isBlank() || prefs.jellyfinAccessToken.isBlank()) {
                    hasObservedPrefs = true
                    lastObservedLastSync = prefs.jellyfinLastSync
                    _uiState.value = HomeUiState.EmptySettings
                } else {
                    // `jellyfinLastSync` cambia justo al terminar un sync: hay que reconstruir el
                    // feed aunque la carga anterior siga en vuelo (antes la recarga se perdía y
                    // las listas se quedaban vacías hasta pulsar refrescar).
                    val firstEmission = !hasObservedPrefs
                    hasObservedPrefs = true
                    val syncCompleted = !firstEmission &&
                        prefs.jellyfinLastSync.isNotBlank() &&
                        prefs.jellyfinLastSync != lastObservedLastSync
                    lastObservedLastSync = prefs.jellyfinLastSync

                    if (syncCompleted) {
                        GetHomeFeedUseCase.invalidateFeedCache()
                        MediaRepository.invalidateServerCaches()
                    }
                    loadFeedForPrefs(
                        prefs = prefs,
                        forceRefresh = syncCompleted,
                        startSync = !syncCompleted
                    )
                }
            }
        }
    }

    fun loadHomeFeed(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (prefs.jellyfinServerUrl.isBlank() || prefs.jellyfinAccessToken.isBlank()) {
                _uiState.value = HomeUiState.EmptySettings
            } else {
                loadFeedForPrefs(prefs, forceRefresh)
            }
        }
    }

    fun forceRefreshHomeFeed() {
        GetHomeFeedUseCase.invalidateFeedCache()
        MediaRepository.invalidateServerCaches()
        lastStaticSections = null
        lastSections = null
        viewModelScope.launch(Dispatchers.IO) {
            getHomeFeedUseCase.clearSectionCache()
        }
        loadHomeFeed(forceRefresh = true)
    }

    fun activateDemoMode() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            tv.medusa.app.data.demo.DemoCatalogManager.populateDemoCatalog(
                database = database,
                prefsRepo = userPreferencesRepository
            )
        }
    }

    fun refreshContinueWatching() {
        val current = _uiState.value as? HomeUiState.Success ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (prefs.jellyfinServerUrl.isBlank() || prefs.jellyfinAccessToken.isBlank()) return@launch

            // 1. Refresco inmediato desde Room local (<10ms)
            val localItems = runCatching { mediaRepository.getContinueWatchingLocal(20) }.getOrDefault(emptyList())
            updateContinueWatchingSection(localItems, prefs)

            // 2. Refresco remoto desde Jellyfin (detecta nuevo capítulo NextUp o progreso sincronizado)
            val serverResult = mediaRepository.getContinueWatching(
                prefs.jellyfinServerUrl,
                prefs.jellyfinUserId,
                prefs.jellyfinAccessToken
            ).getOrNull()

            if (serverResult != null) {
                updateContinueWatchingSection(serverResult, prefs)
            }
        }
    }

    private fun updateContinueWatchingSection(
        entities: List<JellyfinMediaEntity>,
        prefs: UserPreferences
    ) {
        val current = _uiState.value as? HomeUiState.Success ?: return
        val currentSections = current.sections.toMutableList()
        val sectionIndex = currentSections.indexOfFirst { it.title == "Continuar Viendo" }

        if (entities.isEmpty()) {
            if (sectionIndex != -1) {
                currentSections.removeAt(sectionIndex)
                _uiState.value = current.copy(sections = currentSections)
                lastSections = currentSections
                lastStaticSections = currentSections
            }
        } else {
            val newItems = entities.take(15).map {
                it.toOptimizedMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
            }
            val newSection = HomeSection(title = "Continuar Viendo", items = newItems)
            if (sectionIndex != -1) {
                currentSections[sectionIndex] = newSection
            } else {
                currentSections.add(0, newSection)
            }
            _uiState.value = current.copy(sections = currentSections)
            lastSections = currentSections
            lastStaticSections = currentSections
        }
    }

    private fun loadFeedForPrefs(
        prefs: UserPreferences,
        forceRefresh: Boolean = false,
        startSync: Boolean = true
    ) {
        // Si ya hay una carga en curso, la recarga no se puede perder: se encola y se ejecuta
        // al terminar (p. ej. el sync acaba mientras el primer feed aún resuelve consultas).
        if (isFeedLoading) {
            val pending = pendingReload
            pendingReload = PendingReload(
                forceRefresh = (pending?.forceRefresh ?: false) || forceRefresh,
                startSync = (pending?.startSync ?: false) || startSync
            )
            return
        }
        isFeedLoading = true

        // Si ya tenemos snapshot previo, lo mostramos de inmediato (sin spinner)
        val cached = lastSections ?: lastStaticSections
        if (!cached.isNullOrEmpty()) {
            val filtered = filterSections(cached, _selectedFormat.value)
            val currentFocused = (_uiState.value as? HomeUiState.Success)?.focusedItem
            val validFocused = currentFocused?.takeIf { focused ->
                filtered.any { section -> section.items.any { it.id == focused.id } }
            }
            _uiState.value = HomeUiState.Success(
                sections = filtered,
                genres = lastGenres,
                focusedItem = validFocused
            )
        } else {
            _uiState.value = HomeUiState.Loading
        }

        // Launch background sync (delta sync if already populated, or full sync if empty).
        // Tras terminar un sync no se relanza otro desde aquí (el cambio de `jellyfinLastSync`
        // volvería a disparar el collector y encadenaría syncs en bucle); solo se refresca la UI.
        if (startSync) {
            viewModelScope.launch(Dispatchers.IO) {
                mediaRepository.startBackgroundSync(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    lastSyncTimestamp = prefs.jellyfinLastSync
                )
                // Solo enriquecer en paralelo si la biblioteca ya tiene contenido sincronizado;
                // en la sincronización inicial, JellyfinSyncWorker la encolará al terminar
                // para no saturar el servidor ni competir por el ancho de banda.
                if (!prefs.jellyfinLastSync.isNullOrBlank() || mediaRepository.getLocalCount() >= 500) {
                    mediaRepository.startBackgroundOverviewEnrichment(
                        serverUrl = prefs.jellyfinServerUrl,
                        userId = prefs.jellyfinUserId,
                        token = prefs.jellyfinAccessToken
                    )
                }
            }
        }
        viewModelScope.launch {
            try {
                var hasEmitted = false

                getHomeFeedUseCase(prefs, forceRefresh = forceRefresh).conflate().collect { sections ->
                    if (sections.isNotEmpty()) {
                        hasEmitted = true
                        lastSections = sections
                        lastStaticSections = sections
                        // La agregación de géneros es CPU-bound: fuera del hilo principal.
                        lastGenres = withContext(Dispatchers.Default) { mediaRepository.getMostWatchedGenres() }
                        lastStaticGenres = lastGenres
                        val filtered = filterSections(sections, _selectedFormat.value)
                        val currentFocused = (_uiState.value as? HomeUiState.Success)?.focusedItem
                        val validFocused = currentFocused?.takeIf { focused ->
                            filtered.any { section -> section.items.any { it.id == focused.id } }
                        }
                        _uiState.value = HomeUiState.Success(
                            sections = filtered,
                            genres = lastGenres,
                            focusedItem = validFocused
                        )
                    }
                }
                if (!hasEmitted && lastSections.isNullOrEmpty()) {
                    _uiState.value = HomeUiState.EmptyLibrary(prefs.jellyfinServerUrl)
                }
            } catch (e: Exception) {
                if (lastSections.isNullOrEmpty()) {
                    _uiState.value = HomeUiState.Error(e.localizedMessage ?: context.getString(R.string.home_error_connecting))
                }
            } finally {
                isFeedLoading = false
                pendingReload?.let { pending ->
                    pendingReload = null
                    viewModelScope.launch {
                        val latestPrefs = userPreferencesRepository.userPreferencesFlow.first()
                        if (latestPrefs.jellyfinServerUrl.isBlank() || latestPrefs.jellyfinAccessToken.isBlank()) {
                            _uiState.value = HomeUiState.EmptySettings
                        } else {
                            loadFeedForPrefs(
                                prefs = latestPrefs,
                                forceRefresh = pending.forceRefresh,
                                startSync = pending.startSync
                            )
                        }
                    }
                }
            }
        }
    }

    private val _selectedFormat = MutableStateFlow(MediaFormatFilter.ALL)
    val selectedFormat: StateFlow<MediaFormatFilter> = _selectedFormat.asStateFlow()

    fun setFormat(format: MediaFormatFilter) {
        _selectedFormat.value = format
        val sections = lastSections ?: return
        val filtered = filterSections(sections, format)
        val currentFocused = (_uiState.value as? HomeUiState.Success)?.focusedItem
        val validFocused = currentFocused?.takeIf { focused ->
            filtered.any { section -> section.items.any { it.id == focused.id } }
        }
        _uiState.value = HomeUiState.Success(
            sections = filtered,
            genres = lastGenres,
            focusedItem = validFocused
        )
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

    fun setFocusedItem(item: MediaItem?) {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            _uiState.value = currentState.copy(focusedItem = item)
        }
    }

    fun toggleFavorite(item: MediaItem) {
        val newFav = !item.isFavorite
        updateItemInState(item.id) { it.copy(isFavorite = newFav) }
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            mediaRepository.toggleFavorite(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                itemId = item.id,
                makeFavorite = newFav
            )
        }
    }

    fun togglePlayed(item: MediaItem) {
        val newPlayed = !item.isPlayed
        updateItemInState(item.id) {
            it.copy(
                isPlayed = newPlayed,
                playbackPositionTicks = if (newPlayed) 0L else it.playbackPositionTicks
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            mediaRepository.togglePlayed(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                itemId = item.id,
                makePlayed = newPlayed
            )
            refreshContinueWatching()
        }
    }

    fun removeFromContinueWatching(item: MediaItem) {
        updateItemInState(item.id) { it.copy(playbackPositionTicks = 0L) }
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            mediaRepository.clearPlaybackPosition(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                itemId = item.id
            )
            refreshContinueWatching()
        }
    }

    fun playFromBeginning(item: MediaItem, onPlay: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            mediaRepository.clearPlaybackPosition(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                itemId = item.id
            )
            withContext(Dispatchers.Main) {
                onPlay(item.id)
            }
        }
    }

    private fun updateItemInState(itemId: String, transform: (MediaItem) -> MediaItem) {
        val current = _uiState.value as? HomeUiState.Success ?: return
        val updatedSections = current.sections.map { section ->
            if (section.items.any { it.id == itemId }) {
                section.copy(items = section.items.map { if (it.id == itemId) transform(it) else it })
            } else {
                section
            }
        }
        val updatedFocused = if (current.focusedItem?.id == itemId) transform(current.focusedItem) else current.focusedItem
        _uiState.value = current.copy(sections = updatedSections, focusedItem = updatedFocused)
        lastSections = updatedSections
        lastStaticSections = updatedSections
    }
}
