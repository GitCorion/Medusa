package tv.medusa.app.ui.screens.medusa

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import tv.medusa.app.ui.theme.TextPrimary

object MedusaCategoryColors {
    val Cyan = Color(0xFF00A4DC)
    val SkyBlue = Color(0xFF38BDF8)
    val Violet = Color(0xFF7C3AED)
    val Purple = Color(0xFF8B5CF6)
    val Indigo = Color(0xFF6366F1)

    fun getColorFor(category: String, isMonochrome: Boolean = false): Color {
        if (isMonochrome) return TextPrimary
        // Solo azulitos y morados (identidad oficial Medusa), cero colorines
        return when (category) {
            "Acción", "Aventura", "Ciencia Ficción" -> Cyan
            "Terror", "Fantasía", "Animación" -> Violet
            "Comedia", "Romance", "Musical" -> Purple
            "Drama", "Crimen", "Documental" -> SkyBlue
            else -> Indigo
        }
    }
}

/**
 * Cápsula de temática ultra-elegante con cristal obsidian, micro-animación de escala y foco para Android TV.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MedusaMosaicChip(
    tag: MedusaMosaicTag,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMonochrome: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }

    val categoryColor = remember(tag.category, isMonochrome) {
        MedusaCategoryColors.getColorFor(tag.category, isMonochrome)
    }

    val backgroundColor = when {
        isFocused -> Color(0xFF1E222D)
        tag.isSelected -> tv.medusa.app.ui.theme.TvPalette.GlassSelected
        else -> Color(0x10FFFFFF)
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color(0xFF00F2FE), RoundedCornerShape(12.dp))
                } else if (tag.isSelected) {
                    Modifier.border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .semantics {
                role = Role.Checkbox
                selected = tag.isSelected
                contentDescription = tag.label
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Lado Izquierdo: Punto / Icono bioluminiscente + Nombre del tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (tag.isSelected) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(TextPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = tv.medusa.app.ui.theme.TvPalette.FocusInk,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.75f))
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = tag.label,
                    color = when {
                        isFocused -> TextPrimary
                        tag.isSelected -> TextPrimary
                        else -> tv.medusa.app.ui.theme.TvPalette.TextSoft
                    },
                    fontSize = 13.sp,
                    fontWeight = if (tag.isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Lado Derecho: Píldora con el conteo de obras que tienen esta etiqueta
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x22000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${tag.movieCount}",
                    color = if (isFocused) TextPrimary else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}
