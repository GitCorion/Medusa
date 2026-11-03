package tv.medusa.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.util.handleTouchClick

/**
 * Universal format filter bar (All / Movies / Series) with watched filter toggle.
 * Follows 10-foot Android TV guidelines with standard 8dp corner radius and focus scaling.
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
    modifier: Modifier = Modifier
) {
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentColorKey)

    val showIcons = buttonStyleKey != BUTTON_STYLE_TEXT_ONLY
    val showText = buttonStyleKey != BUTTON_STYLE_ICONS_ONLY

    val buttonPadding = if (!showText) {
        PaddingValues(0.dp)
    } else {
        PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    }

    val chipShape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggleHideWatched != null) {
            val hideWatchedLabel = stringResource(R.string.filter_hide_watched)
            Button(
                onClick = { onToggleHideWatched() },
                contentPadding = buttonPadding,
                scale = ButtonDefaults.scale(focusedScale = 1.05f),
                colors = ButtonDefaults.colors(
                    containerColor = if (hideWatched) (if (isMonochrome) Color(0x30FFFFFF) else focusColor.copy(alpha = 0.22f)) else (if (isMonochrome) Color(0x10FFFFFF) else focusColor.copy(alpha = 0.08f)),
                    contentColor = if (hideWatched) (if (isMonochrome) Color.White else focusColor) else (if (isMonochrome) Color(0xFF94A3B8) else Color(0xFFBAE6FD)),
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                ),
                shape = ButtonDefaults.shape(shape = chipShape),
                border = ButtonDefaults.border(
                    border = Border(
                        border = BorderStroke(
                            width = if (hideWatched) 1.dp else 0.5.dp,
                            color = if (hideWatched) (if (isMonochrome) Color(0x40FFFFFF) else focusColor.copy(alpha = 0.45f)) else (if (isMonochrome) Color(0x18FFFFFF) else focusColor.copy(alpha = 0.22f))
                        ),
                        shape = chipShape
                    ),
                    focusedBorder = Border.None
                ),
                modifier = Modifier
                    .handleTouchClick { onToggleHideWatched() }
                    .height(34.dp)
                    .then(if (!showText) Modifier.width(38.dp) else Modifier)
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
                            modifier = Modifier.size(if (!showText) 16.dp else 14.dp)
                        )
                    }

                    if (showText) {
                        if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = hideWatchedLabel,
                            fontSize = 12.5.sp,
                            fontWeight = if (hideWatched) FontWeight.Bold else FontWeight.Medium
                        )
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
            Button(
                onClick = { onSelect(format) },
                contentPadding = buttonPadding,
                scale = ButtonDefaults.scale(focusedScale = 1.05f),
                colors = ButtonDefaults.colors(
                    containerColor = if (isSelected) (if (isMonochrome) Color(0x30FFFFFF) else focusColor.copy(alpha = 0.22f)) else (if (isMonochrome) Color(0x10FFFFFF) else focusColor.copy(alpha = 0.08f)),
                    contentColor = if (isSelected) (if (isMonochrome) Color.White else focusColor) else (if (isMonochrome) Color(0xFF94A3B8) else Color(0xFFBAE6FD)),
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                ),
                shape = ButtonDefaults.shape(shape = chipShape),
                border = ButtonDefaults.border(
                    border = Border(
                        border = BorderStroke(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = if (isSelected) (if (isMonochrome) Color(0x40FFFFFF) else focusColor.copy(alpha = 0.45f)) else (if (isMonochrome) Color(0x18FFFFFF) else focusColor.copy(alpha = 0.22f))
                        ),
                        shape = chipShape
                    ),
                    focusedBorder = Border.None
                ),
                modifier = Modifier
                    .handleTouchClick { onSelect(format) }
                    .height(34.dp)
                    .then(if (!showText) Modifier.width(38.dp) else Modifier)
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
                            modifier = Modifier.size(if (!showText) 16.dp else 14.dp)
                        )
                    }

                    if (showText) {
                        if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatLabel,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
