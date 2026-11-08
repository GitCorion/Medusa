package tv.medusa.app.ui.screens.brand

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import coil.compose.AsyncImage
import tv.medusa.app.R
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.components.FormatFilterBar
import tv.medusa.app.ui.components.JellyLoadingIndicator
import tv.medusa.app.ui.components.MediaCard
import tv.medusa.app.ui.components.MediaRow
import tv.medusa.app.ui.components.TvBackButton
import tv.medusa.app.ui.components.TvNavTab
import tv.medusa.app.ui.components.TvTopBar
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.util.handleTouchClick

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrandScreen(
    brandId: String,
    onBack: () -> Unit,
    onPlayMedia: (String) -> Unit,
    onDetailMedia: (String) -> Unit,
    onNavigateHome: () -> Unit = onBack,
    onOpenMedusa: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: BrandViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMonochrome by viewModel.isMonochrome.collectAsStateWithLifecycle()
    val accentColorKey by viewModel.accentColor.collectAsStateWithLifecycle()
    val buttonStyleKey by viewModel.buttonStyle.collectAsStateWithLifecycle()
    val focusColor = TvAccent.getColor(accentColorKey)
    val focusContent = TvAccent.getFocusedContentColor(accentColorKey)
    val state = uiState as? BrandUiState.Success

    BackHandler(enabled = state?.showAll == true) {
        viewModel.closeAll()
    }

    LaunchedEffect(brandId) {
        viewModel.loadBrandFeed(brandId)
    }

    when (val current = uiState) {
        is BrandUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090A0F)),
                contentAlignment = Alignment.Center
            ) {
                JellyLoadingIndicator(
                    size = 72.dp,
                    message = stringResource(R.string.brand_loading),
                    isMonochrome = isMonochrome
                )
            }
        }

        is BrandUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090A0F)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = current.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        is BrandUiState.Success -> {
            val brand = current.brand
            val gradientColors = if (isMonochrome) {
                listOf(Color(0xFF141622), Color(0xFF07070B))
            } else {
                listOf(brand.gradientStart, Color(0xFF07070B))
            }

            val focusedBackdrop = current.focusedItem?.backdropUrl

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = gradientColors))
            ) {
                // Global ambient backdrop crossfade
                Crossfade(
                    targetState = focusedBackdrop,
                    animationSpec = tween(durationMillis = 400),
                    label = "brandGlobalBackdrop"
                ) { backdropUrl ->
                    if (!backdropUrl.isNullOrBlank()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = backdropUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.25f)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xD007070B),
                                                Color(0xE007070B),
                                                Color(0xFF07070B)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Universal Persistent TV TopBar
                    TvTopBar(
                        selectedTab = TvNavTab.NONE,
                        onNavigateHome = onNavigateHome,
                        onOpenMedusa = onOpenMedusa,
                        onOpenFavorites = onOpenFavorites,
                        onOpenSearch = onOpenSearch,
                        onOpenSettings = onOpenSettings,
                        accentColorKey = accentColorKey,
                        buttonStyleKey = buttonStyleKey,
                        isMonochrome = isMonochrome
                    )

                    if (current.showAll) {
                        BrandAllGrid(
                            state = current,
                            accentColorKey = accentColorKey,
                            buttonStyleKey = buttonStyleKey,
                            isMonochrome = isMonochrome,
                            onBackToLanding = viewModel::closeAll,
                            onFormat = viewModel::setFormat,
                            onToggleHideWatched = viewModel::toggleHideWatched,
                            onDetailMedia = onDetailMedia,
                            onLoadMore = viewModel::loadMoreAll
                        )
                    } else {
                        BrandLanding(
                            state = current,
                            brandId = brandId,
                            accentColorKey = accentColorKey,
                            buttonStyleKey = buttonStyleKey,
                            isMonochrome = isMonochrome,
                            focusColor = focusColor,
                            focusContent = focusContent,
                            onOpenAll = viewModel::openAll,
                            onFormat = viewModel::setFormat,
                            onToggleHideWatched = viewModel::toggleHideWatched,
                            onDetailMedia = onDetailMedia,
                            onItemFocus = viewModel::setFocusedItem,
                            onRetry = { viewModel.loadBrandFeed(brandId, forceRefresh = true) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrandLanding(
    state: BrandUiState.Success,
    brandId: String,
    accentColorKey: String,
    buttonStyleKey: String,
    isMonochrome: Boolean,
    focusColor: Color,
    focusContent: Color,
    onOpenAll: () -> Unit,
    onFormat: (MediaFormatFilter) -> Unit,
    onToggleHideWatched: () -> Unit,
    onDetailMedia: (String) -> Unit,
    onItemFocus: (MediaItem) -> Unit,
    onRetry: () -> Unit
) {
    val brand = state.brand

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "brand_header", contentType = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 10.dp)
            ) {
                val iconRes = if (isMonochrome) brand.iconMonoRes else brand.iconRes
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = brand.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(36.dp)
                        .wrapContentWidth()
                )
            }
        }

        item(key = "brand_format_filter_bar", contentType = "filter_bar") {
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

        if (state.sections.isEmpty()) {
            item(key = "brand_empty_state", contentType = "empty_placeholder") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.brand_no_titles, brand.name),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.brand_no_titles_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.handleTouchClick { onRetry() },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF1E222D),
                                focusedContainerColor = focusColor,
                                contentColor = Color.White,
                                focusedContentColor = focusContent
                            )
                        ) {
                            Text(stringResource(R.string.brand_retry_load))
                        }
                    }
                }
            }
        } else {
            // Platform Rows
            items(
                items = state.sections,
                key = { it.title },
                contentType = { "media_row" }
            ) { section ->
                MediaRow(
                    section = section,
                    onItemClick = { item -> onDetailMedia(item.id) },
                    onItemFocus = onItemFocus
                )
            }

            if (state.allCount > 0) {
                item(key = "brand_all_button", contentType = "all_button") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = onOpenAll,
                            scale = ButtonDefaults.scale(focusedScale = 1.05f),
                            modifier = Modifier.handleTouchClick { onOpenAll() },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isMonochrome) Color(0x22FFFFFF) else brand.accentColor.copy(alpha = 0.18f),
                                contentColor = Color.White,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                            border = ButtonDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.common_view_all_count, state.allCount),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item(key = "brand_bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrandAllGrid(
    state: BrandUiState.Success,
    accentColorKey: String,
    buttonStyleKey: String,
    isMonochrome: Boolean,
    onBackToLanding: () -> Unit,
    onFormat: (MediaFormatFilter) -> Unit,
    onToggleHideWatched: () -> Unit,
    onDetailMedia: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentColorKey)
    val gridState = rememberLazyGridState()

    LaunchedEffect(state.allItems.size, state.hasMoreAll, state.isLoadingAll) {
        if (state.hasMoreAll && !state.isLoadingAll) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { lastVisible ->
                    if (lastVisible >= state.allItems.size - 8) onLoadMore()
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
        item(key = "brand_all_header", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 10.dp)
            ) {
                val iconRes = if (isMonochrome) state.brand.iconMonoRes else state.brand.iconRes
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = state.brand.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(36.dp)
                        .wrapContentWidth()
                )
            }
        }

        item(key = "brand_filters_all", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
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
                TvBackButton(
                    onClick = onBackToLanding,
                    accentColorKey = accentColorKey,
                    isMonochrome = isMonochrome
                )
            }
        }

        if (state.allItems.isEmpty()) {
            item(key = "brand_all_empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.brand_no_titles, state.brand.name),
                        color = Color(0x99FFFFFF),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            gridItems(items = state.allItems, key = { it.id }, contentType = { "media_card" }) { item ->
                MediaCard(item = item, onClick = { onDetailMedia(item.id) })
            }
            if (state.isLoadingAll) {
                item(key = "brand_loading_more", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        JellyLoadingIndicator(size = 42.dp)
                    }
                }
            }
        }
    }
}
