package tv.medusa.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.components.cinema.CinemaFormatBadge
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvCornerRadius
import tv.medusa.app.ui.theme.TvMotion
import tv.medusa.app.ui.theme.TvRatingBadge
import tv.medusa.app.ui.util.adaptiveMarquee
import tv.medusa.app.ui.util.handleTouchClick

/**
 * Tarjeta panorámica 16:9 (280.dp) específica para la fila 'Continuar Viendo'.
 *
 * Características:
 * - Backdrop cinemático de alta resolución (16:9).
 * - Icono de Play circular bioluminiscente en el centro visible únicamente en foco.
 * - Código de episodio destacado (ej. T2:E4).
 * - Barra de progreso cian flotando con padding interior (horizontal 12.dp, bottom 10.dp) sobre gradiente oscuro.
 * - Sin recorte en zoom gracias a zIndex y graphicsLayer en el contenedor raíz.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingCard(
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
            controller.showContextMenu(item, isContinueWatching = true)
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

    val playAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = TvMotion.DurationFadeMs),
        label = "continuePlayAlpha"
    )

    val isEpisode = item.type.equals("Episode", ignoreCase = true) || !item.seriesId.isNullOrEmpty()

    val preferredImage = item.backdropUrl ?: item.posterUrl

    var currentImageModel by remember(item.id, preferredImage) { mutableStateOf(preferredImage) }
    var isFallbackAttempted by remember(item.id) { mutableStateOf(false) }
    var isImageError by remember(item.id) { mutableStateOf(false) }

    val cardShape = remember { RoundedCornerShape(10.dp) }

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
            .width(280.dp)
            .zIndex(if (isFocused) 10f else 0f)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocus()
                }
            }
            .handleTouchClick(onLongClick = effectiveLongClick, onClick = safeOnClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(cardShape)
                .background(tv.medusa.app.ui.theme.TvPalette.SurfaceCard)
                .then(if (isFocused) Modifier.border(1.5.dp, focusColor, cardShape) else Modifier)
        ) {
            if (!isImageError && !currentImageModel.isNullOrBlank()) {
                AsyncImage(
                    model = currentImageModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        val alternateImage = if (currentImageModel == item.backdropUrl) item.posterUrl else item.backdropUrl
                        if (!isFallbackAttempted && !alternateImage.isNullOrBlank() && alternateImage != currentImageModel) {
                            isFallbackAttempted = true
                            currentImageModel = alternateImage
                        } else {
                            isImageError = true
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    tv.medusa.app.ui.theme.TvPalette.FallbackGradientTop,
                                    tv.medusa.app.ui.theme.TvPalette.FallbackGradientBottom
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEpisode) Icons.Rounded.Tv else Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = Color(0x66FFFFFF),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Gradiente oscuro inferior para asegurar legibilidad cinematográfica
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x20000000),
                                Color(0x9506080D),
                                Color(0xF206080D)
                            ),
                            startY = 40f
                        )
                    )
            )

            // Play Icon central con animación de entrada fluida al hacer foco
            if (playAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(46.dp)
                        .graphicsLayer {
                            alpha = playAlpha
                            scaleX = 0.82f + (playAlpha * 0.18f)
                            scaleY = 0.82f + (playAlpha * 0.18f)
                        }
                        .background(Color(0x9906080D), CircleShape)
                        .border(1.5.dp, focusColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .offset(x = 1.5.dp) // Compensación óptica: el centro visual del triángulo está desplazado a la derecha
                    )
                }
            }

            // Badge superior derecha de nota si existe (holgura de 10.dp para no rozar borde)
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

            // Extraer código de episodio (ej: T2:E4) del título o serie
            val episodeCode = remember(item.title) {
                val match = Regex("""(?:T|S)(\d+)\s*:\s*(?:E|Cap\.?)\s*(\d+)""", RegexOption.IGNORE_CASE).find(item.title)
                    ?: Regex("""\((?:T|S)(\d+):(?:E)(\d+)\)""", RegexOption.IGNORE_CASE).find(item.title)
                match?.let { "T${it.groupValues[1]}:E${it.groupValues[2]}" }
            }

            val displayTitle = remember(item.title, item.seriesName) {
                val base = item.seriesName ?: item.title
                base.replace(Regex("""\s*\([TE]\d+:[TE]\d+\)"""), "").trim()
            }

            // Información inferior: Código de Episodio + Título
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (episodeCode != null) {
                        CinemaFormatBadge(
                            text = episodeCode,
                            isHighlighted = isFocused,
                            highlightColor = focusColor
                        )
                    } else {
                        val isTv = item.type.equals("Series", ignoreCase = true) || item.type.equals("Episode", ignoreCase = true)
                        CinemaFormatBadge(
                            text = if (isTv) "SERIE" else "PELÍCULA"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = displayTitle,
                    color = if (isFocused) Color.White else TextPrimary,
                    fontFamily = tv.medusa.app.ui.theme.DmSansFamily,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.adaptiveMarquee(enabled = isFocused)
                )
            }

            // Barra de progreso flotando con padding(horizontal = 12.dp, bottom = 10.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x80000000))
            ) {
                val progressFraction = remember(item) {
                    when {
                        item.totalEpisodes != null && (item.totalEpisodes ?: 0) > 0 && item.playedEpisodes != null -> {
                            (item.playedEpisodes!!.toFloat() / item.totalEpisodes!!.toFloat()).coerceIn(0.08f, 0.95f)
                        }
                        item.playbackPositionTicks > 0L -> 0.45f
                        else -> 0.35f
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isFocused) Color.White else focusColor)
                )
            }
        }
    }
}
