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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
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
import tv.medusa.app.data.remote.jellyfin.CatalogoFormatUtils
import tv.medusa.app.data.remote.jellyfin.JellyfinMediaSourceDto
import tv.medusa.app.data.remote.jellyfin.TorrentOverlayUiState
import tv.medusa.app.ui.theme.TvPill
import tv.medusa.app.ui.util.handleTouchClick
import tv.medusa.app.ui.theme.TextPrimary

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
        // 1. Backdrop cinemático
        if (!state.backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = state.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.30f
            )
        }

        // 2. Viñeta oscura aterciopelada (Anti-slop background)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xD0060913),
                            Color(0xF505070E),
                            Color(0xFA030408)
                        )
                    )
                )
        )

        // 3. Tarjeta central elevada
        Box(
            modifier = Modifier
                .fillMaxWidth(0.58f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF20B101D))
                .border(
                    width = 1.dp,
                    color = if (state.isError) Color(0x55EF4444) else tv.medusa.app.ui.theme.TvPalette.GlassIdle,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 36.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icono de estado con aura sutil
                val iconVector = when {
                    state.isError -> Icons.Rounded.ErrorOutline
                    state.isUnavailable -> Icons.Rounded.WarningAmber
                    state.streamReady -> Icons.Rounded.PlayArrow
                    state.state.equals("PAUSED", ignoreCase = true) -> Icons.Rounded.Pause
                    else -> Icons.Rounded.CloudDownload
                }
                val iconTint = when {
                    state.isError -> Color(0xFFEF4444)
                    state.isUnavailable -> Color(0xFFF59E0B)
                    state.streamReady -> Color(0xFF4ADE80)
                    else -> focusColor
                }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(iconTint.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, iconTint.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Título
                if (state.title.isNotBlank()) {
                    Text(
                        text = state.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Fila de Badges cinemáticos (Calidad, Audio, Enjambre, Versión activa)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!state.quality.isNullOrBlank()) {
                        TvPill(
                            text = state.quality.uppercase(),
                            containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                            textColor = tv.medusa.app.ui.theme.TvPalette.TextSoft
                        )
                    }

                    if (!state.audio.isNullOrBlank()) {
                        TvPill(
                            text = state.audio.replaceFirstChar { it.uppercase() },
                            containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                            textColor = tv.medusa.app.ui.theme.TvPalette.TextSoft
                        )
                    }

                    // Badge de enjambre (Semillas / Pares)
                    if (state.seeds > 0 || state.peers > 0) {
                        TvPill(
                            text = stringResource(R.string.torrent_seeds_peers, state.seeds, state.peers),
                            containerColor = Color(0x1822C55E),
                            textColor = Color(0xFF4ADE80),
                            borderColor = Color(0x3322C55E)
                        )
                    } else if (!state.isError && !state.isUnavailable && !state.streamReady) {
                        TvPill(
                            text = stringResource(R.string.torrent_searching_seeds),
                            containerColor = Color(0x18EAB308),
                            textColor = Color(0xFFFBBF24),
                            borderColor = Color(0x33EAB308)
                        )
                    }

                    // Badge de versión activa si hay múltiples versiones disponibles
                    if (state.availableVersions.size > 1) {
                        val currentVer = state.availableVersions.firstOrNull { it.id == state.currentMediaSourceId }
                        val verName = currentVer?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.torrent_version_n, 1)
                        TvPill(
                            text = verName,
                            containerColor = focusColor.copy(alpha = 0.16f),
                            textColor = focusColor,
                            borderColor = focusColor.copy(alpha = 0.35f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Área de progreso de búfer
                if (!state.isError && !state.isUnavailable) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x10FFFFFF))
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        // Fila superior: Texto de estado y bytes contiguos / requeridos
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.statusText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )

                            val downloadedStr = CatalogoFormatUtils.formatBytes(state.downloadedBytes)
                            val targetStr = CatalogoFormatUtils.formatBytes(state.targetBufferBytes)
                            val pct = (animatedProgress * 100).toInt().coerceIn(0, 100)

                            Text(
                                text = "$downloadedStr / $targetStr ($pct%)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = focusColor
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Barra de progreso estilizada
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x20FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                focusColor,
                                                focusColor.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Fila inferior: velocidad y tiempo restante
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val speedText = if (state.speedBps > 0) {
                                CatalogoFormatUtils.formatSpeed(state.speedBps)
                            } else if (state.isZeroProgressFor10s) {
                                stringResource(R.string.torrent_connecting_swarm)
                            } else {
                                stringResource(R.string.torrent_starting_download)
                            }

                            Text(
                                text = speedText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = tv.medusa.app.ui.theme.TvPalette.TextMuted
                            )

                            val etaText = when {
                                state.streamReady -> "¡Listo! Iniciando…"
                                state.etaSeconds > 0 -> stringResource(R.string.torrent_eta_remaining, CatalogoFormatUtils.formatEta(state.etaSeconds))
                                state.seeds == 0 -> stringResource(R.string.torrent_waiting_peers)
                                else -> stringResource(R.string.torrent_buffering_initial)
                            }

                            Text(
                                text = etaText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (state.streamReady) Color(0xFF4ADE80) else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                            )
                        }
                    }
                }

                // Mensaje de error
                if (state.isError || state.isUnavailable) {
                    val errText = state.errorMessage ?: state.statusText.takeIf { it.isNotBlank() } ?: stringResource(R.string.torrent_prepare_failed)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFCA5A5),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Fila de acciones (Reintentar, Cambiar versión, Cancelar)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isError) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .handleTouchClick { onRetry() }
                                .focusRequester(primaryButtonFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = focusColor,
                                contentColor = focusContent,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            ),
                            scale = ButtonDefaults.scale(focusedScale = 1.05f),
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.torrent_retry),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Botón para elegir otra versión disponible si hay más de 1
                    if (state.availableVersions.size > 1) {
                        Button(
                            onClick = { showVersionSelector = true },
                            modifier = Modifier
                                .handleTouchClick { showVersionSelector = true }
                                .focusRequester(versionsButtonFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                                contentColor = TextPrimary,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            ),
                            scale = ButtonDefaults.scale(focusedScale = 1.05f),
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.torrent_versions_count, state.availableVersions.size),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Botón Cancelar
                    Button(
                        onClick = onCancel,
                        modifier = Modifier
                            .handleTouchClick { onCancel() }
                            .then(
                                if (state.availableVersions.size <= 1 && !state.isError) {
                                    Modifier.focusRequester(primaryButtonFocusRequester)
                                } else Modifier
                            ),
                        colors = ButtonDefaults.colors(
                            containerColor = tv.medusa.app.ui.theme.TvPalette.GlassIdle,
                            contentColor = TextPrimary,
                            focusedContainerColor = if (state.availableVersions.size <= 1) focusColor else Color(0x40FFFFFF),
                            focusedContentColor = if (state.availableVersions.size <= 1) focusContent else TextPrimary
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.05f),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.torrent_cancel),
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
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
                        text = stringResource(R.string.torrent_options_count, versions.size),
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
