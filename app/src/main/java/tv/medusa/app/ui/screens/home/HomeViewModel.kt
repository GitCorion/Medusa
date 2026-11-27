package tv.medusa.app.ui.screens.home

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
import tv.medusa.app.domain.model.HomeSectionKind
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.usecase.GetHomeFeedUseCase
import tv.medusa.app.domain.usecase.GetSeasonalFeedUseCase
import tv.medusa.app.domain.model.SeasonalEventsManager
import tv.medusa.app.ui.components.contextmenu.MediaActionHandler
import tv.medusa.app.util.toOptimizedMediaItem
import tv.medusa.app.util.AppForeground
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val getSeasonalFeedUseCase: GetSeasonalFeedUseCase,
    private val mediaActionHandler: MediaActionHandler
) : ViewModel() {

    companion object {
        @Volatile
        private var lastStaticSections: List<HomeSection>? = null
        @Volatile
        private var lastStaticGenres: List<String> = emptyList()

        fun invalidateStaticCaches() {
            lastStaticSections = null
            lastStaticGenres = emptyList()
        }
    }

    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome
    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle
    val selectedPlatforms: StateFlow<Set<String>> = userPreferencesRepository.selectedPlatforms
    val seasonalAudioEnabled: StateFlow<Boolean> = userPreferencesRepository.seasonalAudioEnabled
    val particleScale: StateFlow<Float> = userPreferencesRepository.userPreferencesFlow
        .map { it.getEffectiveParticleScale(context) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val isBackdropEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.isBackdropEnabled(context) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val watchRegion: StateFlow<String> = userPreferencesRepository.userPreferencesFlow
        .map { it.watchRegion }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), tv.medusa.app.data.remote.ApiConfig.TMDB_WATCH_REGION)

    /**
     * Indica si hay alguna fuente para llenar las pantallas de plataforma (clave TMDB local o
     * proxy del plugin del servidor): sin ella (y sin catálogo remoto publicado) la fila de logos
     * llevaría a una pantalla vacía, así que se oculta.
     */
    val hasDiscoverySource: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.hasTmdbAccess }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncProgress: StateFlow<SyncProgress> = MediaRepository.syncProgress
    val localMediaCount: StateFlow<Int> = mediaRepository.getLibraryStatsFlow()
        .map { it.total }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _focusedItem = MutableStateFlow<MediaItem?>(null)
    val focusedItem: StateFlow<MediaItem?> = _focusedItem.asStateFlow()

    // Debe inicializarse ANTES de los bloques init: el colector de preferencias puede llamar a
    // loadFeedForPrefs durante la construcción del ViewModel.
    private val _selectedFormat = MutableStateFlow(MediaFormatFilter.ALL)
    val selectedFormat: StateFlow<MediaFormatFilter> = _selectedFormat.asStateFlow()

    private var focusDebounceJob: kotlinx.coroutines.Job? = null

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
    private var lastServerIdentity: String? = null

    init {
        viewModelScope.launch {
            mediaActionHandler.onRemoveFromContinueWatching.collect { item ->
                removeFromContinueWatching(item)
            }
        }
        viewModelScope.launch {
            mediaActionHandler.onMediaChanged.collect {
                refreshContinueWatching()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val count = mediaRepository.getLocalCount()
            MediaRepository.checkActiveWorkerSync(context, count)
        }
        // Precalienta el índice del especial activo (Halloween/Navidad) mientras el usuario
        // está en la Home. Si ya está fresco, ensureIndex no hace ninguna consulta pesada.
        viewModelScope.launch(Dispatchers.IO) {
            delay(6000L)
            if (tv.medusa.app.util.AppForeground.isPlaybackActive) return@launch
            SeasonalEventsManager.getActiveEvent()?.let { event ->
                getSeasonalFeedUseCase.ensureIndex(event.id)
            }
        }
        viewModelScope.launch {
            var isFirstForeground = true
            AppForeground.isForegroundFlow.collect { isForeground ->
                if (isForeground) {
                    if (!isFirstForeground) {
                        delay(2500L)
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
                    if (tv.medusa.app.util.AppForeground.isPlaybackActive) return@collect
                    val prefs = userPreferencesRepository.userPreferencesFlow.first()
                    if (prefs.jellyfinServerUrl.isNotBlank() && prefs.jellyfinAccessToken.isNotBlank() && mediaRepository.getLocalCount() > 0) {
                        GetHomeFeedUseCase.invalidateFeedCache()
                        MediaRepository.invalidateServerCaches()
                        lastStaticSections = null
                        lastSections = null
                        // Nuevo catálogo → reconstruir el índice de descubrimiento en background.
                        tv.medusa.app.data.worker.CollectionIndexScheduler.enqueueNow(context, force = false)
                        loadFeedForPrefs(prefs, forceRefresh = true, startSync = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            var lastTriggeredCount = 0
            localMediaCount.collect { count ->
                // Si el usuario está viendo una película/serie a pantalla completa, no recalcular
                // ni instanciar el feed en segundo plano: satura la RAM del SoC de TV y provoca OOM/cierre.
                if (tv.medusa.app.util.AppForeground.isPlaybackActive) return@collect

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
                // Cambio de servidor/usuario: el catálogo anterior ya se borró en Ajustes.
                val identity = "${prefs.jellyfinServerUrl}|${prefs.jellyfinUserId}"
                val identityChanged = lastServerIdentity != null && lastServerIdentity != identity
                lastServerIdentity = identity
                if (identityChanged) {
                    GetHomeFeedUseCase.invalidateFeedCache()
                    MediaRepository.invalidateServerCaches()
                    invalidateStaticCaches()
                    lastSections = null
                    lastGenres = emptyList()
                    _uiState.value = HomeUiState.Loading
                }

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
        loadHomeFeed(forceRefresh = true)
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
            val newSection = HomeSection(
                title = "Continuar Viendo",
                items = newItems,
                kind = HomeSectionKind.CONTINUE_WATCHING
            )
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
            // Pintar el snapshot persistente antes de que el feed termine sus consultas.
            viewModelScope.launch(Dispatchers.IO) {
                val snapshot = runCatching { getHomeFeedUseCase.restoreSnapshot(prefs) }.getOrNull()
                if (!snapshot.isNullOrEmpty() && _uiState.value is HomeUiState.Loading) {
                    lastSections = snapshot
                    lastStaticSections = snapshot
                    _uiState.value = HomeUiState.Success(
                        sections = filterSections(snapshot, _selectedFormat.value),
                        genres = lastGenres,
                        focusedItem = null
                    )
                }
            }
        }

        // Lanza enriquecimiento en segundo plano (vía WorkManager) para completar pósteres y descripciones
        // faltantes de forma continua sin bloquear la UI ni el inicio de la app.
        viewModelScope.launch(Dispatchers.IO) {
            // Dar margen al primer frame y a las consultas locales: el sync y el enriquecido
            // compiten por CPU/SQLite con la pantalla que el usuario está esperando.
            delay(2500L)
            if (tv.medusa.app.util.AppForeground.isPlaybackActive) return@launch
            if (prefs.jellyfinServerUrl.isNotBlank() && mediaRepository.getLocalCount() >= 10) {
                mediaRepository.startBackgroundOverviewEnrichment(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken
                )
            }
        }

        // Launch background sync (delta sync if already populated, or full sync if empty).
        if (startSync) {
            viewModelScope.launch(Dispatchers.IO) {
                delay(2500L)
                if (tv.medusa.app.util.AppForeground.isPlaybackActive) return@launch
                mediaRepository.startBackgroundSync(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    lastSyncTimestamp = prefs.jellyfinLastSync
                )
            }
        }
        viewModelScope.launch {
            try {
                var hasEmitted = false
                var cachedGenresList: List<String>? = null

                getHomeFeedUseCase(prefs, forceRefresh = forceRefresh).conflate().collect { sections ->
                    if (sections.isNotEmpty()) {
                        hasEmitted = true
                        lastSections = sections
                        lastStaticSections = sections
                        // La agregación de géneros se calcula una sola vez para evitar escaneos pesados de SQLite a mitad de scroll.
                        if (cachedGenresList == null || forceRefresh) {
                            cachedGenresList = withContext(Dispatchers.Default) { mediaRepository.getMostWatchedGenres() }
                        }
                        lastGenres = cachedGenresList ?: emptyList()
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
        focusDebounceJob?.cancel()
        if (item == null) {
            _focusedItem.value = null
            return
        }
        focusDebounceJob = viewModelScope.launch {
            delay(150L) // Debounce 150ms para navegación fluida
            _focusedItem.value = item
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
        val current = _uiState.value as? HomeUiState.Success
        if (current != null) {
            val updatedSections = current.sections.map { section ->
                if (section.title.equals("Continuar Viendo", ignoreCase = true) || section.kind == HomeSectionKind.CONTINUE_WATCHING) {
                    section.copy(items = section.items.filter { it.id != item.id && (item.seriesId.isNullOrBlank() || it.seriesId != item.seriesId) })
                } else {
                    section
                }
            }.filterNot { (it.title.equals("Continuar Viendo", ignoreCase = true) || it.kind == HomeSectionKind.CONTINUE_WATCHING) && it.items.isEmpty() }
            _uiState.value = current.copy(sections = updatedSections)
            lastSections = updatedSections
            lastStaticSections = updatedSections
        }
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            mediaRepository.clearPlaybackPosition(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                itemId = item.id
            )
            if (!item.seriesId.isNullOrBlank()) {
                mediaRepository.clearPlaybackPosition(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    itemId = item.seriesId
                )
            }
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
