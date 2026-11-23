package tv.medusa.app.ui.screens.settings

import android.util.Log

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import tv.medusa.app.data.local.ACCENT_AMBER
import tv.medusa.app.data.local.ACCENT_CYAN
import tv.medusa.app.data.local.ACCENT_WHITE
import tv.medusa.app.data.local.APP_THEME_MONOCHROME
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_OFF
import tv.medusa.app.data.local.INDICATOR_THEME_COLOR
import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.data.local.PERFORMANCE_MODE_AUTO
import tv.medusa.app.data.local.PLATFORM_LOGO_COLOR
import tv.medusa.app.data.local.PLATFORM_LOGO_MONOCHROME
import tv.medusa.app.data.local.SUBTITLE_SIZE_NORMAL
import tv.medusa.app.data.remote.update.UpdateInfo
import tv.medusa.app.data.remote.trakt.TraktUserListDto
import tv.medusa.app.ui.screens.brand.BrandViewModel
import tv.medusa.app.ui.screens.home.HomeViewModel
import tv.medusa.app.ui.screens.genre.GenreViewModel
import tv.medusa.app.domain.usecase.GetHomeFeedUseCase
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.work.WorkManager
import androidx.lifecycle.viewModelScope
import tv.medusa.app.R
import tv.medusa.app.data.local.db.AppDatabase
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.DEFAULT_TRAKT_CLIENT_ID
import tv.medusa.app.data.local.DEFAULT_TRAKT_CLIENT_SECRET
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.HomeSnapshotFileStore
import tv.medusa.app.data.repository.MediaCaches
import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.update.AppUpdateManager
import tv.medusa.app.data.remote.jellyfin.DiscoveredJellyfinServer
import tv.medusa.app.data.remote.jellyfin.JellyfinApiService
import tv.medusa.app.data.remote.jellyfin.JellyfinAuthRequest
import tv.medusa.app.data.remote.jellyfin.JellyfinQuickConnectAuthRequest
import tv.medusa.app.data.remote.jellyfin.JellyfinQuickConnectResult
import tv.medusa.app.data.remote.jellyfin.JellyfinServerDiscovery
import tv.medusa.app.data.worker.JellyfinSyncWorker
import tv.medusa.app.data.worker.OverviewEnrichmentWorker
import tv.medusa.app.data.remote.trakt.TraktApiService
import tv.medusa.app.data.remote.trakt.TraktDeviceCodeRequest
import tv.medusa.app.data.remote.trakt.TraktDeviceTokenRequest
import tv.medusa.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLEncoder
import javax.inject.Inject

enum class SettingField(
    @StringRes val titleRes: Int,
    @StringRes val placeholderRes: Int,
    val isPassword: Boolean = false
) {
    SERVER_URL(
        R.string.settings_field_server_url,
        R.string.settings_field_server_url_placeholder
    ),
    USERNAME(
        R.string.settings_field_username,
        R.string.settings_field_username_placeholder
    ),
    PASSWORD(
        R.string.settings_field_password,
        R.string.settings_field_password_placeholder,
        isPassword = true
    ),
    TRAKT_CLIENT_ID(
        R.string.settings_field_trakt_client_id,
        R.string.settings_field_trakt_client_id_placeholder
    ),
    TRAKT_CLIENT_SECRET(
        R.string.settings_field_trakt_client_secret,
        R.string.settings_field_trakt_client_secret_placeholder,
        isPassword = true
    ),
    TRAKT_MANUAL_TOKEN(
        R.string.settings_field_trakt_token,
        R.string.settings_field_trakt_token_placeholder
    ),
    WATCH_REGION(
        R.string.settings_field_watch_region,
        R.string.settings_field_watch_region_placeholder
    ),
    TMDB_API_KEY(
        R.string.settings_field_tmdb_api_key,
        R.string.settings_field_tmdb_api_key_placeholder,
        isPassword = true
    )
}

