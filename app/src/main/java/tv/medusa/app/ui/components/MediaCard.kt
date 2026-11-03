package tv.medusa.app.ui.components

import androidx.compose.ui.text.font.FontStyle
import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import tv.medusa.app.ui.util.handleTouchClick
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.medusa.app.R
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvRatingBadge

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val accentKey = LocalAccentColor.current
    val accentColor = TvAccent.getColor(accentKey)
    val indicatorTheme = LocalIndicatorTheme.current
    val isMonochrome = LocalIsMonochromeTheme.current ||
            indicatorTheme == INDICATOR_THEME_MONOCHROME
    val focusColor = if (isMonochrome) Color.White else accentColor

    val contextMenuController = tv.medusa.app.ui.components.contextmenu.LocalContextMenuController.current
    val view = androidx.compose.ui.platform.LocalView.current
    var lastLongClickTimestamp by remember { mutableStateOf(0L) }

    val effectiveLongClick: (() -> Unit)? = onLongClick ?: contextMenuController?.let { controller ->
        {
            lastLongClickTimestamp = android.os.SystemClock.uptimeMillis()
            view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
            controller.showContextMenu(item)
        }
    }

    val safeOnClick: () -> Unit = {
        // Evita que al soltar una pulsación larga se interprete erróneamente como un clic estándar
        if (android.os.SystemClock.uptimeMillis() - lastLongClickTimestamp > 800L) {
            onClick()
        }
    }

    Card(
        onClick = safeOnClick,
        onLongClick = effectiveLongClick,
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Color(0xFF1E222D) else Color(0xFF111319),
            focusedContainerColor = Color(0xFF1E222D)
        ),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.05f
        ),
        modifier = modifier
            .handleTouchClick(onLongClick = effectiveLongClick, onClick = safeOnClick)
            .width(150.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocus()
                }
            }
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused) 1.5.dp else 0.5.dp,
                color = if (isFocused) focusColor else (if (isMonochrome) Color(0x18FFFFFF) else focusColor.copy(alpha = 0.16f)),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) Color(0xFF1B1F2A) else Color(0xFF0F1117))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            ) {
                var isImageError by remember(item.id, item.posterUrl) { mutableStateOf(false) }

                if (!isImageError && !item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { isImageError = true }
                    )
                } else {
                    val fallbackGradient = remember {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF232A3B), Color(0xFF0F1420))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(fallbackGradient)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = Color(0x66FFFFFF),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Top-Right Rating Badge
                if (item.rating != null && item.rating > 0f) {
                    TvRatingBadge(
                        rating = item.rating,
                        compact = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .zIndex(2f)
                    )
                }

                val indicatorTheme = LocalIndicatorTheme.current
                val isMonochrome = LocalIsMonochromeTheme.current ||
                        indicatorTheme == INDICATOR_THEME_MONOCHROME

                val isTvSeries = item.type.equals("Series", ignoreCase = true)
                val hasEpisodeProgress = !item.isPlayed &&
                        isTvSeries &&
                        item.playedEpisodes != null &&
                        item.playedEpisodes > 0
                val hasMovieProgress = !item.isPlayed &&
                        !isTvSeries &&
                        item.playbackPositionTicks > 0

                // Top-Left Badges: Favorito, Visto
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = 8.dp)
                        .zIndex(2f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    if (hasEpisodeProgress) {
                        val progressText = if (item.totalEpisodes != null && item.totalEpisodes > 0) {
                            "${item.playedEpisodes}/${item.totalEpisodes}"
                        } else {
                            null
                        }

                        if (progressText != null) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xCC000000),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = progressText,
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (item.isFavorite) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    color = Color(0xCC000000),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = stringResource(R.string.common_favorite),
                                tint = if (isMonochrome) Color.White else Color(0xFFF43F5E),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    if (item.isPlayed && !hasEpisodeProgress && !hasMovieProgress) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    color = Color(0xCC000000),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.common_watched),
                                tint = if (isMonochrome) Color.White.copy(alpha = 0.90f) else Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                val progressFraction = when {
                    hasEpisodeProgress && (item.totalEpisodes ?: 0) > 0 ->
                        (item.playedEpisodes!!.toFloat() / item.totalEpisodes!!.toFloat()).coerceIn(0.05f, 0.98f)
                    hasMovieProgress -> 0.40f
                    else -> null
                }

                if (progressFraction != null) {
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
                                .background(if (isMonochrome) Color.White else focusColor)
                        )
                    }
                }

                if (item.rank != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xD9000000)),
                                    startY = 90f
                                )
                            )
                    )

                    Text(
                        text = "${item.rank}",
                        color = Color.White.copy(alpha = 0.95f),
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        fontSize = 42.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp, bottom = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isFocused) Color.White else Color(0xFFE2E8F0),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.year != null) {
                        Text(
                            text = item.year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    if (item.year != null) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }
                    val isTv = item.type.equals("Series", ignoreCase = true) ||
                            item.type.equals("TvProgram", ignoreCase = true) ||
                            item.type.equals("Episode", ignoreCase = true)
                    Text(
                        text = stringResource(if (isTv) R.string.type_series else R.string.type_movie),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}



