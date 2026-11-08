package tv.medusa.app.ui.screens.detail

import androidx.compose.foundation.layout.FlowRow
import tv.medusa.app.ui.components.JellyLoadingIndicator
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.currentFocusColor
import tv.medusa.app.ui.theme.currentFocusContentColor
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.foundation.layout.statusBarsPadding
import tv.medusa.app.ui.util.handleTouchClick
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.domain.model.SeriesStatusType
import tv.medusa.app.ui.components.EpisodeCard
import tv.medusa.app.ui.components.InAppTrailerPlayer
import tv.medusa.app.ui.components.MediaCard
import tv.medusa.app.ui.components.TvBackButton
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvPill
import tv.medusa.app.ui.theme.TvRatingBadge
import tv.medusa.app.util.DeviceUtils

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    itemId: String,
    onPlay: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    var isShowingTrailer by remember { mutableStateOf(false) }
    var isLaunchingPlayer by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            isLaunchingPlayer = false
        }
    }

    LaunchedEffect(itemId) {
        viewModel.loadDetail(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMonochromeFlow by viewModel.isMonochrome.collectAsStateWithLifecycle()
    val isMonochrome = LocalIsMonochromeTheme.current || isMonochromeFlow

    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState) {
        if (uiState is DetailUiState.Success) {
            delay(120)
            runCatching {
                playButtonFocusRequester.requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06080D))
    ) {
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    JellyLoadingIndicator(
                        size = 84.dp,
                        message = stringResource(R.string.detail_loading),
                        isMonochrome = isMonochrome
                    )
                }
            }

            is DetailUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val errFocusColor = currentFocusColor(isMonochrome)
                        val errFocusContent = currentFocusContentColor(isMonochrome)
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0x22FFFFFF),
                                contentColor = Color.White,
                                focusedContainerColor = errFocusColor,
                                focusedContentColor = errFocusContent
                            )
                        ) {
                            Text(stringResource(R.string.common_back), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            is DetailUiState.Success -> {
                val entity = state.entity
                val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(state.accentColor)
                val focusContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(state.accentColor)
                val isTv = entity.type.equals("Series", ignoreCase = true) || entity.type.equals("Episode", ignoreCase = true)
                val isTvSeries = entity.type.equals("Series", ignoreCase = true)

                // Backdrop Image Background
                if (state.backdropUrl != null) {
                    AsyncImage(
                        model = state.backdropUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Dark Gradient Overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xF506080D),
                                    Color(0xD806080D),
                                    Color(0x8806080D),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = 1400f
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x5506080D),
                                    Color(0xFF06080D)
                                )
                            )
                        )
                )

                val context = LocalContext.current
                val isTouchDevice = remember(context) { DeviceUtils.isTouchDevice(context) }
                val detailScrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(detailScrollState)
                        .padding(top = 40.dp, bottom = 64.dp)
                ) {
                    // Hero Section: Poster + Metadata + Action Buttons + Synopsis
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Poster Card
                        if (state.posterUrl != null) {
                            Box(
                                modifier = Modifier
                                    .width(210.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF141B26))
                            ) {
                                AsyncImage(
                                    model = state.posterUrl,
                                    contentDescription = entity.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(36.dp))
                        }

                        Column(
                            modifier = if (state.posterUrl != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
                        ) {
                            val isEpisode = entity.type.equals("Episode", ignoreCase = true)
                            val sNum = entity.seasonNumber ?: 1
                            val eNum = entity.episodeNumber ?: 1
                            val sName = entity.seriesName ?: ""

                            if (isEpisode && sName.isNotBlank()) {
                                Text(
                                    text = sName.uppercase(),
                                    color = if (isMonochrome) Color.White else focusColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            // ClearLogo transparent vector with high-contrast typography fallback
                            var isLogoLoaded by remember(entity.id) { mutableStateOf(false) }

                            if (!state.logoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = state.logoUrl,
                                    contentDescription = entity.title,
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .heightIn(max = 75.dp)
                                        .widthIn(max = 380.dp)
                                        .graphicsLayer { alpha = if (isLogoLoaded) 1f else 0f },
                                    onSuccess = { isLogoLoaded = true },
                                    onError = { isLogoLoaded = false }
                                )
                            }

                            if (state.logoUrl.isNullOrBlank() || !isLogoLoaded) {
                                Text(
                                    text = entity.title,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        initialDelayMillis = 1500,
                                        repeatDelayMillis = 2000,
                                        velocity = 35.dp
                                    )
                                )
                            }

                            if (isEpisode) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.detail_season_episode, sNum, eNum),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Metadata Badges Row
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (entity.communityRating != null && entity.communityRating > 0f) {
                                    TvRatingBadge(rating = entity.communityRating, compact = false)
                                }

                                if (entity.productionYear != null) {
                                    TvPill(text = "${entity.productionYear}")
                                }

                                TvPill(
                                    text = when {
                                        state.isCollection -> stringResource(R.string.detail_collection)
                                        isEpisode -> stringResource(R.string.detail_type_episode, sNum, eNum)
                                        isTvSeries -> stringResource(R.string.detail_type_series)
                                        else -> stringResource(R.string.detail_type_movie)
                                    }
                                )

                                // Age Rating Badge (OfficialRating: PG-13, R, TV-MA, etc.)
                                if (!entity.officialRating.isNullOrBlank()) {
                                    TvPill(text = entity.officialRating)
                                }

                                // Series Status Badge
                                if (state.seriesStatus != null && state.seriesStatus.type != SeriesStatusType.UNKNOWN) {
                                    val statusLabel = when (state.seriesStatus.type) {
                                        SeriesStatusType.CANCELED -> stringResource(R.string.series_status_canceled)
                                        SeriesStatusType.ENDED -> stringResource(R.string.series_status_ended)
                                        SeriesStatusType.CONTINUING -> stringResource(R.string.series_status_continuing)
                                        SeriesStatusType.UNKNOWN -> ""
                                    }
                                    if (statusLabel.isNotBlank()) {
                                        val (statusBg, statusBorder, statusText) = when {
                                            isMonochrome -> Triple(Color(0x18FFFFFF), Color(0x22FFFFFF), Color(0xFFCBD5E1))
                                            state.seriesStatus.type == SeriesStatusType.CONTINUING -> Triple(Color(0x2010B981), Color(0x3510B981), Color(0xFF6EE7B7))
                                            state.seriesStatus.type == SeriesStatusType.CANCELED -> Triple(Color(0x20EF4444), Color(0x35EF4444), Color(0xFFFCA5A5))
                                            else -> Triple(Color(0x18FFFFFF), Color(0x22FFFFFF), Color(0xFFCBD5E1))
                                        }
                                        TvPill(
                                            text = statusLabel,
                                            containerColor = statusBg,
                                            borderColor = statusBorder,
                                            textColor = statusText
                                        )
                                    }
                                }

                                // Individual Genre Pills
                                val genreList = entity.genres
                                    ?.split(",", ";")
                                    ?.map { it.trim() }
                                    ?.filter { it.isNotBlank() }
                                    ?: emptyList()

                                genreList.forEach { genre ->
                                    TvPill(text = genre)
                                }

                                val totalEps = entity.totalItemCount
                                val unplayedEps = entity.unplayedItemCount
                                val playedEps = state.playedEpisodes ?: if (totalEps != null && unplayedEps != null) (totalEps - unplayedEps).coerceAtLeast(0) else 0
                                val isSeriesFinished = if (isTvSeries && unplayedEps != null) unplayedEps == 0 && (totalEps ?: 0) > 0 else entity.isPlayed
                                val hasSeriesProgress = !isSeriesFinished && isTvSeries && totalEps != null && totalEps > 0 && playedEps > 0
                                val hasItemProgress = !isTvSeries && entity.playbackPositionTicks > 0 && !entity.isPlayed

                                if (hasSeriesProgress) {
                                    TvPill(
                                        text = stringResource(R.string.detail_progress, playedEps, totalEps ?: 0),
                                        containerColor = if (isMonochrome) Color(0x22FFFFFF) else focusColor.copy(alpha = 0.16f),
                                        textColor = if (isMonochrome) Color.White else focusColor
                                    )
                                }

                                if (hasItemProgress) {
                                    TvPill(
                                        text = stringResource(R.string.badge_in_progress),
                                        containerColor = if (isMonochrome) Color(0x22FFFFFF) else focusColor.copy(alpha = 0.16f),
                                        textColor = if (isMonochrome) Color.White else focusColor
                                    )
                                }

                                if (state.isFavorite) {
                                    TvPill(
                                        text = stringResource(R.string.badge_favorite),
                                        containerColor = if (isMonochrome) Color(0x18FFFFFF) else Color(0x20F43F5E),
                                        textColor = if (isMonochrome) Color(0xFFE2E8F0) else Color(0xFFFDA4AF)
                                    )
                                }

                                if (isSeriesFinished && !hasSeriesProgress && !hasItemProgress) {
                                    TvPill(
                                        text = stringResource(R.string.badge_watched),
                                        containerColor = if (isMonochrome) Color(0x18FFFFFF) else Color(0x2010B981),
                                        textColor = if (isMonochrome) Color(0xFFE2E8F0) else Color(0xFF6EE7B7)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            val showIcons = state.buttonStyle != BUTTON_STYLE_TEXT_ONLY
                            val showText = state.buttonStyle != BUTTON_STYLE_ICONS_ONLY

                            val actionButtonPadding = if (!showText) {
                                PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            } else {
                                PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            }

                            // Action Buttons Row (Adapts dynamically to buttonStyle)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val playTargetId = when {
                                    state.isCollection && state.collectionItems.isNotEmpty() -> state.collectionItems.first().id
                                    isTvSeries && state.nextUpEpisode != null -> state.nextUpEpisode.id
                                    isTvSeries && state.episodes.isNotEmpty() -> state.episodes.first().id
                                    else -> entity.id
                                }
                                val isResume = (state.nextUpEpisode != null && (state.nextUpEpisode.isPlayed || state.nextUpEpisode.playbackPositionTicks > 0)) ||
                                        (!isTvSeries && entity.playbackPositionTicks > 0)
                                val playLabel = when {
                                    state.isCollection -> if (state.collectionItems.isNotEmpty()) stringResource(R.string.detail_play_collection) else stringResource(R.string.detail_play)
                                    isTvSeries && state.nextUpEpisode != null && isResume -> stringResource(R.string.detail_resume_episode, state.nextUpEpisode.displayCode)
                                    isTvSeries && state.nextUpEpisode != null -> stringResource(R.string.detail_play_episode, state.nextUpEpisode.displayCode)
                                    isTvSeries && state.episodes.isNotEmpty() -> stringResource(R.string.detail_play_first_episode)
                                    isTvSeries -> stringResource(R.string.detail_play)
                                    !isTvSeries && entity.playbackPositionTicks > 0 -> stringResource(R.string.detail_continue)
                                    else -> stringResource(R.string.detail_play)
                                }
                                val playIcon = Icons.Rounded.PlayArrow

                                // 1. Play Button
                                Button(
                                    onClick = {
                                        isLaunchingPlayer = true
                                        onPlay(playTargetId)
                                    },
                                    contentPadding = actionButtonPadding,
                                    scale = ButtonDefaults.scale(focusedScale = 1.06f),
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isMonochrome) Color(0x28FFFFFF) else focusColor.copy(alpha = 0.20f),
                                        contentColor = if (isMonochrome) Color.White else Color(0xFFE0F2FE),
                                        focusedContainerColor = focusColor,
                                        focusedContentColor = focusContent
                                    ),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                    modifier = Modifier
                                        .handleTouchClick {
                                            isLaunchingPlayer = true
                                            onPlay(playTargetId)
                                        }
                                        .focusRequester(playButtonFocusRequester)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (showIcons) {
                                            Icon(
                                                imageVector = playIcon,
                                                contentDescription = playLabel,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (showText) {
                                            if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                                            Text(playLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                    }
                                }

                                // 2. Go to Series Button (when viewing an episode)
                                if (isEpisode && !entity.seriesId.isNullOrBlank()) {
                                    Button(
                                        onClick = {
                                            onNavigateToDetail(entity.seriesId)
                                        },
                                        contentPadding = actionButtonPadding,
                                        scale = ButtonDefaults.scale(focusedScale = 1.06f),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                        border = ButtonDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border.None
                                        ),
                                        modifier = Modifier.handleTouchClick { onNavigateToDetail(entity.seriesId) },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isMonochrome) Color(0x14FFFFFF) else focusColor.copy(alpha = 0.10f),
                                            contentColor = if (isMonochrome) Color.White else Color(0xFFBAE6FD),
                                            focusedContainerColor = focusColor,
                                            focusedContentColor = focusContent
                                        )
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (showIcons) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Tv,
                                                    contentDescription = if (sName.isNotBlank()) "Ir a $sName" else "Ir a la serie",
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            if (showText) {
                                                if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (sName.isNotBlank()) "Ir a $sName" else "Ir a la serie",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. Favorite Toggle Button
                                var isFavFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { viewModel.toggleFavorite() },
                                    contentPadding = actionButtonPadding,
                                    scale = ButtonDefaults.scale(focusedScale = 1.06f),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    modifier = Modifier
                                        .onFocusChanged { isFavFocused = it.isFocused }
                                        .handleTouchClick { viewModel.toggleFavorite() },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (state.isFavorite) {
                                            (if (isMonochrome) Color(0x28FFFFFF) else focusColor.copy(alpha = 0.22f))
                                        } else {
                                            (if (isMonochrome) Color(0x14FFFFFF) else focusColor.copy(alpha = 0.10f))
                                        },
                                        contentColor = if (isMonochrome) Color.White else Color(0xFFBAE6FD),
                                        focusedContainerColor = focusColor,
                                        focusedContentColor = focusContent
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (showIcons) {
                                            Icon(
                                                imageVector = if (state.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                contentDescription = if (state.isFavorite) stringResource(R.string.detail_in_favorites) else stringResource(R.string.detail_add_favorites),
                                                tint = if (isFavFocused) focusContent else (if (state.isFavorite && !isMonochrome) Color(0xFFEF4444) else Color.White),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        if (showText) {
                                            if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (state.isFavorite) stringResource(R.string.detail_in_favorites) else stringResource(R.string.detail_add_favorites),
                                                color = if (isFavFocused) focusContent else Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                // 4. Trailer Button
                                if (state.trailerUrl != null) {
                                    Button(
                                        onClick = { isShowingTrailer = true },
                                        contentPadding = actionButtonPadding,
                                        scale = ButtonDefaults.scale(focusedScale = 1.06f),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                        border = ButtonDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border.None
                                        ),
                                        modifier = Modifier.handleTouchClick { isShowingTrailer = true },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isMonochrome) Color(0x14FFFFFF) else focusColor.copy(alpha = 0.10f),
                                            contentColor = if (isMonochrome) Color.White else Color(0xFFBAE6FD),
                                            focusedContainerColor = focusColor,
                                            focusedContentColor = focusContent
                                        )
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (showIcons) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Movie,
                                                    contentDescription = stringResource(R.string.detail_watch_trailer),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            if (showText) {
                                                if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.detail_watch_trailer), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }

                                // 5. Mark as Watched / Watched Button
                                var isWatchedFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { viewModel.togglePlayed() },
                                    contentPadding = actionButtonPadding,
                                    scale = ButtonDefaults.scale(focusedScale = 1.06f),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                    border = ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None
                                    ),
                                    modifier = Modifier
                                        .onFocusChanged { isWatchedFocused = it.isFocused }
                                        .handleTouchClick { viewModel.togglePlayed() },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (state.isPlayed) {
                                            (if (isMonochrome) Color(0x28FFFFFF) else focusColor.copy(alpha = 0.22f))
                                        } else {
                                            (if (isMonochrome) Color(0x14FFFFFF) else focusColor.copy(alpha = 0.10f))
                                        },
                                        contentColor = if (isMonochrome) Color.White else Color(0xFFBAE6FD),
                                        focusedContainerColor = focusColor,
                                        focusedContentColor = focusContent
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (showIcons) {
                                            Icon(
                                                imageVector = if (state.isPlayed) Icons.Rounded.CheckCircle else Icons.Rounded.Check,
                                                contentDescription = if (state.isPlayed) "Visto" else "Marcar como visto",
                                                tint = if (isWatchedFocused) focusContent else (if (state.isPlayed && !isMonochrome) Color(0xFF10B981) else Color.White),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        if (showText) {
                                            if (showIcons) Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (state.isPlayed) "Visto" else "Marcar como visto",
                                                color = if (isWatchedFocused) focusContent else Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Overview Synopsis - Clean editorial typography (Zero SaaS box slop)
                            val overviewText = entity.overview?.takeIf { it.isNotBlank() }
                            if (!overviewText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = overviewText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 24.sp
                                    ),
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Seasons and Episodes Section for Series
                    if (isTvSeries && state.seasons.isNotEmpty()) {
                        if (state.seasons.size > 1) {
                            Spacer(modifier = Modifier.height(36.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 48.dp)
                            ) {
                                if (!isMonochrome) {
                                    Box(
                                        modifier = Modifier
                                            .height(14.dp)
                                            .width(3.dp)
                                            .background(focusColor, RoundedCornerShape(1.5.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = stringResource(R.string.detail_seasons),
                                    color = if (!isMonochrome) Color(0xFFCBD5E1) else Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = state.seasons,
                                    key = { it.id },
                                    contentType = { "season_chip" }
                                ) { season ->
                                    val isSelected = season.id == state.selectedSeasonId
                                    Button(
                                        onClick = { viewModel.selectSeason(season.id) },
                                        modifier = Modifier.handleTouchClick { viewModel.selectSeason(season.id) },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) (if (isMonochrome) Color(0x33FFFFFF) else focusColor.copy(alpha = 0.22f)) else Color(0x10FFFFFF),
                                            contentColor = if (isSelected) (if (isMonochrome) Color.White else focusColor) else Color(0xFF94A3B8),
                                            focusedContainerColor = focusColor,
                                            focusedContentColor = focusContent
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border.None
                                        ),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(16.dp))
                                    ) {
                                        Text(
                                            text = season.name,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 48.dp)
                        ) {
                            if (!isMonochrome) {
                                Box(
                                    modifier = Modifier
                                        .height(14.dp)
                                        .width(3.dp)
                                        .background(focusColor, RoundedCornerShape(1.5.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(R.string.detail_episodes_count, state.episodes.size),
                                color = if (!isMonochrome) Color(0xFFCBD5E1) else Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (state.isLoadingEpisodes) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.detail_loading_episodes),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                            }
                        } else if (state.episodes.isEmpty()) {
                            Text(
                                text = stringResource(R.string.detail_no_episodes),
                                color = Color(0xFF64748B),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 48.dp)
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = state.episodes,
                                    key = { it.id },
                                    contentType = { "episode_card" }
                                ) { episode ->
                                    EpisodeCard(
                                        episode = episode,
                                        onClick = {
                                            isLaunchingPlayer = true
                                            onPlay(episode.id)
                                        },
                                        accentColor = state.accentColor
                                    )
                                }
                            }
                        }
                    }

                    // Collection Titles Section for Collections / BoxSets (Full-Width TV Carousel)
                    if (state.collectionItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(36.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 48.dp)
                        ) {
                            if (!isMonochrome) {
                                Box(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .width(3.dp)
                                        .background(focusColor, RoundedCornerShape(1.5.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(R.string.detail_collection_titles, state.collectionItems.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 18.dp, bottom = 24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = state.collectionItems,
                                key = { it.id },
                                contentType = { "media_card" }
                            ) { item ->
                                MediaCard(
                                    item = item,
                                    onClick = {
                                        viewModel.loadDetail(item.id)
                                    }
                                )
                            }
                        }
                    }

                    // Discrete Recommendations Section (Full-Width TV Carousels with anti-collision spacing)
                    if (state.similarItems.isNotEmpty() || (state.genreItems.isNotEmpty() && !state.genreName.isNullOrBlank())) {
                        Spacer(modifier = Modifier.height(40.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                                .height(1.dp)
                                .background(Color(0x18FFFFFF))
                        )
                        Spacer(modifier = Modifier.height(28.dp))

                        if (state.similarItems.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 48.dp)
                            ) {
                                if (!isMonochrome) {
                                    Box(
                                        modifier = Modifier
                                            .height(16.dp)
                                            .width(3.dp)
                                            .background(focusColor, RoundedCornerShape(1.5.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = stringResource(R.string.detail_similar_titles),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 18.dp, bottom = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = state.similarItems,
                                    key = { it.id },
                                    contentType = { "media_card" }
                                ) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = {
                                            viewModel.loadDetail(item.id)
                                        }
                                    )
                                }
                            }
                        }

                        if (state.genreItems.isNotEmpty() && !state.genreName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(36.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 48.dp)
                            ) {
                                if (!isMonochrome) {
                                    Box(
                                        modifier = Modifier
                                            .height(16.dp)
                                            .width(3.dp)
                                            .background(focusColor, RoundedCornerShape(1.5.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = stringResource(R.string.detail_more_from_genre, state.genreName!!.uppercase()),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 18.dp, bottom = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = state.genreItems,
                                    key = { it.id },
                                    contentType = { "media_card" }
                                ) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = {
                                            viewModel.loadDetail(item.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Top scrim gradient para máxima legibilidad de botón volver y status bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xD906080D),
                                    Color(0x6006080D),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Botón Volver táctil ultra-elegante (solo en tablets / pantallas táctiles; en TV se usa la tecla BACK física)
                if (isTouchDevice) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 28.dp, top = 22.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x40000000))
                            .focusProperties { canFocus = false }
                            .handleTouchClick { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                if (isShowingTrailer && state.trailerUrl != null) {
                    InAppTrailerPlayer(
                        trailerUrl = state.trailerUrl,
                        onClose = { isShowingTrailer = false }
                    )
                }
            }
        }

        // Overlay when launching player
        if (isLaunchingPlayer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = if (isMonochrome) Color.White else Color(0xFF38BDF8),
                        strokeWidth = 3.5.dp,
                        modifier = Modifier.size(50.dp)
                    )
                    Text(
                        text = stringResource(R.string.detail_starting_player),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
