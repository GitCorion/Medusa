package tv.medusa.app.ui.screens.player

import android.media.MediaCodecList
import java.util.concurrent.TimeUnit
import tv.medusa.app.data.remote.jellyfin.JellyfinAuthRequest
import tv.medusa.app.data.remote.jellyfin.JellyfinCodecProfile
import tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfile
import tv.medusa.app.data.remote.jellyfin.JellyfinDirectPlayProfile
import tv.medusa.app.data.remote.jellyfin.JellyfinIntroTimestamps
import tv.medusa.app.data.remote.jellyfin.JellyfinMediaSourceDto
import tv.medusa.app.data.remote.jellyfin.JellyfinPlaybackInfoRequest
import tv.medusa.app.data.remote.jellyfin.JellyfinProfileCondition
import tv.medusa.app.data.remote.jellyfin.JellyfinSubtitleProfile
import tv.medusa.app.data.remote.jellyfin.JellyfinTranscodingProfile
import tv.medusa.app.util.DeviceUtils
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

data class SubtitleTrackInfo(
    val url: String,
    val language: String,
    val title: String,
    val codec: String,
    val isDefault: Boolean
)

data class AudioTrackInfo(
    val index: Int,
    val language: String,
    val title: String,
    val codec: String,
    val isDefault: Boolean
)

