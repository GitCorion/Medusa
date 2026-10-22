package tv.medusa.app.ui.components

import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import tv.medusa.app.util.SeasonalAudioManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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

private sealed interface HeroSlideState {
    data class Seasonal(val event: SeasonalEvent) : HeroSlideState
    data class Movie(val item: MediaItem) : HeroSlideState
    data object Empty : HeroSlideState
}

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
    onActiveHeroChanged: ((MediaItem) -> Unit)? = null,
    seasonalAudioEnabled: Boolean = true
) {
    val indicatorTheme = LocalIndicatorTheme.current
    val isMonochrome = LocalIsMonochromeTheme.current ||
            indicatorTheme == INDICATOR_THEME_MONOCHROME

    // 1. Carrusel de destacados con rotación reposada (12s) que se pausa cuando el usuario interactúa
    val cleanFeatured = remember(featuredItems) { featuredItems.take(6) }
    var carouselIndex by remember { mutableIntStateOf(0) }
    var isHeroFocused by remember { mutableStateOf(false) }
    val actionButtonFocusRequester = remember { FocusRequester() }

    val hasSeasonalSlide = seasonalEvent != null
    val totalSlides = cleanFeatured.size + (if (hasSeasonalSlide) 1 else 0)

    LaunchedEffect(item, totalSlides, isHeroFocused, carouselIndex) {
        if (item == null && !isHeroFocused && totalSlides > 1) {
            delay(12000L)
            carouselIndex = (carouselIndex + 1) % totalSlides
        }
    }

    val currentSlideIndex = if (totalSlides > 0) carouselIndex % totalSlides else 0
    val isSeasonalSlide = item == null && hasSeasonalSlide && currentSlideIndex == 0
    val movieSlideIndex = if (hasSeasonalSlide) currentSlideIndex - 1 else currentSlideIndex
    val effectiveItem = item ?: if (!isSeasonalSlide && cleanFeatured.isNotEmpty() && movieSlideIndex in cleanFeatured.indices) cleanFeatured[movieSlideIndex] else null

    // Notificar al contenedor principal para sincronizar el ambient backlight
    LaunchedEffect(effectiveItem?.id) {
        effectiveItem?.let { onActiveHeroChanged?.invoke(it) }
    }

    val currentSlideState: HeroSlideState = when {
        isSeasonalSlide -> HeroSlideState.Seasonal(seasonalEvent!!)
        effectiveItem != null -> HeroSlideState.Movie(effectiveItem)
        else -> HeroSlideState.Empty
    }



    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .onFocusChanged { isHeroFocused = it.hasFocus }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && totalSlides > 1) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            carouselIndex = if (carouselIndex > 0) carouselIndex - 1 else totalSlides - 1
                            actionButtonFocusRequester.requestFocus()
                            true
                        }
                        Key.DirectionRight -> {
                            carouselIndex = (carouselIndex + 1) % totalSlides
                            actionButtonFocusRequester.requestFocus()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(totalSlides) {
                if (totalSlides > 1) {
                    var dragAccumulator = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragAccumulator += dragAmount },
                        onDragEnd = {
                            if (dragAccumulator > 50f) {
                                carouselIndex = if (carouselIndex > 0) carouselIndex - 1 else totalSlides - 1
                            } else if (dragAccumulator < -50f) {
                                carouselIndex = (carouselIndex + 1) % totalSlides
                            }
                        }
                    )
                }
            }
    ) {
        // Crossfade cinematográfico de la imagen de fondo con proporción nativa (16:9 sin recortes de cabezas ni bordes)
        Crossfade(
            targetState = currentSlideState,
            animationSpec = tween(durationMillis = 400),
            label = "heroBackdropCrossfade"
        ) { slideState ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (slideState) {
                    is HeroSlideState.Seasonal -> {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                                .align(Alignment.CenterEnd)
                        ) {
                            Image(
                                painter = painterResource(slideState.event.backdropResId),
                                contentDescription = slideState.event.title,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Suave desvanecimiento horizontal a la izquierda
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
                                            endX = 260f
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
                    }
                    is HeroSlideState.Movie -> {
                        val currentItem = slideState.item
                        val isBackdrop = !currentItem.backdropUrl.isNullOrBlank()
                        val imageModel = currentItem.backdropUrl ?: currentItem.posterUrl

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
                                    contentDescription = currentItem.title,
                                    contentScale = ContentScale.Crop,
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
                                                endX = 260f
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
                    HeroSlideState.Empty -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0C0D14))
                        )
                    }
                }
            }
        }

        // Horizontal gradient for dark left text background
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


        if (currentSlideState !is HeroSlideState.Empty) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, end = 24.dp, top = 24.dp, bottom = 14.dp)
                    .fillMaxWidth(0.55f)
            ) {

                // Title / ClearLogo
                when (currentSlideState) {
                    is HeroSlideState.Seasonal -> {
                        val event = currentSlideState.event
                        val cinematicFont = remember {
                            FontFamily(Font(R.font.cinzel_decorative_bold, FontWeight.Bold))
                        }
                        val titleGradient = remember(event.id) {
                            when (event.id) {
                                "halloween" -> Brush.verticalGradient(
                                    listOf(Color(0xFFFFFFFF), Color(0xFFFFEBD9), Color(0xFFFDBA74))
                                )
                                "christmas" -> Brush.verticalGradient(
                                    listOf(Color(0xFFFFFFFF), Color(0xFFFEF3C7), Color(0xFFFDE68A))
                                )
                                "valentines" -> Brush.verticalGradient(
                                    listOf(Color(0xFFFFFFFF), Color(0xFFFCE7F3), Color(0xFFFBCFE8))
                                )
                                else -> Brush.verticalGradient(
                                    listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0))
                                )
                            }
                        }

                        Text(
                            text = event.title.uppercase(),
                            fontFamily = cinematicFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            letterSpacing = 1.8.sp,
                            lineHeight = 36.sp,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                brush = titleGradient,
                                shadow = Shadow(
                                    color = Color(0xEE000000),
                                    blurRadius = 18f,
                                    offset = Offset(2f, 3f)
                                )
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is HeroSlideState.Movie -> {
                        val effectiveItem = currentSlideState.item
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
                    }
                    HeroSlideState.Empty -> {}
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Metadata or Subtitle
                when (currentSlideState) {
                    is HeroSlideState.Seasonal -> {
                        val event = currentSlideState.event
                        Text(
                            text = event.subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 20.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = Color(0xFFCBD5E1),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is HeroSlideState.Movie -> {
                        val effectiveItem = currentSlideState.item
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
                    }
                    HeroSlideState.Empty -> {}
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action button: single stable instance across ALL slides
                val focusColor = TvAccent.getColor(accentColor)
                val focusContent = TvAccent.getFocusedContentColor(accentColor)
                val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY
                val showText = buttonStyle != BUTTON_STYLE_ICONS_ONLY

                val isSeasonalActive = currentSlideState is HeroSlideState.Seasonal
                val activeEventAccent = if (isSeasonalActive) {
                    val event = (currentSlideState as HeroSlideState.Seasonal).event
                    runCatching { Color(android.graphics.Color.parseColor(event.accentHex)) }
                        .getOrDefault(Color(0xFFEA580C))
                } else {
                    focusColor
                }

                val buttonContainerColor = if (isMonochrome) {
                    if (isSeasonalActive) Color(0x33FFFFFF) else Color(0x28FFFFFF)
                } else {
                    if (isSeasonalActive) activeEventAccent.copy(alpha = 0.35f) else focusColor.copy(alpha = 0.22f)
                }

                val buttonFocusedContainerColor = if (isMonochrome) {
                    Color.White
                } else {
                    if (isSeasonalActive) activeEventAccent else focusColor
                }

                val buttonContentColor = if (isMonochrome) Color.White else if (isSeasonalActive) Color.White else focusColor
                val buttonFocusedContentColor = if (isMonochrome) Color(0xFF0F172A) else if (isSeasonalActive) Color.White else focusContent
                val buttonBorderColor = Color(0x28FFFFFF)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            when (currentSlideState) {
                                is HeroSlideState.Seasonal -> onExploreSeasonal?.invoke()
                                is HeroSlideState.Movie -> onDetailClick(currentSlideState.item)
                                HeroSlideState.Empty -> {}
                            }
                        },
                        modifier = Modifier
                            .focusRequester(actionButtonFocusRequester)
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && totalSlides > 1) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            carouselIndex = if (carouselIndex > 0) carouselIndex - 1 else totalSlides - 1
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            carouselIndex = (carouselIndex + 1) % totalSlides
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            },
                        colors = ButtonDefaults.colors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor,
                            focusedContainerColor = buttonFocusedContainerColor,
                            focusedContentColor = buttonFocusedContentColor
                        ),
                        shape = ButtonDefaults.shape(shape = CircleShape),
                        border = ButtonDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, buttonBorderColor),
                                shape = CircleShape
                            ),
                            focusedBorder = Border.None
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val actionIcon = if (isSeasonalActive) Icons.Rounded.Collections else Icons.Rounded.Info
                            val actionLabel = if (isSeasonalActive) "Ver colección" else stringResource(R.string.common_details)

                            if (showIcons) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = actionLabel,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (showText) {
                                if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = actionLabel,
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

        // Carousel Indicators & Subtle Navigation Chevrons (Bottom Right)
        if (item == null && totalSlides > 1) {
            val focusColor = TvAccent.getColor(accentColor)
            val chevronAlpha by animateFloatAsState(
                targetValue = if (isHeroFocused) 0.85f else 0.40f,
                animationSpec = tween(250),
                label = "chevronAlpha"
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subtle previous arrow
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = "Anterior",
                    tint = Color.White.copy(alpha = chevronAlpha),
                    modifier = Modifier
                        .size(11.dp)
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            carouselIndex = if (carouselIndex > 0) carouselIndex - 1 else totalSlides - 1
                            actionButtonFocusRequester.requestFocus()
                        }
                )

                val currentActiveIdx = if (totalSlides > 0) carouselIndex % totalSlides else 0
                repeat(totalSlides) { idx ->
                    val isActive = idx == currentActiveIdx
                    val isEventSlide = hasSeasonalSlide && idx == 0
                    val eventAccent = if (isEventSlide) {
                        runCatching { Color(android.graphics.Color.parseColor(seasonalEvent!!.accentHex)) }
                            .getOrDefault(Color(0xFFEA580C))
                    } else {
                        focusColor
                    }
                    val activeColor = if (isMonochrome) Color.White else eventAccent
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
                            .background(if (isActive) activeColor else Color(0x44FFFFFF))
                            .focusProperties { canFocus = false }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                carouselIndex = idx
                                actionButtonFocusRequester.requestFocus()
                            }
                    )
                }

                // Subtle next arrow
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = "Siguiente",
                    tint = Color.White.copy(alpha = chevronAlpha),
                    modifier = Modifier
                        .size(11.dp)
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            carouselIndex = (carouselIndex + 1) % totalSlides
                            actionButtonFocusRequester.requestFocus()
                        }
                )
            }
        }
    }
}
