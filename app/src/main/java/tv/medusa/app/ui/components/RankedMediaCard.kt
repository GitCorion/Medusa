package tv.medusa.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvRatingBadge
import tv.medusa.app.ui.util.adaptiveMarquee
import tv.medusa.app.ui.util.handleTouchClick

/**
 * Tarjeta estilizada con número gigante para la fila 'Top 10 Today'.
 *
 * Características:
 * - Número de ranking (1-10) en tamaño monumental (110.sp, Black/Bold, Italic).
 * - Gradiente vertical de CyanBioluminescent a transparente.
 * - El póster vertical (145.dp de ancho, 2:3) se solapa ligeramente por delante del número con un offset.
 * - Metadatos fuera del póster sobre fondo negro abisal.
 * - Zoom y zIndex aplicados en la raíz sin recortes.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RankedMediaCard(
    item: MediaItem,
    rank: Int,
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

    val textAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.75f,
        animationSpec = tween(durationMillis = 200),
        label = "rankedTextAlpha"
    )

    val numberBrush = remember(focusColor, isFocused) {
        Brush.verticalGradient(
            colors = listOf(
                if (isFocused) Color.White else focusColor,
                focusColor.copy(alpha = 0.50f),
                Color.Transparent
            )
        )
    }

    val posterShape = remember { RoundedCornerShape(10.dp) }

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
            .width(215.dp)
            .zIndex(if (isFocused) 10f else 0f)
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
            // Contenedor del Número Gigante + Póster solapado
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(218.dp)
            ) {
                // 1. Número Gigante estilizado a la izquierda
                Text(
                    text = "$rank",
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        brush = numberBrush,
                        fontSize = 112.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-4).sp
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-4).dp, y = 8.dp)
                        .zIndex(1f)
                )

                // 2. Póster vertical a la derecha solapándose por encima del número con bisel
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(145.dp)
                        .aspectRatio(2f / 3f)
                        .zIndex(2f)
                        .clip(posterShape)
                        .background(Color(0xFF0C1017))
                        .then(
                            if (isFocused) {
                                Modifier.border(1.5.dp, focusColor, posterShape)
                            } else {
                                Modifier.border(0.75.dp, Color(0x20FFFFFF), posterShape)
                            }
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
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Nota TMDB superior derecha con holgura de 10.dp
                    if (item.rating != null && item.rating > 0f) {
                        TvRatingBadge(
                            rating = item.rating,
                            compact = true,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp)
                                .zIndex(3f)
                        )
                    }

                    // Insignias superior izquierda con holgura de 10.dp
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 10.dp, start = 10.dp)
                            .zIndex(3f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.isFavorite) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(0xCC000000), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = stringResource(R.string.common_favorite),
                                    tint = if (isMonochrome) Color.White else Color(0xFFF43F5E),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        if (item.isPlayed) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(0xCC000000), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.common_watched),
                                    tint = if (isMonochrome) Color.White.copy(alpha = 0.90f) else Color(0xFF10B981),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Metadatos fuera del póster alineados a la derecha del contenedor (anchura 145.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .graphicsLayer { this.alpha = textAlpha },
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = Modifier.width(145.dp)
                ) {
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isFocused) Color.White else TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.adaptiveMarquee(enabled = isFocused)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.year != null) {
                                Text(
                                    text = item.year.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isFocused) focusColor else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                            if (item.year != null) {
                                Text(
                                    text = " • ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tv.medusa.app.ui.theme.TextTertiary,
                                    fontSize = 10.sp
                                )
                            }
                            val isTv = item.type.equals("Series", ignoreCase = true) ||
                                    item.type.equals("TvProgram", ignoreCase = true) ||
                                    item.type.equals("Episode", ignoreCase = true)
                            Text(
                                text = stringResource(if (isTv) R.string.type_series else R.string.type_movie),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

