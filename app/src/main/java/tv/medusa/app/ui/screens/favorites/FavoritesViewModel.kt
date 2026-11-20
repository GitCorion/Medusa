package tv.medusa.app.ui.screens.favorites

import tv.medusa.app.domain.model.MediaFormatFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.util.toOptimizedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias FavoriteFilter = MediaFormatFilter

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState
    data class Success(
        val allItems: List<MediaItem>,
        val filteredItems: List<MediaItem>,
        val activeFilter: FavoriteFilter,
        val hideWatched: Boolean = false,
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
    val hideWatched: StateFlow<Boolean> = userPreferencesRepository.hideWatched

    private val _uiState = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private var currentFilter = FavoriteFilter.ALL

    init {
        // Observación reactiva continua de la base de datos Room.
        // Cualquier cambio (sincronización remota, toggle de favorito en ficha o fondo)
        // se refleja de inmediato en la pantalla sin necesidad de recargarla a mano.
        viewModelScope.launch {
            combine(
                mediaRepository.getFavoritesFlow(),
                userPreferencesRepository.userPreferencesFlow
            ) { entities, prefs ->
                val items = entities.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken) }
                items to prefs.hideWatched
            }.collect { (items, hide) ->
                val isSyncing = (_uiState.value as? FavoritesUiState.Success)?.isSyncing ?: false
                _uiState.value = FavoritesUiState.Success(
                    allItems = items,
                    filteredItems = applyFilter(items, currentFilter, hide),
                    activeFilter = currentFilter,
                    hideWatched = hide,
                    isSyncing = isSyncing
                )
            }
        }
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is FavoritesUiState.Success) {
                _uiState.value = FavoritesUiState.Loading
            } else {
                _uiState.value = currentState.copy(isSyncing = true)
            }

            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val hide = prefs.hideWatched

            // 1. Mostrar de inmediato la base local si el estado aún estaba en Loading
            if (_uiState.value is FavoritesUiState.Loading) {
                val local = mediaRepository.getFavoritesLocal()
                val mediaItems = local.map { it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken) }
                _uiState.value = FavoritesUiState.Success(
                    allItems = mediaItems,
                    filteredItems = applyFilter(mediaItems, currentFilter, hide),
                    activeFilter = currentFilter,
                    hideWatched = hide,
                    isSyncing = true
                )
            }

            // 2. Traer favoritos actualizados del servidor Jellyfin (Series, Películas y Episodios en paralelo)
            if (prefs.jellyfinServerUrl.isNotBlank() && prefs.jellyfinAccessToken.isNotBlank() && prefs.jellyfinUserId.isNotBlank()) {
                mediaRepository.getFavorites(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken
                )
            }

            // Marcar fin de sincronización
            val state = _uiState.value as? FavoritesUiState.Success
            if (state != null) {
                _uiState.value = state.copy(isSyncing = false)
            }
        }
    }

    fun setFilter(filter: FavoriteFilter) {
        currentFilter = filter
        val state = _uiState.value as? FavoritesUiState.Success ?: return
        _uiState.value = state.copy(
            activeFilter = filter,
            filteredItems = applyFilter(state.allItems, filter, state.hideWatched)
        )
    }

    fun toggleHideWatched() {
        viewModelScope.launch {
            userPreferencesRepository.toggleHideWatched()
        }
    }

    private fun applyFilter(items: List<MediaItem>, filter: FavoriteFilter, hideWatched: Boolean): List<MediaItem> {
        val byFormat = when (filter) {
            FavoriteFilter.ALL -> items
            FavoriteFilter.MOVIES -> items.filter { it.type.equals("Movie", ignoreCase = true) }
            FavoriteFilter.SERIES -> items.filter {
                it.type.equals("Series", ignoreCase = true) ||
                it.type.equals("TvProgram", ignoreCase = true) ||
                it.type.equals("Episode", ignoreCase = true) ||
                it.type.equals("tvshow", ignoreCase = true) ||
                it.type.equals("show", ignoreCase = true)
            }
        }
        return if (hideWatched) byFormat.filter { !it.isPlayed } else byFormat
    }

    private fun JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String): MediaItem {
        return toOptimizedMediaItem(baseUrl, token, MediaSource.JELLYFIN).copy(isFavorite = true)
    }
}
