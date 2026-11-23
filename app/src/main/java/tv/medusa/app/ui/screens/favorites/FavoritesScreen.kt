package tv.medusa.app.ui.screens.favorites

import tv.medusa.app.ui.components.FormatFilterBar
import tv.medusa.app.ui.components.TvNavTab
import tv.medusa.app.ui.components.TvTopBar
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import tv.medusa.app.ui.util.handleTouchClick
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.medusa.app.R
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.ui.components.MediaCard
import tv.medusa.app.ui.components.sort.CatalogSortButton
import tv.medusa.app.ui.components.sort.CatalogSortOption
import tv.medusa.app.ui.theme.CinematicTypography
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary
import tv.medusa.app.ui.theme.TvAccent

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onPlayMedia: (String) -> Unit,
    onDetailMedia: (String) -> Unit,
    onNavigateHome: () -> Unit = onBack,
    onOpenMedusa: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accentColorKey by viewModel.accentColor.collectAsStateWithLifecycle()
    val buttonStyleKey by viewModel.buttonStyle.collectAsStateWithLifecycle()
    val isMonochrome = LocalIsMonochromeTheme.current
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentColorKey)

    val showIcons = buttonStyleKey != BUTTON_STYLE_TEXT_ONLY
    val showText = buttonStyleKey != BUTTON_STYLE_ICONS_ONLY

    var sortOption by rememberSaveable { mutableStateOf(CatalogSortOption.DEFAULT) }
    val sortButtonFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sortOption) {
        try { gridState.scrollToItem(0) } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06080D))
    ) {
        when (val state = uiState) {
            is FavoritesUiState.Loading -> {
                val infiniteTransition = rememberInfiniteTransition(label = "favoritesLoadingPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = focusColor.copy(alpha = pulseAlpha),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.favorites_syncing),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tv.medusa.app.ui.theme.TvPalette.TextSoft
                        )
                    }
                }
            }

            is FavoritesUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadFavorites() },
                            modifier = Modifier.handleTouchClick { viewModel.loadFavorites() },
                            colors = ButtonDefaults.colors(
                                containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                                contentColor = Color.White,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            )
                        ) {
                            Text(stringResource(R.string.common_retry), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            is FavoritesUiState.Success -> {
                val sortedItems = remember(state.filteredItems, sortOption) {
                    sortOption.sort(state.filteredItems)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Universal Persistent TV TopBar
                    TvTopBar(
                        selectedTab = TvNavTab.FAVORITES,
                        onNavigateHome = onNavigateHome,
                        onOpenMedusa = onOpenMedusa,
                        onOpenFavorites = onOpenFavorites,
                        onOpenSearch = onOpenSearch,
                        onOpenSettings = onOpenSettings,
                        accentColorKey = accentColorKey,
                        buttonStyleKey = buttonStyleKey,
                        isMonochrome = isMonochrome
                    )

                    FormatFilterBar(
                        selected = state.activeFilter,
                        onSelect = { viewModel.setFilter(it) },
                        accentColorKey = accentColorKey,
                        buttonStyleKey = buttonStyleKey,
                        isMonochrome = isMonochrome,
                        hideWatched = state.hideWatched,
                        onToggleHideWatched = { viewModel.toggleHideWatched() },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (state.filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = tv.medusa.app.ui.theme.TvPalette.Placeholder,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (state.allItems.isEmpty()) {
                                        stringResource(R.string.favorites_empty_title)
                                    } else {
                                        stringResource(R.string.favorites_empty_filter_title)
                                    },
                                    style = CinematicTypography.SectionTitle,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (state.allItems.isEmpty()) {
                                        stringResource(R.string.favorites_empty_hint)
                                    } else {
                                        stringResource(R.string.favorites_filter_hint)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Grid of favorites (Directly accessible without nested scrolling)
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(150.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 48.dp)
                        ) {
                            item(key = "fav_header", span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.favorites_collection_count, sortedItems.size),
                                        style = CinematicTypography.SectionTitle,
                                        color = Color.White
                                    )

                                    CatalogSortButton(
                                        currentSort = sortOption,
                                        onSortSelected = { option ->
                                            sortOption = option
                                            coroutineScope.launch {
                                                gridState.scrollToItem(0)
                                                delay(30)
                                                sortButtonFocusRequester.requestFocus()
                                            }
                                        },
                                        accentColorKey = accentColorKey,
                                        isMonochrome = isMonochrome,
                                        focusRequester = sortButtonFocusRequester,
                                        buttonStyleKey = buttonStyleKey,
                                        modifier = Modifier
                                            .focusRequester(sortButtonFocusRequester)
                                            .focusProperties {
                                                down = firstResultFocusRequester
                                            }
                                    )
                                }
                            }

                            itemsIndexed(
                                items = sortedItems,
                                key = { _, item -> item.id },
                                contentType = { _, _ -> "media_card" }
                            ) { index, item ->
                                MediaCard(
                                    item = item,
                                    onClick = {
                                        val targetId = if (item.type.equals("Episode", ignoreCase = true) && !item.seriesId.isNullOrBlank()) {
                                            item.seriesId!!
                                        } else {
                                            item.id
                                        }
                                        onDetailMedia(targetId)
                                    },
                                    modifier = if (index == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
