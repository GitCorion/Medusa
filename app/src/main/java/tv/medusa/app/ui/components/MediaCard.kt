package tv.medusa.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.medusa.app.R
import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvRatingBadge
import tv.medusa.app.ui.util.handleTouchClick

/**
 * Tarjeta de póster vertical (2:3) para Android TV.
 *
 * Filosofía Anti-Slop:
 * - Cero cajón gris inferior de plantilla: el póster es 100% arte a sangre con esquinas suaves (12.dp).
 * - Título y metadatos ubicados FUERA del póster, directamente sobre el fondo abisal, con iluminación selectiva.
 * - Escalado y zIndex aplicados en el contenedor raíz exterior antes de cualquier clip para evitar recortes de badges o bordes.
 * - Efecto Spotlight: atenúa las tarjetas inactivas de la fila enfocada (0.65f) para resaltar la tarjeta en foco.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isRowFocused: Boolean = false,
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
    var isLongPressConsumed by remember { mutableStateOf(false) }

    val effectiveLongClick: (() -> Unit)? = onLongClick ?: contextMenuController?.let { controller ->
        {
            isLongPressConsumed = true
            lastLongClickTimestamp = android.os.SystemClock.uptimeMillis()
            view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
            controller.showContextMenu(item)
        }
    }

    val safeOnClick: () -> Unit = {
        val now = android.os.SystemClock.uptimeMillis()
        if (isLongPressConsumed || now - lastLongClickTimestamp < 1200L) {
            isLongPressConsumed = false
        } else {
            onClick()
        }
    }

    // Animaciones de foco y Spotlight
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "mediaCardScale"
    )

    val spotlightAlpha by animateFloatAsState(
        targetValue = if (isRowFocused && !isFocused) 0.65f else 1.0f,
        animationSpec = tween(durationMillis = 220),
        label = "spotlightAlpha"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.40f,
        animationSpec = tween(durationMillis = 200),
        label = "textAlpha"
    )

    // Contenedor Raíz de TV sin clipping: scale y zIndex aplicados ANTES de cualquier clip
    Card(
        onClick = safeOnClick,
        onLongClick = effectiveLongClick,
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
        border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
        shape = CardDefaults.shape(shape = RoundedCornerShape(0.dp)),
        modifier = modifier
            .width(150.dp)
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = spotlightAlpha
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocus()
                }
            }
            .handleTouchClick(onLongClick = effectiveLongClick, onClick = safeOnClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Póster vertical (2:3) 100% arte a sangre con esquinas redondeadas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C1017))
                    .then(
                        if (isFocused) {
                            Modifier.border(2.dp, focusColor, RoundedCornerShape(12.dp))
                        } else Modifier
                    )
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

                // Insignia de Nota superior derecha (con padding holgado para no rozar el borde)
                if (item.rating != null && item.rating > 0f) {
                    TvRatingBadge(
                        rating = item.rating,
                        compact = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .zIndex(2f)
                    )
                }

                val isTvSeries = item.type.equals("Series", ignoreCase = true)
                val hasEpisodeProgress = !item.isPlayed &&
                        isTvSeries &&
                        item.playedEpisodes != null &&
                        item.playedEpisodes > 0
                val hasMovieProgress = !item.isPlayed &&
                        !isTvSeries &&
                        item.playbackPositionTicks > 0

                // Insignias superior izquierda: Favorito, Visto (con padding holgado de 10.dp)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 10.dp, start = 10.dp)
                        .zIndex(2f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasEpisodeProgress) {
                        val progressText = if (item.totalEpisodes != null && item.totalEpisodes > 0) {
                            "${item.playedEpisodes}/${item.totalEpisodes}"
                        } else null

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
            }

            // Metadatos FUERA del póster, sobre el fondo negro abisal, con iluminación en foco
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 2.dp, end = 2.dp)
                    .graphicsLayer { this.alpha = textAlpha }
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
                            color = if (isFocused) focusColor else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
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
