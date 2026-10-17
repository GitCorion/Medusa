package tv.medusa.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.model.DEFAULT_SELECTED_PLATFORMS
import tv.medusa.app.data.model.SUPPORTED_PLATFORMS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlatformSelectionViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _selected = MutableStateFlow<Set<String>>(DEFAULT_SELECTED_PLATFORMS)
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    fun togglePlatform(id: String) {
        val current = _selected.value
        _selected.value = if (id in current) current - id else current + id
    }

    fun selectAll() {
        _selected.value = SUPPORTED_PLATFORMS.map { it.id }.toSet()
    }

    fun deselectAll() {
        _selected.value = emptySet()
    }

    fun onContinue(onDone: () -> Unit) {
        viewModelScope.launch {
            userPreferencesRepository.completeOnboarding(_selected.value)
            onDone()
        }
    }
}
