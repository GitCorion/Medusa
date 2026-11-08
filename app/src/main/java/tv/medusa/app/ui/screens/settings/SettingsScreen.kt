package tv.medusa.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import java.net.URLEncoder
import java.util.Locale
import tv.medusa.app.BuildConfig
import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.ui.components.MedusaProDialog
import tv.medusa.app.ui.theme.LocalBillingManager
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import tv.medusa.app.data.local.APP_THEME_ORIGINAL
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_AND_TEXT
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_OFF
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_AUTO
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_DIRECT
import tv.medusa.app.data.local.PERFORMANCE_MODE_AUTO
import tv.medusa.app.data.local.PERFORMANCE_MODE_HIGH
import tv.medusa.app.data.local.PERFORMANCE_MODE_LOW
import tv.medusa.app.data.model.SUPPORTED_PLATFORMS
import tv.medusa.app.data.model.StreamPlatform
import tv.medusa.app.data.remote.trakt.TraktUserListDto
import tv.medusa.app.ui.components.TvNavTab
import tv.medusa.app.ui.components.TvTopBar
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvColors
import kotlinx.coroutines.delay

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
    val focusColor = if (uiState.isMonochrome) Color.White else TvAccent.getColor(uiState.accentColor)
    val focusContent = if (uiState.isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(uiState.accentColor)
    var showProDialog by remember { mutableStateOf(false) }
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
                isMonochrome = uiState.isMonochrome
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 48.dp, bottom = 24.dp)
            ) {
                // =============================================================
                // BARRA LATERAL DE CATEGORÍAS (220dp)
                // =============================================================
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(Color(0x06FFFFFF), RoundedCornerShape(14.dp))
                        .border(0.5.dp, Color(0x10FFFFFF), RoundedCornerShape(14.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TvColors.TextTertiary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.Dns,
                        title = stringResource(R.string.settings_nav_jellyfin),
                        isSelected = uiState.activeCategory == 0,
                        onClick = { viewModel.selectCategory(0) },
                        focusColor = focusColor,
                        focusContent = focusContent
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.SyncAlt,
                        title = stringResource(R.string.settings_nav_trakt),
                        isSelected = uiState.activeCategory == 1,
                        onClick = { viewModel.selectCategory(1) },
                        focusColor = focusColor,
                        focusContent = focusContent
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.Public,
                        title = stringResource(R.string.settings_nav_platforms),
                        isSelected = uiState.activeCategory == 2,
                        onClick = { viewModel.selectCategory(2) },
                        focusColor = focusColor,
                        focusContent = focusContent
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.settings_nav_appearance),
                        isSelected = uiState.activeCategory == 3,
                        onClick = { viewModel.selectCategory(3) },
                        focusColor = focusColor,
                        focusContent = focusContent
                    )

                    TvSettingsNavRailItem(
                        icon = Icons.Rounded.SystemUpdate,
                        title = stringResource(R.string.settings_nav_about),
                        isSelected = uiState.activeCategory == 4,
                        onClick = { viewModel.selectCategory(4) },
                        focusColor = focusColor,
                        focusContent = focusContent
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
                        1 -> stringResource(R.string.settings_nav_trakt)
                        2 -> stringResource(R.string.settings_nav_platforms)
                        3 -> stringResource(R.string.settings_section_appearance_perf)
                        else -> stringResource(R.string.settings_nav_about)
                    }

                    Text(
                        text = sectionTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
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
                            1 -> TraktCategory(uiState, viewModel, focusColor, focusContent)
                            2 -> PlatformsCategory(uiState, viewModel, focusColor, focusContent)
                            3 -> AppearanceCategory(uiState, viewModel, focusColor, focusContent)
                            4 -> AboutCategory(
                                uiState = uiState,
                                viewModel = viewModel,
                                focusColor = focusColor,
                                focusContent = focusContent,
                                onOpenProDialog = { showProDialog = true },
                                onOpenPrivacyPolicy = { showPrivacyPolicyDialog = true }
                            )
                        }

                        if (uiState.statusMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                                    .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
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
                                        color = Color(0xFFE2E8F0),
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

        val billingManager = LocalBillingManager.current
        if (showProDialog && billingManager != null) {
            MedusaProDialog(
                billingManager = billingManager,
                onDismiss = { showProDialog = false }
            )
        }

        if (showPrivacyPolicyDialog) {
            TvPrivacyPolicyDialog(
                onDismiss = { showPrivacyPolicyDialog = false },
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
        TvSectionHeader(title = stringResource(R.string.settings_section_server_session))

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

        TvSectionHeader(title = stringResource(R.string.settings_section_actions))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Button(
                onClick = { viewModel.forceSyncCatalog() },
                modifier = Modifier.handleTouchClick(enabled = !uiState.isSyncingCatalog) { viewModel.forceSyncCatalog() },
                colors = ButtonDefaults.colors(
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color.White,
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                ),
                enabled = !uiState.isSyncingCatalog
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isSyncingCatalog) {
                        CircularProgressIndicator(
                            color = Color.White,
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
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color.White,
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
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color(0xFFEF4444),
                    focusedContainerColor = Color(0xFFEF4444),
                    focusedContentColor = Color.White
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_btn_logout), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
            onClick = { viewModel.activateDemoMode() },
            modifier = Modifier.handleTouchClick { viewModel.activateDemoMode() },
            colors = ButtonDefaults.colors(
                containerColor = Color(0x18FFFFFF),
                contentColor = Color.White,
                focusedContainerColor = focusColor,
                focusedContentColor = focusContent
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_btn_demo_catalog), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (uiState.serverUrl.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { viewModel.testServerConnection() },
                modifier = Modifier.handleTouchClick { viewModel.testServerConnection() },
                colors = ButtonDefaults.colors(
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color.White,
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
                        containerColor = Color(0x14FFFFFF),
                        contentColor = Color.White,
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

                if (uiState.jellyfinQrUrl != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    TvActivationCodeBox(
                        qrUrl = uiState.jellyfinQrUrl!!,
                        step1Text = stringResource(R.string.settings_qc_step1),
                        code = uiState.jellyfinQuickConnectCode ?: "..."
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
            color = Color(0xFF94A3B8),
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
                containerColor = Color(0x14FFFFFF),
                contentColor = Color.White,
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
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color(0xFFFF8A80),
                    focusedContainerColor = Color(0xFFEF4444),
                    focusedContentColor = Color.White
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
        color = Color(0xFF64748B),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

// =============================================================================
// CATEGORÍA 1: TRAKT.TV
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TraktCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
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
            color = Color(0xFF94A3B8),
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
                    color = Color(0xFF94A3B8)
                )
            }
        } else if (uiState.traktUserLists.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_trakt_no_lists),
                fontSize = 12.sp,
                color = Color(0xFF64748B),
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
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color.White,
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                )
            ) {
                Text(stringResource(R.string.settings_btn_refresh_lists), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { viewModel.disconnectTrakt() },
                colors = ButtonDefaults.colors(
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color(0xFFEF4444),
                    focusedContainerColor = Color(0xFFEF4444),
                    focusedContentColor = Color.White
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
                containerColor = Color(0x14FFFFFF),
                contentColor = Color.White,
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

        if (uiState.traktQrUrl != null) {
            Spacer(modifier = Modifier.height(14.dp))
            TvActivationCodeBox(
                qrUrl = uiState.traktQrUrl!!,
                step1Text = stringResource(R.string.settings_trakt_step1),
                code = uiState.traktUserCode ?: "..."
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
                    containerColor = if (isCurrent) Color(0x28FFFFFF) else Color(0x0CFFFFFF),
                    focusedContainerColor = Color(0x35FFFFFF)
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = BorderStroke(
                            0.5.dp,
                            if (isCurrent) focusColor.copy(alpha = 0.6f) else Color(0x14FFFFFF)
                        )
                    ),
                    focusedBorder = Border(border = BorderStroke(1.5.dp, focusColor))
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
                        color = if (isCurrent) Color.White else Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }

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

// =============================================================================
// CATEGORÍA 3: APARIENCIA & RENDIMIENTO
// =============================================================================

@Composable
private fun AppearanceCategory(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    focusColor: Color,
    focusContent: Color
) {
    TvSectionHeader(title = stringResource(R.string.settings_section_theme))

    TvSegmentedSelector(
        options = listOf(
            APP_THEME_ORIGINAL to stringResource(R.string.settings_theme_neon),
            APP_THEME_MONOCHROME to stringResource(R.string.settings_theme_monochrome)
        ),
        selectedOption = if (uiState.isMonochrome) APP_THEME_MONOCHROME else APP_THEME_ORIGINAL,
        onOptionSelected = { viewModel.setAppTheme(it) },
        focusColor = focusColor,
        focusContent = focusContent
    )

    TvSectionHeader(title = stringResource(R.string.settings_section_buttons))

    TvSegmentedSelector(
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

    TvSectionHeader(title = stringResource(R.string.settings_section_perf))

    TvSegmentedSelector(
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

    TvSectionHeader(title = stringResource(R.string.settings_section_navigation_sound))

    TvSegmentedSelector(
        options = listOf(
            true to stringResource(R.string.settings_navigation_sound_enabled),
            false to stringResource(R.string.settings_navigation_sound_disabled)
        ),
        selectedOption = uiState.navigationSoundEnabled,
        onOptionSelected = { viewModel.updateNavigationSoundEnabled(it) },
        focusColor = focusColor,
        focusContent = focusContent
    )

    TvSectionHeader(title = stringResource(R.string.settings_section_seasonal_audio))

    TvSegmentedSelector(
        options = listOf(
            true to stringResource(R.string.settings_seasonal_audio_enabled),
            false to stringResource(R.string.settings_seasonal_audio_disabled)
        ),
        selectedOption = uiState.seasonalAudioEnabled,
        onOptionSelected = { viewModel.updateSeasonalAudioEnabled(it) },
        focusColor = focusColor,
        focusContent = focusContent
    )

    TvSectionHeader(title = stringResource(R.string.settings_section_audio_passthrough))

    TvSegmentedSelector(
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
    onOpenProDialog: () -> Unit,
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
        uiState.hasPurchasedPro -> stringResource(R.string.settings_edition_lifetime)
        uiState.isTrialActive -> stringResource(R.string.settings_edition_trial, uiState.trialDaysRemaining)
        else -> stringResource(R.string.settings_edition_standard)
    }
    TvSettingRow(
        title = stringResource(R.string.settings_row_edition),
        value = editionText
    )

    if (!uiState.hasPurchasedPro && !uiState.isFamilyVipUser) {
        Row(
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        ) {
            Button(
                onClick = onOpenProDialog,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFFD97706),
                    contentColor = Color.White,
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isTrialActive) stringResource(R.string.settings_btn_get_lifetime) else stringResource(R.string.settings_btn_unlock_pro),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    TvSettingRow(
        title = stringResource(R.string.settings_row_status),
        value = if (hasUpdate) stringResource(R.string.settings_status_update_available, updateInfo?.latestVersion.orEmpty()) else stringResource(R.string.settings_status_up_to_date)
    )

    if (BuildConfig.ENABLE_IN_APP_UPDATER) {
        TvSectionHeader(title = stringResource(R.string.settings_section_updates))

        val channelText = if (uiState.isFamilyVipUser) {
            stringResource(R.string.settings_channel_vip)
        } else {
            stringResource(R.string.settings_channel_ota)
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
                    containerColor = Color(0x14FFFFFF),
                    contentColor = Color.White,
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
                .border(0.5.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            Text(
                text = notes,
                color = Color(0xFFE2E8F0),
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
                focusedContainerColor = Color(0x28FFFFFF)
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(border = BorderStroke(0.5.dp, Color(0x14FFFFFF))),
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
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.settings_tmdb_attribution),
                    color = Color(0xFFE2E8F0),
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
                focusedContainerColor = Color(0x28FFFFFF)
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(border = BorderStroke(0.5.dp, Color(0x14FFFFFF))),
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
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.settings_trakt_attribution),
                    color = Color(0xFFE2E8F0),
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
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .handleTouchClick { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0x18FFFFFF) else Color.Transparent,
            focusedContainerColor = focusColor,
            contentColor = if (isSelected) focusColor else Color(0xFFCBD5E1),
            focusedContentColor = focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    0.5.dp,
                    if (isSelected) Color(0x28FFFFFF) else Color.Transparent
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
                tint = if (isFocused) focusContent else (if (isSelected) focusColor else Color(0xFF94A3B8)),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                color = if (isFocused) focusContent else (if (isSelected) focusColor else Color(0xFFCBD5E1)),
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
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentKey)

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
                focusedContainerColor = Color(0x28FFFFFF)
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(border = BorderStroke(0.5.dp, Color(0x14FFFFFF))),
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
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                if (value != null) {
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
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
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.weight(1f)
                )

                if (value != null) {
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
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
                    containerColor = if (isSelected) (if (isMonochrome) Color(0x28FFFFFF) else focusColor.copy(alpha = 0.22f)) else Color(0x0AFFFFFF),
                    focusedContainerColor = focusColor,
                    contentColor = if (isSelected) (if (isMonochrome) Color.White else focusColor) else Color(0xFFCBD5E1),
                    focusedContentColor = focusContent
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = BorderStroke(
                            if (isSelected) 1.dp else 0.5.dp,
                            if (isSelected) (if (isMonochrome) Color(0x40FFFFFF) else focusColor.copy(alpha = 0.45f)) else Color(0x14FFFFFF)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ),
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
                            tint = if (isFocused) focusContent else (if (isMonochrome) Color.White else focusColor),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isFocused) focusContent else (if (isSelected) (if (isMonochrome) Color.White else focusColor) else Color(0xFFCBD5E1)),
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
            focusedContainerColor = Color(0x28FFFFFF)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    0.5.dp,
                    if (isVisible) Color(0x22FFFFFF) else Color(0x0EFFFFFF)
                )
            ),
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
                    tint = if (isVisible) Color.White else Color(0xFF64748B),
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
                    color = if (isVisible) Color.White else Color(0xFF94A3B8)
                )
                val subtitle = buildString {
                    append(stringResource(R.string.settings_list_item_count, list.itemCount ?: 0))
                    val author = list.user?.username
                    if (!author.isNullOrBlank()) {
                        append(" • ")
                        append(stringResource(R.string.settings_list_author, author))
                    }
                    if (!isVisible) {
                        append(" • ")
                        append(stringResource(R.string.settings_hidden_in_medusa))
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isVisible) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isVisible) focusColor else Color(0x18FFFFFF)),
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
            focusedContainerColor = Color(0x28FFFFFF)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    0.5.dp,
                    if (isSelected) Color(0x22FFFFFF) else Color(0x0EFFFFFF)
                )
            ),
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
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isSelected) focusColor else Color(0x18FFFFFF)),
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

@Composable
private fun TvActivationCodeBox(
    qrUrl: String,
    step1Text: String,
    code: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
            .border(0.5.dp, Color(0x18FFFFFF), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = qrUrl,
                contentDescription = stringResource(R.string.settings_qr_content_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_activation_step1, step1Text),
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.settings_activation_step2),
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = code,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun TvSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TvColors.TextTertiary,
        letterSpacing = 1.2.sp,
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
                    .width(480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(field.titleRes),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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
                                color = Color(0xFF64748B),
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
                                        tint = Color(0xFF94A3B8),
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
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0x18FFFFFF),
                            unfocusedContainerColor = Color(0x0CFFFFFF),
                            focusedBorderColor = focusColor,
                            unfocusedBorderColor = Color(0x28FFFFFF),
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
                                contentColor = Color.White,
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
                                containerColor = Color(0x18FFFFFF),
                                contentColor = Color.White,
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
    val qrUrl = remember {
        "${ApiConfig.QR_GENERATOR_BASE_URL}?size=320x320&data=" + URLEncoder.encode(ApiConfig.PRIVACY_POLICY_URL, "UTF-8")
    }

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
                    .width(520.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
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
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.settings_privacy_policy_dialog_desc),
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                            .border(0.5.dp, Color(0x18FFFFFF), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = qrUrl,
                                contentDescription = stringResource(R.string.settings_qr_content_description),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "URL:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
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
                                containerColor = Color(0x18FFFFFF),
                                contentColor = Color.White,
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
                                containerColor = Color(0x18FFFFFF),
                                contentColor = Color.White,
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
