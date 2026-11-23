package tv.medusa.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

import java.util.Locale
import tv.medusa.app.BuildConfig
import tv.medusa.app.data.remote.ApiConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import tv.medusa.app.ui.util.handleTouchClick
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.medusa.app.R
import tv.medusa.app.data.local.APP_THEME_MONOCHROME
import tv.medusa.app.data.local.ACCENT_AMBER
import tv.medusa.app.data.local.ACCENT_CYAN
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_AND_TEXT
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_OFF
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_AUTO
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_DIRECT
import tv.medusa.app.data.local.PERFORMANCE_MODE_AUTO
import tv.medusa.app.data.local.PERFORMANCE_MODE_HIGH
import tv.medusa.app.data.local.PERFORMANCE_MODE_LOW
import tv.medusa.app.data.local.SUBTITLE_SIZE_SMALL
import tv.medusa.app.data.local.SUBTITLE_SIZE_NORMAL
import tv.medusa.app.data.local.SUBTITLE_SIZE_LARGE
import tv.medusa.app.data.model.SUPPORTED_PLATFORMS
import tv.medusa.app.data.model.StreamPlatform
import tv.medusa.app.data.remote.trakt.TraktUserListDto
import tv.medusa.app.ui.components.QrCodeImage
import tv.medusa.app.ui.components.TvNavTab
import tv.medusa.app.ui.components.TvTopBar
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.adaptiveDialogWidth
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvColors
import kotlinx.coroutines.delay
import tv.medusa.app.ui.theme.TextPrimary

// =============================================================================
// PANTALLA DE AJUSTES: DISEÑO LIMPIO Y ELEGANTE TIPO APPLE TV / PLEX / NETFLIX
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onOpenMedusa: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusColor = if (uiState.isMonochrome) TextPrimary else TvAccent.getColor(uiState.accentColor)
    val focusContent = if (uiState.isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(uiState.accentColor)
    // ARRIBA desde el rail vuelve a la pestaña Ajustes, no a Inicio.
    val topBarSettingsFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            delay(1500)
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TvTopBar(
                selectedTab = TvNavTab.SETTINGS,
                onNavigateHome = onNavigateHome,
                onOpenMedusa = onOpenMedusa,
                onOpenFavorites = onOpenFavorites,
                onOpenSearch = onOpenSearch,
                onOpenSettings = { /* Ya en Ajustes */ },
                accentColorKey = uiState.accentColor,
                buttonStyleKey = uiState.buttonStyle,
                isMonochrome = uiState.isMonochrome,
                settingsFocusRequester = topBarSettingsFocusRequester
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 48.dp, bottom = 24.dp)
            ) {
                // =============================================================
                // BARRA LATERAL DE CATEGORÍAS (8 APARTADOS)
                // =============================================================
                Column(
                    modifier = Modifier
                        .width(236.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x06FFFFFF))
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TvColors.TextTertiary,
                        letterSpacing = 0.2.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.Dns,
                        title = stringResource(R.string.settings_nav_jellyfin),
                        isSelected = uiState.activeCategory == 0,
                        onClick = { viewModel.selectCategory(0) },
                        focusColor = focusColor,
                        focusContent = focusContent,
                        upRequester = topBarSettingsFocusRequester
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.Public,
                        title = stringResource(R.string.settings_nav_platforms),
                        isSelected = uiState.activeCategory == 1,
                        onClick = { viewModel.selectCategory(1) },
                        focusColor = focusColor,
                        focusContent = focusContent,
                        upRequester = topBarSettingsFocusRequester
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.SyncAlt,
                        title = stringResource(R.string.settings_nav_library),
                        isSelected = uiState.activeCategory == 2,
                        onClick = { viewModel.selectCategory(2) },
                        focusColor = focusColor,
                        focusContent = focusContent,
                        upRequester = topBarSettingsFocusRequester
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.PlayCircle,
                        title = stringResource(R.string.settings_nav_playback),
                        isSelected = uiState.activeCategory == 3,
                        onClick = { viewModel.selectCategory(3) },
                        focusColor = focusColor,
                        focusContent = focusContent,
                        upRequester = topBarSettingsFocusRequester
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        title = stringResource(R.string.settings_nav_audio),
                        isSelected = uiState.activeCategory == 4,
                        onClick = { viewModel.selectCategory(4) },
                        focusColor = focusColor,
                        focusContent = focusContent,
                        upRequester = topBarSettingsFocusRequester
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.Subtitles,
                        title = stringResource(R.string.settings_nav_subtitles),
                        isSelected = uiState.activeCategory == 5,
                        onClick = { viewModel.selectCategory(5) },
                        focusColor = focusColor,
                        focusContent = focusContent,
                        upRequester = topBarSettingsFocusRequester
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.settings_nav_appearance),
                        isSelected = uiState.activeCategory == 6,
                        onClick = { viewModel.selectCategory(6) },
                        focusColor = focusColor,
                        focusContent = focusContent,
                        upRequester = topBarSettingsFocusRequester
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.settings_nav_about),
                        isSelected = uiState.activeCategory == 7,
                        onClick = { viewModel.selectCategory(7) },
                        focusColor = focusColor,
                        focusContent = focusContent,
                        upRequester = topBarSettingsFocusRequester
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // =============================================================
                // PANEL PRINCIPAL DE CONFIGURACIÓN
                // =============================================================
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val sectionTitle = when (uiState.activeCategory) {
                        0 -> stringResource(R.string.settings_nav_jellyfin)
                        1 -> stringResource(R.string.settings_nav_platforms)
                        2 -> stringResource(R.string.settings_nav_library)
                        3 -> stringResource(R.string.settings_nav_playback)
                        4 -> stringResource(R.string.settings_nav_audio)
                        5 -> stringResource(R.string.settings_nav_subtitles)
                        6 -> stringResource(R.string.settings_nav_appearance)
                        else -> stringResource(R.string.settings_nav_about)
                    }

                    Text(
                        text = sectionTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (uiState.activeCategory) {
                            0 -> JellyfinCategory(uiState, viewModel, focusColor, focusContent)
                            1 -> PlatformsCategory(uiState, viewModel, focusColor, focusContent)
                            2 -> LibraryCategory(uiState, viewModel, focusColor, focusContent)
                            3 -> PlaybackCategory(uiState, viewModel, focusColor, focusContent)
                            4 -> AudioCategory(uiState, viewModel, focusColor, focusContent)
                            5 -> SubtitlesCategory(uiState, viewModel, focusColor, focusContent)
                            6 -> AppearanceCategory(uiState, viewModel, focusColor, focusContent)
                            else -> AboutCategory(
                                uiState = uiState,
                                viewModel = viewModel,
                                focusColor = focusColor,
                                focusContent = focusContent,
                                onOpenPrivacyPolicy = { showPrivacyPolicyDialog = true }
                            )
                        }

                        if (uiState.statusMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tv.medusa.app.ui.theme.TvPalette.GlassSubtle, RoundedCornerShape(10.dp))
                                    .border(0.5.dp, tv.medusa.app.ui.theme.TvPalette.GlassRaised, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = focusColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = uiState.statusMessage!!,
                                        color = tv.medusa.app.ui.theme.TvPalette.TextSoft,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        if (uiState.activeDialogField != null) {
            TvEditDialog(
                field = uiState.activeDialogField!!,
                initialValue = uiState.activeDialogValue,
                onValueChange = { viewModel.onDialogValueChange(it) },
                onCommit = { viewModel.commitDialogValue() },
                onDismiss = { viewModel.dismissDialog() },
                focusColor = focusColor,
                focusContent = focusContent
            )
        }

        if (showPrivacyPolicyDialog) {
            TvPrivacyPolicyDialog(
                onDismiss = { showPrivacyPolicyDialog = false },
                focusColor = focusColor,
                focusContent = focusContent
            )
        }

        if (uiState.jellyfinQrPayload != null) {
            TvQuickConnectDialog(
                title = stringResource(R.string.settings_login_quick_connect),
                step1Text = stringResource(R.string.settings_qc_step1),
                code = uiState.jellyfinQuickConnectCode ?: "...",
                qrData = uiState.jellyfinQrPayload!!,
                onDismiss = { viewModel.cancelJellyfinQuickConnect() },
                focusColor = focusColor,
                focusContent = focusContent
            )
        }

        if (uiState.traktQrPayload != null) {
            TvQuickConnectDialog(
                title = stringResource(R.string.pro_feature_trakt_title),
                step1Text = stringResource(R.string.settings_trakt_step1),
                code = uiState.traktUserCode ?: "...",
                qrData = uiState.traktQrPayload!!,
                onDismiss = { viewModel.cancelTraktQuickConnect() },
                focusColor = focusColor,
                focusContent = focusContent
            )
        }
    }
}

