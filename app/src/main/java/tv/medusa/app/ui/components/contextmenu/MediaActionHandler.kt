package tv.medusa.app.ui.components.contextmenu

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor centralizado de acciones de medios para toda la aplicación.
 * Permite ejecutar acciones contextuales (favoritos, visto, continuar viendo, reproducir)
 * desde cualquier pantalla sin duplicar lógica en cada ViewModel.
 */
@Singleton
class MediaActionHandler @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _onRemoveFromContinueWatching = MutableSharedFlow<MediaItem>(extraBufferCapacity = 64)
    val onRemoveFromContinueWatching = _onRemoveFromContinueWatching.asSharedFlow()

    private val _onMediaChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val onMediaChanged = _onMediaChanged.asSharedFlow()

    fun toggleFavorite(item: MediaItem) {
        val newFav = !item.isFavorite
        scope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            mediaRepository.toggleFavorite(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                itemId = item.id,
                makeFavorite = newFav
            )
            _onMediaChanged.tryEmit(Unit)
        }
    }

    fun togglePlayed(item: MediaItem) {
        val newPlayed = !item.isPlayed
        scope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            mediaRepository.togglePlayed(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                itemId = item.id,
                makePlayed = newPlayed
            )
            _onMediaChanged.tryEmit(Unit)
        }
    }

    fun removeFromContinueWatching(item: MediaItem) {
        _onRemoveFromContinueWatching.tryEmit(item)
        scope.launch {
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
            tv.medusa.app.data.repository.MediaCaches.serverContinueWatchingCache.clear()
            tv.medusa.app.domain.usecase.GetHomeFeedUseCase.invalidateFeedCache()
            MediaRepository.invalidateServerCaches()
            _onMediaChanged.tryEmit(Unit)
        }
    }

    fun playFromBeginning(item: MediaItem, onPlay: (String) -> Unit) {
        scope.launch {
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
}
