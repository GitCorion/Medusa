package tv.medusa.app.ui.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Extension para Modifier que añade soporte táctil (tap y long-press)
 * a los componentes de Jetpack Compose para TV (Button, Card, Surface, Tab, etc.)
 * que por defecto solo responden a eventos de D-Pad / Teclado.
 */
fun Modifier.handleTouchClick(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = if (enabled) {
    this.pointerInput(onClick, onLongClick) {
        detectTapGestures(
            onLongPress = { if (onLongClick != null) onLongClick() },
            onTap = { onClick() }
        )
    }
} else this
