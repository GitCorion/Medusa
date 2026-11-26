@file:OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)

package tv.medusa.app.ui.screens.player

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.util.EventLogger
import java.util.Locale
import kotlin.math.abs
import tv.medusa.app.data.local.db.AppDatabase
import tv.medusa.app.data.repository.buildJellyfinAuthHeader
import tv.medusa.app.ui.components.JellyLoadingIndicator
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.media3.session.MediaSession
import tv.medusa.app.MainActivity
import tv.medusa.app.util.player.SeekableExtractorsFactory
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.foundation.lazy.items
import tv.medusa.app.util.AudioEnhancementManager
import tv.medusa.app.util.AutoFrameRateManager
import tv.medusa.app.util.AppForeground
import tv.medusa.app.data.local.SUBTITLE_SIZE_SMALL
import tv.medusa.app.data.local.SUBTITLE_SIZE_NORMAL
import tv.medusa.app.data.local.SUBTITLE_SIZE_LARGE
import android.media.AudioFormat
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_OFF
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_AUTO
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_DIRECT
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TvAccent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.Surface
import tv.medusa.app.ui.util.handleTouchClick
import androidx.tv.material3.Text
import tv.medusa.app.R
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_AND_TEXT
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.data.remote.jellyfin.JellyfinMediaSourceDto
import tv.medusa.app.data.remote.jellyfin.NextEpisodeInfo
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.jellyfin.SkipMarker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class PlayerModalTab {
    NONE,
    AUDIO,
    SUBTITLES,
    VERSIONS,
    SETTINGS
}

private data class TrackItem(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val flagEmoji: String? = null,
    val isSelected: Boolean,
    val jellyfinStreamIndex: Int? = null
)

/**
 * Si el reproductor lleva este tiempo montado sin recibir un solo byte, se
 * reintenta la resolución. Cubre el estado colgado en "Cargando reproducción…"
 * que dejaba un fallo previo de preparación (sin peticiones al servidor).
 */
private const val PLAYER_STALL_TIMEOUT_MS = 45_000L

private fun extractHttpResponseCode(error: Throwable?): Int =
    generateSequence(error) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode ?: 0

