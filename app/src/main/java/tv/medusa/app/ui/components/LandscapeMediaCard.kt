package tv.medusa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvRatingBadge
import tv.medusa.app.ui.util.handleTouchClick

/**
 * Tarjeta apaisada 16:9 estilo Netflix para Sagas, Colecciones (BoxSets) y Series destacadas.
 * Muestra el Backdrop panorámico de fondo y el ClearLogo (PNG transparente) flotando en la base,
 * con fallback elegante a tipografía cinematográfica.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LandscapeMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val accentKey = LocalAccentColor.current
    val accentColor = TvAccent.getColor(accentKey)
    val indicatorTheme = LocalIndicatorTheme.current
    val isMonochrome = LocalIsMonochromeTheme.current || indicatorTheme == INDICATOR_THEME_MONOCHROME
    val focusColor = if (isMonochrome) Color.White else accentColor

    val contextMenuController = tv.medusa.app.ui.components.contextmenu.LocalContextMenuController.current
    val view = androidx.compose.ui.platform.LocalView.current
    var lastLongClickTimestamp by remember { mutableStateOf(0L) }
    var isLongPressConsumed by remember { mutableStateOf(false) }

    val effectiveLongClick: (() -> Unit)? = onLongClick ?: contextMenuController?.let { controller ->
        {
            isLongPressConsumed = true
            lastLongClickTimestamp = android.os.SystemClock.uptimeMillis()
            view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
            controller.showContextMenu(item)
        }
    }

    val safeOnClick: () -> Unit = {
        val now = android.os.SystemClock.uptimeMillis()
        // Si ha habido una pulsación larga o está en curso, ignorar cualquier clic residual al soltar
        if (isLongPressConsumed || now - lastLongClickTimestamp < 1200L) {
            isLongPressConsumed = false
        } else {
            onClick()
        }
    }

    val imageModel = item.backdropUrl ?: item.posterUrl

    Card(
        onClick = safeOnClick,
        onLongClick = effectiveLongClick,
        colors = CardDefaults.colors(
            containerColor = Color(0xFF0C1017),
            focusedContainerColor = Color(0xFF141B26)
        ),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.08f
        ),
        modifier = modifier
            .handleTouchClick(onLongClick = effectiveLongClick, onClick = safeOnClick)
            .width(250.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, focusColor, RoundedCornerShape(10.dp))
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(if (isFocused) Color(0xFF181F2C) else Color(0xFF0C1017))
        ) {
            var isImageError by remember(item.id, imageModel) { mutableStateOf(false) }

            if (!isImageError && !imageModel.isNullOrBlank()) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { isImageError = true }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF232A3B), Color(0xFF0F1420))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = Color(0x66FFFFFF),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Gradiente inferior cinematográfico para asegurar legibilidad del logo y título
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x30000000),
                                Color(0xC0000000),
                                Color(0xF007090E)
                            ),
                            startY = 60f
                        )
                    )
            )

            // Insignia de Nota o Tipo en la esquina superior derecha
            val rating = item.rating
            if (rating != null && rating > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    TvRatingBadge(
                        rating = rating,
                        compact = true
                    )
                }
            }

            // Esquina inferior: ClearLogo flotante o Título tipográfico
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                var isLogoError by remember(item.id, item.logoUrl) { mutableStateOf(false) }

                if (!isLogoError && !item.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.logoUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier
                            .height(40.dp)
                            .fillMaxWidth(0.75f),
                        onError = { isLogoError = true }
                    )
                } else {
                    Column {
                        val cleanTitle = item.title
                            .replace(" - Colección", "", ignoreCase = true)
                            .replace(" Colección", "", ignoreCase = true)
                        Text(
                            text = cleanTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = buildList {
                            if (item.type.equals("BoxSet", ignoreCase = true)) add("COLECCIÓN")
                            item.year?.let { add(it.toString()) }
                        }.joinToString(" • ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = Color(0xAAFFFFFF),
                                fontWeight = FontWeight.Normal,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Barra de progreso si está a medias
            if (item.playbackPositionTicks > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x80000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f) // progreso estimado
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isFocused) Color.White else focusColor)
                    )
                }
            }
        }
    }
}