// =============================================================================
// CATEGORÍA 0: JELLYFIN
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun JellyfinCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
    if (uiState.isJellyfinConnected) {
        TvSettingsGroupCard(title = stringResource(R.string.settings_section_server_session)) {
            TvSettingRow(
                title = stringResource(R.string.settings_row_server),
                value = uiState.serverUrl,
                onClick = { viewModel.openEditDialog(SettingField.SERVER_URL) }
            )

            TvSettingRow(
                title = stringResource(R.string.settings_row_user),
                value = uiState.username
            )

            TvSettingRow(
                title = stringResource(R.string.settings_row_status),
                value = stringResource(R.string.settings_status_connected)
            )

            val totalTitles = uiState.syncedMoviesCount + uiState.syncedSeriesCount
            TvSettingRow(
                title = stringResource(R.string.settings_row_library_synced),
                value = if (totalTitles > 0) {
                    stringResource(
                        R.string.settings_library_count,
                        formatNumber(totalTitles),
                        formatNumber(uiState.syncedMoviesCount),
                        formatNumber(uiState.syncedSeriesCount)
                    )
                } else {
                    stringResource(R.string.settings_library_empty)
                }
            )

            if (!uiState.lastSyncTimestamp.isNullOrBlank()) {
                val formatted = uiState.lastSyncTimestamp?.replace("T", " ")?.substringBefore(".") ?: ""
                TvSettingRow(
                    title = stringResource(R.string.settings_row_last_sync),
                    value = formatted
                )
            }
        }

        TvSettingsGroupCard(title = stringResource(R.string.settings_section_actions)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
            Button(
                onClick = { viewModel.forceSyncCatalog() },
                modifier = Modifier.handleTouchClick(enabled = !uiState.isSyncingCatalog) { viewModel.forceSyncCatalog() },
                colors = ButtonDefaults.colors(
                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                    contentColor = TextPrimary,
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                ),
                enabled = !uiState.isSyncingCatalog
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isSyncingCatalog) {
                        CircularProgressIndicator(
                            color = TextPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_btn_syncing), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_btn_sync_library), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Button(
                onClick = { viewModel.testServerConnection() },
                modifier = Modifier.handleTouchClick { viewModel.testServerConnection() },
                colors = ButtonDefaults.colors(
                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                    contentColor = TextPrimary,
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_btn_check_connection), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = { viewModel.disconnectJellyfin() },
                modifier = Modifier.handleTouchClick { viewModel.disconnectJellyfin() },
                colors = ButtonDefaults.colors(
                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                    contentColor = Color(0xFFEF4444),
                    focusedContainerColor = Color(0xFFEF4444),
                    focusedContentColor = TextPrimary
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_btn_logout), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    TlsCertificateSection(uiState, viewModel, focusColor, focusContent)
    } else {
        // Desconectado
        TvSectionHeader(title = stringResource(R.string.settings_section_server_address))

        TvSettingRow(
            title = stringResource(R.string.settings_row_server),
            value = uiState.serverUrl.ifBlank { stringResource(R.string.settings_placeholder_enter_url) },
            onClick = { viewModel.openEditDialog(SettingField.SERVER_URL) }
        )

        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = { viewModel.discoverJellyfinServers() },
            modifier = Modifier.handleTouchClick(enabled = !uiState.isDiscoveringServers) {
                viewModel.discoverJellyfinServers()
            },
            colors = ButtonDefaults.colors(
                containerColor = tv.medusa.app.ui.theme.TvPalette.GlassIdle,
                contentColor = TextPrimary,
                focusedContainerColor = focusColor,
                focusedContentColor = focusContent
            ),
            enabled = !uiState.isDiscoveringServers
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isDiscoveringServers) {
                    CircularProgressIndicator(
                        color = TextPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (uiState.isDiscoveringServers) R.string.settings_btn_searching_servers
                        else R.string.settings_btn_discover_servers
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (uiState.discoveredServers.isNotEmpty()) {
            TvSettingsGroupCard(title = stringResource(R.string.settings_section_servers_found)) {
                uiState.discoveredServers.forEach { server ->
                    TvSettingRow(
                        title = server.name,
                        value = server.address,
                        onClick = { viewModel.selectDiscoveredServer(server) }
                    )
                }
            }
        }

        if (uiState.serverUrl.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { viewModel.testServerConnection() },
                modifier = Modifier.handleTouchClick { viewModel.testServerConnection() },
                colors = ButtonDefaults.colors(
                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                    contentColor = TextPrimary,
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_btn_check_server), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            TlsCertificateSection(uiState, viewModel, focusColor, focusContent)

            var authMethod by remember { mutableIntStateOf(0) }

            TvSectionHeader(title = stringResource(R.string.settings_section_login_method))

            TvSegmentedSelector(
                options = listOf(
                    0 to stringResource(R.string.settings_login_quick_connect),
                    1 to stringResource(R.string.settings_login_user_pass)
                ),
                selectedOption = authMethod,
                onOptionSelected = { authMethod = it },
                focusColor = focusColor,
                focusContent = focusContent
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (authMethod == 0) {
                // Quick Connect
                Button(
                    onClick = { viewModel.startJellyfinQuickConnect() },
                    modifier = Modifier.handleTouchClick(enabled = !uiState.isLoading) { viewModel.startJellyfinQuickConnect() },
                    colors = ButtonDefaults.colors(
                        containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                        contentColor = TextPrimary,
                        focusedContainerColor = focusColor,
                        focusedContentColor = focusContent
                    ),
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = if (uiState.isWaitingJellyfinQuickConnect) stringResource(R.string.settings_btn_waiting_confirmation) else stringResource(R.string.settings_btn_generate_qr),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // Manual
                TvSettingRow(
                    title = stringResource(R.string.settings_row_user),
                    value = uiState.username.ifBlank { stringResource(R.string.settings_placeholder_enter_user) },
                    onClick = { viewModel.openEditDialog(SettingField.USERNAME) }
                )

                TvSettingRow(
                    title = stringResource(R.string.settings_row_password),
                    value = if (uiState.password.isNotBlank()) "••••••••" else stringResource(R.string.settings_placeholder_enter_password),
                    onClick = { viewModel.openEditDialog(SettingField.PASSWORD) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.saveAndConnect() },
                    modifier = Modifier.handleTouchClick(enabled = !uiState.isLoading) { viewModel.saveAndConnect() },
                    colors = ButtonDefaults.colors(
                        containerColor = focusColor,
                        contentColor = focusContent,
                        focusedContainerColor = focusColor,
                        focusedContentColor = focusContent
                    ),
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = if (uiState.isLoading) stringResource(R.string.settings_btn_connecting) else stringResource(R.string.settings_btn_login),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Sección TLS: solo aparece para servidores HTTPS. El pin de certificado es un opt-in
 * explícito por servidor y se compara con el fingerprint SHA-256 mostrado al usuario.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TlsCertificateSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
    if (!uiState.serverUrl.startsWith("https://", ignoreCase = true)) return

    TvSectionHeader(title = stringResource(R.string.settings_section_tls))

    TvSettingRow(
        title = stringResource(R.string.settings_row_tls_self_signed),
        value = if (uiState.jellyfinAllowSelfSigned && uiState.jellyfinCertificateFingerprint.isNotBlank()) {
            stringResource(R.string.settings_tls_pinned)
        } else {
            stringResource(R.string.settings_tls_not_pinned)
        }
    )

    if (uiState.jellyfinCertificateFingerprint.isNotBlank()) {
        Text(
            text = uiState.jellyfinCertificateFingerprint,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { viewModel.pinSelfSignedCertificate() },
            modifier = Modifier.handleTouchClick(enabled = !uiState.isFetchingCertificateFingerprint) {
                viewModel.pinSelfSignedCertificate()
            },
            colors = ButtonDefaults.colors(
                containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                contentColor = TextPrimary,
                focusedContainerColor = focusColor,
                focusedContentColor = focusContent
            ),
            enabled = !uiState.isFetchingCertificateFingerprint
        ) {
            Text(
                text = when {
                    uiState.isFetchingCertificateFingerprint -> stringResource(R.string.settings_tls_fetching)
                    uiState.jellyfinAllowSelfSigned -> stringResource(R.string.settings_btn_pin_certificate_again)
                    else -> stringResource(R.string.settings_btn_pin_certificate)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (uiState.jellyfinAllowSelfSigned) {
            Button(
                onClick = { viewModel.clearSelfSignedCertificate() },
                modifier = Modifier.handleTouchClick { viewModel.clearSelfSignedCertificate() },
                colors = ButtonDefaults.colors(
                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                    contentColor = Color(0xFFFF8A80),
                    focusedContainerColor = Color(0xFFEF4444),
                    focusedContentColor = TextPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.settings_btn_remove_pin),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Text(
        text = stringResource(R.string.settings_tls_pin_hint),
        fontSize = 11.sp,
        color = tv.medusa.app.ui.theme.TvPalette.Placeholder,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

// =============================================================================
// CATEGORÍA 5: BIBLIOTECA Y TRAKT
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
    TvSettingsGroupCard(title = stringResource(R.string.settings_section_library_view)) {
        TvSettingSwitchRow(
            title = stringResource(R.string.settings_library_hide_watched_title),
            description = stringResource(R.string.settings_library_hide_watched_desc),
            checked = uiState.hideWatched,
            onCheckedChange = { viewModel.updateHideWatched(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }

    TvSettingsGroupCard(title = stringResource(R.string.settings_section_library_database)) {
        val syncStatusText = if (uiState.isSyncingCatalog) {
            "Sincronizando..."
        } else if (!uiState.lastSyncTimestamp.isNullOrBlank()) {
            uiState.lastSyncTimestamp?.replace("T", " ")?.substringBefore(".") ?: "Completada"
        } else {
            "Completada"
        }

        TvSettingActionRow(
            title = stringResource(R.string.settings_library_db_stats_title),
            description = stringResource(
                R.string.settings_library_db_stats_desc,
                uiState.syncedMoviesCount,
                uiState.syncedSeriesCount,
                uiState.syncedEpisodesCount
            ),
            value = syncStatusText,
            icon = Icons.Rounded.Sync,
            onClick = { viewModel.forceSyncCatalog() },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }

    if (uiState.isTraktConnected) {
        TvSectionHeader(title = stringResource(R.string.settings_section_account_linked))

        TvSettingRow(
            title = stringResource(R.string.settings_row_trakt_user),
            value = "@${uiState.traktUsername ?: stringResource(R.string.settings_status_active)}"
        )

        TvSettingRow(
            title = stringResource(R.string.settings_row_status),
            value = stringResource(R.string.settings_status_linked)
        )

        TvSectionHeader(title = stringResource(R.string.settings_section_lists_collections))

        Text(
            text = stringResource(R.string.settings_trakt_lists_desc),
            fontSize = 12.sp,
            color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
            lineHeight = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (uiState.isLoadingTraktLists) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = focusColor,
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.settings_loading_trakt_lists),
                    fontSize = 12.sp,
                    color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                )
            }
        } else if (uiState.traktUserLists.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_trakt_no_lists),
                fontSize = 12.sp,
                color = tv.medusa.app.ui.theme.TvPalette.Placeholder,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                uiState.traktUserLists.forEach { traktList ->
                    val isVisible = !traktList.isUserHidden(uiState.hiddenTraktLists)
                    TvTraktListToggleRow(
                        list = traktList,
                        isVisible = isVisible,
                        onToggle = { viewModel.toggleTraktListVisibility(traktList) },
                        focusColor = focusColor,
                        focusContent = focusContent
                    )
                }
            }
        }

        TvSectionHeader(title = stringResource(R.string.settings_section_actions))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Button(
                onClick = {
                    viewModel.validateTraktToken()
                    viewModel.loadTraktUserLists()
                },
                colors = ButtonDefaults.colors(
                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                    contentColor = TextPrimary,
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                )
            ) {
                Text(stringResource(R.string.settings_btn_refresh_lists), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { viewModel.disconnectTrakt() },
                colors = ButtonDefaults.colors(
                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                    contentColor = Color(0xFFEF4444),
                    focusedContainerColor = Color(0xFFEF4444),
                    focusedContentColor = TextPrimary
                )
            ) {
                Text(stringResource(R.string.settings_btn_unlink), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        TvSectionHeader(title = stringResource(R.string.settings_section_link_account))

        Button(
            onClick = { viewModel.startTraktDeviceFlow() },
            colors = ButtonDefaults.colors(
                containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                contentColor = TextPrimary,
                focusedContainerColor = focusColor,
                focusedContentColor = focusContent
            ),
            enabled = !uiState.isWaitingTraktAuth
        ) {
            Text(
                text = if (uiState.isWaitingTraktAuth) stringResource(R.string.settings_btn_generating_code) else stringResource(R.string.settings_btn_link_qr),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    TvSectionHeader(title = stringResource(R.string.settings_section_advanced_api))

    TvSettingRow(
        title = stringResource(R.string.settings_row_client_id),
        value = formatKey(uiState.traktClientId),
        onClick = { viewModel.openEditDialog(SettingField.TRAKT_CLIENT_ID) }
    )

    TvSettingRow(
        title = stringResource(R.string.settings_row_client_secret),
        value = if (uiState.traktClientSecret.isNotBlank()) "••••••••" else stringResource(R.string.settings_value_default),
        onClick = { viewModel.openEditDialog(SettingField.TRAKT_CLIENT_SECRET) }
    )

    TvSettingRow(
        title = stringResource(R.string.settings_row_manual_token),
        value = if (uiState.traktToken.isNotBlank()) formatKey(uiState.traktToken) else stringResource(R.string.settings_value_no_token),
        onClick = { viewModel.openEditDialog(SettingField.TRAKT_MANUAL_TOKEN) }
    )
}

// =============================================================================
// CATEGORÍA 2: PLATAFORMAS (FILTROS DE CATÁLOGO)
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlatformsCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
    TvSectionHeader(title = stringResource(R.string.settings_section_catalog_country))

    TvSettingRow(
        title = stringResource(R.string.settings_row_region),
        value = uiState.watchRegion,
        onClick = { viewModel.openEditDialog(SettingField.WATCH_REGION) }
    )

    TvSettingRow(
        title = stringResource(R.string.settings_field_tmdb_api_key),
        value = stringResource(
            if (uiState.tmdbApiKey.isBlank()) R.string.settings_tmdb_key_unset
            else R.string.settings_tmdb_key_set
        ),
        onClick = { viewModel.openEditDialog(SettingField.TMDB_API_KEY) }
    )

    // Accesos directos rápidos para cambiar de país con un solo clic de mando
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
    ) {
        // La región del sistema va primera: es el valor por defecto y así siempre se puede
        // recuperar con el mando después de haber elegido otra (si no, solo se podría escribiendo).
        (listOf(ApiConfig.TMDB_WATCH_REGION) + listOf("ES", "MX", "US", "AR", "CO", "CL", "PE")).distinct().forEach { code ->
            val isCurrent = uiState.watchRegion.equals(code, ignoreCase = true)
            Surface(
                onClick = { viewModel.updateWatchRegion(code) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isCurrent) tv.medusa.app.ui.theme.TvPalette.GlassSelected else Color(0x0CFFFFFF),
                    focusedContainerColor = Color(0x35FFFFFF)
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = focusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = code,
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) TextPrimary else tv.medusa.app.ui.theme.TvPalette.TextMuted
                    )
                }
            }
        }
    }

    // Sin clave TMDB no hay catálogo que filtrar: los toggles no tendrían efecto y parecerían
    // rotos. La fila de la clave, arriba, sigue visible para que el usuario pueda activarlos.
    if (uiState.tmdbApiKey.isNotBlank()) {
        TvSectionHeader(title = stringResource(R.string.settings_section_filter_platform))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val region = uiState.watchRegion.ifBlank { ApiConfig.TMDB_WATCH_REGION }
            SUPPORTED_PLATFORMS.filter { it.isAvailableIn(region) }.forEach { platform ->
                val isSelected = platform.id in uiState.selectedPlatforms
                TvPlatformToggleRow(
                    platform = platform,
                    isSelected = isSelected,
                    onToggle = { viewModel.togglePlatform(platform.id) },
                    focusColor = focusColor,
                    focusContent = focusContent
                )
            }
        }
    }
}

// =============================================================================
// CATEGORÍA 1: REPRODUCCIÓN
// =============================================================================

@Composable
private fun PlaybackCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
    TvSettingsGroupCard(title = stringResource(R.string.settings_section_playback_auto)) {
        TvSettingSwitchRow(
            title = stringResource(R.string.settings_playback_next_episode_title),
            description = stringResource(R.string.settings_playback_next_episode_desc),
            checked = uiState.autoPlayNextEpisodeEnabled,
            onCheckedChange = { viewModel.updateAutoPlayNextEpisode(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )

        TvSettingSwitchRow(
            title = stringResource(R.string.settings_playback_next_movie_title),
            description = stringResource(R.string.settings_playback_next_movie_desc),
            checked = uiState.autoPlayNextMovieEnabled,
            onCheckedChange = { viewModel.updateAutoPlayNextMovie(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }

    TvSettingsGroupCard(title = stringResource(R.string.settings_section_video_display)) {
        TvSettingSwitchRow(
            title = stringResource(R.string.settings_playback_afr_title),
            description = stringResource(R.string.settings_playback_afr_desc),
            checked = uiState.autoFrameRateEnabled,
            onCheckedChange = { viewModel.updateAutoFrameRate(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }
}

// =============================================================================
// CATEGORÍA 2: AUDIO Y SONIDO
// =============================================================================

@Composable
private fun AudioCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
    TvSettingsGroupCard(title = stringResource(R.string.settings_section_audio_output)) {
        TvSettingsSegmentedRow(
            title = stringResource(R.string.settings_audio_passthrough_title),
            description = stringResource(R.string.settings_audio_passthrough_desc),
            options = listOf(
                AUDIO_PASSTHROUGH_OFF to stringResource(R.string.settings_passthrough_off),
                AUDIO_PASSTHROUGH_AUTO to stringResource(R.string.settings_passthrough_auto),
                AUDIO_PASSTHROUGH_DIRECT to stringResource(R.string.settings_passthrough_direct)
            ),
            selectedOption = uiState.audioPassthroughMode,
            onOptionSelected = { viewModel.updateAudioPassthroughMode(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }

    TvSettingsGroupCard(title = stringResource(R.string.settings_section_audio_enhancements)) {
        TvSettingsSegmentedRow(
            title = stringResource(R.string.settings_audio_night_mode_title),
            description = stringResource(R.string.settings_audio_night_mode_desc),
            options = listOf(
                0 to stringResource(R.string.settings_audio_night_off),
                1 to stringResource(R.string.settings_audio_night_moderate),
                2 to stringResource(R.string.settings_audio_night_high)
            ),
            selectedOption = uiState.nightModeAudio,
            onOptionSelected = { viewModel.updateNightModeAudio(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )

        Spacer(modifier = Modifier.height(10.dp))

        TvSettingsSegmentedRow(
            title = stringResource(R.string.settings_audio_boost_title),
            description = stringResource(R.string.settings_audio_boost_desc),
            options = listOf(
                0 to stringResource(R.string.settings_audio_boost_none),
                5 to stringResource(R.string.settings_audio_boost_5db),
                10 to stringResource(R.string.settings_audio_boost_10db),
                15 to stringResource(R.string.settings_audio_boost_15db)
            ),
            selectedOption = uiState.audioVolumeBoostDb,
            onOptionSelected = { viewModel.updateAudioVolumeBoost(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }

    TvSettingsGroupCard(title = stringResource(R.string.settings_section_ambient_audio)) {
        TvSettingSwitchRow(
            title = stringResource(R.string.settings_section_theme_songs_audio),
            description = stringResource(R.string.settings_audio_theme_songs_desc),
            checked = uiState.themeSongsAudioEnabled,
            onCheckedChange = { viewModel.updateThemeSongsAudioEnabled(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )

        TvSettingSwitchRow(
            title = stringResource(R.string.settings_section_seasonal_audio),
            description = stringResource(R.string.settings_audio_seasonal_desc),
            checked = uiState.seasonalAudioEnabled,
            onCheckedChange = { viewModel.updateSeasonalAudioEnabled(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )

        TvSettingSwitchRow(
            title = stringResource(R.string.settings_section_navigation_sound),
            description = stringResource(R.string.settings_audio_navigation_desc),
            checked = uiState.navigationSoundEnabled,
            onCheckedChange = { viewModel.updateNavigationSoundEnabled(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }
}

// =============================================================================
// CATEGORÍA 3: SUBTÍTULOS
// =============================================================================

@Composable
private fun SubtitlesCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
    TvSettingsGroupCard(title = stringResource(R.string.settings_section_subtitles_style)) {
        TvSettingsSegmentedRow(
            title = stringResource(R.string.settings_subtitles_size_title),
            description = stringResource(R.string.settings_subtitles_size_desc),
            options = listOf(
                SUBTITLE_SIZE_SMALL to stringResource(R.string.settings_subtitles_size_small),
                SUBTITLE_SIZE_NORMAL to stringResource(R.string.settings_subtitles_size_normal),
                SUBTITLE_SIZE_LARGE to stringResource(R.string.settings_subtitles_size_large)
            ),
            selectedOption = uiState.subtitleSize,
            onOptionSelected = { viewModel.updateSubtitleSize(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )

        Spacer(modifier = Modifier.height(10.dp))

        TvSettingSwitchRow(
            title = stringResource(R.string.settings_subtitles_background_title),
            description = stringResource(R.string.settings_subtitles_background_desc),
            checked = uiState.subtitleBackground,
            onCheckedChange = { viewModel.updateSubtitleBackground(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }

    TvSettingsGroupCard(title = stringResource(R.string.settings_subtitles_preview_title)) {
        TvSubtitlePreviewBox(
            subtitleSize = uiState.subtitleSize,
            subtitleBackground = uiState.subtitleBackground,
            sampleText = stringResource(R.string.settings_subtitles_preview_sample),
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

// =============================================================================
// CATEGORÍA 4: INTERFAZ Y PANTALLA
// =============================================================================

@Composable
private fun AppearanceCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
    TvSettingsGroupCard(title = stringResource(R.string.settings_section_theme)) {
        TvSettingsSegmentedRow(
            title = stringResource(R.string.settings_section_theme),
            description = stringResource(R.string.settings_theme_desc),
            options = listOf(
                ACCENT_CYAN to stringResource(R.string.settings_theme_neon),
                ACCENT_AMBER to stringResource(R.string.settings_theme_amber),
                APP_THEME_MONOCHROME to stringResource(R.string.settings_theme_monochrome)
            ),
            selectedOption = when {
                uiState.isMonochrome -> APP_THEME_MONOCHROME
                uiState.accentColor == ACCENT_AMBER -> ACCENT_AMBER
                else -> ACCENT_CYAN
            },
            onOptionSelected = { viewModel.setAppTheme(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }

    TvSettingsGroupCard(title = stringResource(R.string.settings_section_buttons)) {
        TvSettingsSegmentedRow(
            title = stringResource(R.string.settings_section_buttons),
            description = stringResource(R.string.settings_buttons_desc),
            options = listOf(
                BUTTON_STYLE_ICONS_ONLY to stringResource(R.string.settings_btn_style_icons),
                BUTTON_STYLE_TEXT_ONLY to stringResource(R.string.settings_btn_style_text),
                BUTTON_STYLE_ICONS_AND_TEXT to stringResource(R.string.settings_btn_style_both)
            ),
            selectedOption = uiState.buttonStyle,
            onOptionSelected = { viewModel.updateButtonStyle(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }

    TvSettingsGroupCard(title = stringResource(R.string.settings_section_perf)) {
        TvSettingsSegmentedRow(
            title = stringResource(R.string.settings_section_perf),
            description = stringResource(R.string.settings_perf_desc),
            options = listOf(
                PERFORMANCE_MODE_AUTO to stringResource(R.string.settings_perf_auto),
                PERFORMANCE_MODE_HIGH to stringResource(R.string.settings_perf_high),
                PERFORMANCE_MODE_LOW to stringResource(R.string.settings_perf_low)
            ),
            selectedOption = uiState.performanceMode,
            onOptionSelected = { viewModel.updatePerformanceMode(it) },
            focusColor = focusColor,
            focusContent = focusContent
        )
    }
}

// =============================================================================
// CATEGORÍA 4: ACERCA DE
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AboutCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color,
    onOpenPrivacyPolicy: () -> Unit
) {
    val updateInfo = uiState.updateInfo
    val hasUpdate = updateInfo?.hasUpdate == true

    TvSectionHeader(title = stringResource(R.string.settings_section_system))

    TvSettingRow(
        title = stringResource(R.string.settings_row_app),
        value = stringResource(R.string.app_name)
    )

    TvSettingRow(
        title = stringResource(R.string.settings_row_version),
        value = stringResource(
            R.string.settings_version_build,
            updateInfo?.currentVersion ?: BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
    )

    val editionText = when {
        uiState.isFamilyVipUser -> stringResource(R.string.settings_edition_vip)
        else -> stringResource(R.string.settings_edition_lifetime)
    }
    TvSettingRow(
        title = stringResource(R.string.settings_row_edition),
        value = editionText
    )

    TvSettingRow(
        title = stringResource(R.string.settings_row_status),
        value = if (hasUpdate) stringResource(R.string.settings_status_update_available, updateInfo?.latestVersion.orEmpty()) else stringResource(R.string.settings_status_up_to_date)
    )

    if (BuildConfig.ENABLE_IN_APP_UPDATER) {
        TvSectionHeader(title = stringResource(R.string.settings_section_updates))

        val channelText = when {
            uiState.isFamilyVipUser -> stringResource(R.string.settings_channel_vip)
            BuildConfig.UPDATE_CHANNEL.equals("beta", ignoreCase = true) ->
                stringResource(R.string.settings_channel_ota_beta)
            else -> stringResource(R.string.settings_channel_ota)
        }
        TvSettingRow(
            title = stringResource(R.string.settings_row_channel),
            value = channelText
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Button(
                onClick = { viewModel.checkForAppUpdates() },
                colors = ButtonDefaults.colors(
                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                    contentColor = TextPrimary,
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                ),
                enabled = !uiState.isCheckingUpdate
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isCheckingUpdate) stringResource(R.string.settings_btn_checking) else stringResource(R.string.settings_btn_check_updates),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (hasUpdate && updateInfo?.apkUrl != null) {
                Button(
                    onClick = { viewModel.downloadAndInstallUpdate() },
                    colors = ButtonDefaults.colors(
                        containerColor = focusColor,
                        contentColor = focusContent,
                        focusedContainerColor = focusColor,
                        focusedContentColor = focusContent
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDone, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        val progressText = uiState.downloadProgress?.let { " (${(it * 100).toInt()}%)" } ?: ""
                        Text(
                            text = stringResource(R.string.settings_btn_download_install, progressText),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        TvSectionHeader(title = stringResource(R.string.settings_section_distribution))
        TvSettingRow(
            title = stringResource(R.string.settings_row_channel),
            value = stringResource(R.string.settings_channel_play)
        )
    }

    val notes = updateInfo?.releaseNotes
    if (!notes.isNullOrBlank()) {
        TvSectionHeader(title = stringResource(R.string.settings_section_release_notes, updateInfo.latestVersion.uppercase()))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0CFFFFFF), RoundedCornerShape(10.dp))
                .border(0.5.dp, tv.medusa.app.ui.theme.TvPalette.GlassSubtle, RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            Text(
                text = notes,
                color = tv.medusa.app.ui.theme.TvPalette.TextSoft,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }

    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    TvSectionHeader(title = stringResource(R.string.settings_section_data_sources))
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // TMDB Source Card
        Surface(
            onClick = { openUrl("https://www.themoviedb.org/") },
            modifier = Modifier
                .handleTouchClick { openUrl("https://www.themoviedb.org/") }
                .fillMaxWidth(),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x0CFFFFFF),
                focusedContainerColor = tv.medusa.app.ui.theme.TvPalette.GlassSelected
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(border = BorderStroke(1.5.dp, focusColor))
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_tmdb_logo),
                        contentDescription = "TMDB",
                        modifier = Modifier.height(20.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = stringResource(R.string.settings_row_tmdb),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.settings_tmdb_attribution),
                    color = tv.medusa.app.ui.theme.TvPalette.TextSoft,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Trakt.tv Source Card
        Surface(
            onClick = { openUrl("https://trakt.tv/") },
            modifier = Modifier
                .handleTouchClick { openUrl("https://trakt.tv/") }
                .fillMaxWidth(),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x0CFFFFFF),
                focusedContainerColor = tv.medusa.app.ui.theme.TvPalette.GlassSelected
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(border = BorderStroke(1.5.dp, focusColor))
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_brand_trakt),
                        contentDescription = "Trakt.tv",
                        modifier = Modifier.height(18.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = stringResource(R.string.settings_row_trakt_data),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.settings_trakt_attribution),
                    color = tv.medusa.app.ui.theme.TvPalette.TextSoft,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }

    TvSectionHeader(title = stringResource(R.string.settings_section_legal))
    TvSettingRow(
        title = stringResource(R.string.settings_row_privacy_policy),
        value = stringResource(R.string.settings_privacy_policy_value),
        onClick = onOpenPrivacyPolicy
    )
}

// =============================================================================
// COMPONENTES REUTILIZABLES (ESTILO TV MINIMALISTA)
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsNavRailItem(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusColor: Color,
    focusContent: Color,
    modifier: Modifier = Modifier,
    upRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .then(
                if (upRequester != null) {
                    Modifier.focusProperties { up = upRequester }
                } else Modifier
            )
            .handleTouchClick { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) tv.medusa.app.ui.theme.TvPalette.GlassIdle else Color.Transparent,
            focusedContainerColor = focusColor,
            contentColor = if (isSelected) focusColor else tv.medusa.app.ui.theme.TvPalette.TextMuted,
            focusedContentColor = focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    0.5.dp,
                    if (isSelected) tv.medusa.app.ui.theme.TvPalette.GlassSelected else Color.Transparent
                )
            ),
            focusedBorder = Border.None
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isFocused) focusContent else (if (isSelected) focusColor else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                color = if (isFocused) focusContent else (if (isSelected) focusColor else tv.medusa.app.ui.theme.TvPalette.TextMuted),
                fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isMonochrome = LocalIsMonochromeTheme.current
    val accentKey = LocalAccentColor.current
    val focusColor = if (isMonochrome) TextPrimary else TvAccent.getColor(accentKey)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .handleTouchClick { onClick() }
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x0CFFFFFF),
                focusedContainerColor = tv.medusa.app.ui.theme.TvPalette.GlassSelected
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(border = BorderStroke(1.5.dp, focusColor))
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (value != null) {
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = tv.medusa.app.ui.theme.TvPalette.Placeholder,
                        modifier = Modifier.size(15.dp)
                    )
                } else if (trailingContent != null) {
                    trailingContent()
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(Color(0x08FFFFFF), RoundedCornerShape(10.dp))
                .border(0.5.dp, Color(0x0EFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = tv.medusa.app.ui.theme.TvPalette.TextMuted,
                    modifier = Modifier.weight(1f)
                )

                if (value != null) {
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (trailingContent != null) {
                    trailingContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T> TvSegmentedSelector(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    focusColor: Color,
    focusContent: Color,
    modifier: Modifier = Modifier
) {
    val isMonochrome = LocalIsMonochromeTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (option, label) ->
            val isSelected = option == selectedOption
            var isFocused by remember { mutableStateOf(false) }

            Surface(
                onClick = { onOptionSelected(option) },
                modifier = Modifier
                    .handleTouchClick { onOptionSelected(option) }
                    .onFocusChanged { isFocused = it.isFocused }
                    .weight(1f),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) (if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassSelected else focusColor.copy(alpha = 0.22f)) else Color(0x0AFFFFFF),
                    focusedContainerColor = focusColor,
                    contentColor = if (isSelected) (if (isMonochrome) TextPrimary else focusColor) else tv.medusa.app.ui.theme.TvPalette.TextMuted,
                    focusedContentColor = focusContent
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (isFocused) focusContent else (if (isMonochrome) TextPrimary else focusColor),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isFocused) focusContent else (if (isSelected) (if (isMonochrome) TextPrimary else focusColor) else tv.medusa.app.ui.theme.TvPalette.TextMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTraktListToggleRow(
    list: TraktUserListDto,
    isVisible: Boolean,
    onToggle: () -> Unit,
    focusColor: Color,
    focusContent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        modifier = modifier
            .handleTouchClick { onToggle() }
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isVisible) Color(0x12FFFFFF) else Color(0x06FFFFFF),
            focusedContainerColor = tv.medusa.app.ui.theme.TvPalette.GlassSelected
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(border = BorderStroke(1.5.dp, focusColor))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isVisible) Color(0x1AFFFFFF) else Color(0x0DFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = if (isVisible) TextPrimary else tv.medusa.app.ui.theme.TvPalette.Placeholder,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = list.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isVisible) TextPrimary else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                )
                val subtitle = buildString {
                    append(stringResource(R.string.settings_list_item_count, list.itemCount ?: 0))
                    val author = list.user?.username
                    if (!author.isNullOrBlank()) {
                        append(", ")
                        append(stringResource(R.string.settings_list_author, author))
                    }
                    if (!isVisible) {
                        append(", ")
                        append(stringResource(R.string.settings_hidden_in_medusa))
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isVisible) tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool else tv.medusa.app.ui.theme.TvPalette.Placeholder
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isVisible) focusColor else tv.medusa.app.ui.theme.TvPalette.GlassIdle),
                contentAlignment = Alignment.Center
            ) {
                if (isVisible) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = focusContent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPlatformToggleRow(
    platform: StreamPlatform,
    isSelected: Boolean,
    onToggle: () -> Unit,
    focusColor: Color,
    focusContent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        modifier = modifier
            .handleTouchClick { onToggle() }
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0x12FFFFFF) else Color(0x06FFFFFF),
            focusedContainerColor = tv.medusa.app.ui.theme.TvPalette.GlassSelected
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(border = BorderStroke(1.5.dp, focusColor))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .width(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1118))
                    .border(0.5.dp, Color(0x24FFFFFF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = platform.iconRes),
                    contentDescription = platform.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = platform.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isSelected) focusColor else tv.medusa.app.ui.theme.TvPalette.GlassIdle),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = focusContent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun TvQuickConnectDialog(
    title: String,
    step1Text: String,
    code: String,
    qrData: String,
    onDismiss: () -> Unit,
    focusColor: Color,
    focusContent: Color
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDD06080D)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .adaptiveDialogWidth(540.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, tv.medusa.app.ui.theme.TvPalette.GlassDivider, RoundedCornerShape(16.dp))
                    .padding(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_activation_step1, step1Text),
                        fontSize = 13.sp,
                        color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                            .border(0.5.dp, tv.medusa.app.ui.theme.TvPalette.GlassIdle, RoundedCornerShape(12.dp))
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TextPrimary)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            QrCodeImage(
                                data = qrData,
                                contentDescription = stringResource(R.string.settings_qr_content_description),
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.settings_activation_step2),
                                fontSize = 12.sp,
                                color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = code,
                                color = TextPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = focusColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_btn_waiting_confirmation),
                                    fontSize = 11.sp,
                                    color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .handleTouchClick { onDismiss() },
                        colors = ButtonDefaults.colors(
                            containerColor = tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
                            contentColor = TextPrimary,
                            focusedContainerColor = focusColor,
                            focusedContentColor = focusContent
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TvColors.TextTertiary,
        letterSpacing = 0.2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 2.dp)
    )
}

// =============================================================================
// MODAL DIÁLOGO DE EDICIÓN PARA TV
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvEditDialog(
    field: SettingField,
    initialValue: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
    focusColor: Color,
    focusContent: Color
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isPasswordVisible by remember(field) { mutableStateOf(false) }

    var textFieldValue by remember(field) {
        mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
    }

    LaunchedEffect(initialValue) {
        if (textFieldValue.text != initialValue) {
            textFieldValue = TextFieldValue(initialValue, TextRange(initialValue.length))
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .adaptiveDialogWidth(480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, tv.medusa.app.ui.theme.TvPalette.GlassDivider, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(field.titleRes),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newTfv ->
                            textFieldValue = newTfv
                            onValueChange(newTfv.text)
                        },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(field.placeholderRes),
                                color = tv.medusa.app.ui.theme.TvPalette.Placeholder,
                                fontSize = 13.sp
                            )
                        },
                        trailingIcon = {
                            if (textFieldValue.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    textFieldValue = TextFieldValue("", TextRange.Zero)
                                    onValueChange("")
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = stringResource(R.string.settings_dialog_clear),
                                        tint = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        visualTransformation = if (field.isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = if (field.isPassword) KeyboardType.Password else KeyboardType.Text
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onCommit() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = tv.medusa.app.ui.theme.TvPalette.GlassIdle,
                            unfocusedContainerColor = Color(0x0CFFFFFF),
                            focusedBorderColor = focusColor,
                            unfocusedBorderColor = tv.medusa.app.ui.theme.TvPalette.GlassSelected,
                            cursorColor = focusColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (field.isPassword) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            modifier = Modifier
                                .fillMaxWidth()
                                .handleTouchClick { isPasswordVisible = !isPasswordVisible },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0x12FFFFFF),
                                contentColor = TextPrimary,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPasswordVisible) stringResource(R.string.settings_dialog_hide_password) else stringResource(R.string.settings_dialog_show_password),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onCommit,
                            modifier = Modifier
                                .weight(1f)
                                .handleTouchClick { onCommit() },
                            colors = ButtonDefaults.colors(
                                containerColor = focusColor,
                                contentColor = focusContent,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            )
                        ) {
                            Text(stringResource(R.string.settings_dialog_save), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .handleTouchClick { onDismiss() },
                            colors = ButtonDefaults.colors(
                                containerColor = tv.medusa.app.ui.theme.TvPalette.GlassIdle,
                                contentColor = TextPrimary,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            )
                        ) {
                            Text(stringResource(R.string.settings_dialog_cancel), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatNumber(count: Int): String {
    return String.format(Locale.GERMANY, "%,d", count)
}

@Composable
private fun formatKey(value: String): String {
    if (value.isBlank()) return stringResource(R.string.settings_value_not_configured)
    return if (value.length > 20) "${value.take(8)}...${value.takeLast(6)}" else value
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPrivacyPolicyDialog(
    onDismiss: () -> Unit,
    focusColor: Color,
    focusContent: Color
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val qrData = remember { ApiConfig.PRIVACY_POLICY_URL }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .adaptiveDialogWidth(520.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, tv.medusa.app.ui.theme.TvPalette.GlassDivider, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_privacy_policy_dialog_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.settings_privacy_policy_dialog_desc),
                        fontSize = 13.sp,
                        color = tv.medusa.app.ui.theme.TvPalette.TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                            .border(0.5.dp, tv.medusa.app.ui.theme.TvPalette.GlassIdle, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TextPrimary)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            QrCodeImage(
                                data = qrData,
                                contentDescription = stringResource(R.string.settings_qr_content_description),
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "URL:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ApiConfig.PRIVACY_POLICY_URL,
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ApiConfig.PRIVACY_POLICY_URL))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = tv.medusa.app.ui.theme.TvPalette.GlassIdle,
                                contentColor = TextPrimary,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_privacy_policy_dialog_open_browser),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.colors(
                                containerColor = tv.medusa.app.ui.theme.TvPalette.GlassIdle,
                                contentColor = TextPrimary,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_privacy_policy_dialog_close),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
