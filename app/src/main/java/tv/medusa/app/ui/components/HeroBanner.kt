package tv.medusa.app.ui.components

import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
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
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.model.SeasonalEvent
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvPill
import tv.medusa.app.ui.theme.TvRatingBadge
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroBanner(
    item: MediaItem?,
    onPlayClick: (MediaItem) -> Unit,
    onDetailClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    featuredItems: List<MediaItem> = emptyList(),
    accentColor: String = ACCENT_CYAN,
    buttonStyle: String = BUTTON_STYLE_ICONS_ONLY,
    seasonalEvent: SeasonalEvent? = null,
    onExploreSeasonal: (() -> Unit)? = null,
    onActiveHeroChanged: ((MediaItem) -> Unit)? = null
) {
    val indicatorTheme = LocalIndicatorTheme.current
    val isMonochrome = LocalIsMonochromeTheme.current ||
            indicatorTheme == INDICATOR_THEME_MONOCHROME

    // 1. Carrusel de destacados con rotación reposada (12s) que se pausa cuando el usuario interactúa
    val cleanFeatured = remember(featuredItems) { featuredItems.take(6) }
    var carouselIndex by remember { mutableIntStateOf(0) }
    var isHeroFocused by remember { mutableStateOf(false) }

    LaunchedEffect(item, cleanFeatured, isHeroFocused) {
        if (item == null && !isHeroFocused && cleanFeatured.size > 1) {
            while (true) {
                delay(12000L)
                carouselIndex = (carouselIndex + 1) % cleanFeatured.size
            }
        }
    }

    val effectiveIndex = if (cleanFeatured.isNotEmpty()) carouselIndex % cleanFeatured.size else 0
    val effectiveItem = item ?: cleanFeatured.getOrNull(effectiveIndex)

    // Notificar al contenedor principal para sincronizar el ambient backlight
    LaunchedEffect(effectiveItem?.id) {
        effectiveItem?.let { onActiveHeroChanged?.invoke(it) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .onFocusChanged { isHeroFocused = it.hasFocus }
    ) {
        // Crossfade cinematográfico de la imagen de fondo con proporción nativa (16:9 sin recortes de cabezas ni bordes)
        Crossfade(
            targetState = effectiveItem,
            animationSpec = tween(durationMillis = 400),
            label = "heroBackdropCrossfade"
        ) { currentItem ->
            Box(modifier = Modifier.fillMaxSize()) {
                val isBackdrop = !currentItem?.backdropUrl.isNullOrBlank()
                val imageModel = currentItem?.backdropUrl ?: currentItem?.posterUrl

                if (imageModel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .then(
                                if (isBackdrop) {
                                    Modifier.aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                                } else {
                                    Modifier.aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
                                }
                            )
                            .align(Alignment.CenterEnd)
                    ) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = currentItem?.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Suave desvanecimiento horizontal a la izquierda para fundirse con la zona de texto
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF07070B),
                                            Color(0xEE07070B),
                                            Color(0x3307070B),
                                            Color.Transparent
                                        ),
                                        startX = 0f,
                                        endX = 200f
                                    )
                                )
                        )
                        // Suave desvanecimiento inferior para integrar con la fila siguiente
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color(0x5507070B),
                                            Color(0xFF07070B)
                                        )
                                    )
                                )
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0C0D14))
                    )
                }
            }
        }

        // Horizontal gradient for dark left text background (solo cubre el lado izquierdo para máxima legibilidad sin tapar la ilustración)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.55f)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF607070B),
                            Color(0xEE07070B),
                            Color(0x6607070B),
                            Color.Transparent
                        )
                    )
                )
        )

        if (effectiveItem != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, end = 24.dp, top = 20.dp, bottom = 14.dp)
                    .fillMaxWidth(0.58f)
            ) {
                // Festive / Seasonal Editorial Badge (Apple TV+ style, no emojis)
                if (seasonalEvent != null) {
                    val eventAccentColor = remember(seasonalEvent.accentHex) {
                        runCatching { Color(android.graphics.Color.parseColor(seasonalEvent.accentHex)) }
                            .getOrDefault(Color(0xFFEA580C))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .background(
                                color = if (isMonochrome) Color(0x22FFFFFF) else eventAccentColor.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isMonochrome) Color(0x33FFFFFF) else eventAccentColor.copy(alpha = 0.60f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isMonochrome) Color.White else eventAccentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = seasonalEvent.badgeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = Color.White
                        )
                    }
                }

                // Title / ClearLogo
                var isLogoLoaded by remember(effectiveItem.id, effectiveItem.logoUrl) { mutableStateOf(false) }

                if (!effectiveItem.logoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .heightIn(max = 64.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        AsyncImage(
                            model = effectiveItem.logoUrl,
                            contentDescription = effectiveItem.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier
                                .heightIn(max = 64.dp)
                                .widthIn(max = 340.dp),
                            onSuccess = { isLogoLoaded = true },
                            onError = { isLogoLoaded = false }
                        )

                        if (!isLogoLoaded) {
                            Text(
                                text = effectiveItem.title,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    shadow = Shadow(
                                        color = Color.Black,
                                        blurRadius = 18f,
                                        offset = Offset(2f, 3f)
                                    )
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        text = effectiveItem.title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            shadow = Shadow(
                                color = Color.Black,
                                blurRadius = 18f,
                                offset = Offset(2f, 3f)
                            )
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Metadata Badges Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (effectiveItem.rating != null && effectiveItem.rating > 0f) {
                        TvRatingBadge(rating = effectiveItem.rating, compact = false)
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (effectiveItem.year != null) {
                        TvPill(
                            text = "${effectiveItem.year}",
                            containerColor = Color(0x18FFFFFF),
                            textColor = Color(0xFFE2E8F0),
                            borderColor = Color(0x22FFFFFF)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    val isTv = effectiveItem.type.equals("Series", ignoreCase = true) ||
                            effectiveItem.type.equals("TvProgram", ignoreCase = true) ||
                            effectiveItem.type.equals("Episode", ignoreCase = true)
                    TvPill(
                        text = stringResource(if (isTv) R.string.type_series else R.string.type_movie),
                        containerColor = if (isMonochrome) Color(0x18FFFFFF) else Color(0x2400A4DC),
                        textColor = if (isMonochrome) Color(0xFFE2E8F0) else Color(0xFF7DD3FC),
                        borderColor = if (isMonochrome) Color(0x22FFFFFF) else Color(0x5500A4DC)
                    )

                    val isTvSeries = effectiveItem.type.equals("Series", ignoreCase = true)
                    val hasEpisodeProgress = !effectiveItem.isPlayed &&
                            isTvSeries &&
                            effectiveItem.playedEpisodes != null &&
                            effectiveItem.playedEpisodes > 0
                    val hasMovieProgress = !effectiveItem.isPlayed &&
                            !isTvSeries &&
                            effectiveItem.playbackPositionTicks > 0

                    if (hasEpisodeProgress) {
                        val progressText = if (effectiveItem.totalEpisodes != null && effectiveItem.totalEpisodes > 0) {
                            "PROGRESO: ${effectiveItem.playedEpisodes}/${effectiveItem.totalEpisodes}"
                        } else {
                            "PROGRESO: ${effectiveItem.playedEpisodes} caps"
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TvPill(
                            text = progressText,
                            containerColor = Color(0x22FFFFFF),
                            textColor = if (isMonochrome) Color.White else Color(0xFF38BDF8),
                            borderColor = if (isMonochrome) Color(0x33FFFFFF) else Color(0x5500A4DC)
                        )
                    }

                    if (hasMovieProgress) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TvPill(
                            text = stringResource(R.string.badge_in_progress),
                            containerColor = Color(0x22FFFFFF),
                            textColor = if (isMonochrome) Color.White else Color(0xFF38BDF8),
                            borderColor = if (isMonochrome) Color(0x33FFFFFF) else Color(0x5500A4DC)
                        )
                    }

                    if (effectiveItem.isFavorite) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TvPill(
                            text = stringResource(R.string.badge_favorite),
                            containerColor = Color(0x22FFFFFF),
                            textColor = if (isMonochrome) Color.White else Color(0xFFEF4444),
                            borderColor = if (isMonochrome) Color(0x33FFFFFF) else Color(0x66EF4444)
                        )
                    }

                    if (effectiveItem.isPlayed && !hasEpisodeProgress && !hasMovieProgress) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TvPill(
                            text = stringResource(R.string.badge_watched),
                            containerColor = Color(0x22FFFFFF),
                            textColor = if (isMonochrome) Color.White else Color(0xFF6EE7B7),
                            borderColor = if (isMonochrome) Color(0x33FFFFFF) else Color(0x3310B981)
                        )
                    }

                    val sourceLabel = when (effectiveItem.source) {
                        MediaSource.TRAKT_RECOMMENDATION -> stringResource(R.string.hero_source_trakt_recommended)
                        MediaSource.TMDB_TRENDING -> if (effectiveItem.rank != null && effectiveItem.rank <= 10) stringResource(R.string.hero_source_top_today, effectiveItem.rank) else stringResource(R.string.hero_source_trending)
                        MediaSource.TMDB_RECOMMENDATION -> stringResource(R.string.hero_source_for_you)
                        MediaSource.TRAKT_WATCHLIST -> stringResource(R.string.hero_source_watchlist)
                        MediaSource.JELLYFIN -> stringResource(R.string.hero_source_collection)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TvPill(
                        text = sourceLabel,
                        containerColor = Color(0x12FFFFFF),
                        textColor = Color(0xFF94A3B8),
                        borderColor = Color(0x18FFFFFF)
                    )
                }

                // Synopsis
                if (!effectiveItem.overview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = effectiveItem.overview,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp
                        ),
                        color = Color(0xFFCBD5E1),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                val focusColor = TvAccent.getColor(accentColor)
                val focusContent = TvAccent.getFocusedContentColor(accentColor)
                val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY
                val showText = buttonStyle != BUTTON_STYLE_ICONS_ONLY

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onExploreSeasonal != null && seasonalEvent != null) {
                        val eventAccent = remember(seasonalEvent.accentHex) {
                            runCatching { Color(android.graphics.Color.parseColor(seasonalEvent.accentHex)) }
                                .getOrDefault(Color(0xFFEA580C))
                        }
                        Button(
                            onClick = onExploreSeasonal,
                            colors = ButtonDefaults.colors(
                                containerColor = if (isMonochrome) Color(0x28FFFFFF) else eventAccent.copy(alpha = 0.28f),
                                contentColor = Color.White,
                                focusedContainerColor = if (isMonochrome) Color.White else eventAccent,
                                focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else Color.White
                            ),
                            shape = ButtonDefaults.shape(shape = CircleShape),
                            border = ButtonDefaults.border(
                                border = Border(
                                    border = BorderStroke(1.dp, if (isMonochrome) Color(0x33FFFFFF) else eventAccent.copy(alpha = 0.70f)),
                                    shape = CircleShape
                                ),
                                focusedBorder = Border.None
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "VER SELECCIÓN",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Button(
                        onClick = { onDetailClick(effectiveItem) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isMonochrome) Color(0x28FFFFFF) else focusColor.copy(alpha = 0.22f),
                            contentColor = if (isMonochrome) Color.White else focusColor,
                            focusedContainerColor = focusColor,
                            focusedContentColor = focusContent
                        ),
                        shape = ButtonDefaults.shape(shape = CircleShape),
                        border = ButtonDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, if (isMonochrome) Color(0x33FFFFFF) else focusColor.copy(alpha = 0.60f)),
                                shape = CircleShape
                            ),
                            focusedBorder = Border.None
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (showIcons) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = stringResource(R.string.common_details),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (showText) {
                                if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.common_details),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Carousel Indicators (Bottom Right)
        if (item == null && cleanFeatured.size > 1) {
            val focusColor = TvAccent.getColor(accentColor)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cleanFeatured.indices.forEach { idx ->
                    val isActive = idx == effectiveIndex
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (isActive) 24.dp else 6.dp,
                        animationSpec = tween(durationMillis = 350),
                        label = "carouselIndicatorWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(indicatorWidth)
                            .clip(CircleShape)
                            .background(if (isActive) (if (isMonochrome) Color.White else focusColor) else Color(0x44FFFFFF))
                    )
                }
            }
        }
    }
}
