package tv.medusa.app.ui.components

import androidx.compose.ui.graphics.Brush
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.medusa.app.R
import tv.medusa.app.data.local.ACCENT_CYAN
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.theme.TextSecondary
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvRatingBadge
import tv.medusa.app.ui.theme.TextPrimary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CompactFocusedHeader(
    item: MediaItem?,
    onPlayClick: (MediaItem) -> Unit,
    onDetailClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: String = ACCENT_CYAN,
    buttonStyle: String = BUTTON_STYLE_ICONS_ONLY,
    isMonochrome: Boolean = LocalIsMonochromeTheme.current
) {
    if (item == null) return

    val focusColor = if (isMonochrome) TextPrimary else TvAccent.getColor(accentColor)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentColor)
    val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY
    val showText = buttonStyle != BUTTON_STYLE_ICONS_ONLY

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tv.medusa.app.ui.theme.AbyssSurface.copy(alpha = 0.90f))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Title / ClearLogo + Badges
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                var isLogoLoaded by remember(item.id, item.logoUrl) { mutableStateOf(false) }

                if (!item.logoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .heightIn(max = 38.dp)
                            .padding(bottom = 2.dp)
                    ) {
                        AsyncImage(
                            model = item.logoUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier
                                .heightIn(max = 38.dp)
                                .widthIn(max = 260.dp),
                            onSuccess = { isLogoLoaded = true },
                            onError = { isLogoLoaded = false }
                        )

                        if (!isLogoLoaded) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.rating != null && item.rating > 0f) {
                        TvRatingBadge(rating = item.rating, compact = true)
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (item.year != null) {
                        Text(
                            text = "${item.year}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    val isTv = item.type.equals("Series", ignoreCase = true) ||
                            item.type.equals("Episode", ignoreCase = true)
                    Text(
                        text = stringResource(if (isTv) R.string.type_series else R.string.type_movie),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    if (!item.overview.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right Column: Quick Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onPlayClick(item) },
                    contentPadding = tv.medusa.app.ui.theme.TvChipDefaults.ContentPadding,
                    scale = tv.medusa.app.ui.theme.TvChipDefaults.buttonScale(),
                    shape = ButtonDefaults.shape(tv.medusa.app.ui.theme.TvChipDefaults.Shape),
                    border = tv.medusa.app.ui.theme.TvChipDefaults.buttonBorder(),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassSelected else focusColor.copy(alpha = 0.20f),
                        contentColor = if (isMonochrome) TextPrimary else Color(0xFFE0F2FE),
                        focusedContainerColor = focusColor,
                        focusedContentColor = focusContent
                    ),
                    modifier = Modifier.height(tv.medusa.app.ui.theme.TvChipDefaults.Height)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (showIcons) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.common_play),
                                modifier = Modifier
                                    .size(15.dp)
                                    .offset(x = if (!showText) 1.dp else 0.5.dp) // Compensación óptica del centro visual
                            )
                        }
                        if (showText) {
                            if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.common_play),
                                style = tv.medusa.app.ui.theme.TvChipDefaults.textStyle,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = { onDetailClick(item) },
                    contentPadding = tv.medusa.app.ui.theme.TvChipDefaults.ContentPadding,
                    scale = tv.medusa.app.ui.theme.TvChipDefaults.buttonScale(),
                    shape = ButtonDefaults.shape(tv.medusa.app.ui.theme.TvChipDefaults.Shape),
                    border = tv.medusa.app.ui.theme.TvChipDefaults.buttonBorder(),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassSubtle else focusColor.copy(alpha = 0.10f),
                        contentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.TextSoft else Color(0xFFBAE6FD),
                        focusedContainerColor = focusColor,
                        focusedContentColor = focusContent
                    ),
                    modifier = Modifier.height(tv.medusa.app.ui.theme.TvChipDefaults.Height)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (showIcons) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = stringResource(R.string.common_details),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        if (showText) {
                            if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.common_details),
                                style = tv.medusa.app.ui.theme.TvChipDefaults.textStyle,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
