package tv.medusa.app.ui.screens.seasonal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import tv.medusa.app.util.SeasonalAudioManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.medusa.app.R
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.medusa.app.ui.components.FormatFilterBar
import tv.medusa.app.ui.components.JellyLoadingIndicator
import tv.medusa.app.ui.components.MediaCard
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
    val seasonalAudioEnabled by viewModel.seasonalAudioEnabled.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    LaunchedEffect(eventId) {
        viewModel.loadFeed(eventId)
    }

    when (val state = uiState) {
        is SeasonalUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090A0F)),
                contentAlignment = Alignment.Center
            ) {
                JellyLoadingIndicator(
                    size = 72.dp,
                    message = "Cargando selección especial...",
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        is SeasonalUiState.Success -> {
            val event = state.event
            val eventAccent = Color(android.graphics.Color.parseColor(event.accentHex))


            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090A0F))
            ) {
                // 1. Imagen de fondo cinematográfica con degradado suave
                Image(
                    painter = painterResource(id = event.backdropResId),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )

                // Degradados para fundir la imagen con el fondo oscuro y garantizar legibilidad
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x99090A0F),
                                    Color(0xFF090A0F)
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xDD090A0F),
                                    Color(0x88090A0F),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 3. Contenido principal
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

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 145.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header con información de la colección
                        item(key = "seasonal_header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 8.dp)
                            ) {
                                val cinematicFont = remember {
                                    FontFamily(Font(R.font.cinzel_decorative_bold, FontWeight.Bold))
                                }
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
                                        else -> Brush.verticalGradient(
                                            listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0))
                                        )
                                    }
                                }

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
                                Text(
                                    text = "${state.items.size} títulos en catálogo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMonochrome) Color(0xFF94A3B8) else eventAccent.copy(alpha = 0.9f)
                                )
                            }
                        }

                        // Filtro de formato (Todas, Películas, Series)
                        item(key = "seasonal_format_filter_bar", span = { GridItemSpan(maxLineSpan) }, contentType = "filter_bar") {
                            FormatFilterBar(
                                selected = state.format,
                                onSelect = { viewModel.setFormat(it) },
                                accentColorKey = accentColorKey,
                                buttonStyleKey = buttonStyleKey,
                                isMonochrome = isMonochrome,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        // Muro de posters (Grid)
                        items(
                            items = state.items,
                            key = { it.id },
                            contentType = { "media_card" }
                        ) { item ->
                            MediaCard(
                                item = item,
                                onClick = { onDetailMedia(item.id) },
                                onFocus = { viewModel.setFocusedItem(item) }
                            )
                        }

                        if (state.items.isEmpty()) {
                            item(key = "seasonal_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_placeholder") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp, vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay títulos de esta festividad en el formato seleccionado.",
                                        color = Color(0x99FFFFFF),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Capa ambiental estacional por encima de toda la pantalla
                SeasonalParticles(
                    particleType = event.particleType,
                    modifier = Modifier.fillMaxSize(),
                    particleCount = 30
                )
            }
        }
    }
}
