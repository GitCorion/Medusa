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
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.foundation.lazy.items
import tv.medusa.app.util.AudioEnhancementManager
import tv.medusa.app.util.AutoFrameRateManager
import tv.medusa.app.util.AppForeground
import tv.medusa.app.data.local.SUBTITLE_SIZE_SMALL
import tv.medusa.app.data.local.SUBTITLE_SIZE_NORMAL
import tv.medusa.app.data.local.SUBTITLE_SIZE_LARGE
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import androidx.media3.datasource.DefaultHttpDataSource
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
import androidx.tv.material3.Text
import tv.medusa.app.R
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_AND_TEXT
import tv.medusa.app.data.local.BUTTON_STYLE_ICONS_ONLY
import tv.medusa.app.data.local.BUTTON_STYLE_TEXT_ONLY
import tv.medusa.app.data.remote.jellyfin.NextEpisodeInfo
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.jellyfin.SkipMarker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class PlayerModalTab {
    NONE,
    AUDIO,
    SUBTITLES,
    SETTINGS
}

private data class TrackItem(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean
)

@OptIn(UnstableApi::class)
@ExperimentalTvMaterial3Api
@Composable
fun PlayerScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(itemId) {
        Log.i("PlayerScreen", "PlayerScreen LaunchedEffect for itemId=$itemId")
        tv.medusa.app.util.SeasonalAudioManager.release()
        viewModel.loadStreamUrl(itemId)
    }

    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val buttonStyle by viewModel.buttonStyle.collectAsStateWithLifecycle()
    val isMonochrome by viewModel.isMonochrome.collectAsStateWithLifecycle()
    val accentColorKey by viewModel.accentColor.collectAsStateWithLifecycle()
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentColorKey)

    // Load intro markers + next episode once stream is ready
    val introMarker by viewModel.introMarker.collectAsStateWithLifecycle()
    val creditsMarker by viewModel.creditsMarker.collectAsStateWithLifecycle()
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
    if (currentInfo != null) {
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    color = if (isMonochrome) Color.White else focusColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = stringResource(R.string.player_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
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
    nextEpisode: NextEpisodeInfo? = null,
    onBack: () -> Unit,
    onPlayNextEpisode: (NextEpisodeInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isProUser by viewModel.isProUser.collectAsStateWithLifecycle()
    val nightModeAudio by viewModel.nightModeAudio.collectAsStateWithLifecycle()
    val audioVolumeBoostDb by viewModel.audioVolumeBoostDb.collectAsStateWithLifecycle()
    val subtitleSize by viewModel.subtitleSize.collectAsStateWithLifecycle()
    val subtitleBackground by viewModel.subtitleBackground.collectAsStateWithLifecycle()
    val autoFrameRateEnabled by viewModel.autoFrameRateEnabled.collectAsStateWithLifecycle()

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

    // Intro / Credits skip banner visibility
    var showSkipIntroBanner by remember { mutableStateOf(false) }
    var showSkipCreditsBanner by remember { mutableStateOf(false) }

    // Next-episode overlay
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }
    // countdown calculado en vivo desde la posición del reproductor, no un timer fijo
    // -1 = sin cuenta atrás (episodio terminó, mostrar sin contador)
    var nextEpisodeCountdown by remember { mutableIntStateOf(-1) }
    var playbackEnded by remember { mutableStateOf(false) }
    var nextEpisodeTriggered by remember { mutableStateOf(false) }

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
    var duration by remember { mutableLongStateOf(0L) }
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

    val rewindFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val fastForwardFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val audioButtonFocusRequester = remember { FocusRequester() }
    val subtitleButtonFocusRequester = remember { FocusRequester() }
    val settingsButtonFocusRequester = remember { FocusRequester() }

    var previousModalTab by remember { mutableStateOf(PlayerModalTab.NONE) }

    val candidates = info.candidateUrls.ifEmpty { listOf(info.primaryStreamUrl) }

    val exoPlayer = remember(info, retryTrigger) {
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

        val mediaSourceFactory = DefaultMediaSourceFactory(resolvingDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            // ON = prefiere el decodificador de hardware de la plataforma y sólo recurre
            // a la extensión FFmpeg (software) cuando no hay uno válido.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
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

                val mediaItem = MediaItem.Builder()
                    .setUri(candidates.first())
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
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = (playbackState == Player.STATE_BUFFERING)
                        if (playbackState == Player.STATE_READY) {
                            playerErrorMessage = null
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        val (audios, subs) = extractTracksFromExoPlayer(this@apply, context)
                        audioTracks = audios
                        subtitleTracks = subs

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
                        Log.e("PlayerScreen", "ExoPlayer error on candidate $candidateIdx (${candidates.getOrNull(candidateIdx)}): ${error.errorCodeName}", error)

                        // ── Recuperación automática de fallos de audio (Passthrough o pista incompatible) ──
                        val isAudioError = error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                                error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                                error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                                error.cause is AudioSink.InitializationException ||
                                error.cause is AudioSink.WriteException

                        if (isAudioError) {
                            val availableAudios = audioTracks
                            val currentTrack = availableAudios.firstOrNull { it.isSelected }
                            val fallbackTrack = availableAudios.firstOrNull { !it.isSelected }
                            if (fallbackTrack != null) {
                                Log.w("PlayerScreen", "Error en pista de audio (${currentTrack?.label}). Conmutando automáticamente a: ${fallbackTrack.label}")
                                playerErrorMessage = context.getString(R.string.player_audio_track_switched_toast, fallbackTrack.label)
                                selectAudioTrackInExoPlayer(this@apply, fallbackTrack)
                                val pos = currentPosition.coerceAtLeast(0L)
                                if (pos > 0 && isCurrentMediaItemSeekable) seekTo(pos)
                                prepare()
                                playWhenReady = true
                                return
                            }
                        }

                        candidateIdx++
                        if (candidateIdx < candidates.size) {
                            val nextUrl = candidates[candidateIdx]
                            Log.i("PlayerScreen", "Attempting fallback candidate $candidateIdx: $nextUrl")
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
                            if (pos > 0 && isCurrentMediaItemSeekable) seekTo(pos)
                            prepare()
                            playWhenReady = true
                        } else {
                            Log.e("PlayerScreen", "All ${candidates.size} playback candidates failed!")
                            val httpCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
                            val humanMsg = when {
                                httpCode in 500..599 -> context.getString(R.string.player_error_http_5xx, httpCode)
                                httpCode == 401 -> context.getString(R.string.player_error_http_401)
                                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> context.getString(R.string.player_error_bad_http)
                                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> context.getString(R.string.player_error_network)
                                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> context.getString(R.string.player_error_container)
                                error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> context.getString(R.string.player_decoder_unsupported)
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

    // Integración de MediaSession (androidx.media3.session) para Android TV:
    // Permite que los botones físicos del mando de TV (Play, Pause, FastForward, Rewind),
    // mandos Bluetooth / HDMI-CEC y comandos por voz de Google Assistant ("Ok Google, pausa")
    // controlen la reproducción nativamente y mantengan el estado global en el sistema.
    val mediaSession = remember(exoPlayer) {
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
            .setId("MedusaMediaSession_${info.playableId.ifBlank { "main" }}")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    LaunchedEffect(activeModalTab) {
        if (activeModalTab != PlayerModalTab.NONE) {
            val (audios, subs) = extractTracksFromExoPlayer(exoPlayer, context)
            if (audios.isNotEmpty()) audioTracks = audios
            if (subs.isNotEmpty()) subtitleTracks = subs
        } else if (previousModalTab != PlayerModalTab.NONE) {
            delay(100L)
            runCatching {
                when (previousModalTab) {
                    PlayerModalTab.AUDIO -> audioButtonFocusRequester.requestFocus()
                    PlayerModalTab.SUBTITLES -> subtitleButtonFocusRequester.requestFocus()
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
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    isPlaying = false
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    viewModel.reportProgress(pos, isPaused = true, dur)
                }
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

    fun seekRelative(deltaMs: Long) {
        notifyInteraction()
        val now = System.currentTimeMillis()
        val cur = if (isScrubbing) scrubTargetPosition else exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: duration.takeIf { it > 0 } ?: Long.MAX_VALUE

        val timeSinceLast = now - lastScrubKeystrokeTime
        // Aceleración progresiva natural: si se pulsa repetidamente en menos de 280ms, acelera el salto
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

    // Debounce del seek: ejecuta el salto real en ExoPlayer 650 ms después de que el usuario deja de pulsar
    LaunchedEffect(scrubTargetPosition, isScrubbing, lastScrubKeystrokeTime) {
        if (isScrubbing) {
            delay(650L)
            if (isScrubbing) {
                exoPlayer.seekTo(scrubTargetPosition)
                currentPosition = scrubTargetPosition
                isScrubbing = false
                scrubDeltaMs = 0L
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

    // Auto-focus the play/pause button when controls overlay is visible so remote D-pad works immediately!
    LaunchedEffect(showOverlayControls) {
        if (showOverlayControls && playerErrorMessage == null && !showResumeDialog) {
            delay(50L)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Auto-hide controls overlay after 5s if activeModalTab is NONE (resets on user interaction)
    LaunchedEffect(showOverlayControls, activeModalTab, lastInteractionTrigger) {
        if (showOverlayControls && activeModalTab == PlayerModalTab.NONE) {
            delay(5000L)
            showOverlayControls = false
        }
    }

    BackHandler {
        if (showResumeDialog) {
            onBack()
        } else if (activeModalTab != PlayerModalTab.NONE) {
            activeModalTab = PlayerModalTab.NONE
        } else if (showNextEpisodeOverlay) {
            showNextEpisodeOverlay = false
            if (playbackEnded) {
                onBack()
            }
        } else if (showOverlayControls) {
            showOverlayControls = false
        } else {
            onBack()
        }
    }

    // 75-second timeout for initial slow proxy resolution (resolving RD / rescue can take 10-40s)
    LaunchedEffect(info, retryTrigger) {
        delay(75_000L)
        if (exoPlayer.playbackState == Player.STATE_BUFFERING && currentPosition == 0L) {
            playerErrorMessage = context.getString(R.string.player_resolve_timeout)
            isBuffering = false
        }
    }

    // Posición/buffer/duración en vivo para el HUD, los banners y la cuenta atrás del siguiente
    // episodio. Sólo late mientras la UI consume esos valores; en reposo (sin overlay ni
    // marcadores) no escribe estado y, por tanto, no provoca recomposición.
    val needsLivePosition = showOverlayControls ||
            introMarker != null || creditsMarker != null || nextEpisode != null
    LaunchedEffect(exoPlayer, needsLivePosition) {
        if (!needsLivePosition) return@LaunchedEffect
        while (isActive) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            delay(500L)
        }
    }

    // Report de progreso a Jellyfin cada 5 s durante reproducción activa (no toca estado de Compose).
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
                if (stateChanged || (now - lastReportedMs) >= 5000L) {
                    viewModel.reportProgress(pos, isPaused = false, durationMs = exoPlayer.duration.coerceAtLeast(0L))
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
    val latestCreditsMarker by rememberUpdatedState(creditsMarker)
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
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                duration = player.duration.coerceAtLeast(0L)
                bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val dur = exoPlayer.duration.coerceAtLeast(1L)
                    viewModel.reportStopped(dur, dur)
                    currentPosition = dur
                    duration = dur
                    playbackEnded = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            mediaSession.release()
            exoPlayer.removeListener(listener)
            val finalPos = exoPlayer.currentPosition
            val totalDur = exoPlayer.duration
            if (finalPos > 0 && exoPlayer.playerError == null) {
                viewModel.reportStopped(finalPos, totalDur)
            }
            exoPlayer.release()
        }
    }

    // ─── Fin de reproducción (STATE_ENDED) ──────────────────────────────────
    // Si hay siguiente episodio: cuenta atrás de 5s y salto automático.
    // Si es película o último capítulo: salida limpia del reproductor (onBack).
    LaunchedEffect(playbackEnded) {
        if (!playbackEnded) return@LaunchedEffect

        val next = latestNextEpisode
        if (next != null) {
            showNextEpisodeOverlay = true
            for (sec in 5 downTo 1) {
                if (!isActive || !showNextEpisodeOverlay || nextEpisodeTriggered) break
                nextEpisodeCountdown = sec
                delay(1000L)
            }
            if (isActive && showNextEpisodeOverlay && !nextEpisodeTriggered) {
                nextEpisodeCountdown = 0
                playNext(next)
            }
        } else {
            delay(800L)
            if (isActive) {
                latestOnBack()
            }
        }
    }

    // ─── Intro / Credits banner: usa los campos del plugin fielmente ────────────
    // introMarker.showAtMs = ShowSkipPromptAt del plugin
    // ─── Intro / Credits banner: función exclusiva de Medusa Pro (Netflix style) ─
    LaunchedEffect(introMarker, isProUser) {
        val marker = introMarker
        if (marker == null || !isProUser) {
            showSkipIntroBanner = false
            return@LaunchedEffect
        }
        snapshotFlow { currentPosition }.collect { pos ->
            val inWindow = pos in marker.showAtMs..marker.hideAtMs
            if (inWindow != showSkipIntroBanner) {
                showSkipIntroBanner = inWindow
            }
        }
    }

    LaunchedEffect(creditsMarker, isProUser) {
        val marker = creditsMarker
        if (marker == null || !isProUser) {
            showSkipCreditsBanner = false
            return@LaunchedEffect
        }
        snapshotFlow { currentPosition }.collect { pos ->
            val inWindow = pos in marker.showAtMs..marker.hideAtMs
            if (nextEpisode == null && inWindow != showSkipCreditsBanner) {
                showSkipCreditsBanner = inWindow
            }
        }
    }

    // ─── Next episode overlay (exclusivo Medusa Pro) durante créditos ───────────
    LaunchedEffect(creditsMarker, nextEpisode, isProUser) {
        val next = nextEpisode
        if (next == null || !isProUser) {
            if (showNextEpisodeOverlay && !playbackEnded) showNextEpisodeOverlay = false
            return@LaunchedEffect
        }

        val marker = creditsMarker
        if (marker != null) {
            snapshotFlow { currentPosition }.collect { pos ->
                if (playbackEnded || nextEpisodeTriggered) return@collect
                val inRange = pos in marker.showAtMs..marker.hideAtMs
                if (inRange) {
                    nextEpisodeCountdown = ((marker.hideAtMs - pos) / 1000L).toInt().coerceAtLeast(0)
                    if (!showNextEpisodeOverlay) showNextEpisodeOverlay = true
                } else if (pos > marker.hideAtMs) {
                    if (showNextEpisodeOverlay) {
                        playNext(next)
                    }
                } else {
                    if (showNextEpisodeOverlay && !playbackEnded) showNextEpisodeOverlay = false
                }
            }
        }
    }

    // Cuando el countdown llega a 0 (caso A: créditos terminaron), lanzar siguiente episodio
    LaunchedEffect(nextEpisodeCountdown, isProUser) {
        if (isProUser && nextEpisodeCountdown == 0 && showNextEpisodeOverlay && nextEpisode != null) {
            playNext(nextEpisode!!)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (showResumeDialog) return@onKeyEvent false
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                when (keyEvent.key) {
                    Key.MediaRewind -> {
                        seekRelative(-10_000L)
                        true
                    }
                    Key.MediaFastForward -> {
                        seekRelative(+10_000L)
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
                            exoPlayer.seekTo(scrubTargetPosition)
                            currentPosition = scrubTargetPosition
                            isScrubbing = false
                            scrubDeltaMs = 0L
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
                            showOverlayControls = false
                            notifyInteraction()
                            true
                        } else {
                            notifyInteraction()
                            false
                        }
                    }
                    else -> false
                }
            }
            .clickable(enabled = !showResumeDialog) {
                notifyInteraction()
                showOverlayControls = !showOverlayControls
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
                }
            },
            update = { playerView ->
                // Reasignar siempre: el player se recrea con remember(info, retryTrigger) y,
                // sin esto, tras un "Reintentar" el PlayerView seguía enlazado al player liberado.
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
                onResume = {
                    showResumeDialog = false
                    resumeDecisionMade = true
                    chosenStartPositionMs = info.startPositionMs
                    exoPlayer.seekTo(info.startPositionMs)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                },
                onStartOver = {
                    showResumeDialog = false
                    resumeDecisionMade = true
                    chosenStartPositionMs = 0L
                    exoPlayer.seekTo(0L)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                },
                isMonochrome = isMonochrome,
                focusColor = focusColor,
                focusContent = focusContent
            )
        }

        // Buffering / Loading Indicator Overlay
        if (isBuffering && playerErrorMessage == null && !showResumeDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xEE121320))
                        .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    JellyLoadingIndicator(
                        size = 28.dp,
                        message = null,
                        isMonochrome = isMonochrome
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.player_loading_playback),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Playback Error Overlay
        if (playerErrorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xEE1A1828))
                        .border(1.dp, Color(0x66FF5252), RoundedCornerShape(20.dp))
                        .padding(horizontal = 36.dp, vertical = 28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.player_error_cd),
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.player_playback_error),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playerErrorMessage ?: stringResource(R.string.player_cannot_play_file),
                        color = Color(0xFFDDDDDD),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                playerErrorMessage = null
                                retryTrigger++
                            },
                            modifier = Modifier.focusRequester(retryFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF3F51B5),
                                focusedContainerColor = Color(0xFF5C6BC0)
                            )
                        ) {
                            Text(stringResource(R.string.common_retry), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF2C2D3C),
                                focusedContainerColor = Color(0xFF424458)
                            )
                        ) {
                            Text(stringResource(R.string.common_back), color = Color.White)
                        }
                    }
                }
            }
        }

        // Cinematic Scrubber HUD Bubble (Apple TV+ / Infuse style)
        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showOverlayControls) 124.dp else 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF2080A14))
                    .border(0.75.dp, if (isMonochrome) Color(0x33FFFFFF) else focusColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (scrubDeltaMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = if (isMonochrome) Color.White else focusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = formatSeekDelta(scrubDeltaMs),
                        color = if (isMonochrome) Color.White else focusColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(Color(0x33FFFFFF))
                    )
                    Text(
                        text = formatTime(scrubTargetPosition),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = info.title.ifBlank { stringResource(R.string.player_playing) },
                            color = Color.White,
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
                                        seekRelative(-10_000L)
                                    },
                                    icon = Icons.Default.FastRewind,
                                    contentDescription = stringResource(R.string.player_rewind_10),
                                    size = 42.dp,
                                    iconSize = 20.dp,
                                    containerColor = Color(0x22FFFFFF),
                                    focusedContainerColor = if (isMonochrome) Color.White else focusColor,
                                    contentColor = Color.White,
                                    focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusRequester = rewindFocusRequester,
                                    modifier = Modifier.focusProperties {
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
                                    containerColor = if (isMonochrome) Color.White else focusColor,
                                    focusedContainerColor = if (isMonochrome) Color.White else focusColor,
                                    contentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent,
                                    focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusRequester = playPauseFocusRequester,
                                    modifier = Modifier.focusProperties {
                                        left = rewindFocusRequester
                                        right = fastForwardFocusRequester
                                    }
                                )

                                // 3. Fast Forward 10s (Avance)
                                TvCircularIconButton(
                                    onClick = {
                                        seekRelative(+10_000L)
                                    },
                                    icon = Icons.Default.FastForward,
                                    contentDescription = stringResource(R.string.player_forward_10),
                                    size = 42.dp,
                                    iconSize = 20.dp,
                                    containerColor = Color(0x22FFFFFF),
                                    focusedContainerColor = if (isMonochrome) Color.White else focusColor,
                                    contentColor = Color.White,
                                    focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusRequester = fastForwardFocusRequester,
                                    modifier = Modifier.focusProperties {
                                        left = playPauseFocusRequester
                                        right = audioButtonFocusRequester
                                    }
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // Time Indicator
                                Text(
                                    text = "${formatTime(displayPos)} / ${formatTime(duration)}",
                                    color = if (isScrubbing && !isMonochrome) focusColor else Color(0xFFCBD5E1),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Right Group: Audio, Subtitles, Settings (Respects buttonStyle)
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
                                    label = stringResource(R.string.player_audio),
                                    isSelected = activeModalTab == PlayerModalTab.AUDIO,
                                    buttonStyle = buttonStyle,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    modifier = Modifier
                                        .focusRequester(audioButtonFocusRequester)
                                        .focusProperties {
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
                                    icon = Icons.Default.Subtitles,
                                    label = stringResource(R.string.player_subtitles),
                                    isSelected = activeModalTab == PlayerModalTab.SUBTITLES,
                                    buttonStyle = buttonStyle,
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    modifier = Modifier
                                        .focusRequester(subtitleButtonFocusRequester)
                                        .focusProperties {
                                            left = audioButtonFocusRequester
                                            right = settingsButtonFocusRequester
                                        }
                                )

                                // 6. Settings Selector Button
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
                                            left = subtitleButtonFocusRequester
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
                currentSpeed = currentPlaybackSpeed,
                currentResizeMode = currentResizeMode,
                nightModeAudio = nightModeAudio,
                audioVolumeBoostDb = audioVolumeBoostDb,
                subtitleSize = subtitleSize,
                subtitleBackground = subtitleBackground,
                autoFrameRateEnabled = autoFrameRateEnabled,
                onSelectAudioTrack = { track ->
                    notifyInteraction()
                    selectAudioTrackInExoPlayer(exoPlayer, track)
                },
                onSelectSubtitleTrack = { track ->
                    notifyInteraction()
                    selectSubtitleTrackInExoPlayer(exoPlayer, track)
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

        // ─── Skip Intro Banner (bottom-right, Netflix-style) ─────────────────
        AnimatedVisibility(
            visible = showSkipIntroBanner && !showResumeDialog && activeModalTab == PlayerModalTab.NONE,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 120.dp)
        ) {
            SkipIntroBanner(
                isMonochrome = isMonochrome,
                focusColor = focusColor,
                focusContent = focusContent,
                onSkip = {
                    // skipToMs = IntroEnd del plugin, el campo exacto para saltar
                    val skipTo = introMarker?.skipToMs ?: return@SkipIntroBanner
                    exoPlayer.seekTo(skipTo)
                    showSkipIntroBanner = false
                    notifyInteraction()
                }
            )
        }

        // ─── Skip Credits Banner ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = showSkipCreditsBanner && !showResumeDialog && activeModalTab == PlayerModalTab.NONE,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 120.dp)
        ) {
            SkipIntroBanner(
                label = stringResource(R.string.player_skip_credits),
                isMonochrome = isMonochrome,
                focusColor = focusColor,
                focusContent = focusContent,
                onSkip = {
                    // skipToMs = EndPositionTicks del segmento Outro
                    val skipTo = creditsMarker?.skipToMs ?: return@SkipIntroBanner
                    exoPlayer.seekTo(skipTo)
                    showSkipCreditsBanner = false
                    notifyInteraction()
                }
            )
        }

        // ─── Next Episode Overlay (Apple TV+ style, bottom-right) ────────────
        AnimatedVisibility(
            visible = showNextEpisodeOverlay && !showResumeDialog && nextEpisode != null,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 120.dp)
        ) {
            nextEpisode?.let { ep ->
                NextEpisodeCard(
                    nextEpisode = ep,
                    countdown = nextEpisodeCountdown,
                    isMonochrome = isMonochrome,
                    focusColor = focusColor,
                    focusContent = focusContent,
                    onPlayNow = {
                        playNext(ep)
                    },
                    onDismiss = {
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
    focusColor: Color = Color.White,
    focusContent: Color = Color(0xFF0F172A),
    onSkip: () -> Unit
) {
    val skipFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120L)
        runCatching { skipFocusRequester.requestFocus() }
    }

    Surface(
        onClick = onSkip,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xEE1A1C2E),
            focusedContainerColor = if (isMonochrome) Color.White else focusColor,
            contentColor = Color.White,
            focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.5.dp, if (isMonochrome) Color(0x66FFFFFF) else focusColor.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, if (isMonochrome) Color.White else focusColor),
                shape = RoundedCornerShape(14.dp)
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
private fun NextEpisodeCard(
    nextEpisode: NextEpisodeInfo,
    countdown: Int,
    isMonochrome: Boolean = false,
    focusColor: Color = Color.White,
    focusContent: Color = Color(0xFF0F172A),
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120L)
        runCatching { playFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .width(360.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF0111422))
            .border(1.5.dp, if (isMonochrome) Color(0x33FFFFFF) else focusColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    // Si hay countdown activo: "Siguiente en Xs" · Si no (STATE_ENDED): "Siguiente episodio"
                    text = if (countdown > 0) stringResource(R.string.player_next_in, countdown) else stringResource(R.string.player_next_episode),
                    color = if (isMonochrome) Color.White else focusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                // Círculo de countdown sólo cuando hay créditos con tiempo restante real
                if (countdown > 0) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isMonochrome) Color(0x22FFFFFF) else focusColor.copy(alpha = 0.18f))
                            .border(1.5.dp, if (isMonochrome) Color.White else focusColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$countdown",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Episode info row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Episode code badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMonochrome) Color(0x22FFFFFF) else focusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = nextEpisode.episodeCode,
                        color = if (isMonochrome) Color.White else focusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = nextEpisode.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Play now (primary, focused by default)
                Surface(
                    onClick = onPlayNow,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isMonochrome) Color.White else focusColor,
                        focusedContainerColor = if (isMonochrome) Color(0xFFE0E7FF) else focusColor,
                        contentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent,
                        focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent
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
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.player_play_now),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dismiss
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0x22FFFFFF),
                        focusedContainerColor = Color(0x44FFFFFF),
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    )
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
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
    containerColor: Color = Color(0x22FFFFFF),
    focusedContainerColor: Color = Color.White,
    contentColor: Color = Color.White,
    focusedContentColor: Color = Color(0xFF0F172A),
    isMonochrome: Boolean = false,
    focusColor: Color = Color.White,
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
            border = Border(border = BorderStroke(1.dp, Color(0x18FFFFFF)), shape = CircleShape),
            focusedBorder = Border(border = BorderStroke(1.5.dp, if (isMonochrome) Color.White else focusColor), shape = CircleShape)
        ),
        modifier = modifier
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
    buttonStyle: String = BUTTON_STYLE_ICONS_AND_TEXT,
    isMonochrome: Boolean = false,
    focusColor: Color = Color.White,
    focusContent: Color = Color(0xFF0F172A),
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val showIcons = buttonStyle != BUTTON_STYLE_TEXT_ONLY
    val showText = buttonStyle != BUTTON_STYLE_ICONS_ONLY

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) (if (isMonochrome) Color(0x35FFFFFF) else focusColor.copy(alpha = 0.22f)) else Color(0x18FFFFFF),
            focusedContainerColor = if (isMonochrome) Color.White else focusColor,
            contentColor = Color.White,
            focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, if (isSelected) (if (isMonochrome) Color.White else focusColor) else Color(0x20FFFFFF)),
                shape = RoundedCornerShape(20.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, if (isMonochrome) Color.White else focusColor),
                shape = RoundedCornerShape(20.dp)
            )
        ),
        modifier = modifier
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
            val currentContentColor = if (isFocused) (if (isMonochrome) Color(0xFF0F172A) else focusContent) else Color.White
            if (showIcons) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = currentContentColor,
                    modifier = Modifier.size(16.dp)
                )
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
    currentSpeed: Float,
    currentResizeMode: Int,
    nightModeAudio: Int = 0,
    audioVolumeBoostDb: Int = 0,
    subtitleSize: String = SUBTITLE_SIZE_NORMAL,
    subtitleBackground: Boolean = false,
    autoFrameRateEnabled: Boolean = false,
    onSelectAudioTrack: (TrackItem) -> Unit,
    onSelectSubtitleTrack: (TrackItem?) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectResizeMode: (Int) -> Unit,
    onSelectNightMode: (Int) -> Unit = {},
    onSelectVolumeBoost: (Int) -> Unit = {},
    onSelectSubtitleSize: (String) -> Unit = {},
    onToggleSubtitleBackground: (Boolean) -> Unit = {},
    onToggleAutoFrameRate: (Boolean) -> Unit = {},
    onClose: () -> Unit,
    isMonochrome: Boolean = false,
    focusColor: Color = Color.White,
    focusContent: Color = Color(0xFF0F172A)
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
                    if (isMonochrome) Color(0x22FFFFFF) else focusColor.copy(alpha = 0.25f),
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
                        val titleIcon = when (activeTab) {
                            PlayerModalTab.AUDIO -> Icons.Default.Audiotrack
                            PlayerModalTab.SUBTITLES -> Icons.Default.Subtitles
                            PlayerModalTab.SETTINGS -> Icons.Default.Tune
                            PlayerModalTab.NONE -> Icons.Default.Settings
                        }
                        val titleText = when (activeTab) {
                            PlayerModalTab.AUDIO -> stringResource(R.string.player_tab_audio)
                            PlayerModalTab.SUBTITLES -> stringResource(R.string.player_tab_subtitles)
                            PlayerModalTab.SETTINGS -> stringResource(R.string.player_tab_settings)
                            PlayerModalTab.NONE -> ""
                        }

                        Icon(
                            imageVector = titleIcon,
                            contentDescription = null,
                            tint = if (isMonochrome) Color.White else focusColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = titleText,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0x18FFFFFF),
                            contentColor = Color.White,
                            focusedContainerColor = if (isMonochrome) Color.White else focusColor,
                            focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent
                        ),
                        modifier = Modifier.size(34.dp)
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
                                    color = Color(0xFFCBD5E1),
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

                            // 2. Modo Nocturno / DRC (Segmented Option Card)
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                TvSegmentedControlRow(
                                    title = stringResource(R.string.player_night_mode),
                                    subtitle = stringResource(R.string.player_night_mode_desc),
                                    icon = Icons.Default.Bedtime,
                                    options = listOf(0, 1, 2),
                                    selectedOption = nightModeAudio,
                                    optionLabel = { level ->
                                        when (level) {
                                            1 -> stringResource(R.string.player_night_mode_soft)
                                            2 -> stringResource(R.string.player_night_mode_strong)
                                            else -> stringResource(R.string.player_night_mode_off)
                                        }
                                    },
                                    onOptionSelected = { onSelectNightMode(it) },
                                    isMonochrome = isMonochrome,
                                    focusColor = focusColor,
                                    focusContent = focusContent,
                                    firstFocusRequester = if (audioTracks.isEmpty()) firstItemFocusRequester else null
                                )
                            }

                            // 3. Amplificador de Volumen / Diálogos (Segmented Option Card)
                            item {
                                Spacer(modifier = Modifier.height(2.dp))
                                TvSegmentedControlRow(
                                    title = stringResource(R.string.player_volume_boost),
                                    subtitle = stringResource(R.string.player_volume_boost_desc),
                                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                                    options = listOf(0, 3, 6, 10, 15),
                                    selectedOption = audioVolumeBoostDb,
                                    optionLabel = { db ->
                                        when (db) {
                                            0 -> stringResource(R.string.player_volume_boost_original)
                                            else -> "+$db dB"
                                        }
                                    },
                                    onOptionSelected = { onSelectVolumeBoost(it) },
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

                        PlayerModalTab.SETTINGS -> {
                            // Velocidad de reproducción (Segmented Option Card)
                            item {
                                TvSegmentedControlRow(
                                    title = stringResource(R.string.player_playback_speed),
                                    icon = Icons.Default.Tune,
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
                                    subtitle = "Sincroniza la tasa de refresco (Hz) de la TV",
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
    isMonochrome: Boolean = false,
    focusColor: Color = Color.White,
    focusContent: Color = Color(0xFF0F172A),
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) (if (isMonochrome) Color(0x35FFFFFF) else focusColor.copy(alpha = 0.22f)) else Color(0x12FFFFFF),
            focusedContainerColor = if (isMonochrome) Color.White else focusColor,
            focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    1.dp,
                    if (isSelected) (if (isMonochrome) Color(0x66FFFFFF) else focusColor.copy(alpha = 0.5f)) else Color(0x14FFFFFF)
                ),
                shape = RoundedCornerShape(10.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, if (isMonochrome) Color.White else focusColor),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        modifier = Modifier
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
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) (if (isMonochrome) Color.White else focusColor) else Color(0xFF94A3B8)
                    )
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
                    tint = if (isMonochrome) Color.White else focusColor,
                    modifier = Modifier.size(18.dp)
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
    options: List<T>,
    selectedOption: T,
    optionLabel: @Composable (T) -> String,
    onOptionSelected: (T) -> Unit,
    isMonochrome: Boolean = false,
    focusColor: Color = Color.White,
    focusContent: Color = Color(0xFF0F172A),
    firstFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x12FFFFFF))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title + Subtitle Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isMonochrome) Color.White else focusColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
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
                        .weight(1f)
                        .then(if (index == 0 && firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) {
                            if (isMonochrome) Color(0x40FFFFFF) else focusColor.copy(alpha = 0.28f)
                        } else Color(0x12FFFFFF),
                        focusedContainerColor = if (isMonochrome) Color.White else focusColor,
                        focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 0.5.dp,
                                if (isSelected) {
                                    if (isMonochrome) Color(0x88FFFFFF) else focusColor.copy(alpha = 0.7f)
                                } else Color(0x18FFFFFF)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, if (isMonochrome) Color.White else focusColor),
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
    focusColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferProgress = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x33FFFFFF))
        )

        // Buffer Track
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferProgress)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x55FFFFFF))
        )

        // Progress Track
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isMonochrome) {
                        Brush.horizontalGradient(listOf(Color.White, Color.White))
                    } else {
                        Brush.horizontalGradient(
                            listOf(Color(0xFF22D3EE), focusColor)
                        )
                    }
                )
        )

        // Thumb Dot
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(14.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(if (isMonochrome) Color.White else focusColor)
                    .border(1.dp, Color(0x66000000), CircleShape)
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun extractTracksFromExoPlayer(exoPlayer: ExoPlayer, context: Context): Pair<List<TrackItem>, List<TrackItem>> {
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
            val rawLabel = format.label?.takeIf { it.isNotBlank() } ?: context.getString(R.string.player_track_default, i + 1)

            if (trackType == C.TRACK_TYPE_AUDIO) {
                val codec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: ""
                val channels = if (format.channelCount > 0) "${format.channelCount} ch" else ""
                val displayParts = listOfNotNull(
                    rawLabel.ifBlank { null },
                    lang.uppercase(),
                    codec.ifBlank { null },
                    channels.ifBlank { null }
                )
                val displayLabel = displayParts.joinToString(" • ")

                audioTracks.add(
                    TrackItem(
                        group = group,
                        trackIndex = i,
                        label = displayLabel,
                        language = lang,
                        isSelected = isSelected
                    )
                )
            } else if (trackType == C.TRACK_TYPE_TEXT) {
                val displayLabel = if (!rawLabel.contains(lang, ignoreCase = true)) {
                    "$rawLabel (${lang.uppercase()})"
                } else {
                    rawLabel
                }

                subtitleTracks.add(
                    TrackItem(
                        group = group,
                        trackIndex = i,
                        label = displayLabel,
                        language = lang,
                        isSelected = isSelected
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
    focusColor: Color = Color.White,
    focusContent: Color = Color(0xFF0F172A)
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
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xEE121424))
                .border(1.dp, if (isMonochrome) Color(0x33FFFFFF) else focusColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Resume button (focused by default) with position included
            Button(
                onClick = onResume,
                modifier = Modifier.focusRequester(resumeFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = if (isMonochrome) Color.White else focusColor,
                    contentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent,
                    focusedContainerColor = if (isMonochrome) Color(0xFFE0E7FF) else focusColor,
                    focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_resume, formatTime(savedPositionMs)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Start from beginning button
            Button(
                onClick = onStartOver,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0x22FFFFFF),
                    contentColor = Color.White,
                    focusedContainerColor = if (isMonochrome) Color.White else focusColor,
                    focusedContentColor = if (isMonochrome) Color(0xFF0F172A) else focusContent
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
