package tv.medusa.app.data.local

import java.util.Locale
import tv.medusa.app.BuildConfig
import tv.medusa.app.util.DeviceUtils
import tv.medusa.app.util.ServerVerificationUtils
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    val hasPurchasedPro: Boolean = false,
    val isFamilyVipUser: Boolean = false,
    val isTrialActive: Boolean = false,
    val trialDaysRemaining: Int = 0
) {
    val isMonochrome: Boolean
        get() = indicatorTheme == INDICATOR_THEME_MONOCHROME || accentColor == ACCENT_WHITE

    fun getEffectiveParticleScale(context: Context): Float {
        return when (performanceMode) {
            PERFORMANCE_MODE_HIGH -> 1.0f
            PERFORMANCE_MODE_LOW -> 0.25f
            else -> DeviceUtils.getRecommendedParticleScale(context)
        }
    }
}

class UserPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext

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
    }

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
        // Estado de COMPRA (Google Play Billing). Se mantiene la clave original "is_pro_user"
        // para no invalidar instalaciones existentes.
        val HAS_PURCHASED_PRO = stringPreferencesKey("is_pro_user")
        // Desbloqueo por código promocional, independiente de la compra.
        val HAS_PROMO_UNLOCK = stringPreferencesKey("has_promo_unlock")
        // Desbloqueo Pro en builds DEBUG (para probar sin pagar). Desactivable en runtime
        // para testear el flujo no-Pro. Solo se lee cuando BuildConfig.DEBUG es true.
        val DEBUG_PRO_OVERRIDE = stringPreferencesKey("debug_pro_override")
        val FIRST_INSTALL_TIME = longPreferencesKey("first_install_time_ms")
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

    /** Marca/desmarca la compra de Medusa Pro. Solo debe usarla Google Play Billing. */
    suspend fun updatePurchasedPro(isPurchased: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.HAS_PURCHASED_PRO] = isPurchased.toString()
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
            prefs[Keys.JELLYFIN_PASSWORD] = CryptoUtils.encrypt(password)
        }
    }

    suspend fun updateJellyfinConfig(serverUrl: String, userId: String, accessToken: String, username: String = "", password: String = "") {
        val cleanUrl = serverUrl.trimEnd('/')
        appContext.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_SERVER_URL] = cleanUrl
            prefs[Keys.JELLYFIN_USER_ID] = userId
            prefs[Keys.JELLYFIN_ACCESS_TOKEN] = CryptoUtils.encrypt(accessToken)
            if (username.isNotBlank()) {
                prefs[Keys.JELLYFIN_USERNAME] = username
            }
            if (password.isNotBlank()) {
                prefs[Keys.JELLYFIN_PASSWORD] = CryptoUtils.encrypt(password)
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
            prefs[Keys.JELLYFIN_ACCESS_TOKEN] = CryptoUtils.encrypt(accessToken)
            if (username.isNotBlank()) prefs[Keys.JELLYFIN_USERNAME] = username
            if (password.isNotBlank()) prefs[Keys.JELLYFIN_PASSWORD] = CryptoUtils.encrypt(password)
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
            val tmdbKey = if (rawTmdb.isBlank() || rawTmdb == "b673238641eb86a9f4787f0b5d564177" || rawTmdb == "82dee22856e0d0ac5f767ec6fb845efc") DEFAULT_TMDB_API_KEY else rawTmdb
            val rawTraktId = this[Keys.TRAKT_CLIENT_ID] ?: ""
            val traktId = if (rawTraktId.isBlank() || rawTraktId.startsWith("4376c989")) DEFAULT_TRAKT_CLIENT_ID else rawTraktId
            val rawTraktSecret = this[Keys.TRAKT_CLIENT_SECRET] ?: ""
            val traktSecret = if (rawTraktSecret.isBlank()) DEFAULT_TRAKT_CLIENT_SECRET else rawTraktSecret

            val serverUrl = this[Keys.JELLYFIN_SERVER_URL] ?: ""
            val token = CryptoUtils.decryptStored(this[Keys.JELLYFIN_ACCESS_TOKEN] ?: "")
            val isFamilyVip = ServerVerificationUtils.isAuthorizedServer(serverUrl) && token.isNotBlank()
            val hasPurchased = (this[Keys.HAS_PURCHASED_PRO] == "true")
            val hasPromo = (this[Keys.HAS_PROMO_UNLOCK] == "true")

            val now = System.currentTimeMillis()
            val trialDurationMs = 7L * 24L * 60L * 60L * 1000L // 7 días
            val recordedInstallTime = this[Keys.FIRST_INSTALL_TIME] ?: 0L
            val baseInstallTime = if (recordedInstallTime > 0L) recordedInstallTime else now
            val elapsed = now - baseInstallTime
            val trialActive = !hasPurchased && !hasPromo && !isFamilyVip && (elapsed in 0L..trialDurationMs)
            val daysRemaining = if (trialActive) {
                val remMs = trialDurationMs - elapsed
                ((remMs / (24L * 60L * 60L * 1000L)) + 1).toInt().coerceIn(1, 7)
            } else {
                0
            }

            // Desbloqueo de DEBUG: aislado del entitlement real y desactivable en runtime
            // para poder probar el flujo no-Pro sin recompilar. Por defecto activo solo en DEBUG.
            val debugProUnlock = BuildConfig.DEBUG &&
                (this[Keys.DEBUG_PRO_OVERRIDE] ?: "true") != "false"

            val isPro = hasPurchased || hasPromo || isFamilyVip || trialActive || debugProUnlock

            return UserPreferences(
                jellyfinServerUrl = serverUrl,
                jellyfinUserId = this[Keys.JELLYFIN_USER_ID] ?: "",
                jellyfinUsername = this[Keys.JELLYFIN_USERNAME] ?: "",
                jellyfinPassword = CryptoUtils.decryptStored(this[Keys.JELLYFIN_PASSWORD] ?: ""),
                jellyfinAccessToken = token,
                traktAccessToken = CryptoUtils.decryptStored(this[Keys.TRAKT_ACCESS_TOKEN] ?: ""),
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
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toSet(),
                hasCompletedOnboarding = this[Keys.HAS_COMPLETED_ONBOARDING] == true.toString(),
                isProUser = isPro,
                hasPurchasedPro = hasPurchased,
                isFamilyVipUser = isFamilyVip,
                isTrialActive = trialActive,
                trialDaysRemaining = daysRemaining
            )
        }
    }
}
