package tv.medusa.app.ui.components.cinema

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import tv.medusa.app.ui.theme.AbyssGlassHighlight
import tv.medusa.app.ui.theme.CinematicTypography
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary
import tv.medusa.app.ui.util.handleTouchClick

/**
 * Gatillo Maestro de Proyector (Master Film Trigger).
 *
 * Filosofía Anti-Slop:
 * - Sustituye el botón-píldora genérico de Material por un disparador cinemático de doble cámara.
 * - Cámara izquierda: Acción táctil de reproducción con glifo óptico compensado.
 * - Cámara derecha: Especificación técnica y metadato del celuloide (ej: 4K HDR • ATMOS o duración).
 * - En reposo: Obsidiana noble con micro-bisel superior especular de 0.75dp.
 * - En foco: No se convierte en una pegatina sólida de color fosforito; emite un halo atmosférico
 *   trasero (Cinema Bloom) mientras la superficie mantiene su cuerpo chiseled.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MasterFilmTrigger(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    technicalSpec: String? = null,
    icon: ImageVector = Icons.Rounded.PlayArrow,
    focusColor: Color = Color.White,
    focusContentColor: Color = Color(0xFF070709),
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "triggerScale"
    )

    val cornerRadius = 10.dp
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier.scale(scale)
    ) {
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
            colors = ButtonDefaults.colors(
                containerColor = if (isFocused) focusColor else Color(0xFF16151B),
                contentColor = if (isFocused) focusContentColor else TextPrimary,
                focusedContainerColor = focusColor,
                focusedContentColor = focusContentColor
            ),
            shape = ButtonDefaults.shape(shape = shape),
            border = ButtonDefaults.border(
                border = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(
                        0.75.dp,
                        tv.medusa.app.ui.theme.TvPalette.GlassSelected
                    ),
                    shape = shape
                ),
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White
                    ),
                    shape = shape
                )
            ),
            modifier = Modifier
                .height(46.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .handleTouchClick { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cámara 1: Acción de Reproducción
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(19.dp)
                            .offset(x = 1.dp) // Compensación óptica del centro de gravedad del triángulo
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = CinematicTypography.TriggerButton,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFocused) focusContentColor else TextPrimary
                    )
                }

                // Separador de cámara y Cámara 2 (Especificación técnica)
                if (!technicalSpec.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(if (isFocused) focusContentColor.copy(alpha = 0.25f) else Color(0x30FFFFFF))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(
                                if (isFocused) focusColor.copy(alpha = 0.90f) else Color(0x24000000)
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = technicalSpec.uppercase(),
                            style = CinematicTypography.FilmCodecBadge,
                            color = if (isFocused) focusContentColor.copy(alpha = 0.90f) else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
