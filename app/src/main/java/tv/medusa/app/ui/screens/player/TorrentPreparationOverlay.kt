package tv.medusa.app.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.medusa.app.R
import tv.medusa.app.data.remote.jellyfin.TodFormatUtils
import tv.medusa.app.data.remote.jellyfin.JellyfinMediaSourceDto
import tv.medusa.app.data.remote.jellyfin.TorrentOverlayUiState
import tv.medusa.app.ui.theme.TvPill
import tv.medusa.app.ui.util.handleTouchClick
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary

/**
 * Pantalla de búfer y preparación de torrent con diseño Anti-Slop cinemático:
 * - Backdrop inmersivo con viñeta oscura aterciopelada
 * - Superficie de tarjeta elevada sin bordes gruesos
 * - Medidores precisos de búfer contiguo, velocidad y ETA
 * - Selector integrado de versiones / releases si hay más de una fuente disponible
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TorrentPreparationOverlay(
    state: TorrentOverlayUiState,
    focusColor: Color,
    focusContent: Color,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onSelectVersion: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showVersionSelector by remember { mutableStateOf(false) }
    val primaryButtonFocusRequester = remember { FocusRequester() }
    val versionsButtonFocusRequester = remember { FocusRequester() }

    // Manejo de tecla Atrás: si el selector de versión está abierto, cerrarlo; si no, cancelar
    BackHandler(enabled = true) {
        if (showVersionSelector) {
            showVersionSelector = false
        } else {
            onCancel()
        }
    }

    LaunchedEffect(state.isError, state.isUnavailable) {
        delay(120)
        try {
            primaryButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "torrentBufferProgress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF04060A)),
        contentAlignment = Alignment.Center
    ) {
        // 1. Fondo y scrim, sin tarjeta contenedora
        if (!state.backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = state.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.18f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF204060A),
                            Color(0xFA04060A),
                            Color(0xFF04060A)
                        )
                    )
                )
        )

        // 2. Contenido directo sobre el fondo: sin caja, sin iconos decorativos
        Column(
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.title.isNotBlank()) {
                Text(
                    text = state.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            val metaParts = buildList {
                if (!state.quality.isNullOrBlank()) add(state.quality.uppercase())
                if (!state.audio.isNullOrBlank()) add(state.audio.replaceFirstChar { it.uppercase() })
                if (state.seeds > 0 || state.peers > 0) {
                    add(stringResource(R.string.torrent_seeds_peers, state.seeds, state.peers))
                }
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString("  ·  "),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            if (!state.isError && !state.isUnavailable) {
                val downloadedStr = TodFormatUtils.formatBytes(state.downloadedBytes)
                val targetStr = TodFormatUtils.formatBytes(state.targetBufferBytes)
                val pct = (animatedProgress * 100).toInt().coerceIn(0, 100)
                val speedText = if (state.speedBps > 0) {
                    TodFormatUtils.formatSpeed(state.speedBps)
                } else if (state.isZeroProgressFor10s) {
                    stringResource(R.string.torrent_connecting_swarm)
                } else {
                    stringResource(R.string.torrent_starting_download)
                }

                Text(
                    text = state.statusText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$downloadedStr / $targetStr",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "$pct %",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = focusColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0x24FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .background(focusColor)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = speedText,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            if (state.isError || state.isUnavailable) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.errorMessage ?: state.statusText.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.torrent_prepare_failed),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (state.isUnavailable) Color(0xFFF59E0B) else Color(0xFFFCA5A5),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isError) {
                    TorrentTextButton(
                        text = stringResource(R.string.torrent_retry),
                        onClick = onRetry,
                        focusColor = focusColor,
                        focusContent = focusContent,
                        focusRequester = primaryButtonFocusRequester
                    )
                }
                if (state.availableVersions.size > 1) {
                    TorrentTextButton(
                        text = stringResource(R.string.torrent_versions_count, state.availableVersions.size),
                        onClick = { showVersionSelector = true },
                        focusColor = focusColor,
                        focusContent = focusContent,
                        focusRequester = versionsButtonFocusRequester
                    )
                }
                TorrentTextButton(
                    text = stringResource(R.string.torrent_cancel),
                    onClick = onCancel,
                    focusColor = focusColor,
                    focusContent = focusContent,
                    focusRequester = if (state.availableVersions.size <= 1 && !state.isError) {
                        primaryButtonFocusRequester
                    } else {
                        null
                    }
                )
            }
        }

        // 4. Modal / Diálogo de selección de versión disponible
        AnimatedVisibility(
            visible = showVersionSelector,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.96f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.96f)
        ) {
            VersionSelectorModal(
                versions = state.availableVersions,
                currentMediaSourceId = state.currentMediaSourceId,
                focusColor = focusColor,
                focusContent = focusContent,
                onSelectVersion = { versionId ->
                    showVersionSelector = false
                    onSelectVersion(versionId)
                },
                onClose = { showVersionSelector = false }
            )
        }
    }
}

/**
 * Acción sobria: sin contenedor en reposo; el foco pinta la cápsula de acento.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TorrentTextButton(
    text: String,
    onClick: () -> Unit,
    focusColor: Color,
    focusContent: Color,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .handleTouchClick { onClick() }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TextSecondary,
            focusedContainerColor = focusColor,
            focusedContentColor = focusContent
        ),
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
        border = ButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Modal centrado para elegir entre las versiones/releases disponibles del contenido.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VersionSelectorModal(
    versions: List<JellyfinMediaSourceDto>,
    currentMediaSourceId: String?,
    focusColor: Color,
    focusContent: Color,
    onSelectVersion: (String) -> Unit,
    onClose: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable { onClose() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Back || keyEvent.key == Key.Escape)) {
                    onClose()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF6090D18))
                .border(1.dp, focusColor.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cabecera del modal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = focusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.torrent_choose_version),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = pluralStringResource(R.plurals.torrent_options_count, versions.size, versions.size),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                    )
                }

                // Lista de versiones
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = versions,
                        key = { idx, v -> "ver_${v.id}_$idx" }
                    ) { index, version ->
                        val isSelected = version.id == currentMediaSourceId ||
                                (currentMediaSourceId.isNullOrBlank() && index == 0)
                        val label = version.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.torrent_version_n, index + 1)
                        val container = version.container?.uppercase() ?: ""

                        Surface(
                            onClick = { onSelectVersion(version.id) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) focusColor.copy(alpha = 0.18f) else Color(0x12FFFFFF),
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) focusColor.copy(alpha = 0.45f) else tv.medusa.app.ui.theme.TvPalette.GlassSubtle
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                                focusedBorder = Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, focusColor),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            ),
                            modifier = Modifier
                                .handleTouchClick { onSelectVersion(version.id) }
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isSelected) focusColor else Color.Transparent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (container.isNotBlank()) {
                                    TvPill(
                                        text = container,
                                        containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                                        textColor = tv.medusa.app.ui.theme.TvPalette.TextMuted
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
