package tv.medusa.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography
import tv.medusa.app.R

// =============================================================================
// Tipografías Cinematográficas Nativas: DM Serif Display & DM Sans
// =============================================================================
val DmSerifDisplayFamily = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal),
    Font(R.font.dm_serif_display_regular, FontWeight.Bold),
    Font(R.font.dm_serif_display_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.dm_serif_display_italic, FontWeight.Bold, FontStyle.Italic)
)

val DmSansFamily = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
    Font(R.font.dm_sans, FontWeight.SemiBold),
    Font(R.font.dm_sans, FontWeight.Bold),
    Font(R.font.dm_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.dm_sans_italic, FontWeight.Bold, FontStyle.Italic)
)

@OptIn(ExperimentalTvMaterial3Api::class)
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DmSerifDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DmSerifDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DmSerifDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, // Calibrado para legibilidad a 3m (anti-slop 10-foot)
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
    )
)

/**
 * Tokens tipográficos limpios y sobrios para pantallas de cine y sala de proyección (10-foot).
 */
object CinematicTypography {
    val HeroDisplay = TextStyle(
        fontFamily = DmSerifDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp,
        color = TextPrimary
    )

    val SectionTitle = TextStyle(
        fontFamily = DmSerifDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    )

    val SectionArchitectural = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary
    )

    val TriggerButton = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    )

    val FilmCodecBadge = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp, // Elevado desde 10.sp para descanso visual en sofá
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )

    val RatingScore = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )

    val RankedDisplay = TextStyle(
        fontFamily = DmSerifDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 114.sp,
        letterSpacing = (-4).sp
    )
}
