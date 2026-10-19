package tv.medusa.app.ui.components

import android.view.SoundEffectConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.medusa.app.R
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvPill
import java.util.Locale

private data class ContextActionItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val iconTint: Color? = null,
    val action: () -> Unit
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaContextMenuDialog(
    item: MediaItem,
    accentColorKey: String,
    isMonochrome: Boolean,
    onPlayMedia: (String) -> Unit,
    onPlayFromBeginning: (MediaItem) -> Unit,
    onDetailMedia: (String) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onTogglePlayed: (MediaItem) -> Unit,
    onRemoveFromContinue: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentColorKey)
    val dialogOpenedTime = remember { android.os.SystemClock.uptimeMillis() }

    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(250)
        try {
            firstFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    BackHandler {
        onDismiss()
    }

    val stringResume = stringResource(R.string.context_menu_resume)
    val stringPlayFromStart = stringResource(R.string.context_menu_play_from_start)
    val stringPlay = stringResource(R.string.context_menu_play)
    val stringDetails = stringResource(R.string.context_menu_view_details)
    val stringGoToSeries = stringResource(R.string.context_menu_go_to_series)
    val stringMarkWatched = stringResource(R.string.context_menu_mark_watched)
    val stringMarkUnwatched = stringResource(R.string.context_menu_mark_unwatched)
    val stringAddFavorite = stringResource(R.string.context_menu_add_favorite)
    val stringRemoveFavorite = stringResource(R.string.context_menu_remove_favorite)
    val stringRemoveFromContinue = stringResource(R.string.context_menu_remove_from_continue)

    val actions = remember(item) {
        val list = mutableListOf<ContextActionItem>()
        val hasProgress = item.playbackPositionTicks > 0
        val isEpisode = item.type.equals("Episode", ignoreCase = true)

        if (hasProgress) {
            list.add(
                ContextActionItem(
                    id = "resume",
                    label = stringResume,
                    icon = Icons.Rounded.PlayArrow,
                    action = { onPlayMedia(item.id) }
                )
            )
            list.add(
                ContextActionItem(
                    id = "start_from_beginning",
                    label = stringPlayFromStart,
                    icon = Icons.Rounded.Replay,
                    action = { onPlayFromBeginning(item) }
                )
            )
        } else {
            list.add(
                ContextActionItem(
                    id = "play",
                    label = stringPlay,
                    icon = Icons.Rounded.PlayArrow,
                    action = { onPlayMedia(item.id) }
                )
            )
        }

        list.add(
            ContextActionItem(
                id = "details",
                label = stringDetails,
                icon = Icons.Rounded.Info,
                action = { onDetailMedia(item.id) }
            )
        )

        if (isEpisode && !item.seriesId.isNullOrBlank()) {
            list.add(
                ContextActionItem(
                    id = "go_to_series",
                    label = stringGoToSeries,
                    icon = Icons.Rounded.Tv,
                    action = { onDetailMedia(item.seriesId) }
                )
            )
        }

        if (item.isPlayed) {
            list.add(
                ContextActionItem(
                    id = "mark_unwatched",
                    label = stringMarkUnwatched,
                    icon = Icons.Rounded.VisibilityOff,
                    action = { onTogglePlayed(item) }
                )
            )
        } else {
            list.add(
                ContextActionItem(
                    id = "mark_watched",
                    label = stringMarkWatched,
                    icon = Icons.Rounded.Check,
                    action = { onTogglePlayed(item) }
                )
            )
        }

        if (item.isFavorite) {
            list.add(
                ContextActionItem(
                    id = "remove_favorite",
                    label = stringRemoveFavorite,
                    icon = Icons.Rounded.Favorite,
                    iconTint = if (!isMonochrome) Color(0xFFF43F5E) else null,
                    action = { onToggleFavorite(item) }
                )
            )
        } else {
            list.add(
                ContextActionItem(
                    id = "add_favorite",
                    label = stringAddFavorite,
                    icon = Icons.Rounded.FavoriteBorder,
                    action = { onToggleFavorite(item) }
                )
            )
        }

        if (hasProgress) {
            list.add(
                ContextActionItem(
                    id = "remove_continue",
                    label = stringRemoveFromContinue,
                    icon = Icons.Rounded.DeleteOutline,
                    iconTint = if (!isMonochrome) Color(0xFFF87171) else null,
                    action = { onRemoveFromContinue(item) }
                )
            )
        }

        list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC06080E))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(620.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF161922),
                                Color(0xFF0C0E14)
                            )
                        )
                    )
                    .border(
                        border = BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                colors = listOf(
                                    focusColor.copy(alpha = 0.45f),
                                    Color(0x18FFFFFF)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consumir clicks en la tarjeta */ }
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Columna Izquierda: Póster + Información
                    Column(
                        modifier = Modifier.width(160.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                                .background(Color(0xFF12141D)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!item.posterUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.posterUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = if (item.type.equals("Series", true)) Icons.Rounded.Tv else Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            if (item.playbackPositionTicks > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(Color(0x88000000))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.5f)
                                            .fillMaxHeight()
                                            .background(focusColor)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!item.seriesName.isNullOrBlank() && item.type.equals("Episode", true)) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.seriesName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (item.year != null && item.year > 0) {
                                TvPill(
                                    text = item.year.toString(),
                                    containerColor = Color(0x18FFFFFF),
                                    textColor = Color(0xFFCBD5E1),
                                    borderColor = Color(0x22FFFFFF)
                                )
                            }

                            if (item.rating != null && item.rating > 0f) {
                                TvPill(
                                    text = "★ ${String.format(Locale.US, "%.1f", item.rating)}",
                                    containerColor = if (isMonochrome) Color(0x20FFFFFF) else Color(0x26F59E0B),
                                    textColor = if (isMonochrome) Color.White else Color(0xFFFBBF24),
                                    borderColor = if (isMonochrome) Color(0x33FFFFFF) else Color(0x44F59E0B)
                                )
                            }
                        }
                    }

                    // Columna Derecha: Lista de Acciones Contextuales
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "OPCIONES",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )

                        actions.forEachIndexed { index, actionItem ->
                            val isFirst = index == 0
                            Button(
                                onClick = {
                                    if (android.os.SystemClock.uptimeMillis() - dialogOpenedTime < 450L) {
                                        // Ignora la pulsación residual provocada por soltar el botón D-pad tras abrir el menú
                                        return@Button
                                    }
                                    view.playSoundEffect(SoundEffectConstants.CLICK)
                                    actionItem.action()
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0x12FFFFFF),
                                    contentColor = Color.White,
                                    focusedContainerColor = focusColor,
                                    focusedContentColor = focusContent
                                ),
                                border = ButtonDefaults.border(
                                    border = Border(
                                        border = BorderStroke(1.dp, Color(0x1EFFFFFF)),
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                    focusedBorder = Border.None
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isFirst) Modifier.focusRequester(firstFocusRequester) else Modifier)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = actionItem.icon,
                                        contentDescription = null,
                                        tint = actionItem.iconTint ?: androidx.compose.ui.graphics.Color.Unspecified,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = actionItem.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
