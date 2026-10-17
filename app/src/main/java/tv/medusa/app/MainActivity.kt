package tv.medusa.app

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import tv.medusa.app.data.billing.BillingManager
import tv.medusa.app.data.local.ACCENT_WHITE
import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.data.local.PLATFORM_LOGO_MONOCHROME
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.update.AppUpdateManager
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalBillingManager
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.LocalPlatformLogoStyle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.medusa.app.R
import tv.medusa.app.ui.screens.medusa.MedusaScreen
import tv.medusa.app.ui.screens.brand.BrandScreen
import tv.medusa.app.ui.screens.detail.DetailScreen
import tv.medusa.app.ui.screens.favorites.FavoritesScreen
import tv.medusa.app.ui.screens.genre.GenreScreen
import tv.medusa.app.ui.screens.home.HomeScreen
import tv.medusa.app.ui.screens.onboarding.PlatformSelectionScreen
import tv.medusa.app.ui.screens.player.PlayerScreen
import tv.medusa.app.ui.screens.search.SearchScreen
import tv.medusa.app.ui.screens.settings.SettingsScreen
import tv.medusa.app.ui.theme.MedusaTheme
import tv.medusa.app.util.AppForeground

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject
    lateinit var billingManager: BillingManager

    @javax.inject.Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private var pendingPlayItemId by mutableStateOf<String?>(null)

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NetworkClientFactory.initCache(applicationContext)

        val imageLoader = coil.ImageLoader.Builder(this)
            .okHttpClient { NetworkClientFactory.okHttpClient }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            // Sin crossfade global: animar cada imagen de cada tarjeta penaliza el scroll.
            // Hero/backdrop ya animan con su propio Crossfade composable.
            .crossfade(false)
            .build()
        coil.Coil.setImageLoader(imageLoader)

        setContent {
            val userPrefsRepo = userPreferencesRepository
            val currentPrefs by userPrefsRepo.userPreferencesFlow.collectAsStateWithLifecycle(initialValue = null)

            val prefs = currentPrefs
            if (prefs == null) {
                // Fondo neutro mientras se cargan las preferencias de almacenamiento para evitar el parpadeo de onboarding
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0C0C12))
                )
                return@setContent
            }

            val isMonochrome = prefs.isMonochrome
            val indicatorTheme = if (isMonochrome) INDICATOR_THEME_MONOCHROME else prefs.indicatorTheme
            val platformLogoStyle = if (isMonochrome) PLATFORM_LOGO_MONOCHROME else prefs.platformLogoStyle

            val accentColor = if (isMonochrome) ACCENT_WHITE else prefs.accentColor

            MedusaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = NonInteractiveSurfaceDefaults.colors(
                        containerColor = Color(0xFF0C0C12)
                    )
                ) {
                    CompositionLocalProvider(
                        LocalIsMonochromeTheme provides isMonochrome,
                        LocalIndicatorTheme provides indicatorTheme,
                        LocalPlatformLogoStyle provides platformLogoStyle,
                        LocalAccentColor provides accentColor,
                        LocalBillingManager provides billingManager
                    ) {
                        MedusaApp(
                            startOnboarding = !prefs.hasCompletedOnboarding,
                            pendingPlayItemId = pendingPlayItemId,
                            onPlayItemConsumed = { pendingPlayItemId = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("playItemId")?.let {
            pendingPlayItemId = it
        }
    }

    override fun onStart() {
        super.onStart()
        // El trabajo de fondo (enriquecido de metadatos) baja su ritmo mientras hay app delante.
        AppForeground.isVisible = true
    }

    override fun onStop() {
        AppForeground.isVisible = false
        super.onStop()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MedusaApp(
    startOnboarding: Boolean = false,
    pendingPlayItemId: String? = null,
    onPlayItemConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val startDestination = if (startOnboarding) "onboarding" else "home"

    LaunchedEffect(pendingPlayItemId) {
        val id = pendingPlayItemId
        if (!id.isNullOrBlank()) {
            delay(300L)
            Log.i("MainActivity", "Navigating to player/$id via pendingPlayItemId")
            navController.navigate("player/$id")
            onPlayItemConsumed()
        }
    }

    val context = LocalContext.current
    val appUpdateManager = remember { AppUpdateManager(context.applicationContext) }
    val updateInfo by AppUpdateManager.updateInfo.collectAsStateWithLifecycle()
    val isDownloading by AppUpdateManager.isDownloading.collectAsStateWithLifecycle()
    val downloadProgress by AppUpdateManager.downloadProgress.collectAsStateWithLifecycle()

    var showUpdateBanner by remember { mutableStateOf(false) }
    var updateDismissed by remember { mutableStateOf(false) }
    val updateButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // El manifiesto OTA se consulta SIEMPRE, en todos los flavors: de él dependen el
        // kill-switch global y la lista VIP familiar, que deben aplicarse en cualquier versión
        // (no solo en la beta distribuida por Firebase).
        appUpdateManager.checkAutomatically()
    }

    LaunchedEffect(updateInfo) {
        val info = updateInfo
        val forced = info?.isForceUpdate == true
        val enforcedEverywhere = info?.isKillSwitch == true || forced
        // El aviso de "hay actualización" normal solo se muestra en el flavor con updater propio;
        // el kill-switch y la actualización obligatoria se aplican siempre.
        if (info?.hasUpdate == true &&
            (enforcedEverywhere || (BuildConfig.ENABLE_IN_APP_UPDATER && !updateDismissed))
        ) {
            showUpdateBanner = true
        }
    }

    LaunchedEffect(showUpdateBanner) {
        if (showUpdateBanner) {
            delay(200)
            updateButtonFocusRequester.requestFocus()
        }
    }

    // Kill-switch y actualización obligatoria: diálogo no descartable; Atrás cierra la app.
    val isForceUpdate = updateInfo?.isForceUpdate == true
    val isKillSwitch = updateInfo?.isKillSwitch == true

    BackHandler(enabled = showUpdateBanner) {
        if (!isForceUpdate) {
            showUpdateBanner = false
            updateDismissed = true
        } else {
            (context as? Activity)?.finish()
        }
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    val isPlayerActive = currentEntry?.destination?.route?.startsWith("player") == true

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
        composable("onboarding") {
            PlatformSelectionScreen(
                onDone = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                onPlayMedia = { itemId ->
                    navController.navigate("player/$itemId")
                },
                onDetailMedia = { itemId ->
                    navController.navigate("detail/$itemId")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                },
                onOpenSearch = {
                    navController.navigate("search")
                },
                onOpenFavorites = {
                    navController.navigate("favorites")
                },
                onOpenBrand = { brandId ->
                    navController.navigate("brand/$brandId")
                },
                onOpenGenre = { genreName ->
                    navController.navigate("genre/$genreName")
                },
                onOpenMedusa = {
                    navController.navigate("medusa") { launchSingleTop = true }
                }
            )
        }

        composable("medusa") {
            MedusaScreen(
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenFavorites = {
                    navController.navigate("favorites")
                },
                onOpenSearch = {
                    navController.navigate("search")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                },
                onPlayMedia = { itemId ->
                    navController.navigate("player/$itemId")
                },
                onDetailMedia = { itemId ->
                    navController.navigate("detail/$itemId")
                }
            )
        }

        composable("favorites") {
            FavoritesScreen(
                onBack = {
                    navController.popBackStack()
                },
                onPlayMedia = { itemId ->
                    navController.navigate("player/$itemId")
                },
                onDetailMedia = { itemId ->
                    navController.navigate("detail/$itemId")
                },
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenMedusa = {
                    navController.navigate("medusa") { launchSingleTop = true }
                },
                onOpenSearch = {
                    navController.navigate("search")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("search") {
            SearchScreen(
                onBack = {
                    navController.popBackStack()
                },
                onPlayMedia = { itemId ->
                    navController.navigate("player/$itemId")
                },
                onDetailMedia = { itemId ->
                    navController.navigate("detail/$itemId")
                },
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenMedusa = {
                    navController.navigate("medusa") { launchSingleTop = true }
                },
                onOpenFavorites = {
                    navController.navigate("favorites")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable(
            route = "brand/{brandId}",
            arguments = listOf(navArgument("brandId") { type = NavType.StringType })
        ) { backStackEntry ->
            val brandId = backStackEntry.arguments?.getString("brandId") ?: "netflix"
            BrandScreen(
                brandId = brandId,
                onBack = {
                    navController.popBackStack()
                },
                onPlayMedia = { itemId ->
                    navController.navigate("player/$itemId")
                },
                onDetailMedia = { itemId ->
                    navController.navigate("detail/$itemId")
                },
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenMedusa = {
                    navController.navigate("medusa") { launchSingleTop = true }
                },
                onOpenFavorites = {
                    navController.navigate("favorites")
                },
                onOpenSearch = {
                    navController.navigate("search")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable(
            route = "genre/{genreName}",
            arguments = listOf(navArgument("genreName") { type = NavType.StringType })
        ) { backStackEntry ->
            val genreName = backStackEntry.arguments?.getString("genreName") ?: "Acción"
            GenreScreen(
                genreName = genreName,
                onBack = {
                    navController.popBackStack()
                },
                onPlayMedia = { itemId ->
                    navController.navigate("player/$itemId")
                },
                onDetailMedia = { itemId ->
                    navController.navigate("detail/$itemId")
                },
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenMedusa = {
                    navController.navigate("medusa") { launchSingleTop = true }
                },
                onOpenFavorites = {
                    navController.navigate("favorites")
                },
                onOpenSearch = {
                    navController.navigate("search")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable(
            route = "detail/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            DetailScreen(
                itemId = itemId,
                onPlay = { id ->
                    navController.navigate("player/$id")
                },
                onNavigateToDetail = { id ->
                    navController.navigate("detail/$id")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "player/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            PlayerScreen(
                itemId = itemId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onNavigateHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenMedusa = {
                    navController.navigate("medusa") { launchSingleTop = true }
                },
                onOpenFavorites = {
                    navController.navigate("favorites")
                },
                onOpenSearch = {
                    navController.navigate("search")
                }
            )
        }
    }

    val isForceDialog = updateInfo?.isForceUpdate == true
    if (showUpdateBanner && updateInfo?.hasUpdate == true && !isPlayerActive && (isForceDialog || !updateDismissed)) {
        Dialog(
            onDismissRequest = {
                if (isForceDialog) {
                    (context as? Activity)?.finish()
                } else {
                    showUpdateBanner = false
                    updateDismissed = true
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !isForceDialog,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(560.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xF50D111A))
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color(0x8000E5FF), Color(0x30C084FC))
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(28.dp)
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    val isForce = isForceDialog
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (isForce) Color(0xFFEF4444) else Color(0xFF10B981), CircleShape)
                            )
                            Text(
                                text = when {
                                    isKillSwitch -> stringResource(R.string.update_kill_switch_title)
                                    isForce -> stringResource(R.string.update_title_forced)
                                    else -> stringResource(R.string.update_title_available)
                                },
                                color = if (isForce) Color(0xFFF87171) else Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when {
                                isKillSwitch -> stringResource(R.string.update_kill_switch_body)
                                isForce -> stringResource(R.string.update_beta_expired)
                                else -> stringResource(R.string.update_new_version, updateInfo?.latestVersion ?: "")
                            },
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val notes = updateInfo?.releaseNotes
                        if (!notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .background(Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = notes,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            val progress = downloadProgress ?: 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF00E5FF),
                                trackColor = Color(0x30FFFFFF)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.update_downloading_progress, (progress * 100).toInt()),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (updateInfo?.apkUrl != null) {
                                Button(
                                    onClick = {
                                        val info = updateInfo
                                        val apkUrl = info?.apkUrl
                                        if (apkUrl != null) {
                                            coroutineScope.launch {
                                                appUpdateManager.downloadAndInstall(apkUrl, info?.apkSha256)
                                            }
                                        }
                                    },
                                    enabled = !isDownloading,
                                    modifier = Modifier.focusRequester(updateButtonFocusRequester),
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isForce) Color(0xFFEF4444) else Color(0xFF00E5FF),
                                        contentColor = if (isForce) Color.White else Color(0xFF05070B),
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                                ) {
                                    Text(
                                        text = if (isDownloading) stringResource(R.string.update_downloading) else stringResource(R.string.update_now),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            if (!isForce) {
                                Button(
                                    onClick = {
                                        showUpdateBanner = false
                                        updateDismissed = true
                                    },
                                    enabled = !isDownloading,
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color(0x18FFFFFF),
                                        contentColor = Color(0xFFCBD5E1),
                                        focusedContainerColor = Color(0x40FFFFFF),
                                        focusedContentColor = Color.White
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                                ) {
                                    Text(
                                        text = stringResource(R.string.update_later),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
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
}
