package tv.medusa.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
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
 * Tarjeta panorámica imponente (320x180 dp) para 'Sagas y Colecciones'.
 *
 * Filosofía Anti-Slop:
 * - Efecto 'Stack': pestañas sutiles escalonadas en la parte superior que evocan una caja/estuche de colección con múltiples discos.
 * - Dimensiones cinematográficas amplias (320.dp x 180.dp).
 * - ClearLogo vectorial o tipografía con amplio margen inferior (padding(16.dp)) para no rozar el borde cian.
 * - Zoom y zIndex aplicados en el contenedor raíz antes de cualquier clip para evitar recortes.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LandscapeMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isRowFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val accentKey = LocalAccentColor.current
    val accentColor = TvAccent.getColor(accentKey)
    val indicatorTheme = LocalIndicatorTheme.current
    val isMonochrome = LocalIsMonochromeTheme.current ||
            indicatorTheme == INDICATOR_THEME_MONOCHROME
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
        if (isLongPressConsumed || now - lastLongClickTimestamp < 1200L) {
            isLongPressConsumed = false
        } else {
            onClick()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "sagaCardScale"
    )

    val spotlightAlpha by animateFloatAsState(
        targetValue = if (isRowFocused && !isFocused) 0.65f else 1.0f,
        animationSpec = tween(durationMillis = 220),
        label = "sagaSpotlightAlpha"
    )

    val imageModel = item.backdropUrl ?: item.posterUrl

    Card(
        onClick = safeOnClick,
        onLongClick = effectiveLongClick,
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
        border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
        shape = CardDefaults.shape(shape = RoundedCornerShape(0.dp)),
        modifier = modifier
            .width(320.dp)
            .height(192.dp)
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = spotlightAlpha
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .handleTouchClick(onLongClick = effectiveLongClick, onClick = safeOnClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Efecto Stack: Pestaña 2 (más al fondo)
            Box(
                modifier = Modifier
                    .width(284.dp)
                    .height(12.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 0.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(if (isFocused) focusColor.copy(alpha = 0.20f) else Color(0x18FFFFFF))
            )

            // Efecto Stack: Pestaña 1 (intermedia)
            Box(
                modifier = Modifier
                    .width(304.dp)
                    .height(12.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 5.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(if (isFocused) focusColor.copy(alpha = 0.35f) else Color(0x28FFFFFF))
            )

            // Tarjeta Frontal Principal (320 x 180 dp)
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .height(180.dp)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isFocused) Color(0xFF141B26) else Color(0xFF0C1017))
                    .then(
                        if (isFocused) {
                            Modifier.border(2.dp, focusColor, RoundedCornerShape(12.dp))
                        } else Modifier
                    )
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

                // Gradiente inferior cinematográfico profundo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x25000000),
                                    Color(0x9506080D),
                                    Color(0xF506080D)
                                ),
                                startY = 45f
                            )
                        )
                )

                // Insignia de Nota o Tipo en la esquina superior derecha (padding 10.dp holgado)
                val rating = item.rating
                if (rating != null && rating > 0f) {
                    TvRatingBadge(
                        rating = rating,
                        compact = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .zIndex(2f)
                    )
                }

                // Esquina inferior: ClearLogo flotante o Título con padding(16.dp) holgado
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    var isLogoError by remember(item.id, item.logoUrl) { mutableStateOf(false) }

                    if (!isLogoError && !item.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.logoUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            modifier = Modifier
                                .height(44.dp)
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
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            val subtitle = buildList {
                                add("COLECCIÓN")
                                item.year?.let { add(it.toString()) }
                            }.joinToString(" • ")

                            Text(
                                text = subtitle,
                                color = if (isFocused) focusColor else Color(0xCC94A3B8),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
