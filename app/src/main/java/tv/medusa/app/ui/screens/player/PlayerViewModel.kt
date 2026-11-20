package tv.medusa.app.ui.screens.player

import android.media.MediaCodecList
import tv.medusa.app.data.remote.jellyfin.CatalogoFormatUtils
import tv.medusa.app.data.remote.jellyfin.CatalogoTorrentStatusDto
import tv.medusa.app.data.remote.jellyfin.JellyfinAuthRequest
import tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfile
import tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder
import tv.medusa.app.data.remote.jellyfin.JellyfinIntroTimestamps
import tv.medusa.app.data.remote.jellyfin.JellyfinItemDto
import tv.medusa.app.data.remote.jellyfin.JellyfinMediaSourceDto
import tv.medusa.app.data.remote.jellyfin.JellyfinPlaybackInfoRequest
import tv.medusa.app.data.remote.jellyfin.JellyfinPlaybackSourceResolver
import tv.medusa.app.data.remote.jellyfin.TorrentOverlayUiState
import tv.medusa.app.util.DeviceUtils
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import tv.medusa.app.R
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.jellyfin.JellyfinApiService
import tv.medusa.app.data.remote.jellyfin.JellyfinPlaybackProgressRequest
import tv.medusa.app.data.remote.jellyfin.NextEpisodeInfo
import tv.medusa.app.data.remote.jellyfin.SkipMarker
import tv.medusa.app.data.local.db.AppDatabase
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.data.repository.buildJellyfinAuthHeader
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.usecase.GetHomeFeedUseCase
import tv.medusa.app.util.TvChannelManager
import tv.medusa.app.util.toOptimizedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SubtitleTrackInfo(
    val url: String,
    val language: String,
    val title: String,
    val codec: String,
    val isDefault: Boolean,
    val index: Int = -1
)

data class AudioTrackInfo(
    val index: Int,
    val language: String,
    val title: String,
    val codec: String,
    val isDefault: Boolean,
    val channels: Int? = null,
    val channelLayout: String? = null
)

