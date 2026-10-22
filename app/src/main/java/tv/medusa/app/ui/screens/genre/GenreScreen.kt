package tv.medusa.app.ui.screens.genre

import tv.medusa.app.ui.components.FormatFilterBar
import tv.medusa.app.ui.components.JellyLoadingIndicator
import tv.medusa.app.ui.components.TvNavTab
import tv.medusa.app.ui.components.TvTopBar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.medusa.app.R
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.ui.components.MediaRow
import tv.medusa.app.ui.theme.TvAccent

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

    LaunchedEffect(genreName) {
        viewModel.loadGenreFeed(genreName)
    }

    when (val state = uiState) {
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
                        text = state.message,
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
                        isMonochrome = isMonochrome,
                        titleBadge = {
                            val badgeBg = if (isMonochrome) Color(0x22FFFFFF) else TvAccent.getColor(accentColorKey).copy(alpha = 0.15f)
                            val badgeBorder = if (isMonochrome) Color(0x33FFFFFF) else TvAccent.getColor(accentColorKey).copy(alpha = 0.28f)
                            val badgeText = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
                            Box(
                                modifier = Modifier
                                    .background(badgeBg, RoundedCornerShape(16.dp))
                                    .border(0.75.dp, badgeBorder, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.genre_header, state.genreName.uppercase()),
                                    color = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    )

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "genre_format_filter_bar") {
                            FormatFilterBar(
                                selected = state.format,
                                onSelect = { viewModel.setFormat(it) },
                                accentColorKey = accentColorKey,
                                buttonStyleKey = buttonStyleKey,
                                isMonochrome = isMonochrome,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Secciones de Género
                        items(state.sections) { section ->
                            MediaRow(
                                section = section,
                                onItemClick = { item -> onDetailMedia(item.id) },
                                onItemFocus = { item -> viewModel.setFocusedItem(item) }
                            )
                        }

                        if (state.sections.isEmpty()) {
                            item {
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
                        }

                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}
