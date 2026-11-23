package tv.medusa.app.ui.components

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import tv.medusa.app.data.remote.update.AppUpdateManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.medusa.app.ui.theme.DmSansFamily
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import tv.medusa.app.ui.util.handleTouchClick
import tv.medusa.app.R
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.data.repository.SyncProgress
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvSpacing

enum class TvNavTab {
    HOME,
    MEDUSA,
    FAVORITES,
    SEARCH,
    SETTINGS,
    NONE
}

/**
 * Cabecera de navegación universal para Android TV.
 * Mantiene la persistencia del logotipo de Medusa y los accesos rápidos
 * (Inicio, Medusa, Favoritos, Buscar, Ajustes) en todas las pantallas principales.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTopBar(
    selectedTab: TvNavTab,
    onNavigateHome: () -> Unit,
    onOpenMedusa: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    accentColorKey: String,
    buttonStyleKey: String,
    isMonochrome: Boolean,
    modifier: Modifier = Modifier,
    syncProgress: SyncProgress? = null,
    localMediaCount: Int = 0,
    homeFocusRequester: FocusRequester? = null,
    medusaFocusRequester: FocusRequester? = null,
    onDownFromMedusa: (() -> Unit)? = null,
    onDownNav: (() -> Unit)? = null
) {
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentColorKey)
    val showTopIcons = buttonStyleKey != BUTTON_STYLE_TEXT_ONLY
    val showTopText = buttonStyleKey != BUTTON_STYLE_ICONS_ONLY
    val autoUpdateInfo by AppUpdateManager.updateInfo.collectAsStateWithLifecycle()
    val hasAvailableUpdate = autoUpdateInfo?.hasUpdate == true

    val navDownModifier = if (onDownNav != null) {
        Modifier.onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                onDownNav()
                true
            } else false
        }
    } else Modifier

    Row(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = TvSpacing.ScreenSafeHorizontal),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sección izquierda: Logo Medusa
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val headerLogo = if (isMonochrome) {
                R.drawable.ic_medusa_header_mono
            } else {
                R.drawable.ic_medusa_header
            }
            Image(
                painter = painterResource(id = headerLogo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.height(52.dp)
            )
        }

        // Sección central: Estado de sincronización / alertas del sistema
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 16.dp)
        ) {
            if (syncProgress != null && syncProgress.isSyncing) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassRaised else focusColor.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    CircularProgressIndicator(
                        color = if (isMonochrome) Color.White else focusColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else if (syncProgress != null && syncProgress.isError && syncProgress.message.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .background(Color(0x30EF4444), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = syncProgress.message,
                        color = Color(0xFFFEE2E2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }

        // Sección derecha: Pestañas de navegación en Lens Carriage Dock
        var isDockFocused by remember { mutableStateOf(false) }
        val dockBgColor by animateColorAsState(
            targetValue = if (isDockFocused) Color(0x240A0F1D) else Color(0x100A0F1D),
            animationSpec = tween(300),
            label = "dockBg"
        )

        Box(
            modifier = Modifier
                .onFocusChanged { isDockFocused = it.hasFocus }
                .background(dockBgColor, RoundedCornerShape(22.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
            // 1. Botón Inicio
            val isHomeActive = selectedTab == TvNavTab.HOME
            Button(
                onClick = onNavigateHome,
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                modifier = Modifier
                    .then(if (homeFocusRequester != null) Modifier.focusRequester(homeFocusRequester) else Modifier)
                    .then(navDownModifier)
                    .handleTouchClick { onNavigateHome() },
                colors = topBarButtonColors(isHomeActive, isMonochrome, focusColor, focusContent, isDockFocused),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(18.dp)),
                border = topBarButtonBorder(isHomeActive, isMonochrome, focusColor, isDockFocused)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (showTopIcons) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = stringResource(R.string.nav_home),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (showTopText) {
                        if (showTopIcons) Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.nav_home),
                            fontWeight = if (isHomeActive) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontFamily = DmSansFamily,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 2. Botón Descubrir
            val isDiscoverActive = selectedTab == TvNavTab.MEDUSA
            var isDiscoverFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onOpenMedusa,
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                modifier = Modifier
                    .then(if (medusaFocusRequester != null) Modifier.focusRequester(medusaFocusRequester) else Modifier)
                    .then(navDownModifier)
                    .handleTouchClick { onOpenMedusa() }
                    .onFocusChanged { isDiscoverFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                            if (onDownFromMedusa != null) {
                                onDownFromMedusa()
                                true
                            } else false
                        } else false
                    },
                colors = topBarButtonColors(isDiscoverActive, isMonochrome, focusColor, focusContent, isDockFocused),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(18.dp)),
                border = topBarButtonBorder(isDiscoverActive, isMonochrome, focusColor, isDockFocused)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (showTopIcons) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = stringResource(R.string.nav_discover),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (showTopText) {
                        if (showTopIcons) Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.nav_discover),
                            fontWeight = if (isDiscoverActive) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontFamily = DmSansFamily,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 3. Botón Favoritos
            val isFavoritesActive = selectedTab == TvNavTab.FAVORITES
            Button(
                onClick = onOpenFavorites,
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                modifier = Modifier
                    .then(navDownModifier)
                    .handleTouchClick { onOpenFavorites() },
                colors = topBarButtonColors(isFavoritesActive, isMonochrome, focusColor, focusContent, isDockFocused),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(18.dp)),
                border = topBarButtonBorder(isFavoritesActive, isMonochrome, focusColor, isDockFocused)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (showTopIcons) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = stringResource(R.string.nav_favorites),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (showTopText) {
                        if (showTopIcons) Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.nav_favorites),
                            fontWeight = if (isFavoritesActive) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontFamily = DmSansFamily,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 4. Botón Buscar
            val isSearchActive = selectedTab == TvNavTab.SEARCH
            Button(
                onClick = onOpenSearch,
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                modifier = Modifier
                    .then(navDownModifier)
                    .handleTouchClick { onOpenSearch() },
                colors = topBarButtonColors(isSearchActive, isMonochrome, focusColor, focusContent, isDockFocused),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(18.dp)),
                border = topBarButtonBorder(isSearchActive, isMonochrome, focusColor, isDockFocused)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (showTopIcons) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.nav_search),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (showTopText) {
                        if (showTopIcons) Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.nav_search),
                            fontWeight = if (isSearchActive) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontFamily = DmSansFamily,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 5. Botón Ajustes
            val isSettingsActive = selectedTab == TvNavTab.SETTINGS
            Button(
                onClick = onOpenSettings,
                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                modifier = Modifier
                    .then(navDownModifier)
                    .handleTouchClick { onOpenSettings() },
                colors = topBarButtonColors(isSettingsActive, isMonochrome, focusColor, focusContent, isDockFocused),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(18.dp)),
                border = topBarButtonBorder(isSettingsActive, isMonochrome, focusColor, isDockFocused)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (showTopIcons) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.nav_settings),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (showTopText) {
                        if (showTopIcons) Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.nav_settings),
                            fontWeight = if (isSettingsActive) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontFamily = DmSansFamily,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (hasAvailableUpdate) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                        }
                    } else if (hasAvailableUpdate) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun topBarButtonColors(
    isActive: Boolean,
    isMonochrome: Boolean,
    focusColor: Color,
    focusContent: Color,
    isDockFocused: Boolean = true
) = ButtonDefaults.colors(
    containerColor = if (isActive) {
        if (isMonochrome) Color(0x30FFFFFF) else focusColor.copy(alpha = 0.20f)
    } else Color.Transparent,
    contentColor = if (isActive) {
        if (isMonochrome) Color.White else focusColor
    } else Color(0xFFA39E93),
    focusedContainerColor = focusColor,
    focusedContentColor = focusContent
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun topBarButtonBorder(
    isActive: Boolean,
    isMonochrome: Boolean,
    focusColor: Color,
    isDockFocused: Boolean = true
) = ButtonDefaults.border(
    border = Border.None,
    focusedBorder = Border.None
)
