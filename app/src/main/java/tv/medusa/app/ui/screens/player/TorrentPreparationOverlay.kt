package tv.medusa.app.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.medusa.app.R
import tv.medusa.app.data.remote.jellyfin.CatalogoFormatUtils
import tv.medusa.app.data.remote.jellyfin.TorrentOverlayUiState
import tv.medusa.app.ui.util.handleTouchClick

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun TorrentPreparationOverlay(
    state: TorrentOverlayUiState,
    focusColor: Color,
    focusContent: Color,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isError, state.isUnavailable) {
        delay(150)
        try {
            primaryButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        label = "torrentProgress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Fondo con backdrop sutil si está disponible
        if (!state.backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = state.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(0.dp)),
                alpha = 0.22f
            )
        }

        // Gradiente oscuro de fondo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xDD090B14),
                            Color(0xF5090B14)
                        )
                    )
                )
        )

        // Tarjeta central de progreso
        Box(
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF0121726))
                .border(
                    width = 0.5.dp,
                    color = if (state.isError) Color(0x60EF4444) else Color(0x28FFFFFF),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 32.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icono de estado
                val iconVector = when {
                    state.isError -> Icons.Rounded.ErrorOutline
                    state.isUnavailable -> Icons.Rounded.WarningAmber
                    state.state.equals("PAUSED", ignoreCase = true) -> Icons.Rounded.Pause
                    else -> Icons.Rounded.CloudDownload
                }
                val iconTint = when {
                    state.isError -> Color(0xFFEF4444)
                    state.isUnavailable -> Color(0xFFF59E0B)
                    else -> focusColor
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(iconTint.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, iconTint.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Título del contenido
                if (state.title.isNotBlank()) {
                    Text(
                        text = state.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Subtítulo con explicación clara de por qué espera
                if (!state.isError && !state.isUnavailable && state.reasonText.isNotBlank()) {
                    Text(
                        text = state.reasonText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                } else if (!state.isError && !state.isUnavailable) {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Barra de carga estilo videojuego con datos cuantitativos de búfer
                if (!state.isError && !state.isUnavailable) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x18FFFFFF))
                            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Fila superior: estado y Megas/porcentaje de búfer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.statusText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
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
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x35FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                focusColor,
                                                focusColor.copy(alpha = 0.82f)
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Fila inferior: velocidad y tiempo restante estimado
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val speedText = if (state.speedBps > 0) {
                                "⚡ ${CatalogoFormatUtils.formatSpeed(state.speedBps)}"
                            } else if (state.isZeroProgressFor10s) {
                                "Conectando…"
                            } else {
                                "Preparando…"
                            }

                            Text(
                                text = speedText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFFCBD5E1)
                            )

                            val etaText = when {
                                state.streamReady -> "¡Listo! Iniciando…"
                                state.etaSeconds > 0 -> "Faltan ~${CatalogoFormatUtils.formatEta(state.etaSeconds)}"
                                state.seeds == 0 -> "Buscando semillas…"
                                else -> "Iniciando en breve…"
                            }

                            Text(
                                text = etaText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (state.streamReady) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Chips o detalles (Calidad, Audio, Seeds/Peers)
                if (state.detailText.isNotBlank() && !state.isError) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x18FFFFFF))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = state.detailText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Mensaje de error si aplica
                if (state.isError || state.isUnavailable) {
                    val errText = state.errorMessage ?: state.statusText
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFCA5A5),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botones de acción
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

                        Button(
                            onClick = onCancel,
                            modifier = Modifier.handleTouchClick { onCancel() },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0x1EFFFFFF),
                                contentColor = Color.White,
                                focusedContainerColor = Color(0x3EFFFFFF),
                                focusedContentColor = Color.White
                            ),
                            scale = ButtonDefaults.scale(focusedScale = 1.05f),
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = stringResource(R.string.torrent_cancel),
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onCancel,
                            modifier = Modifier
                                .handleTouchClick { onCancel() }
                                .focusRequester(primaryButtonFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0x1EFFFFFF),
                                contentColor = Color.White,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            ),
                            scale = ButtonDefaults.scale(focusedScale = 1.05f),
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = stringResource(R.string.torrent_cancel),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
