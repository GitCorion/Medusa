package tv.medusa.app.ui.components.contextmenu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.components.MediaContextMenuDialog

/**
 * Controlador de estado para el menú contextual a nivel global.
 */
@Stable
class ContextMenuController {
    var activeItem by mutableStateOf<MediaItem?>(null)
        private set

    fun showContextMenu(item: MediaItem) {
        activeItem = item
    }

    fun dismiss() {
        activeItem = null
    }
}

val LocalContextMenuController = staticCompositionLocalOf<ContextMenuController?> { null }

/**
 * Host global del menú contextual.
 * Envuelve el árbol de navegación proveyendo [LocalContextMenuController],
 * permitiendo que cualquier [MediaCard] en cualquier pantalla (Inicio, Favoritos,
 * Ficha de detalle, Búsqueda, Géneros, Marcas) invoque el menú contextual automáticamente
 * al mantener pulsado el botón de selección del mando.
 */
@Composable
fun ContextMenuHost(
    controller: ContextMenuController,
    actionHandler: MediaActionHandler,
    accentColorKey: String,
    isMonochrome: Boolean,
    onPlayMedia: (String) -> Unit,
    onDetailMedia: (String) -> Unit,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalContextMenuController provides controller) {
        content()

        controller.activeItem?.let { item ->
            MediaContextMenuDialog(
                item = item,
                accentColorKey = accentColorKey,
                isMonochrome = isMonochrome,
                onPlayMedia = { id ->
                    controller.dismiss()
                    onPlayMedia(id)
                },
                onPlayFromBeginning = { mediaItem ->
                    controller.dismiss()
                    actionHandler.playFromBeginning(mediaItem, onPlayMedia)
                },
                onDetailMedia = { id ->
                    controller.dismiss()
                    onDetailMedia(id)
                },
                onToggleFavorite = { mediaItem ->
                    actionHandler.toggleFavorite(mediaItem)
                    controller.dismiss()
                },
                onTogglePlayed = { mediaItem ->
                    actionHandler.togglePlayed(mediaItem)
                    controller.dismiss()
                },
                onRemoveFromContinue = { mediaItem ->
                    actionHandler.removeFromContinueWatching(mediaItem)
                    controller.dismiss()
                },
                onDismiss = { controller.dismiss() }
            )
        }
    }
}
