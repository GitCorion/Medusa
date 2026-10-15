package com.example.tujelly.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tujelly.data.local.UserPreferencesRepository
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import com.example.tujelly.data.repository.MediaRepository
import com.example.tujelly.domain.model.MediaItem
import com.example.tujelly.domain.model.MediaSource
import com.example.tujelly.util.toOptimizedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias FavoriteFilter = com.example.tujelly.domain.model.MediaFormatFilter

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState
    data class Success(
        val allItems: List<MediaItem>,
        val filteredItems: List<MediaItem>,
        val activeFilter: FavoriteFilter,
        val isSyncing: Boolean = false
    ) : FavoritesUiState
    data class Error(val message: String) : FavoritesUiState
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle

    private val _uiState = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private var currentFilter = FavoriteFilter.ALL

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _uiState.value = FavoritesUiState.Loading
            val prefs = userPreferencesRepository.userPreferencesFlow.first()

            // 1. First observe local DB favorites
            val local = mediaRepository.getFavoritesLocal()
            val mediaItems = local.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken) }
            _uiState.value = FavoritesUiState.Success(
                allItems = mediaItems,
                filteredItems = applyFilter(mediaItems, currentFilter),
                activeFilter = currentFilter,
                isSyncing = true
            )

            // 2. Fetch updated favorites from Jellyfin remote API
            if (prefs.jellyfinServerUrl.isNotBlank() && prefs.jellyfinAccessToken.isNotBlank()) {
                val remoteResult = mediaRepository.getFavorites(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken
                )
                val syncedList = remoteResult.getOrNull() ?: local
                val syncedItems = syncedList.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken) }
                _uiState.value = FavoritesUiState.Success(
                    allItems = syncedItems,
                    filteredItems = applyFilter(syncedItems, currentFilter),
                    activeFilter = currentFilter,
                    isSyncing = false
                )
            } else {
                _uiState.value = FavoritesUiState.Success(
                    allItems = mediaItems,
                    filteredItems = applyFilter(mediaItems, currentFilter),
                    activeFilter = currentFilter,
                    isSyncing = false
                )
            }
        }
    }

    fun setFilter(filter: FavoriteFilter) {
        currentFilter = filter
        val state = _uiState.value as? FavoritesUiState.Success ?: return
        _uiState.value = state.copy(
            activeFilter = filter,
            filteredItems = applyFilter(state.allItems, filter)
        )
    }

    private fun applyFilter(items: List<MediaItem>, filter: FavoriteFilter): List<MediaItem> {
        return when (filter) {
            FavoriteFilter.ALL -> items
            FavoriteFilter.MOVIES -> items.filter { it.type.equals("Movie", ignoreCase = true) }
            FavoriteFilter.SERIES -> items.filter {
                it.type.equals("Series", ignoreCase = true) || it.type.equals("TvProgram", ignoreCase = true)
            }
        }
    }

    private fun JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String): MediaItem {
        return toOptimizedMediaItem(baseUrl, token, MediaSource.JELLYFIN).copy(isFavorite = true)
    }
}