private fun Modifier.monochromeFilter(isMonochrome: Boolean): Modifier = if (isMonochrome) {
    this.drawWithContent {
        val paint = Paint().apply {
            val cm = ColorMatrix().apply { setToSaturation(0f) }
            asFrameworkPaint().colorFilter = android.graphics.ColorMatrixColorFilter(cm.values)
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }
} else {
    this
}

private fun getLanguageFlag(language: String?): String? {
    if (language.isNullOrBlank() || language.equals("und", ignoreCase = true)) return null
    val code = language.lowercase().trim()
    return when {
        code == "es" || code == "spa" || code.startsWith("es-es") -> "🇪🇸"
        code == "es-419" || code == "es-la" || code == "es-mx" || code.startsWith("es-") -> "🇲🇽"
        code == "en" || code == "eng" || code.startsWith("en-us") -> "🇺🇸"
        code == "en-gb" || code == "en-uk" -> "🇬🇧"
        code == "fr" || code == "fra" || code == "fre" -> "🇫🇷"
        code == "de" || code == "deu" || code == "ger" -> "🇩🇪"
        code == "it" || code == "ita" -> "🇮🇹"
        code == "pt" || code == "por" || code.startsWith("pt-pt") -> "🇵🇹"
        code == "pt-br" -> "🇧🇷"
        code == "ja" || code == "jpn" -> "🇯🇵"
        code == "ko" || code == "kor" -> "🇰🇷"
        code == "zh" || code == "chi" || code == "zho" || code.startsWith("zh-") -> "🇨🇳"
        code == "ru" || code == "rus" -> "🇷🇺"
        code == "uk" || code == "ukr" -> "🇺🇦"
        code == "ca" || code == "cat" || code == "gl" || code == "glg" || code == "eu" || code == "eus" || code == "baq" -> "🇪🇸"
        code == "nl" || code == "nld" || code == "dut" -> "🇳🇱"
        code == "pl" || code == "pol" -> "🇵🇱"
        code == "sv" || code == "swe" -> "🇸🇪"
        code == "da" || code == "dan" -> "🇩🇰"
        code == "no" || code == "nor" || code == "nob" || code == "nno" -> "🇳🇴"
        code == "fi" || code == "fin" -> "🇫🇮"
        code == "el" || code == "ell" || code == "gre" -> "🇬🇷"
        code == "tr" || code == "tur" -> "🇹🇷"
        code == "ar" || code == "ara" -> "🇸🇦"
        code == "hi" || code == "hin" -> "🇮🇳"
        code == "he" || code == "heb" -> "🇮🇱"
        code == "cs" || code == "ces" || code == "cze" -> "🇨🇿"
        code == "hu" || code == "hun" -> "🇭🇺"
        code == "ro" || code == "ron" || code == "rum" -> "🇷🇴"
        code == "th" || code == "tha" -> "🇹🇭"
        code == "vi" || code == "vie" -> "🇻🇳"
        code == "id" || code == "ind" -> "🇮🇩"
        code.length == 2 && code.all { it in 'a'..'z' } -> {
            val first = Character.toChars(0x1F1E6 + (code[0] - 'a'))
            val second = Character.toChars(0x1F1E6 + (code[1] - 'a'))
            String(first) + String(second)
        }
        else -> null
    }
}

private fun getLanguageDisplayName(langCode: String?, rawLabel: String?): String {
    val rawLower = rawLabel?.lowercase() ?: ""
    if (rawLower.isNotBlank()) {
        when {
            rawLower.contains("castellano") || rawLower.contains("es-es") -> return "Castellano"
            rawLower.contains("latino") || rawLower.contains("es-419") || rawLower.contains("es-la") || rawLower.contains("es-mx") -> return "Español Latino"
            rawLower.contains("español") || rawLower.contains("espanol") || rawLower.contains("spanish") -> return "Español"
            rawLower.contains("inglés") || rawLower.contains("ingles") || rawLower.contains("english") -> return "Inglés"
            rawLower.contains("francés") || rawLower.contains("frances") || rawLower.contains("french") -> return "Francés"
            rawLower.contains("alemán") || rawLower.contains("aleman") || rawLower.contains("german") -> return "Alemán"
            rawLower.contains("italiano") || rawLower.contains("italian") -> return "Italiano"
            rawLower.contains("japonés") || rawLower.contains("japones") || rawLower.contains("japanese") -> return "Japonés"
            rawLower.contains("coreano") || rawLower.contains("korean") -> return "Coreano"
            rawLower.contains("chino") || rawLower.contains("chinese") -> return "Chino"
            rawLower.contains("ruso") || rawLower.contains("russian") -> return "Ruso"
            rawLower.contains("portugués") || rawLower.contains("portugues") || rawLower.contains("portuguese") -> return "Portugués"
            rawLower.contains("catalán") || rawLower.contains("catalan") -> return "Catalán"
            rawLower.contains("gallego") || rawLower.contains("galician") -> return "Gallego"
            rawLower.contains("vasco") || rawLower.contains("euskera") -> return "Vasco"
        }
    }

    if (langCode.isNullOrBlank() || langCode.equals("und", ignoreCase = true)) {
        return "Desconocido"
    }
    val code = langCode.lowercase().trim()
    return when (code) {
        "es", "spa" -> "Español"
        "es-419", "es-la", "es-mx" -> "Español Latino"
        "en", "eng" -> "Inglés"
        "fr", "fra", "fre" -> "Francés"
        "de", "deu", "ger" -> "Alemán"
        "it", "ita" -> "Italiano"
        "pt", "por" -> "Portugués"
        "pt-br" -> "Portugués Brasil"
        "ja", "jpn" -> "Japonés"
        "ko", "kor" -> "Coreano"
        "zh", "chi", "zho" -> "Chino"
        "ru", "rus" -> "Ruso"
        "ca", "cat" -> "Catalán"
        "gl", "glg" -> "Gallego"
        "eu", "eus", "baq" -> "Vasco"
        "nl", "nld", "dut" -> "Holandés"
        "pl", "pol" -> "Polaco"
        "sv", "swe" -> "Sueco"
        "da", "dan" -> "Danés"
        "no", "nor", "nob" -> "Noruego"
        "fi", "fin" -> "Finés"
        "el", "ell", "gre" -> "Griego"
        "tr", "tur" -> "Turco"
        "ar", "ara" -> "Árabe"
        "hi", "hin" -> "Hindi"
        "he", "heb" -> "Hebreo"
        "uk", "ukr" -> "Ucraniano"
        "cs", "ces", "cze" -> "Checo"
        "hu", "hun" -> "Húngaro"
        "ro", "ron", "rum" -> "Rumano"
        else -> {
            try {
                val loc = Locale.forLanguageTag(code)
                val display = loc.getDisplayLanguage(Locale("es"))
                if (!display.isNullOrBlank()) display.replaceFirstChar { it.uppercase() } else code.uppercase()
            } catch (_: Exception) {
                code.uppercase()
            }
        }
    }
}

@OptIn(UnstableApi::class)
@ExperimentalTvMaterial3Api
@Composable
fun PlayerScreen(
    itemId: String,
    initialMediaSourceId: String? = null,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(itemId, initialMediaSourceId) {
        Log.i("PlayerScreen", "PlayerScreen LaunchedEffect for itemId=$itemId initialMediaSourceId=$initialMediaSourceId")
        tv.medusa.app.util.SeasonalAudioManager.release()
        tv.medusa.app.util.ThemeSongPlayerManager.release()
        viewModel.loadStreamUrl(itemId, preferredMediaSourceId = initialMediaSourceId)
    }

    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val buttonStyle by viewModel.buttonStyle.collectAsStateWithLifecycle()
    val isMonochrome by viewModel.isMonochrome.collectAsStateWithLifecycle()
    val accentColorKey by viewModel.accentColor.collectAsStateWithLifecycle()
    val focusColor = if (isMonochrome) TextPrimary else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentColorKey)

    // Load intro markers + next episode once stream is ready
    val introMarker by viewModel.introMarker.collectAsStateWithLifecycle()
    val creditsMarker by viewModel.creditsMarker.collectAsStateWithLifecycle()
    val recapMarker by viewModel.recapMarker.collectAsStateWithLifecycle()
    val previewMarker by viewModel.previewMarker.collectAsStateWithLifecycle()
    val nextEpisode by viewModel.nextEpisode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(streamInfo) {
        val info = streamInfo ?: return@LaunchedEffect
        // Look up seriesId from streamInfo or local DB
        val db = AppDatabase.getDatabase(context)
        val entity = db.jellyfinDao().getItemById(info.playableId)
        val seriesId = info.seriesId ?: entity?.seriesId
        viewModel.loadIntroAndNextEpisode(itemId = info.playableId, seriesId = seriesId)
    }

    val currentInfo = streamInfo
    val torrentState by viewModel.torrentOverlayState.collectAsStateWithLifecycle()
    if (torrentState != null) {
        BackHandler(enabled = true) {
            viewModel.cancelTorrentPreparation()
            onBack()
        }
        TorrentPreparationOverlay(
            state = torrentState!!,
            focusColor = focusColor,
            focusContent = focusContent,
            onCancel = {
                viewModel.cancelTorrentPreparation()
                onBack()
            },
            onRetry = {
                viewModel.retryTorrentPreparation()
            },
            onSelectVersion = { versionId ->
                viewModel.selectVersion(versionId)
            }
        )
    } else if (currentInfo != null) {
        key(currentInfo.playableId) {
            PlayerContent(
                info = currentInfo,
                buttonStyle = buttonStyle,
                isMonochrome = isMonochrome,
                focusColor = focusColor,
                focusContent = focusContent,
                viewModel = viewModel,
                introMarker = introMarker,
                creditsMarker = creditsMarker,
                recapMarker = recapMarker,
                previewMarker = previewMarker,
                nextEpisode = nextEpisode,
                onBack = onBack,
                onPlayNextEpisode = { nextEp ->
                    viewModel.loadStreamUrl(nextEp.itemId)
                }
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            JellyLoadingIndicator(
                size = 84.dp,
                message = stringResource(R.string.player_loading),
                isMonochrome = isMonochrome
            )
        }
    }
}

@OptIn(UnstableApi::class)
@ExperimentalTvMaterial3Api
@Composable
private fun PlayerContent(
    info: PlayerStreamInfo,
    buttonStyle: String,
    isMonochrome: Boolean,
    focusColor: Color,
    focusContent: Color,
    viewModel: PlayerViewModel,
    introMarker: SkipMarker? = null,
    creditsMarker: SkipMarker? = null,
    recapMarker: SkipMarker? = null,
    previewMarker: SkipMarker? = null,
    nextEpisode: NextEpisodeInfo? = null,
    onBack: () -> Unit,
    onPlayNextEpisode: (NextEpisodeInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val playerLifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    val nightModeAudio by viewModel.nightModeAudio.collectAsStateWithLifecycle()
    val audioVolumeBoostDb by viewModel.audioVolumeBoostDb.collectAsStateWithLifecycle()
    val audioPassthroughMode by viewModel.audioPassthroughMode.collectAsStateWithLifecycle()
    val subtitleSize by viewModel.subtitleSize.collectAsStateWithLifecycle()
    val subtitleBackground by viewModel.subtitleBackground.collectAsStateWithLifecycle()
    val autoFrameRateEnabled by viewModel.autoFrameRateEnabled.collectAsStateWithLifecycle()
    val autoPlayNextEpisodeEnabled by viewModel.autoPlayNextEpisodeEnabled.collectAsStateWithLifecycle()
    val autoPlayNextMovieEnabled by viewModel.autoPlayNextMovieEnabled.collectAsStateWithLifecycle()
    val fatalPlaybackError by viewModel.fatalPlaybackError.collectAsStateWithLifecycle()
    val torrentOverlayState by viewModel.torrentOverlayState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        AppForeground.isPlaybackActive = true
        onDispose {
            AppForeground.isPlaybackActive = false
        }
    }

    val audioEnhancementManager = remember { AudioEnhancementManager() }
    var currentVideoFps by remember { mutableFloatStateOf(0f) }

    var showOverlayControls by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubTargetPosition by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var lastScrubKeystrokeTime by remember { mutableLongStateOf(0L) }
    var activeModalTab by remember { mutableStateOf(PlayerModalTab.NONE) }
    var lastInteractionTrigger by remember { mutableLongStateOf(0L) }

    fun notifyInteraction() {
        lastInteractionTrigger = System.currentTimeMillis()
    }

    // Skip banners: intro, resumen, avance y créditos (TheIntroDB / MediaSegments)
    var showSkipIntroBanner by remember { mutableStateOf(false) }
    var showSkipCreditsBanner by remember { mutableStateOf(false) }
    var showSkipRecapBanner by remember { mutableStateOf(false) }
    var showSkipPreviewBanner by remember { mutableStateOf(false) }
    var introSkippedOrDismissed by remember(introMarker) { mutableStateOf(false) }
    var creditsSkippedOrDismissed by remember(creditsMarker) { mutableStateOf(false) }
    var recapSkippedOrDismissed by remember(recapMarker) { mutableStateOf(false) }
    var previewSkippedOrDismissed by remember(previewMarker) { mutableStateOf(false) }

    // Next-episode / movie overlay & auto exit state
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }
    var nextEpisodeCountdown by remember { mutableIntStateOf(-1) }
    var autoExitCountdown by remember { mutableIntStateOf(-1) }
    var playbackEnded by remember { mutableStateOf(false) }
    var nextEpisodeTriggered by remember { mutableStateOf(false) }

    val recommendedList by viewModel.recommendedList.collectAsStateWithLifecycle()

    // Ocultar controles inferiores cuando el overlay post-créditos está activo para no interferir con el foco
    LaunchedEffect(showNextEpisodeOverlay) {
        if (showNextEpisodeOverlay) {
            showOverlayControls = false
        }
    }

    // Visual shrink animation when post-credits recommendation card is visible
    val videoScale by animateFloatAsState(
        targetValue = if (showNextEpisodeOverlay && !isScrubbing) 0.54f else 1.0f,
        animationSpec = tween(durationMillis = 350),
        label = "videoScale"
    )
    val videoTranslationX by animateDpAsState(
        targetValue = if (showNextEpisodeOverlay && !isScrubbing) (-180).dp else 0.dp,
        animationSpec = tween(durationMillis = 350),
        label = "videoTranslationX"
    )
    val videoTranslationY by animateDpAsState(
        targetValue = if (showNextEpisodeOverlay && !isScrubbing) (-110).dp else 0.dp,
        animationSpec = tween(durationMillis = 350),
        label = "videoTranslationY"
    )

    // Resume dialog state: null = not yet decided, true = show dialog, false = decision made
    var showResumeDialog by remember { mutableStateOf(false) }
    var resumeDecisionMade by remember { mutableStateOf(false) }
    var chosenStartPositionMs by remember { mutableLongStateOf(0L) }

    // Show resume dialog immediately when streamInfo is loaded if user has saved progress (>=10s)
    LaunchedEffect(info) {
        if (info.startPositionMs >= 10_000L && !resumeDecisionMade) {
            showResumeDialog = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember(info.durationMs) { mutableLongStateOf(info.durationMs.coerceAtLeast(0L)) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }

    // Prevent Android TV from going to sleep / ambient mode while playing or viewing controls
    DisposableEffect(activity, isPlaying, showOverlayControls) {
        val keepOn = isPlaying || showOverlayControls
        if (keepOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var audioTracks by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackItem>>(emptyList()) }

    var currentPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var isBuffering by remember { mutableStateOf(true) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var audioAutoSelected by remember(info) { mutableStateOf(false) }
    var hasEverBeenReady by remember(info, retryTrigger) { mutableStateOf(false) }

    LaunchedEffect(fatalPlaybackError) {
        fatalPlaybackError?.let { message ->
            playerErrorMessage = message
            isBuffering = false
        }
    }

    val rewindFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val fastForwardFocusRequester = remember { FocusRequester() }
    val progressBarFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val audioButtonFocusRequester = remember { FocusRequester() }
    val subtitleButtonFocusRequester = remember { FocusRequester() }
    val versionButtonFocusRequester = remember { FocusRequester() }
    val settingsButtonFocusRequester = remember { FocusRequester() }
    val playerRootFocusRequester = remember { FocusRequester() }

    var isProgressBarFocused by remember { mutableStateOf(false) }
    var pendingFocusProgressBar by remember { mutableStateOf(false) }
    var scrubRepeatCount by remember { mutableIntStateOf(0) }

    var previousModalTab by remember { mutableStateOf(PlayerModalTab.NONE) }

    val candidates = info.candidateUrls.ifEmpty { listOf(info.primaryStreamUrl) }

    val exoPlayer = remember(info, retryTrigger, audioPassthroughMode) {
        var candidateIdx = 0

        val defaultHeaders = buildMap {
            val token = info.token
            val auth = info.authHeader
            if (token.isNotBlank()) {
                put("X-Emby-Token", token)
                put("X-MediaBrowser-Token", token)
            }
            if (auth.isNotBlank()) {
                put("Authorization", auth)
                put("X-Emby-Authorization", auth)
            } else if (token.isNotBlank()) {
                val genAuth = buildJellyfinAuthHeader(token = token)
                put("Authorization", genAuth)
                put("X-Emby-Authorization", genAuth)
            }
        }

        // Fuente OkHttp compartida con soporte IPv6, pool de conexiones y bypass SSL local
        val baseHttpDataSourceFactory = OkHttpDataSource.Factory(NetworkClientFactory.okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Google TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        // ResolvingDataSource: inyecta cabeceras de Jellyfin únicamente si la petición va a Jellyfin.
        // Si va dirigida directamente a un CDN externo o almacenamiento remoto, no envía cabeceras
        // de Jellyfin para evitar que el servidor remoto rechace la petición con 401/403.
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(baseHttpDataSourceFactory) { dataSpec ->
            val uriString = dataSpec.uri.toString()
            val isJellyfinRequest = uriString.contains("/Videos/", ignoreCase = true) ||
                    uriString.contains("/Audio/", ignoreCase = true) ||
                    uriString.contains("/Items/", ignoreCase = true)

            if (isJellyfinRequest && defaultHeaders.isNotEmpty()) {
                val mergedHeaders = HashMap(dataSpec.httpRequestHeaders)
                defaultHeaders.forEach { (k, v) ->
                    if (!mergedHeaders.containsKey(k)) {
                        mergedHeaders[k] = v
                    }
                }
                dataSpec.buildUpon().setHttpRequestHeaders(mergedHeaders).build()
            } else {
                dataSpec
            }
        }

        val effectiveAuth = defaultHeaders["Authorization"] ?: defaultHeaders["X-Emby-Authorization"]
        val extractorsFactory = SeekableExtractorsFactory(
            fallbackDurationMs = info.durationMs,
            authHeader = effectiveAuth,
            trustedHost = runCatching { android.net.Uri.parse(candidates.first()).host }.getOrNull(),
            allowAsyncCuesFetch = !info.isOnDemand
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(resolvingDataSourceFactory, extractorsFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 90_000,
                /* bufferForPlaybackMs = */ 3_000,
                /* bufferForPlaybackAfterRebufferMs = */ 8_000
            )
            .setBackBuffer(30_000, true)
            .build()

        val audioCapabilities = when (audioPassthroughMode) {
            AUDIO_PASSTHROUGH_AUTO, AUDIO_PASSTHROUGH_DIRECT -> AudioCapabilities.getCapabilities(context)
            else -> {
                // Modo decodificación local por software (FFmpeg -> PCM):
                // Permitimos PCM estéreo y multicanal (hasta 8 canales) para que pistas 5.1/7.1 se reproduzcan
                // sin ser bloqueadas por el sink de audio con límites estéreo.
                val encodings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    intArrayOf(
                        AudioFormat.ENCODING_PCM_16BIT,
                        AudioFormat.ENCODING_PCM_FLOAT,
                        AudioFormat.ENCODING_PCM_24BIT_PACKED,
                        AudioFormat.ENCODING_PCM_32BIT
                    )
                } else {
                    intArrayOf(
                        AudioFormat.ENCODING_PCM_16BIT,
                        AudioFormat.ENCODING_PCM_FLOAT
                    )
                }
                AudioCapabilities(encodings, 8)
            }
        }

        val extensionRendererMode = if (audioPassthroughMode == AUDIO_PASSTHROUGH_OFF) {
            // Decodificación local FFmpeg en software prioritaria: evita códecs defectuosos/silenciosos de fabricantes de TV
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        } else {
            // Passthrough HDMI: permite que el hardware intente bitstream directo si el receptor/soundbar lo soporta
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(audioCapabilities)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(audioEnhancementManager.audioProcessor))
                    .build()
            }
        }
            .setExtensionRendererMode(extensionRendererMode)
            .setEnableDecoderFallback(true)

        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage("es")
                .setPreferredTextLanguage("es")
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekForwardIncrementMs(10_000L)
            .setSeekBackIncrementMs(10_000L)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(audioAttributes, true)
            .build().apply {
                // EventLogger sólo en builds depurables: en release inundaba logcat y añadía coste.
                val isDebuggable = (context.applicationInfo.flags and
                        ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (isDebuggable) {
                    addAnalyticsListener(EventLogger())
                }
                Log.i("PlayerScreen", "ExoPlayer configured (passthroughMode=$audioPassthroughMode, extMode=$extensionRendererMode)")

                // Subtítulos sidecar de Jellyfin: sin esto el menú no ofrece ninguna pista
                // externa y el usuario solo ve las incrustadas en el contenedor.
                val subtitleConfigurations = info.subtitles.mapNotNull { sub ->
                    val mimeType = when (sub.codec.trim().lowercase()) {
                        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                        "ass", "ssa" -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }
                    runCatching {
                        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                            .setMimeType(mimeType)
                            .setLanguage(sub.language.ifBlank { null })
                            .setLabel(sub.title.ifBlank { null })
                            .setSelectionFlags(if (sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    }.getOrNull()
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(candidates.first())
                    .setSubtitleConfigurations(subtitleConfigurations)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(info.title)
                            .setDisplayTitle(info.title)
                            .build()
                    )
                    .build()

                setMediaItem(mediaItem)

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (playing) {
                            viewModel.reportStart(currentPosition.coerceAtLeast(0L))
                        } else if (playerLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            // Pausa del usuario con el mando, sin salir del reproductor.
                            viewModel.reportPaused(currentPosition.coerceAtLeast(0L))
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = (playbackState == Player.STATE_BUFFERING)
                        if (playbackState == Player.STATE_READY) {
                            hasEverBeenReady = true
                            playerErrorMessage = null
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        val (audios, subs) = extractTracksFromExoPlayer(this@apply, context, info)
                        audioTracks = audios
                        subtitleTracks = subs

                        // En DirectPlay, Jellyfin puede devolver varias pistas del mismo idioma y
                        // códec (p. ej. AC3 5.1 y AC3 2.0). Forzamos una sola vez la pista que
                        // eligió la negociación (idioma+códec+canales+default).
                        if (!audioAutoSelected && audios.isNotEmpty()) {
                            val preferredIndex = info.audioTracks.firstOrNull { it.isDefault }?.index
                            val selectedIndex = audios.firstOrNull { it.isSelected }?.jellyfinStreamIndex
                            if (preferredIndex != null && selectedIndex != preferredIndex) {
                                audios.firstOrNull { it.jellyfinStreamIndex == preferredIndex }?.let { preferred ->
                                    selectAudioTrackInExoPlayer(this@apply, preferred)
                                }
                            }
                            audioAutoSelected = true
                        }

                        for (trackGroup in tracks.groups) {
                            if (trackGroup.type == C.TRACK_TYPE_VIDEO && trackGroup.isSelected) {
                                for (i in 0 until trackGroup.length) {
                                    if (trackGroup.isTrackSelected(i)) {
                                        val fps = trackGroup.getTrackFormat(i).frameRate
                                        if (fps > 0f) {
                                            currentVideoFps = fps
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val sanitizedCurrentUrl = tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder.sanitizeUrlForLog(candidates.getOrNull(candidateIdx))
                        Log.e("PlayerScreen", "ExoPlayer error on candidate $candidateIdx ($sanitizedCurrentUrl): ${error.errorCodeName}", error)

                        // Fallo de decoder: se distingue audio y vídeo para no gastar el
                        // reintento de audio cuando el que falla es el vídeo.
                        val decoderFailure = classifyDecoderFailure(error)
                        val isAudioDecoderFailure = decoderFailure == DecoderFailureKind.AUDIO
                        val isVideoDecoderFailure = decoderFailure == DecoderFailureKind.VIDEO

                        if (isAudioDecoderFailure) {
                            Log.w(
                                "PlayerScreen",
                                "Audio decoder failed (${error.errorCodeName}). Retrying once with server-side audio transcode."
                            )
                            chosenStartPositionMs = currentPosition.coerceAtLeast(0L)
                            isBuffering = true
                            viewModel.retryWithTranscoding(
                                forceAudioTranscodeOnly = true,
                                resumePositionMs = chosenStartPositionMs
                            )
                            return
                        }

                        // Si el decodificador de vídeo falla (init o ERROR_CODE_DECODING_FAILED /
                        // capacidades superadas en 4K/DV), primero intentamos otra versión compatible
                        // (p. ej. 1080p) y si no hay, pedimos transcodificación completa al servidor.
                        if (isVideoDecoderFailure) {
                            chosenStartPositionMs = currentPosition.coerceAtLeast(0L)
                            isBuffering = true
                            if (viewModel.tryFallbackToCompatibleVersion(resumePositionMs = chosenStartPositionMs)) {
                                Log.w(
                                    "PlayerScreen",
                                    "Video decoder failed (${error.errorCodeName}). Switching to compatible media version."
                                )
                                return
                            }
                            Log.w(
                                "PlayerScreen",
                                "Video decoder failed (${error.errorCodeName}). Retrying once with full server transcode."
                            )
                            viewModel.retryWithTranscoding(
                                forceFullTranscode = true,
                                resumePositionMs = chosenStartPositionMs
                            )
                            return
                        }

                        // Si el fallo es del contenedor
                        val isVideoOrCodecError =
                            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

                        if (isVideoOrCodecError && candidateIdx >= candidates.size - 1) {
                            Log.w("PlayerScreen", "Decodificador de vídeo falló o formato no soportado. Solicitando transcodificación completa HLS al servidor.")
                            chosenStartPositionMs = currentPosition.coerceAtLeast(0L)
                            viewModel.retryWithTranscoding(forceFullTranscode = true, resumePositionMs = chosenStartPositionMs)
                            return
                        }

                        val httpCode = extractHttpResponseCode(error)
                        val isTimeoutOrSeekError =
                            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                        val isLastCandidate = candidateIdx >= candidates.size - 1
                        if (
                            shouldRecoverOnDemandError(
                                isOnDemand = info.isOnDemand,
                                isAudioDecoderFailure = isAudioDecoderFailure,
                                isDecoderInitFailure = decoderFailure != DecoderFailureKind.NONE,
                                isContainerError = isVideoOrCodecError,
                                httpCode = httpCode,
                                isTimeoutOrSeekError = isTimeoutOrSeekError,
                                isLastCandidate = isLastCandidate,
                                recoveryAttempts = viewModel.currentOnDemandRecoveryAttempts()
                            )
                        ) {
                            val resumePos = currentPosition.coerceAtLeast(0L)
                            if (
                                viewModel.tryRecoverOnDemandPlayback(
                                    resumePositionMs = resumePos,
                                    httpCode = httpCode,
                                    isTimeoutOrSeekError = isTimeoutOrSeekError
                                )
                            ) {
                                Log.w("PlayerScreen", "Error recuperable TOD (http=$httpCode, code=${error.errorCodeName}); comprobando estado en servidor")
                                chosenStartPositionMs = resumePos
                                isBuffering = true
                                return
                            }
                        }

                        candidateIdx++
                        if (candidateIdx < candidates.size) {
                            val nextUrl = candidates[candidateIdx]
                            val sanitizedNext = tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder.sanitizeUrlForLog(nextUrl)
                            Log.i("PlayerScreen", "Attempting fallback candidate $candidateIdx: $sanitizedNext")
                            val pos = currentPosition.coerceAtLeast(0L)

                            val fallbackItem = MediaItem.Builder()
                                .setUri(nextUrl)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(info.title)
                                        .setDisplayTitle(info.title)
                                        .build()
                                )
                                .build()
                            setMediaItem(fallbackItem)
                            if (pos > 0) seekTo(pos)
                            prepare()
                            playWhenReady = true
                        } else {
                            if (!isVideoOrCodecError && !info.isStrm) {
                                Log.w("PlayerScreen", "Todos los candidatos directos fallaron. Último recurso: renegociar transcodificación en Jellyfin.")
                                chosenStartPositionMs = currentPosition.coerceAtLeast(0L)
                                viewModel.retryWithTranscoding(forceFullTranscode = true, resumePositionMs = chosenStartPositionMs)
                                return
                            }
                            Log.e("PlayerScreen", "All ${candidates.size} playback candidates failed!")
                            val appContext = context.applicationContext
                            val humanMsg = when {
                                httpCode in 500..599 -> appContext.getString(R.string.player_error_http_5xx, httpCode)
                                httpCode == 401 -> appContext.getString(R.string.player_error_http_401)
                                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> appContext.getString(R.string.player_error_bad_http)
                                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> appContext.getString(R.string.player_error_network)
                                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> appContext.getString(R.string.player_error_container)
                                decoderFailure != DecoderFailureKind.NONE -> appContext.getString(R.string.player_decoder_unsupported)
                                else -> error.message ?: error.errorCodeName
                            }
                            playerErrorMessage = humanMsg
                            isBuffering = false
                        }
                    }
                })

                // Only prepare ExoPlayer immediately if no resume decision is required
                if (resumeDecisionMade || info.startPositionMs < 10_000L) {
                    val startAt = if (chosenStartPositionMs > 0) chosenStartPositionMs else info.startPositionMs
                    if (startAt > 0) {
                        seekTo(startAt)
                    }
                    prepare()
                    playWhenReady = true
                }
            }
    }

    /**
     * Salida real de la pantalla: persiste el progreso y cierra la sesión de Jellyfin antes
     * de navegar. El onDispose del player solo guarda progreso para no matar el transcode
     * cuando el player se recrea por un fallback.
     */
    fun exitPlayer() {
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: info.durationMs.coerceAtLeast(0L)
        viewModel.finishPlayback(pos, dur)
        onBack()
    }

    // Integración de MediaSession (androidx.media3.session) para Android TV:
    // Permite que los botones físicos del mando de TV (Play, Pause, FastForward, Rewind),
    // mandos Bluetooth / HDMI-CEC y comandos por voz de Google Assistant ("Ok Google, pausa")
    // controlen la reproducción nativamente y mantengan el estado global en el sistema.
    val mediaSession = remember(exoPlayer) {
        runCatching {
            val sessionActivityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                context,
                0,
                sessionActivityIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            MediaSession.Builder(context, exoPlayer)
                .setId("MedusaMediaSession_${info.playableId.ifBlank { "main" }}_${System.identityHashCode(exoPlayer)}")
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
        }.getOrNull()
    }

    LaunchedEffect(activeModalTab) {
        if (activeModalTab != PlayerModalTab.NONE) {
            val (audios, subs) = extractTracksFromExoPlayer(exoPlayer, context, info)
            if (audios.isNotEmpty()) audioTracks = audios
            if (subs.isNotEmpty()) subtitleTracks = subs
        } else if (previousModalTab != PlayerModalTab.NONE) {
            delay(100L)
            runCatching {
                when (previousModalTab) {
                    PlayerModalTab.AUDIO -> audioButtonFocusRequester.requestFocus()
                    PlayerModalTab.SUBTITLES -> subtitleButtonFocusRequester.requestFocus()
                    PlayerModalTab.VERSIONS -> versionButtonFocusRequester.requestFocus()
                    PlayerModalTab.SETTINGS -> settingsButtonFocusRequester.requestFocus()
                    else -> playPauseFocusRequester.requestFocus()
                }
            }
        }
        previousModalTab = activeModalTab
    }

    LaunchedEffect(exoPlayer) {
        audioEnhancementManager.attachToPlayer(exoPlayer)
    }

    LaunchedEffect(nightModeAudio, audioVolumeBoostDb) {
        audioEnhancementManager.setNightMode(nightModeAudio)
        audioEnhancementManager.setVolumeBoost(audioVolumeBoostDb)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // En tablets/móvil, el background entra en PiP y la reproducción continúa.
                    // En TV no se activa: allí la app nunca se "minimiza" y el mando manda.
                    val canUsePip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        tv.medusa.app.util.DeviceUtils.isTouchDevice(context) &&
                        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
                        activity?.isInPictureInPictureMode == false
                    if (canUsePip && exoPlayer.isPlaying) {
                        runCatching {
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                            activity?.enterPictureInPictureMode(params)
                        }
                    } else if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val dur = exoPlayer.duration.takeIf { it > 0 } ?: info.durationMs.coerceAtLeast(0L)
                        viewModel.reportProgress(pos, isPaused = true, dur)
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    // Spec: la sesión se cierra al pasar a segundo plano.
                    exoPlayer.pause()
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration.takeIf { it > 0 } ?: info.durationMs.coerceAtLeast(0L)
                    viewModel.finishPlayback(pos, dur)
                }
                Lifecycle.Event.ON_START -> viewModel.resumeSessionAfterBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            audioEnhancementManager.release()
        }
    }

    LaunchedEffect(autoFrameRateEnabled, currentVideoFps, isPlaying, activity) {
        if (activity != null) {
            // Solo sincronizar la tasa de refresco cuando el vídeo ya esté en reproducción activa.
            // Conmutar el modo de pantalla durante la carga inicial destruye el Surface de la TV
            // y cancela la descarga del segmento de inicialización (-1.mp4).
            if (autoFrameRateEnabled && currentVideoFps > 0f && isPlaying) {
                AutoFrameRateManager.matchFrameRate(activity, currentVideoFps)
            } else if (!autoFrameRateEnabled) {
                AutoFrameRateManager.restoreOriginal(activity)
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                AutoFrameRateManager.restoreOriginal(activity)
            }
        }
    }

    fun performSeek(targetMs: Long) {
        notifyInteraction()
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: duration.takeIf { it > 0 } ?: info.durationMs.coerceAtLeast(0L)
        val target = if (dur > 0) targetMs.coerceIn(0L, dur) else targetMs.coerceAtLeast(0L)

        // Seek nativo con HTTP Range (206) contra el servidor Jellyfin
        Log.i("PlayerScreen", "performSeek target=${target}ms (duration=${dur}ms, isSeekable=${exoPlayer.isCurrentMediaItemSeekable})")
        if (exoPlayer.playbackState != Player.STATE_IDLE && !exoPlayer.isCurrentMediaItemSeekable) {
            Log.w("PlayerScreen", "Seek requested on media reported unseekable by container; forcing position at ${target}ms")
            chosenStartPositionMs = target
        }
        exoPlayer.seekTo(target)
        currentPosition = target

        scrubTargetPosition = target
        isScrubbing = false
        scrubDeltaMs = 0L
        scrubRepeatCount = 0
    }

    fun commitScrub() {
        if (isScrubbing) {
            performSeek(scrubTargetPosition)
        }
    }

    fun seekToExactPosition(targetMs: Long) {
        performSeek(targetMs)
    }

    fun updateScrubPosition(targetMs: Long) {
        notifyInteraction()
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: duration.takeIf { it > 0 } ?: info.durationMs.coerceAtLeast(0L)
        val target = if (dur > 0) targetMs.coerceIn(0L, dur) else targetMs.coerceAtLeast(0L)
        val cur = exoPlayer.currentPosition.coerceAtLeast(0L)
        scrubTargetPosition = target
        scrubDeltaMs = target - cur
        isScrubbing = true
        lastScrubKeystrokeTime = System.currentTimeMillis()
    }

    fun scrubStep(deltaSign: Int) {
        notifyInteraction()
        val now = System.currentTimeMillis()
        val cur = if (isScrubbing) scrubTargetPosition else exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: duration.takeIf { it > 0 } ?: info.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE

        val timeSinceLast = now - lastScrubKeystrokeTime
        if (!isScrubbing || timeSinceLast > 350L || (scrubDeltaMs > 0 && deltaSign < 0) || (scrubDeltaMs < 0 && deltaSign > 0)) {
            scrubRepeatCount = 0
        } else {
            scrubRepeatCount++
        }
        lastScrubKeystrokeTime = now

        val baseStep = when {
            scrubRepeatCount < 4 -> 5_000L
            scrubRepeatCount < 11 -> 15_000L
            scrubRepeatCount < 21 -> 30_000L
            else -> 60_000L
        }

        val effectiveDelta = baseStep * deltaSign
        if (!isScrubbing || (scrubDeltaMs > 0 && deltaSign < 0) || (scrubDeltaMs < 0 && deltaSign > 0)) {
            scrubDeltaMs = effectiveDelta
            scrubTargetPosition = (cur + effectiveDelta).coerceIn(0L, dur)
        } else {
            scrubDeltaMs += effectiveDelta
            scrubTargetPosition = (scrubTargetPosition + effectiveDelta).coerceIn(0L, dur)
        }
        isScrubbing = true
    }

    fun seekRelative(deltaMs: Long) {
        notifyInteraction()
        val now = System.currentTimeMillis()
        val cur = if (isScrubbing) scrubTargetPosition else exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: duration.takeIf { it > 0 } ?: info.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE

        val timeSinceLast = now - lastScrubKeystrokeTime
        val effectiveDelta = if (timeSinceLast < 280L && ((scrubDeltaMs > 0 && deltaMs > 0) || (scrubDeltaMs < 0 && deltaMs < 0))) {
            deltaMs * 2
        } else {
            deltaMs
        }

        if (!isScrubbing || (scrubDeltaMs > 0 && deltaMs < 0) || (scrubDeltaMs < 0 && deltaMs > 0)) {
            scrubDeltaMs = effectiveDelta
            scrubTargetPosition = (cur + effectiveDelta).coerceIn(0L, dur)
        } else {
            scrubDeltaMs += effectiveDelta
            scrubTargetPosition = (scrubTargetPosition + effectiveDelta).coerceIn(0L, dur)
        }
        lastScrubKeystrokeTime = now
        isScrubbing = true
    }

    // Debounce del seek: ejecuta el salto real en ExoPlayer 400 ms después de que el usuario deja de pulsar
    LaunchedEffect(scrubTargetPosition, isScrubbing, lastScrubKeystrokeTime) {
        if (isScrubbing) {
            delay(400L)
            if (isScrubbing) {
                performSeek(scrubTargetPosition)
            }
        }
    }

    // Auto-focus retry button if an error occurs
    LaunchedEffect(playerErrorMessage) {
        if (playerErrorMessage != null) {
            delay(150L)
            try {
                retryFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Auto-focus the play/pause button (or progress bar if opened via UP) when controls overlay is visible,
    // and restore focus to the root player container when controls auto-hide or close, ensuring D-Pad remote events
    // are always captured by onKeyEvent even when controls overlay is not in the composition tree.
    LaunchedEffect(
        showOverlayControls,
        showResumeDialog,
        activeModalTab,
        showSkipIntroBanner,
        showSkipCreditsBanner,
        showSkipRecapBanner,
        showSkipPreviewBanner,
        showNextEpisodeOverlay
    ) {
        if (showResumeDialog) return@LaunchedEffect

        if (showOverlayControls && playerErrorMessage == null) {
            delay(50L)
            try {
                if (pendingFocusProgressBar) {
                    progressBarFocusRequester.requestFocus()
                    pendingFocusProgressBar = false
                } else {
                    playPauseFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        } else if (!showOverlayControls &&
            activeModalTab == PlayerModalTab.NONE &&
            !showSkipIntroBanner &&
            !showSkipCreditsBanner &&
            !showSkipRecapBanner &&
            !showSkipPreviewBanner &&
            !showNextEpisodeOverlay
        ) {
            delay(50L)
            try {
                playerRootFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Auto-hide controls overlay after 5s (or 9s if timeline is focused) if activeModalTab is NONE (resets on user interaction)
    LaunchedEffect(showOverlayControls, activeModalTab, lastInteractionTrigger, isProgressBarFocused, isScrubbing) {
        if (showOverlayControls && activeModalTab == PlayerModalTab.NONE && !isScrubbing && !playbackEnded) {
            val timeout = if (isProgressBarFocused) 9000L else 5000L
            delay(timeout)
            if (!isScrubbing && !playbackEnded) {
                showOverlayControls = false
            }
        }
    }

    BackHandler {
        if (showResumeDialog) {
            exitPlayer()
        } else if (activeModalTab != PlayerModalTab.NONE) {
            activeModalTab = PlayerModalTab.NONE
        } else if (showNextEpisodeOverlay) {
            creditsSkippedOrDismissed = true
            showNextEpisodeOverlay = false
            if (playbackEnded) {
                exitPlayer()
            } else {
                nextEpisodeCountdown = -1
            }
        } else if (showSkipIntroBanner || showSkipCreditsBanner || showSkipRecapBanner || showSkipPreviewBanner) {
            introSkippedOrDismissed = true
            creditsSkippedOrDismissed = true
            recapSkippedOrDismissed = true
            previewSkippedOrDismissed = true
            showSkipIntroBanner = false
            showSkipCreditsBanner = false
            showSkipRecapBanner = false
            showSkipPreviewBanner = false
        } else if (isScrubbing) {
            isScrubbing = false
            scrubDeltaMs = 0L
            scrubRepeatCount = 0
        } else if (showOverlayControls) {
            showOverlayControls = false
        } else {
            exitPlayer()
        }
    }

    // 75-second timeout for initial slow proxy resolution (resolving RD / rescue can take 10-40s)
    // Si el ítem es On-Demand (TOD), se eleva a TORRENT_PREP_TIMEOUT_MS (15 min) para no cortar la descarga.
    // Tampoco debe correr mientras el overlay de preparación TOD esté activo.
    val isTodPreparing = torrentOverlayState != null &&
            torrentOverlayState?.state != "ERROR" &&
            torrentOverlayState?.state != "UNAVAILABLE"
    val timeoutMs = playerResolveTimeoutMs(info.isOnDemand)
    LaunchedEffect(info, retryTrigger, isTodPreparing) {
        if (isTodPreparing) return@LaunchedEffect
        delay(timeoutMs)
        if (!hasEverBeenReady && exoPlayer.playbackState == Player.STATE_BUFFERING && exoPlayer.currentPosition <= 0L) {
            playerErrorMessage = context.applicationContext.getString(R.string.player_resolve_timeout)
            isBuffering = false
        }
    }

    // Vigilante de arranque: si no entra ni un byte, reintentar la resolución una vez.
    var stallRetried by remember(info, retryTrigger) { mutableStateOf(false) }
    LaunchedEffect(info, retryTrigger, isTodPreparing) {
        if (isTodPreparing || stallRetried) return@LaunchedEffect
        var waited = 0L
        while (waited < PLAYER_STALL_TIMEOUT_MS) {
            delay(1_000L)
            waited += 1_000L
            if (hasEverBeenReady || exoPlayer.totalBufferedDuration > 0L) return@LaunchedEffect
            if (exoPlayer.playbackState != Player.STATE_BUFFERING) return@LaunchedEffect
        }
        if (!hasEverBeenReady && exoPlayer.totalBufferedDuration <= 0L) {
            stallRetried = true
            val resumePos = exoPlayer.currentPosition.coerceAtLeast(0L)
            Log.w("PlayerScreen", "Sin datos tras ${PLAYER_STALL_TIMEOUT_MS} ms en el reproductor; reintentando resolución")
            if (info.isOnDemand) {
                viewModel.tryRecoverOnDemandPlayback(resumePositionMs = resumePos, isTimeoutOrSeekError = true)
            } else {
                viewModel.retryWithTranscoding(
                    resumePositionMs = resumePos,
                    consumeRetryBudget = false
                )
            }
        }
    }

    LaunchedEffect(info, isBuffering, isTodPreparing, hasEverBeenReady) {
        if (!info.isOnDemand || isTodPreparing || !hasEverBeenReady || !isBuffering) return@LaunchedEffect
        delay(PLAYER_STALL_TIMEOUT_MS)
        if (exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_BUFFERING && isBuffering) {
            val resumePos = exoPlayer.currentPosition.coerceAtLeast(0L)
            Log.w("PlayerScreen", "Rebuffer prolongado On-Demand (${PLAYER_STALL_TIMEOUT_MS} ms); comprobando estado TOD")
            viewModel.tryRecoverOnDemandPlayback(resumePositionMs = resumePos, isTimeoutOrSeekError = true)
        }
    }

    // Posición/buffer/duración en vivo para el HUD, los banners y la cuenta atrás del siguiente
    // episodio. Sólo late mientras la UI consume esos valores; en reposo (sin overlay ni
    // marcadores) no escribe estado y, por tanto, no provoca recomposición.
    // nextEpisode suele estar presente desde el inicio: usarlo aquí mantenía el bucle
    // de 500 ms vivo durante toda la reproducción. Solo hace falta con HUD, scrubbing,
    // marcadores activos del plugin o cuenta atrás del siguiente episodio visible.
    val needsLivePosition = showOverlayControls || isScrubbing || showNextEpisodeOverlay ||
            introMarker != null || creditsMarker != null || recapMarker != null || previewMarker != null
    LaunchedEffect(exoPlayer, needsLivePosition) {
        if (!needsLivePosition) return@LaunchedEffect
        while (isActive) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            val exoDur = exoPlayer.duration
            duration = if (exoDur > 0) exoDur else info.durationMs.coerceAtLeast(0L)
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            delay(500L)
        }
    }

    // Report de progreso a Jellyfin cada 10 s durante reproducción activa (no toca estado de Compose).
    LaunchedEffect(exoPlayer) {
        var lastReportedMs = 0L
        var lastWasPlaying = false

        while (isActive) {
            val playing = exoPlayer.isPlaying
            val state = exoPlayer.playbackState
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)

            val isActivelyPlaying = exoPlayer.playerError == null && state == Player.STATE_READY && playing
            if (isActivelyPlaying && pos > 0) {
                val now = System.currentTimeMillis()
                val stateChanged = playing != lastWasPlaying
                if (stateChanged || (now - lastReportedMs) >= 10_000L) {
                    val durReport = exoPlayer.duration.takeIf { it > 0 } ?: info.durationMs.coerceAtLeast(0L)
                    viewModel.reportProgress(pos, isPaused = false, durationMs = durReport)
                    lastReportedMs = now
                    lastWasPlaying = playing
                }
            }

            delay(1000L)
        }
    }

    // Valores actualizados para el listener: nextEpisode/creditsMarker llegan después de crear
    // el player; sin rememberUpdatedState la lambda capturaría valores obsoletos.
    val latestNextEpisode by rememberUpdatedState(nextEpisode)
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnPlayNextEpisode by rememberUpdatedState(onPlayNextEpisode)

    fun playNext(ep: NextEpisodeInfo) {
        if (nextEpisodeTriggered) return
        nextEpisodeTriggered = true
        showNextEpisodeOverlay = false
        latestOnPlayNextEpisode(ep)
    }

    DisposableEffect(exoPlayer) {
        // Un único listener para discontinuidades, fin de reproducción y buffer; se elimina en
        // onDispose (antes se añadía uno en un LaunchedEffect y nunca se retiraba).
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                currentPosition = newPosition.positionMs.coerceAtLeast(0L)
                val exoDur = exoPlayer.duration
                duration = if (exoDur > 0) exoDur else info.durationMs.coerceAtLeast(0L)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
                val exoDur = player.duration
                duration = if (exoDur > 0) exoDur else info.durationMs.coerceAtLeast(0L)
                bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val dur = exoPlayer.duration.takeIf { it > 0 } ?: info.durationMs.coerceAtLeast(1L)
                    viewModel.reportStopped(dur, dur)
                    currentPosition = dur
                    duration = dur
                    playbackEnded = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            runCatching { mediaSession?.release() }
            exoPlayer.removeListener(listener)
            val finalPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val totalDur = exoPlayer.duration.takeIf { it > 0 } ?: info.durationMs.coerceAtLeast(0L)
            // El player se recrea al cambiar de versión, de pista o al hacer fallback.
            // No cerramos la sesión aquí: solo guardamos progreso. El Stopped real se envía
            // al salir de la pantalla (exitPlayer) o en onCleared().
            viewModel.onPlayerReleased(finalPos, totalDur)
            exoPlayer.release()
        }
    }

    // ─── Fin de reproducción (STATE_ENDED) ──────────────────────────────────
    // Si hay siguiente episodio: cuenta atrás de 5s y salto automático.
    // Si es película o último capítulo: mantener controles en pantalla para que el usuario decida,
    // nunca expulsar abruptamente al usuario al menú principal.
    LaunchedEffect(playbackEnded) {
        if (!playbackEnded) return@LaunchedEffect

        var next = latestNextEpisode
        if (next == null) {
            Log.i("PlayerScreen", "Playback ended without next media cached; refreshing…")
            isBuffering = true
            next = runCatching {
                viewModel.refreshNextEpisode(info.playableId, info.seriesId)
            }.getOrNull() ?: latestNextEpisode
            isBuffering = false
        }

        Log.i("PlayerScreen", "Playback ended. nextMedia=${next?.itemId ?: "none"}")
        if (next != null) {
            showOverlayControls = false
            showNextEpisodeOverlay = true
            for (sec in 10 downTo 1) {
                if (!isActive || !showNextEpisodeOverlay || nextEpisodeTriggered) break
                nextEpisodeCountdown = sec
                delay(1000L)
            }
            if (isActive && showNextEpisodeOverlay && !nextEpisodeTriggered) {
                val allowAutoPlay = if (next.isMovie) autoPlayNextMovieEnabled else autoPlayNextEpisodeEnabled
                if (allowAutoPlay) {
                    nextEpisodeCountdown = 0
                    playNext(next)
                }
            } else if (isActive && !nextEpisodeTriggered) {
                // Si la sugerencia se canceló/descartó al finalizar -> auto-salir
                latestOnBack()
            }
        } else {
            // Sin siguiente película o episodio: iniciar temporizador de salida automática (10s)
            nextEpisodeCountdown = -1
            showOverlayControls = true
            showNextEpisodeOverlay = false
            for (sec in 10 downTo 1) {
                if (!isActive) break
                autoExitCountdown = sec
                delay(1000L)
            }
            if (isActive) {
                latestOnBack()
            }
        }
    }

    // ─── Intro / Credits banner ─────────────────────────────────────────────
    LaunchedEffect(introMarker) {
        val marker = introMarker
        if (marker == null) {
            showSkipIntroBanner = false
            return@LaunchedEffect
        }
        snapshotFlow { currentPosition }.collect { pos ->
            if (pos < marker.showAtMs) {
                introSkippedOrDismissed = false
            }
            val inWindow = !introSkippedOrDismissed && pos >= marker.showAtMs && pos < marker.hideAtMs
            if (inWindow != showSkipIntroBanner) {
                showSkipIntroBanner = inWindow
            }
        }
    }

    LaunchedEffect(creditsMarker) {
        val marker = creditsMarker
        if (marker == null) {
            showSkipCreditsBanner = false
            return@LaunchedEffect
        }
        snapshotFlow { currentPosition }.collect { pos ->
            if (pos < marker.showAtMs) {
                creditsSkippedOrDismissed = false
            }
            val inWindow = !creditsSkippedOrDismissed && pos >= marker.showAtMs && pos < marker.hideAtMs
            if (nextEpisode == null && inWindow != showSkipCreditsBanner) {
                showSkipCreditsBanner = inWindow
            }
        }
    }

    LaunchedEffect(recapMarker) {
        val marker = recapMarker
        if (marker == null) {
            showSkipRecapBanner = false
            return@LaunchedEffect
        }
        snapshotFlow { currentPosition }.collect { pos ->
            if (pos < marker.showAtMs) recapSkippedOrDismissed = false
            val inWindow = !recapSkippedOrDismissed && pos >= marker.showAtMs && pos < marker.hideAtMs
            if (inWindow != showSkipRecapBanner) showSkipRecapBanner = inWindow
        }
    }

    LaunchedEffect(previewMarker) {
        val marker = previewMarker
        if (marker == null) {
            showSkipPreviewBanner = false
            return@LaunchedEffect
        }
        snapshotFlow { currentPosition }.collect { pos ->
            if (pos < marker.showAtMs) previewSkippedOrDismissed = false
            val inWindow = !previewSkippedOrDismissed && pos >= marker.showAtMs && pos < marker.hideAtMs
            if (inWindow != showSkipPreviewBanner) showSkipPreviewBanner = inWindow
        }
    }

    // ─── Next episode overlay durante créditos ──────────────────────────────
    LaunchedEffect(creditsMarker, nextEpisode) {
        val next = nextEpisode
        if (next == null) {
            if (showNextEpisodeOverlay && !playbackEnded) showNextEpisodeOverlay = false
            return@LaunchedEffect
        }

        val marker = creditsMarker
        if (marker != null) {
            snapshotFlow { currentPosition }.collect { pos ->
                if (playbackEnded || nextEpisodeTriggered) return@collect
                if (pos < marker.showAtMs) {
                    creditsSkippedOrDismissed = false
                }
                if (!creditsSkippedOrDismissed) {
                    val inRange = pos >= marker.showAtMs && pos < marker.hideAtMs
                    if (inRange) {
                        nextEpisodeCountdown = ((marker.hideAtMs - pos) / 1000L).toInt().coerceAtLeast(0)
                        if (!showNextEpisodeOverlay) showNextEpisodeOverlay = true
                    } else if (pos >= marker.hideAtMs) {
                        if (showNextEpisodeOverlay) {
                            val allowAutoPlay = if (next.isMovie) autoPlayNextMovieEnabled else autoPlayNextEpisodeEnabled
                            if (allowAutoPlay) {
                                playNext(next)
                            }
                        }
                    } else {
                        if (showNextEpisodeOverlay && !playbackEnded) showNextEpisodeOverlay = false
                    }
                }
            }
        }
    }

    // Cuando el countdown llega a 0 (caso A: créditos terminaron), lanzar siguiente episodio/película si está habilitado
    LaunchedEffect(nextEpisodeCountdown) {
        val next = nextEpisode
        if (nextEpisodeCountdown == 0 && showNextEpisodeOverlay && next != null) {
            val allowAutoPlay = if (next.isMovie) autoPlayNextMovieEnabled else autoPlayNextEpisodeEnabled
            if (allowAutoPlay) {
                playNext(next)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerRootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (showResumeDialog) return@onKeyEvent false
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                when (keyEvent.key) {
                    Key.MediaRewind, Key.MediaStepBackward, Key.MediaSkipBackward -> {
                        val cur = if (isScrubbing) scrubTargetPosition else currentPosition
                        val target = (cur - 10_000L).coerceAtLeast(0L)
                        performSeek(target)
                        true
                    }
                    Key.MediaFastForward, Key.MediaStepForward, Key.MediaSkipForward -> {
                        val cur = if (isScrubbing) scrubTargetPosition else currentPosition
                        val target = cur + 10_000L
                        performSeek(target)
                        true
                    }
                    Key.MediaPlayPause -> {
                        notifyInteraction()
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        true
                    }
                    Key.MediaPlay -> {
                        notifyInteraction()
                        exoPlayer.play()
                        true
                    }
                    Key.MediaPause -> {
                        notifyInteraction()
                        exoPlayer.pause()
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!showOverlayControls && activeModalTab == PlayerModalTab.NONE) {
                            seekRelative(-10_000L)
                            true
                        } else {
                            notifyInteraction()
                            false
                        }
                    }
                    Key.DirectionRight -> {
                        if (!showOverlayControls && activeModalTab == PlayerModalTab.NONE) {
                            seekRelative(+10_000L)
                            true
                        } else {
                            notifyInteraction()
                            false
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (isScrubbing) {
                            performSeek(scrubTargetPosition)
                            notifyInteraction()
                            true
                        } else if (!showOverlayControls) {
                            showOverlayControls = true
                            notifyInteraction()
                            true
                        } else {
                            notifyInteraction()
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (!showOverlayControls) {
                            pendingFocusProgressBar = true
                            showOverlayControls = true
                            notifyInteraction()
                            true
                        } else {
                            notifyInteraction()
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        if (!showOverlayControls) {
                            showOverlayControls = true
                            notifyInteraction()
                            true
                        } else if (activeModalTab == PlayerModalTab.NONE) {
                            if (isProgressBarFocused) {
                                notifyInteraction()
                                playPauseFocusRequester.requestFocus()
                                true
                            } else {
                                showOverlayControls = false
                                notifyInteraction()
                                true
                            }
                        } else {
                            notifyInteraction()
                            false
                        }
                    }
                    else -> false
                }
            }
            .pointerInput(showResumeDialog) {
                if (showResumeDialog) return@pointerInput
                detectTapGestures(
                    onTap = {
                        notifyInteraction()
                        showOverlayControls = !showOverlayControls
                    },
                    onDoubleTap = { offset ->
                        notifyInteraction()
                        val width = size.width
                        if (width > 0) {
                            if (offset.x < width * 0.35f) {
                                seekRelative(-10_000L)
                            } else if (offset.x > width * 0.65f) {
                                seekRelative(+10_000L)
                            } else {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                    }
                )
            }
    ) {
        // Video Player Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = currentResizeMode
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            update = { playerView ->
                // Reasignar siempre: el player se recrea con remember(info, retryTrigger, audioPassthroughMode)
                // y, sin esto, tras un "Reintentar" el PlayerView seguía enlazado al player liberado.
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
                playerView.resizeMode = currentResizeMode
                playerView.keepScreenOn = isPlaying || showOverlayControls

                // Subtitle appearance (size & contrast background)
                val textScale = when (subtitleSize) {
                    SUBTITLE_SIZE_SMALL -> 0.042f
                    SUBTITLE_SIZE_LARGE -> 0.065f
                    else -> 0.053f
                }
                playerView.subtitleView?.setFractionalTextSize(textScale)

                val subStyle = if (subtitleBackground) {
                    CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.argb(160, 0, 0, 0),
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_NONE,
                        android.graphics.Color.BLACK,
                        null
                    )
                } else {
                    CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        null
                    )
                }
                playerView.subtitleView?.setStyle(subStyle)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Resume Playback Dialog Overlay (Netflix-style)
        if (showResumeDialog && info.startPositionMs >= 10_000L) {
            ResumePlaybackDialog(
                savedPositionMs = info.startPositionMs,
                isMonochrome = isMonochrome,
                focusColor = focusColor,
                focusContent = focusContent,
                onResume = {
                    showResumeDialog = false
                    resumeDecisionMade = true
                    chosenStartPositionMs = info.startPositionMs
                    performSeek(info.startPositionMs)
                    exoPlayer.prepare()
                    exoPlayer.play()
                },
                onStartOver = {
                    showResumeDialog = false
                    resumeDecisionMade = true
                    chosenStartPositionMs = 0L
                    performSeek(0L)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            )
        }

        // Buffering / Loading Indicator Overlay
        if (isBuffering && playerErrorMessage == null && !showResumeDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JellyLoadingIndicator(
                        size = 30.dp,
                        message = null,
                        isMonochrome = isMonochrome
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.player_loading_playback),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Playback Error Overlay
        if (playerErrorMessage != null) {
            val errorIsUnavailable = playerErrorMessage
                ?.contains("No hay ninguna fuente", ignoreCase = true) == true
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xF20B101D))
                        .padding(horizontal = 36.dp, vertical = 28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.player_error_close),
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.player_playback_error),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playerErrorMessage ?: stringResource(R.string.player_cannot_play_file),
                        color = tv.medusa.app.ui.theme.TvPalette.TextMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Sin fuente reproducible, reintentar es un bucle: se ofrece volver.
                        if (!errorIsUnavailable) {
                            val onRetryPlayback = {
                                playerErrorMessage = null
                                viewModel.clearPlaybackError()
                                if (!info.isOnDemand || !viewModel.tryRecoverOnDemandPlayback(resumePositionMs = chosenStartPositionMs)) {
                                    retryTrigger++
                                }
                                Unit
                            }
                            Button(
                                onClick = onRetryPlayback,
                                modifier = Modifier
                                    .handleTouchClick(onClick = onRetryPlayback)
                                    .focusRequester(retryFocusRequester),
                                colors = ButtonDefaults.colors(
                                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                                    contentColor = TextPrimary,
                                    focusedContainerColor = focusColor,
                                    focusedContentColor = focusContent
                                )
                            ) {
                                Text(stringResource(R.string.common_retry), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Button(
                            onClick = onBack,
                            modifier = Modifier
                                .then(if (errorIsUnavailable) Modifier.focusRequester(retryFocusRequester) else Modifier)
                                .handleTouchClick { exitPlayer() },
                            colors = ButtonDefaults.colors(
                                containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                                contentColor = TextPrimary,
                                focusedContainerColor = focusColor,
                                focusedContentColor = focusContent
                            )
                        ) {
                            Text(stringResource(R.string.common_back))
                        }
                    }
                }
            }
        }

        // Cinematic Scrubber HUD Bubble (Apple TV+ / Infuse style)
        AnimatedVisibility(
            visible = isScrubbing || isProgressBarFocused,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showOverlayControls) 130.dp else 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF2080A14))
                    .border(0.75.dp, if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassDivider else focusColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                if (isScrubbing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (scrubDeltaMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = if (isMonochrome) TextPrimary else focusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = formatSeekDelta(scrubDeltaMs),
                            color = if (isMonochrome) TextPrimary else focusColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(14.dp)
                                .background(tv.medusa.app.ui.theme.TvPalette.GlassDivider)
                        )
                        Text(
                            text = formatTime(scrubTargetPosition),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (isMonochrome) TextPrimary else focusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = formatTime(currentPosition),
                            color = if (isMonochrome) TextPrimary else focusColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Apple TV+ / High-End Cinema HUD Controls Overlay
        AnimatedVisibility(
            visible = showOverlayControls && !showResumeDialog,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = activeModalTab == PlayerModalTab.NONE }
            ) {
                // Top Bar: Back Button + Title + Minimal Metadata
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 40.dp, vertical = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .handleTouchClick { exitPlayer() }
                            .size(44.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = tv.medusa.app.ui.theme.TvPalette.GlassDivider,
                            contentColor = TextPrimary,
                            focusedContainerColor = focusColor,
                            focusedContentColor = focusContent
                        ),
                        shape = ButtonDefaults.shape(CircleShape),
                        border = ButtonDefaults.border(
                            border = Border(border = BorderStroke(1.dp, tv.medusa.app.ui.theme.TvPalette.GlassDivider), shape = CircleShape),
                            focusedBorder = Border.None
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = info.title.ifBlank { stringResource(R.string.player_playing) },
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.player_stream_info_placeholder),
                            color = Color(0x99FFFFFF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Bottom Floating Apple TV Style HUD Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.95f)
                                )
                            )
                        )
                        .padding(horizontal = 40.dp, vertical = 28.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Progress Scrubber
                        val displayPos = if (isScrubbing) scrubTargetPosition else currentPosition
                        TvProgressBar(
                            currentPositionMs = displayPos,
                            bufferedPositionMs = bufferedPosition,
                            durationMs = duration,
                            isMonochrome = isMonochrome,
                            focusColor = focusColor,
                            focusRequester = progressBarFocusRequester,
                            introMarker = introMarker,
                            creditsMarker = creditsMarker,
                            recapMarker = recapMarker,
                            previewMarker = previewMarker,
                            onSeekRelative = { deltaSign ->
                                scrubStep(deltaSign)
                            },
                            onSeekTo = { targetMs ->
                                seekToExactPosition(targetMs)
                            },
                            onScrubStart = { targetMs ->
                                updateScrubPosition(targetMs)
                            },
                            onScrubUpdate = { targetMs ->
                                updateScrubPosition(targetMs)
                            },
                            onCommitSeek = {
                                commitScrub()
                            },
                            onFocusChanged = { focused ->
                                isProgressBarFocused = focused
                                if (focused) {
                                    notifyInteraction()
                                }
                            },
                            onDownNavigation = {
                                notifyInteraction()
                                playPauseFocusRequester.requestFocus()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Time & Unified Glass Controls Row (Ergonomic Playback Controls on Left, Options on Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Group: Rewind 10s, Play / Pause (Center Focus), Fast Forward 10s, and Time
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Rewind 10s (Retroceso)
                                TvCircularIconButton(
                                    onClick = {
                                        val cur = if (isScrubbing) scrubTargetPosition else currentPosition
                                        performSeek((cur - 10_000L).coerceAtLeast(0L))
                                    },
                                    icon = Icons.Default.FastRewind,
                                    contentDescription = stringResource(R.string.player_rewind_10),
                                    size = 42.dp,
                                    iconSize = 20.dp,
                                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                                    focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
                                    contentColor = TextPrimary,
                                    focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusRequester = rewindFocusRequester,
                                    modifier = Modifier.focusProperties {
                                        up = progressBarFocusRequester
                                        right = playPauseFocusRequester
                                    }
                                )

                                // 2. Play / Pause (Primary Action - Centered & Default Focus)
                                TvCircularIconButton(
                                    onClick = {
                                        notifyInteraction()
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            exoPlayer.play()
                                        }
                                    },
                                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.common_play),
                                    size = 48.dp,
                                    iconSize = 26.dp,
                                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                                    focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
                                    contentColor = TextPrimary,
                                    focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusRequester = playPauseFocusRequester,
                                    modifier = Modifier.focusProperties {
                                        up = progressBarFocusRequester
                                        left = rewindFocusRequester
                                        right = fastForwardFocusRequester
                                    }
                                )

                                // 3. Fast Forward 10s (Avance)
                                TvCircularIconButton(
                                    onClick = {
                                        val cur = if (isScrubbing) scrubTargetPosition else currentPosition
                                        performSeek(cur + 10_000L)
                                    },
                                    icon = Icons.Default.FastForward,
                                    contentDescription = stringResource(R.string.player_forward_10),
                                    size = 42.dp,
                                    iconSize = 20.dp,
                                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                                    focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
                                    contentColor = TextPrimary,
                                    focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusRequester = fastForwardFocusRequester,
                                    modifier = Modifier.focusProperties {
                                        up = progressBarFocusRequester
                                        left = playPauseFocusRequester
                                        right = audioButtonFocusRequester
                                    }
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // Time Indicator
                                Text(
                                    text = "${formatTime(displayPos)} / ${formatTime(duration)}",
                                    color = if ((isScrubbing || isProgressBarFocused) && !isMonochrome) focusColor else tv.medusa.app.ui.theme.TvPalette.TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Right Group: Audio, Subtitles, Settings (Respects buttonStyle)
                            val currentAudioTrack = audioTracks.firstOrNull { it.isSelected }
                            val currentSubTrack = subtitleTracks.firstOrNull { it.isSelected }
                            val audioFlag = currentAudioTrack?.flagEmoji
                            val subFlag = currentSubTrack?.flagEmoji
                            val hasVersions = info.availableVersions.size > 1

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 4. Audio Selector Button
                                TvPillButton(
                                    onClick = {
                                        notifyInteraction()
                                        activeModalTab = PlayerModalTab.AUDIO
                                    },
                                    icon = Icons.Default.Audiotrack,
                                    flagEmoji = audioFlag,
                                    label = stringResource(R.string.player_audio),
                                    isSelected = activeModalTab == PlayerModalTab.AUDIO,
                                    buttonStyle = buttonStyle,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    modifier = Modifier
                                        .focusRequester(audioButtonFocusRequester)
                                        .focusProperties {
                                            up = progressBarFocusRequester
                                            left = fastForwardFocusRequester
                                            right = subtitleButtonFocusRequester
                                        }
                                )

                                // 5. Subtitles Selector Button
                                TvPillButton(
                                    onClick = {
                                        notifyInteraction()
                                        activeModalTab = PlayerModalTab.SUBTITLES
                                    },
                                    icon = if (currentSubTrack != null) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                    flagEmoji = subFlag,
                                    label = stringResource(R.string.player_subtitles),
                                    isSelected = activeModalTab == PlayerModalTab.SUBTITLES,
                                    buttonStyle = buttonStyle,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    modifier = Modifier
                                        .focusRequester(subtitleButtonFocusRequester)
                                        .focusProperties {
                                            up = progressBarFocusRequester
                                            left = audioButtonFocusRequester
                                            right = if (hasVersions) versionButtonFocusRequester else settingsButtonFocusRequester
                                        }
                                )

                                if (hasVersions) {
                                    // 6. Version Selector Button (solo si Jellyfin ofrece varias versiones)
                                    TvPillButton(
                                        onClick = {
                                            notifyInteraction()
                                            activeModalTab = PlayerModalTab.VERSIONS
                                        },
                                        icon = Icons.Default.Layers,
                                        label = stringResource(R.string.player_versions),
                                        isSelected = activeModalTab == PlayerModalTab.VERSIONS,
                                        buttonStyle = buttonStyle,
                                        isMonochrome = isMonochrome,
                                        focusColor = focusColor,
                                        focusContent = focusContent,
                                        modifier = Modifier
                                            .focusRequester(versionButtonFocusRequester)
                                            .focusProperties {
                                                up = progressBarFocusRequester
                                                left = subtitleButtonFocusRequester
                                                right = settingsButtonFocusRequester
                                            }
                                    )
                                }

                                // 7. Settings Selector Button
                                TvPillButton(
                                    onClick = {
                                        notifyInteraction()
                                        activeModalTab = PlayerModalTab.SETTINGS
                                    },
                                    icon = Icons.Default.Tune,
                                    label = stringResource(R.string.nav_settings),
                                    isSelected = activeModalTab == PlayerModalTab.SETTINGS,
                                    buttonStyle = buttonStyle,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    modifier = Modifier
                                        .focusRequester(settingsButtonFocusRequester)
                                        .focusProperties {
                                            up = progressBarFocusRequester
                                            left = if (hasVersions) versionButtonFocusRequester else subtitleButtonFocusRequester
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom TV Modal Sheet Overlay for Track / Settings Selection
        if (activeModalTab != PlayerModalTab.NONE) {
            PlayerCustomModalSheet(
                activeTab = activeModalTab,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                versions = info.availableVersions,
                currentVersionName = info.currentVersionName,
                currentMediaSourceId = info.mediaSourceId,
                currentSpeed = currentPlaybackSpeed,
                currentResizeMode = currentResizeMode,
                nightModeAudio = nightModeAudio,
                audioVolumeBoostDb = audioVolumeBoostDb,
                audioPassthroughMode = audioPassthroughMode,
                subtitleSize = subtitleSize,
                subtitleBackground = subtitleBackground,
                autoFrameRateEnabled = autoFrameRateEnabled,
                buttonStyle = buttonStyle,
                onSelectAudioTrack = { track ->
                    notifyInteraction()
                    val jfIndex = track.jellyfinStreamIndex
                    val hasLocalTrack = track.trackIndex >= 0
                    if (hasLocalTrack) {
                        selectAudioTrackInExoPlayer(exoPlayer, track)
                        if (jfIndex != null) {
                            viewModel.updateActiveAudioStreamIndex(jfIndex)
                        }
                    } else if (jfIndex != null) {
                        viewModel.selectAudioStream(
                            streamIndex = jfIndex,
                            resumePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                        )
                    } else {
                        selectAudioTrackInExoPlayer(exoPlayer, track)
                    }
                },
                onSelectSubtitleTrack = { track ->
                    notifyInteraction()
                    selectSubtitleTrackInExoPlayer(exoPlayer, track)
                    viewModel.updateActiveSubtitleStreamIndex(track?.jellyfinStreamIndex)
                },
                onSelectVersion = { mediaSourceId ->
                    notifyInteraction()
                    exoPlayer.pause()
                    viewModel.selectVersion(mediaSourceId)
                },
                onSelectSpeed = { speed ->
                    notifyInteraction()
                    currentPlaybackSpeed = speed
                    exoPlayer.setPlaybackSpeed(speed)
                },
                onSelectResizeMode = { mode ->
                    notifyInteraction()
                    currentResizeMode = mode
                },
                onSelectNightMode = { level ->
                    notifyInteraction()
                    viewModel.setNightModeAudio(level)
                },
                onSelectVolumeBoost = { db ->
                    notifyInteraction()
                    viewModel.setAudioVolumeBoostDb(db)
                },
                onSelectAudioPassthroughMode = { mode ->
                    notifyInteraction()
                    chosenStartPositionMs = currentPosition.coerceAtLeast(0L)
                    viewModel.setAudioPassthroughMode(mode)
                },
                onSelectSubtitleSize = { size ->
                    notifyInteraction()
                    viewModel.setSubtitleSize(size)
                },
                onToggleSubtitleBackground = { enabled ->
                    notifyInteraction()
                    viewModel.setSubtitleBackground(enabled)
                },
                onToggleAutoFrameRate = { enabled ->
                    notifyInteraction()
                    viewModel.setAutoFrameRateEnabled(enabled)
                },
                onClose = {
                    notifyInteraction()
                    activeModalTab = PlayerModalTab.NONE
                },
                isMonochrome = isMonochrome,
                focusColor = focusColor,
                focusContent = focusContent
            )
        }

        // ─── Skip Banner (bottom-right, Netflix-style) ───────────────────────
        // Un solo banner activo por prioridad: los segmentos de TheIntroDB pueden solaparse
        // y dos Surface enfocables en la misma esquina romperían la navegación con el mando.
        val activeSkipMarker: SkipMarker?
        val activeSkipLabel: String
        when {
            showSkipIntroBanner && introMarker != null -> {
                activeSkipMarker = introMarker
                activeSkipLabel = stringResource(R.string.player_skip_intro)
            }
            showSkipRecapBanner && recapMarker != null -> {
                activeSkipMarker = recapMarker
                activeSkipLabel = stringResource(R.string.player_skip_recap)
            }
            showSkipPreviewBanner && previewMarker != null -> {
                activeSkipMarker = previewMarker
                activeSkipLabel = stringResource(R.string.player_skip_preview)
            }
            showSkipCreditsBanner && creditsMarker != null -> {
                activeSkipMarker = creditsMarker
                activeSkipLabel = stringResource(R.string.player_skip_credits)
            }
            else -> {
                activeSkipMarker = null
                activeSkipLabel = ""
            }
        }
        AnimatedVisibility(
            visible = activeSkipMarker != null && !showResumeDialog && activeModalTab == PlayerModalTab.NONE,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 120.dp)
        ) {
            SkipIntroBanner(
                label = activeSkipLabel,
                isMonochrome = isMonochrome,
                focusColor = focusColor,
                focusContent = focusContent,
                onSkip = {
                    // skipToMs viene del servidor (EndPositionTicks / IntroEnd), nunca se inventa.
                    val skipTo = activeSkipMarker?.skipToMs ?: return@SkipIntroBanner
                    introSkippedOrDismissed = true
                    creditsSkippedOrDismissed = true
                    recapSkippedOrDismissed = true
                    previewSkippedOrDismissed = true
                    showSkipIntroBanner = false
                    showSkipCreditsBanner = false
                    showSkipRecapBanner = false
                    showSkipPreviewBanner = false
                    performSeek(skipTo)
                    notifyInteraction()
                }
            )
        }

        // ─── Auto Exit Overlay (Bottom-Center) ──────────────────────────────
        AnimatedVisibility(
            visible = autoExitCountdown > 0 && playbackEnded && !showNextEpisodeOverlay,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.95f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xEE111422))
                    .border(1.5.dp, if (isMonochrome) Color(0x66FFFFFF) else focusColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.player_auto_exit_in, autoExitCountdown),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ─── Next Episode / Movie Overlay (Disney+ / Netflix PIP & Recommendations) ─
        AnimatedVisibility(
            visible = showNextEpisodeOverlay && !showResumeDialog && nextEpisode != null,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth()
        ) {
            nextEpisode?.let { ep ->
                PostCreditsBoard(
                    nextEpisode = ep,
                    recommendedList = recommendedList,
                    countdown = nextEpisodeCountdown,
                    isMonochrome = isMonochrome,
                    focusColor = focusColor,
                    focusContent = focusContent,
                    onPlayNow = { targetItem ->
                        playNext(targetItem)
                    },
                    onDismiss = {
                        creditsSkippedOrDismissed = true
                        showNextEpisodeOverlay = false
                        if (playbackEnded) {
                            latestOnBack()
                        } else {
                            nextEpisodeCountdown = -1 // Prevent auto-play during credits
                        }
                    }
                )
            }
        }
    }
}

@ExperimentalTvMaterial3Api
@Composable
private fun SkipIntroBanner(
    label: String = "Omitir intro",
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusContent: Color = tv.medusa.app.ui.theme.TvPalette.FocusInk,
    onSkip: () -> Unit
) {
    val skipFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120L)
        runCatching { skipFocusRequester.requestFocus() }
    }

    Surface(
        onClick = onSkip,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xEE1A1C2E),
            focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
            contentColor = TextPrimary,
            focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.5.dp, if (isMonochrome) Color(0x66FFFFFF) else focusColor.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, if (isMonochrome) TextPrimary else focusColor),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        modifier = Modifier.focusRequester(skipFocusRequester)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@ExperimentalTvMaterial3Api
@Composable
private fun PostCreditsBoard(
    nextEpisode: NextEpisodeInfo,
    recommendedList: List<NextEpisodeInfo>,
    countdown: Int,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusContent: Color = tv.medusa.app.ui.theme.TvPalette.FocusInk,
    onPlayNow: (NextEpisodeInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120L)
        runCatching { playFocusRequester.requestFocus() }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 32.dp, bottom = 32.dp)
    ) {
        // Hero Main Recommendation Card (Top-Right)
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF00D0F1D))
                .border(1.5.dp, if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassDivider else focusColor.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val headerTitle = when {
                        countdown > 0 -> stringResource(R.string.player_next_in, countdown)
                        nextEpisode.isMovie -> stringResource(R.string.player_next_movie)
                        else -> stringResource(R.string.player_next_episode)
                    }

                    Text(
                        text = headerTitle,
                        color = if (isMonochrome) TextPrimary else focusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    if (countdown > 0) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassRaised else focusColor.copy(alpha = 0.2f))
                                .border(1.5.dp, if (isMonochrome) TextPrimary else focusColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$countdown",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Main Info Row: Backdrop/Poster + Title/Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val imageUrl = nextEpisode.backdropUrl ?: nextEpisode.thumbUrl
                    if (!imageUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 66.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(tv.medusa.app.ui.theme.TvPalette.GlassDivider)
                        ) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = nextEpisode.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nextEpisode.episodeCode,
                            color = if (isMonochrome) TextPrimary.copy(alpha = 0.7f) else focusColor.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = nextEpisode.title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!nextEpisode.overview.isNullOrBlank()) {
                    Text(
                        text = nextEpisode.overview,
                        color = TextPrimary.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Play now
                    Surface(
                        onClick = { onPlayNow(nextEpisode) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                            focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
                            contentColor = TextPrimary,
                            focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent
                        ),
                        modifier = Modifier
                            .focusRequester(playFocusRequester)
                            .weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.player_play_now),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Dismiss / Watch credits
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                            focusedContainerColor = Color(0x44FFFFFF),
                            contentColor = TextPrimary,
                            focusedContentColor = TextPrimary
                        )
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                text = stringResource(R.string.player_watch_credits),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Alternative Recommendations Carousel ("También te podría gustar")
        val alternatives = recommendedList.filter { it.itemId != nextEpisode.itemId }.take(4)
        if (alternatives.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.player_more_like_this),
                    color = TextPrimary.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    alternatives.forEach { altItem ->
                        Surface(
                            onClick = { onPlayNow(altItem) },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xF0111422),
                                focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
                                contentColor = TextPrimary,
                                focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(
                                    border = BorderStroke(1.dp, tv.medusa.app.ui.theme.TvPalette.GlassDivider),
                                    shape = RoundedCornerShape(14.dp)
                                ),
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, if (isMonochrome) TextPrimary else focusColor),
                                    shape = RoundedCornerShape(14.dp)
                                )
                            ),
                            modifier = Modifier.width(180.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val altImage = altItem.thumbUrl ?: altItem.backdropUrl
                                if (!altImage.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(tv.medusa.app.ui.theme.TvPalette.GlassDivider)
                                    ) {
                                        AsyncImage(
                                            model = altImage,
                                            contentDescription = altItem.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = altItem.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = altItem.episodeCode,
                                        fontSize = 10.sp,
                                        color = TextPrimary.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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


@ExperimentalTvMaterial3Api
@Composable
private fun TvCircularIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    containerColor: Color = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
    focusedContainerColor: Color = TextPrimary,
    contentColor: Color = TextPrimary,
    focusedContentColor: Color = tv.medusa.app.ui.theme.TvPalette.FocusInk,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = focusedContainerColor,
            contentColor = contentColor,
            focusedContentColor = focusedContentColor
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, tv.medusa.app.ui.theme.TvPalette.GlassIdle), shape = CircleShape),
            focusedBorder = Border(border = BorderStroke(1.5.dp, if (isMonochrome) TextPrimary else focusColor), shape = CircleShape)
        ),
        modifier = modifier
            .handleTouchClick { onClick() }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .size(size)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) focusedContentColor else contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@ExperimentalTvMaterial3Api
@Composable
private fun TvPillButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    flagEmoji: String? = null,
    buttonStyle: String = BUTTON_STYLE_ICONS_AND_TEXT,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusContent: Color = tv.medusa.app.ui.theme.TvPalette.FocusInk,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY
    val showText = buttonStyle != BUTTON_STYLE_ICONS_ONLY

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) (if (isMonochrome) Color(0x35FFFFFF) else focusColor.copy(alpha = 0.22f)) else tv.medusa.app.ui.theme.TvPalette.GlassIdle,
            focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
            contentColor = TextPrimary,
            focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, if (isSelected) (if (isMonochrome) TextPrimary else focusColor) else Color(0x20FFFFFF)),
                shape = RoundedCornerShape(10.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, if (isMonochrome) TextPrimary else focusColor),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        modifier = modifier
            .handleTouchClick { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = if (showText) 14.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val currentContentColor = if (isFocused) (if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent) else TextPrimary
            if (showIcons) {
                if (!flagEmoji.isNullOrBlank()) {
                    Text(
                        text = flagEmoji,
                        fontSize = 16.sp,
                        modifier = Modifier.monochromeFilter(isMonochrome)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = currentContentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (showText) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = currentContentColor
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@ExperimentalTvMaterial3Api
@Composable
private fun PlayerCustomModalSheet(
    activeTab: PlayerModalTab,
    audioTracks: List<TrackItem>,
    subtitleTracks: List<TrackItem>,
    versions: List<JellyfinMediaSourceDto> = emptyList(),
    currentVersionName: String = "",
    currentMediaSourceId: String = "",
    currentSpeed: Float,
    currentResizeMode: Int,
    nightModeAudio: Int = 0,
    audioVolumeBoostDb: Int = 0,
    audioPassthroughMode: Int = AUDIO_PASSTHROUGH_OFF,
    subtitleSize: String = SUBTITLE_SIZE_NORMAL,
    subtitleBackground: Boolean = false,
    autoFrameRateEnabled: Boolean = false,
    buttonStyle: String = BUTTON_STYLE_ICONS_AND_TEXT,
    onSelectAudioTrack: (TrackItem) -> Unit,
    onSelectSubtitleTrack: (TrackItem?) -> Unit,
    onSelectVersion: (String) -> Unit = {},
    onSelectSpeed: (Float) -> Unit,
    onSelectResizeMode: (Int) -> Unit,
    onSelectNightMode: (Int) -> Unit = {},
    onSelectVolumeBoost: (Int) -> Unit = {},
    onSelectAudioPassthroughMode: (Int) -> Unit = {},
    onSelectSubtitleSize: (String) -> Unit = {},
    onToggleSubtitleBackground: (Boolean) -> Unit = {},
    onToggleAutoFrameRate: (Boolean) -> Unit = {},
    onClose: () -> Unit,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusContent: Color = tv.medusa.app.ui.theme.TvPalette.FocusInk
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = true) {
        onClose()
    }

    LaunchedEffect(activeTab) {
        delay(120L)
        runCatching { firstItemFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable { onClose() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Back || keyEvent.key == Key.Escape)) {
                    onClose()
                    true
                } else false
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(440.dp)
                .clickable(enabled = false) {}
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Back || keyEvent.key == Key.Escape)) {
                        onClose()
                        true
                    } else false
                }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                        onClose()
                        true
                    } else false
                }
                .border(
                    1.dp,
                    if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassRaised else focusColor.copy(alpha = 0.25f),
                    RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                ),
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
            colors = NonInteractiveSurfaceDefaults.colors(
                containerColor = Color(0xF6090D18)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Modal Title Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val currentAudioTrack = audioTracks.firstOrNull { it.isSelected }
                        val currentSubTrack = subtitleTracks.firstOrNull { it.isSelected }
                        val titleFlag = when (activeTab) {
                            PlayerModalTab.AUDIO -> currentAudioTrack?.flagEmoji
                            PlayerModalTab.SUBTITLES -> currentSubTrack?.flagEmoji
                            else -> null
                        }
                        val titleIcon = when (activeTab) {
                            PlayerModalTab.AUDIO -> Icons.Default.Audiotrack
                            PlayerModalTab.SUBTITLES -> if (currentSubTrack != null) Icons.Default.Subtitles else Icons.Default.SubtitlesOff
                            PlayerModalTab.VERSIONS -> Icons.Default.Layers
                            PlayerModalTab.SETTINGS -> Icons.Default.Tune
                            PlayerModalTab.NONE -> Icons.Default.Settings
                        }
                        val titleText = when (activeTab) {
                            PlayerModalTab.AUDIO -> stringResource(R.string.player_tab_audio)
                            PlayerModalTab.SUBTITLES -> stringResource(R.string.player_tab_subtitles)
                            PlayerModalTab.VERSIONS -> stringResource(R.string.player_tab_versions)
                            PlayerModalTab.SETTINGS -> stringResource(R.string.player_tab_settings)
                            PlayerModalTab.NONE -> ""
                        }

                        if (!titleFlag.isNullOrBlank() && buttonStyle != BUTTON_STYLE_TEXT_ONLY) {
                            Text(
                                text = titleFlag,
                                fontSize = 22.sp,
                                modifier = Modifier.monochromeFilter(isMonochrome)
                            )
                        } else {
                            Icon(
                                imageVector = titleIcon,
                                contentDescription = null,
                                tint = if (isMonochrome) TextPrimary else focusColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = titleText,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .handleTouchClick { onClose() }
                            .size(34.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = tv.medusa.app.ui.theme.TvPalette.GlassIdle,
                            contentColor = TextPrimary,
                            focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
                            focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Modal Content Body
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (activeTab) {
                        PlayerModalTab.AUDIO -> {
                            // 1. Pistas de audio
                            item {
                                Text(
                                    text = stringResource(R.string.player_audio_tracks),
                                    color = tv.medusa.app.ui.theme.TvPalette.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            if (audioTracks.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.player_no_audio_tracks),
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    items = audioTracks,
                                    key = { index, track -> "audio_${track.label}_$index" },
                                    contentType = { _, _ -> "track_item" }
                                ) { index, track ->
                                    TrackOptionItem(
                                        label = track.label,
                                        isSelected = track.isSelected,
                                        flagEmoji = track.flagEmoji,
                                        fallbackIcon = Icons.Default.Audiotrack,
                                        buttonStyle = buttonStyle,
                                        isMonochrome = isMonochrome,
                                        focusColor = focusColor,
                                        focusContent = focusContent,
                                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                                        onClick = {
                                            onSelectAudioTrack(track)
                                            onClose()
                                        }
                                    )
                                }
                            }

                            // 2. Modo Nocturno / DRC (Clean Toggle Card)
                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                val isNightModeActive = nightModeAudio > 0
                                TvToggleOptionRow(
                                    title = stringResource(R.string.player_night_mode),
                                    subtitle = null,
                                    icon = Icons.Default.Bedtime,
                                    buttonStyle = buttonStyle,
                                    isChecked = isNightModeActive,
                                    onToggle = { enable ->
                                        onSelectNightMode(if (enable) 1 else 0)
                                    },
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    firstFocusRequester = if (audioTracks.isEmpty()) firstItemFocusRequester else null
                                )
                            }

                            // 3. Amplificador de Volumen / Diálogos con deslizante real interactivo (0 a 20 dB)
                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                TvAudioVolumeSliderRow(
                                    title = stringResource(R.string.player_volume_boost),
                                    currentDb = audioVolumeBoostDb,
                                    maxDb = 20,
                                    buttonStyle = buttonStyle,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    onValueChange = { db ->
                                        onSelectVolumeBoost(db)
                                    }
                                )
                            }

                            // 4. Salida de audio / Decodificación Local PCM vs Passthrough HDMI
                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                TvSegmentedControlRow(
                                    title = stringResource(R.string.player_audio_passthrough),
                                    subtitle = stringResource(R.string.player_audio_passthrough_desc),
                                    icon = Icons.Default.Audiotrack,
                                    buttonStyle = buttonStyle,
                                    options = listOf(
                                        AUDIO_PASSTHROUGH_OFF,
                                        AUDIO_PASSTHROUGH_AUTO,
                                        AUDIO_PASSTHROUGH_DIRECT
                                    ),
                                    selectedOption = audioPassthroughMode,
                                    optionLabel = { mode ->
                                        when (mode) {
                                            AUDIO_PASSTHROUGH_OFF -> stringResource(R.string.player_audio_passthrough_off)
                                            AUDIO_PASSTHROUGH_AUTO -> stringResource(R.string.player_audio_passthrough_auto)
                                            else -> stringResource(R.string.player_audio_passthrough_direct)
                                        }
                                    },
                                    onOptionSelected = { mode ->
                                        onSelectAudioPassthroughMode(mode)
                                    },
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent
                                )
                            }
                        }

                        PlayerModalTab.SUBTITLES -> {
                            item {
                                val isSubDisabled = subtitleTracks.none { it.isSelected }
                                TrackOptionItem(
                                    label = stringResource(R.string.player_disabled),
                                    isSelected = isSubDisabled,
                                    icon = Icons.Default.SubtitlesOff,
                                    buttonStyle = buttonStyle,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    focusRequester = firstItemFocusRequester,
                                    onClick = {
                                        onSelectSubtitleTrack(null)
                                        onClose()
                                    }
                                )
                            }

                            itemsIndexed(
                                items = subtitleTracks,
                                key = { index, track -> "sub_${track.label}_$index" },
                                contentType = { _, _ -> "track_item" }
                            ) { _, track ->
                                TrackOptionItem(
                                    label = track.label,
                                    isSelected = track.isSelected,
                                    flagEmoji = track.flagEmoji,
                                    fallbackIcon = Icons.Default.Subtitles,
                                    buttonStyle = buttonStyle,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    onClick = {
                                        onSelectSubtitleTrack(track)
                                        onClose()
                                    }
                                )
                            }

                            // Tamaño de subtítulos (Segmented Option Card)
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                TvSegmentedControlRow(
                                    title = stringResource(R.string.player_subtitle_size),
                                    icon = Icons.Default.Subtitles,
                                    buttonStyle = buttonStyle,
                                    options = listOf(SUBTITLE_SIZE_SMALL, SUBTITLE_SIZE_NORMAL, SUBTITLE_SIZE_LARGE),
                                    selectedOption = subtitleSize,
                                    optionLabel = { sizeVal ->
                                        when (sizeVal) {
                                            SUBTITLE_SIZE_SMALL -> stringResource(R.string.player_subtitle_size_small)
                                            SUBTITLE_SIZE_LARGE -> stringResource(R.string.player_subtitle_size_large)
                                            else -> stringResource(R.string.player_subtitle_size_normal)
                                        }
                                    },
                                    onOptionSelected = { onSelectSubtitleSize(it) },
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent
                                )
                            }

                            // Fondo de contraste (Segmented Option Card)
                            item {
                                Spacer(modifier = Modifier.height(2.dp))
                                TvSegmentedControlRow(
                                    title = stringResource(R.string.player_subtitle_contrast),
                                    buttonStyle = buttonStyle,
                                    options = listOf(false, true),
                                    selectedOption = subtitleBackground,
                                    optionLabel = { bg ->
                                        if (bg) stringResource(R.string.player_subtitle_contrast_on) else stringResource(R.string.player_subtitle_contrast_off)
                                    },
                                    onOptionSelected = { onToggleSubtitleBackground(it) },
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent
                                )
                            }
                        }

                        PlayerModalTab.VERSIONS -> {
                            item {
                                Text(
                                    text = stringResource(R.string.player_version_choose),
                                    color = tv.medusa.app.ui.theme.TvPalette.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            if (versions.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.player_no_versions),
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    items = versions,
                                    key = { index, version -> "version_${version.id}_$index" },
                                    contentType = { _, _ -> "version_item" }
                                ) { index, version ->
                                    val label = version.name?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.player_version_default, index + 1)
                                    val container = version.container?.uppercase()?.takeIf { it.isNotBlank() }
                                    val displayLabel = if (container != null) "$label   $container" else label
                                    TrackOptionItem(
                                        label = displayLabel,
                                        isSelected = version.id == currentMediaSourceId ||
                                            (currentMediaSourceId.isBlank() && version.name == currentVersionName),
                                        icon = Icons.Default.Layers,
                                        buttonStyle = buttonStyle,
                                        isMonochrome = isMonochrome,
                                        focusColor = focusColor,
                                        focusContent = focusContent,
                                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                                        onClick = {
                                            onSelectVersion(version.id)
                                            onClose()
                                        }
                                    )
                                }
                            }
                        }

                        PlayerModalTab.SETTINGS -> {
                            // Velocidad de reproducción (Segmented Option Card)
                            item {
                                TvSegmentedControlRow(
                                    title = stringResource(R.string.player_playback_speed),
                                    icon = Icons.Default.Tune,
                                    buttonStyle = buttonStyle,
                                    options = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
                                    selectedOption = currentSpeed,
                                    optionLabel = { spd ->
                                        if (spd == 1.0f) "1.0×" else "${spd}×"
                                    },
                                    onOptionSelected = { onSelectSpeed(it) },
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    firstFocusRequester = firstItemFocusRequester
                                )
                            }

                            // Modo de pantalla / Aspect Ratio (Segmented Option Card)
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                TvSegmentedControlRow(
                                    title = stringResource(R.string.player_aspect_ratio),
                                    buttonStyle = buttonStyle,
                                    options = listOf(
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    ),
                                    selectedOption = currentResizeMode,
                                    optionLabel = { mode ->
                                        when (mode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> stringResource(R.string.player_aspect_fit)
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> stringResource(R.string.player_aspect_zoom)
                                            else -> stringResource(R.string.player_aspect_fill)
                                        }
                                    },
                                    onOptionSelected = { onSelectResizeMode(it) },
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent
                                )
                            }

                            // Sincronización de fotogramas AFR (Segmented Option Card)
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                TvSegmentedControlRow(
                                    title = stringResource(R.string.player_afr_title),
                                    subtitle = null,
                                    buttonStyle = buttonStyle,
                                    options = listOf(false, true),
                                    selectedOption = autoFrameRateEnabled,
                                    optionLabel = { afr ->
                                        if (afr) stringResource(R.string.player_afr_enabled) else stringResource(R.string.player_afr_disabled)
                                    },
                                    onOptionSelected = { onToggleAutoFrameRate(it) },
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent
                                )
                            }
                        }

                        PlayerModalTab.NONE -> {}
                    }
                }
            }
        }
    }
}

@ExperimentalTvMaterial3Api
@Composable
private fun TrackOptionItem(
    label: String,
    isSelected: Boolean,
    icon: ImageVector? = null,
    flagEmoji: String? = null,
    fallbackIcon: ImageVector? = null,
    buttonStyle: String = BUTTON_STYLE_ICONS_AND_TEXT,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusContent: Color = tv.medusa.app.ui.theme.TvPalette.FocusInk,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) (if (isMonochrome) Color(0x35FFFFFF) else focusColor.copy(alpha = 0.22f)) else Color(0x12FFFFFF),
            focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
            focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    1.dp,
                    if (isSelected) (if (isMonochrome) Color(0x66FFFFFF) else focusColor.copy(alpha = 0.5f)) else tv.medusa.app.ui.theme.TvPalette.GlassSubtle
                ),
                shape = RoundedCornerShape(10.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, if (isMonochrome) TextPrimary else focusColor),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        modifier = Modifier
            .handleTouchClick { onClick() }
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (showIcons) {
                    if (!flagEmoji.isNullOrBlank()) {
                        Text(
                            text = flagEmoji,
                            fontSize = 18.sp,
                            modifier = Modifier.monochromeFilter(isMonochrome)
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) (if (isMonochrome) TextPrimary else focusColor) else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                        )
                    } else if (fallbackIcon != null) {
                        Icon(
                            imageVector = fallbackIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) (if (isMonochrome) TextPrimary else focusColor) else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                        )
                    }
                }
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isMonochrome) TextPrimary else focusColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@ExperimentalTvMaterial3Api
@Composable
private fun TvToggleOptionRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    buttonStyle: String = BUTTON_STYLE_ICONS_AND_TEXT,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusContent: Color = tv.medusa.app.ui.theme.TvPalette.FocusInk,
    firstFocusRequester: FocusRequester? = null
) {
    val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { onToggle(!isChecked) },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isChecked) (if (isMonochrome) Color(0x2EFFFFFF) else focusColor.copy(alpha = 0.20f)) else tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
            focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
            pressedContainerColor = if (isMonochrome) Color(0x55FFFFFF) else focusColor.copy(alpha = 0.45f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    1.dp,
                    if (isChecked) (if (isMonochrome) Color(0x44FFFFFF) else focusColor.copy(alpha = 0.35f)) else tv.medusa.app.ui.theme.TvPalette.GlassIdle
                ),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(
                    2.dp,
                    if (isMonochrome) TextPrimary else focusColor
                ),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        modifier = Modifier
            .handleTouchClick { onToggle(!isChecked) }
            .fillMaxWidth()
            .then(if (firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (showIcons && icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isFocused) {
                            if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent
                        } else if (isChecked) {
                            if (isMonochrome) TextPrimary else focusColor
                        } else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                    )
                }
                Column {
                    Text(
                        text = title,
                        color = if (isFocused) (if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent) else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = if (isFocused) (if (isMonochrome) Color(0xCC0F172A) else focusContent.copy(alpha = 0.7f)) else Color(0x88CBD5E1),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Status Badge
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isChecked) {
                    if (isFocused) (if (isMonochrome) Color(0x330F172A) else focusContent.copy(alpha = 0.15f))
                    else (if (isMonochrome) TextPrimary.copy(alpha = 0.25f) else focusColor.copy(alpha = 0.30f))
                } else Color(0x1AFFFFFF),
                border = BorderStroke(
                    1.dp,
                    if (isChecked) {
                        if (isFocused) (if (isMonochrome) Color(0x660F172A) else focusContent.copy(alpha = 0.3f))
                        else (if (isMonochrome) TextPrimary.copy(alpha = 0.6f) else focusColor.copy(alpha = 0.7f))
                    } else tv.medusa.app.ui.theme.TvPalette.GlassRaised
                )
            ) {
                Text(
                    text = if (isChecked) stringResource(R.string.settings_seasonal_audio_enabled) else stringResource(R.string.player_night_mode_off),
                    color = if (isFocused) (if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent)
                            else if (isChecked && !isMonochrome) focusColor else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@ExperimentalTvMaterial3Api
@Composable
private fun TvAudioVolumeSliderRow(
    title: String,
    currentDb: Int,
    maxDb: Int = 20,
    buttonStyle: String = BUTTON_STYLE_ICONS_AND_TEXT,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusRequester: FocusRequester? = null,
    onValueChange: (Int) -> Unit
) {
    val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY
    var isFocused by remember { mutableStateOf(false) }
    var localDb by remember(currentDb) { mutableIntStateOf(currentDb) }

    val fraction = (localDb.toFloat() / maxDb.toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 60, easing = LinearOutSlowInEasing),
        label = "volume_slider_fraction"
    )

    val isBoosted = localDb > 0

    val iconVector = when {
        localDb == 0 -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }

    Surface(
        onClick = {
            val nextDb = if (localDb == 0) 6 else 0
            localDb = nextDb
            onValueChange(nextDb)
        },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isBoosted) {
                if (isMonochrome) Color(0x2EFFFFFF) else focusColor.copy(alpha = 0.20f)
            } else tv.medusa.app.ui.theme.TvPalette.GlassSubtle,
            focusedContainerColor = if (isMonochrome) Color(0x40FFFFFF) else focusColor.copy(alpha = 0.35f),
            pressedContainerColor = if (isMonochrome) Color(0x55FFFFFF) else focusColor.copy(alpha = 0.45f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    1.dp,
                    if (isBoosted) {
                        if (isMonochrome) Color(0x44FFFFFF) else focusColor.copy(alpha = 0.35f)
                    } else tv.medusa.app.ui.theme.TvPalette.GlassIdle
                ),
                shape = RoundedCornerShape(10.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(
                    2.dp,
                    if (isMonochrome) TextPrimary else focusColor
                ),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            val newDb = (localDb - 1).coerceAtLeast(0)
                            if (newDb != localDb) {
                                localDb = newDb
                                onValueChange(newDb)
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            val newDb = (localDb + 1).coerceAtMost(maxDb)
                            if (newDb != localDb) {
                                localDb = newDb
                                onValueChange(newDb)
                            }
                            true
                        }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> true
                        else -> false
                    }
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Icon + Title + Decibels Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (showIcons) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isBoosted) {
                                if (isMonochrome) TextPrimary else focusColor
                            } else tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                        )
                    }
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Badge de Decibelios
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isBoosted) {
                        if (isMonochrome) TextPrimary.copy(alpha = 0.25f) else focusColor.copy(alpha = 0.30f)
                    } else Color(0x1AFFFFFF),
                    border = BorderStroke(
                        1.dp,
                        if (isBoosted) {
                            if (isMonochrome) TextPrimary.copy(alpha = 0.6f) else focusColor.copy(alpha = 0.7f)
                        } else tv.medusa.app.ui.theme.TvPalette.GlassRaised
                    )
                ) {
                    Text(
                        text = if (localDb == 0) "0 dB" else "+$localDb dB",
                        color = if (isBoosted && !isMonochrome) focusColor else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Slider with interactive thumb
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isFocused && localDb > 0) (if (isMonochrome) Color(0x40FFFFFF) else focusColor.copy(alpha = 0.3f)) else Color(0x15FFFFFF))
                        .clickable {
                            val newDb = (localDb - 1).coerceAtLeast(0)
                            if (newDb != localDb) {
                                localDb = newDb
                                onValueChange(newDb)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = null,
                        tint = if (localDb > 0) {
                            if (isFocused) (if (isMonochrome) TextPrimary else focusColor) else TextPrimary
                        } else tv.medusa.app.ui.theme.TvPalette.GlassDivider,
                        modifier = Modifier.size(14.dp)
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val trackWidth = maxWidth
                    val thumbSize = 16.dp
                    val availableWidth = (trackWidth - thumbSize).coerceAtLeast(0.dp)
                    val thumbOffset = availableWidth * animatedFraction

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(tv.medusa.app.ui.theme.TvPalette.GlassDivider)
                    )

                    if (animatedFraction > 0f) {
                        val activeColor = if (isMonochrome) TextPrimary else focusColor
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedFraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            activeColor.copy(alpha = 0.65f),
                                            activeColor
                                        )
                                    )
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset)
                            .size(thumbSize)
                            .clip(CircleShape)
                            .background(
                                if (isFocused) TextPrimary else (if (isMonochrome) TextPrimary else focusColor)
                            )
                            .border(
                                1.5.dp,
                                if (isFocused) (if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusColor) else Color(0x99FFFFFF),
                                CircleShape
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isFocused && localDb < maxDb) (if (isMonochrome) Color(0x40FFFFFF) else focusColor.copy(alpha = 0.3f)) else Color(0x15FFFFFF))
                        .clickable {
                            val newDb = (localDb + 1).coerceAtMost(maxDb)
                            if (newDb != localDb) {
                                localDb = newDb
                                onValueChange(newDb)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = if (localDb < maxDb) {
                            if (isFocused) (if (isMonochrome) TextPrimary else focusColor) else TextPrimary
                        } else tv.medusa.app.ui.theme.TvPalette.GlassDivider,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Clean scale bounds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0 dB",
                    fontSize = 10.sp,
                    color = Color(0x66CBD5E1)
                )
                Text(
                    text = "+$maxDb dB",
                    fontSize = 10.sp,
                    color = Color(0x66CBD5E1)
                )
            }
        }
    }
}

