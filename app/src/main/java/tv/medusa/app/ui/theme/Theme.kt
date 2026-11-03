package tv.medusa.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import tv.medusa.app.data.billing.BillingManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme

import tv.medusa.app.data.local.ACCENT_CYAN

private val DeepBackground = Color(0xFF0C0C12)
private val SurfaceDark = Color(0xFF161622)
private val TextWhite = Color(0xFFF5F5FA)
private val TextMuted = Color(0xFFB0B0C4)

val LocalBillingManager = staticCompositionLocalOf<BillingManager?> { null }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MedusaTheme(
    accentColorKey: String = ACCENT_CYAN,
    isMonochrome: Boolean = false,
    content: @Composable () -> Unit,
) {
    val primaryColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val onPrimaryColor = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentColorKey)
    val secondaryColor = if (isMonochrome) Color(0xFFCBD5E1) else primaryColor.copy(alpha = 0.75f)

    val tvColorScheme = tvDarkColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        background = DeepBackground,
        surface = SurfaceDark,
        onPrimary = onPrimaryColor,
        onSecondary = Color.White,
        onBackground = TextWhite,
        onSurface = TextWhite,
        onSurfaceVariant = TextMuted
    )

    val m3ColorScheme = m3DarkColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        background = DeepBackground,
        surface = SurfaceDark,
        onPrimary = onPrimaryColor,
        onSecondary = Color.White,
        onBackground = TextWhite,
        onSurface = TextWhite,
        onSurfaceVariant = TextMuted
    )

    TvMaterialTheme(
        colorScheme = tvColorScheme,
        typography = Typography
    ) {
        M3MaterialTheme(
            colorScheme = m3ColorScheme,
            content = content
        )
    }
}