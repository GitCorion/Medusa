package tv.medusa.app.ui.components.contextmenu

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        }
    }

    fun removeFromContinueWatching(item: MediaItem) {
        scope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            mediaRepository.clearPlaybackPosition(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                itemId = item.id
            )
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
