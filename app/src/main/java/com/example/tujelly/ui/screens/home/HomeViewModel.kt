package com.example.tujelly.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tujelly.data.local.ACCENT_CYAN
import com.example.tujelly.data.local.UserPreferences
import com.example.tujelly.data.local.UserPreferencesRepository
import com.example.tujelly.data.local.db.AppDatabase
import com.example.tujelly.data.repository.MediaRepository
import com.example.tujelly.domain.model.HomeSection
import com.example.tujelly.domain.model.MediaItem
import com.example.tujelly.domain.usecase.FilterToLibraryUseCase
import com.example.tujelly.domain.usecase.GetHomeFeedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        @Volatile
        private var lastStaticSections: List<HomeSection>? = null
        @Volatile
        private var lastStaticGenres: List<String> = emptyList()
    }

    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val database = AppDatabase.getDatabase(application)
    private val mediaRepository = MediaRepository(database.jellyfinDao(), userPreferencesRepository, database.tmdbVoteCacheDao())
    private val filterToLibraryUseCase = FilterToLibraryUseCase(mediaRepository)
    private val getHomeFeedUseCase = GetHomeFeedUseCase(
        mediaRepository = mediaRepository,
        filterToLibraryUseCase = filterToLibraryUseCase,
        homeSectionCacheDao = database.homeSectionCacheDao()
    )

    val accentColor: StateFlow<String> = userPreferencesRepository.userPreferencesFlow
        .map { it.accentColor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ACCENT_CYAN)

    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.isMonochrome }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val buttonStyle: StateFlow<String> = userPreferencesRepository.userPreferencesFlow
        .map { it.buttonStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.tujelly.data.local.BUTTON_STYLE_ICONS_ONLY)

    val selectedPlatforms: StateFlow<Set<String>> = userPreferencesRepository.userPreferencesFlow
        .map { it.selectedPlatforms }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val syncProgress: StateFlow<com.example.tujelly.data.repository.SyncProgress> = MediaRepository.syncProgress
    val localMediaCount: StateFlow<Int> = mediaRepository.getMediaCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastSections: List<HomeSection>? = lastStaticSections
    private var lastGenres: List<String> = lastStaticGenres
    private var isFeedLoading = false

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val count = mediaRepository.getLocalCount()
            MediaRepository.checkActiveWorkerSync(application, count)
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
                        loadFeedForPrefs(prefs)
                    }
                }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                if (prefs.jellyfinServerUrl.isBlank() || prefs.jellyfinAccessToken.isBlank()) {
                    _uiState.value = HomeUiState.EmptySettings
                } else {
                    loadFeedForPrefs(prefs)
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
        loadHomeFeed(forceRefresh = true)
    }

    private fun loadFeedForPrefs(prefs: UserPreferences, forceRefresh: Boolean = false) {
        if (isFeedLoading) return
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

        // Launch background sync (delta sync if already populated, or full sync if empty)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mediaRepository.startBackgroundSync(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                lastSyncTimestamp = prefs.jellyfinLastSync
            )
            mediaRepository.startBackgroundOverviewEnrichment(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken
            )
        }
        viewModelScope.launch {
            try {
                var hasEmitted = false
                lastGenres = mediaRepository.getMostWatchedGenres()
                lastStaticGenres = lastGenres

                getHomeFeedUseCase(prefs, forceRefresh = forceRefresh).collect { sections ->
                    if (sections.isNotEmpty()) {
                        hasEmitted = true
                        lastSections = sections
                        lastStaticSections = sections
                        lastGenres = mediaRepository.getMostWatchedGenres()
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
                    _uiState.value = HomeUiState.Error(e.localizedMessage ?: "Error conectando con Jellyfin")
                }
            } finally {
                isFeedLoading = false
            }
        }
    }

    private val _selectedFormat = MutableStateFlow(com.example.tujelly.domain.model.MediaFormatFilter.ALL)
    val selectedFormat: StateFlow<com.example.tujelly.domain.model.MediaFormatFilter> = _selectedFormat.asStateFlow()

    fun setFormat(format: com.example.tujelly.domain.model.MediaFormatFilter) {
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
        format: com.example.tujelly.domain.model.MediaFormatFilter
    ): List<HomeSection> {
        return when (format) {
            com.example.tujelly.domain.model.MediaFormatFilter.ALL -> rawSections
            com.example.tujelly.domain.model.MediaFormatFilter.MOVIES -> rawSections.mapNotNull { section ->
                val filteredItems = section.items.filter { it.type.equals("Movie", ignoreCase = true) }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
            com.example.tujelly.domain.model.MediaFormatFilter.SERIES -> rawSections.mapNotNull { section ->
                val filteredItems = section.items.filter {
                    it.type.equals("Series", ignoreCase = true) || it.type.equals("Episode", ignoreCase = true)
                }
                if (filteredItems.isNotEmpty()) section.copy(items = filteredItems) else null
            }
        }
    }

    fun setFocusedItem(item: MediaItem?) {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            _uiState.value = currentState.copy(focusedItem = item)
        }
    }
}
