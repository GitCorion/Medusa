package tv.medusa.app.ui.screens.seasonal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.SeasonalEvent
import tv.medusa.app.domain.usecase.GetSeasonalFeedUseCase
import tv.medusa.app.domain.usecase.SeasonalFeedResult
import javax.inject.Inject

private object SeasonalCache {
    val cache = mutableMapOf<String, SeasonalFeedResult>()
}

sealed interface SeasonalUiState {
    data object Loading : SeasonalUiState
    data class Success(
        val event: SeasonalEvent,
        val items: List<MediaItem>,
        val sections: List<HomeSection> = emptyList(),
        val format: MediaFormatFilter = MediaFormatFilter.ALL,
        val hideWatched: Boolean = false,
        val focusedItem: MediaItem? = null
    ) : SeasonalUiState
    data class Error(val message: String) : SeasonalUiState
}

@HiltViewModel
class SeasonalViewModel @Inject constructor(
    private val getSeasonalFeedUseCase: GetSeasonalFeedUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeasonalUiState>(SeasonalUiState.Loading)
    val uiState: StateFlow<SeasonalUiState> = _uiState.asStateFlow()

    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome
    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle
    val seasonalAudioEnabled: StateFlow<Boolean> = userPreferencesRepository.seasonalAudioEnabled
    val hideWatched: StateFlow<Boolean> = userPreferencesRepository.hideWatched

    private var rawResult: SeasonalFeedResult? = null
    private var currentFormat: MediaFormatFilter = MediaFormatFilter.ALL

    init {
        viewModelScope.launch {
            userPreferencesRepository.hideWatched.collect { hide ->
                val state = _uiState.value as? SeasonalUiState.Success ?: return@collect
                if (state.hideWatched != hide) {
                    applyFormatFilter(currentFormat, hide)
                }
            }
        }
    }

    fun loadFeed(eventId: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                SeasonalCache.cache.remove(eventId)
            }
            val cached = SeasonalCache.cache[eventId]
            if (cached != null) {
                rawResult = cached
                applyFormatFilter(currentFormat)
            } else {
                _uiState.value = SeasonalUiState.Loading
            }

            val result = getSeasonalFeedUseCase.getSeasonalFeed(eventId)
            if (result != null && result.sections.isNotEmpty()) {
                rawResult = result
                SeasonalCache.cache[eventId] = result
                applyFormatFilter(currentFormat)
            } else if (cached == null) {
                _uiState.value = SeasonalUiState.Error("No se encontraron contenidos de la festividad en tu catálogo.")
            }
        }
    }

    fun setFormat(format: MediaFormatFilter) {
        currentFormat = format
        applyFormatFilter(format)
    }

    fun toggleHideWatched() {
        viewModelScope.launch {
            userPreferencesRepository.toggleHideWatched()
        }
    }

    fun setFocusedItem(item: MediaItem?) {
        val current = _uiState.value
        if (current is SeasonalUiState.Success) {
            _uiState.value = current.copy(focusedItem = item)
        }
    }

    private fun applyFormatFilter(format: MediaFormatFilter, hideWatchedOverride: Boolean? = null) {
        val result = rawResult ?: return
        val hide = hideWatchedOverride ?: userPreferencesRepository.hideWatched.value
        val filteredSections = when (format) {
            MediaFormatFilter.ALL -> result.sections.mapNotNull { section ->
                val items = if (hide) section.items.filter { !it.isPlayed } else section.items
                if (items.isNotEmpty()) section.copy(items = items) else null
            }
            MediaFormatFilter.MOVIES -> result.sections.mapNotNull { section ->
                val filteredItems = section.items.filter { it.type.equals("Movie", ignoreCase = true) && (!hide || !it.isPlayed) }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
            MediaFormatFilter.SERIES -> result.sections.mapNotNull { section ->
                val filteredItems = section.items.filter { it.type.equals("Series", ignoreCase = true) && (!hide || !it.isPlayed) }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
        }

        val allItems = filteredSections.flatMap { it.items }.distinctBy { it.id }

        _uiState.value = SeasonalUiState.Success(
            event = result.event,
            items = allItems,
            sections = filteredSections,
            format = format,
            hideWatched = hide,
            focusedItem = allItems.firstOrNull()
        )
    }
}