@ExperimentalTvMaterial3Api
@Composable
private fun <T> TvSegmentedControlRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    buttonStyle: String = BUTTON_STYLE_ICONS_AND_TEXT,
    options: List<T>,
    selectedOption: T,
    optionLabel: @Composable (T) -> String,
    onOptionSelected: (T) -> Unit,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusContent: Color = tv.medusa.app.ui.theme.TvPalette.FocusInk,
    firstFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x12FFFFFF))
            .border(1.dp, tv.medusa.app.ui.theme.TvPalette.GlassIdle, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title + Subtitle Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showIcons && icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isMonochrome) TextPrimary else focusColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Color(0x99CBD5E1),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Horizontal Segmented Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selectedOption
                Surface(
                    onClick = { onOptionSelected(option) },
                    modifier = Modifier
                        .handleTouchClick { onOptionSelected(option) }
                        .weight(1f)
                        .then(if (index == 0 && firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) {
                            if (isMonochrome) Color(0x40FFFFFF) else focusColor.copy(alpha = 0.28f)
                        } else Color(0x12FFFFFF),
                        focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
                        focusedContentColor = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else focusContent
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 0.5.dp,
                                if (isSelected) {
                                    if (isMonochrome) Color(0x88FFFFFF) else focusColor.copy(alpha = 0.7f)
                                } else tv.medusa.app.ui.theme.TvPalette.GlassIdle
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, if (isMonochrome) TextPrimary else focusColor),
                            shape = RoundedCornerShape(8.dp)
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = optionLabel(option),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvProgressBar(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusRequester: FocusRequester,
    introMarker: SkipMarker? = null,
    creditsMarker: SkipMarker? = null,
    recapMarker: SkipMarker? = null,
    previewMarker: SkipMarker? = null,
    onSeekRelative: (deltaSign: Int) -> Unit,
    onSeekTo: (targetMs: Long) -> Unit,
    onScrubStart: (targetMs: Long) -> Unit,
    onScrubUpdate: (targetMs: Long) -> Unit,
    onCommitSeek: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onDownNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferProgress = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val trackHeight by animateDpAsState(
        targetValue = if (isFocused) 8.dp else 4.dp,
        animationSpec = tween(durationMillis = 150),
        label = "trackHeight"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (isFocused) 22.dp else 12.dp,
        animationSpec = tween(durationMillis = 150),
        label = "thumbSize"
    )

    val accessibilityLabel = stringResource(
        R.string.player_timeline_desc,
        formatTime(currentPositionMs),
        formatTime(durationMs)
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                onFocusChanged(focusState.isFocused)
            }
            .focusProperties {
                up = FocusRequester.Cancel
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        onSeekRelative(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        onSeekRelative(+1)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onCommitSeek()
                        true
                    }
                    Key.DirectionDown -> {
                        onDownNavigation()
                        true
                    }
                    else -> false
                }
            }
            .semantics {
                contentDescription = accessibilityLabel
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                setProgress { targetProgress ->
                    val targetMs = (targetProgress * durationMs).toLong()
                    onSeekTo(targetMs)
                    true
                }
            }
            .pointerInput(durationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width
                    if (width > 0 && durationMs > 0) {
                        val fraction = (down.position.x / width).coerceIn(0f, 1f)
                        val targetMs = (fraction * durationMs).toLong()
                        onScrubStart(targetMs)
                    }
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (change.pressed) {
                            change.consume()
                            val width = size.width
                            if (width > 0 && durationMs > 0) {
                                val fraction = (change.position.x / width).coerceIn(0f, 1f)
                                val targetMs = (fraction * durationMs).toLong()
                                onScrubUpdate(targetMs)
                            }
                        } else {
                            change.consume()
                            onCommitSeek()
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(tv.medusa.app.ui.theme.TvPalette.GlassDivider)
                .then(
                    if (isFocused) {
                        Modifier.border(1.dp, (if (isMonochrome) TextPrimary else focusColor).copy(alpha = 0.5f), RoundedCornerShape(trackHeight / 2))
                    } else Modifier
                )
        )

        // Intro / Credits Markers
        if (durationMs > 0) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
            ) {
                introMarker?.let { intro ->
                    if (intro.showAtMs in 0..durationMs && intro.skipToMs > intro.showAtMs) {
                        val startX = (intro.showAtMs.toFloat() / durationMs.toFloat()) * size.width
                        val endX = (intro.skipToMs.toFloat() / durationMs.toFloat()).coerceAtMost(size.width)
                        drawRect(
                            color = Color(0xFFFFD54F).copy(alpha = 0.5f),
                            topLeft = Offset(startX, 0f),
                            size = Size((endX - startX).coerceAtLeast(3f), size.height)
                        )
                    }
                }
                creditsMarker?.let { credits ->
                    if (credits.showAtMs in 0..durationMs) {
                        val startX = (credits.showAtMs.toFloat() / durationMs.toFloat()) * size.width
                        drawRect(
                            color = Color(0xFF00D2FF).copy(alpha = 0.7f),
                            topLeft = Offset(startX, 0f),
                            size = Size(4f, size.height)
                        )
                    }
                }
                recapMarker?.let { recap ->
                    if (recap.showAtMs in 0..durationMs && recap.skipToMs > recap.showAtMs) {
                        val startX = (recap.showAtMs.toFloat() / durationMs.toFloat()) * size.width
                        val endX = (recap.skipToMs.toFloat() / durationMs.toFloat()).coerceAtMost(size.width)
                        drawRect(
                            color = Color(0xFF81C784).copy(alpha = 0.55f),
                            topLeft = Offset(startX, 0f),
                            size = Size((endX - startX).coerceAtLeast(3f), size.height)
                        )
                    }
                }
                previewMarker?.let { preview ->
                    if (preview.showAtMs in 0..durationMs && preview.skipToMs > preview.showAtMs) {
                        val startX = (preview.showAtMs.toFloat() / durationMs.toFloat()) * size.width
                        val endX = (preview.skipToMs.toFloat() / durationMs.toFloat()).coerceAtMost(size.width)
                        drawRect(
                            color = Color(0xFFBA68C8).copy(alpha = 0.55f),
                            topLeft = Offset(startX, 0f),
                            size = Size((endX - startX).coerceAtLeast(3f), size.height)
                        )
                    }
                }
            }
        }

        // Buffer Track
        if (bufferProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferProgress)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color(0x55FFFFFF))
            )
        }

        // Progress Track
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(
                        if (isMonochrome) {
                            Brush.horizontalGradient(listOf(TextPrimary, TextPrimary))
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00D2FF), focusColor)
                            )
                        }
                    )
            )
        }

        // Thumb Dot / Playhead Knob
        val availableWidth = (maxWidth - thumbSize).coerceAtLeast(0.dp)
        Box(
            modifier = Modifier
                .offset(x = availableWidth * progress)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (isMonochrome) TextPrimary else focusColor)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) TextPrimary else Color(0x66000000),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TextPrimary)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