data class PlayerStreamInfo(
    val playableId: String,
    val primaryStreamUrl: String,
    val candidateUrls: List<String> = emptyList(),
    val subtitles: List<SubtitleTrackInfo> = emptyList(),
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val title: String = "",
    val startPositionMs: Long = 0L,
    val token: String = "",
    val authHeader: String = "",
    val availableVersions: List<JellyfinMediaSourceDto> = emptyList(),
    val currentVersionName: String = "",
    val seriesId: String? = null
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

    /** Item actual para publicar en la fila "Continuar viendo" (Watch Next) del launcher. */
    @Volatile
    private var nowPlaying: MediaItem? = null

    // Perfil de dispositivo y soporte 4K HEVC: enumerar MediaCodecList es costoso y el
    // resultado es estable durante toda la sesión. Se cachea y se calcula la primera vez
    // en Dispatchers.Default (antes se enumeraba dos veces en el hilo principal por carga).
    private val cachedDeviceProfile: JellyfinDeviceProfile
            by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { createAndroidTvDeviceProfile() }
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

    // ─── Next episode info ────────────────────────────────────────────────
    private val _nextEpisode = MutableStateFlow<NextEpisodeInfo?>(null)
    val nextEpisode: StateFlow<NextEpisodeInfo?> = _nextEpisode.asStateFlow()

    private suspend fun resolveStrmTargetUrl(
        baseUrl: String,
        token: String,
        authHeader: String,
        playableId: String,
        mediaSourceId: String
    ): String? {
        val downloadEndpoints = listOfNotNull(
            "$baseUrl/Items/$playableId/Download?api_key=$token",
            if (mediaSourceId.isNotBlank()) "$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=$mediaSourceId&api_key=$token" else null
        )

        val fastClient = NetworkClientFactory.okHttpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

        for (targetUrl in downloadEndpoints) {
            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("Authorization", authHeader)
                    .header("X-Emby-Authorization", authHeader)
                    .build()

                val lineWithUrl = withContext(Dispatchers.IO) {
                    fastClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful && response.header("Content-Type")?.contains("video", ignoreCase = true) != true) {
                            val content = runCatching { response.peekBody(4096).string().trim() }.getOrNull()
                            content?.lineSequence()
                                ?.map { it.trim() }
                                ?.firstOrNull { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
                        } else null
                    }
                }

                if (lineWithUrl != null) {
                    Log.i("PlayerViewModel", "Extracted target URL from .strm file: $lineWithUrl")
                    return lineWithUrl
                }
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Failed to resolve .strm from $targetUrl: ${e.message}")
            }
        }
        return null
    }

    private fun isDockerOrLocalhostHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        if (host.equals("localhost", ignoreCase = true) || host == "127.0.0.1") return true
        val parts = host.split(".")
        if (parts.size == 4) {
            val p0 = parts[0].toIntOrNull() ?: return false
            val p1 = parts[1].toIntOrNull() ?: return false
            // Docker default / bridge subnets: 172.16.0.0 - 172.31.255.255
            if (p0 == 172 && p1 in 16..31) return true
            // Also 10.0.0.0 - 10.255.255.255 if in a docker overlay
            if (p0 == 10 && p1 in 0..1) return true
        }
        return false
    }

    /**
     * Builds DeviceProfile dynamically from MediaCodecList (A.2 requirement from backend dev).
     * - On emulator / no HW HEVC: declares only h264 + aac → Jellyfin will transcode HEVC content
     * - On real TV with HEVC HW decoder: declares hevc + ac3/eac3 → Jellyfin allows DirectPlay
     * This ensures PlayMethod == DirectPlay when the device truly supports the codecs.
     */
    private fun createAndroidTvDeviceProfile(): JellyfinDeviceProfile {
        val isEmu = DeviceUtils.isEmulator()
        val hwVideoCodecs = mutableSetOf("h264") // h264 always supported
        val hwAudioCodecs = mutableSetOf("aac", "mp3") // baseline audio
        var maxVideoWidth = 1920 // default to 1080p

        if (!isEmu) {
            try {
                val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                for (info in codecList.codecInfos) {
                    if (info.isEncoder) continue
                    val name = info.name.lowercase()
                    val isSoftware = name.startsWith("omx.google.") ||
                            name.startsWith("c2.android.") ||
                            name.startsWith("c2.goldfish.")
                    if (isSoftware) continue

                    for (type in info.supportedTypes) {
                        when {
                            type.equals("video/hevc", ignoreCase = true) -> {
                                val caps = info.getCapabilitiesForType(type)
                                hwVideoCodecs.add("hevc")
                                if (caps.videoCapabilities?.isSizeSupported(3840, 2160) == true) {
                                    maxVideoWidth = maxOf(maxVideoWidth, 3840)
                                }
                            }
                            type.equals("video/vp9", ignoreCase = true) -> hwVideoCodecs.add("vp9")
                            type.equals("video/av01", ignoreCase = true) -> hwVideoCodecs.add("av1")
                        }
                    }
                }
                // Si el hardware soporta HEVC, soporta AC3/EAC3 passthrough en Android TV
                if ("hevc" in hwVideoCodecs) {
                    hwAudioCodecs.addAll(listOf("ac3", "eac3"))
                }
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Error probing MediaCodecList: ${e.message}")
            }
        }

        val videoCodecStr = hwVideoCodecs.joinToString(",")
        val audioCodecStr = (hwAudioCodecs + setOf("aac", "mp3", "ac3", "eac3", "dts", "dca", "truehd", "flac", "opus", "vorbis")).distinct().joinToString(",")

        Log.i("PlayerViewModel", "DeviceProfile: isEmulator=$isEmu videoCodecs=$videoCodecStr audioCodecs=$audioCodecStr maxWidth=$maxVideoWidth")

        // Build CodecProfiles with width constraints per codec (A.1 spec)
        val codecProfiles = mutableListOf<JellyfinCodecProfile>()
        codecProfiles.add(
            JellyfinCodecProfile(
                type = "Video",
                codec = "h264",
                conditions = listOf(
                    JellyfinProfileCondition(
                        condition = "LessThanEqual",
                        property = "Width",
                        value = "1920"
                    )
                )
            )
        )
        if ("hevc" in hwVideoCodecs) {
            codecProfiles.add(
                JellyfinCodecProfile(
                    type = "Video",
                    codec = "hevc",
                    conditions = listOf(
                        JellyfinProfileCondition(
                            condition = "LessThanEqual",
                            property = "Width",
                            value = maxVideoWidth.toString()
                        )
                    )
                )
            )
        }
        // VideoAudio codec profile (no conditions = allow all declared audio codecs)
        codecProfiles.add(
            JellyfinCodecProfile(
                type = "VideoAudio",
                codec = audioCodecStr
            )
        )

        return JellyfinDeviceProfile(
            name = "Medusa Android TV",
            maxStreamingBitrate = 120_000_000L,
            maxStaticBitrate = 120_000_000L,
            musicStreamingTranscodingBitrate = 128_000L,
            directPlayProfiles = listOf(
                JellyfinDirectPlayProfile(
                    container = "mkv,mp4,mov,webm,ts,avi,m4v,strm,flv,asf,wmv,3gp,ogv,m3u8,mpd",
                    type = "Video",
                    videoCodec = videoCodecStr,
                    audioCodec = audioCodecStr
                )
            ),
            transcodingProfiles = listOf(
                JellyfinTranscodingProfile(
                    container = "mkv",
                    type = "Video",
                    videoCodec = "h264",
                    audioCodec = "aac,mp3,ac3",
                    protocol = "hls"
                )
            ),
            subtitleProfiles = listOf(
                JellyfinSubtitleProfile(format = "srt", method = "External"),
                JellyfinSubtitleProfile(format = "subrip", method = "External"),
                JellyfinSubtitleProfile(format = "vtt", method = "External"),
                JellyfinSubtitleProfile(format = "webvtt", method = "External"),
                JellyfinSubtitleProfile(format = "ass", method = "External"),
                JellyfinSubtitleProfile(format = "ssa", method = "External"),
                JellyfinSubtitleProfile(format = "pgs", method = "External"),
                JellyfinSubtitleProfile(format = "sub", method = "External"),
                JellyfinSubtitleProfile(format = "dvdsub", method = "External"),
                JellyfinSubtitleProfile(format = "dvbsub", method = "External"),
                JellyfinSubtitleProfile(format = "srt", method = "Embed"),
                JellyfinSubtitleProfile(format = "subrip", method = "Embed"),
                JellyfinSubtitleProfile(format = "vtt", method = "Embed"),
                JellyfinSubtitleProfile(format = "webvtt", method = "Embed"),
                JellyfinSubtitleProfile(format = "ass", method = "Embed"),
                JellyfinSubtitleProfile(format = "ssa", method = "Embed"),
                JellyfinSubtitleProfile(format = "pgs", method = "Embed"),
                JellyfinSubtitleProfile(format = "sub", method = "Embed"),
                JellyfinSubtitleProfile(format = "dvdsub", method = "Embed"),
                JellyfinSubtitleProfile(format = "dvbsub", method = "Embed")
            ),
            codecProfiles = codecProfiles
        )
    }

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
            loadStreamUrl(currentPlayableId, preferredMediaSourceId = mediaSourceId)
        }
    }

    fun loadStreamUrl(itemId: String, preferredMediaSourceId: String? = null) {
        viewModelScope.launch {
            Log.i("PlayerViewModel", "loadStreamUrl START for itemId=$itemId preferredMediaSourceId=$preferredMediaSourceId")
            _streamInfo.value = null
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

            if (tv.medusa.app.data.demo.DemoCatalogManager.isDemoMediaId(itemId) || tv.medusa.app.data.demo.DemoCatalogManager.isDemoServer(baseUrl)) {
                val demoEntity = if (entity?.type?.equals("Series", ignoreCase = true) == true) {
                    val firstEp = database.jellyfinDao().getEpisodesForSeries(itemId).firstOrNull()
                    firstEp ?: entity
                } else {
                    entity
                }
                val demoPlayableId = demoEntity?.id ?: itemId
                val demoStreamUrl = tv.medusa.app.data.demo.DemoCatalogManager.getDemoStreamUrl(demoPlayableId)
                currentPlayableId = demoPlayableId
                nowPlaying = demoEntity?.toOptimizedMediaItem(baseUrl, token, MediaSource.JELLYFIN)
                val rawTicks = demoEntity?.playbackPositionTicks ?: 0L
                val startPositionMs = (rawTicks / 10_000L).coerceAtLeast(0L)
                _streamInfo.value = PlayerStreamInfo(
                    playableId = demoPlayableId,
                    primaryStreamUrl = demoStreamUrl,
                    candidateUrls = listOf(demoStreamUrl),
                    subtitles = emptyList(),
                    audioTracks = emptyList(),
                    title = demoEntity?.title ?: "Demo Stream",
                    startPositionMs = startPositionMs,
                    token = "",
                    authHeader = "",
                    availableVersions = emptyList(),
                    currentVersionName = "Direct Play (Demo Stream)",
                    seriesId = demoEntity?.seriesId
                )
                return@launch
            }

            var playableId = itemId
            val api = NetworkClientFactory.createService(baseUrl, JellyfinApiService::class.java)

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

            val androidTvProfile = withContext(Dispatchers.Default) { cachedDeviceProfile }

            val pbRequest = JellyfinPlaybackInfoRequest(
                userId = effectiveUserId,
                enableDirectPlay = true,
                enableDirectStream = true,
                enableTranscoding = true,
                allowVideoStreamCopy = true,
                allowAudioStreamCopy = true,
                enableSubtitleExtraction = false,
                maxStreamingBitrate = 120_000_000L,
                deviceProfile = androidTvProfile
            )

            var itemDetailResult = runCatching { api.getItemDetail(authHeader = effectiveAuthHeader, userId = effectiveUserId, itemId = playableId) }
            var playbackInfoResult = runCatching { api.getPlaybackInfo(authHeader = effectiveAuthHeader, itemId = playableId, userId = effectiveUserId, request = pbRequest) }

            val is401 = (itemDetailResult.exceptionOrNull() as? retrofit2.HttpException)?.code() == 401 ||
                    (playbackInfoResult.exceptionOrNull() as? retrofit2.HttpException)?.code() == 401 ||
                    (itemDetailResult.isFailure && playbackInfoResult.isFailure && prefs.jellyfinPassword.isNotBlank())

            if (is401 && prefs.jellyfinUsername.isNotBlank() && prefs.jellyfinPassword.isNotBlank()) {
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
                    itemDetailResult = runCatching { api.getItemDetail(authHeader = effectiveAuthHeader, userId = effectiveUserId, itemId = playableId) }
                    playbackInfoResult = runCatching { api.getPlaybackInfo(authHeader = effectiveAuthHeader, itemId = playableId, userId = effectiveUserId, request = updatedPbRequest) }
                    Log.i("PlayerViewModel", "Re-authentication succeeded! New token saved: ${effectiveToken.take(6)}...")
                } catch (e: Exception) {
                    Log.w("PlayerViewModel", "Re-authentication attempt failed: ${e.message}")
                }
            }

            var itemDto = itemDetailResult.getOrNull()
            var playbackInfo = playbackInfoResult.getOrNull()

            val mediaSources = playbackInfo?.mediaSources?.ifEmpty { null } ?: itemDto?.mediaSources

            // A.2 verification: log PlayMethod for each MediaSource (DirectPlay = success, Transcode = profile mismatch)
            mediaSources?.forEachIndexed { idx, ms ->
                Log.i("PlayerViewModel", "MS[$idx]: Name='${ms.name}' Container='${ms.container}' " +
                        "SupportsDirectPlay=${ms.supportsDirectPlay} SupportsDirectStream=${ms.supportsDirectStream} " +
                        "SupportsTranscoding=${ms.supportsTranscoding} " +
                        "DirectStreamUrl='${ms.directStreamUrl}' TranscodingUrl='${ms.transcodingUrl}'")
                // Log the definitive PlayMethod check
                val playMethod = when {
                    ms.supportsDirectPlay -> "DirectPlay"
                    ms.supportsDirectStream -> "DirectStream"
                    ms.supportsTranscoding -> "Transcode"
                    else -> "Unknown"
                }
                Log.i("PlayerViewModel", "MS[$idx] PlayMethod=$playMethod ← ${if (playMethod == "DirectPlay") "✓ SUCCESS" else "⚠ Profile mismatch — check DeviceProfile codecs"}")
            }

            val supports4K = withContext(Dispatchers.Default) { cachedDevice4kHevc }
            Log.i("PlayerViewModel", "Device 4K HEVC hardware decode support: $supports4K")

            val chosenMediaSource = if (!preferredMediaSourceId.isNullOrBlank()) {
                mediaSources?.firstOrNull { it.id == preferredMediaSourceId } ?: mediaSources?.firstOrNull()
            } else if (!supports4K && mediaSources != null && mediaSources.size > 1) {
                // If device cannot decode 4K HEVC (e.g. emulator, 1080p stick), pick 1080p H264 version first, then any 1080p
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
                mediaSources?.firstOrNull()
            }

            val candidateUrls = mutableListOf<String>()
            val subtitles = mutableListOf<SubtitleTrackInfo>()
            val audioTracks = mutableListOf<AudioTrackInfo>()

            var isStrmMedia = false

            chosenMediaSource?.let { mediaSource ->
                val mediaSourceId = mediaSource.id
                val path = mediaSource.path ?: ""
                val container = mediaSource.container?.lowercase() ?: ""
                val isStrm = container == "strm" ||
                        path.endsWith(".strm", ignoreCase = true) ||
                        mediaSource.protocol?.equals("Http", ignoreCase = true) == true
                isStrmMedia = isStrm

                Log.d("PlayerViewModel", "Using MediaSource id=$mediaSourceId name='${mediaSource.name}' container=$container isStrm=$isStrm path=$path")

                // Remote descriptor / stream resolution for .strm and rclone files:
                if (isStrm) {
                    // 1. Direct static stream from Jellyfin (static=true forces Jellyfin to stream raw bytes with HTTP Range 206)
                    if (mediaSourceId.isNotBlank()) {
                        val staticStream = "$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=$mediaSourceId&api_key=$effectiveToken"
                        if (!candidateUrls.contains(staticStream)) {
                            candidateUrls.add(staticStream)
                        }
                    }

                    // 2. DirectStreamUrl provided by Jellyfin API if available
                    val dsUrl = mediaSource.directStreamUrl
                    if (!dsUrl.isNullOrBlank()) {
                        val fullDsUrl = if (dsUrl.startsWith("http", ignoreCase = true)) dsUrl else "$baseUrl$dsUrl"
                        val authDsUrl = if (!fullDsUrl.contains("api_key=")) "$fullDsUrl&api_key=$effectiveToken" else fullDsUrl
                        if (!candidateUrls.contains(authDsUrl)) {
                            candidateUrls.add(authDsUrl)
                        }
                    }

                    // 3. Alternative versions as direct static candidates (seamless fallback si la versión primaria falla)
                    mediaSources?.filter { it.id != mediaSourceId }?.forEach { altSource ->
                        if (altSource.id.isNotBlank()) {
                            val altStatic = "$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=${altSource.id}&api_key=$effectiveToken"
                            if (!candidateUrls.contains(altStatic)) {
                                candidateUrls.add(altStatic)
                            }
                        }
                    }

                    // 4. Direct path from MediaSourceInfo.Path ONLY if LAN/routable IP (not docker bridge)
                    if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
                        val pathUri = runCatching { Uri.parse(path) }.getOrNull()
                        val pathHost = pathUri?.host
                        if (!isDockerOrLocalhostHost(pathHost) && !pathHost.isNullOrBlank()) {
                            if (!candidateUrls.contains(path)) candidateUrls.add(path)
                        }
                    }

                    // 5. Extracted direct target URL from the .strm descriptor as fallback
                    val extractedStrmUrl = resolveStrmTargetUrl(
                        baseUrl = baseUrl,
                        token = effectiveToken,
                        authHeader = effectiveAuthHeader,
                        playableId = playableId,
                        mediaSourceId = mediaSourceId
                    )
                    if (!extractedStrmUrl.isNullOrBlank()) {
                        val strmUri = runCatching { Uri.parse(extractedStrmUrl) }.getOrNull()
                        val strmHost = strmUri?.host
                        val serverHost = runCatching { Uri.parse(baseUrl).host }.getOrNull()
                        if (!serverHost.isNullOrBlank() && isDockerOrLocalhostHost(strmHost)) {
                            val transformed = extractedStrmUrl.replaceFirst(strmHost!!, serverHost)
                            if (!candidateUrls.contains(transformed)) candidateUrls.add(transformed)
                        } else if (!isDockerOrLocalhostHost(strmHost) && !strmHost.isNullOrBlank()) {
                            if (!candidateUrls.contains(extractedStrmUrl)) candidateUrls.add(extractedStrmUrl)
                        }
                    }

                    // 6. Transcoding / HLS fallbacks from Jellyfin (ONLY as last resort if direct play and static fails)
                    if (mediaSourceId.isNotBlank()) {
                        val transcode1080 = "$baseUrl/Videos/$playableId/master.m3u8?MediaSourceId=$mediaSourceId&VideoCodec=h264&AudioCodec=aac,mp3,ac3&MaxHeight=1080&MaxWidth=1920&api_key=$effectiveToken"
                        if (!candidateUrls.contains(transcode1080)) {
                            candidateUrls.add(transcode1080)
                        }
                        val hlsWithMs = "$baseUrl/Videos/$playableId/master.m3u8?MediaSourceId=$mediaSourceId&api_key=$effectiveToken"
                        if (!candidateUrls.contains(hlsWithMs)) {
                            candidateUrls.add(hlsWithMs)
                        }
                        val mainHlsWithMs = "$baseUrl/Videos/$playableId/main.m3u8?MediaSourceId=$mediaSourceId&api_key=$effectiveToken"
                        if (!candidateUrls.contains(mainHlsWithMs)) {
                            candidateUrls.add(mainHlsWithMs)
                        }
                    }
                } else {
                    // For regular non-strm media files (MKV, MP4 on rclone or local disk):
                    // 1. Direct container stream with static=true
                    if (container.isNotBlank()) {
                        val containerUrl = "$baseUrl/Videos/$playableId/stream.$container?Static=true&MediaSourceId=$mediaSourceId&api_key=$effectiveToken"
                        if (!candidateUrls.contains(containerUrl)) {
                            candidateUrls.add(containerUrl)
                        }
                    }

                    // 2. Direct static file stream with MediaSourceId
                    if (mediaSourceId.isNotBlank()) {
                        val staticWithMs = "$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=$mediaSourceId&api_key=$effectiveToken"
                        if (!candidateUrls.contains(staticWithMs)) {
                            candidateUrls.add(staticWithMs)
                        }
                    }

                    // 3. Dynamic stream through Jellyfin (without static=true)
                    if (mediaSourceId.isNotBlank()) {
                        val dynamicWithMs = "$baseUrl/Videos/$playableId/stream?MediaSourceId=$mediaSourceId&api_key=$effectiveToken"
                        if (!candidateUrls.contains(dynamicWithMs)) {
                            candidateUrls.add(dynamicWithMs)
                        }

                        // 4. Master & Main HLS Playlists with MediaSourceId
                        val transcode1080 = "$baseUrl/Videos/$playableId/master.m3u8?MediaSourceId=$mediaSourceId&VideoCodec=h264&AudioCodec=aac,mp3,ac3&MaxHeight=1080&MaxWidth=1920&api_key=$effectiveToken"
                        if (!candidateUrls.contains(transcode1080)) {
                            candidateUrls.add(transcode1080)
                        }
                        val hlsWithMs = "$baseUrl/Videos/$playableId/master.m3u8?MediaSourceId=$mediaSourceId&api_key=$effectiveToken"
                        if (!candidateUrls.contains(hlsWithMs)) {
                            candidateUrls.add(hlsWithMs)
                        }
                        val mainHlsWithMs = "$baseUrl/Videos/$playableId/main.m3u8?MediaSourceId=$mediaSourceId&api_key=$effectiveToken"
                        if (!candidateUrls.contains(mainHlsWithMs)) {
                            candidateUrls.add(mainHlsWithMs)
                        }
                    }
                }

                // Parse Subtitles & Audio streams
                for (stream in mediaSource.mediaStreams) {
                    if (stream.type.equals("Subtitle", ignoreCase = true)) {
                        val subUrl = if (!stream.deliveryUrl.isNullOrBlank()) {
                            if (stream.deliveryUrl.startsWith("http", ignoreCase = true)) stream.deliveryUrl else "$baseUrl${stream.deliveryUrl}"
                        } else {
                            "$baseUrl/Videos/$playableId/${mediaSource.id}/Subtitles/${stream.index}/Stream.srt?api_key=$effectiveToken"
                        }
                        subtitles.add(
                            SubtitleTrackInfo(
                                url = subUrl,
                                language = stream.language ?: "es",
                                title = stream.displayTitle ?: stream.language ?: appContext.getString(R.string.player_subtitle_default),
                                codec = stream.codec ?: "srt",
                                isDefault = stream.isDefault || stream.isForced
                            )
                        )
                    } else if (stream.type.equals("Audio", ignoreCase = true)) {
                        audioTracks.add(
                            AudioTrackInfo(
                                index = stream.index,
                                language = stream.language ?: "und",
                                title = stream.displayTitle ?: stream.language ?: appContext.getString(R.string.player_audio_track_default, stream.index),
                                codec = stream.codec ?: "aac",
                                isDefault = stream.isDefault
                            )
                        )
                    }
                }
            }

            // Fallback URLs if candidateUrls is still empty
            if (!isStrmMedia) {
                val defaultStatic = "$baseUrl/Videos/$playableId/stream?static=true&api_key=$effectiveToken"
                if (!candidateUrls.contains(defaultStatic)) {
                    candidateUrls.add(defaultStatic)
                }
            }

            val defaultStandard = "$baseUrl/Videos/$playableId/stream?api_key=$effectiveToken"
            if (!candidateUrls.contains(defaultStandard)) {
                candidateUrls.add(defaultStandard)
            }

            val defaultHls = "$baseUrl/Videos/$playableId/master.m3u8?api_key=$effectiveToken"
            if (!candidateUrls.contains(defaultHls)) {
                candidateUrls.add(defaultHls)
            }

            val lanUrl = prefs.jellyfinLanServerUrl.trimEnd('/')
            val finalCandidates = if (lanUrl.isNotBlank() && lanUrl != baseUrl) {
                val list = mutableListOf<String>()
                candidateUrls.forEach { url ->
                    if (url.startsWith(baseUrl)) {
                        val lanCandidate = url.replaceFirst(baseUrl, lanUrl)
                        if (!list.contains(lanCandidate)) list.add(lanCandidate)
                    }
                    if (!list.contains(url)) list.add(url)
                }
                list
            } else {
                candidateUrls
            }
            val primaryStreamUrl = finalCandidates.firstOrNull() ?: "$baseUrl/Videos/$playableId/stream?api_key=$effectiveToken"

            val rawTicks = itemDto?.userData?.playbackPositionTicks ?: entity?.playbackPositionTicks ?: 0L
            val startPositionMs = (rawTicks / 10_000L).coerceAtLeast(0L)

            Log.i("PlayerViewModel", "Resolved ${finalCandidates.size} candidate URLs for $playableId. Primary: $primaryStreamUrl")

            if (primaryStreamUrl.isNotBlank()) {
                prewarmStream(primaryStreamUrl, effectiveToken, effectiveAuthHeader)
            }

            val seriesId = itemDto?.seriesId ?: entity?.seriesId

            _streamInfo.value = PlayerStreamInfo(
                playableId = playableId,
                primaryStreamUrl = primaryStreamUrl,
                candidateUrls = finalCandidates,
                subtitles = subtitles,
                audioTracks = audioTracks,
                title = itemDto?.name ?: entity?.title ?: "",
                startPositionMs = startPositionMs,
                token = effectiveToken,
                authHeader = effectiveAuthHeader,
                availableVersions = mediaSources ?: emptyList(),
                currentVersionName = chosenMediaSource?.name ?: "",
                seriesId = seriesId
            )
        }
    }

    private fun prewarmStream(url: String, token: String, authHeader: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("PlayerViewModel", "Pre-warming stream: $url")
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-0")
                    .apply {
                        if (token.isNotBlank()) {
                            header("X-Emby-Token", token)
                            header("X-MediaBrowser-Token", token)
                        }
                        if (authHeader.isNotBlank()) {
                            header("Authorization", authHeader)
                            header("X-Emby-Authorization", authHeader)
                        } else if (token.isNotBlank()) {
                            val genAuth = buildJellyfinAuthHeader(token = token)
                            header("Authorization", genAuth)
                            header("X-Emby-Authorization", genAuth)
                        }
                    }
                    .build()
                NetworkClientFactory.okHttpClient.newBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                    .newCall(request)
                    .execute()
                    .use { response ->
                        Log.i("PlayerViewModel", "Pre-warm response: code=${response.code}")
                    }
            } catch (e: Exception) {
                Log.d("PlayerViewModel", "Pre-warm completed/timed out (non-blocking): ${e.message}")
            }
        }
    }

    fun reportStart(positionMs: Long) {
        if (currentPlayableId.isBlank() || jellyfinBaseUrl.isBlank()) return
        // Fuera del hilo principal: createService/la red no deben bloquear la UI.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val api = NetworkClientFactory.createService(jellyfinBaseUrl, JellyfinApiService::class.java)
                val ticks = positionMs * 10_000L
                api.reportPlaybackStart(
                    authHeader = jellyfinAuthHeader,
                    request = JellyfinPlaybackProgressRequest(itemId = currentPlayableId, positionTicks = ticks, isPaused = false)
                )
            }
        }
    }

    fun reportProgress(positionMs: Long, isPaused: Boolean, durationMs: Long = 0L) {
        if (currentPlayableId.isBlank() || jellyfinBaseUrl.isBlank()) return
        // Fuera del hilo principal: createService/la red no deben bloquear la UI.
        viewModelScope.launch(Dispatchers.IO) {
            // Publicar en la fila "Continuar viendo" del launcher (TvContract).
            nowPlaying?.let { item ->
                if (positionMs > 0L && durationMs > 0L) {
                    runCatching { TvChannelManager.publishToWatchNext(appContext, item, positionMs, durationMs) }
                }
            }
            runCatching {
                val api = NetworkClientFactory.createService(jellyfinBaseUrl, JellyfinApiService::class.java)
                val ticks = positionMs * 10_000L
                api.reportPlaybackProgress(
                    authHeader = jellyfinAuthHeader,
                    request = JellyfinPlaybackProgressRequest(
                        itemId = currentPlayableId,
                        positionTicks = ticks,
                        isPaused = isPaused,
                        eventName = "timeupdate"
                    )
                )
            }
        }
    }

    fun reportStopped(positionMs: Long, durationMs: Long = 0L) {
        val itemId = currentPlayableId
        val baseUrl = jellyfinBaseUrl
        val authHeader = jellyfinAuthHeader
        if (itemId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
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

            // Si es un episodio completado y se dispone del siguiente capítulo,
            // se marca localmente en Room con la fecha actual para que aparezca de
            // inmediato al principio de "Continuar viendo" al volver a Inicio.
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

            // 3. Notificar al servidor Jellyfin
            if (baseUrl.isNotBlank() && authHeader.isNotBlank()) {
                runCatching {
                    val api = NetworkClientFactory.createService(baseUrl, JellyfinApiService::class.java)
                    val ticks = positionMs * 10_000L
                    api.reportPlaybackStopped(
                        authHeader = authHeader,
                        request = JellyfinPlaybackProgressRequest(itemId = itemId, positionTicks = ticks)
                    )
                    if (isCompleted) {
                        val prefs = userPreferencesRepository.userPreferencesFlow.first()
                        if (prefs.jellyfinUserId.isNotBlank()) {
                            api.markPlayed(authHeader = authHeader, userId = prefs.jellyfinUserId, itemId = itemId)
                        }
                    }
                }
            }
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
            val api = NetworkClientFactory.createService(jellyfinBaseUrl, JellyfinApiService::class.java)
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val userId = prefs.jellyfinUserId

            // Reset previous state
            _introMarker.value = null
            _creditsMarker.value = null
            _nextEpisode.value = null

            // 1️⃣ Try native MediaSegments (Jellyfin 10.10+) — covers Intro, Outro, Recap, etc.
            var nativeSegmentsLoaded = false
            runCatching {
                val segmentsResponse = api.getMediaSegments(
                    authHeader = jellyfinAuthHeader,
                    itemId = itemId
                )
                if (segmentsResponse.items.isNotEmpty()) {
                    nativeSegmentsLoaded = true
                    for (seg in segmentsResponse.items) {
                        // MediaSegments usa ticks; los convertimos a ms directamente
                        val startMs = seg.startPositionTicks / 10_000L
                        val endMs   = seg.endPositionTicks   / 10_000L
                        when (seg.type.lowercase()) {
                            "intro" -> {
                                // Para intro nativo: showAt=start, hideAt=end, skipTo=end
                                _introMarker.value = SkipMarker(
                                    showAtMs = startMs,
                                    hideAtMs = endMs,
                                    skipToMs = endMs
                                )
                                Log.i("PlayerViewModel", "MediaSegments Intro: ${startMs}ms → ${endMs}ms")
                            }
                            "outro", "credits" -> {
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

            // 2️⃣ Fallback: Intro Skipper plugin — usa exactamente los campos que el plugin devuelve
            if (!nativeSegmentsLoaded) {
                var introLoaded = false

                fun applyIntroSkipperResult(ts: JellyfinIntroTimestamps) {
                    if (!ts.valid || ts.introEnd <= ts.introStart) return
                    // El plugin especifica exactamente cuándo mostrar y ocultar el botón
                    _introMarker.value = SkipMarker(
                        showAtMs = (ts.showSkipPromptAt ?: ts.introStart).times(1000).toLong(),
                        hideAtMs = (ts.hideSkipPromptAt ?: ts.introEnd).times(1000).toLong(),
                        skipToMs = ts.introEnd.times(1000).toLong()
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

            // 3️⃣ Next episode via NextUp (only for series episodes)
            if (!seriesId.isNullOrBlank()) {
                runCatching {
                    val nextUp = api.getNextUpItems(
                        authHeader = jellyfinAuthHeader,
                        userId = userId,
                        seriesId = seriesId
                    )
                    val nextDto = nextUp.items.firstOrNull()
                    if (nextDto != null && nextDto.id != itemId) {
                        val season = nextDto.parentIndexNumber ?: 0
                        val episode = nextDto.indexNumber ?: 0
                        val thumbUrl = if (nextDto.imageTags?.containsKey("Primary") == true) {
                            "$jellyfinBaseUrl/Items/${nextDto.id}/Images/Primary?fillWidth=300"
                        } else if (!nextDto.seriesId.isNullOrBlank() && !nextDto.seriesPrimaryImageTag.isNullOrBlank()) {
                            "$jellyfinBaseUrl/Items/${nextDto.seriesId}/Images/Primary?fillWidth=300"
                        } else null

                        _nextEpisode.value = NextEpisodeInfo(
                            itemId = nextDto.id,
                            title = nextDto.name ?: appContext.getString(R.string.player_next_episode),
                            episodeCode = "T$season E$episode",
                            thumbUrl = thumbUrl
                        )
                        Log.i("PlayerViewModel", "NextEpisode loaded: ${nextDto.id} ${nextDto.name}")
                    }
                }.onFailure { e ->
                    Log.d("PlayerViewModel", "NextUp fetch failed: ${e.message}")
                }
            }
        }
    }

    /** Seek the current player to [positionMs]. Used for Skip Intro / Skip Credits. */
    fun seekToMs(positionMs: Long, exoPlayerSeek: (Long) -> Unit) {
        exoPlayerSeek(positionMs)
        Log.i("PlayerViewModel", "Seeked to ${positionMs}ms via user action")
    }
}

