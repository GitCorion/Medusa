package tv.medusa.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.medusa.app.R
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvChipDefaults
import tv.medusa.app.ui.theme.TvSpacing
import tv.medusa.app.ui.util.handleTouchClick

/**
 * Barra universal de filtros de formato (Todo / Películas / Series) con conmutador de vistos.
 * Basada 100% en el estándar canónico [TvChipDefaults]:
 * - Altura: 36dp
 * - Cero bordes en reposo (Anti-slop)
 * - Foco: Borde 1.5dp blanco nítido, escala 1.0f
 * - Tipografía: DM Sans 12sp SemiBold sin font-padding residual
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FormatFilterBar(
    selected: MediaFormatFilter,
    onSelect: (MediaFormatFilter) -> Unit,
    accentColorKey: String,
    buttonStyleKey: String,
    isMonochrome: Boolean,
    hideWatched: Boolean = false,
    onToggleHideWatched: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = TvSpacing.ScreenSafeHorizontal, vertical = 6.dp),
    modifier: Modifier = Modifier
) {
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentColorKey)

    val showIcons = buttonStyleKey != BUTTON_STYLE_TEXT_ONLY
    val showText = buttonStyleKey != BUTTON_STYLE_ICONS_ONLY

    Row(
        modifier = modifier.padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggleHideWatched != null) {
            val hideWatchedLabel = stringResource(R.string.filter_hide_watched)
            Surface(
                onClick = { onToggleHideWatched() },
                shape = ClickableSurfaceDefaults.shape(shape = TvChipDefaults.Shape),
                scale = TvChipDefaults.scale(),
                colors = TvChipDefaults.colors(
                    isSelected = hideWatched,
                    isMonochrome = isMonochrome,
                    focusColor = focusColor,
                    focusContent = focusContent
                ),
                border = TvChipDefaults.border(),
                modifier = Modifier
                    .handleTouchClick { onToggleHideWatched() }
                    .semantics {
                        role = Role.Switch
                        toggleableState = ToggleableState(hideWatched)
                    }
            ) {
                Box(
                    modifier = Modifier
                        .height(TvChipDefaults.Height)
                        .padding(horizontal = if (!showText) 12.dp else TvChipDefaults.HorizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (showIcons) {
                            val icon = if (hideWatched) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
                            Icon(
                                imageVector = icon,
                                contentDescription = hideWatchedLabel,
                                modifier = Modifier.size(TvChipDefaults.IconSize)
                            )
                        }

                        if (showText) {
                            if (showIcons) Spacer(modifier = Modifier.width(TvChipDefaults.IconSpacing))
                            Text(
                                text = hideWatchedLabel,
                                style = TvChipDefaults.textStyle,
                                fontWeight = if (hideWatched) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        MediaFormatFilter.entries.forEach { format ->
            val isSelected = selected == format
            val formatLabel = when (format) {
                MediaFormatFilter.ALL -> stringResource(R.string.format_all)
                MediaFormatFilter.MOVIES -> stringResource(R.string.format_movies)
                MediaFormatFilter.SERIES -> stringResource(R.string.format_series)
            }
            Surface(
                onClick = { onSelect(format) },
                shape = ClickableSurfaceDefaults.shape(shape = TvChipDefaults.Shape),
                scale = TvChipDefaults.scale(),
                colors = TvChipDefaults.colors(
                    isSelected = isSelected,
                    isMonochrome = isMonochrome,
                    focusColor = focusColor,
                    focusContent = focusContent
                ),
                border = TvChipDefaults.border(),
                modifier = Modifier
                    .handleTouchClick { onSelect(format) }
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    }
            ) {
                Box(
                    modifier = Modifier
                        .height(TvChipDefaults.Height)
                        .padding(horizontal = if (!showText) 12.dp else TvChipDefaults.HorizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (showIcons) {
                            val icon = when (format) {
                                MediaFormatFilter.ALL -> Icons.Rounded.Apps
                                MediaFormatFilter.MOVIES -> Icons.Rounded.Movie
                                MediaFormatFilter.SERIES -> Icons.Rounded.Tv
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = formatLabel,
                                modifier = Modifier.size(TvChipDefaults.IconSize)
                            )
                        }

                        if (showText) {
                            if (showIcons) Spacer(modifier = Modifier.width(TvChipDefaults.IconSpacing))
                            Text(
                                text = formatLabel,
                                style = TvChipDefaults.textStyle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
