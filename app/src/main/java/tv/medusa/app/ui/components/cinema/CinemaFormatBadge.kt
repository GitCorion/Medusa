package tv.medusa.app.ui.components.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import tv.medusa.app.ui.theme.CinematicTypography
import tv.medusa.app.ui.theme.GoldNitrate
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary

/**
 * Insignia de Formato y Celuloide (Cinema Format Badge).
 *
 * Reemplaza la cadena genérica unida por puntos medios ("2024 · 2h 15m · Drama") por
 * cartelas nítidas inspiradas en las marcas técnicas de cajas de negativo (35mm/70mm).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinemaFormatBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isHighlighted: Boolean = false,
    highlightColor: Color = GoldNitrate,
    horizontalPad: Dp = 8.dp,
    verticalPad: Dp = 4.dp
) {
    val shape = RoundedCornerShape(4.dp)
    val bgColor = if (isHighlighted) highlightColor.copy(alpha = 0.16f) else Color(0x18FFFFFF)
    val borderColor = if (isHighlighted) highlightColor.copy(alpha = 0.55f) else Color(0x22FFFFFF)
    val textColor = if (isHighlighted) highlightColor else TextPrimary

    Box(
        modifier = modifier
            .background(bgColor, shape)
            .border(0.6.dp, borderColor, shape)
            .padding(horizontal = horizontalPad, vertical = verticalPad)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text.uppercase(),
                style = CinematicTypography.FilmCodecBadge,
                fontSize = 10.sp,
                color = textColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
