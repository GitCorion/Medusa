package tv.medusa.app.ui.components

import tv.medusa.app.data.local.ACCENT_CYAN
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.medusa.app.ui.util.handleTouchClick
import coil.compose.AsyncImage
import tv.medusa.app.R
import tv.medusa.app.domain.model.EpisodeItem
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvCornerRadius
import tv.medusa.app.ui.theme.TvPill

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeCard(
    episode: EpisodeItem,
    onClick: () -> Unit,
    accentColor: String = ACCENT_CYAN,
    modifier: Modifier = Modifier
) {
    val isMonochrome = LocalIsMonochromeTheme.current
    val effectiveAccent = if (accentColor.isNotBlank()) accentColor else LocalAccentColor.current
    val focusColor = if (isMonochrome) TextPrimary else TvAccent.getColor(effectiveAccent)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(effectiveAccent)
    var isFocused by remember { mutableStateOf(false) }

    val cardShape = remember { RoundedCornerShape(10.dp) }

    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = Color(0xFF0C1017),
            focusedContainerColor = Color(0xFF141B26)
        ),
        scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
        shape = CardDefaults.shape(shape = cardShape),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, focusColor),
                shape = cardShape
            )
        ),
        modifier = modifier
            .handleTouchClick { onClick() }
            .width(230.dp)
            .zIndex(if (isFocused) 10f else 0f)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = TvCornerRadius.Card, topEnd = TvCornerRadius.Card))
                    .background(Color(0xFF1A1C24))
            ) {
                var isImageError by remember(episode.id, episode.imageUrl) { mutableStateOf(false) }

                if (!isImageError && !episode.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = episode.imageUrl,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { isImageError = true }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF222838), Color(0xFF10131B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Vignette gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x88000000)
                                )
                            )
                        )
                )

                // Watched badge
                if (episode.isPlayed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 8.dp, start = 8.dp)
                            .zIndex(2f)
                            .size(20.dp)
                            .background(
                                color = tv.medusa.app.ui.theme.TvPalette.BadgeBackdrop,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.common_watched),
                            tint = if (isMonochrome) TextPrimary.copy(alpha = 0.90f) else tv.medusa.app.ui.theme.TvPalette.SemanticWatched,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Progress bar
                if (!episode.isPlayed && episode.playbackPositionTicks > 0) {
                    val durationTicks = episode.durationMinutes?.let { it.toLong() * 600_000_000L }
                    val progressFraction = if (durationTicks != null && durationTicks > 0L) {
                        (episode.playbackPositionTicks.toFloat() / durationTicks.toFloat()).coerceIn(0.05f, 0.98f)
                    } else 0.40f

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0x66000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(3.dp)
                                .background(if (isMonochrome) TextPrimary else focusColor)
                        )
                    }
                }

                // Duration badge
                if (episode.formattedDuration != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        TvPill(
                            text = episode.formattedDuration!!,
                            containerColor = Color(0xCC000000),
                            textColor = TextPrimary.copy(alpha = 0.90f),
                            borderColor = Color(0x24FFFFFF)
                        )
                    }
                }

                // Play icon overlay when focused
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .background(TextPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.common_play),
                            tint = tv.medusa.app.ui.theme.TvPalette.FocusInk,
                            modifier = Modifier
                                .size(22.dp)
                                .padding(start = 2.dp) // Optical center compensation for triangle
                        )
                    }
                }
            }

            // Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.name}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = tv.medusa.app.ui.theme.DmSansFamily,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocused) TextPrimary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!episode.overview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = episode.overview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = tv.medusa.app.ui.theme.DmSansFamily,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        ),
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

