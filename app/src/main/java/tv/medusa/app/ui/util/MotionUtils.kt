package tv.medusa.app.ui.util

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Respeta el ajuste de accesibilidad "Remove animations" de Android. */
object MotionPrefs {
    fun reduceMotion(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)
}

/**
 * Marquesina que se desactiva cuando el sistema pide reducir el movimiento.
 * Mantiene el truncado con elipsis para que el título siga siendo legible.
 */
@Composable
fun Modifier.adaptiveMarquee(
    enabled: Boolean,
    initialDelayMillis: Int = 1000,
    repeatDelayMillis: Int = 1500,
    velocity: Dp = 35.dp
): Modifier {
    val context = LocalContext.current
    val reduceMotion = remember(context) { MotionPrefs.reduceMotion(context) }
    return if (enabled && !reduceMotion) {
        basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = initialDelayMillis,
            repeatDelayMillis = repeatDelayMillis,
            velocity = velocity
        )
    } else this
}