data class PlayerStreamInfo(
    val playableId: String,
    val primaryStreamUrl: String,
    val candidateUrls: List<String> = emptyList(),
    val subtitles: List<SubtitleTrackInfo> = emptyList(),
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val title: String = "",
    val startPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val token: String = "",
    val authHeader: String = "",
    val availableVersions: List<JellyfinMediaSourceDto> = emptyList(),
    val currentVersionName: String = "",
    val seriesId: String? = null,
    val playSessionId: String = "",
    val mediaSourceId: String = "",
    val isStrm: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _streamInfo = MutableStateFlow<PlayerStreamInfo?>(null)
    val streamInfo: StateFlow<PlayerStreamInfo?> = _streamInfo.asStateFlow()

    val buttonStyle: StateFlow<String> = userPreferencesRepository.buttonStyle
    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome
    val accentColor: StateFlow<String> = userPreferencesRepository.accentColor
    val isProUser: StateFlow<Boolean> = userPreferencesRepository.isProUser
    val nightModeAudio: StateFlow<Int> = userPreferencesRepository.nightModeAudio
    private val _audioVolumeBoostDb = MutableStateFlow(userPreferencesRepository.audioVolumeBoostDb.value)
    val audioVolumeBoostDb: StateFlow<Int> = _audioVolumeBoostDb.asStateFlow()
    private var volumeBoostPersistJob: Job? = null

    init {
        viewModelScope.launch {
            userPreferencesRepository.audioVolumeBoostDb.collect { db ->
                if (volumeBoostPersistJob == null || !volumeBoostPersistJob!!.isActive) {
                    _audioVolumeBoostDb.value = db
                }
            }
        }
    }
    val subtitleSize: StateFlow<String> = userPreferencesRepository.subtitleSize
    val subtitleBackground: StateFlow<Boolean> = userPreferencesRepository.subtitleBackground
    val autoFrameRateEnabled: StateFlow<Boolean> = userPreferencesRepository.autoFrameRateEnabled
    val audioPassthroughMode: StateFlow<Int> = userPreferencesRepository.audioPassthroughMode
    val autoPlayNextEpisodeEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.autoPlayNextEpisodeEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoPlayNextMovieEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.autoPlayNextMovieEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAudioPassthroughMode(mode: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAudioPassthroughMode(mode)
        }
    }

    fun setNightModeAudio(level: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setNightModeAudio(level)
        }
    }

    fun setAudioVolumeBoostDb(db: Int) {
        val clamped = db.coerceIn(0, 20)
        _audioVolumeBoostDb.value = clamped
        volumeBoostPersistJob?.cancel()
        volumeBoostPersistJob = viewModelScope.launch {
            delay(350L)
            userPreferencesRepository.setAudioVolumeBoostDb(clamped)
        }
    }

    fun setSubtitleSize(size: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateSubtitleSize(size)
        }
    }

    fun setSubtitleBackground(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateSubtitleBackground(enabled)
        }
    }

    fun setAutoFrameRateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateAutoFrameRate(enabled)
        }
    }

    private var currentPlayableId: String = ""
    private var jellyfinBaseUrl: String = ""
    private var jellyfinAuthHeader: String = ""

    @Volatile
    private var activePlaySessionId: String = ""
    @Volatile
    private var activeMediaSourceId: String = ""
    @Volatile
    private var activeAudioStreamIndex: Int? = null
    @Volatile
    private var activeSubtitleStreamIndex: Int? = null
    @Volatile
    private var activePlayMethod: String = "DirectPlay"

    /**
     * Scope independiente del ViewModel para que el PlaybackStopped final sobreviva a la
     * destrucción del player. No se cancela nunca: cada petición es corta y termina sola.
     */
    private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var hasReportedStart = false
    @Volatile
    private var sessionFinished = false
    @Volatile
    private var lastReportedPositionMs = 0L
    @Volatile
    private var lastDurationMs = 0L

    private var audioFallbackAttempts = 0
    private var fullTranscodeAttempts = 0
    @Volatile
    private var retryInFlight = false

    private val _fatalPlaybackError = MutableStateFlow<String?>(null)
    val fatalPlaybackError: StateFlow<String?> = _fatalPlaybackError.asStateFlow()

    private val _torrentOverlayState = MutableStateFlow<TorrentOverlayUiState?>(null)
    val torrentOverlayState: StateFlow<TorrentOverlayUiState?> = _torrentOverlayState.asStateFlow()

    private var loadStreamJob: Job? = null
    private var torrentZeroProgressStartMs: Long? = null

    fun cancelTorrentPreparation() {
        Log.i("PlayerViewModel", "Torrent preparation cancelled by user")
        loadStreamJob?.cancel()
        _torrentOverlayState.value = null
    }

    fun retryTorrentPreparation() {
        val id = currentPlayableId.takeIf { it.isNotBlank() } ?: return
        Log.i("PlayerViewModel", "Torrent preparation retry requested for $id")
        loadStreamUrl(id, preferredMediaSourceId = activeMediaSourceId.takeIf { it.isNotBlank() }, reuseSessionId = true)
    }

    fun updateActiveAudioStreamIndex(index: Int) {
        activeAudioStreamIndex = index
    }

    fun updateActiveSubtitleStreamIndex(index: Int?) {
        activeSubtitleStreamIndex = index
    }

    /** Item actual para publicar en la fila "Continuar viendo" (Watch Next) del launcher. */
    @Volatile
    private var nowPlaying: MediaItem? = null

    // Perfil de dispositivo adaptativo y soporte 4K HEVC delegados al builder universal
    private val cachedDeviceProfile: JellyfinDeviceProfile
        get() = tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder.createDeviceProfile(
            appContext,
            userPreferencesRepository.audioPassthroughMode.value
        )
    private val cachedDevice4kHevc: Boolean
        by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { canDeviceDecode4kHevc() }

    // ─── Skip markers ──────────────────────────────────────────────────
    // Cada campo de SkipMarker viene directamente del plugin/API, sin valores inventados.
    /** Marcador de intro. Campos: showAt=ShowSkipPromptAt, hideAt=HideSkipPromptAt, skipTo=IntroEnd */
    private val _introMarker = MutableStateFlow<SkipMarker?>(null)
    val introMarker: StateFlow<SkipMarker?> = _introMarker.asStateFlow()

    /** Marcador de outro/créditos. Campos: showAt=StartTicks, hideAt=EndTicks, skipTo=EndTicks */
    private val _creditsMarker = MutableStateFlow<SkipMarker?>(null)
    val creditsMarker: StateFlow<SkipMarker?> = _creditsMarker.asStateFlow()

    // ─── Next episode / media info & recommended list ────────────────────
    private val _nextEpisode = MutableStateFlow<NextEpisodeInfo?>(null)
    val nextEpisode: StateFlow<NextEpisodeInfo?> = _nextEpisode.asStateFlow()

    private val _recommendedList = MutableStateFlow<List<NextEpisodeInfo>>(emptyList())
    val recommendedList: StateFlow<List<NextEpisodeInfo>> = _recommendedList.asStateFlow()

    private fun canDeviceDecode4kHevc(): Boolean {
        if (DeviceUtils.isEmulator()) {
            return false
        }
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                if (!info.isEncoder) {
                    val name = info.name.lowercase()
                    val isSoftware = name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.startsWith("c2.goldfish.")
                    if (!isSoftware) {
                        info.supportedTypes.any { type ->
                            if (type.equals("video/hevc", ignoreCase = true)) {
                                val caps = info.getCapabilitiesForType(type)
                                caps.videoCapabilities?.isSizeSupported(3840, 2160) == true
                            } else false
                        }
                    } else false
                } else false
            }
        } catch (e: Exception) {
            Log.w("PlayerViewModel", "Error checking 4K HEVC capabilities: ${e.message}")
            false
        }
    }

    fun selectVersion(mediaSourceId: String) {
        if (currentPlayableId.isNotBlank()) {
            loadStreamUrl(currentPlayableId, preferredMediaSourceId = mediaSourceId, reuseSessionId = true)
        }
    }

    fun loadStreamUrl(
        itemId: String,
        preferredMediaSourceId: String? = null,
        reuseSessionId: Boolean = false
    ) {
        loadStreamJob?.cancel()
        loadStreamJob = viewModelScope.launch {
            Log.i("PlayerViewModel", "loadStreamUrl START for itemId=$itemId preferredMediaSourceId=$preferredMediaSourceId reuseSessionId=$reuseSessionId")
            if (!reuseSessionId || activePlaySessionId.isBlank()) {
                activePlaySessionId = java.util.UUID.randomUUID().toString().replace("-", "")
            }
            // En una reproducción nueva no arrastramos la pista de audio del ítem anterior.
            // Al cambiar de versión (reuseSessionId=true) se conserva la pista elegida.
            if (!reuseSessionId) {
                activeAudioStreamIndex = null
                activeSubtitleStreamIndex = null
                activeMediaSourceId = ""
            }
            // Nueva carga de reproducción: la sesión anterior ya no está activa.
            sessionFinished = false
            hasReportedStart = false
            audioFallbackAttempts = 0
            fullTranscodeAttempts = 0
            retryInFlight = false
            _fatalPlaybackError.value = null
            _streamInfo.value = null
            _torrentOverlayState.value = null
            torrentZeroProgressStartMs = null
            _introMarker.value = null
            _creditsMarker.value = null
            _nextEpisode.value = null
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val baseUrl = prefs.jellyfinServerUrl
            val token = prefs.jellyfinAccessToken
            val userId = prefs.jellyfinUserId

            jellyfinBaseUrl = baseUrl
            jellyfinAuthHeader = buildJellyfinAuthHeader(token = token)

            val entity = database.jellyfinDao().getItemById(itemId)

            var playableId = itemId
            val api = NetworkClientFactory.createJellyfinService(baseUrl, JellyfinApiService::class.java)

            if (entity?.type?.equals("Series", ignoreCase = true) == true) {
                try {
                    val nextUp = api.getNextUpItems(authHeader = jellyfinAuthHeader, userId = userId, seriesId = itemId)
                    val nextEp = nextUp.items.firstOrNull()
                    if (nextEp != null) {
                        playableId = nextEp.id
                    } else {
                        val episodes = api.getEpisodesForSeries(authHeader = jellyfinAuthHeader, seriesId = itemId, userId = userId)
                        episodes.items.firstOrNull()?.id?.let { playableId = it }
                    }
                } catch (_: Exception) {}
            }

            currentPlayableId = playableId
            // Metadatos para Watch Next (título/póster). Si el ítem no está en Room, se omite.
            nowPlaying = entity?.toOptimizedMediaItem(baseUrl, token, MediaSource.JELLYFIN)

            var effectiveToken = token
            var effectiveUserId = userId
            var effectiveAuthHeader = jellyfinAuthHeader

            // PlaySessionId único y persistente: si renegociamos o cambiamos versión, mantenemos la sesión.
            val androidTvProfile = withContext(Dispatchers.Default) { cachedDeviceProfile }

            // Registrar inmediatamente las capacidades de la sesión en Jellyfin (PlayableMediaTypes, SupportedCommands, DeviceProfile)
            runCatching {
                val caps = tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder.createDefaultCapabilities(
                    appContext,
                    userPreferencesRepository.audioPassthroughMode.value
                )
                api.postCapabilities(authHeader = effectiveAuthHeader, capabilities = caps)
                Log.i("PlayerViewModel", "Session capabilities registered with Jellyfin")
            }

            val entityTicks = entity?.playbackPositionTicks ?: 0L

            val pbRequest = JellyfinPlaybackInfoRequest(
                userId = effectiveUserId,
                playSessionId = activePlaySessionId,
                startTimeTicks = entityTicks,
                enableDirectPlay = true,
                enableDirectStream = true,
                enableTranscoding = true,
                allowVideoStreamCopy = true,
                allowAudioStreamCopy = true,
                enableSubtitleExtraction = false,
                maxStreamingBitrate = 120_000_000L,
                audioStreamIndex = activeAudioStreamIndex,
                deviceProfile = androidTvProfile
            )

            val itemDetailResult = runCatching { api.getItemDetail(authHeader = effectiveAuthHeader, userId = effectiveUserId, itemId = playableId) }

            val is401 = (itemDetailResult.exceptionOrNull() as? retrofit2.HttpException)?.code() == 401 ||
                    (itemDetailResult.isFailure && prefs.jellyfinPassword.isNotBlank())

            var effectiveItemDetailResult = itemDetailResult
            if (is401 && prefs.jellyfinUsername.isNotBlank() && prefs.jellyfinPassword.isNotBlank()) {
                Log.i("PlayerViewModel", "ItemDetail token 401 challenged/revoked. Re-authenticating with Jellyfin credentials...")
                try {
                    val authResp = api.authenticateByName(
                        authHeader = buildJellyfinAuthHeader(token = ""),
                        request = JellyfinAuthRequest(
                            username = prefs.jellyfinUsername,
                            pw = prefs.jellyfinPassword
                        )
                    )
                    effectiveToken = authResp.effectiveToken
                    effectiveUserId = authResp.effectiveUser.effectiveId
                    effectiveAuthHeader = buildJellyfinAuthHeader(token = effectiveToken)
                    jellyfinAuthHeader = effectiveAuthHeader
                    userPreferencesRepository.updateJellyfinConfig(
                        serverUrl = baseUrl,
                        userId = effectiveUserId,
                        accessToken = effectiveToken,
                        username = prefs.jellyfinUsername,
                        password = prefs.jellyfinPassword
                    )
                    effectiveItemDetailResult = runCatching {
                        api.getItemDetail(authHeader = effectiveAuthHeader, userId = effectiveUserId, itemId = playableId)
                    }
                    Log.i("PlayerViewModel", "Re-authentication succeeded! New token saved: ${effectiveToken.take(6)}...")
                } catch (e: Exception) {
                    Log.w("PlayerViewModel", "Re-authentication attempt failed: ${e.message}")
                }
            }

            var itemDto = effectiveItemDetailResult.getOrNull()

            val isStrm = (itemDto?.mediaSources?.any { JellyfinPlaybackSourceResolver.isStrm(it) } == true)

            // Detección y preparación de torrent de Catálogo ANTES de solicitar PlaybackInfo.
            // Si el torrent está frío (stream_ready=false), solicitar PlaybackInfo a Jellyfin bloquearía
            // su motor de análisis multimedia (ffprobe) congelando la petición durante 120s.
            if (isStrm) {
                val backdropUrl = nowPlaying?.backdropUrl ?: entity?.backdropImageTag?.let { tag ->
                    tv.medusa.app.util.JellyfinImageUtils.getBackdropUrl(baseUrl, playableId, tag, token = effectiveToken)
                }
                val releaseIdFromPath = itemDto?.mediaSources?.firstNotNullOfOrNull { ms ->
                    ms.path?.let { path ->
                        runCatching {
                            android.net.Uri.parse(path).getQueryParameter("release")?.toLongOrNull()
                        }.getOrNull()
                    }
                }
                val isReady = handleCatalogoTorrentPreparation(
                    api = api,
                    effectiveAuthHeader = effectiveAuthHeader,
                    playableId = playableId,
                    title = itemDto?.name ?: entity?.title ?: "",
                    backdropUrl = backdropUrl,
                    releaseId = releaseIdFromPath
                )
                if (!isReady) {
                    Log.i("PlayerViewModel", "Catalogo preparation halted or cancelled for $playableId")
                    return@launch
                }
            }

            var playbackInfoResult = runCatching {
                api.getPlaybackInfo(authHeader = effectiveAuthHeader, itemId = playableId, userId = effectiveUserId, startTimeTicks = entityTicks, request = pbRequest)
            }

            val isPb401 = (playbackInfoResult.exceptionOrNull() as? retrofit2.HttpException)?.code() == 401 ||
                    (playbackInfoResult.isFailure && prefs.jellyfinPassword.isNotBlank())

            if (isPb401 && prefs.jellyfinUsername.isNotBlank() && prefs.jellyfinPassword.isNotBlank()) {
                Log.i("PlayerViewModel", "Playback token 401 challenged/revoked. Re-authenticating with Jellyfin credentials...")
                try {
                    val authResp = api.authenticateByName(
                        authHeader = buildJellyfinAuthHeader(token = ""),
                        request = JellyfinAuthRequest(
                            username = prefs.jellyfinUsername,
                            pw = prefs.jellyfinPassword
                        )
                    )
                    effectiveToken = authResp.effectiveToken
                    effectiveUserId = authResp.effectiveUser.effectiveId
                    effectiveAuthHeader = buildJellyfinAuthHeader(token = effectiveToken)
                    jellyfinAuthHeader = effectiveAuthHeader
                    userPreferencesRepository.updateJellyfinConfig(
                        serverUrl = baseUrl,
                        userId = effectiveUserId,
                        accessToken = effectiveToken,
                        username = prefs.jellyfinUsername,
                        password = prefs.jellyfinPassword
                    )
                    val updatedPbRequest = pbRequest.copy(userId = effectiveUserId)
                    playbackInfoResult = runCatching {
                        api.getPlaybackInfo(authHeader = effectiveAuthHeader, itemId = playableId, userId = effectiveUserId, request = updatedPbRequest)
                    }
                    Log.i("PlayerViewModel", "Re-authentication succeeded! New token saved: ${effectiveToken.take(6)}...")
                } catch (e: Exception) {
                    Log.w("PlayerViewModel", "Re-authentication attempt failed: ${e.message}")
                }
            }

            var playbackInfo = playbackInfoResult.getOrNull()

            // Si Jellyfin devolvió un PlaySessionId, usarlo; de lo contrario mantenemos el UUID generado
            playbackInfo?.playSessionId?.takeIf { it.isNotBlank() }?.let { serverSessionId ->
                activePlaySessionId = serverSessionId
            }

            val mediaSources = playbackInfo?.mediaSources?.ifEmpty { null } ?: itemDto?.mediaSources

            val supports4K = withContext(Dispatchers.Default) { cachedDevice4kHevc }
            Log.i("PlayerViewModel", "Device 4K HEVC hardware decode support: $supports4K")

            var chosenMediaSource = if (!preferredMediaSourceId.isNullOrBlank()) {
                mediaSources?.firstOrNull { it.id == preferredMediaSourceId } ?: mediaSources?.firstOrNull()
            } else if (!supports4K && mediaSources != null && mediaSources.size > 1) {
                val primaryName = mediaSources.firstOrNull()?.name?.lowercase().orEmpty()
                val primaryIs4K = primaryName.contains("4k") || primaryName.contains("uhd") || primaryName.contains("2160")
                if (primaryIs4K) {
                    val pH264 = mediaSources.firstOrNull { ms ->
                        val name = ms.name?.lowercase() ?: ""
                        (name.contains("1080") || name.contains("fhd")) && (name.contains("h264") || name.contains("eng+esp") || name.contains("multi"))
                    }
                    val p1080 = mediaSources.firstOrNull { ms ->
                        val name = ms.name?.lowercase() ?: ""
                        name.contains("1080") || name.contains("fhd")
                    }
                    pH264 ?: p1080 ?: mediaSources.firstOrNull()
                } else {
                    mediaSources.firstOrNull()
                }
            } else {
                mediaSources?.firstOrNull()
            }

            // Selección inteligente de pista de audio:
            // Si la pista por defecto no está soportada (ej. DTS-HD en una TV sin soporte HDMI) y existe una
            // pista compatible (ej. AC3 o AAC), forzamos dicha pista y re-negociamos PlaybackInfo para
            // habilitar DirectPlay/DirectStream en lugar de forzar transcodificación completa.
            chosenMediaSource?.let { ms ->
                val audioStreams = ms.mediaStreams.filter { it.type.equals("Audio", ignoreCase = true) }
                val defaultOrFirst = audioStreams.firstOrNull { it.isDefault } ?: audioStreams.firstOrNull()
                val isDefaultSupported = defaultOrFirst != null &&
                        tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder.isAudioCodecSupported(defaultOrFirst.codec, androidTvProfile)
                val compatibleStream = if (isDefaultSupported) {
                    defaultOrFirst
                } else {
                    audioStreams.firstOrNull {
                        tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder.isAudioCodecSupported(it.codec, androidTvProfile)
                    } ?: defaultOrFirst
                }
                activeAudioStreamIndex = compatibleStream?.index

                if (compatibleStream != null && compatibleStream != defaultOrFirst && !ms.supportsDirectPlay) {
                    runCatching {
                        Log.i("PlayerViewModel", "Default audio (${defaultOrFirst?.codec}) not supported. Requesting PlaybackInfo with compatible audio track ${compatibleStream.index} (${compatibleStream.codec})")
                        val audioPbReq = pbRequest.copy(
                            audioStreamIndex = compatibleStream.index,
                            mediaSourceId = ms.id
                        )
                        val renegotiatedPb = api.getPlaybackInfo(
                            authHeader = effectiveAuthHeader,
                            itemId = playableId,
                            userId = effectiveUserId,
                            audioStreamIndex = compatibleStream.index,
                            mediaSourceId = ms.id,
                            request = audioPbReq
                        )
                        val renegotiatedMs = renegotiatedPb.mediaSources.firstOrNull { it.id == ms.id } ?: renegotiatedPb.mediaSources.firstOrNull()
                        if (renegotiatedMs != null) {
                            chosenMediaSource = renegotiatedMs
                            renegotiatedPb.playSessionId?.takeIf { it.isNotBlank() }?.let { activePlaySessionId = it }
                            Log.i("PlayerViewModel", "Renegotiated PlayMethod: supportsDirectPlay=${renegotiatedMs.supportsDirectPlay} supportsDirectStream=${renegotiatedMs.supportsDirectStream}")
                        }
                    }
                }
            }

            val candidateUrls = mutableListOf<String>()
            val subtitles = mutableListOf<SubtitleTrackInfo>()
            val audioTracks = mutableListOf<AudioTrackInfo>()

            chosenMediaSource?.let { mediaSource ->
                val mediaSourceId = mediaSource.id
                val container = mediaSource.container?.lowercase()?.trim() ?: "mkv"
                activeMediaSourceId = mediaSourceId
                activePlayMethod = when {
                    mediaSource.supportsDirectPlay -> "DirectPlay"
                    mediaSource.supportsDirectStream -> "DirectStream"
                    else -> "Transcode"
                }

                val isStrmMediaSource = JellyfinPlaybackSourceResolver.isStrm(mediaSource)
                Log.i("PlayerViewModel", "Chosen MediaSource: id=$mediaSourceId container=$container isStrm=$isStrmMediaSource PlayMethod=$activePlayMethod playSessionId=$activePlaySessionId")

                candidateUrls += JellyfinPlaybackSourceResolver.buildCandidates(
                    baseUrl = baseUrl,
                    playableId = playableId,
                    token = effectiveToken,
                    playSessionId = activePlaySessionId,
                    audioStreamIndex = activeAudioStreamIndex,
                    mediaSource = mediaSource,
                    alternativeMediaSources = mediaSources ?: emptyList()
                )

                if (candidateUrls.isEmpty()) {
                    Log.e("PlayerViewModel", "No playable URL from Jellyfin for itemId=$playableId source=$mediaSourceId")
                    failFatalPlayback()
                    return@launch
                }

                // Parse Subtitles & Audio streams
                for (stream in mediaSource.mediaStreams) {
                    if (stream.type.equals("Subtitle", ignoreCase = true)) {
                        val subUrl = JellyfinPlaybackSourceResolver.buildSubtitleUrl(
                            baseUrl = baseUrl,
                            playableId = playableId,
                            mediaSourceId = mediaSourceId,
                            stream = stream,
                            token = effectiveToken
                        )
                        subtitles.add(
                            SubtitleTrackInfo(
                                url = subUrl,
                                language = stream.language ?: "es",
                                title = stream.displayTitle ?: stream.language ?: appContext.getString(R.string.player_subtitle_default),
                                codec = stream.codec ?: "srt",
                                isDefault = stream.isDefault || stream.isForced,
                                index = stream.index
                            )
                        )
                    } else if (stream.type.equals("Audio", ignoreCase = true)) {
                        audioTracks.add(
                            AudioTrackInfo(
                                index = stream.index,
                                language = stream.language ?: "und",
                                title = stream.displayTitle ?: stream.language ?: appContext.getString(R.string.player_audio_track_default, stream.index),
                                codec = stream.codec ?: "aac",
                                isDefault = (stream.index == activeAudioStreamIndex),
                                channels = stream.channels,
                                channelLayout = stream.channelLayout
                            )
                        )
                    }
                }
            }

            if (candidateUrls.isEmpty()) {
                Log.e("PlayerViewModel", "No playable URL from Jellyfin for itemId=$playableId")
                failFatalPlayback()
                return@launch
            }

            val finalCandidates = applyLanFallback(
                candidates = candidateUrls,
                baseUrl = baseUrl,
                lanUrl = prefs.jellyfinLanServerUrl
            )
            val primaryStreamUrl = finalCandidates.first()

            val rawTicks = itemDto?.userData?.playbackPositionTicks ?: entity?.playbackPositionTicks ?: 0L
            val startPositionMs = (rawTicks / 10_000L).coerceAtLeast(0L)

            val durationTicks = itemDto?.runTimeTicks?.takeIf { it > 0 }
                ?: chosenMediaSource?.runTimeTicks?.takeIf { it > 0 }
                ?: mediaSources?.firstNotNullOfOrNull { it.runTimeTicks?.takeIf { t -> t > 0 } }
                ?: 0L
            val durationMs = (durationTicks / 10_000L).coerceAtLeast(0L)

            val sanitizedPrimary = JellyfinDeviceProfileBuilder.sanitizeUrlForLog(primaryStreamUrl)
            val sanitizedCandidates = finalCandidates.map { JellyfinDeviceProfileBuilder.sanitizeUrlForLog(it) }
            Log.i("PlayerViewModel", "Resolved ${finalCandidates.size} candidate URLs for $playableId. Primary: $sanitizedPrimary, durationMs=$durationMs")
            Log.i("PlayerViewModel", "Candidate URLs: $sanitizedCandidates")

            val seriesId = itemDto?.seriesId ?: entity?.seriesId

            _streamInfo.value = PlayerStreamInfo(
                playableId = playableId,
                primaryStreamUrl = primaryStreamUrl,
                candidateUrls = finalCandidates,
                subtitles = subtitles,
                audioTracks = audioTracks,
                title = itemDto?.name ?: entity?.title ?: "",
                startPositionMs = startPositionMs,
                durationMs = durationMs,
                token = effectiveToken,
                authHeader = effectiveAuthHeader,
                availableVersions = mediaSources ?: emptyList(),
                currentVersionName = chosenMediaSource?.name ?: "",
                seriesId = seriesId,
                playSessionId = activePlaySessionId,
                mediaSourceId = activeMediaSourceId,
                isStrm = chosenMediaSource?.let { JellyfinPlaybackSourceResolver.isStrm(it) } ?: false
            )
        }
    }

    /**
     * Fallback dinámico de transcodificación en tiempo de ejecución.
     * Si ExoPlayer falla por códec de audio o vídeo no soportado localmente por la TV,
     * renegocia con Jellyfin forzando transcode en el servidor manteniendo el mismo PlaySessionId.
     */
    /**
     * Cambio manual de pista de audio. Renegocia PlaybackInfo con el MediaStream.Index de
     * Jellyfin, mantiene el mismo PlaySessionId y reanuda en la posición actual.
     */
    fun selectAudioStream(streamIndex: Int, resumePositionMs: Long) {
        if (streamIndex <= 0) return
        activeAudioStreamIndex = streamIndex
        retryWithTranscoding(
            forceAudioTranscodeOnly = false,
            forceFullTranscode = false,
            resumePositionMs = resumePositionMs,
            consumeRetryBudget = false
        )
    }

    fun retryWithTranscoding(
        forceAudioTranscodeOnly: Boolean = false,
        forceFullTranscode: Boolean = false,
        resumePositionMs: Long = 0L,
        consumeRetryBudget: Boolean = true
    ) {
        if (currentPlayableId.isBlank() || jellyfinBaseUrl.isBlank()) return
        if (retryInFlight) return
        if (consumeRetryBudget) {
            if (forceFullTranscode) {
                if (fullTranscodeAttempts >= 1) {
                    Log.e("PlayerViewModel", "Retry budget exhausted: full transcode already attempted")
                    failFatalPlayback()
                    return
                }
                fullTranscodeAttempts++
            } else {
                if (audioFallbackAttempts >= 1) {
                    Log.e("PlayerViewModel", "Retry budget exhausted: audio fallback already attempted")
                    failFatalPlayback()
                    return
                }
                audioFallbackAttempts++
            }
        }
        retryInFlight = true
        _fatalPlaybackError.value = null
        viewModelScope.launch {
            try {
            Log.i("PlayerViewModel", "retryWithTranscoding START: forceAudioOnly=$forceAudioTranscodeOnly forceFull=$forceFullTranscode resumePosMs=$resumePositionMs session=$activePlaySessionId")
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val baseUrl = jellyfinBaseUrl
            val token = prefs.jellyfinAccessToken
            val userId = prefs.jellyfinUserId
            val api = NetworkClientFactory.createJellyfinService(baseUrl, JellyfinApiService::class.java)

            val androidTvProfile = withContext(Dispatchers.Default) { cachedDeviceProfile }
            val resumeTicks = resumePositionMs * 10_000L

            val transcodeRequest = JellyfinPlaybackInfoRequest(
                userId = userId,
                playSessionId = activePlaySessionId,
                // Un fallo de decoder de audio reintenta siempre en el servidor:
                // DirectPlay/DirectStream desactivados y sin copiar el audio.
                enableDirectPlay = !forceFullTranscode && !forceAudioTranscodeOnly,
                enableDirectStream = !forceFullTranscode && !forceAudioTranscodeOnly,
                enableTranscoding = true,
                allowVideoStreamCopy = !forceFullTranscode,
                allowAudioStreamCopy = !(forceAudioTranscodeOnly || forceFullTranscode),
                enableSubtitleExtraction = false,
                maxStreamingBitrate = 120_000_000L,
                startTimeTicks = resumeTicks,
                audioStreamIndex = activeAudioStreamIndex,
                mediaSourceId = activeMediaSourceId.takeIf { it.isNotBlank() },
                deviceProfile = androidTvProfile
            )

            val pbResult = runCatching {
                api.getPlaybackInfo(
                    authHeader = jellyfinAuthHeader,
                    itemId = currentPlayableId,
                    userId = userId,
                    startTimeTicks = resumeTicks,
                    audioStreamIndex = activeAudioStreamIndex,
                    mediaSourceId = activeMediaSourceId.takeIf { it.isNotBlank() },
                    enableDirectPlay = !forceFullTranscode && !forceAudioTranscodeOnly,
                    enableDirectStream = !forceFullTranscode && !forceAudioTranscodeOnly,
                    enableTranscoding = true,
                    request = transcodeRequest
                )
            }

            val pb = pbResult.getOrNull()
            if (pb == null) {
                Log.e("PlayerViewModel", "retryWithTranscoding failed to obtain PlaybackInfo: ${pbResult.exceptionOrNull()?.message}")
                failFatalPlayback()
                return@launch
            }

            pb.playSessionId?.takeIf { it.isNotBlank() }?.let { activePlaySessionId = it }

            val msList = pb.mediaSources
            val chosenMs = if (activeMediaSourceId.isNotBlank()) {
                msList.firstOrNull { it.id == activeMediaSourceId } ?: msList.firstOrNull()
            } else {
                msList.firstOrNull()
            }

            if (chosenMs == null) {
                Log.e("PlayerViewModel", "retryWithTranscoding: no MediaSource returned")
                failFatalPlayback()
                return@launch
            }

            val candidates: List<String> = if (forceFullTranscode || forceAudioTranscodeOnly) {
                JellyfinPlaybackSourceResolver.buildTranscodeOnlyCandidates(
                    baseUrl = baseUrl,
                    playableId = currentPlayableId,
                    token = token,
                    playSessionId = activePlaySessionId,
                    audioStreamIndex = activeAudioStreamIndex,
                    mediaSource = chosenMs
                )
            } else {
                JellyfinPlaybackSourceResolver.buildCandidates(
                    baseUrl = baseUrl,
                    playableId = currentPlayableId,
                    token = token,
                    playSessionId = activePlaySessionId,
                    audioStreamIndex = activeAudioStreamIndex,
                    mediaSource = chosenMs,
                    alternativeMediaSources = msList
                )
            }

            if (candidates.isEmpty()) {
                Log.w("PlayerViewModel", "retryWithTranscoding produced 0 candidates")
                failFatalPlayback()
                return@launch
            }

            val finalCandidates = applyLanFallback(
                candidates = candidates,
                baseUrl = baseUrl,
                lanUrl = prefs.jellyfinLanServerUrl
            )

            activePlayMethod = when {
                forceFullTranscode || forceAudioTranscodeOnly -> "Transcode"
                chosenMs.supportsDirectPlay -> "DirectPlay"
                chosenMs.supportsDirectStream -> "DirectStream"
                else -> "Transcode"
            }

            val isStrmSource = JellyfinPlaybackSourceResolver.isStrm(chosenMs)
            val currentStream = _streamInfo.value
            _streamInfo.value = currentStream?.copy(
                primaryStreamUrl = finalCandidates.first(),
                candidateUrls = finalCandidates,
                startPositionMs = resumePositionMs,
                playSessionId = activePlaySessionId,
                mediaSourceId = chosenMs.id,
                isStrm = isStrmSource
            ) ?: PlayerStreamInfo(
                playableId = currentPlayableId,
                primaryStreamUrl = finalCandidates.first(),
                candidateUrls = finalCandidates,
                title = "",
                startPositionMs = resumePositionMs,
                durationMs = (chosenMs.runTimeTicks ?: 0L) / 10_000L,
                token = token,
                authHeader = jellyfinAuthHeader,
                availableVersions = msList,
                currentVersionName = chosenMs.name ?: "",
                playSessionId = activePlaySessionId,
                mediaSourceId = chosenMs.id,
                isStrm = isStrmSource
            )
            hasReportedStart = false
            val sanitizedNew = JellyfinDeviceProfileBuilder.sanitizeUrlForLog(finalCandidates.first())
            val sanitizedRetryCandidates = finalCandidates.map { JellyfinDeviceProfileBuilder.sanitizeUrlForLog(it) }
            Log.i("PlayerViewModel", "retryWithTranscoding resolved ${finalCandidates.size} URLs, primary: $sanitizedNew")
            Log.i("PlayerViewModel", "retryWithTranscoding candidates: $sanitizedRetryCandidates")
            } finally {
                retryInFlight = false
            }
        }
    }

    /**
     * Si el usuario configuró una URL LAN distinta de la principal, se antepone esa variante
     * para cada candidato del mismo servidor. El resto de URLs (CDN, .strm público) se deja
     * intacto.
     */
    private fun applyLanFallback(
        candidates: List<String>,
        baseUrl: String,
        lanUrl: String
    ): List<String> {
        val cleanLan = lanUrl.trimEnd('/')
        val cleanBase = baseUrl.trimEnd('/')
        if (cleanLan.isBlank() || cleanLan == cleanBase) return candidates

        val result = linkedSetOf<String>()
        candidates.forEach { url ->
            if (url.startsWith(cleanBase)) {
                result += url.replaceFirst(cleanBase, cleanLan)
            }
            result += url
        }
        return result.toList()
    }

    private fun failFatalPlayback() {
        if (_fatalPlaybackError.value == null) {
            _fatalPlaybackError.value = appContext.getString(R.string.player_cannot_play_file)
        }
    }

    /**
     * Reinicia el estado de error y el presupuesto de reintentos cuando el usuario pulsa
     * "Reintentar". Sin esto, el siguiente fallo volvía a ser fatal de inmediato porque el
     * contador seguía agotado y el Flow de error no emitía de nuevo.
     */
    fun clearPlaybackError() {
        _fatalPlaybackError.value = null
        audioFallbackAttempts = 0
        fullTranscodeAttempts = 0
        retryInFlight = false
    }

    fun reportStart(positionMs: Long) {
        if (currentPlayableId.isBlank() || jellyfinBaseUrl.isBlank() || sessionFinished) return
        if (hasReportedStart) {
            // Pausa -> reanudar no es un nuevo "PlaybackStart"; Jellyfin recibiría eventos
            // duplicados. En su lugar se envía un progreso normal.
            reportProgress(positionMs, isPaused = false, durationMs = lastDurationMs)
            return
        }
        val sessionId = activePlaySessionId
        val msId = activeMediaSourceId
        val audioIdx = activeAudioStreamIndex
        val subIdx = activeSubtitleStreamIndex
        val method = activePlayMethod

        hasReportedStart = true
        lastReportedPositionMs = positionMs

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val api = NetworkClientFactory.createJellyfinService(jellyfinBaseUrl, JellyfinApiService::class.java)
                val ticks = positionMs * 10_000L
                api.reportPlaybackStart(
                    authHeader = jellyfinAuthHeader,
                    request = JellyfinPlaybackProgressRequest(
                        itemId = currentPlayableId,
                        playSessionId = sessionId.takeIf { it.isNotBlank() },
                        mediaSourceId = msId.takeIf { it.isNotBlank() },
                        audioStreamIndex = audioIdx,
                        subtitleStreamIndex = subIdx,
                        positionTicks = ticks,
                        isPaused = false,
                        playMethod = method,
                        canSeek = true
                    )
                )
                Log.i("PlayerViewModel", "Reported PlaybackStart: itemId=$currentPlayableId playSessionId=$sessionId playMethod=$method posMs=$positionMs")
            }.onFailure { e ->
                Log.w("PlayerViewModel", "Failed to report PlaybackStart: ${e.message}")
            }
        }
    }

    fun reportProgress(positionMs: Long, isPaused: Boolean, durationMs: Long = 0L) {
        if (currentPlayableId.isBlank() || jellyfinBaseUrl.isBlank() || sessionFinished) return
        val sessionId = activePlaySessionId
        val msId = activeMediaSourceId
        val audioIdx = activeAudioStreamIndex
        val subIdx = activeSubtitleStreamIndex
        val method = activePlayMethod

        lastReportedPositionMs = positionMs
        if (durationMs > 0L) lastDurationMs = durationMs

        viewModelScope.launch(Dispatchers.IO) {
            nowPlaying?.let { item ->
                if (positionMs > 0L && durationMs > 0L) {
                    runCatching { TvChannelManager.publishToWatchNext(appContext, item, positionMs, durationMs) }
                }
            }
            runCatching {
                val api = NetworkClientFactory.createJellyfinService(jellyfinBaseUrl, JellyfinApiService::class.java)
                val ticks = positionMs * 10_000L
                api.reportPlaybackProgress(
                    authHeader = jellyfinAuthHeader,
                    request = JellyfinPlaybackProgressRequest(
                        itemId = currentPlayableId,
                        playSessionId = sessionId.takeIf { it.isNotBlank() },
                        mediaSourceId = msId.takeIf { it.isNotBlank() },
                        audioStreamIndex = audioIdx,
                        subtitleStreamIndex = subIdx,
                        positionTicks = ticks,
                        isPaused = isPaused,
                        playMethod = method,
                        eventName = "timeupdate",
                        canSeek = true
                    )
                )
            }.onFailure { e ->
                Log.w("PlayerViewModel", "Failed to report PlaybackProgress: ${e.message}")
            }
        }
    }

    /**
     * Detiene la sesión de reproducción una sola vez. Es idempotente: las llamadas
     * posteriores (onDispose, onCleared, STATE_ENDED) no duplican el Stopped.
     */
    fun reportStopped(positionMs: Long, durationMs: Long = 0L) {
        finishPlayback(positionMs, durationMs)
    }

    /**
     * Al volver del segundo plano se reabre la misma sesión (mismo PlaySessionId), de modo
     * que la URL del stream y los reportes sigan usando el identificador que ya tenía.
     */
    fun resumeSessionAfterBackground() {
        if (!sessionFinished) return
        sessionFinished = false
        hasReportedStart = false
    }

    /**
     * Se llama cuando ExoPlayer se libera porque se recrea (fallback, cambio de versión o
     * de modo de audio). No cierra la sesión: solo guarda el progreso.
     */
    fun onPlayerReleased(positionMs: Long, durationMs: Long = 0L) {
        if (currentPlayableId.isBlank() || jellyfinBaseUrl.isBlank() || sessionFinished) return
        if (durationMs > 0L) lastDurationMs = durationMs
        lastReportedPositionMs = positionMs
        if (hasReportedStart) {
            reportProgress(positionMs, isPaused = true, durationMs = durationMs)
        }
    }

    fun finishPlayback(positionMs: Long, durationMs: Long = 0L) {
        if (sessionFinished) return
        val itemId = currentPlayableId
        if (itemId.isBlank()) return
        // No crear una sesión fantasma si el usuario sale antes de que empiece la reproducción.
        if (!hasReportedStart && lastReportedPositionMs <= 0L && positionMs <= 0L) return

        sessionFinished = true
        hasReportedStart = false
        lastReportedPositionMs = positionMs
        if (durationMs > 0L) lastDurationMs = durationMs

        val baseUrl = jellyfinBaseUrl
        val authHeader = jellyfinAuthHeader
        val sessionId = activePlaySessionId
        val msId = activeMediaSourceId

        reportScope.launch {
            val isCompleted = durationMs > 0L && positionMs >= (durationMs * 0.90).toLong()
            val isTooShort = positionMs < 30_000L && !isCompleted
            val finalTicks = if (isCompleted || isTooShort) 0L else positionMs * 10_000L
            val isPlayed = isCompleted
            val nowMs = System.currentTimeMillis()

            // 1. Actualizar Room localmente de inmediato
            runCatching {
                database.jellyfinDao().updatePlaybackPosition(
                    itemId = itemId,
                    positionTicks = finalTicks,
                    isPlayed = isPlayed,
                    lastPlayedTicks = nowMs
                )
            }

            if (isCompleted) {
                val nextEpInfo = _nextEpisode.value
                if (nextEpInfo != null) {
                    runCatching {
                        val existingNext = database.jellyfinDao().getItemById(nextEpInfo.itemId)
                        if (existingNext != null) {
                            database.jellyfinDao().updatePlaybackPosition(
                                itemId = nextEpInfo.itemId,
                                positionTicks = 0L,
                                isPlayed = false,
                                lastPlayedTicks = nowMs + 1000L
                            )
                        }
                    }
                }
            }

            // 2. Invalidar cachés de Home para reflejar el estado de inmediato
            GetHomeFeedUseCase.invalidateFeedCache()
            MediaRepository.invalidateServerCaches()

            // 3. Notificar al servidor Jellyfin con PlaySessionId
            if (baseUrl.isNotBlank() && authHeader.isNotBlank()) {
                runCatching {
                    val api = NetworkClientFactory.createJellyfinService(baseUrl, JellyfinApiService::class.java)
                    api.reportPlaybackStopped(
                        authHeader = authHeader,
                        request = JellyfinPlaybackProgressRequest(
                            itemId = itemId,
                            playSessionId = sessionId.takeIf { it.isNotBlank() },
                            mediaSourceId = msId.takeIf { it.isNotBlank() },
                            positionTicks = finalTicks
                        )
                    )
                    Log.i("PlayerViewModel", "Reported PlaybackStopped: itemId=$itemId playSessionId=$sessionId posMs=$positionMs")
                    if (isCompleted) {
                        val prefs = userPreferencesRepository.userPreferencesFlow.first()
                        if (prefs.jellyfinUserId.isNotBlank()) {
                            api.markPlayed(authHeader = authHeader, userId = prefs.jellyfinUserId, itemId = itemId)
                        }
                    }
                }.onFailure { e ->
                    Log.w("PlayerViewModel", "Failed to report PlaybackStopped: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (!sessionFinished && hasReportedStart && currentPlayableId.isNotBlank() && jellyfinBaseUrl.isNotBlank()) {
            finishPlayback(lastReportedPositionMs, lastDurationMs)
        }
    }

    // ─── Intro Skipper + Next Episode ─────────────────────────────────────────

    /**
     * Loads intro/credits markers and next-episode info for the currently playing item.
     * Priority: Jellyfin 10.10+ MediaSegments API → Intro Skipper plugin legacy endpoint.
     * Call this after [loadStreamUrl] resolves successfully.
     *
     * @param itemId  The Jellyfin ID of the currently playing episode.
     * @param seriesId  The series ID, needed to fetch next-up episode.
     */
    fun loadIntroAndNextEpisode(itemId: String, seriesId: String?) {
        if (jellyfinBaseUrl.isBlank() || jellyfinAuthHeader.isBlank()) return
        viewModelScope.launch {
            val api = NetworkClientFactory.createJellyfinService(jellyfinBaseUrl, JellyfinApiService::class.java)
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val userId = prefs.jellyfinUserId

            // Reset previous state
            _introMarker.value = null
            _creditsMarker.value = null
            _nextEpisode.value = null
            _recommendedList.value = emptyList()

            // 1️⃣ Try native MediaSegments (Jellyfin 10.10+) — covers Intro, Outro, Recap, etc.
            runCatching {
                val segmentsResponse = api.getMediaSegments(
                    authHeader = jellyfinAuthHeader,
                    itemId = itemId
                )
                if (segmentsResponse.items.isNotEmpty()) {
                    for (seg in segmentsResponse.items) {
                        // MediaSegments usa ticks; los convertimos a ms directamente
                        val startMs = seg.startPositionTicks / 10_000L
                        val endMs   = seg.endPositionTicks   / 10_000L
                        if (endMs <= startMs || startMs < 0) continue

                        when (seg.type.trim().lowercase()) {
                            "intro", "opening" -> {
                                // Para intro nativo: showAt=start, hideAt=end, skipTo=end
                                _introMarker.value = SkipMarker(
                                    showAtMs = startMs,
                                    hideAtMs = endMs,
                                    skipToMs = endMs
                                )
                                Log.i("PlayerViewModel", "MediaSegments Intro: ${startMs}ms → ${endMs}ms")
                            }
                            "outro", "credits", "credit", "ending" -> {
                                // Para outro: showAt=start (aquí aparece el card), hideAt=end, skipTo=end
                                _creditsMarker.value = SkipMarker(
                                    showAtMs = startMs,
                                    hideAtMs = endMs,
                                    skipToMs = endMs
                                )
                                Log.i("PlayerViewModel", "MediaSegments Outro: ${startMs}ms → ${endMs}ms")
                            }
                            else -> Log.d("PlayerViewModel", "MediaSegments segment type '${seg.type}' ignored")
                        }
                    }
                }
            }.onFailure { e ->
                Log.d("PlayerViewModel", "MediaSegments API not available (${e.message}), trying Intro Skipper plugin…")
            }

            // 2️⃣ Fallback: Intro Skipper plugin — si todavía no tenemos marcador de intro, consultar plugin
            if (_introMarker.value == null) {
                var introLoaded = false

                fun applyIntroSkipperResult(ts: JellyfinIntroTimestamps) {
                    if (!ts.valid || ts.introEnd <= ts.introStart) return
                    val showPrompt = (ts.showSkipPromptAt?.takeIf { it > 0 } ?: ts.introStart).times(1000).toLong()
                    val hidePrompt = (ts.hideSkipPromptAt?.takeIf { it > showPrompt / 1000.0 } ?: ts.introEnd).times(1000).toLong()
                    val skipTarget = ts.introEnd.times(1000).toLong()
                    if (skipTarget <= showPrompt) return

                    _introMarker.value = SkipMarker(
                        showAtMs = showPrompt,
                        hideAtMs = hidePrompt,
                        skipToMs = skipTarget
                    )
                }

                // Probar ruta v1 primero (builds recientes del plugin)
                runCatching {
                    val ts = api.getIntroTimestampsV1(authHeader = jellyfinAuthHeader, itemId = itemId)
                    if (ts.valid && ts.introEnd > ts.introStart) {
                        applyIntroSkipperResult(ts)
                        introLoaded = true
                        Log.i("PlayerViewModel", "IntroSkipper v1: show=${ts.showSkipPromptAt}s hide=${ts.hideSkipPromptAt}s skipTo=${ts.introEnd}s")
                    }
                }
                if (!introLoaded) {
                    runCatching {
                        val ts = api.getIntroTimestamps(authHeader = jellyfinAuthHeader, itemId = itemId)
                        if (ts.valid && ts.introEnd > ts.introStart) {
                            applyIntroSkipperResult(ts)
                            Log.i("PlayerViewModel", "IntroSkipper base: show=${ts.showSkipPromptAt}s hide=${ts.hideSkipPromptAt}s skipTo=${ts.introEnd}s")
                        }
                    }.onFailure { e ->
                        Log.d("PlayerViewModel", "Intro Skipper plugin not available: ${e.message}")
                    }
                }
            }

            // 3️⃣ Next episode via NextUp (series) OR Next movie recommendation (movies)
            if (!seriesId.isNullOrBlank()) {
                fetchNextEpisode(
                    api = api,
                    authHeader = jellyfinAuthHeader,
                    userId = userId,
                    seriesId = seriesId,
                    currentItemId = itemId,
                    logLabel = "NextEpisode loaded"
                )
            } else {
                fetchNextMovieSuggestion(
                    api = api,
                    authHeader = jellyfinAuthHeader,
                    userId = userId,
                    currentItemId = itemId
                )
            }
        }
    }

    private fun buildNextEpisodeInfo(nextDto: JellyfinItemDto): NextEpisodeInfo {
        val season = nextDto.parentIndexNumber ?: 0
        val episode = nextDto.indexNumber ?: 0
        val thumbUrl = if (nextDto.imageTags?.containsKey("Primary") == true) {
            "$jellyfinBaseUrl/Items/${nextDto.id}/Images/Primary?fillWidth=300"
        } else if (!nextDto.seriesId.isNullOrBlank() && !nextDto.seriesPrimaryImageTag.isNullOrBlank()) {
            "$jellyfinBaseUrl/Items/${nextDto.seriesId}/Images/Primary?fillWidth=300"
        } else null

        return NextEpisodeInfo(
            itemId = nextDto.id,
            title = nextDto.name ?: appContext.getString(R.string.player_next_episode),
            episodeCode = "T$season E$episode",
            thumbUrl = thumbUrl,
            overview = nextDto.overview,
            isMovie = false
        )
    }

    private suspend fun fetchNextEpisode(
        api: JellyfinApiService,
        authHeader: String,
        userId: String,
        seriesId: String?,
        currentItemId: String,
        logLabel: String
    ): NextEpisodeInfo? {
        if (seriesId.isNullOrBlank()) return null
        return runCatching {
            val nextUp = api.getNextUpItems(
                authHeader = authHeader,
                userId = userId,
                seriesId = seriesId
            )
            // NextUp puede devolver el episodio actual si sigue en curso; se ignora.
            val nextDto = nextUp.items.firstOrNull { it.id != currentItemId }
            nextDto?.let { buildNextEpisodeInfo(it) }?.also { info ->
                _nextEpisode.value = info
                Log.i("PlayerViewModel", "$logLabel: ${nextDto.id} ${nextDto.name}")
            }
        }.onFailure { e ->
            Log.d("PlayerViewModel", "$logLabel failed: ${e.message}")
        }.getOrNull()
    }

    private suspend fun fetchNextMovieSuggestion(
        api: JellyfinApiService,
        authHeader: String,
        userId: String,
        currentItemId: String
    ): NextEpisodeInfo? {
        return runCatching {
            val candidateDtos = mutableListOf<JellyfinItemDto>()
            val codeText = appContext.getString(R.string.player_recommended_movie)

            // A) Consultar películas similares vía API
            val similarRes = runCatching {
                api.getSimilarItems(
                    authHeader = authHeader,
                    itemId = currentItemId,
                    userId = userId,
                    limit = 10
                )
            }.getOrNull()

            if (similarRes?.items != null) {
                candidateDtos.addAll(similarRes.items.filter { it.id != currentItemId })
            }

            // B) Fallback / complemento: buscar películas de la biblioteca
            if (candidateDtos.size < 5) {
                val libraryRes = runCatching {
                    api.getLibraryItems(
                        authHeader = authHeader,
                        userId = userId,
                        includeItemTypes = "Movie",
                        filters = "IsUnplayed",
                        limit = 10,
                        sortBy = "DateCreated",
                        sortOrder = "Descending"
                    )
                }.getOrNull()

                libraryRes?.items?.filter { it.id != currentItemId }?.let { items ->
                    for (item in items) {
                        if (candidateDtos.none { it.id == item.id }) {
                            candidateDtos.add(item)
                        }
                    }
                }
            }

            val mappedInfoList = candidateDtos.take(5).map { dto ->
                val year = dto.productionYear?.let { " • $it" } ?: ""
                val thumbUrl = if (dto.imageTags?.containsKey("Primary") == true) {
                    "$jellyfinBaseUrl/Items/${dto.id}/Images/Primary?fillWidth=400"
                } else null

                val backdropUrl = if (!dto.backdropImageTags.isNullOrEmpty()) {
                    "$jellyfinBaseUrl/Items/${dto.id}/Images/Backdrop/0?fillWidth=800"
                } else null

                NextEpisodeInfo(
                    itemId = dto.id,
                    title = dto.name ?: appContext.getString(R.string.player_next_movie),
                    episodeCode = "$codeText$year",
                    thumbUrl = thumbUrl,
                    overview = dto.overview,
                    isMovie = true,
                    backdropUrl = backdropUrl
                )
            }

            _recommendedList.value = mappedInfoList
            val primary = mappedInfoList.firstOrNull()
            if (primary != null) {
                _nextEpisode.value = primary
                Log.i("PlayerViewModel", "NextMovie suggestions loaded: ${mappedInfoList.size} items (primary=${primary.title})")
            }
            primary
        }.getOrNull()
    }

    /**
     * Reintenta resolver el siguiente episodio/película al terminar la reproducción.
     */
    suspend fun refreshNextEpisode(itemId: String, seriesId: String?): NextEpisodeInfo? {
        if (jellyfinBaseUrl.isBlank() || jellyfinAuthHeader.isBlank()) return null
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val api = NetworkClientFactory.createJellyfinService(jellyfinBaseUrl, JellyfinApiService::class.java)
        return if (!seriesId.isNullOrBlank()) {
            fetchNextEpisode(
                api = api,
                authHeader = jellyfinAuthHeader,
                userId = prefs.jellyfinUserId,
                seriesId = seriesId,
                currentItemId = itemId,
                logLabel = "NextEpisode refreshed"
            )
        } else {
            fetchNextMovieSuggestion(
                api = api,
                authHeader = jellyfinAuthHeader,
                userId = prefs.jellyfinUserId,
                currentItemId = itemId
            )
        }
    }

    /** Seek the current player to [positionMs]. Used for Skip Intro / Skip Credits. */
    fun seekToMs(positionMs: Long, exoPlayerSeek: (Long) -> Unit) {
        exoPlayerSeek(positionMs)
        Log.i("PlayerViewModel", "Seeked to ${positionMs}ms via user action")
    }

    /**
     * Gestiona la detección y preparación de torrents bajo demanda a través del plugin Catalogo.
     * Devuelve true si el ítem ya está listo para reproducir (o si el plugin no aplica/falla en modo fail-open).
     * Devuelve false si la preparación fue cancelada o detenida por un error no recuperable.
     */
    private suspend fun handleCatalogoTorrentPreparation(
        api: JellyfinApiService,
        effectiveAuthHeader: String,
        playableId: String,
        title: String,
        backdropUrl: String?,
        releaseId: Long? = null
    ): Boolean {
        // 1. Detección del plugin: GET /Catalogo/Capabilities
        val capsRes = runCatching { api.getCatalogoCapabilities() }.getOrNull()
        if (capsRes == null || !capsRes.isSuccessful || capsRes.body()?.isSupported != true) {
            Log.i("PlayerViewModel", "Catalogo plugin not available or unsupported (code=${capsRes?.code()}). Proceeding normally with Jellyfin.")
            return true
        }

        // 2. Consultar estado: GET /Catalogo/Status/{itemId}?release=...
        var authHeader = effectiveAuthHeader
        var statusRes = runCatching { api.getCatalogoStatus(authHeader = authHeader, itemId = playableId, releaseId = releaseId) }.getOrNull()

        if (statusRes == null || statusRes.code() == 404) {
            Log.i("PlayerViewModel", "Catalogo status 404/network error. Proceeding normally.")
            return true
        }

        if (statusRes.code() == 401) {
            Log.w("PlayerViewModel", "Catalogo status 401. Re-authenticating with Jellyfin...")
            val reAuthSuccess = runCatching {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                if (prefs.jellyfinUsername.isNotBlank() && prefs.jellyfinPassword.isNotBlank()) {
                    val authResp = api.authenticateByName(
                        authHeader = buildJellyfinAuthHeader(token = ""),
                        request = JellyfinAuthRequest(username = prefs.jellyfinUsername, pw = prefs.jellyfinPassword)
                    )
                    authHeader = buildJellyfinAuthHeader(token = authResp.effectiveToken)
                    true
                } else false
            }.getOrDefault(false)

            if (reAuthSuccess) {
                statusRes = runCatching { api.getCatalogoStatus(authHeader = authHeader, itemId = playableId, releaseId = releaseId) }.getOrNull()
            }
            if (statusRes == null || statusRes.code() == 401) {
                _fatalPlaybackError.value = appContext.getString(R.string.player_error_http_401)
                return false
            }
        }

        if (statusRes.code() == 503) {
            _torrentOverlayState.value = TorrentOverlayUiState(
                itemId = playableId,
                title = title,
                backdropUrl = backdropUrl,
                state = "ERROR",
                isError = true,
                statusText = appContext.getString(R.string.torrent_server_unavailable),
                errorMessage = appContext.getString(R.string.torrent_server_unavailable)
            )
            return false
        }

        val initialBody = statusRes.body()
        if (initialBody == null || !initialBody.isCatalog) {
            Log.i("PlayerViewModel", "Item $playableId is not a catalog torrent (is_catalog=false). Proceeding normally.")
            return true
        }

        val initialStatus = initialBody.status
        if (initialStatus?.streamReady == true) {
            Log.i("PlayerViewModel", "Torrent stream_ready is already true for $playableId. Proceeding normally without overlay.")
            return true
        }

        val effectiveReleaseId = releaseId ?: initialBody.releaseId ?: initialStatus?.releaseId

        if (initialStatus?.state.equals("UNAVAILABLE", ignoreCase = true)) {
            Log.w("PlayerViewModel", "Torrent is UNAVAILABLE for $playableId.")
            updateTorrentOverlay(playableId, title, backdropUrl, initialStatus)
            return false
        }

        // 3. stream_ready == false: mostrar overlay e iniciar descarga sin vídeo
        Log.i("PlayerViewModel", "Torrent not ready for $playableId. Showing overlay and calling Prepare (releaseId=$effectiveReleaseId).")
        torrentZeroProgressStartMs = System.currentTimeMillis()
        updateTorrentOverlay(playableId, title, backdropUrl, initialStatus)

        // POST /Catalogo/Prepare/{itemId} una sola vez
        runCatching {
            val prepRes = api.prepareCatalogoItem(authHeader = authHeader, itemId = playableId, releaseId = effectiveReleaseId)
            if (prepRes.code() == 503) {
                _torrentOverlayState.value = _torrentOverlayState.value?.copy(
                    isError = true,
                    statusText = appContext.getString(R.string.torrent_server_unavailable),
                    errorMessage = appContext.getString(R.string.torrent_server_unavailable)
                )
                return false
            }
        }.onFailure { e ->
            Log.w("PlayerViewModel", "Catalogo prepare request failed: ${e.message}")
        }

        // 4. Polling cada 1.5 s
        while (coroutineContext.isActive) {
            delay(1500L)

            val pollRes = runCatching { api.getCatalogoStatus(authHeader = authHeader, itemId = playableId, releaseId = effectiveReleaseId) }.getOrNull()
            if (pollRes == null) {
                continue
            }

            if (pollRes.code() == 503) {
                _torrentOverlayState.value = _torrentOverlayState.value?.copy(
                    isError = true,
                    statusText = appContext.getString(R.string.torrent_server_unavailable),
                    errorMessage = appContext.getString(R.string.torrent_server_unavailable)
                )
                return false
            }

            if (pollRes.code() == 404) {
                Log.w("PlayerViewModel", "Catalogo status 404 during polling. Proceeding normally.")
                _torrentOverlayState.value = null
                return true
            }

            val pollBody = pollRes.body() ?: continue
            if (!pollBody.isCatalog) {
                _torrentOverlayState.value = null
                return true
            }

            val currStatus = pollBody.status ?: continue
            updateTorrentOverlay(playableId, title, backdropUrl, currStatus)

            if (currStatus.streamReady) {
                Log.i("PlayerViewModel", "Torrent reached stream_ready for $playableId! Starting playback.")
                _torrentOverlayState.value = null
                return true
            }

            if (currStatus.state.equals("ERROR", ignoreCase = true)) {
                _torrentOverlayState.value = _torrentOverlayState.value?.copy(
                    isError = true,
                    statusText = appContext.getString(R.string.torrent_error),
                    errorMessage = appContext.getString(R.string.torrent_error)
                )
                return false
            }

            if (currStatus.state.equals("UNAVAILABLE", ignoreCase = true)) {
                _torrentOverlayState.value = _torrentOverlayState.value?.copy(
                    isUnavailable = true,
                    statusText = appContext.getString(R.string.torrent_unavailable),
                    errorMessage = appContext.getString(R.string.torrent_unavailable)
                )
                return false
            }
        }

        return false
    }

    private fun updateTorrentOverlay(
        playableId: String,
        title: String,
        backdropUrl: String?,
        status: CatalogoTorrentStatusDto?
    ) {
        if (status == null) {
            _torrentOverlayState.value = TorrentOverlayUiState(
                itemId = playableId,
                title = title,
                backdropUrl = backdropUrl,
                state = "COLD",
                statusText = appContext.getString(R.string.torrent_preparing),
                reasonText = appContext.getString(R.string.torrent_buffering_desc)
            )
            return
        }

        val sizeBytes = status.sizeBytes ?: 0L
        // Buffer contiguo real desde el inicio del fichero (no total descargado)
        val bufferBytes = status.bufferBytes ?: status.downloadedBytes
            ?: if (sizeBytes > 0) (status.safeProgress * sizeBytes).toLong() else 0L
        val downloadedBytes = status.downloadedBytes ?: bufferBytes

        // Meta de búfer de arranque: del backend (buffer_required) si existe,
        // o cálculo local: tamaño / 120 clampeado a [32 MB, 128 MB]
        val targetBufferBytes = when {
            (status.bufferRequired ?: 0L) > 0L -> status.bufferRequired!!
            sizeBytes > 0L -> (sizeBytes / 120L).coerceIn(32L * 1024L * 1024L, 128L * 1024L * 1024L)
            else -> 50L * 1024L * 1024L
        }

        // Progreso del búfer de arranque (0f a 1f) basado en buffer contiguo
        val bufferProgress = when {
            status.streamReady -> 1.0f
            targetBufferBytes > 0L -> (bufferBytes.toFloat() / targetBufferBytes.toFloat()).coerceIn(0f, 0.98f)
            else -> status.safeProgress
        }

        val speedBps = status.safeSpeedBps
        val seeds = status.safeSeeds
        val peers = status.safePeers
        val now = System.currentTimeMillis()

        // ETA calculada específicamente para completar el búfer contiguo inicial
        val bufferEtaSeconds = if (!status.streamReady && speedBps > 0 && bufferBytes < targetBufferBytes) {
            ((targetBufferBytes - bufferBytes) / speedBps).coerceAtLeast(1L)
        } else {
            0L
        }

        if (bufferBytes <= 1024L && (status.state.equals("DOWNLOADING", ignoreCase = true) || status.state.equals("COLD", ignoreCase = true))) {
            if (torrentZeroProgressStartMs == null) {
                torrentZeroProgressStartMs = now
            }
        } else {
            torrentZeroProgressStartMs = null
        }

        val isZeroProgressFor10s = torrentZeroProgressStartMs != null && (now - torrentZeroProgressStartMs!!) >= 10_000L

        val statusText = when {
            status.streamReady -> appContext.getString(R.string.torrent_buffer_ready)
            isZeroProgressFor10s -> appContext.getString(R.string.torrent_connecting_peers)
            status.state.equals("COLD", ignoreCase = true) -> appContext.getString(R.string.torrent_preparing)
            status.state.equals("DOWNLOADING", ignoreCase = true) -> appContext.getString(R.string.torrent_buffering)
            status.state.equals("PAUSED", ignoreCase = true) -> appContext.getString(R.string.torrent_paused)
            status.state.equals("SEEDING", ignoreCase = true) || status.state.equals("AVAILABLE", ignoreCase = true) -> appContext.getString(R.string.torrent_ready)
            status.state.equals("ERROR", ignoreCase = true) -> appContext.getString(R.string.torrent_error)
            status.state.equals("UNAVAILABLE", ignoreCase = true) -> appContext.getString(R.string.torrent_unavailable)
            else -> appContext.getString(R.string.torrent_preparing)
        }

        val reasonText = when {
            isZeroProgressFor10s -> appContext.getString(R.string.torrent_connecting_peers_desc)
            else -> appContext.getString(R.string.torrent_buffering_desc)
        }

        val detailParts = mutableListOf<String>()
        if (!status.quality.isNullOrBlank()) detailParts.add(status.quality)
        if (!status.audio.isNullOrBlank()) detailParts.add(status.audio)
        if (seeds > 0 || peers > 0) {
            detailParts.add("Seeds: $seeds • Peers: $peers")
        }

        val isUnavailable = status.state.equals("UNAVAILABLE", ignoreCase = true)
        val isError = status.state.equals("ERROR", ignoreCase = true)

        _torrentOverlayState.value = TorrentOverlayUiState(
            itemId = playableId,
            title = title,
            backdropUrl = backdropUrl,
            state = status.state,
            progress = bufferProgress,
            downloadedBytes = bufferBytes,
            targetBufferBytes = targetBufferBytes,
            speedBps = speedBps,
            etaSeconds = bufferEtaSeconds,
            seeds = seeds,
            peers = peers,
            streamReady = status.streamReady,
            quality = status.quality,
            audio = status.audio,
            statusText = statusText,
            reasonText = reasonText,
            detailText = detailParts.joinToString(" · "),
            isZeroProgressFor10s = isZeroProgressFor10s,
            isError = isError,
            isUnavailable = isUnavailable,
            errorMessage = if (isError || isUnavailable) statusText else null
        )
    }
}

