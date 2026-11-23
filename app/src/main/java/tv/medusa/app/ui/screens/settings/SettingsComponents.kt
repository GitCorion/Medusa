package tv.medusa.app.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvColors
import tv.medusa.app.ui.util.handleTouchClick
import tv.medusa.app.ui.theme.TextPrimary

/**
 * Contenedor de tarjeta agrupada para secciones de ajustes.
 * Elimina la apariencia plana de "AI slop" agrupando opciones relacionadas
 * en una superficie obsidian de 14dp con separación visual clara y sin bordes grises ociosos.
 */
@Composable
fun TvSettingsGroupCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TvColors.TextTertiary,
                letterSpacing = 0.2.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x0CFFFFFF))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            content()
        }
    }
}

/**
 * Fila interactiva con Switch de TV para ajustes booleanos (Activar/Desactivar).
 * Presenta título prominente, subtítulo descriptivo del impacto de la opción,
 * y un switch de TV fluido con foco accesible.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    focusColor: Color,
    focusContent: Color,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier
            .handleTouchClick { onCheckedChange(!checked) }
            .onFocusChanged { isFocused = it.isFocused }
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(border = BorderStroke(1.5.dp, focusColor))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                        lineHeight = 16.sp
                    )
                }
            }

            // Switch visual de TV
            val switchTrackColor by animateColorAsState(
                targetValue = if (checked) (if (isFocused) focusContent else focusColor) else tv.medusa.app.ui.theme.TvPalette.GlassSelected,
                animationSpec = tween(durationMillis = 200),
                label = "switchTrack"
            )
            val thumbOffset by animateDpAsState(
                targetValue = if (checked) 20.dp else 2.dp,
                animationSpec = tween(durationMillis = 200),
                label = "switchThumb"
            )

            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(switchTrackColor)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (checked) (if (isFocused) focusColor else tv.medusa.app.ui.theme.TvPalette.FocusInk) else tv.medusa.app.ui.theme.TvPalette.TextMuted)
                )
            }
        }
    }
}

/**
 * Fila interactiva para ajustes que abren un modal, acción o navegación.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingActionRow(
    title: String,
    description: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    focusColor: Color,
    focusContent: Color,
    modifier: Modifier = Modifier
) {
    if (onClick != null) {
        var isFocused by remember { mutableStateOf(false) }

        Surface(
            onClick = onClick,
            modifier = modifier
                .handleTouchClick { onClick() }
                .onFocusChanged { isFocused = it.isFocused }
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(border = BorderStroke(1.5.dp, focusColor))
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) focusColor else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 12.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    if (!description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                            lineHeight = 16.sp
                        )
                    }
                }

                if (!value.isNullOrBlank()) {
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        color = if (isFocused) focusColor else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = if (isFocused) focusColor else tv.medusa.app.ui.theme.TvPalette.Placeholder,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = tv.medusa.app.ui.theme.TvPalette.TextMuted
                )
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = tv.medusa.app.ui.theme.TvPalette.Placeholder,
                        lineHeight = 16.sp
                    )
                }
            }

            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Selector segmentado en cápsula de TV de alta precisión con título y subtítulo explicativo.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> TvSettingsSegmentedRow(
    title: String,
    description: String? = null,
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    focusColor: Color,
    focusContent: Color,
    modifier: Modifier = Modifier
) {
    val isMonochrome = LocalIsMonochromeTheme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Contenedor bandeja
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x0EFFFFFF))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (option, label) ->
                val isSelected = option == selectedOption
                var isFocused by remember { mutableStateOf(false) }

                Surface(
                    onClick = { onOptionSelected(option) },
                    modifier = Modifier
                        .handleTouchClick { onOptionSelected(option) }
                        .onFocusChanged { isFocused = it.isFocused }
                        .weight(1f),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) (if (isMonochrome) Color(0x30FFFFFF) else focusColor.copy(alpha = 0.20f)) else Color.Transparent,
                        focusedContainerColor = focusColor,
                        contentColor = if (isSelected) (if (isMonochrome) TextPrimary else focusColor) else tv.medusa.app.ui.theme.TvPalette.TextMuted,
                        focusedContentColor = focusContent
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border.None,
                        focusedBorder = Border.None
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = if (isFocused) focusContent else (if (isMonochrome) TextPrimary else focusColor),
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isFocused) focusContent else (if (isSelected) (if (isMonochrome) TextPrimary else focusColor) else tv.medusa.app.ui.theme.TvPalette.TextMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Previsualización en vivo del renderizado real de subtítulos.
 * Simula una escena oscura de película a 3 metros de distancia.
 */
@Composable
fun TvSubtitlePreviewBox(
    subtitleSize: String,
    subtitleBackground: Boolean,
    sampleText: String,
    modifier: Modifier = Modifier
) {
    val fontSize = when (subtitleSize) {
        "SMALL" -> 14.sp
        "LARGE" -> 22.sp
        else -> 17.sp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(tv.medusa.app.ui.theme.TvPalette.FocusInk, Color(0xFF050811))
                )
            )
            .border(0.5.dp, tv.medusa.app.ui.theme.TvPalette.GlassIdle, RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val textModifier = if (subtitleBackground) {
            Modifier
                .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        } else {
            Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        }

        Text(
            text = sampleText,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = textModifier
        )
    }
}

