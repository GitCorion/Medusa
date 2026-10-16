package com.example.tujelly.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tujelly_settings")

// Claves por defecto para TMDB/Trakt. Provienen de BuildConfig (inyectables en build-time vía
// env/keystore.properties); ya no hay literales hardcodeados en el código fuente.
val DEFAULT_TMDB_API_KEY: String = com.example.tujelly.BuildConfig.DEFAULT_TMDB_API_KEY
val DEFAULT_TRAKT_CLIENT_ID: String = com.example.tujelly.BuildConfig.DEFAULT_TRAKT_CLIENT_ID
val DEFAULT_TRAKT_CLIENT_SECRET: String = com.example.tujelly.BuildConfig.DEFAULT_TRAKT_CLIENT_SECRET

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

data class UserPreferences(
    val jellyfinServerUrl: String = "",
    val jellyfinUserId: String = "",
    val jellyfinUsername: String = "",
    val jellyfinPassword: String = "",
    val jellyfinAccessToken: String = "",
    val traktAccessToken: String = "",
    val traktClientId: String = DEFAULT_TRAKT_CLIENT_ID,
    val traktClientSecret: String = DEFAULT_TRAKT_CLIENT_SECRET,
    val tmdbApiKey: String = DEFAULT_TMDB_API_KEY,
    val watchRegion: String = "ES",
    val buttonStyle: String = BUTTON_STYLE_ICONS_ONLY,
    val accentColor: String = ACCENT_CYAN,
    val indicatorTheme: String = INDICATOR_THEME_COLOR,
    val platformLogoStyle: String = PLATFORM_LOGO_COLOR,
    val performanceMode: String = PERFORMANCE_MODE_AUTO,
    val jellyfinLastSync: String = "",
    val selectedPlatforms: Set<String> = emptySet(),
    val hiddenTraktLists: Set<String> = emptySet(),
    val hasCompletedOnboarding: Boolean = false,
    val isProUser: Boolean = false,
    val isFamilyVipUser: Boolean = false
) {
    val isMonochrome: Boolean
        get() = indicatorTheme == INDICATOR_THEME_MONOCHROME || accentColor == ACCENT_WHITE

    fun getEffectiveParticleScale(context: Context): Float {
        return when (performanceMode) {
            PERFORMANCE_MODE_HIGH -> 1.0f
            PERFORMANCE_MODE_LOW -> 0.25f
            else -> com.example.tujelly.util.DeviceUtils.getRecommendedParticleScale(context)
        }
    }
}

class UserPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext

    /** Contexto de aplicación (sin riesgo de leak) para acceder a ficheros, etc. */
    val applicationContext: Context get() = appContext

    private object Keys {
        val JELLYFIN_SERVER_URL = stringPreferencesKey("jellyfin_server_url")
        val JELLYFIN_USER_ID = stringPreferencesKey("jellyfin_user_id")
        val JELLYFIN_USERNAME = stringPreferencesKey("jellyfin_username")
        val JELLYFIN_PASSWORD = stringPreferencesKey("jellyfin_password")
        val JELLYFIN_ACCESS_TOKEN = stringPreferencesKey("jellyfin_access_token")
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
        val IS_PRO_USER = stringPreferencesKey("is_pro_user")
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

    val isProUser: StateFlow<Boolean> =
        userPreferences.map { it.isProUser }.stateIn(appScope, SharingStarted.Eagerly, false)

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

    suspend fun updateProUser(isPro: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.IS_PRO_USER] = isPro.toString()
        }
    }

    /**
     * Valida y canjea un código promocional u operado por familia.
     * Los códigos solo se comprueban mediante hashes SHA-256 para evitar que figuren en texto plano.
     */
    suspend fun redeemPromoCode(code: String): Boolean {
        val trimmed = code.trim().uppercase()
        if (trimmed.isBlank()) return false
        val hash = com.example.tujelly.util.ServerVerificationUtils.sha256(trimmed)
        val validHashes = setOf(
            "2debaffca0c97c243e36962184e4a3b1844847931790990f4cd0fe5af754b820", // VIP 1
            "524540576cf95fe4d888b0a98b1d14411be0a6576c333e62910e49944db29537", // VIP 2
            "140d7031f62e662310b9da56506f05ec8845386affc3d945c14f26bcadfefe67"  // VIP 3
        )
        return if (validHashes.contains(hash)) {
            updateProUser(true)
            true
        } else {
            false
        }
    }

    suspend fun updateJellyfinServerUrl(serverUrl: String) {
        val cleanUrl = serverUrl.trimEnd('/')
        appContext.dataStore.edit { prefs ->
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
            prefs[Keys.JELLYFIN_PASSWORD] = password
        }
    }

    suspend fun updateJellyfinConfig(serverUrl: String, userId: String, accessToken: String, username: String = "", password: String = "") {
        val cleanUrl = serverUrl.trimEnd('/')
        appContext.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_SERVER_URL] = cleanUrl
            prefs[Keys.JELLYFIN_USER_ID] = userId
            prefs[Keys.JELLYFIN_ACCESS_TOKEN] = accessToken
            if (username.isNotBlank()) {
                prefs[Keys.JELLYFIN_USERNAME] = username
            }
            if (password.isNotBlank()) {
                prefs[Keys.JELLYFIN_PASSWORD] = password
            }
        }
    }

    suspend fun updateTraktConfig(token: String, clientId: String, clientSecret: String = "") {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.TRAKT_ACCESS_TOKEN] = token
            prefs[Keys.TRAKT_CLIENT_ID] = clientId
            if (clientSecret.isNotBlank()) {
                prefs[Keys.TRAKT_CLIENT_SECRET] = clientSecret
            }
        }
    }

    suspend fun updateTmdbConfig(apiKey: String, region: String = "ES") {
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
        val cleanKey = key.trim().lowercase(java.util.Locale.ROOT)
        if (cleanKey.isBlank()) return emptySet()
        var updated: Set<String> = emptySet()
        appContext.dataStore.edit { prefs ->
            val current = (prefs[Keys.HIDDEN_TRAKT_LISTS] ?: "")
                .split(",")
                .map { it.trim().lowercase(java.util.Locale.ROOT) }
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
        traktToken: String,
        traktClientId: String,
        traktClientSecret: String = "",
        tmdbApiKey: String,
        watchRegion: String = "ES"
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        appContext.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_SERVER_URL] = cleanUrl
            prefs[Keys.JELLYFIN_USER_ID] = userId
            prefs[Keys.JELLYFIN_ACCESS_TOKEN] = accessToken
            if (username.isNotBlank()) prefs[Keys.JELLYFIN_USERNAME] = username
            if (password.isNotBlank()) prefs[Keys.JELLYFIN_PASSWORD] = password
            prefs[Keys.TRAKT_ACCESS_TOKEN] = traktToken
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

    companion object {
        /** Scope de aplicación para el flujo de preferencias compartido (vive todo el proceso). */
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var sharedPrefs: StateFlow<UserPreferences>? = null

        /** Devuelve el único [StateFlow] de preferencias, creándolo la primera vez. */
        private fun prefsStateFlow(context: Context): StateFlow<UserPreferences> {
            sharedPrefs?.let { return it }
            return synchronized(this) {
                sharedPrefs ?: context.applicationContext.dataStore.data
                    .map { it.toUserPreferences() }
                    .stateIn(appScope, SharingStarted.Eagerly, UserPreferences())
                    .also { sharedPrefs = it }
            }
        }

        private fun Preferences.toUserPreferences(): UserPreferences {
            val rawTmdb = this[Keys.TMDB_API_KEY] ?: ""
            val tmdbKey = if (rawTmdb.isBlank() || rawTmdb == "b673238641eb86a9f4787f0b5d564177" || rawTmdb == "82dee22856e0d0ac5f767ec6fb845efc") DEFAULT_TMDB_API_KEY else rawTmdb
            val rawTraktId = this[Keys.TRAKT_CLIENT_ID] ?: ""
            val traktId = if (rawTraktId.isBlank() || rawTraktId.startsWith("4376c989")) DEFAULT_TRAKT_CLIENT_ID else rawTraktId
            val rawTraktSecret = this[Keys.TRAKT_CLIENT_SECRET] ?: ""
            val traktSecret = if (rawTraktSecret.isBlank()) DEFAULT_TRAKT_CLIENT_SECRET else rawTraktSecret

            val serverUrl = this[Keys.JELLYFIN_SERVER_URL] ?: ""
            val token = this[Keys.JELLYFIN_ACCESS_TOKEN] ?: ""
            val isFamilyVip = com.example.tujelly.util.ServerVerificationUtils.isAuthorizedServer(serverUrl) && token.isNotBlank()

            return UserPreferences(
                jellyfinServerUrl = serverUrl,
                jellyfinUserId = this[Keys.JELLYFIN_USER_ID] ?: "",
                jellyfinUsername = this[Keys.JELLYFIN_USERNAME] ?: "",
                jellyfinPassword = this[Keys.JELLYFIN_PASSWORD] ?: "",
                jellyfinAccessToken = token,
                traktAccessToken = this[Keys.TRAKT_ACCESS_TOKEN] ?: "",
                traktClientId = traktId,
                traktClientSecret = traktSecret,
                tmdbApiKey = tmdbKey,
                watchRegion = this[Keys.WATCH_REGION] ?: "ES",
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
                    .map { it.trim().lowercase(java.util.Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toSet(),
                hasCompletedOnboarding = this[Keys.HAS_COMPLETED_ONBOARDING] == true.toString(),
                isFamilyVipUser = isFamilyVip,
                isProUser = (this[Keys.IS_PRO_USER] == "true") || com.example.tujelly.BuildConfig.DEBUG || isFamilyVip
            )
        }
    }
}
