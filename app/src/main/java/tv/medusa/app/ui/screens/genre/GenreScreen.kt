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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
                    .padding(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 10.dp)
            ) {
                Text(
                    text = state.genreName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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
                        .padding(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = onOpenAll,
                        scale = ButtonDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.handleTouchClick { onOpenAll() },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isMonochrome) Color(0x22FFFFFF) else TvAccent.getColor(accentColorKey).copy(alpha = 0.18f),
                            contentColor = Color.White,
                            focusedContainerColor = accentColor,
                            focusedContentColor = focusedContent
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

    LazyVerticalGrid(
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
                    modifier = if (isTouchDevice) Modifier.weight(1f) else Modifier.fillMaxWidth()
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

        if (state.allItems.isEmpty()) {
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
            gridItems(items = state.allItems, key = { it.id }, contentType = { "media_card" }) { item ->
                MediaCard(item = item, onClick = { onDetailMedia(item.id) })
            }
        }
    }
}