data class SettingsUiState(
    val serverUrl: String = "",
    val lanServerUrl: String = "",
    val username: String = "",
    val password: String = "",
    val traktToken: String = "",
    val traktClientId: String = DEFAULT_TRAKT_CLIENT_ID,
    val traktClientSecret: String = DEFAULT_TRAKT_CLIENT_SECRET,
    val tmdbApiKey: String = "",
    val watchRegion: String = ApiConfig.TMDB_WATCH_REGION,
    val buttonStyle: String = BUTTON_STYLE_ICONS_ONLY,
    val accentColor: String = ACCENT_CYAN,
    val indicatorTheme: String = INDICATOR_THEME_COLOR,
    val platformLogoStyle: String = PLATFORM_LOGO_COLOR,
    val performanceMode: String = PERFORMANCE_MODE_AUTO,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isSuccess: Boolean = false,
    val activeCategory: Int = 0,
    val serverName: String? = null,
    val serverVersion: String? = null,
    val isJellyfinConnected: Boolean = false,
    val jellyfinAllowSelfSigned: Boolean = false,
    val jellyfinCertificateFingerprint: String = "",
    val isFetchingCertificateFingerprint: Boolean = false,
    // Trakt QR Device Flow & Status
    val isTraktConnected: Boolean = false,
    val traktUsername: String? = null,
    val traktUserCode: String? = null,
    val traktQrPayload: String? = null,
    val isWaitingTraktAuth: Boolean = false,
    // Jellyfin Quick Connect Flow
    val jellyfinQuickConnectCode: String? = null,
    val jellyfinQrPayload: String? = null,
    val isWaitingJellyfinQuickConnect: Boolean = false,
    // Descubrimiento de servidores en la red local
    val isDiscoveringServers: Boolean = false,
    val discoveredServers: List<DiscoveredJellyfinServer> = emptyList(),
    // TV Dialog Edit State
    val activeDialogField: SettingField? = null,
    val activeDialogValue: String = "",
    // GitHub App Updates
    val isCheckingUpdate: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val updateChannel: String = "stable",
    val downloadProgress: Float? = null,
    val selectedPlatforms: Set<String> = emptySet(),
    val hiddenTraktLists: Set<String> = emptySet(),
    val traktUserLists: List<TraktUserListDto> = emptyList(),
    val isLoadingTraktLists: Boolean = false,
    // Sincronización y Catálogo
    val syncedMoviesCount: Int = 0,
    val syncedSeriesCount: Int = 0,
    val syncedEpisodesCount: Int = 0,
    val isSyncingCatalog: Boolean = false,
    val lastSyncTimestamp: String? = null,
    val seasonalAudioEnabled: Boolean = true,
    val themeSongsAudioEnabled: Boolean = true,
    val navigationSoundEnabled: Boolean = true,
    val audioPassthroughMode: Int = AUDIO_PASSTHROUGH_OFF,
    val autoPlayNextEpisodeEnabled: Boolean = true,
    val autoPlayNextMovieEnabled: Boolean = true,
    val autoFrameRateEnabled: Boolean = false,
    val subtitleSize: String = SUBTITLE_SIZE_NORMAL,
    val subtitleBackground: Boolean = false,
    val hideWatched: Boolean = false,
    val nightModeAudio: Int = 0,
    val audioVolumeBoostDb: Int = 0
) {
    val isMonochrome: Boolean
        get() = indicatorTheme == INDICATOR_THEME_MONOCHROME || accentColor == ACCENT_WHITE
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val appUpdateManager: AppUpdateManager,
    private val mediaRepository: MediaRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var traktPollJob: Job? = null
    private var jellyfinQuickConnectJob: Job? = null

    init {
        _uiState.value = _uiState.value.copy(updateChannel = appUpdateManager.currentChannel())
        loadCurrentPreferences()
        viewModelScope.launch {
            AppUpdateManager.updateInfo.collect { info ->
                if (info != null) {
                    _uiState.value = _uiState.value.copy(
                        updateInfo = info,
                        statusMessage = if (info.hasUpdate) context.getString(R.string.settings_update_available_toast, info.latestVersion) else context.getString(R.string.settings_up_to_date_toast, info.currentVersion)
                    )
                }
            }
        }
        viewModelScope.launch {
            AppUpdateManager.downloadProgress.collect { progress ->
                if (progress != null) {
                    _uiState.value = _uiState.value.copy(downloadProgress = progress)
                }
            }
        }
        viewModelScope.launch {
            mediaRepository.getLibraryStatsFlow().collect { stats ->
                _uiState.value = _uiState.value.copy(
                    syncedMoviesCount = stats.movies,
                    syncedSeriesCount = stats.series,
                    syncedEpisodesCount = stats.episodes
                )
            }
        }
        viewModelScope.launch {
            MediaRepository.syncProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(isSyncingCatalog = progress.isSyncing)
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    lastSyncTimestamp = prefs.jellyfinLastSync,
                    seasonalAudioEnabled = prefs.seasonalAudioEnabled,
                    themeSongsAudioEnabled = prefs.themeSongsAudioEnabled,
                    navigationSoundEnabled = prefs.navigationSoundEnabled,
                    audioPassthroughMode = prefs.audioPassthroughMode,
                    autoPlayNextEpisodeEnabled = prefs.autoPlayNextEpisodeEnabled,
                    autoPlayNextMovieEnabled = prefs.autoPlayNextMovieEnabled,
                    autoFrameRateEnabled = prefs.autoFrameRateEnabled,
                    subtitleSize = prefs.subtitleSize,
                    subtitleBackground = prefs.subtitleBackground,
                    hideWatched = prefs.hideWatched,
                    nightModeAudio = prefs.nightModeAudio,
                    audioVolumeBoostDb = prefs.audioVolumeBoostDb,
                    jellyfinAllowSelfSigned = prefs.jellyfinAllowSelfSigned,
                    jellyfinCertificateFingerprint = prefs.jellyfinCertificateFingerprint
                )
            }
        }
        checkForAppUpdates()
    }

    private fun loadCurrentPreferences() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val isJfConnected = prefs.jellyfinAccessToken.isNotBlank() && prefs.jellyfinServerUrl.isNotBlank()

            _uiState.value = _uiState.value.copy(
                serverUrl = prefs.jellyfinServerUrl,
                lanServerUrl = prefs.jellyfinLanServerUrl,
                username = prefs.jellyfinUsername,
                password = prefs.jellyfinPassword,
                traktToken = prefs.traktAccessToken,
                traktClientId = prefs.traktClientId,
                traktClientSecret = prefs.traktClientSecret,
                tmdbApiKey = prefs.tmdbApiKey,
                watchRegion = prefs.watchRegion,
                buttonStyle = prefs.buttonStyle,
                accentColor = prefs.accentColor,
                indicatorTheme = prefs.indicatorTheme,
                platformLogoStyle = prefs.platformLogoStyle,
                performanceMode = prefs.performanceMode,
                selectedPlatforms = prefs.selectedPlatforms,
                hiddenTraktLists = prefs.hiddenTraktLists,
                isJellyfinConnected = isJfConnected,
                isTraktConnected = prefs.traktAccessToken.isNotBlank(),
                seasonalAudioEnabled = prefs.seasonalAudioEnabled,
                themeSongsAudioEnabled = prefs.themeSongsAudioEnabled,
                navigationSoundEnabled = prefs.navigationSoundEnabled,
                audioPassthroughMode = prefs.audioPassthroughMode,
                autoPlayNextEpisodeEnabled = prefs.autoPlayNextEpisodeEnabled,
                autoPlayNextMovieEnabled = prefs.autoPlayNextMovieEnabled,
                autoFrameRateEnabled = prefs.autoFrameRateEnabled,
                subtitleSize = prefs.subtitleSize,
                subtitleBackground = prefs.subtitleBackground,
                hideWatched = prefs.hideWatched,
                nightModeAudio = prefs.nightModeAudio,
                audioVolumeBoostDb = prefs.audioVolumeBoostDb,
                jellyfinAllowSelfSigned = prefs.jellyfinAllowSelfSigned,
                jellyfinCertificateFingerprint = prefs.jellyfinCertificateFingerprint
            )
            if (prefs.traktAccessToken.isNotBlank()) {
                validateTraktToken(prefs.traktAccessToken, prefs.traktClientId)
                loadTraktUserLists(prefs.traktAccessToken, prefs.traktClientId)
            }
        }
    }

    fun selectCategory(categoryIndex: Int) {
        _uiState.value = _uiState.value.copy(activeCategory = categoryIndex)
    }

    fun updatePerformanceMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.updatePerformanceMode(mode)
            _uiState.value = _uiState.value.copy(performanceMode = mode)
        }
    }

    fun updateSeasonalAudioEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateSeasonalAudioEnabled(enabled)
            _uiState.value = _uiState.value.copy(seasonalAudioEnabled = enabled)
        }
    }

    fun updateThemeSongsAudioEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateThemeSongsAudioEnabled(enabled)
            _uiState.value = _uiState.value.copy(themeSongsAudioEnabled = enabled)
        }
    }

    fun updateNavigationSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateNavigationSoundEnabled(enabled)
            _uiState.value = _uiState.value.copy(navigationSoundEnabled = enabled)
        }
    }

    fun updateAudioPassthroughMode(mode: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAudioPassthroughMode(mode)
            _uiState.value = _uiState.value.copy(audioPassthroughMode = mode)
        }
    }

    fun updateAutoPlayNextEpisode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateAutoPlayNextEpisode(enabled)
            _uiState.value = _uiState.value.copy(autoPlayNextEpisodeEnabled = enabled)
        }
    }

    fun updateAutoPlayNextMovie(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateAutoPlayNextMovie(enabled)
            _uiState.value = _uiState.value.copy(autoPlayNextMovieEnabled = enabled)
        }
    }

    fun updateAutoFrameRate(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateAutoFrameRate(enabled)
            _uiState.value = _uiState.value.copy(autoFrameRateEnabled = enabled)
        }
    }

    fun updateSubtitleSize(size: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateSubtitleSize(size)
            _uiState.value = _uiState.value.copy(subtitleSize = size)
        }
    }

    fun updateSubtitleBackground(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateSubtitleBackground(enabled)
            _uiState.value = _uiState.value.copy(subtitleBackground = enabled)
        }
    }

    fun updateHideWatched(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHideWatched(enabled)
            _uiState.value = _uiState.value.copy(hideWatched = enabled)
        }
    }

    fun updateNightModeAudio(level: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setNightModeAudio(level)
            _uiState.value = _uiState.value.copy(nightModeAudio = level)
        }
    }

    fun updateAudioVolumeBoost(db: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAudioVolumeBoostDb(db)
            _uiState.value = _uiState.value.copy(audioVolumeBoostDb = db)
        }
    }

    /**
     * Opt-in explícito de certificado autofirmado: descarga el certificado del servidor,
     * calcula su fingerprint SHA-256 y lo fija como pin. No acepta el certificado durante
     * la sonda.
     */
    fun pinSelfSignedCertificate() {
        val serverUrl = _uiState.value.serverUrl
        if (serverUrl.isBlank()) return
        if (_uiState.value.isFetchingCertificateFingerprint) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isFetchingCertificateFingerprint = true,
                statusMessage = context.getString(R.string.settings_tls_fetching)
            )

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    NetworkClientFactory.fetchCertificateFingerprint(serverUrl)
                }
            }

            result.onSuccess { fingerprint ->
                // El pin se asocia a la URL mostrada, aunque todavía no haya sesión: así
                // "Comprobar servidor" usa el host correcto antes de guardar la conexión.
                userPreferencesRepository.updateJellyfinServerUrl(serverUrl)
                userPreferencesRepository.setJellyfinSelfSigned(true, fingerprint)
                NetworkClientFactory.setTrustedServer(
                    serverUrl = serverUrl,
                    allowSelfSigned = true,
                    certificateFingerprintSha256 = fingerprint
                )
                _uiState.value = _uiState.value.copy(
                    isFetchingCertificateFingerprint = false,
                    jellyfinAllowSelfSigned = true,
                    jellyfinCertificateFingerprint = fingerprint,
                    isSuccess = true,
                    statusMessage = context.getString(R.string.settings_tls_pinned)
                )
            }.onFailure { error ->
                Log.e("SettingsViewModel", "No se pudo fijar el certificado autofirmado", error)
                _uiState.value = _uiState.value.copy(
                    isFetchingCertificateFingerprint = false,
                    isSuccess = false,
                    statusMessage = context.getString(
                        R.string.settings_tls_pin_failed,
                        error.message ?: ""
                    )
                )
            }
        }
    }

    fun clearSelfSignedCertificate() {
        viewModelScope.launch {
            userPreferencesRepository.setJellyfinSelfSigned(false, "")
            NetworkClientFactory.setTrustedServer(
                serverUrl = _uiState.value.serverUrl,
                allowSelfSigned = false,
                certificateFingerprintSha256 = null
            )
            _uiState.value = _uiState.value.copy(
                jellyfinAllowSelfSigned = false,
                jellyfinCertificateFingerprint = "",
                isSuccess = true,
                statusMessage = context.getString(R.string.settings_tls_pin_removed)
            )
        }
    }

    fun updateButtonStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateButtonStyle(style)
            _uiState.value = _uiState.value.copy(buttonStyle = style)
        }
    }

    fun updateAccentColor(accent: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateAccentColor(accent)
            _uiState.value = _uiState.value.copy(accentColor = accent)
        }
    }

    fun updateIndicatorTheme(theme: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateIndicatorTheme(theme)
            _uiState.value = _uiState.value.copy(indicatorTheme = theme)
        }
    }

    fun updatePlatformLogoStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.updatePlatformLogoStyle(style)
            _uiState.value = _uiState.value.copy(platformLogoStyle = style)
        }
    }

    fun togglePlatform(platformId: String) {
        val current = _uiState.value.selectedPlatforms
        val updated = if (platformId in current) current - platformId else current + platformId
        _uiState.value = _uiState.value.copy(selectedPlatforms = updated)
        viewModelScope.launch {
            userPreferencesRepository.updateSelectedPlatforms(updated)
        }
    }

    fun loadTraktUserLists(
        token: String = _uiState.value.traktToken,
        clientId: String = _uiState.value.traktClientId
    ) {
        if (token.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTraktLists = true)
            val repository = mediaRepository
            val effectiveClientId = clientId.ifBlank { DEFAULT_TRAKT_CLIENT_ID }
            val listsResult = repository.getTraktUserLists(token, effectiveClientId)
            val lists = listsResult.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                traktUserLists = lists,
                isLoadingTraktLists = false
            )
        }
    }

    fun toggleTraktListVisibility(list: TraktUserListDto) {
        val key = list.ids.trakt?.toString() ?: list.ids.slug ?: list.name
        viewModelScope.launch {
            val updated = userPreferencesRepository.toggleHiddenTraktList(key)
            _uiState.value = _uiState.value.copy(hiddenTraktLists = updated)
            BrandViewModel.clearCache("trakt")
        }
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAppTheme(theme)
            val isMono = theme == APP_THEME_MONOCHROME
            // Respetar el acento elegido (Cian / Ámbar Cine) al volver de Monocromático,
            // en lugar de forzar siempre Cian. Monocromático sigue bloqueando a Blanco.
            val nextAccent = when {
                isMono -> ACCENT_WHITE
                theme == ACCENT_AMBER -> ACCENT_AMBER
                else -> ACCENT_CYAN
            }
            _uiState.value = _uiState.value.copy(
                accentColor = nextAccent,
                indicatorTheme = if (isMono) INDICATOR_THEME_MONOCHROME else INDICATOR_THEME_COLOR,
                platformLogoStyle = if (isMono) PLATFORM_LOGO_MONOCHROME else PLATFORM_LOGO_COLOR
            )
        }
    }

    fun validateTraktToken(
        token: String = _uiState.value.traktToken,
        clientId: String = _uiState.value.traktClientId
    ) {
        if (token.isBlank()) {
            _uiState.value = _uiState.value.copy(isTraktConnected = false, traktUsername = null)
            return
        }
        viewModelScope.launch {
            val repository = mediaRepository
            val effectiveClientId = clientId.ifBlank { DEFAULT_TRAKT_CLIENT_ID }
            val result = repository.getTraktUserProfile(token, effectiveClientId)
            result.onSuccess { user ->
                val handle = user.username ?: user.name ?: context.getString(R.string.settings_status_connected)
                _uiState.value = _uiState.value.copy(
                    isTraktConnected = true,
                    traktUsername = handle,
                    statusMessage = context.getString(R.string.settings_trakt_verified, handle)
                )
                loadTraktUserLists(token, effectiveClientId)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isTraktConnected = true,
                    traktUsername = null,
                    statusMessage = context.getString(R.string.settings_trakt_connected_msg, e.localizedMessage ?: context.getString(R.string.settings_status_active))
                )
            }
        }
    }

    fun openEditDialog(field: SettingField) {
        val currentVal = when (field) {
            SettingField.SERVER_URL -> _uiState.value.serverUrl
            SettingField.USERNAME -> _uiState.value.username
            SettingField.PASSWORD -> _uiState.value.password
            SettingField.TRAKT_CLIENT_ID -> _uiState.value.traktClientId
            SettingField.TRAKT_CLIENT_SECRET -> _uiState.value.traktClientSecret
            SettingField.TRAKT_MANUAL_TOKEN -> _uiState.value.traktToken
            SettingField.WATCH_REGION -> _uiState.value.watchRegion
            SettingField.TMDB_API_KEY -> _uiState.value.tmdbApiKey
        }
        _uiState.value = _uiState.value.copy(
            activeDialogField = field,
            activeDialogValue = currentVal
        )
    }

    fun onDialogValueChange(newValue: String) {
        _uiState.value = _uiState.value.copy(activeDialogValue = newValue)
    }

    fun commitDialogValue() {
        val field = _uiState.value.activeDialogField ?: return
        val value = _uiState.value.activeDialogValue.trim()
        when (field) {
            SettingField.SERVER_URL -> {
                var cleanUrl = value.trimEnd('/')
                if (cleanUrl.isNotBlank() && !cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                    cleanUrl = "http://$cleanUrl"
                }
                // Se persiste y se valida al pulsar "Guardar y conectar"; así no se mezcla
                // el catálogo del servidor anterior con una URL nueva a medio escribir.
                _uiState.value = _uiState.value.copy(serverUrl = cleanUrl)
            }
            SettingField.USERNAME -> {
                _uiState.value = _uiState.value.copy(username = value)
                viewModelScope.launch {
                    userPreferencesRepository.updateJellyfinUsername(value)
                }
            }
            SettingField.PASSWORD -> {
                _uiState.value = _uiState.value.copy(password = value)
                viewModelScope.launch {
                    userPreferencesRepository.updateJellyfinPassword(value)
                }
            }
            SettingField.TRAKT_CLIENT_ID -> {
                _uiState.value = _uiState.value.copy(traktClientId = value)
                viewModelScope.launch {
                    userPreferencesRepository.updateTraktConfig(
                        token = _uiState.value.traktToken,
                        clientId = value,
                        clientSecret = _uiState.value.traktClientSecret
                    )
                }
            }
            SettingField.TRAKT_CLIENT_SECRET -> {
                _uiState.value = _uiState.value.copy(traktClientSecret = value)
                viewModelScope.launch {
                    userPreferencesRepository.updateTraktConfig(
                        token = _uiState.value.traktToken,
                        clientId = _uiState.value.traktClientId,
                        clientSecret = value
                    )
                }
            }
            SettingField.TRAKT_MANUAL_TOKEN -> {
                val isConnected = value.isNotBlank()
                _uiState.value = _uiState.value.copy(
                    traktToken = value,
                    isTraktConnected = isConnected,
                    statusMessage = if (isConnected) context.getString(R.string.settings_saving_trakt_token) else context.getString(R.string.settings_trakt_token_removed)
                )
                viewModelScope.launch {
                    userPreferencesRepository.updateTraktConfig(
                        token = value,
                        clientId = _uiState.value.traktClientId,
                        clientSecret = _uiState.value.traktClientSecret
                    )
                    BrandViewModel.clearCache("trakt")
                    if (isConnected) {
                        validateTraktToken(value, _uiState.value.traktClientId)
                    }
                }
            }
            SettingField.WATCH_REGION -> {
                val region = value.ifBlank { ApiConfig.TMDB_WATCH_REGION }.uppercase()
                _uiState.value = _uiState.value.copy(watchRegion = region)
                viewModelScope.launch {
                    userPreferencesRepository.updateTmdbConfig(
                        apiKey = _uiState.value.tmdbApiKey,
                        region = region
                    )
                }
            }
            SettingField.TMDB_API_KEY -> {
                _uiState.value = _uiState.value.copy(tmdbApiKey = value)
                viewModelScope.launch {
                    userPreferencesRepository.updateTmdbConfig(
                        apiKey = value,
                        region = _uiState.value.watchRegion
                    )
                    MediaCaches.invalidateServerCaches()
                }
            }
        }
        _uiState.value = _uiState.value.copy(activeDialogField = null)
    }

    fun updateWatchRegion(region: String) {
        val clean = region.ifBlank { tv.medusa.app.data.remote.ApiConfig.TMDB_WATCH_REGION }.uppercase()
        _uiState.value = _uiState.value.copy(watchRegion = clean)
        viewModelScope.launch {
            userPreferencesRepository.updateTmdbConfig(
                apiKey = _uiState.value.tmdbApiKey,
                region = clean
            )
        }
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(activeDialogField = null)
    }

    fun onServerUrlChange(url: String) {
        // Solo estado local. La URL se persiste al conectar: si se guardara en cada pulsación,
        // un cambio de servidor podría dejar el catálogo viejo asociado al servidor nuevo.
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    /**
     * Si el servidor o el usuario Jellyfin cambian, borra el catálogo local y todas las cachés
     * antes de guardar las credenciales nuevas. Así no se mezclan títulos, favoritos, progreso
     * ni especiales del servidor anterior.
     */
    private suspend fun clearCatalogIfIdentityChanged(
        newServerUrl: String? = null,
        newUserId: String? = null
    ): Boolean {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val serverChanged = newServerUrl != null &&
            NetworkClientFactory.normalizeUrl(prefs.jellyfinServerUrl) != NetworkClientFactory.normalizeUrl(newServerUrl)
        val userChanged = newUserId != null &&
            prefs.jellyfinUserId.isNotBlank() && prefs.jellyfinUserId != newUserId
        if (!serverChanged && !userChanged) return false

        cancelLibraryWorkers()
        mediaRepository.clearLocalCatalog()
        userPreferencesRepository.clearJellyfinSyncMetadata()
        GetHomeFeedUseCase.invalidateFeedCache()
        HomeViewModel.invalidateStaticCaches()
        GenreViewModel.clearAllCaches()
        BrandViewModel.clearCache()
        GetHomeFeedUseCase.clearMemorySnapshot()
        runCatching { HomeSnapshotFileStore(context).clear() }
        runCatching { database.collectionDao().clearAll() }
        return true
    }

    private fun cancelLibraryWorkers() {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(JellyfinSyncWorker.WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(OverviewEnrichmentWorker.WORK_NAME)
        }
    }

    private suspend fun clearAllLocalCatalog() {
        cancelLibraryWorkers()
        mediaRepository.clearLocalCatalog()
        userPreferencesRepository.clearJellyfinSyncMetadata()
        GetHomeFeedUseCase.invalidateFeedCache()
        HomeViewModel.invalidateStaticCaches()
        GenreViewModel.clearAllCaches()
        BrandViewModel.clearCache()
        GetHomeFeedUseCase.clearMemorySnapshot()
        runCatching { HomeSnapshotFileStore(context).clear() }
        runCatching { database.collectionDao().clearAll() }
    }

    fun disconnectJellyfin() {
        jellyfinQuickConnectJob?.cancel()
        viewModelScope.launch {
            clearAllLocalCatalog()
            userPreferencesRepository.updateJellyfinConfig(
                serverUrl = "",
                userId = "",
                accessToken = "",
                username = "",
                password = ""
            )
            // No dejar credenciales descifradas en memoria tras cerrar sesión.
            tv.medusa.app.util.CryptoUtils.clearCache()
            _uiState.value = _uiState.value.copy(
                isJellyfinConnected = false,
                serverUrl = "",
                username = "",
                password = "",
                serverName = null,
                serverVersion = null,
                isSuccess = false,
                discoveredServers = emptyList(),
                statusMessage = context.getString(R.string.settings_jellyfin_session_closed)
            )
        }
    }

    /**
     * Busca servidores Jellyfin en la red local mediante el broadcast UDP del puerto 7359.
     * Los resultados se muestran en ajustes para rellenar la URL con un toque.
     */
    fun discoverJellyfinServers() {
        if (_uiState.value.isDiscoveringServers) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDiscoveringServers = true,
                discoveredServers = emptyList(),
                statusMessage = context.getString(R.string.settings_discovering_servers)
            )

            val servers = JellyfinServerDiscovery.discover()

            _uiState.value = _uiState.value.copy(
                isDiscoveringServers = false,
                discoveredServers = servers,
                statusMessage = when {
                    servers.isEmpty() -> context.getString(R.string.settings_no_servers_found)
                    else -> context.resources.getQuantityString(R.plurals.settings_servers_found, servers.size, servers.size)
                }
            )
        }
    }

    /** Rellena la URL con el servidor elegido y comprueba la conexión. */
    fun selectDiscoveredServer(server: DiscoveredJellyfinServer) {
        _uiState.value = _uiState.value.copy(
            serverUrl = server.address,
            discoveredServers = emptyList()
        )
        // Si todavía no hay sesión, se persiste la URL al validar el servidor: así la
        // elección sobrevive a un reinicio de la app antes de terminar el inicio de sesión.
        testServerConnection(persistUrlIfDisconnected = true)
    }

    fun disconnectTrakt() {
        traktPollJob?.cancel()
        viewModelScope.launch {
            userPreferencesRepository.updateTraktConfig("", _uiState.value.traktClientId, _uiState.value.traktClientSecret)
            BrandViewModel.clearCache("trakt")
            _uiState.value = _uiState.value.copy(
                isTraktConnected = false,
                traktToken = "",
                traktUsername = null,
                traktUserCode = null,
                traktQrPayload = null,
                isWaitingTraktAuth = false,
                traktUserLists = emptyList(),
                statusMessage = context.getString(R.string.settings_trakt_unlinked)
            )
        }
    }

    fun startTraktDeviceFlow() {
        traktPollJob?.cancel()
        traktPollJob = viewModelScope.launch {
            val clientId = _uiState.value.traktClientId.trim().ifBlank { DEFAULT_TRAKT_CLIENT_ID }
            val clientSecret = _uiState.value.traktClientSecret.trim()

            _uiState.value = _uiState.value.copy(
                isWaitingTraktAuth = true,
                statusMessage = context.getString(R.string.settings_requesting_trakt_code)
            )
            try {
                val traktApi = NetworkClientFactory.createService(ApiConfig.TRAKT_BASE_URL, TraktApiService::class.java)
                val codeResponse = traktApi.getDeviceCode(
                    apiVersion = "2",
                    clientIdHeader = clientId,
                    request = TraktDeviceCodeRequest(clientId = clientId)
                )

                val verifyUrl = ApiConfig.TRAKT_ACTIVATE_URL
                // El payload se codifica como QR en el dispositivo; nunca se envía a un tercero.
                val qrPayload = verifyUrl

                _uiState.value = _uiState.value.copy(
                    traktUserCode = codeResponse.userCode,
                    traktQrPayload = qrPayload,
                    statusMessage = context.getString(R.string.settings_scan_qr)
                )

                val safeInterval = codeResponse.interval.coerceIn(5, 60)
                var intervalMs = safeInterval * 1000L
        val maxAttempts = (codeResponse.expiresIn / safeInterval).coerceAtLeast(10)

                for (i in 0 until maxAttempts) {
                    delay(intervalMs)
                    try {
                        val tokenResponse = traktApi.pollDeviceToken(
                            apiVersion = "2",
                            clientIdHeader = clientId,
                            request = TraktDeviceTokenRequest(
                                code = codeResponse.deviceCode,
                                clientId = clientId,
                                clientSecret = clientSecret
                            )
                        )
                        if (tokenResponse.accessToken.isNotBlank()) {
                            userPreferencesRepository.updateTraktConfig(
                                token = tokenResponse.accessToken,
                                clientId = clientId,
                                clientSecret = clientSecret
                            )
                            BrandViewModel.clearCache("trakt")
                            _uiState.value = _uiState.value.copy(
                                isWaitingTraktAuth = false,
                                isTraktConnected = true,
                                traktToken = tokenResponse.accessToken,
                                statusMessage = context.getString(R.string.settings_trakt_linked)
                            )
                            validateTraktToken(tokenResponse.accessToken, clientId)
                            loadTraktUserLists(tokenResponse.accessToken, clientId)
                            break
                        }
                    } catch (e: Throwable) {
                        if (e is retrofit2.HttpException) {
                            val code = e.code()
                            val errBody = runCatching { e.response()?.errorBody()?.string() ?: "" }.getOrDefault("")
                            if (code == 400) {
                                if (errBody.contains("slow_down")) {
                                    intervalMs += 2000L
                                } else if (errBody.contains("authorization_pending")) {
                                    continue
                                } else if (errBody.contains("expired_token") || errBody.contains("invalid_grant")) {
                                    _uiState.value = _uiState.value.copy(
                                        isWaitingTraktAuth = false,
                                        statusMessage = context.getString(R.string.settings_trakt_code_expired)
                                    )
                                    break
                                }
                            } else if (code == 401 || code == 403) {
                                _uiState.value = _uiState.value.copy(
                                    isWaitingTraktAuth = false,
                                    statusMessage = context.getString(R.string.settings_trakt_invalid_credentials)
                                )
                                break
                            } else if (code == 404 || code == 410 || code == 418) {
                                _uiState.value = _uiState.value.copy(
                                    isWaitingTraktAuth = false,
                                    statusMessage = context.getString(R.string.settings_trakt_cancelled)
                                )
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val errorDetails = if (e is retrofit2.HttpException && e.code() == 401) {
                    context.getString(R.string.settings_trakt_client_id_not_found)
                } else {
                    context.getString(R.string.settings_trakt_link_error, e.localizedMessage)
                }
                _uiState.value = _uiState.value.copy(
                    isWaitingTraktAuth = false,
                    statusMessage = errorDetails
                )
            }
        }
    }

    fun cancelTraktQuickConnect() {
        traktPollJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isWaitingTraktAuth = false,
            traktUserCode = null,
            traktQrPayload = null
        )
    }

    private fun formatNetworkError(e: Throwable, cleanUrl: String): String {
        val msg = e.localizedMessage ?: ""
        val isHtmlInsteadOfJson = msg.contains("Expected start of the object", ignoreCase = true)
                || msg.contains("<!DOCTYPE", ignoreCase = true)
                || msg.contains("Unexpected JSON token", ignoreCase = true)

        if (isHtmlInsteadOfJson) {
            return context.getString(R.string.settings_error_html_not_jellyfin)
        }

        return when (e) {
            is retrofit2.HttpException -> {
                when (e.code()) {
                    400 -> context.getString(R.string.settings_error_400)
                    401 -> context.getString(R.string.settings_error_401)
                    403 -> context.getString(R.string.settings_error_403)
                    404 -> context.getString(R.string.settings_error_404, cleanUrl)
                    500, 502, 503 -> context.getString(R.string.settings_error_5xx, e.code())
                    else -> context.getString(R.string.settings_error_http, e.code())
                }
            }
            is ConnectException -> context.getString(R.string.settings_error_connect, cleanUrl)
            is SocketTimeoutException -> context.getString(R.string.settings_error_timeout, cleanUrl)
            is UnknownHostException -> context.getString(R.string.settings_error_unknown_host, cleanUrl)
            is javax.net.ssl.SSLHandshakeException -> context.getString(R.string.settings_error_ssl)
            else -> context.getString(R.string.settings_error_connection)
        }
    }

    fun testServerConnection(persistUrlIfDisconnected: Boolean = false) {
        val raw = _uiState.value.serverUrl
        val cleanUrl = NetworkClientFactory.normalizeUrl(raw)
        if (cleanUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = context.getString(R.string.settings_enter_server_url_first))
            return
        }

        _uiState.value = _uiState.value.copy(
            serverUrl = cleanUrl,
            isLoading = true,
            discoveredServers = emptyList(),
            statusMessage = context.getString(R.string.settings_testing_connection, cleanUrl)
        )

        viewModelScope.launch {
            val api = NetworkClientFactory.createJellyfinService(cleanUrl, JellyfinApiService::class.java)
            try {
                val info = api.getPublicInfo()
                val serverName = info.effectiveServerName
                val version = info.effectiveVersion
                val isOutdated = isServerVersionOutdated(version)
                val statusText = if (isOutdated) {
                    "Aviso: Servidor Jellyfin $serverName ($version) antiguo. Se recomienda 10.7+ para compatibilidad completa."
                } else {
                    context.getString(R.string.settings_server_detected, serverName, version)
                }
                if (persistUrlIfDisconnected && !_uiState.value.isJellyfinConnected) {
                    userPreferencesRepository.updateJellyfinServerUrl(cleanUrl)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    serverName = serverName,
                    serverVersion = version,
                    statusMessage = statusText
                )
            } catch (e: Exception) {
                val details = formatNetworkError(e, cleanUrl)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = details
                )
            }
        }
    }

    private fun isServerVersionOutdated(versionStr: String): Boolean {
        if (versionStr.isBlank()) return false
        val clean = versionStr.filter { it.isDigit() || it == '.' }
        val parts = clean.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return false
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        return major < 10 || (major == 10 && minor < 7)
    }

    fun startJellyfinQuickConnect() {
        val raw = _uiState.value.serverUrl
        val cleanUrl = NetworkClientFactory.normalizeUrl(raw)
        if (cleanUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = context.getString(R.string.settings_enter_jellyfin_url))
            return
        }
        _uiState.value = _uiState.value.copy(serverUrl = cleanUrl)

        jellyfinQuickConnectJob?.cancel()
        jellyfinQuickConnectJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isWaitingJellyfinQuickConnect = true,
                jellyfinQuickConnectCode = null,
                jellyfinQrPayload = null,
                statusMessage = context.getString(R.string.settings_starting_quick_connect)
            )

            val api = NetworkClientFactory.createJellyfinService(cleanUrl, JellyfinApiService::class.java)
            val authHeader = tv.medusa.app.data.repository.buildJellyfinAuthHeader(token = "")

            try {
                val initResult = api.initiateQuickConnect(authHeader = authHeader)
                val detectedVersion = runCatching { api.getPublicInfo(authHeader).effectiveVersion }
                    .getOrDefault("")

                val webQuickConnectUrl = buildQuickConnectWebUrl(cleanUrl, detectedVersion, initResult.effectiveCode)

                _uiState.value = _uiState.value.copy(
                    jellyfinQuickConnectCode = initResult.effectiveCode,
                    jellyfinQrPayload = webQuickConnectUrl,
                    statusMessage = context.getString(R.string.settings_qc_code, initResult.effectiveCode)
                )

                var authorized = false
                var authFailure: String? = null
                // Si el servidor deja de responder varios ciclos seguidos (caído, bloqueado
                // o inaccesible) se corta en vez de esperar los 9 minutos completos.
                val maxCheckFailures = 5
                var checkFailures = 0
                for (i in 0 until 180) {
                    delay(3000L)

                    var check: JellyfinQuickConnectResult? = null
                    try {
                        check = api.checkQuickConnect(authHeader = authHeader, secret = initResult.effectiveSecret)
                        checkFailures = 0
                    } catch (e: Exception) {
                        // Normalmente solo significa que el usuario aún no ha autorizado.
                        checkFailures++
                        if (checkFailures >= maxCheckFailures) {
                            authFailure = formatNetworkError(e, cleanUrl)
                            break
                        }
                        continue
                    }
                    if (check == null || !check.isAuthed) continue

                    // `QuickConnect/Connect` solo marca `Authenticated`: el token hay que
                    // canjearlo aparte. El secreto es de un solo uso, así que si el canje
                    // falla no se puede reintentar con el mismo código.
                    val authResult = try {
                        api.authenticateWithQuickConnect(
                            request = JellyfinQuickConnectAuthRequest(secret = initResult.effectiveSecret)
                        )
                    } catch (e: Exception) {
                        authFailure = formatNetworkError(e, cleanUrl)
                        break
                    }

                    val token = authResult.effectiveToken
                    if (token.isBlank()) {
                        authFailure = context.getString(R.string.settings_error_unknown)
                        break
                    }

                    authorized = true
                    val authedHeader = tv.medusa.app.data.repository.buildJellyfinAuthHeader(token = token)
                    try {
                        val me = api.getCurrentUser(authedHeader)

                        val info = runCatching { api.getPublicInfo(authedHeader) }.getOrNull()
                        val serverId = info?.effectiveId ?: ""
                        val serverVersion = info?.effectiveVersion ?: ""

                        // 1. Si es otro servidor/usuario, borrar catálogo y cachés antes
                        // de guardar las credenciales nuevas.
                        clearCatalogIfIdentityChanged(cleanUrl, me.effectiveId)
                        userPreferencesRepository.updateJellyfinConfig(
                            serverUrl = cleanUrl,
                            userId = me.effectiveId,
                            accessToken = token,
                            username = me.effectiveName,
                            serverId = serverId,
                            serverVersion = serverVersion
                        )

                        // Registrar capacidades del dispositivo en Jellyfin
                        runCatching {
                            val caps = tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder.createDefaultCapabilities(context)
                            api.postCapabilities(authHeader = authedHeader, capabilities = caps)
                        }

                        _uiState.value = _uiState.value.copy(
                            isWaitingJellyfinQuickConnect = false,
                            username = me.effectiveName,
                            isJellyfinConnected = true,
                            statusMessage = context.getString(R.string.settings_authorized_as, me.effectiveName)
                        )

                        val repository = mediaRepository
                        // 2. Sincronizar catálogo en segundo plano (no se cancela al salir de la pantalla)
                        repository.startBackgroundSync(
                            serverUrl = cleanUrl,
                            userId = me.effectiveId,
                            token = token,
                            forceFullSync = true
                        )
                        val count = repository.getLocalCount()

                        _uiState.value = _uiState.value.copy(
                            isSuccess = true,
                            isLoading = false,
                            statusMessage = context.resources.getQuantityString(R.plurals.settings_sync_started, count, count)
                        )
                    } catch (e: Exception) {
                        authorized = false
                        authFailure = formatNetworkError(e, cleanUrl)
                    }
                    break
                }

                if (!authorized) {
                    _uiState.value = _uiState.value.copy(
                        isWaitingJellyfinQuickConnect = false,
                        jellyfinQuickConnectCode = null,
                        jellyfinQrPayload = null,
                        statusMessage = authFailure?.let {
                            context.getString(R.string.settings_quick_connect_token_error, it)
                        } ?: context.getString(R.string.settings_quick_connect_expired)
                    )
                }
            } catch (e: Exception) {
                val details = formatNetworkError(e, cleanUrl)
                _uiState.value = _uiState.value.copy(
                    isWaitingJellyfinQuickConnect = false,
                    statusMessage = details
                )
            }
        }
    }

    fun cancelJellyfinQuickConnect() {
        jellyfinQuickConnectJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isWaitingJellyfinQuickConnect = false,
            jellyfinQuickConnectCode = null,
            jellyfinQrPayload = null,
            statusMessage = null
        )
    }

    /**
     * URL de la página de Quick Connect de la web de Jellyfin.
     *
     * Jellyfin 10.9+ usa el router nuevo (`#/quickconnect`) y admite `?code=` para
     * prerrellenar el código, así que el QR sirve tanto de atajo como de ayuda para
     * teclearlo. Las versiones 10.8 y anteriores usaban el router antiguo (`#!`), cuyo
     * alias de la página de usuario es `mypreferencesquickconnect.html`.
     */
    private fun buildQuickConnectWebUrl(
        serverUrl: String,
        serverVersion: String,
        code: String
    ): String {
        val base = serverUrl.trimEnd('/')
        val parts = serverVersion
            .filter { it.isDigit() || it == '.' }
            .split('.')
            .mapNotNull { it.toIntOrNull() }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        // Sin versión conocida asumimos la web moderna (el caso habitual hoy).
        val isModernWeb = serverVersion.isBlank() || major > 10 || (major == 10 && minor >= 9)

        return if (isModernWeb) {
            val query = if (code.isBlank()) "" else "?code=" + URLEncoder.encode(code, "UTF-8")
            "$base/web/index.html#/quickconnect$query"
        } else {
            "$base/web/index.html#!/mypreferencesquickconnect.html"
        }
    }

    fun saveAndConnect() {
        viewModelScope.launch {
            val state = _uiState.value
            val cleanUrl = NetworkClientFactory.normalizeUrl(state.serverUrl)
            _uiState.value = state.copy(serverUrl = cleanUrl)

            val currentPrefs = userPreferencesRepository.userPreferencesFlow.first()
            val effectiveUsername = state.username.ifBlank { currentPrefs.jellyfinUsername }.trim()
            val effectivePassword = state.password.ifBlank { currentPrefs.jellyfinPassword }

            if (cleanUrl.isBlank() || effectiveUsername.isBlank()) {
                _uiState.value = state.copy(statusMessage = context.getString(R.string.settings_enter_url_and_user))
                return@launch
            }

            _uiState.value = state.copy(isLoading = true, statusMessage = context.getString(R.string.settings_connecting_with, cleanUrl))

            val api = NetworkClientFactory.createJellyfinService(cleanUrl, JellyfinApiService::class.java)
            val authHeader = tv.medusa.app.data.repository.buildJellyfinAuthHeader(token = "")

            var detectedServerId = ""
            var detectedServerVersion = ""
            try {
                val info = api.getPublicInfo()
                val serverName = info.effectiveServerName
                detectedServerId = info.effectiveId
                detectedServerVersion = info.effectiveVersion
                _uiState.value = state.copy(isLoading = true, statusMessage = context.getString(R.string.settings_server_authenticating, serverName))
            } catch (_: Exception) {
                _uiState.value = state.copy(isLoading = true, statusMessage = context.getString(R.string.settings_authenticating_user, effectiveUsername))
            }

            try {
                val response = api.authenticateByName(
                    authHeader = authHeader,
                    request = JellyfinAuthRequest(username = effectiveUsername, pw = effectivePassword)
                )

                val userId = response.effectiveUser.effectiveId
                val token = response.effectiveToken
                val authedUser = response.effectiveUser.effectiveName.ifBlank { effectiveUsername }

                // 1. Si es otro servidor/usuario, borrar catálogo y cachés antes de guardar.
                clearCatalogIfIdentityChanged(cleanUrl, userId)

                // 2. Guardar credenciales inmediatamente en DataStore (una sola escritura)
                userPreferencesRepository.saveConnectionConfig(
                    serverUrl = cleanUrl,
                    userId = userId,
                    accessToken = token,
                    username = authedUser,
                    password = effectivePassword,
                    serverId = detectedServerId,
                    serverVersion = detectedServerVersion,
                    traktToken = state.traktToken,
                    traktClientId = state.traktClientId,
                    traktClientSecret = state.traktClientSecret,
                    tmdbApiKey = state.tmdbApiKey,
                    watchRegion = state.watchRegion
                )

                // Registrar capacidades del dispositivo en Jellyfin
                runCatching {
                    val authedHeader = tv.medusa.app.data.repository.buildJellyfinAuthHeader(token = token)
                    val caps = tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder.createDefaultCapabilities(context)
                    api.postCapabilities(authHeader = authedHeader, capabilities = caps)
                    Log.i("SettingsViewModel", "Registered Jellyfin session capabilities after login")
                }

                val repository = mediaRepository

                // 3. Sincronizar catálogo en segundo plano en syncScope (no se cancela al salir de Ajustes)
                repository.startBackgroundSync(
                    serverUrl = cleanUrl,
                    userId = userId,
                    token = token,
                    forceFullSync = true
                )

                val count = repository.getLocalCount()
                _uiState.value = state.copy(
                    isLoading = false,
                    isSuccess = true,
                    username = authedUser,
                    isJellyfinConnected = true,
                    statusMessage = context.getString(R.string.settings_connected_as, authedUser)
                )
            } catch (e: Exception) {
                val details = formatNetworkError(e, cleanUrl)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = details
                )
            }
        }
    }

    fun forceSyncCatalog() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (prefs.jellyfinServerUrl.isBlank() || prefs.jellyfinUserId.isBlank() || prefs.jellyfinAccessToken.isBlank()) {
                _uiState.value = _uiState.value.copy(statusMessage = context.getString(R.string.settings_connect_first))
                return@launch
            }
            mediaRepository.startBackgroundSync(
                serverUrl = prefs.jellyfinServerUrl,
                userId = prefs.jellyfinUserId,
                token = prefs.jellyfinAccessToken,
                forceFullSync = true
            )
            _uiState.value = _uiState.value.copy(statusMessage = context.getString(R.string.settings_full_sync_started))
        }
    }

    fun setUpdateChannel(channel: String) {
        appUpdateManager.setChannel(channel)
        _uiState.value = _uiState.value.copy(
            updateChannel = appUpdateManager.currentChannel(),
            updateInfo = null
        )
        checkForAppUpdates()
    }

    fun checkForAppUpdates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdate = true, statusMessage = context.getString(R.string.settings_checking_updates))
            val result = appUpdateManager.checkForUpdates()
            result.onSuccess { info ->
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    updateInfo = info,
                    statusMessage = if (info.hasUpdate) context.getString(R.string.settings_update_available_toast, info.latestVersion) else context.getString(R.string.settings_up_to_date_toast, info.currentVersion)
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    statusMessage = context.getString(R.string.settings_check_failed, e.localizedMessage ?: context.getString(R.string.settings_error_connection_fallback))
                )
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val info = _uiState.value.updateInfo ?: return
        val apkUrl = info.apkUrl ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusMessage = context.getString(R.string.settings_downloading_update))
            val result = appUpdateManager.downloadAndInstall(apkUrl, info.apkSha256) { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)
            }
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    downloadProgress = null,
                    statusMessage = e.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.settings_error_unknown)
                )
            }
        }
    }
}
