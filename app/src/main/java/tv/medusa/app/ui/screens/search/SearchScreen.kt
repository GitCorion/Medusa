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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import tv.medusa.app.ui.theme.TvAccent
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
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentColorKey)

    val context = LocalContext.current
    val voicePrompt = stringResource(R.string.search_voice_prompt)
    val isVoiceSearchAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context) ||
            context.packageManager.queryIntentActivities(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                PackageManager.MATCH_DEFAULT_ONLY
            ).isNotEmpty()
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

    // Enfoque inicial automático en la primera tecla del teclado (A)
    val initialKeyFocusRequester = remember { FocusRequester() }

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
            .background(Color(0xFF06080D))
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
            isMonochrome = isMonochrome
        )

        // Contenedor principal de búsqueda dividida (Panel Izquierdo: Teclado | Panel Derecho: Resultados)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 6.dp)
        ) {
            // ==========================================================
            // PANEL IZQUIERDO: Display de Texto + Teclado Integrado Fijo
            // ==========================================================
            Column(
                modifier = Modifier.width(320.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra de visualización de lo que se escribe (Compacta, sin lupa para no confundir con un input)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(Color(0xFF0C1017), RoundedCornerShape(8.dp))
                        .then(
                            if (uiState.query.isNotEmpty()) {
                                Modifier.border(1.dp, focusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            } else Modifier
                        )
                        .padding(horizontal = 12.dp),
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
                                color = Color(0xFF64748B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = uiState.query,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Cursor parpadeante
                            Text(
                                text = "|",
                                color = (if (isMonochrome) Color.White else focusColor).copy(alpha = cursorAlpha),
                                fontSize = 16.sp,
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
                                    containerColor = Color(0x14FFFFFF),
                                    contentColor = if (isMonochrome) Color.White else focusColor,
                                    focusedContainerColor = focusColor,
                                    focusedContentColor = focusContent
                                ),
                                border = ButtonDefaults.border(
                                    border = Border.None,
                                    focusedBorder = Border.None
                                ),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = stringResource(R.string.search_voice_title),
                                    modifier = Modifier.size(14.dp)
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
                                    if (isMonochrome) Color(0x22FFFFFF) else focusColor.copy(alpha = 0.18f)
                                } else Color(0x0AFFFFFF),
                                contentColor = if (uiState.isPredictiveActive) {
                                    if (isMonochrome) Color.White else focusColor
                                } else Color(0xFF64748B),
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            ),
                            border = ButtonDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None
                            ),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = if (uiState.isPredictiveActive) "Auto" else "Libre",
                                fontSize = 10.sp,
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
                    initialFocusRequester = initialKeyFocusRequester
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
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.search_catalog_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Estado con consulta activa: Muestra Sugerencias superiores, contador y Cuadrícula
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Fila de sugerencias de autocompletado directa
                        if (uiState.suggestions.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                uiState.suggestions.take(4).forEach { suggestion ->
                                    Button(
                                        onClick = { viewModel.selectSuggestion(suggestion) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color(0xFF191D2C),
                                            contentColor = Color(0xFFE2E8F0),
                                            focusedContainerColor = focusColor,
                                            focusedContentColor = focusContent
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border.None
                                        ),
                                        modifier = Modifier.height(26.dp)
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

                        // Cabecera de resultados
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val counterText = when {
                                uiState.isLoading -> stringResource(R.string.search_counter_searching)
                                uiState.totalHits == 1 -> stringResource(R.string.search_counter_one)
                                uiState.totalHits > 1 -> stringResource(R.string.search_counter_many, uiState.totalHits)
                                else -> stringResource(R.string.search_counter_none)
                            }

                            Text(
                                text = counterText,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (uiState.totalHits > 0) Color.White else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (uiState.results.isEmpty() && !uiState.isLoading) {
                            // Sin resultados para el término
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
                                        color = Color(0xFFCBD5E1),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            // Cuadrícula TV de Resultados (Pósters interactivos)
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = uiState.results,
                                    key = { it.id },
                                    contentType = { "media_card" }
                                ) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { onDetailMedia(item.id) },
                                        onFocus = { viewModel.setFocusedItem(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
