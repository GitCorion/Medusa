package tv.medusa.app.ui.screens.seasonal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.SeasonalEvent
import tv.medusa.app.domain.usecase.GetSeasonalFeedUseCase
import tv.medusa.app.domain.usecase.SeasonalFeedResult
import javax.inject.Inject

sealed interface SeasonalUiState {
    data object Loading : SeasonalUiState

    data class Success(
        val event: SeasonalEvent,
        val sections: List<HomeSection> = emptyList(),
        val items: List<MediaItem> = emptyList(),
        val allCount: Int = 0,
        val format: MediaFormatFilter = MediaFormatFilter.ALL,
        val hideWatched: Boolean = false,
        val showAll: Boolean = false,
        val isLoadingAll: Boolean = false,
        val hasMoreAll: Boolean = false,
        val isRefreshing: Boolean = false
    ) : SeasonalUiState

    data class Error(val message: String) : SeasonalUiState
}

@HiltViewModel
class SeasonalViewModel @Inject constructor(
    private val getSeasonalFeedUseCase: GetSeasonalFeedUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeasonalUiState>(SeasonalUiState.Loading)
    val uiState: StateFlow<SeasonalUiState> = _uiState.asStateFlow()

    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome
    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle
    val seasonalAudioEnabled: StateFlow<Boolean> = userPreferencesRepository.seasonalAudioEnabled
    val hideWatched: StateFlow<Boolean> = userPreferencesRepository.hideWatched
    val particleScale: StateFlow<Float> = userPreferencesRepository.userPreferencesFlow
        .map { it.getEffectiveParticleScale(context) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    private var currentEventId: String = ""
    private var currentFormat: MediaFormatFilter = MediaFormatFilter.ALL
    private var loadJob: Job? = null
    private var allJob: Job? = null

    init {
        viewModelScope.launch {
            userPreferencesRepository.hideWatched.collect { hide ->
                val state = _uiState.value as? SeasonalUiState.Success ?: return@collect
                if (state.hideWatched != hide) {
                    reloadForCurrentFilter(hide)
                }
            }
        }
        // Un sync puede añadir títulos nuevos: al terminar, refrescamos el índice del especial
        // sin sacar al usuario de la pantalla ni mostrar un spinner.
        viewModelScope.launch {
            var wasSyncing = false
            MediaRepository.syncProgress.collect { progress ->
                if (progress.isSyncing) {
                    wasSyncing = true
                } else if (wasSyncing) {
                    wasSyncing = false
                    if (currentEventId.isNotBlank() && !tv.medusa.app.util.AppForeground.isPlaybackActive) {
                        loadFeed(currentEventId)
                    }
                }
            }
        }
    }

    fun loadFeed(eventId: String, forceRefresh: Boolean = false) {
        val eventChanged = currentEventId != eventId
        currentEventId = eventId
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (eventChanged) currentFormat = MediaFormatFilter.ALL
            val hide = prefs.hideWatched

            // 1. Si ya hay índice, pintar la landing cacheada en milisegundos.
            val status = getSeasonalFeedUseCase.getIndexStatus(eventId)
            if (status.itemCount > 0) {
                val landing = getSeasonalFeedUseCase.getLanding(eventId, currentFormat, hide)
                if (landing != null) {
                    _uiState.value = landing.toSuccess(hide = hide, refreshing = !status.isFresh)
                }
            } else {
                _uiState.value = SeasonalUiState.Loading
            }

            // 2. Construir/refrescar el índice en segundo plano.
            val count = getSeasonalFeedUseCase.ensureIndex(eventId, force = forceRefresh)
            if (count <= 0) {
                if (_uiState.value !is SeasonalUiState.Success) {
                    _uiState.value = SeasonalUiState.Error(
                        "No se encontraron contenidos de esta festividad en tu catálogo."
                    )
                }
                return@launch
            }

            val landing = getSeasonalFeedUseCase.getLanding(eventId, currentFormat, hide)
            if (landing != null) {
                val previous = _uiState.value as? SeasonalUiState.Success
                _uiState.value = landing.toSuccess(
                    hide = hide,
                    refreshing = false,
                    showAll = previous?.showAll ?: false,
                    items = previous?.items ?: emptyList(),
                    isLoadingAll = previous?.isLoadingAll ?: false,
                    hasMoreAll = previous?.hasMoreAll ?: false
                )
            }

            // 3. La selección editorial de Trakt/TMDb no bloquea: si llega, se incorpora.
            if (status.itemCount == 0 || !status.isFresh) {
                getSeasonalFeedUseCase.refreshEditorial(eventId)
                val refreshed = getSeasonalFeedUseCase.getLanding(eventId, currentFormat, hide)
                if (refreshed != null && _uiState.value is SeasonalUiState.Success) {
                    val current = _uiState.value as SeasonalUiState.Success
                    _uiState.value = refreshed.toSuccess(
                        hide = hide,
                        refreshing = false,
                        showAll = current.showAll,
                        items = current.items,
                        isLoadingAll = current.isLoadingAll,
                        hasMoreAll = current.hasMoreAll
                    )
                }
            }
        }
    }

    fun openAll() {
        val state = _uiState.value as? SeasonalUiState.Success ?: return
        if (state.showAll) return
        _uiState.value = state.copy(
            showAll = true,
            items = emptyList(),
            isLoadingAll = true,
            hasMoreAll = true
        )
        loadAllPage(reset = true)
    }

    fun closeAll() {
        val state = _uiState.value as? SeasonalUiState.Success ?: return
        allJob?.cancel()
        _uiState.value = state.copy(showAll = false, isLoadingAll = false)
    }

    fun loadMore() {
        val state = _uiState.value as? SeasonalUiState.Success ?: return
        if (!state.showAll || state.isLoadingAll || !state.hasMoreAll) return
        loadAllPage(reset = false)
    }

    private fun loadAllPage(reset: Boolean) {
        val state = _uiState.value as? SeasonalUiState.Success ?: return
        allJob?.cancel()
        allJob = viewModelScope.launch(Dispatchers.IO) {
            if (reset) {
                _uiState.value = state.copy(items = emptyList(), isLoadingAll = true, hasMoreAll = true)
            } else {
                _uiState.value = state.copy(isLoadingAll = true)
            }
            val current = _uiState.value as? SeasonalUiState.Success ?: return@launch
            val offset = if (reset) 0 else current.items.size
            val page = getSeasonalFeedUseCase.getPage(
                eventId = currentEventId,
                format = current.format,
                hideWatched = current.hideWatched,
                offset = offset
            )
            val latest = _uiState.value as? SeasonalUiState.Success ?: return@launch
            val merged = if (reset) {
                page.items.distinctBy { it.id }
            } else {
                (latest.items + page.items).distinctBy { it.id }
            }
            _uiState.value = latest.copy(
                items = merged,
                allCount = page.total,
                isLoadingAll = false,
                hasMoreAll = page.hasMore
            )
        }
    }

    fun setFormat(format: MediaFormatFilter) {
        if (currentFormat == format) return
        currentFormat = format
        val state = _uiState.value as? SeasonalUiState.Success ?: return
        _uiState.value = state.copy(format = format, isRefreshing = true)
        viewModelScope.launch(Dispatchers.IO) {
            val hide = userPreferencesRepository.userPreferencesFlow.first().hideWatched
            val landing = getSeasonalFeedUseCase.getLanding(currentEventId, format, hide)
            if (landing != null) {
                _uiState.value = landing.toSuccess(
                    hide = hide,
                    refreshing = false,
                    showAll = state.showAll,
                    items = emptyList(),
                    isLoadingAll = state.showAll,
                    hasMoreAll = true
                )
                if (state.showAll) loadAllPage(reset = true)
            }
        }
    }

    fun toggleHideWatched() {
        viewModelScope.launch {
            userPreferencesRepository.toggleHideWatched()
        }
    }

    private fun reloadForCurrentFilter(hide: Boolean) {
        val state = _uiState.value as? SeasonalUiState.Success ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val landing = getSeasonalFeedUseCase.getLanding(currentEventId, currentFormat, hide)
            if (landing != null) {
                _uiState.value = landing.toSuccess(
                    hide = hide,
                    refreshing = false,
                    showAll = state.showAll,
                    items = emptyList(),
                    isLoadingAll = state.showAll,
                    hasMoreAll = true
                )
                if (state.showAll) loadAllPage(reset = true)
            }
        }
    }

    private fun SeasonalFeedResult.toSuccess(
        hide: Boolean,
        refreshing: Boolean,
        showAll: Boolean = false,
        items: List<MediaItem> = emptyList(),
        isLoadingAll: Boolean = false,
        hasMoreAll: Boolean = false
    ): SeasonalUiState.Success = SeasonalUiState.Success(
        event = event,
        sections = sections,
        items = items,
        allCount = allCount,
        format = currentFormat,
        hideWatched = hide,
        showAll = showAll,
        isLoadingAll = isLoadingAll,
        hasMoreAll = hasMoreAll,
        isRefreshing = refreshing
    )
}