/**
 * Mapea una pista de ExoPlayer a su MediaStream.Index de Jellyfin usando
 * idioma + códec + canales + IsDefault. Nunca devuelve el índice de ExoPlayer.
 */
private fun mapExoAudioTrackToJellyfinIndex(
    format: androidx.media3.common.Format,
    jellyfinAudioTracks: List<AudioTrackInfo>?
): Int? {
    if (jellyfinAudioTracks.isNullOrEmpty()) return null

    val lang = format.language?.lowercase()?.trim() ?: ""
    val codec = normalizeAudioCodec(format.sampleMimeType)
    val channels = format.channelCount.takeIf { it > 0 }

    val scored = jellyfinAudioTracks.map { jf ->
        val jfLang = jf.language.lowercase().trim()
        val languageMatches = lang.isNotBlank() &&
            (lang == jfLang || lang.startsWith(jfLang) || jfLang.startsWith(lang))
        val codecMatches = codec.isNotBlank() && normalizeAudioCodec(jf.codec) == codec
        val channelsMatch = channels != null && jf.channels != null && jf.channels == channels

        var score = 0
        if (languageMatches) score += 8
        if (codecMatches) score += 4
        if (channelsMatch) score += 2
        if (jf.isDefault) score += 1
        jf to score
    }

    val best = scored.maxByOrNull { it.second }
    return if ((best?.second ?: 0) > 0) best?.first?.index else jellyfinAudioTracks.firstOrNull()?.index
}

