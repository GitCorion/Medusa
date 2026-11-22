package tv.medusa.app.data.local

import java.util.Locale
import tv.medusa.app.BuildConfig
import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.util.DeviceUtils
import tv.medusa.app.util.ServerVerificationUtils
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.medusa.app.util.CryptoUtils

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "medusa_settings")

// Claves por defecto para TMDB/Trakt. Provienen de BuildConfig (inyectables en build-time vía
// env/keystore.properties); ya no hay literales hardcodeados en el código fuente.
/**
 * Clave de TMDB. En la distribución pública no hay proxy ni clave embebida: el usuario
 * introduce su propia clave en Ajustes. El BuildConfig solo permite una inyección opcional
 * en builds propios; si está vacía, las funciones que dependen de TMDB se desactivan.
 */
val DEFAULT_TMDB_API_KEY: String = BuildConfig.DEFAULT_TMDB_API_KEY
val DEFAULT_TRAKT_CLIENT_ID: String = BuildConfig.DEFAULT_TRAKT_CLIENT_ID
val DEFAULT_TRAKT_CLIENT_SECRET: String = BuildConfig.DEFAULT_TRAKT_CLIENT_SECRET

const val BUTTON_STYLE_ICONS_ONLY = "ICONS_ONLY"
const val BUTTON_STYLE_ICONS_AND_TEXT = "ICONS_AND_TEXT"
const val BUTTON_STYLE_TEXT_ONLY = "TEXT_ONLY"

const val ACCENT_CYAN = "CYAN"
const val ACCENT_AMBER = "AMBER"
const val ACCENT_WHITE = "WHITE"

const val INDICATOR_THEME_COLOR = "COLOR"
const val INDICATOR_THEME_MONOCHROME = "MONOCHROME"

const val PLATFORM_LOGO_COLOR = "COLOR"
const val PLATFORM_LOGO_MONOCHROME = "MONOCHROME"

const val APP_THEME_ORIGINAL = "ORIGINAL"
const val APP_THEME_MONOCHROME = "MONOCHROME"

const val PERFORMANCE_MODE_AUTO = "AUTO"
const val PERFORMANCE_MODE_HIGH = "HIGH"
const val PERFORMANCE_MODE_LOW = "LOW"

const val SUBTITLE_SIZE_SMALL = "SMALL"
const val SUBTITLE_SIZE_NORMAL = "NORMAL"
const val SUBTITLE_SIZE_LARGE = "LARGE"

const val AUDIO_PASSTHROUGH_OFF = 0       // Decodificación PCM (Máxima compatibilidad sin silencios)
const val AUDIO_PASSTHROUGH_AUTO = 1      // Detección automática según capacidades HDMI
const val AUDIO_PASSTHROUGH_DIRECT = 2    // Passthrough directo HDMI (Bitstream receptor AV)

data class UserPreferences(
    val jellyfinServerUrl: String = "",
    val jellyfinLanServerUrl: String = "",
    val jellyfinUserId: String = "",
    val jellyfinUsername: String = "",
    val jellyfinPassword: String = "",
    val jellyfinAccessToken: String = "",
    val jellyfinServerId: String = "",
    val jellyfinServerVersion: String = "",
    /** Opt-in explícito para aceptar el certificado autofirmado del servidor configurado. */
    val jellyfinAllowSelfSigned: Boolean = false,
    /** Fingerprint SHA-256 fijado del certificado autofirmado (vacío si no hay pin). */
    val jellyfinCertificateFingerprint: String = "",
    val traktAccessToken: String = "",
    val traktClientId: String = DEFAULT_TRAKT_CLIENT_ID,
    val traktClientSecret: String = DEFAULT_TRAKT_CLIENT_SECRET,
    val tmdbApiKey: String = DEFAULT_TMDB_API_KEY,
    val watchRegion: String = ApiConfig.TMDB_WATCH_REGION,
    val buttonStyle: String = BUTTON_STYLE_ICONS_ONLY,
    val accentColor: String = ACCENT_CYAN,
    val indicatorTheme: String = INDICATOR_THEME_COLOR,
    val platformLogoStyle: String = PLATFORM_LOGO_COLOR,
    val performanceMode: String = PERFORMANCE_MODE_AUTO,
    val jellyfinLastSync: String = "",
    val selectedPlatforms: Set<String> = emptySet(),
    val hiddenTraktLists: Set<String> = emptySet(),
    val hasCompletedOnboarding: Boolean = true,
    val isProUser: Boolean = true,
    val hasPurchasedPro: Boolean = true,
    val isFamilyVipUser: Boolean = false,
    val isTrialActive: Boolean = false,
    val trialDaysRemaining: Int = 0,
    val nightModeAudio: Int = 0,
    val audioVolumeBoostDb: Int = 0,
    val subtitleSize: String = SUBTITLE_SIZE_NORMAL,
    val subtitleBackground: Boolean = false,
    val autoFrameRateEnabled: Boolean = false,
    val seasonalAudioEnabled: Boolean = true,
    val themeSongsAudioEnabled: Boolean = true,
    val navigationSoundEnabled: Boolean = true,
    val audioPassthroughMode: Int = AUDIO_PASSTHROUGH_OFF,
    val hideWatched: Boolean = false,
    val autoPlayNextMovieEnabled: Boolean = true,
    val autoPlayNextEpisodeEnabled: Boolean = true
) {
    val isMonochrome: Boolean
        get() = indicatorTheme == INDICATOR_THEME_MONOCHROME || accentColor == ACCENT_WHITE

    fun getEffectiveParticleScale(context: Context): Float {
        return when (performanceMode) {
            PERFORMANCE_MODE_HIGH -> 1.0f
            PERFORMANCE_MODE_LOW -> 0.0f
            else -> DeviceUtils.getRecommendedParticleScale(context)
        }
    }

    fun isBackdropEnabled(context: Context): Boolean {
        return when (performanceMode) {
            PERFORMANCE_MODE_HIGH -> true
            PERFORMANCE_MODE_LOW -> false
            else -> !DeviceUtils.isLowResourceDevice(context)
        }
    }
}

class UserPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext

    /** Contexto de aplicación (sin riesgo de leak) para acceder a ficheros, etc. */
    val applicationContext: Context get() = appContext

    private object Keys {
        val JELLYFIN_SERVER_URL = stringPreferencesKey("jellyfin_server_url")
        val JELLYFIN_LAN_SERVER_URL = stringPreferencesKey("jellyfin_lan_server_url")
        val JELLYFIN_USER_ID = stringPreferencesKey("jellyfin_user_id")
        val JELLYFIN_USERNAME = stringPreferencesKey("jellyfin_username")
        val JELLYFIN_PASSWORD = stringPreferencesKey("jellyfin_password")
        val JELLYFIN_ACCESS_TOKEN = stringPreferencesKey("jellyfin_access_token")
        val JELLYFIN_SERVER_ID = stringPreferencesKey("jellyfin_server_id")
        val JELLYFIN_SERVER_VERSION = stringPreferencesKey("jellyfin_server_version")
        val JELLYFIN_ALLOW_SELF_SIGNED = booleanPreferencesKey("jellyfin_allow_self_signed")
        val JELLYFIN_CERT_FINGERPRINT = stringPreferencesKey("jellyfin_cert_fingerprint")
        val TRAKT_ACCESS_TOKEN = stringPreferencesKey("trakt_access_token")
        val TRAKT_CLIENT_ID = stringPreferencesKey("trakt_client_id")
        val TRAKT_CLIENT_SECRET = stringPreferencesKey("trakt_client_secret")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val WATCH_REGION = stringPreferencesKey("watch_region")
        val BUTTON_STYLE = stringPreferencesKey("button_style")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val INDICATOR_THEME = stringPreferencesKey("indicator_theme")
        val PLATFORM_LOGO_STYLE = stringPreferencesKey("platform_logo_style")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val JELLYFIN_LAST_SYNC = stringPreferencesKey("jellyfin_last_sync")
        val JELLYFIN_SYNC_CHECKPOINT_VIEW_INDEX = intPreferencesKey("jellyfin_sync_checkpoint_view_index")
        val JELLYFIN_SYNC_CHECKPOINT_OFFSET = intPreferencesKey("jellyfin_sync_checkpoint_offset")
        val JELLYFIN_SYNC_CHECKPOINT_TOTAL = intPreferencesKey("jellyfin_sync_checkpoint_total")
        val SELECTED_PLATFORMS = stringPreferencesKey("selected_platforms")
        val HIDDEN_TRAKT_LISTS = stringPreferencesKey("hidden_trakt_lists")
        val HAS_COMPLETED_ONBOARDING = stringPreferencesKey("has_completed_onboarding")
        // Estado heredado de la antigua licencia de pago. La distribución pública no factura:
        // se mantiene la clave original "is_pro_user" para no invalidar instalaciones existentes.
        val HAS_PURCHASED_PRO = stringPreferencesKey("is_pro_user")
        // Desbloqueo por código promocional, independiente de la compra.
        val HAS_PROMO_UNLOCK = stringPreferencesKey("has_promo_unlock")
        // Desbloqueo Pro en builds DEBUG (para probar sin pagar). Desactivable en runtime
        // para testear el flujo no-Pro. Solo se lee cuando BuildConfig.DEBUG es true.
        val DEBUG_PRO_OVERRIDE = stringPreferencesKey("debug_pro_override")
        val FIRST_INSTALL_TIME = longPreferencesKey("first_install_time_ms")
        val NIGHT_MODE_AUDIO = intPreferencesKey("night_mode_audio")
        val AUDIO_VOLUME_BOOST_DB = intPreferencesKey("audio_volume_boost_db")
        val SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
        val SUBTITLE_BACKGROUND = stringPreferencesKey("subtitle_background")
        val AUTO_FRAME_RATE = stringPreferencesKey("auto_frame_rate")
        val SEASONAL_AUDIO_ENABLED = stringPreferencesKey("seasonal_audio_enabled")
        val THEME_SONGS_AUDIO_ENABLED = stringPreferencesKey("theme_songs_audio_enabled")
        val NAVIGATION_SOUND_ENABLED = stringPreferencesKey("navigation_sound_enabled")
        val AUDIO_PASSTHROUGH_MODE = intPreferencesKey("audio_passthrough_mode")
        val HIDE_WATCHED = stringPreferencesKey("hide_watched")
        val AUTO_PLAY_NEXT_MOVIE = stringPreferencesKey("auto_play_next_movie")
        val AUTO_PLAY_NEXT_EPISODE = stringPreferencesKey("auto_play_next_episode")
        // Versión del algoritmo de sincronización aplicada. Si la almacenada es menor que la
        // actual, se fuerza un sync completo para reconstruir catálogos truncados por versiones
        // anteriores (p. ej. paginación que cortaba al recibir páginas más cortas que el límite).
        val SYNC_ALGORITHM_VERSION = intPreferencesKey("sync_algorithm_version")
    }

    /**
     * Flujo único de preferencias compartido por toda la app.
     *
     * Antes cada ViewModel (y cada uso de `userPreferencesFlow.first()`) creaba su propia
     * suscripción al DataStore. Ahora hay una sola colección compartida a nivel de aplicación.
     */
    val userPreferences: StateFlow<UserPreferences> = prefsStateFlow(appContext)

    /** Alias de compatibilidad: mismo [StateFlow] compartido, expuesto como [Flow]. */
    val userPreferencesFlow: Flow<UserPreferences> = userPreferences

    init {
        appScope.launch {
            appContext.dataStore.edit { prefs ->
                if ((prefs[Keys.FIRST_INSTALL_TIME] ?: 0L) <= 0L) {
                    val osTime = runCatching {
                        appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime
                    }.getOrDefault(System.currentTimeMillis())
                    prefs[Keys.FIRST_INSTALL_TIME] = if (osTime > 0L) osTime else System.currentTimeMillis()
                }
            }
        }
        appScope.launch {
            userPreferences.collect { prefs ->
                tv.medusa.app.data.remote.NetworkClientFactory.setTrustedServer(
                    serverUrl = prefs.jellyfinServerUrl,
                    allowSelfSigned = prefs.jellyfinAllowSelfSigned,
                    certificateFingerprintSha256 = prefs.jellyfinCertificateFingerprint
                )
            }
        }
    }

    val isProUser: StateFlow<Boolean> =
        userPreferences.map { it.isProUser }.stateIn(appScope, SharingStarted.Eagerly, true)

    val isTrialActive: StateFlow<Boolean> =
        userPreferences.map { it.isTrialActive }.stateIn(appScope, SharingStarted.Eagerly, false)

    val trialDaysRemaining: StateFlow<Int> =
        userPreferences.map { it.trialDaysRemaining }.stateIn(appScope, SharingStarted.Eagerly, 0)

    val isFamilyVipUser: StateFlow<Boolean> =
        userPreferences.map { it.isFamilyVipUser }.stateIn(appScope, SharingStarted.Eagerly, false)

    val accentColor: StateFlow<String> =
        userPreferences.map { it.accentColor }.stateIn(appScope, SharingStarted.Eagerly, ACCENT_CYAN)

    val isMonochrome: StateFlow<Boolean> =
        userPreferences.map { it.isMonochrome }.stateIn(appScope, SharingStarted.Eagerly, false)

    val buttonStyle: StateFlow<String> =
        userPreferences.map { it.buttonStyle }.stateIn(appScope, SharingStarted.Eagerly, BUTTON_STYLE_ICONS_ONLY)

    val selectedPlatforms: StateFlow<Set<String>> =
        userPreferences.map { it.selectedPlatforms }.stateIn(appScope, SharingStarted.Eagerly, emptySet())

    val nightModeAudio: StateFlow<Int> =
        userPreferences.map { it.nightModeAudio }.stateIn(appScope, SharingStarted.Eagerly, 0)

    val audioVolumeBoostDb: StateFlow<Int> =
        userPreferences.map { it.audioVolumeBoostDb }.stateIn(appScope, SharingStarted.Eagerly, 0)

    val subtitleSize: StateFlow<String> =
        userPreferences.map { it.subtitleSize }.stateIn(appScope, SharingStarted.Eagerly, SUBTITLE_SIZE_NORMAL)

    val subtitleBackground: StateFlow<Boolean> =
        userPreferences.map { it.subtitleBackground }.stateIn(appScope, SharingStarted.Eagerly, false)

    val hideWatched: StateFlow<Boolean> =
        userPreferences.map { it.hideWatched }.stateIn(appScope, SharingStarted.Eagerly, false)

    val autoFrameRateEnabled: StateFlow<Boolean> =
        userPreferences.map { it.autoFrameRateEnabled }.stateIn(appScope, SharingStarted.Eagerly, false)

    val seasonalAudioEnabled: StateFlow<Boolean> =
        userPreferences.map { it.seasonalAudioEnabled }.stateIn(appScope, SharingStarted.Eagerly, true)

    val themeSongsAudioEnabled: StateFlow<Boolean> =
        userPreferences.map { it.themeSongsAudioEnabled }.stateIn(appScope, SharingStarted.Eagerly, true)

    val navigationSoundEnabled: StateFlow<Boolean> =
        userPreferences.map { it.navigationSoundEnabled }.stateIn(appScope, SharingStarted.Eagerly, true)

    val audioPassthroughMode: StateFlow<Int> =
        userPreferences.map { it.audioPassthroughMode }.stateIn(appScope, SharingStarted.Eagerly, AUDIO_PASSTHROUGH_OFF)

    val jellyfinLanServerUrl: StateFlow<String> =
        userPreferences.map { it.jellyfinLanServerUrl }.stateIn(appScope, SharingStarted.Eagerly, "")

    suspend fun updateJellyfinLanServerUrl(lanUrl: String) {
        val cleanUrl = lanUrl.trimEnd('/')
        appContext.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_LAN_SERVER_URL] = cleanUrl
        }
    }

    suspend fun setAudioPassthroughMode(mode: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.AUDIO_PASSTHROUGH_MODE] = mode
        }
    }

    suspend fun setNightModeAudio(level: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.NIGHT_MODE_AUDIO] = level
        }
    }

    suspend fun setAudioVolumeBoostDb(db: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.AUDIO_VOLUME_BOOST_DB] = db
        }
    }

    /** Marca/desmarca la compra de Medusa Pro. */
    suspend fun updatePurchasedPro(isPurchased: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.HAS_PURCHASED_PRO] = "true"
        }
    }

    /**
     * Activa/desactiva el desbloqueo Pro de DEBUG (solo relevante en builds DEBUG).
     * Permite probar el flujo no-Pro sin recompilar ni realizar una compra.
     */
    suspend fun setDebugProOverride(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.DEBUG_PRO_OVERRIDE] = enabled.toString()
        }
    }

    /**
     * Fija o retira el opt-in de certificado autofirmado para el servidor configurado.
     * Un pin vacío equivale a retirar la confianza.
     */
    suspend fun setJellyfinSelfSigned(allowed: Boolean, fingerprintSha256: String) {
        val hasValidPin = allowed && fingerprintSha256.isNotBlank()
        appContext.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_ALLOW_SELF_SIGNED] = hasValidPin
            prefs[Keys.JELLYFIN_CERT_FINGERPRINT] = if (hasValidPin) fingerprintSha256.trim() else ""
        }
    }

    private fun normalizedHost(url: String): String? =
        url.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    java.net.URI(tv.medusa.app.data.remote.NetworkClientFactory.normalizeUrl(it))
                }.getOrNull()?.host?.lowercase()
            }

    suspend fun updateJellyfinServerUrl(serverUrl: String) {
        val cleanUrl = serverUrl.trimEnd('/')
        appContext.dataStore.edit { prefs ->
            val previous = prefs[Keys.JELLYFIN_SERVER_URL] ?: ""
            // Solo se invalida el pin si ya había un host guardado y cambia. Si no había
            // host (pin fijado antes de guardar la URL), se conserva para la conexión nueva.
            val previousHost = normalizedHost(previous)
            if (previousHost != null && previousHost != normalizedHost(cleanUrl)) {
                prefs[Keys.JELLYFIN_ALLOW_SELF_SIGNED] = false
                prefs[Keys.JELLYFIN_CERT_FINGERPRINT] = ""
            }
            prefs[Keys.JELLYFIN_SERVER_URL] = cleanUrl
        }
    }

    suspend fun updateJellyfinUsername(username: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_USERNAME] = username
        }
    }

    suspend fun updateJellyfinPassword(password: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_PASSWORD] = CryptoUtils.encrypt(password)
        }
    }

    suspend fun updateJellyfinConfig(
        serverUrl: String,
        userId: String,
        accessToken: String,
        username: String = "",
        password: String = "",
        serverId: String = "",
        serverVersion: String = ""
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        appContext.dataStore.edit { prefs ->
            val previous = prefs[Keys.JELLYFIN_SERVER_URL] ?: ""
            // Solo se invalida el pin si ya había un host guardado y cambia. Si no había
            // host (pin fijado antes de guardar la URL), se conserva para la conexión nueva.
            val previousHost = normalizedHost(previous)
            if (previousHost != null && previousHost != normalizedHost(cleanUrl)) {
                prefs[Keys.JELLYFIN_ALLOW_SELF_SIGNED] = false
                prefs[Keys.JELLYFIN_CERT_FINGERPRINT] = ""
            }
            prefs[Keys.JELLYFIN_SERVER_URL] = cleanUrl
            prefs[Keys.JELLYFIN_USER_ID] = userId
            prefs[Keys.JELLYFIN_ACCESS_TOKEN] = CryptoUtils.encrypt(accessToken)
            if (username.isNotBlank()) {
                prefs[Keys.JELLYFIN_USERNAME] = username
            }
            if (password.isNotBlank()) {
                prefs[Keys.JELLYFIN_PASSWORD] = CryptoUtils.encrypt(password)
            }
            if (serverId.isNotBlank()) {
                prefs[Keys.JELLYFIN_SERVER_ID] = serverId
            }
            if (serverVersion.isNotBlank()) {
                prefs[Keys.JELLYFIN_SERVER_VERSION] = serverVersion
            }
        }
    }

    suspend fun updateTraktConfig(token: String, clientId: String, clientSecret: String = "") {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.TRAKT_ACCESS_TOKEN] = CryptoUtils.encrypt(token)
            prefs[Keys.TRAKT_CLIENT_ID] = clientId
            if (clientSecret.isNotBlank()) {
                prefs[Keys.TRAKT_CLIENT_SECRET] = clientSecret
            }
        }
    }

    suspend fun updateTmdbConfig(apiKey: String, region: String = ApiConfig.TMDB_WATCH_REGION) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.TMDB_API_KEY] = apiKey
            prefs[Keys.WATCH_REGION] = region
        }
    }

    suspend fun updateButtonStyle(style: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.BUTTON_STYLE] = style
        }
    }

    suspend fun updateAccentColor(color: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.ACCENT_COLOR] = color
        }
    }

    suspend fun updateIndicatorTheme(theme: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.INDICATOR_THEME] = theme
        }
    }

    suspend fun updatePlatformLogoStyle(style: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.PLATFORM_LOGO_STYLE] = style
        }
    }

    suspend fun updatePerformanceMode(mode: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.PERFORMANCE_MODE] = mode
        }
    }

    suspend fun updateSubtitleSize(size: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SUBTITLE_SIZE] = size
        }
    }

    suspend fun updateSubtitleBackground(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SUBTITLE_BACKGROUND] = enabled.toString()
        }
    }

    suspend fun updateAutoFrameRate(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.AUTO_FRAME_RATE] = enabled.toString()
        }
    }

    suspend fun updateAutoPlayNextMovie(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.AUTO_PLAY_NEXT_MOVIE] = enabled.toString()
        }
    }

    suspend fun updateAutoPlayNextEpisode(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.AUTO_PLAY_NEXT_EPISODE] = enabled.toString()
        }
    }

    suspend fun updateSeasonalAudioEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SEASONAL_AUDIO_ENABLED] = enabled.toString()
        }
    }

    suspend fun updateThemeSongsAudioEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.THEME_SONGS_AUDIO_ENABLED] = enabled.toString()
        }
    }

    suspend fun updateNavigationSoundEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.NAVIGATION_SOUND_ENABLED] = enabled.toString()
        }
    }

    suspend fun setHideWatched(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.HIDE_WATCHED] = enabled.toString()
        }
    }

    suspend fun toggleHideWatched() {
        appContext.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDE_WATCHED] == "true"
            prefs[Keys.HIDE_WATCHED] = (!current).toString()
        }
    }

    suspend fun setAppTheme(theme: String) {
        appContext.dataStore.edit { prefs ->
            if (theme == APP_THEME_MONOCHROME) {
                prefs[Keys.ACCENT_COLOR] = ACCENT_WHITE
                prefs[Keys.INDICATOR_THEME] = INDICATOR_THEME_MONOCHROME
                prefs[Keys.PLATFORM_LOGO_STYLE] = PLATFORM_LOGO_MONOCHROME
            } else {
                prefs[Keys.ACCENT_COLOR] = ACCENT_CYAN
                prefs[Keys.INDICATOR_THEME] = INDICATOR_THEME_COLOR
                prefs[Keys.PLATFORM_LOGO_STYLE] = PLATFORM_LOGO_COLOR
            }
        }
    }

    suspend fun updateJellyfinLastSync(timestamp: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_LAST_SYNC] = timestamp
        }
    }

    suspend fun clearJellyfinLastSync() {
        appContext.dataStore.edit { prefs ->
            prefs.remove(Keys.JELLYFIN_LAST_SYNC)
        }
    }

    /** Borra marcas de sync/checkpoint al cambiar de servidor o usuario. */
    suspend fun clearJellyfinSyncMetadata() {
        appContext.dataStore.edit { prefs ->
            prefs.remove(Keys.JELLYFIN_LAST_SYNC)
            prefs.remove(Keys.JELLYFIN_SYNC_CHECKPOINT_VIEW_INDEX)
            prefs.remove(Keys.JELLYFIN_SYNC_CHECKPOINT_OFFSET)
            prefs.remove(Keys.JELLYFIN_SYNC_CHECKPOINT_TOTAL)
            prefs.remove(Keys.SYNC_ALGORITHM_VERSION)
        }
    }

    suspend fun updateSelectedPlatforms(platforms: Set<String>) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_PLATFORMS] = platforms.sorted().joinToString(",")
        }
    }

    /** Finaliza el onboarding en una sola escritura (plataformas + flag). */
    suspend fun completeOnboarding(platforms: Set<String>) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_PLATFORMS] = platforms.sorted().joinToString(",")
            prefs[Keys.HAS_COMPLETED_ONBOARDING] = true.toString()
        }
    }

    /** Alterna una lista de Trakt oculta y devuelve el conjunto resultante (sin relectura). */
    suspend fun toggleHiddenTraktList(key: String): Set<String> {
        val cleanKey = key.trim().lowercase(Locale.ROOT)
        if (cleanKey.isBlank()) return emptySet()
        var updated: Set<String> = emptySet()
        appContext.dataStore.edit { prefs ->
            val current = (prefs[Keys.HIDDEN_TRAKT_LISTS] ?: "")
                .split(",")
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() }
                .toMutableSet()
            if (current.contains(cleanKey)) {
                current.remove(cleanKey)
            } else {
                current.add(cleanKey)
            }
            prefs[Keys.HIDDEN_TRAKT_LISTS] = current.joinToString(",")
            updated = current
        }
        return updated
    }

    /**
     * Guarda de una sola vez toda la configuración de conexión (Jellyfin + Trakt + TMDB).
     * Evita 3 escrituras separadas en la misma acción del usuario.
     */
    suspend fun saveConnectionConfig(
        serverUrl: String,
        userId: String,
        accessToken: String,
        username: String = "",
        password: String = "",
        serverId: String = "",
        serverVersion: String = "",
        traktToken: String,
        traktClientId: String,
        traktClientSecret: String = "",
        tmdbApiKey: String,
        watchRegion: String = ApiConfig.TMDB_WATCH_REGION
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        appContext.dataStore.edit { prefs ->
            val previous = prefs[Keys.JELLYFIN_SERVER_URL] ?: ""
            // Solo se invalida el pin si ya había un host guardado y cambia. Si no había
            // host (pin fijado antes de guardar la URL), se conserva para la conexión nueva.
            val previousHost = normalizedHost(previous)
            if (previousHost != null && previousHost != normalizedHost(cleanUrl)) {
                prefs[Keys.JELLYFIN_ALLOW_SELF_SIGNED] = false
                prefs[Keys.JELLYFIN_CERT_FINGERPRINT] = ""
            }
            prefs[Keys.JELLYFIN_SERVER_URL] = cleanUrl
            prefs[Keys.JELLYFIN_USER_ID] = userId
            prefs[Keys.JELLYFIN_ACCESS_TOKEN] = CryptoUtils.encrypt(accessToken)
            if (username.isNotBlank()) prefs[Keys.JELLYFIN_USERNAME] = username
            if (password.isNotBlank()) prefs[Keys.JELLYFIN_PASSWORD] = CryptoUtils.encrypt(password)
            if (serverId.isNotBlank()) prefs[Keys.JELLYFIN_SERVER_ID] = serverId
            if (serverVersion.isNotBlank()) prefs[Keys.JELLYFIN_SERVER_VERSION] = serverVersion
            prefs[Keys.TRAKT_ACCESS_TOKEN] = CryptoUtils.encrypt(traktToken)
            prefs[Keys.TRAKT_CLIENT_ID] = traktClientId
            if (traktClientSecret.isNotBlank()) prefs[Keys.TRAKT_CLIENT_SECRET] = traktClientSecret
            prefs[Keys.TMDB_API_KEY] = tmdbApiKey
            prefs[Keys.WATCH_REGION] = watchRegion
        }
    }

    suspend fun markOnboardingCompleted() {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_ONBOARDING] = true.toString()
        }
    }

    suspend fun saveSyncCheckpoint(viewIndex: Int, offset: Int, totalSynced: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_SYNC_CHECKPOINT_VIEW_INDEX] = viewIndex
            prefs[Keys.JELLYFIN_SYNC_CHECKPOINT_OFFSET] = offset
            prefs[Keys.JELLYFIN_SYNC_CHECKPOINT_TOTAL] = totalSynced
        }
    }

    suspend fun clearSyncCheckpoint() {
        appContext.dataStore.edit { prefs ->
            prefs.remove(Keys.JELLYFIN_SYNC_CHECKPOINT_VIEW_INDEX)
            prefs.remove(Keys.JELLYFIN_SYNC_CHECKPOINT_OFFSET)
            prefs.remove(Keys.JELLYFIN_SYNC_CHECKPOINT_TOTAL)
        }
    }

    suspend fun getSyncCheckpoint(): Triple<Int, Int, Int> {
        val prefs = appContext.dataStore.data.first()
        return Triple(
            prefs[Keys.JELLYFIN_SYNC_CHECKPOINT_VIEW_INDEX] ?: 0,
            prefs[Keys.JELLYFIN_SYNC_CHECKPOINT_OFFSET] ?: 0,
            prefs[Keys.JELLYFIN_SYNC_CHECKPOINT_TOTAL] ?: 0
        )
    }

    /** Versión del algoritmo de sync ya aplicada sobre el catálogo local (0 = desconocida). */
    suspend fun getSyncAlgorithmVersion(): Int {
        val prefs = appContext.dataStore.data.first()
        return prefs[Keys.SYNC_ALGORITHM_VERSION] ?: 0
    }

    suspend fun updateSyncAlgorithmVersion(version: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SYNC_ALGORITHM_VERSION] = version
        }
    }

    companion object {
        /** Scope de aplicación para el flujo de preferencias compartido (vive todo el proceso). */
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var sharedPrefs: StateFlow<UserPreferences>? = null

        /** Devuelve el único [StateFlow] de preferencias, creándolo la primera vez. */
        private fun prefsStateFlow(context: Context): StateFlow<UserPreferences> {
            sharedPrefs?.let { return it }
            return synchronized(this) {
                sharedPrefs ?: combine(
                    context.applicationContext.dataStore.data,
                    // Se combina con las firmas VIP para que el entitlement se recalcule en cuanto
                    // el manifiesto OTA las aporte (si no, el primer arranque podría evaluarlas
                    // vacías y dejar al usuario VIP como no-Pro hasta la siguiente escritura).
                    ServerVerificationUtils.signatures
                ) { prefs, _ -> prefs.toUserPreferences() }
                    .stateIn(appScope, SharingStarted.Eagerly, UserPreferences())
                    .also { sharedPrefs = it }
            }
        }

        private fun Preferences.toUserPreferences(): UserPreferences {
            val rawTmdb = this[Keys.TMDB_API_KEY] ?: ""
            // Las credenciales heredadas de versiones anteriores (valores literales) ya no se
            // comparan aquí: se retiraron del binario. Solo se cae al valor por defecto cuando
            // el usuario no ha configurado el suyo.
            val tmdbKey = if (rawTmdb.isBlank()) DEFAULT_TMDB_API_KEY else rawTmdb
            val rawTraktId = this[Keys.TRAKT_CLIENT_ID] ?: ""
            val traktId = if (rawTraktId.isBlank()) DEFAULT_TRAKT_CLIENT_ID else rawTraktId
            val rawTraktSecret = this[Keys.TRAKT_CLIENT_SECRET] ?: ""
            val traktSecret = if (rawTraktSecret.isBlank()) DEFAULT_TRAKT_CLIENT_SECRET else rawTraktSecret

            val serverUrl = this[Keys.JELLYFIN_SERVER_URL] ?: ""
            val token = CryptoUtils.decryptStored(this[Keys.JELLYFIN_ACCESS_TOKEN] ?: "")
            val isFamilyVip = ServerVerificationUtils.isAuthorizedServer(serverUrl) && token.isNotBlank()
            val hasPurchased = true
            val hasPromo = true
            val trialActive = false
            val daysRemaining = 0
            val isPro = true

            return UserPreferences(
                jellyfinServerUrl = serverUrl,
                jellyfinLanServerUrl = this[Keys.JELLYFIN_LAN_SERVER_URL] ?: "",
                jellyfinUserId = this[Keys.JELLYFIN_USER_ID] ?: "",
                jellyfinUsername = this[Keys.JELLYFIN_USERNAME] ?: "",
                jellyfinPassword = CryptoUtils.decryptStored(this[Keys.JELLYFIN_PASSWORD] ?: ""),
                jellyfinAccessToken = token,
                jellyfinServerId = this[Keys.JELLYFIN_SERVER_ID] ?: "",
                jellyfinServerVersion = this[Keys.JELLYFIN_SERVER_VERSION] ?: "",
                jellyfinAllowSelfSigned = this[Keys.JELLYFIN_ALLOW_SELF_SIGNED] ?: false,
                jellyfinCertificateFingerprint = this[Keys.JELLYFIN_CERT_FINGERPRINT] ?: "",
                traktAccessToken = CryptoUtils.decryptStored(this[Keys.TRAKT_ACCESS_TOKEN] ?: ""),
                traktClientId = traktId,
                traktClientSecret = traktSecret,
                tmdbApiKey = tmdbKey,
                watchRegion = this[Keys.WATCH_REGION] ?: ApiConfig.TMDB_WATCH_REGION,
                buttonStyle = this[Keys.BUTTON_STYLE] ?: BUTTON_STYLE_ICONS_ONLY,
                accentColor = this[Keys.ACCENT_COLOR] ?: ACCENT_CYAN,
                indicatorTheme = this[Keys.INDICATOR_THEME] ?: INDICATOR_THEME_COLOR,
                platformLogoStyle = this[Keys.PLATFORM_LOGO_STYLE] ?: PLATFORM_LOGO_COLOR,
                performanceMode = this[Keys.PERFORMANCE_MODE] ?: PERFORMANCE_MODE_AUTO,
                jellyfinLastSync = this[Keys.JELLYFIN_LAST_SYNC] ?: "",
                selectedPlatforms = (this[Keys.SELECTED_PLATFORMS] ?: "")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet(),
                hiddenTraktLists = (this[Keys.HIDDEN_TRAKT_LISTS] ?: "")
                    .split(",")
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toSet(),
                hasCompletedOnboarding = (this[Keys.HAS_COMPLETED_ONBOARDING] ?: "true") == true.toString(),
                isProUser = isPro,
                hasPurchasedPro = hasPurchased,
                isFamilyVipUser = isFamilyVip,
                isTrialActive = trialActive,
                trialDaysRemaining = daysRemaining,
                nightModeAudio = this[Keys.NIGHT_MODE_AUDIO] ?: 0,
                audioVolumeBoostDb = this[Keys.AUDIO_VOLUME_BOOST_DB] ?: 0,
                subtitleSize = this[Keys.SUBTITLE_SIZE] ?: SUBTITLE_SIZE_NORMAL,
                subtitleBackground = this[Keys.SUBTITLE_BACKGROUND] == "true",
                autoFrameRateEnabled = (this[Keys.AUTO_FRAME_RATE] ?: "false") == "true",
                seasonalAudioEnabled = (this[Keys.SEASONAL_AUDIO_ENABLED] ?: "true") != "false",
                themeSongsAudioEnabled = (this[Keys.THEME_SONGS_AUDIO_ENABLED] ?: "true") != "false",
                navigationSoundEnabled = (this[Keys.NAVIGATION_SOUND_ENABLED] ?: "true") != "false",
                audioPassthroughMode = this[Keys.AUDIO_PASSTHROUGH_MODE] ?: AUDIO_PASSTHROUGH_OFF,
                hideWatched = this[Keys.HIDE_WATCHED] == "true",
                autoPlayNextMovieEnabled = (this[Keys.AUTO_PLAY_NEXT_MOVIE] ?: "true") != "false",
                autoPlayNextEpisodeEnabled = (this[Keys.AUTO_PLAY_NEXT_EPISODE] ?: "true") != "false"
            )
        }
    }
}
