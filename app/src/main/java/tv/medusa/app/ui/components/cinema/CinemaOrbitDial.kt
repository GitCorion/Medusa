package tv.medusa.app.ui.components.cinema

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import tv.medusa.app.ui.theme.AbyssGlassHighlight
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.util.handleTouchClick

/**
 * Pulsador de Órbita Cinemática (Cinema Orbit Dial).
 *
 * Filosofía Anti-Slop:
 * - Sustituye el botón redondo plano de Material (CircleShape) por un dial con textura
 *   de objetivo fotográfico / anillo de lente anamórfica.
 * - En reposo: Cuerpo de obsidiana mate con anillo interior sutil concéntrico.
 * - En foco: Corona de refracción marfil/dorada y escala elástica, manteniendo el glifo
 *   grabado con nitidez.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinemaOrbitDial(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 46.dp,
    iconSizeDp: Dp = 20.dp,
    isActive: Boolean = false,
    focusColor: Color = Color.White,
    focusContentColor: Color = Color(0xFF070709),
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val bloomAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.32f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "orbitBloom"
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "orbitScale"
    )

    Box(
        modifier = modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
            colors = ButtonDefaults.colors(
                containerColor = when {
                    isFocused -> focusColor
                    isActive -> focusColor.copy(alpha = 0.22f)
                    else -> tv.medusa.app.ui.theme.AbyssSurface
                },
                contentColor = when {
                    isFocused -> focusContentColor
                    isActive -> focusColor
                    else -> TextPrimary
                },
                focusedContainerColor = focusColor,
                focusedContentColor = focusContentColor
            ),
            shape = ButtonDefaults.shape(shape = CircleShape),
            border = ButtonDefaults.border(
                border = androidx.tv.material3.Border.None,
                focusedBorder = androidx.tv.material3.Border(
                    border = BorderStroke(1.5.dp, Color.White),
                    shape = CircleShape
                )
            ),
            modifier = Modifier
                .size(sizeDp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .handleTouchClick { onClick() }
        ) {
            Box(
                modifier = Modifier
                    .size(sizeDp)
                    // Anillo concéntrico interno que simula el cilindro óptico de una lente
                    .border(
                        width = 0.5.dp,
                        color = if (isFocused) focusContentColor.copy(alpha = 0.20f) else Color(0x15FFFFFF),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSizeDp),
                    tint = when {
                        isFocused -> focusContentColor
                        isActive -> focusColor
                        else -> TextPrimary
                    }
                )
            }
        }
    }
}