private fun normalizeAudioCodec(raw: String?): String {
    val value = raw?.lowercase()?.trim() ?: return ""
    return when {
        value.contains("eac3") || value.contains("e-ac3") || value.contains("ec-3") -> "eac3"
        value.contains("ac3") || value.contains("ac-3") -> "ac3"
        value.contains("dts") || value.contains("dca") -> "dts"
        value.contains("truehd") || value.contains("mlp") -> "truehd"
        value.contains("aac") || value.contains("mp4a") -> "aac"
        value.contains("opus") -> "opus"
        value.contains("flac") -> "flac"
        value.contains("vorbis") -> "vorbis"
        value.contains("pcm") -> "pcm"
        else -> value.substringAfter("/")
    }
}

@OptIn(UnstableApi::class)
private fun mapExoSubtitleTrackToJellyfinIndex(
    format: androidx.media3.common.Format,
    jellyfinSubtitles: List<SubtitleTrackInfo>?
): Int? {
    if (jellyfinSubtitles.isNullOrEmpty()) return null
    val lang = format.language?.lowercase()?.trim() ?: ""
    val matchLang = jellyfinSubtitles.firstOrNull { jf ->
        val jfLang = jf.language.lowercase().trim()
        lang.isNotBlank() && (lang == jfLang || lang.startsWith(jfLang) || jfLang.startsWith(lang))
    }
    return matchLang?.index ?: jellyfinSubtitles.firstOrNull()?.index
}

