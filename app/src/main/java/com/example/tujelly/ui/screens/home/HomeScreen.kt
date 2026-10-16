package com.example.tujelly.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.tujelly.data.local.BUTTON_STYLE_ICONS_ONLY
import com.example.tujelly.data.local.BUTTON_STYLE_TEXT_ONLY
import com.example.tujelly.domain.BillboardPoolBuilder
import com.example.tujelly.ui.components.BrandTileRow
import com.example.tujelly.ui.components.GenreRow
import com.example.tujelly.ui.components.HeroBanner
import com.example.tujelly.ui.components.MediaRow
import com.example.tujelly.ui.theme.TvAccent

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayMedia: (String) -> Unit,
    onDetailMedia: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenBrand: (String) -> Unit = {},
    onOpenGenre: (String) -> Unit = {},
    onOpenMedusa: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accentColorKey by viewModel.accentColor.collectAsStateWithLifecycle()
    val isMonochrome by viewModel.isMonochrome.collectAsStateWithLifecycle()
    val buttonStyleKey by viewModel.buttonStyle.collectAsStateWithLifecycle()
    val selectedPlatforms by viewModel.selectedPlatforms.collectAsStateWithLifecycle()
    val showTopIcons = buttonStyleKey != BUTTON_STYLE_TEXT_ONLY
    val showTopText = buttonStyleKey != BUTTON_STYLE_ICONS_ONLY

    val focusColor = TvAccent.getColor(accentColorKey)
    val focusContent = TvAccent.getFocusedContentColor(accentColorKey)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var backPressedOnce by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (listState.firstVisibleItemIndex > 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        } else if (!backPressedOnce) {
            backPressedOnce = true
            android.widget.Toast.makeText(context, "Pulsa Atrás de nuevo para salir", android.widget.Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                kotlinx.coroutines.delay(2000L)
                backPressedOnce = false
            }
        } else {
            (context as? android.app.Activity)?.finish()
        }
    }
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val localMediaCount by viewModel.localMediaCount.collectAsStateWithLifecycle()
    val currentSuccess = uiState as? HomeUiState.Success
    val daySeed = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR).toLong() }
    // Estado del backdrop del carrusel aislado en un holder: se lee dentro de
    // HomeGlobalBackdrop, de modo que su cambio NO recompone HomeScreen (ni la LazyColumn).
    val backdropState = remember { HomeBackdropState() }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshContinueWatching()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val featuredBillboardItems = remember(currentSuccess?.sections) {
        val sections = currentSuccess?.sections ?: emptyList()
        BillboardPoolBuilder.buildBillboardPool(sections, daySeed)
    }

    val defaultHeroBackdrop = remember(featuredBillboardItems) {
        featuredBillboardItems.firstOrNull()?.backdropUrl
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Global ambient backdrop crossfade (aislado en su propio composable)
        HomeGlobalBackdrop(
            focusedItemBackdrop = currentSuccess?.focusedItem?.backdropUrl,
            defaultBackdrop = defaultHeroBackdrop,
            backdropState = backdropState
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
        // Universal Persistent TV TopBar con gradiente protector superior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEE07070B),
                            Color(0xAA07070B),
                            Color(0x0007070B)
                        )
                    )
                )
        ) {
            com.example.tujelly.ui.components.TvTopBar(
                selectedTab = com.example.tujelly.ui.components.TvNavTab.HOME,
                onNavigateHome = { /* Ya en Inicio */ },
                onOpenMedusa = onOpenMedusa,
                onOpenFavorites = onOpenFavorites,
                onOpenSearch = onOpenSearch,
                onOpenSettings = onOpenSettings,
                accentColorKey = accentColorKey,
                buttonStyleKey = buttonStyleKey,
                isMonochrome = isMonochrome,
                syncProgress = syncProgress,
                localMediaCount = localMediaCount,
                modifier = Modifier.onFocusChanged { focusState ->
                    if (focusState.hasFocus) {
                        viewModel.setFocusedItem(null)
                    }
                }
            )
        }


        when (val state = uiState) {
            is HomeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.example.tujelly.ui.components.JellyLoadingIndicator(
                        size = 84.dp,
                        message = "Cargando descubridor de plataformas...",
                        isMonochrome = isMonochrome
                    )
                }
            }

            is HomeUiState.EmptySettings -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Bienvenido a Tujelly",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Configura tu servidor Jellyfin para disfrutar de tus películas, series y recomendaciones.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0x22FFFFFF),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color(0xFF0F172A)
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Configurar Servidor Jellyfin", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            is HomeUiState.EmptyLibrary -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Servidor conectado",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No se encontraron películas ni series en tu servidor Jellyfin.\nComprueba que tu usuario tenga acceso a las bibliotecas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Button(
                                onClick = { viewModel.loadHomeFeed() },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF0F172A),
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color(0xFF0F172A)
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Sincronizar", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }

                            Button(
                                onClick = onOpenSettings,
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0x18FFFFFF),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color(0xFF0F172A)
                                )
                            ) {
                                Text(text = "Ajustes", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            is HomeUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Error: ${state.message}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadHomeFeed() },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0x22FFFFFF),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color(0xFF0F172A)
                            )
                        ) {
                            Text(text = "Reintentar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            is HomeUiState.Success -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        @OptIn(ExperimentalFoundationApi::class)
                        val heroBringIntoViewResponder = remember(listState) {
                            object : BringIntoViewResponder {
                                override fun calculateRectForParent(localRect: Rect): Rect {
                                    // Retornar Rect.Zero indica a la LazyColumn que el banner ya está 100% visible
                                    // y evita que haga scroll hacia arriba cortando la cabecera al hacer foco en sus botones.
                                    return Rect.Zero
                                }

                                override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                                    listState.scrollToItem(0, 0)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewResponder(heroBringIntoViewResponder)
                                .onFocusChanged { focusState ->
                                    if (focusState.hasFocus) {
                                        viewModel.setFocusedItem(null)
                                        coroutineScope.launch {
                                            listState.scrollToItem(0, 0)
                                        }
                                    }
                                }
                        ) {
                            HeroBanner(
                                item = state.focusedItem,
                                featuredItems = featuredBillboardItems,
                                onPlayClick = { item -> onPlayMedia(item.id) },
                                onDetailClick = { item -> onDetailMedia(item.id) },
                                accentColor = accentColorKey,
                                buttonStyle = buttonStyleKey,
                                onActiveHeroChanged = { activeItem -> backdropState.carouselBackdrop = activeItem.backdropUrl }
                            )
                        }
                    }

                    item {
                        if (state.genres.isNotEmpty()) {
                            GenreRow(
                                genres = state.genres,
                                onSelectGenre = { genreName ->
                                    onOpenGenre(genreName)
                                },
                                modifier = Modifier.onFocusChanged { focusState ->
                                    if (focusState.hasFocus) {
                                        viewModel.setFocusedItem(null)
                                    }
                                }
                            )
                        }
                    }

                    item {
                        BrandTileRow(
                            onSelectBrand = { brandId ->
                                onOpenBrand(brandId)
                            },
                            visiblePlatforms = selectedPlatforms.takeIf { it.isNotEmpty() },
                            modifier = Modifier.onFocusChanged { focusState ->
                                if (focusState.hasFocus) {
                                    viewModel.setFocusedItem(null)
                                }
                            }
                        )
                    }

                    items(state.sections, key = { section -> section.title }) { section ->
                        MediaRow(
                            section = section,
                            onItemClick = { item ->
                                if (section.title == "Continuar Viendo") {
                                    onPlayMedia(item.id)
                                } else {
                                    onDetailMedia(item.id)
                                }
                            },
                            onItemFocus = { item -> viewModel.setFocusedItem(item) }
                        )
                    }
                }
            }
        }
    }
}
}

/**
 * Holder del backdrop activo del carrusel. Se separa del scope de [HomeScreen] para que
 * cambiar de hero (D-pad sobre el carrusel) no recomponga toda la pantalla ni la LazyColumn.
 */
private class HomeBackdropState {
    var carouselBackdrop by mutableStateOf<String?>(null)
}

@Composable
private fun HomeGlobalBackdrop(
    focusedItemBackdrop: String?,
    defaultBackdrop: String?,
    backdropState: HomeBackdropState
) {
    // La lectura del estado ocurre aquí dentro, así solo este composable se recompone.
    val focusedBackdrop = focusedItemBackdrop
        ?: backdropState.carouselBackdrop
        ?: defaultBackdrop

    Crossfade(
        targetState = focusedBackdrop,
        animationSpec = tween(durationMillis = 400),
        label = "homeGlobalBackdrop"
    ) { backdropUrl ->
        if (!backdropUrl.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.32f)
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
}


