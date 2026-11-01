package tv.medusa.app.ui.screens.seasonal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.medusa.app.R
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.components.FormatFilterBar
import tv.medusa.app.ui.components.JellyLoadingIndicator
import tv.medusa.app.ui.components.MediaCard
import tv.medusa.app.ui.components.MediaRow
import tv.medusa.app.ui.components.SeasonalParticles
import tv.medusa.app.ui.components.TvNavTab
import tv.medusa.app.ui.components.TvTopBar
import tv.medusa.app.ui.theme.TvAccent

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonalScreen(
    eventId: String,
    onBack: () -> Unit,
    onPlayMedia: (String) -> Unit,
    onDetailMedia: (String) -> Unit,
    onNavigateHome: () -> Unit = onBack,
    onOpenMedusa: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: SeasonalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accentColorKey by viewModel.accentColor.collectAsStateWithLifecycle()
    val isMonochrome by viewModel.isMonochrome.collectAsStateWithLifecycle()
    val buttonStyleKey by viewModel.buttonStyle.collectAsStateWithLifecycle()
    val particleScale by viewModel.particleScale.collectAsStateWithLifecycle()
    val state = uiState as? SeasonalUiState.Success

    BackHandler {
        if (state?.showAll == true) viewModel.closeAll() else onBack()
    }

    LaunchedEffect(eventId) {
        viewModel.loadFeed(eventId)
    }

    when (val current = uiState) {
        is SeasonalUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090A0F)),
                contentAlignment = Alignment.Center
            ) {
                JellyLoadingIndicator(
                    size = 72.dp,
                    message = "Preparando tu selección especial...",
                    isMonochrome = isMonochrome
                )
            }
        }

        is SeasonalUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090A0F)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = current.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        is SeasonalUiState.Success -> {
            val event = current.event
            val eventAccent = remember(event.accentHex) {
                runCatching { Color(android.graphics.Color.parseColor(event.accentHex)) }
                    .getOrDefault(Color(0xFFEA580C))
            }

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF090A0F))) {
                Image(
                    painter = painterResource(id = event.backdropResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x99090A0F), Color(0xFF090A0F))
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xDD090A0F), Color(0x88090A0F), Color.Transparent)
                            )
                        )
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    TvTopBar(
                        selectedTab = TvNavTab.NONE,
                        onNavigateHome = onNavigateHome,
                        onOpenMedusa = onOpenMedusa,
                        onOpenFavorites = onOpenFavorites,
                        onOpenSearch = onOpenSearch,
                        onOpenSettings = onOpenSettings,
                        accentColorKey = accentColorKey,
                        buttonStyleKey = buttonStyleKey,
                        isMonochrome = isMonochrome,
                        titleBadge = {
                            val badgeBorder = if (isMonochrome) Color(0x33FFFFFF) else eventAccent.copy(alpha = 0.28f)
                            val badgeBg = if (isMonochrome) Color(0x22FFFFFF) else eventAccent.copy(alpha = 0.15f)
                            Box(
                                modifier = Modifier
                                    .background(badgeBg, RoundedCornerShape(16.dp))
                                    .border(0.75.dp, badgeBorder, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = event.badgeLabel,
                                    color = if (isMonochrome) Color.White else eventAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }
                    )

                    if (current.showAll) {
                        SeasonalAllGrid(
                            state = current,
                            eventAccent = eventAccent,
                            accentColorKey = accentColorKey,
                            buttonStyleKey = buttonStyleKey,
                            isMonochrome = isMonochrome,
                            onBackToLanding = viewModel::closeAll,
                            onLoadMore = viewModel::loadMore,
                            onFormat = viewModel::setFormat,
                            onToggleHideWatched = viewModel::toggleHideWatched,
                            onDetailMedia = onDetailMedia
                        )
                    } else {
                        SeasonalLanding(
                            state = current,
                            eventAccent = eventAccent,
                            accentColorKey = accentColorKey,
                            buttonStyleKey = buttonStyleKey,
                            isMonochrome = isMonochrome,
                            onOpenAll = viewModel::openAll,
                            onFormat = viewModel::setFormat,
                            onToggleHideWatched = viewModel::toggleHideWatched,
                            onDetailMedia = onDetailMedia
                        )
                    }
                }

                SeasonalParticles(
                    particleType = event.particleType,
                    modifier = Modifier.fillMaxSize(),
                    particleCount = 12,
                    particleScale = particleScale
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonalHeading(
    state: SeasonalUiState.Success,
    eventAccent: Color,
    isMonochrome: Boolean
) {
    val event = state.event
    val cinematicFont = remember { FontFamily(Font(R.font.cinzel_decorative_bold, FontWeight.Bold)) }
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
            else -> Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0)))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = event.title.uppercase(),
            fontFamily = cinematicFont,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            letterSpacing = 2.sp,
            lineHeight = 38.sp,
            style = MaterialTheme.typography.headlineLarge.copy(
                brush = titleGradient,
                shadow = Shadow(
                    color = Color(0xEE000000),
                    blurRadius = 18f,
                    offset = Offset(2f, 3f)
                )
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = event.subtitle,
            fontSize = 14.sp,
            color = Color(0xCCFFFFFF),
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth(0.75f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${state.allCount} títulos en catálogo",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMonochrome) Color(0xFF94A3B8) else eventAccent.copy(alpha = 0.9f)
            )
            if (state.isRefreshing) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Actualizando…",
                    fontSize = 11.sp,
                    color = Color(0x99FFFFFF)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonalLanding(
    state: SeasonalUiState.Success,
    eventAccent: Color,
    accentColorKey: String,
    buttonStyleKey: String,
    isMonochrome: Boolean,
    onOpenAll: () -> Unit,
    onFormat: (tv.medusa.app.domain.model.MediaFormatFilter) -> Unit,
    onToggleHideWatched: () -> Unit,
    onDetailMedia: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item(key = "seasonal_heading") {
            SeasonalHeading(state = state, eventAccent = eventAccent, isMonochrome = isMonochrome)
        }
        item(key = "seasonal_filters") {
            FormatFilterBar(
                selected = state.format,
                onSelect = onFormat,
                accentColorKey = accentColorKey,
                buttonStyleKey = buttonStyleKey,
                isMonochrome = isMonochrome,
                hideWatched = state.hideWatched,
                onToggleHideWatched = onToggleHideWatched,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        lazyItems(state.sections, key = { it.title }) { section ->
            MediaRow(
                section = section,
                onItemClick = { item: MediaItem -> onDetailMedia(item.id) },
                onItemFocus = {}
            )
        }
        item(key = "seasonal_all_button") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onOpenAll,
                    colors = ButtonDefaults.colors(
                        containerColor = if (isMonochrome) Color(0x22FFFFFF) else eventAccent.copy(alpha = 0.18f),
                        contentColor = Color.White,
                        focusedContainerColor = if (isMonochrome) Color.White else eventAccent,
                        focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else Color.White
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = androidx.compose.foundation.BorderStroke(1.dp, eventAccent.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(20.dp)
                        )
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ver toda la colección (${state.allCount})",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonalAllGrid(
    state: SeasonalUiState.Success,
    eventAccent: Color,
    accentColorKey: String,
    buttonStyleKey: String,
    isMonochrome: Boolean,
    onBackToLanding: () -> Unit,
    onLoadMore: () -> Unit,
    onFormat: (tv.medusa.app.domain.model.MediaFormatFilter) -> Unit,
    onToggleHideWatched: () -> Unit,
    onDetailMedia: (String) -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(state.items.size, state.hasMoreAll, state.isLoadingAll) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (!state.isLoadingAll && state.hasMoreAll && lastVisible >= state.items.size - 8) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 145.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "seasonal_heading_all", span = { GridItemSpan(maxLineSpan) }) {
            SeasonalHeading(state = state, eventAccent = eventAccent, isMonochrome = isMonochrome)
        }
        item(key = "seasonal_filters_all", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormatFilterBar(
                    selected = state.format,
                    onSelect = onFormat,
                    accentColorKey = accentColorKey,
                    buttonStyleKey = buttonStyleKey,
                    isMonochrome = isMonochrome,
                    hideWatched = state.hideWatched,
                    onToggleHideWatched = onToggleHideWatched,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onBackToLanding,
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0x22FFFFFF),
                        contentColor = Color.White,
                        focusedContainerColor = eventAccent,
                        focusedContentColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(16.dp))
                ) {
                    Text(text = "Volver", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (state.items.isEmpty() && state.isLoadingAll) {
            items(10) { index ->
                Box(
                    modifier = Modifier
                        .height(240.dp)
                        .background(Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                )
            }
        } else {
            gridItems(items = state.items, key = { it.id }, contentType = { "media_card" }) { item ->
                MediaCard(item = item, onClick = { onDetailMedia(item.id) })
            }
            if (state.isLoadingAll) {
                item(key = "seasonal_loading_more", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { JellyLoadingIndicator(size = 42.dp) }
                }
            }
        }
    }
}