@OptIn(UnstableApi::class)
private fun extractTracksFromExoPlayer(
    exoPlayer: ExoPlayer,
    context: Context,
    streamInfo: PlayerStreamInfo? = null
): Pair<List<TrackItem>, List<TrackItem>> {
    val audioTracks = mutableListOf<TrackItem>()
    val subtitleTracks = mutableListOf<TrackItem>()

    val currentTracks = exoPlayer.currentTracks
    for (group in currentTracks.groups) {
        val trackType = group.type
        val mediaTrackGroup = group.mediaTrackGroup
        for (i in 0 until mediaTrackGroup.length) {
            val format = mediaTrackGroup.getFormat(i)
            val isSelected = group.isTrackSelected(i)
            val lang = format.language?.takeIf { it.isNotBlank() && it != "und" } ?: context.getString(R.string.player_language_unknown)

            if (trackType == C.TRACK_TYPE_AUDIO) {
                val flag = getLanguageFlag(lang)
                val langName = getLanguageDisplayName(lang, format.label)
                val rawMime = format.sampleMimeType?.lowercase() ?: ""
                val codec = when {
                    rawMime.contains("eac3") || rawMime.contains("e-ac3") -> "E-AC3"
                    rawMime.contains("ac3") -> "AC3"
                    rawMime.contains("dts") -> "DTS"
                    rawMime.contains("truehd") -> "TrueHD"
                    rawMime.contains("flac") -> "FLAC"
                    rawMime.contains("aac") || rawMime.contains("mp4a") -> "AAC"
                    rawMime.contains("opus") -> "OPUS"
                    else -> format.sampleMimeType?.substringAfter("/")?.uppercase() ?: ""
                }
                val channels = when (format.channelCount) {
                    6 -> "5.1"
                    8 -> "7.1"
                    2 -> "2.0"
                    1 -> "1.0"
                    else -> if (format.channelCount > 0) "${format.channelCount} ch" else ""
                }
                val displayParts = listOfNotNull(
                    langName.ifBlank { null },
                    codec.ifBlank { null },
                    channels.ifBlank { null }
                )
                val displayLabel = displayParts.joinToString("   ")
                val jfAudioIndex = mapExoAudioTrackToJellyfinIndex(format, streamInfo?.audioTracks)

                audioTracks.add(
                    TrackItem(
                        group = group,
                        trackIndex = i,
                        label = displayLabel,
                        language = lang,
                        flagEmoji = flag,
                        isSelected = isSelected,
                        jellyfinStreamIndex = jfAudioIndex
                    )
                )
            } else if (trackType == C.TRACK_TYPE_TEXT) {
                val flag = getLanguageFlag(lang)
                val displayLabel = getLanguageDisplayName(lang, format.label)
                val jfSubIndex = mapExoSubtitleTrackToJellyfinIndex(format, streamInfo?.subtitles)

                subtitleTracks.add(
                    TrackItem(
                        group = group,
                        trackIndex = i,
                        label = displayLabel,
                        language = lang,
                        flagEmoji = flag,
                        isSelected = isSelected,
                        jellyfinStreamIndex = jfSubIndex
                    )
                )
            }
        }
    }

    return Pair(audioTracks, subtitleTracks)
}

