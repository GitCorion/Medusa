package tv.medusa.app.ui.screens.genre

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import tv.medusa.app.R
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.components.FormatFilterBar
import tv.medusa.app.ui.components.JellyLoadingIndicator
import tv.medusa.app.ui.components.MediaCard
import tv.medusa.app.ui.components.MediaRow
import tv.medusa.app.ui.components.TvBackButton
import tv.medusa.app.ui.components.TvNavTab
import tv.medusa.app.ui.components.TvTopBar
import tv.medusa.app.ui.components.sort.CatalogSortButton
import tv.medusa.app.ui.components.sort.CatalogSortOption
import tv.medusa.app.ui.components.cinema.MasterFilmTrigger
import tv.medusa.app.ui.theme.CinematicTypography
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.util.handleTouchClick
import tv.medusa.app.util.DeviceUtils

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GenreScreen(
    genreName: String,
    onBack: () -> Unit,
    onPlayMedia: (String) -> Unit,
    onDetailMedia: (String) -> Unit,
    onNavigateHome: () -> Unit = onBack,
    onOpenMedusa: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: GenreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accentColorKey by viewModel.accentColor.collectAsStateWithLifecycle()
    val isMonochrome by viewModel.isMonochrome.collectAsStateWithLifecycle()
    val buttonStyleKey by viewModel.buttonStyle.collectAsStateWithLifecycle()
    val state = uiState as? GenreUiState.Success

    BackHandler(enabled = state?.showAll == true) {
        viewModel.closeAll()
    }

    LaunchedEffect(genreName) {
        viewModel.loadGenreFeed(genreName)
    }

    when (val current = uiState) {
        is GenreUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090A0F)),
                contentAlignment = Alignment.Center
            ) {
                JellyLoadingIndicator(
                    size = 72.dp,
                    message = stringResource(R.string.genre_loading, genreName),
                    isMonochrome = isMonochrome
                )
            }
        }

        is GenreUiState.Error -> {
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

        is GenreUiState.Success -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF111224), Color(0xFF07070B))
                        )
                    )
            ) {
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
                        GenreAllGrid(
                            state = current,
                            accentColorKey = accentColorKey,
                            buttonStyleKey = buttonStyleKey,
                            isMonochrome = isMonochrome,
                            onBackToLanding = viewModel::closeAll,
                            onFormat = viewModel::setFormat,
                            onToggleHideWatched = viewModel::toggleHideWatched,
                            onDetailMedia = onDetailMedia
                        )
                    } else {
                        GenreLanding(
                            state = current,
                            accentColorKey = accentColorKey,
                            buttonStyleKey = buttonStyleKey,
                            isMonochrome = isMonochrome,
                            onOpenAll = viewModel::openAll,
                            onFormat = viewModel::setFormat,
                            onToggleHideWatched = viewModel::toggleHideWatched,
                            onDetailMedia = onDetailMedia,
                            onItemFocus = viewModel::setFocusedItem
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GenreLanding(
    state: GenreUiState.Success,
    accentColorKey: String,
    buttonStyleKey: String,
    isMonochrome: Boolean,
    onOpenAll: () -> Unit,
    onFormat: (GenreFormat) -> Unit,
    onToggleHideWatched: () -> Unit,
    onDetailMedia: (String) -> Unit,
    onItemFocus: (MediaItem) -> Unit
) {
    val accentColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusedContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentColorKey)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "genre_header", contentType = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 12.dp)
            ) {
                Text(
                    text = state.genreName.uppercase(),
                    style = CinematicTypography.HeroDisplay.copy(fontSize = 32.sp, letterSpacing = 2.sp),
                    color = TextPrimary
                )
            }
        }

        item(key = "genre_format_filter_bar", contentType = "filter_bar") {
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

        // Secciones de Género
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

        if (state.sections.isEmpty()) {
            item(key = "genre_empty", contentType = "empty_placeholder") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.genre_empty, state.genreName),
                        color = Color(0x99FFFFFF),
                        fontSize = 14.sp
                    )
                }
            }
        } else if (state.allCount > 0) {
            item(key = "genre_all_button", contentType = "all_button") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    MasterFilmTrigger(
                        label = stringResource(R.string.common_view_all_count, state.allCount),
                        icon = Icons.Rounded.Apps,
                        focusColor = accentColor,
                        focusContentColor = focusedContent,
                        onClick = onOpenAll
                    )
                }
            }
        }

        item(key = "genre_bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GenreAllGrid(
    state: GenreUiState.Success,
    accentColorKey: String,
    buttonStyleKey: String,
    isMonochrome: Boolean,
    onBackToLanding: () -> Unit,
    onFormat: (GenreFormat) -> Unit,
    onToggleHideWatched: () -> Unit,
    onDetailMedia: (String) -> Unit
) {
    val accentColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusedContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentColorKey)
    val context = LocalContext.current
    val isTouchDevice = remember(context) { DeviceUtils.isTouchDevice(context) }

    var sortOption by rememberSaveable { mutableStateOf(CatalogSortOption.DEFAULT) }
    val sortedItems = remember(state.allItems, sortOption) {
        sortOption.sort(state.allItems)
    }

    val gridState = rememberLazyGridState()

    LaunchedEffect(sortOption) {
        try { gridState.scrollToItem(0) } catch (_: Exception) {}
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 145.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "genre_all_header", span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                Text(
                    text = state.genreName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        item(key = "genre_filters_all", span = { GridItemSpan(maxLineSpan) }) {
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
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CatalogSortButton(
                        currentSort = sortOption,
                        onSortSelected = { sortOption = it },
                        accentColorKey = accentColorKey,
                        isMonochrome = isMonochrome
                    )
                    if (isTouchDevice) {
                        TvBackButton(
                            onClick = onBackToLanding,
                            accentColorKey = accentColorKey,
                            isMonochrome = isMonochrome
                        )
                    }
                }
            }
        }

        if (sortedItems.isEmpty()) {
            item(key = "genre_all_empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.genre_empty, state.genreName),
                        color = Color(0x99FFFFFF),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            gridItems(items = sortedItems, key = { it.id }, contentType = { "media_card" }) { item ->
                MediaCard(item = item, onClick = { onDetailMedia(item.id) })
            }
        }
    }
}
