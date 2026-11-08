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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
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
import tv.medusa.app.ui.util.handleTouchClick
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
    seasonalAudioEnabled: Boolean = true,
    actionFocusRequester: FocusRequester? = null,
    onUpFromHero: (() -> Unit)? = null
) {
    val indicatorTheme = LocalIndicatorTheme.current
    val isMonochrome = LocalIsMonochromeTheme.current ||
            indicatorTheme == INDICATOR_THEME_MONOCHROME

    // 1. Carrusel de destacados con rotación reposada (12s) que se pausa cuando el usuario interactúa
    val cleanFeatured = remember(featuredItems) { featuredItems.take(6) }
    var carouselIndex by remember { mutableIntStateOf(0) }
    var isHeroFocused by remember { mutableStateOf(false) }
    val internalActionButtonFocusRequester = remember { FocusRequester() }
    val effectiveActionFocusRequester = actionFocusRequester ?: internalActionButtonFocusRequester

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
            .height(390.dp)
            .onFocusChanged { isHeroFocused = it.hasFocus }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && totalSlides > 1) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            carouselIndex = if (carouselIndex > 0) carouselIndex - 1 else totalSlides - 1
                            effectiveActionFocusRequester.requestFocus()
                            true
                        }
                        Key.DirectionRight -> {
                            carouselIndex = (carouselIndex + 1) % totalSlides
                            effectiveActionFocusRequester.requestFocus()
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
                .fillMaxWidth(0.60f)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF607070B),
                            Color(0xEE07070B),
                            Color(0x9907070B),
                            Color(0x3307070B),
                            Color.Transparent
                        )
                    )
                )
        )


        if (currentSlideState !is HeroSlideState.Empty) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, end = 24.dp, top = 68.dp, bottom = 14.dp)
                    .fillMaxWidth(0.55f)
            ) {

                // Title / ClearLogo
                when (currentSlideState) {
                    is HeroSlideState.Seasonal -> {
                        val event = currentSlideState.event
                        Column {
                            Text(
                                text = stringResource(R.string.seasonal_thematic_collection),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isMonochrome) Color(0xFF94A3B8) else Color(0xFFFDBA74),
                                letterSpacing = 1.4.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                    lineHeight = 38.sp,
                                    letterSpacing = (-0.5).sp,
                                    shadow = Shadow(
                                        color = Color(0xCC000000),
                                        blurRadius = 12f,
                                        offset = Offset(0f, 2f)
                                    )
                                ),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                                            fontSize = 32.sp,
                                            lineHeight = 38.sp,
                                            shadow = Shadow(
                                                color = Color.Black,
                                                blurRadius = 18f,
                                                offset = Offset(2f, 3f)
                                            )
                                        ),
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = effectiveItem.title,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 32.sp,
                                    lineHeight = 38.sp,
                                    shadow = Shadow(
                                        color = Color.Black,
                                        blurRadius = 18f,
                                        offset = Offset(2f, 3f)
                                    )
                                ),
                                color = Color.White,
                                maxLines = 2,
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
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = Color(0xFFCBD5E1),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is HeroSlideState.Movie -> {
                        val effectiveItem = currentSlideState.item
                        // Metadata Row (Typographic hierarchy with clean delimiters)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (effectiveItem.rating != null && effectiveItem.rating > 0f) {
                                TvRatingBadge(rating = effectiveItem.rating, compact = false)
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            val isTv = effectiveItem.type.equals("Series", ignoreCase = true) ||
                                    effectiveItem.type.equals("TvProgram", ignoreCase = true) ||
                                    effectiveItem.type.equals("Episode", ignoreCase = true)
                            val typeText = stringResource(if (isTv) R.string.type_series else R.string.type_movie)
                            val metaParts = listOfNotNull(
                                effectiveItem.year?.toString(),
                                typeText
                            ).joinToString("  •  ")

                            if (metaParts.isNotBlank()) {
                                Text(
                                    text = metaParts,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFCBD5E1),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            val hasEpisodeProgress = !effectiveItem.isPlayed &&
                                    isTv &&
                                    effectiveItem.playedEpisodes != null &&
                                    effectiveItem.playedEpisodes > 0
                            val hasMovieProgress = !effectiveItem.isPlayed &&
                                    !isTv &&
                                    effectiveItem.playbackPositionTicks > 0

                            if (hasEpisodeProgress) {
                                val progressText = if (effectiveItem.totalEpisodes != null && effectiveItem.totalEpisodes > 0) {
                                    "${effectiveItem.playedEpisodes}/${effectiveItem.totalEpisodes} caps"
                                } else {
                                    "${effectiveItem.playedEpisodes} caps"
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                TvPill(
                                    text = progressText,
                                    containerColor = Color(0x2238BDF8),
                                    textColor = if (isMonochrome) Color.White else Color(0xFF38BDF8)
                                )
                            } else if (hasMovieProgress) {
                                Spacer(modifier = Modifier.width(10.dp))
                                TvPill(
                                    text = stringResource(R.string.badge_in_progress),
                                    containerColor = Color(0x2238BDF8),
                                    textColor = if (isMonochrome) Color.White else Color(0xFF38BDF8)
                                )
                            } else if (effectiveItem.isPlayed) {
                                Spacer(modifier = Modifier.width(10.dp))
                                TvPill(
                                    text = stringResource(R.string.badge_watched),
                                    containerColor = Color(0x2210B981),
                                    textColor = if (isMonochrome) Color.White else Color(0xFF6EE7B7)
                                )
                            } else if (effectiveItem.isFavorite) {
                                Spacer(modifier = Modifier.width(10.dp))
                                TvPill(
                                    text = stringResource(R.string.badge_favorite),
                                    containerColor = Color(0x22EF4444),
                                    textColor = if (isMonochrome) Color.White else Color(0xFFF87171)
                                )
                            }
                        }

                        // Synopsis
                        if (!effectiveItem.overview.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = effectiveItem.overview,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp
                                ),
                                color = Color(0xFFCBD5E1),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    HeroSlideState.Empty -> {}
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action button: single stable instance across ALL slides
                val isMonochrome = LocalIsMonochromeTheme.current
                val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColor)
                val focusContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentColor)
                val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY
                val showText = buttonStyle != BUTTON_STYLE_ICONS_ONLY
                val isSeasonalActive = currentSlideState is HeroSlideState.Seasonal

                val buttonContainerColor = if (isMonochrome) Color(0x28FFFFFF) else focusColor.copy(alpha = 0.20f)
                val buttonFocusedContainerColor = if (isMonochrome) Color.White else focusColor
                val buttonContentColor = if (isMonochrome) Color.White else Color(0xFFE0F2FE)
                val buttonFocusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent

                val buttonPadding = if (!showText) {
                    PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                } else {
                    // Balance óptico de icono + texto según better-ui
                    if (showIcons) PaddingValues(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)
                    else PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            when (currentSlideState) {
                                is HeroSlideState.Seasonal -> onExploreSeasonal?.invoke()
                                is HeroSlideState.Movie -> onDetailClick(currentSlideState.item)
                                HeroSlideState.Empty -> {}
                            }
                        },
                        contentPadding = buttonPadding,
                        scale = ButtonDefaults.scale(focusedScale = 1.08f),
                        modifier = Modifier
                            .handleTouchClick {
                                when (currentSlideState) {
                                    is HeroSlideState.Seasonal -> onExploreSeasonal?.invoke()
                                    is HeroSlideState.Movie -> onDetailClick(currentSlideState.item)
                                    HeroSlideState.Empty -> {}
                                }
                            }
                            .focusRequester(effectiveActionFocusRequester)
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    if (keyEvent.key == Key.DirectionUp && onUpFromHero != null) {
                                        onUpFromHero()
                                        true
                                    } else if (totalSlides > 1) {
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
                                    } else false
                                } else false
                            },
                        colors = ButtonDefaults.colors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor,
                            focusedContainerColor = buttonFocusedContainerColor,
                            focusedContentColor = buttonFocusedContentColor
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
                        border = ButtonDefaults.border(
                            border = Border.None,
                            focusedBorder = Border.None
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val actionIcon = if (isSeasonalActive) Icons.AutoMirrored.Rounded.PlaylistPlay else Icons.Rounded.Info
                            val actionLabel = if (isSeasonalActive) stringResource(R.string.seasonal_view_collection) else stringResource(R.string.common_details)

                            if (showIcons) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = actionLabel,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (showText) {
                                if (showIcons) Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = actionLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
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
                            effectiveActionFocusRequester.requestFocus()
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
                                effectiveActionFocusRequester.requestFocus()
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
                            effectiveActionFocusRequester.requestFocus()
                        }
                )
            }
        }
    }
}