@OptIn(UnstableApi::class)
private fun selectAudioTrackInExoPlayer(exoPlayer: ExoPlayer, trackItem: TrackItem) {
    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setOverrideForType(
            TrackSelectionOverride(
                trackItem.group.mediaTrackGroup,
                trackItem.trackIndex
            )
        )
        .build()
}

@OptIn(UnstableApi::class)
private fun selectSubtitleTrackInExoPlayer(exoPlayer: ExoPlayer, trackItem: TrackItem?) {
    if (trackItem == null) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
    } else {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setOverrideForType(
                TrackSelectionOverride(
                    trackItem.group.mediaTrackGroup,
                    trackItem.trackIndex
                )
            )
            .build()
    }
}

private fun formatSeekDelta(deltaMs: Long): String {
    val totalSec = abs(deltaMs) / 1000
    val sign = if (deltaMs < 0) "-" else "+"
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min > 0) {
        if (sec > 0) "$sign${min}m ${sec}s" else "$sign${min}m"
    } else {
        "$sign${sec}s"
    }
}

private fun formatTime(timeMs: Long): String {
    if (timeMs <= 0) return "00:00"
    val totalSeconds = timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@ExperimentalTvMaterial3Api
@Composable
private fun ResumePlaybackDialog(
    savedPositionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    isMonochrome: Boolean = false,
    focusColor: Color = TextPrimary,
    focusContent: Color = tv.medusa.app.ui.theme.TvPalette.FocusInk
) {
    val resumeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150L)
        try {
            resumeFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF2090B14))
                .border(0.5.dp, Color(0x24FFFFFF), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Resume button (focused by default) with position included
            Button(
                onClick = onResume,
                modifier = Modifier
                    .handleTouchClick { onResume() }
                    .focusRequester(resumeFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = tv.medusa.app.ui.theme.TvPalette.GlassRaised,
                    contentColor = TextPrimary,
                    focusedContainerColor = if (isMonochrome) TextPrimary else focusColor,
                    focusedContentColor = focusContent
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_resume, formatTime(savedPositionMs)),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Start from beginning button
            Button(
                onClick = onStartOver,
                modifier = Modifier.handleTouchClick { onStartOver() },
                colors = ButtonDefaults.colors(
                    containerColor = Color(0x20FFFFFF),
                    contentColor = TextPrimary,
                    focusedContainerColor = focusColor,
                    focusedContentColor = focusContent
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_from_start),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
