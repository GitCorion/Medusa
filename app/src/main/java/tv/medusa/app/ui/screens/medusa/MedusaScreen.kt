package tv.medusa.app.ui.screens.medusa

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.medusa.app.R
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.components.FormatFilterBar
import tv.medusa.app.ui.components.MediaCard
import tv.medusa.app.ui.components.TvNavTab
import tv.medusa.app.ui.components.TvTopBar
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvRatingBadge
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MedusaScreen(
    onNavigateHome: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayMedia: (String) -> Unit,
    onDetailMedia: (String) -> Unit,
    viewModel: MedusaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val buttonStyleKey by viewModel.buttonStyle.collectAsStateWithLifecycle()

    // Respetar estrictamente la temática adoptada en Ajustes (LocalIsMonochromeTheme y LocalAccentColor)
    val isMonochromeByVm by viewModel.isMonochrome.collectAsStateWithLifecycle()
    val isMonochrome = LocalIsMonochromeTheme.current || isMonochromeByVm
    val accentColorKey = LocalAccentColor.current
    val accentColor = remember(accentColorKey, isMonochrome) {
        if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    }

    val formatNounPlural = when (uiState.format) {
        MediaFormat.ALL -> stringResource(R.string.medusa_noun_titles)
        MediaFormat.MOVIES -> stringResource(R.string.medusa_noun_movies)
        MediaFormat.SERIES -> stringResource(R.string.medusa_noun_series)
    }

    val topBarMedusaFocusRequester = remember { FocusRequester() }
    val tagRailFocusRequester = remember { FocusRequester() }

    var isFocusedOnMoviesRow by remember { mutableStateOf(false) }
    var isFocusedOnTagRail by remember { mutableStateOf(false) }
    var isFocusedOnActiveChain by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        topBarMedusaFocusRequester.requestFocus()
    }

    // Manejo de la tecla ATRÁS (Back) para mando de TV
    BackHandler {
        if (isFocusedOnMoviesRow) {
            isFocusedOnMoviesRow = false
            isFocusedOnTagRail = true
            try {
                tagRailFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else if (isFocusedOnActiveChain) {
            isFocusedOnActiveChain = false
            isFocusedOnTagRail = true
            try {
                tagRailFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else if (!viewModel.onBackPress()) {
            onNavigateHome()
        }
    }

    // Candidatas que no están ya seleccionadas
    val candidateTags = remember(uiState.mosaicTags) {
        uiState.mosaicTags.filterNot { it.isSelected }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
    ) {
        // Fondo: Solo proyectar backdrop si el usuario ha seleccionado alguna temática.
        // La selección se lee en su propio composable para no recomponer la pantalla al
        // mover el foco entre tarjetas.
        if (uiState.matchingMovies.isNotEmpty()) {
            MedusaBackdrop(
                selectedMovieFlow = viewModel.selectedMovie,
                fallbackBackdropUrl = uiState.matchingMovies.firstOrNull()?.backdropUrl
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isMonochrome) Color(0x10FFFFFF) else accentColor.copy(alpha = 0.08f),
                            Color(0xEE05070B),
                            Color(0xFF05070B)
                        ),
                        radius = 1200f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // =========================================================================
            // 1. CABECERA UNIVERSAL DE NAVEGACIÓN (TvTopBar)
            // =========================================================================
            TvTopBar(
                selectedTab = TvNavTab.MEDUSA,
                onNavigateHome = onNavigateHome,
                onOpenMedusa = { /* Ya en Medusa */ },
                onOpenFavorites = onOpenFavorites,
                onOpenSearch = onOpenSearch,
                onOpenSettings = onOpenSettings,
                accentColorKey = accentColorKey,
                buttonStyleKey = buttonStyleKey,
                isMonochrome = isMonochrome,
                localMediaCount = uiState.totalCatalogCount,
                medusaFocusRequester = topBarMedusaFocusRequester,
                modifier = Modifier
                    .onFocusChanged {
                        if (it.hasFocus) {
                            isFocusedOnTagRail = false
                            isFocusedOnMoviesRow = false
                        }
                    }
                    .focusProperties {
                        onEnter = {
                            if (isFocusedOnTagRail && requestedFocusDirection != FocusDirection.Up) {
                                tagRailFocusRequester
                            } else {
                                FocusRequester.Default
                            }
                        }
                    }
            )

            // =========================================================================
            // 2. FILTRO DE FORMATO (TODOS / PELÍCULAS / SERIES)
            // =========================================================================
            FormatFilterBar(
                selected = when (uiState.format) {
                    MediaFormat.ALL -> MediaFormatFilter.ALL
                    MediaFormat.MOVIES -> MediaFormatFilter.MOVIES
                    MediaFormat.SERIES -> MediaFormatFilter.SERIES
                },
                onSelect = { filter ->
                    viewModel.setFormat(
                        when (filter) {
                            MediaFormatFilter.ALL -> MediaFormat.ALL
                            MediaFormatFilter.MOVIES -> MediaFormat.MOVIES
                            MediaFormatFilter.SERIES -> MediaFormat.SERIES
                        }
                    )
                },
                accentColorKey = accentColorKey,
                buttonStyleKey = buttonStyleKey,
                isMonochrome = isMonochrome,
                hideWatched = uiState.hideWatched,
                onToggleHideWatched = { viewModel.toggleHideWatched() },
                modifier = Modifier
                    .onFocusChanged {
                        if (it.hasFocus) {
                            isFocusedOnTagRail = false
                            isFocusedOnMoviesRow = false
                        }
                    }
                    .focusProperties {
                        onEnter = {
                            if (isFocusedOnTagRail && requestedFocusDirection != FocusDirection.Up) {
                                tagRailFocusRequester
                            } else {
                                FocusRequester.Default
                            }
                        }
                    }
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))

                // =========================================================================
                // 3. ETIQUETAS ACTIVAS SELECCIONADAS (Navegables con mando TV para borrar)
                // =========================================================================
                if (uiState.activeChain.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        uiState.activeChain.forEach { tag ->
                            SelectedTagBadge(
                                tag = tag,
                                onRemove = {
                                    viewModel.removeTagFromChain(tag)
                                    try {
                                        tagRailFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                },
                                isMonochrome = isMonochrome,
                                accentColor = accentColor,
                                onFocusChange = { focused ->
                                    if (focused) {
                                        isFocusedOnActiveChain = true
                                        isFocusedOnTagRail = false
                                        isFocusedOnMoviesRow = false
                                    } else {
                                        isFocusedOnActiveChain = false
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // =========================================================================
                // 4. RAIL HORIZONTAL DE TEMÁTICAS
                // =========================================================================
                MedusaTagRail(
                    tags = candidateTags,
                    onTagClick = { tag ->
                        viewModel.toggleTag(tag)
                    },
                    firstPillFocusRequester = tagRailFocusRequester,
                    isMonochrome = isMonochrome,
                    accentColor = accentColor,
                    onTagFocused = { tag ->
                        isFocusedOnTagRail = true
                        isFocusedOnMoviesRow = false
                        viewModel.onTagFocused(tag)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // =========================================================================
                // 5. SECCIÓN PROTAGONISTA: PELÍCULAS DESCUBIERTAS
                // =========================================================================
                if (uiState.matchingMovies.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MedusaMoviesHeader(
                                movieCount = if (uiState.activeChain.isNotEmpty()) uiState.totalMatchingCount else uiState.matchingMovies.size,
                                formatNounPlural = formatNounPlural,
                                selectedMovieFlow = viewModel.selectedMovie,
                                fallbackMovie = uiState.matchingMovies.firstOrNull(),
                                accentColor = accentColor,
                                isMonochrome = isMonochrome
                            )

                            Text(
                                text = if (isFocusedOnMoviesRow) stringResource(R.string.medusa_hint_movies_short) else stringResource(R.string.medusa_hint_nav_movies_short),
                                color = Color(0x7794A3B8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        isFocusedOnMoviesRow = true
                                    }
                                },
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(
                                items = uiState.matchingMovies,
                                key = { _, movie -> movie.id },
                                contentType = { _, _ -> "media_card" }
                            ) { _, movie ->
                                MediaCard(
                                    item = movie,
                                    onClick = {
                                        onDetailMedia(movie.id)
                                    },
                                    onFocus = {
                                        isFocusedOnTagRail = false
                                        isFocusedOnMoviesRow = true
                                        viewModel.selectMovie(movie)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // =========================================================================
                // 6. HUD INFERIOR SOBRIO
                // =========================================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF030508))
                        .padding(horizontal = 48.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hudText = when {
                        isFocusedOnMoviesRow -> stringResource(R.string.medusa_hint_movies)
                        isFocusedOnActiveChain -> stringResource(R.string.medusa_hint_active_chain)
                        uiState.activeChain.isNotEmpty() -> stringResource(R.string.medusa_hint_chain)
                        else -> stringResource(R.string.medusa_hint_default)
                    }

                    Text(
                        text = hudText,
                        color = if (uiState.activeChain.isNotEmpty()) {
                            if (isMonochrome) Color.White else accentColor
                        } else Color(0x8094A3B8),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp
                    )

                    if (uiState.matchingMovies.isNotEmpty()) {
                        Text(
                            text = if (uiState.activeChain.isNotEmpty()) {
                                stringResource(R.string.medusa_available_count, uiState.totalMatchingCount, formatNounPlural)
                            } else {
                                stringResource(R.string.medusa_available_count, uiState.matchingMovies.size, formatNounPlural)
                            },
                            color = if (isMonochrome) Color.White else accentColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backdrop de Medusa. Recibe la selección como flujo y la colecciona aquí para que mover el
 * foco entre tarjetas recomponga sólo este nodo, no toda la pantalla.
 */
@Composable
private fun MedusaBackdrop(
    selectedMovieFlow: StateFlow<MediaItem?>,
    fallbackBackdropUrl: String?
) {
    val selectedMovie by selectedMovieFlow.collectAsStateWithLifecycle()
    val backdropUrl = selectedMovie?.backdropUrl ?: fallbackBackdropUrl
    if (!backdropUrl.isNullOrBlank()) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.18f)
        )
    }
}

/**
 * Cabecera con el recuento de resultados y los datos de la película enfocada. Aísla la lectura
 * de `selectedMovie` para no recomponer raíl ni LazyRow en cada movimiento del D-pad.
 */
@Composable
private fun MedusaMoviesHeader(
    movieCount: Int,
    formatNounPlural: String,
    selectedMovieFlow: StateFlow<MediaItem?>,
    fallbackMovie: MediaItem?,
    accentColor: Color,
    isMonochrome: Boolean
) {
    val selectedMovie by selectedMovieFlow.collectAsStateWithLifecycle()
    val focusedMovie = selectedMovie ?: fallbackMovie

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$movieCount ${formatNounPlural.uppercase()}",
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.SansSerif
        )

        if (focusedMovie != null) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = focusedMovie.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (focusedMovie.year != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(${focusedMovie.year})",
                    color = Color(0x8094A3B8),
                    fontSize = 11.sp
                )
            }
            if (focusedMovie.rating != null && focusedMovie.rating > 0f) {
                Spacer(modifier = Modifier.width(8.dp))
                TvRatingBadge(rating = focusedMovie.rating, compact = true)
            }
        }
    }
}

/**
 * Píldora de temática seleccionada en la cadena activa.
 * Enfocable con mando TV, resalte bioluminiscente nítido y tecla [OK] para eliminar.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SelectedTagBadge(
    tag: MedusaMosaicTag,
    onRemove: () -> Unit,
    isMonochrome: Boolean,
    accentColor: Color,
    onFocusChange: (Boolean) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "badgeScale"
    )

    val containerColor = if (isFocused) {
        Color.White
    } else {
        Color(0x28FFFFFF)
    }

    val contentColor = if (isFocused) {
        Color(0xFF0F172A)
    } else {
        Color.White
    }

    val borderColor = if (isFocused) {
        Color.Transparent
    } else {
        Color(0x33FFFFFF)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .then(
                if (!isFocused) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(8.dp))
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onRemove
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = tag.label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.common_delete),
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
