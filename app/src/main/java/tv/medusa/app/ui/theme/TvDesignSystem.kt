package tv.medusa.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale
import tv.medusa.app.data.local.ACCENT_AMBER
import tv.medusa.app.data.local.ACCENT_CYAN
import tv.medusa.app.data.local.ACCENT_WHITE
import tv.medusa.app.data.local.INDICATOR_THEME_COLOR
import tv.medusa.app.data.local.PLATFORM_LOGO_COLOR
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

object TvColors {
    val Background = AbyssVoid
    val SurfaceDark = AbyssSurface
    val SurfaceElevated = AbyssSurfaceElevated
    val SurfaceGlass = AbyssGlass
    val SurfaceGlassFocused = Color.White

    val TextPrimary = tv.medusa.app.ui.theme.TextPrimary
    val TextSecondary = tv.medusa.app.ui.theme.TextSecondary
    val TextTertiary = tv.medusa.app.ui.theme.TextTertiary

    // Anti-slop: zero borders in idle state
    val BorderSubtle = Color.Transparent
    val BorderFocused = CyanBioluminescent
}

/**
 * Tokens de espaciado y métricas de diseño para Android TV (10-foot experience),
 * calibrados a partir de la ingeniería inversa de Apple TV (1080p reference screen model).
 */
object TvSpacing {
    val ScreenSafeHorizontal = 60.dp       // Margen lateral seguro de pantalla (PostPlay/Shelf safe horizontal)
    val TitleToShelfItems = 24.dp          // TitleToShelfItemsSpacing exacto de Apple TV
    val RaisedTitleToShelfItems = 18.dp    // RaisedTitleToShelfItemsSpacing para carrusel enfocado
    val ShelfBottomPadding = 24.dp         // Ritmo vertical inferior entre filas de contenido
    val ShelfTopPaddingBase = 94.dp        // Altura de cabecera base
    val ShelfItemSpacing = 16.dp           // Separación horizontal entre tarjetas de un carrusel
    val HeroHeight = 260.dp                // Altura de hero banner compacto (Epic short height)
    val BadgeHorizontalPad = 7.dp
    val BadgeVerticalPad = 3.dp
}

/**
 * Tokens de radio de esquina (Corner Radius) extraídos de ShadowLayer y Artwork styles.
 */
object TvCornerRadius {
    val Card = 12.dp                       // Artwork & ShadowLayer default corner radius
    val Hero = 12.dp                       // Contenedores principales
    val Dialog = 24.dp                     // Toast/panel corner radius
    val Modal = 29.dp                      // PostPlay outer radius
    val BadgeSmall = 6.dp                  // Small badge medium radius
    val BadgeMedium = 8.dp                 // Small badge large radius
    val BadgeLarge = 9.dp                  // Small badge xlarge radius
}

/**
 * Constantes de animación y microinteracciones (Motion).
 */
object TvMotion {
    const val DurationFadeMs: Int = 240    // Apple TV artwork & content fade duration
    const val DurationScaleMs: Int = 240   // Card scale & focus transition duration
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvRatingBadge(
    rating: Float?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (rating == null || rating <= 0f) return

    val formattedRating = String.format(Locale.US, "%.1f", rating)
    val isMonochrome = LocalIsMonochromeTheme.current

    val starTint = if (isMonochrome) Color.White else AmberCinema
    val badgeBg = if (isMonochrome) Color(0xCC000000) else Color(0xD006080D)

    Box(
        modifier = modifier
            .background(badgeBg, RoundedCornerShape(TvCornerRadius.BadgeSmall))
            .padding(
                horizontal = if (compact) 5.dp else TvSpacing.BadgeHorizontalPad,
                vertical = if (compact) 2.dp else TvSpacing.BadgeVerticalPad
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = starTint,
                modifier = Modifier.size(if (compact) 10.dp else 12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = formattedRating,
                color = Color.White,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = if (compact) 10.sp else 12.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0x18FFFFFF),
    textColor: Color = Color(0xFFE2E8F0),
    borderColor: Color? = null,
    fontSizeSp: Int = 10,
    horizontalPadDp: Dp = TvSpacing.BadgeHorizontalPad,
    verticalPadDp: Dp = TvSpacing.BadgeVerticalPad
) {
    Box(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(TvCornerRadius.BadgeSmall))
            .then(
                if (borderColor != null && borderColor != Color.Transparent) {
                    Modifier.border(0.5.dp, borderColor, RoundedCornerShape(TvCornerRadius.BadgeSmall))
                } else Modifier
            )
            .padding(horizontal = horizontalPadDp, vertical = verticalPadDp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

object TvAccent {
    fun getColor(key: String): Color {
        return when (key) {
            ACCENT_AMBER -> AmberCinema             // Amber Cinema Warm
            ACCENT_WHITE -> Color(0xFFFFFFFF)       // Pure White Minimal
            else -> CyanBioluminescent             // Medusa Cyan Bioluminescent
        }
    }

    fun getFocusedContentColor(key: String): Color {
        return CyanFocusedContent // Dark void #06080D high-contrast text on bright backgrounds
    }

    fun getDisplayName(key: String): String {
        return when (key) {
            ACCENT_AMBER -> "Ámbar Cine"
            ACCENT_WHITE -> "Blanco Puro"
            else -> "Cian Bioluminiscente"
        }
    }

    fun getDescription(key: String): String {
        return when (key) {
            ACCENT_AMBER -> "Tono ámbar dorado cálido inspirado en salas de cine clásicas."
            ACCENT_WHITE -> "Resalte blanco puro estilo Apple TV minimalista."
            else -> "Cian abisal vibrante con máximo contraste para salón a 3 metros."
        }
    }
}

val LocalIndicatorTheme = compositionLocalOf {
    INDICATOR_THEME_COLOR
}

val LocalPlatformLogoStyle = compositionLocalOf {
    PLATFORM_LOGO_COLOR
}

val LocalIsMonochromeTheme = compositionLocalOf {
    false
}

val LocalAccentColor = compositionLocalOf {
    ACCENT_CYAN
}

@Composable
fun currentFocusColor(
    isMonochrome: Boolean = LocalIsMonochromeTheme.current,
    accentKey: String = LocalAccentColor.current
): Color = if (isMonochrome) Color.White else TvAccent.getColor(accentKey)

@Composable
fun currentFocusContentColor(
    isMonochrome: Boolean = LocalIsMonochromeTheme.current,
    accentKey: String = LocalAccentColor.current
): Color = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentKey)

object TvButtonDefaults {
    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    fun primaryColors(
        focusColor: Color = currentFocusColor(),
        focusContent: Color = currentFocusContentColor(),
        containerColor: Color = Color(0x22FFFFFF),
        contentColor: Color = Color.White
    ) = androidx.tv.material3.ButtonDefaults.colors(
        containerColor = containerColor,
        contentColor = contentColor,
        focusedContainerColor = focusColor,
        focusedContentColor = focusContent
    )

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    fun subtleColors(
        focusColor: Color = currentFocusColor(),
        focusContent: Color = currentFocusContentColor(),
        containerColor: Color = Color(0x14FFFFFF),
        contentColor: Color = Color.White
    ) = androidx.tv.material3.ButtonDefaults.colors(
        containerColor = containerColor,
        contentColor = contentColor,
        focusedContainerColor = focusColor,
        focusedContentColor = focusContent
    )
}


