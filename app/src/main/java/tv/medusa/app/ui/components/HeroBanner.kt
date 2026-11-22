package tv.medusa.app.ui.components

import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.ui.components.cinema.CinemaFormatBadge
import tv.medusa.app.ui.components.cinema.CinemaOrbitDial
import tv.medusa.app.ui.components.cinema.MasterFilmTrigger
import tv.medusa.app.ui.theme.CinematicTypography
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import tv.medusa.app.ui.theme.TvCornerRadius
import tv.medusa.app.ui.theme.TvPill
import tv.medusa.app.ui.theme.TvRatingBadge
import tv.medusa.app.ui.theme.TvSpacing
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
    var isCarouselFocused by remember { mutableStateOf(false) }
    val carouselFocusRequester = remember { FocusRequester() }
    val internalActionButtonFocusRequester = remember { FocusRequester() }
    val effectiveActionFocusRequester = actionFocusRequester ?: internalActionButtonFocusRequester

    val hasSeasonalSlide = seasonalEvent != null
    val totalSlides = cleanFeatured.size + (if (hasSeasonalSlide) 1 else 0)

    // WCAG 2.2.2: la rotación automática se detiene en cuanto el usuario enfoca
    // el hero o el propio carrusel. El foco actúa como pausa explícita.
    LaunchedEffect(item, totalSlides, isHeroFocused, isCarouselFocused, carouselIndex) {
        if (item == null && !isHeroFocused && !isCarouselFocused && totalSlides > 1) {
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
            .height(430.dp)
            .onFocusChanged { isHeroFocused = it.hasFocus }
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
            val isMonochrome = LocalIsMonochromeTheme.current
            val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColor)
            val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentColor)

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = TvSpacing.ScreenSafeHorizontal, end = 24.dp, top = 104.dp, bottom = 14.dp)
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
                                color = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool else Color(0xFFFDBA74),
                                letterSpacing = 1.4.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = event.title,
                                style = CinematicTypography.HeroDisplay.copy(
                                    fontSize = 30.sp,
                                    lineHeight = 36.sp,
                                    shadow = Shadow(
                                        color = Color(0xCC000000),
                                        blurRadius = 14f,
                                        offset = Offset(0f, 2f)
                                    )
                                ),
                                color = TextPrimary,
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
                                        style = CinematicTypography.HeroDisplay.copy(
                                            fontSize = 30.sp,
                                            lineHeight = 36.sp,
                                            shadow = Shadow(
                                                color = Color.Black,
                                                blurRadius = 18f,
                                                offset = Offset(2f, 3f)
                                            )
                                        ),
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = effectiveItem.title,
                                style = CinematicTypography.HeroDisplay.copy(
                                    fontSize = 30.sp,
                                    lineHeight = 36.sp,
                                    shadow = Shadow(
                                        color = Color.Black,
                                        blurRadius = 18f,
                                        offset = Offset(2f, 3f)
                                    )
                                ),
                                color = TextPrimary,
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
                            color = tv.medusa.app.ui.theme.TvPalette.TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is HeroSlideState.Movie -> {
                        val effectiveItem = currentSlideState.item
                        // Metadata Row (Typographic hierarchy with clean delimiters)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (effectiveItem.rating != null && effectiveItem.rating > 0f) {
                                TvRatingBadge(rating = effectiveItem.rating, compact = false)
                            }

                            if (effectiveItem.year != null) {
                                CinemaFormatBadge(text = "${effectiveItem.year}")
                            }

                            val isTv = effectiveItem.type.equals("Series", ignoreCase = true) ||
                                    effectiveItem.type.equals("TvProgram", ignoreCase = true) ||
                                    effectiveItem.type.equals("Episode", ignoreCase = true)
                            val typeText = stringResource(if (isTv) R.string.type_series else R.string.type_movie)
                            CinemaFormatBadge(text = typeText.uppercase())

                            val hasEpisodeProgress = !effectiveItem.isPlayed &&
                                    isTv &&
                                    effectiveItem.playedEpisodes != null &&
                                    effectiveItem.playedEpisodes > 0
                            val hasMovieProgress = !effectiveItem.isPlayed &&
                                    !isTv &&
                                    effectiveItem.playbackPositionTicks > 0

                            if (hasEpisodeProgress) {
                                val progressText = if (effectiveItem.totalEpisodes != null && effectiveItem.totalEpisodes > 0) {
                                    "${effectiveItem.playedEpisodes}/${effectiveItem.totalEpisodes} CAPS"
                                } else {
                                    "${effectiveItem.playedEpisodes} CAPS"
                                }
                                CinemaFormatBadge(
                                    text = progressText,
                                    isHighlighted = true,
                                    highlightColor = focusColor
                                )
                            } else if (hasMovieProgress) {
                                CinemaFormatBadge(
                                    text = stringResource(R.string.badge_in_progress).uppercase(),
                                    isHighlighted = true,
                                    highlightColor = focusColor
                                )
                            } else if (effectiveItem.isPlayed) {
                                CinemaFormatBadge(
                                    text = stringResource(R.string.badge_watched).uppercase(),
                                    isHighlighted = true,
                                    highlightColor = Color(0xFF10B981)
                                )
                            } else if (effectiveItem.isFavorite) {
                                CinemaFormatBadge(
                                    text = stringResource(R.string.badge_favorite).uppercase(),
                                    isHighlighted = true,
                                    highlightColor = Color(0xFFF43F5E)
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
                                color = tv.medusa.app.ui.theme.TvPalette.TextMuted,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    HeroSlideState.Empty -> {}
                }

                Spacer(modifier = Modifier.height(18.dp))

                val isSeasonalActive = currentSlideState is HeroSlideState.Seasonal

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val playLabel = if (isSeasonalActive) {
                        stringResource(R.string.seasonal_view_collection)
                    } else {
                        stringResource(R.string.detail_play)
                    }
                    val hasInfoButton = currentSlideState is HeroSlideState.Movie
                    val actionIcon = if (isSeasonalActive) Icons.AutoMirrored.Rounded.PlaylistPlay else Icons.Rounded.PlayArrow
                    MasterFilmTrigger(
                        label = playLabel,
                        technicalSpec = null,
                        icon = actionIcon,
                        focusColor = focusColor,
                        focusContentColor = focusContent,
                        focusRequester = effectiveActionFocusRequester,
                        modifier = Modifier
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    if (keyEvent.key == Key.DirectionUp && onUpFromHero != null) {
                                        onUpFromHero()
                                        true
                                    } else if (keyEvent.key == Key.DirectionDown && totalSlides > 1) {
                                        // El carrusel está en otra rama: el foco geomérico saltaba
                                        // a los chips de género. Lo enlazamos explícitamente.
                                        carouselFocusRequester.requestFocus()
                                        true
                                    } else if (keyEvent.key == Key.DirectionLeft && totalSlides > 1) {
                                        carouselIndex = if (carouselIndex > 0) carouselIndex - 1 else totalSlides - 1
                                        effectiveActionFocusRequester.requestFocus()
                                        true
                                    } else if (keyEvent.key == Key.DirectionRight && !hasInfoButton && totalSlides > 1) {
                                        carouselIndex = (carouselIndex + 1) % totalSlides
                                        effectiveActionFocusRequester.requestFocus()
                                        true
                                    } else false
                                } else false
                            },
                        onClick = {
                            when (currentSlideState) {
                                is HeroSlideState.Seasonal -> onExploreSeasonal?.invoke()
                                is HeroSlideState.Movie -> onPlayClick(currentSlideState.item)
                                HeroSlideState.Empty -> {}
                            }
                        }
                    )

                    if (hasInfoButton) {
                        CinemaOrbitDial(
                            icon = Icons.Rounded.Info,
                            contentDescription = stringResource(R.string.common_details),
                            focusColor = focusColor,
                            focusContentColor = focusContent,
                            modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    if (keyEvent.key == Key.DirectionUp && onUpFromHero != null) {
                                        onUpFromHero()
                                        true
                                    } else if (keyEvent.key == Key.DirectionDown && totalSlides > 1) {
                                        carouselFocusRequester.requestFocus()
                                        true
                                    } else if (keyEvent.key == Key.DirectionRight && totalSlides > 1) {
                                        carouselIndex = (carouselIndex + 1) % totalSlides
                                        effectiveActionFocusRequester.requestFocus()
                                        true
                                    } else false
                                } else false
                            },
                            onClick = {
                                onDetailClick((currentSlideState as HeroSlideState.Movie).item)
                            }
                        )
                    }
                }
            }
        }

        // Carousel Indicators & Subtle Navigation Chevrons (Bottom Right)
        // Un único objetivo de foco: las flechas y los puntos aislados tenían
        // canFocus = false y eran inalcanzables con D-Pad y TalkBack.
        if (item == null && totalSlides > 1) {
            val focusColor = TvAccent.getColor(accentColor)
            val carouselFocusColor = if (isMonochrome) Color.White else focusColor
            val carouselFocusContent = tv.medusa.app.ui.theme.CyanFocusedContent
            val carouselContentDescription = stringResource(R.string.hero_carousel_cd)
            val goPrevious = {
                carouselIndex = if (carouselIndex > 0) carouselIndex - 1 else totalSlides - 1
            }
            val goNext = { carouselIndex = (carouselIndex + 1) % totalSlides }
            val currentActiveIdx = if (totalSlides > 0) carouselIndex % totalSlides else 0

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = TvSpacing.ScreenSafeHorizontal, bottom = 24.dp)
                    .focusable()
                    .focusRequester(carouselFocusRequester)
                    .onFocusChanged { isCarouselFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (keyEvent.key) {
                                Key.DirectionLeft -> { goPrevious(); true }
                                Key.DirectionRight -> { goNext(); true }
                                Key.DirectionUp -> {
                                    effectiveActionFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = goNext
                    )
                    .background(
                        color = if (isCarouselFocused) carouselFocusColor else Color(0x10070709),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = if (isCarouselFocused) 0.dp else 0.6.dp,
                        color = if (isCarouselFocused) Color.Transparent else Color(0x1CFFFFFF),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .semantics { contentDescription = carouselContentDescription },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = null,
                    tint = if (isCarouselFocused) carouselFocusContent else Color.White.copy(alpha = 0.60f),
                    modifier = Modifier.size(11.dp)
                )
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
                        targetValue = if (isActive) 26.dp else 8.dp,
                        animationSpec = tween(durationMillis = 350),
                        label = "carouselIndicatorWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .width(indicatorWidth)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                when {
                                    !isActive && isCarouselFocused -> carouselFocusContent.copy(alpha = 0.40f)
                                    isActive && isCarouselFocused -> carouselFocusContent
                                    isActive -> activeColor
                                    else -> Color(0x35FFFFFF)
                                }
                            )
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = if (isCarouselFocused) carouselFocusContent else Color.White.copy(alpha = 0.60f),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}
