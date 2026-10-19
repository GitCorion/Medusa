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

sealed interface SeasonalUiState {
    data object Loading : SeasonalUiState
    data class Success(
        val event: SeasonalEvent,
        val items: List<MediaItem>,
        val sections: List<HomeSection> = emptyList(),
        val format: MediaFormatFilter = MediaFormatFilter.ALL,
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

    private var rawResult: SeasonalFeedResult? = null
    private var currentFormat: MediaFormatFilter = MediaFormatFilter.ALL

    fun loadFeed(eventId: String) {
        viewModelScope.launch {
            _uiState.value = SeasonalUiState.Loading
            val result = getSeasonalFeedUseCase.getSeasonalFeed(eventId)
            if (result != null && result.sections.isNotEmpty()) {
                rawResult = result
                applyFormatFilter(currentFormat)
            } else {
                _uiState.value = SeasonalUiState.Error("No se encontraron contenidos de la festividad en tu catálogo.")
            }
        }
    }

    fun setFormat(format: MediaFormatFilter) {
        currentFormat = format
        applyFormatFilter(format)
    }

    fun setFocusedItem(item: MediaItem?) {
        val current = _uiState.value
        if (current is SeasonalUiState.Success) {
            _uiState.value = current.copy(focusedItem = item)
        }
    }

    private fun applyFormatFilter(format: MediaFormatFilter) {
        val result = rawResult ?: return
        val filteredSections = when (format) {
            MediaFormatFilter.ALL -> result.sections
            MediaFormatFilter.MOVIES -> result.sections.mapNotNull { section ->
                val filteredItems = section.items.filter { it.type.equals("Movie", ignoreCase = true) }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
            MediaFormatFilter.SERIES -> result.sections.mapNotNull { section ->
                val filteredItems = section.items.filter { it.type.equals("Series", ignoreCase = true) }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
        }

        val allItems = filteredSections.flatMap { it.items }.distinctBy { it.id }

        _uiState.value = SeasonalUiState.Success(
            event = result.event,
            items = allItems,
            sections = filteredSections,
            format = format,
            focusedItem = allItems.firstOrNull()
        )
    }
}
