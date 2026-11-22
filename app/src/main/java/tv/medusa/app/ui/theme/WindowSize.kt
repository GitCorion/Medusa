package tv.medusa.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clases de tamaño ligeras, sin depender de material3-window-size-class.
 * La TV y las tablets grandes son EXPANDED; los móviles y ventanas pequeñas
 * (multi-window) son COMPACT.
 */
enum class AppWindowSize { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberAppWindowSize(): AppWindowSize {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width < 600 -> AppWindowSize.COMPACT
        width < 840 -> AppWindowSize.MEDIUM
        else -> AppWindowSize.EXPANDED
    }
}

val LocalAppWindowSize = compositionLocalOf { AppWindowSize.EXPANDED }

/** Columna del teclado 10-foot: más estrecha en pantallas compactas. */
@Composable
fun Modifier.adaptiveKeyboardColumnWidth(): Modifier =
    if (LocalAppWindowSize.current == AppWindowSize.COMPACT) {
        this.width(280.dp)
    } else {
        this.width(320.dp)
    }

/** Ancho de diálogo que se adapta a móvil/tablet en vez de desbordarse. */
@Composable
fun Modifier.adaptiveDialogWidth(maxWidth: Dp = 620.dp): Modifier {
    val size = LocalAppWindowSize.current
    return when (size) {
        AppWindowSize.COMPACT -> this.fillMaxWidth()
        AppWindowSize.MEDIUM -> this.widthIn(max = maxWidth)
        AppWindowSize.EXPANDED -> this.widthIn(max = maxWidth)
    }
}
