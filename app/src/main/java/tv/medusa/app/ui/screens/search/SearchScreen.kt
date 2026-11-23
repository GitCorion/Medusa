package tv.medusa.app.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import tv.medusa.app.R
import tv.medusa.app.ui.components.MediaCard
import tv.medusa.app.ui.components.TvNavTab
import tv.medusa.app.ui.components.TvTopBar
import tv.medusa.app.ui.components.sort.CatalogSortButton
import tv.medusa.app.ui.components.sort.CatalogSortOption
import tv.medusa.app.ui.theme.CinematicTypography
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.adaptiveKeyboardColumnWidth
import tv.medusa.app.ui.theme.TvPill
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPlayMedia: (String) -> Unit,
    onDetailMedia: (String) -> Unit,
    onNavigateHome: () -> Unit = {},
    onOpenMedusa: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accentColorKey by viewModel.accentColor.collectAsStateWithLifecycle()
    val buttonStyleKey by viewModel.buttonStyle.collectAsStateWithLifecycle()
    val isMonochrome by viewModel.isMonochrome.collectAsStateWithLifecycle()
    val focusColor = if (isMonochrome) TextPrimary else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentColorKey)

    val context = LocalContext.current
    val voicePrompt = stringResource(R.string.search_voice_prompt)
    val isVoiceSearchAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context) ||
            context.packageManager.queryIntentActivities(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                PackageManager.MATCH_DEFAULT_ONLY
            ).isNotEmpty()
    }

    var sortOption by rememberSaveable { mutableStateOf(CatalogSortOption.DEFAULT) }
    val sortedResults = remember(uiState.results, sortOption) {
        sortOption.sort(uiState.results)
    }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sortOption) {
        try { gridState.scrollToItem(0) } catch (_: Exception) {}
    }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotBlank()) {
            try { gridState.scrollToItem(0) } catch (_: Exception) {}
        }
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onQueryChange(spoken.trim())
            }
        }
    }

    // Enfoque inicial automático en la primera tecla del teclado (A) y requester para el primer resultado
    val initialKeyFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    // ARRIBA desde el teclado o los controles del campo vuelve a la pestaña Buscar.
    val topBarSearchFocusRequester = remember { FocusRequester() }
    val sortButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(180)
        try {
            initialKeyFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // El botón Atrás del mando limpia la búsqueda actual si tiene texto; si ya está limpia, vuelve a Inicio
    BackHandler(enabled = true) {
        if (uiState.query.isNotEmpty()) {
            viewModel.onClear()
        } else {
            onBack()
        }
    }

    // Cursor animado parpadeante para la barra de búsqueda
    val infiniteTransition = rememberInfiniteTransition(label = "SearchCursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tv.medusa.app.ui.theme.AbyssVoid)
    ) {
        // Universal Persistent TV TopBar
        TvTopBar(
            selectedTab = TvNavTab.SEARCH,
            onNavigateHome = onNavigateHome,
            onOpenMedusa = onOpenMedusa,
            onOpenFavorites = onOpenFavorites,
            onOpenSearch = { /* Ya en Buscar */ },
            onOpenSettings = onOpenSettings,
            accentColorKey = accentColorKey,
            buttonStyleKey = buttonStyleKey,
            isMonochrome = isMonochrome,
            searchFocusRequester = topBarSearchFocusRequester
        )

        // Contenedor principal de búsqueda dividida (Panel Izquierdo: Teclado | Panel Derecho: Resultados)
        val searchGutter = if (tv.medusa.app.ui.theme.LocalAppWindowSize.current ==
            tv.medusa.app.ui.theme.AppWindowSize.COMPACT
        ) 12.dp else 48.dp
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = searchGutter, vertical = 6.dp)
        ) {
            // ==========================================================
            // PANEL IZQUIERDO: Display de Texto + Teclado Integrado Fijo
            // ==========================================================
            Column(
                modifier = Modifier.adaptiveKeyboardColumnWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra de visualización de lo que se escribe (Compacta, sin lupa para no confundir con un input)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(tv.medusa.app.ui.theme.TvPalette.SurfaceChip, RoundedCornerShape(10.dp))
                        .then(
                            if (uiState.query.isNotEmpty()) {
                                Modifier.border(1.dp, focusColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            } else Modifier
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                color = tv.medusa.app.ui.theme.TvPalette.Placeholder,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = uiState.query,
                                color = tv.medusa.app.ui.theme.TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Cursor parpadeante
                            Text(
                                text = "|",
                                color = (if (isMonochrome) TextPrimary else focusColor).copy(alpha = cursorAlpha),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isVoiceSearchAvailable) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                                        }
                                        voiceSearchLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Log.w("SearchScreen", "Voice recognition activity not found", e)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                colors = ButtonDefaults.colors(
                                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                                    contentColor = if (isMonochrome) TextPrimary else focusColor,
                                    focusedContainerColor = focusColor,
                                    focusedContentColor = focusContent
                                ),
                                border = ButtonDefaults.border(
                                    border = Border.None,
                                    focusedBorder = Border.None
                                ),
                                modifier = Modifier
                                    .height(44.dp)
                                    .focusProperties {
                                        right = if (sortedResults.isNotEmpty()) sortButtonFocusRequester else FocusRequester.Default
                                        up = topBarSearchFocusRequester
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = stringResource(R.string.search_voice_title),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // Selector de Modo Predictivo sobrio y limpio
                        Button(
                            onClick = { viewModel.togglePredictive() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = if (uiState.isPredictiveActive) {
                                    if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassRaised else focusColor.copy(alpha = 0.18f)
                                } else Color(0x0AFFFFFF),
                                contentColor = if (uiState.isPredictiveActive) {
                                    if (isMonochrome) TextPrimary else focusColor
                                } else tv.medusa.app.ui.theme.TvPalette.Placeholder,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            ),
                            border = ButtonDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .focusProperties { up = topBarSearchFocusRequester }
                        ) {
                            Text(
                                text = if (uiState.isPredictiveActive) {
                                    stringResource(R.string.search_keyboard_auto)
                                } else {
                                    stringResource(R.string.search_keyboard_free)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Teclado integrado en pantalla D-pad (Posición 100% fija, sin saltos verticales)
                TvIntegratedKeyboard(
                    validNextChars = uiState.validNextChars,
                    isPredictiveActive = uiState.isPredictiveActive,
                    query = uiState.query,
                    focusColor = focusColor,
                    focusContentColor = focusContent,
                    onKeyClick = { viewModel.appendChar(it) },
                    onBackspace = { viewModel.onBackspace() },
                    onClear = { viewModel.onClear() },
                    initialFocusRequester = initialKeyFocusRequester,
                    upRequester = topBarSearchFocusRequester
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            // ==========================================
            // PANEL DERECHO: Resultados / Sugerencias
            // ==========================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusProperties {
                        onEnter = {
                            if (sortedResults.isNotEmpty()) {
                                if (gridState.firstVisibleItemIndex > 0) {
                                    coroutineScope.launch {
                                        gridState.scrollToItem(0)
                                    }
                                }
                                firstResultFocusRequester
                            } else {
                                FocusRequester.Default
                            }
                        }
                    }
            ) {
                if (uiState.query.isBlank()) {
                    // Estado inicial limpio y minimalista (sin duplicar novedades del Home)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0x12FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = TextPrimary.copy(alpha = 0.85f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.search_catalog_title),
                                style = CinematicTypography.SectionTitle,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.search_empty_hint),
                                fontSize = 14.sp,
                                color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (sortedResults.isEmpty() && !uiState.isLoading) {
                    // Sin resultados para el término
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (uiState.suggestions.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                uiState.suggestions.take(4).forEachIndexed { sIndex, suggestion ->
                                    Button(
                                        onClick = { viewModel.selectSuggestion(suggestion) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color(0xFF191D2C),
                                            contentColor = tv.medusa.app.ui.theme.TvPalette.TextSoft,
                                            focusedContainerColor = focusColor,
                                            focusedContentColor = focusContent
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border.None
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .focusProperties {
                                                if (sIndex == 0) {
                                                    left = initialKeyFocusRequester
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = suggestion,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.search_no_results, uiState.query),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tv.medusa.app.ui.theme.TvPalette.TextMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    // Cuadrícula TV de Resultados (Pósters interactivos) con cabecera integrada en la propia grid
                    val counterText = when {
                        uiState.isLoading -> stringResource(R.string.search_counter_searching)
                        uiState.totalHits == 1 -> stringResource(R.string.search_counter_one)
                        uiState.totalHits > 1 -> stringResource(R.string.search_counter_many, uiState.totalHits)
                        else -> stringResource(R.string.search_counter_none)
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 48.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "search_header", span = { GridItemSpan(maxLineSpan) }) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (uiState.suggestions.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        uiState.suggestions.take(4).forEachIndexed { sIndex, suggestion ->
                                            Button(
                                                onClick = { viewModel.selectSuggestion(suggestion) },
                                                contentPadding = tv.medusa.app.ui.theme.TvChipDefaults.ContentPadding,
                                                scale = tv.medusa.app.ui.theme.TvChipDefaults.buttonScale(),
                                                shape = ButtonDefaults.shape(tv.medusa.app.ui.theme.TvChipDefaults.Shape),
                                                colors = tv.medusa.app.ui.theme.TvChipDefaults.buttonColors(
                                                    isSelected = false,
                                                    focusColor = focusColor,
                                                    focusContent = focusContent
                                                ),
                                                border = tv.medusa.app.ui.theme.TvChipDefaults.buttonBorder(),
                                                modifier = Modifier
                                                    .height(tv.medusa.app.ui.theme.TvChipDefaults.Height)
                                                    .focusProperties {
                                                        if (sIndex == 0) {
                                                            left = initialKeyFocusRequester
                                                        }
                                                    }
                                            ) {
                                                Text(
                                                    text = suggestion,
                                                    color = tv.medusa.app.ui.theme.TvPalette.TextSoft,
                                                    style = tv.medusa.app.ui.theme.TvChipDefaults.textStyle,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = counterText,
                                        style = CinematicTypography.SectionTitle.copy(fontSize = 17.sp),
                                        color = if (uiState.totalHits > 0) TextPrimary else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (sortedResults.isNotEmpty()) {
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
                                            buttonStyleKey = buttonStyleKey,
                                            focusRequester = sortButtonFocusRequester,
                                            modifier = Modifier
                                                .focusRequester(sortButtonFocusRequester)
                                                .focusProperties {
                                                    down = firstResultFocusRequester
                                                    left = if (uiState.suggestions.isNotEmpty()) FocusRequester.Default else initialKeyFocusRequester
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            items = sortedResults,
                            key = { _, item -> item.id },
                            contentType = { _, _ -> "media_card" }
                        ) { index, item ->
                            MediaCard(
                                item = item,
                                fillWidth = true,
                                onClick = {
                                    val targetId = if (item.type.equals("Episode", ignoreCase = true) && !item.seriesId.isNullOrBlank()) {
                                        item.seriesId!!
                                    } else {
                                        item.id
                                    }
                                    onDetailMedia(targetId)
                                },
                                onFocus = { viewModel.setFocusedItem(item) },
                                modifier = if (index == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}
