package tv.medusa.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import tv.medusa.app.R
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.util.handleTouchClick
import tv.medusa.app.ui.theme.TextPrimary

/**
 * Botón "Volver" cinemático para Android TV y pantallas táctiles.
 *
 * Filosofía Anti-Slop:
 * - Cero bordes ruidosos de 1px en reposo (fondo de cristal obsidiana 0x18FFFFFF).
 * - En foco: iluminación sólida CyanBioluminescent con texto/icono oscuro #06080D para legibilidad instantánea a 3m.
 * - Icono de flecha suave + texto "Volver", perfectamente alineado en escala y altura con los chips de filtro.
 * - Totalmente navegable por D-Pad del mando y táctil / ratón (handleTouchClick).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColorKey: String = "",
    isMonochrome: Boolean = LocalIsMonochromeTheme.current,
    showLabel: Boolean = true,
    buttonStyleKey: String? = null
) {
    val effectiveShowLabel = if (buttonStyleKey != null) {
        buttonStyleKey != BUTTON_STYLE_ICONS_ONLY
    } else {
        showLabel
    }
    val effectiveAccent = if (accentColorKey.isNotBlank()) accentColorKey else LocalAccentColor.current
    val focusColor = if (isMonochrome) TextPrimary else TvAccent.getColor(effectiveAccent)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(effectiveAccent)

    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        scale = tv.medusa.app.ui.theme.TvChipDefaults.buttonScale(),
        contentPadding = if (effectiveShowLabel) {
            tv.medusa.app.ui.theme.TvChipDefaults.ContentPadding
        } else {
            PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        },
        colors = ButtonDefaults.colors(
            containerColor = tv.medusa.app.ui.theme.TvPalette.GlassIdle,
            contentColor = tv.medusa.app.ui.theme.TvPalette.TextMuted,
            focusedContainerColor = focusColor,
            focusedContentColor = focusContent
        ),
        shape = ButtonDefaults.shape(tv.medusa.app.ui.theme.TvChipDefaults.Shape),
        border = tv.medusa.app.ui.theme.TvChipDefaults.buttonBorder(),
        modifier = modifier
            .height(tv.medusa.app.ui.theme.TvChipDefaults.Height)
            .onFocusChanged { isFocused = it.isFocused }
            .handleTouchClick { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = if (isFocused) focusContent else tv.medusa.app.ui.theme.TvPalette.TextMuted,
                modifier = Modifier.size(15.dp)
            )
            if (effectiveShowLabel) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.common_back),
                    color = if (isFocused) focusContent else TextPrimary,
                    style = tv.medusa.app.ui.theme.TvChipDefaults.textStyle
                )
            }
        }
    }
}

