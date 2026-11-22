@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package tv.medusa.app.data.remote.jellyfin

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.exoplayer.audio.AudioCapabilities
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_AUTO
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_DIRECT
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_OFF
import tv.medusa.app.util.DeviceUtils
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object JellyfinDeviceProfileBuilder {

    private const val TAG = "JellyfinDeviceProfile"

    @Volatile
    private var cachedProfile: JellyfinDeviceProfile? = null
    @Volatile
    private var cachedPassthroughMode: Int? = null

    /** Códecs que Media3/FFmpeg decodifican siempre a PCM, sin depender del hardware. */
    private val SOFTWARE_AUDIO_CODECS = setOf("aac", "mp3", "flac", "opus")

    /**
     * AC3/EAC3 se declaran siempre porque el build incluye el decodificador FFmpeg de
     * Jellyfin (media3-ffmpeg-decoder). DTS/TrueHD quedan fuera del software por completo.
     */
    private val FFMPEG_SURROUND_AUDIO_CODECS = setOf("ac3", "eac3")

    /** Códecs que solo se declaran si hay passthrough real confirmado por AudioCapabilities. */
    private val PASSTHROUGH_ONLY_AUDIO_CODECS = setOf("dts", "dca", "truehd", "mlp")

    /**
     * Construye un JellyfinDeviceProfile real adaptado a las capacidades físicas del televisor
     * y al modo de passthrough de audio seleccionado por el usuario.
     */
    @Synchronized
    fun createDeviceProfile(context: Context, audioPassthroughMode: Int = AUDIO_PASSTHROUGH_OFF): JellyfinDeviceProfile {
        if (cachedProfile != null && cachedPassthroughMode == audioPassthroughMode) {
            return cachedProfile!!
        }

        val isEmu = DeviceUtils.isEmulator()
        val hwVideoCodecs = mutableSetOf("h264")
        val hwAudioCodecs = mutableSetOf<String>()
        var maxVideoWidth = 1920

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
                            else -> audioCodecForMime(type)?.let { hwAudioCodecs.add(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error probing MediaCodecList: ${e.message}")
            }
        }

        val passthroughCodecs = try {
            audioPassthroughCodecs(AudioCapabilities.getCapabilities(context.applicationContext))
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting audio capabilities: ${e.message}")
            emptySet()
        }

        val audioCodecs = resolveAudioCodecs(audioPassthroughMode, hwAudioCodecs, passthroughCodecs)
        val videoCodecStr = hwVideoCodecs.joinToString(",")
        val audioCodecStr = audioCodecs.joinToString(",")

        Log.i(
            TAG,
            "Built DeviceProfile: videoCodecs=[$videoCodecStr], audioCodecs=[$audioCodecStr], " +
                "hwAudio=[${hwAudioCodecs.joinToString(",")}], passthrough=[${passthroughCodecs.joinToString(",")}], " +
                "maxWidth=$maxVideoWidth, passthroughMode=$audioPassthroughMode"
        )

        val profile = buildProfileFromCodecs(
            videoCodecs = hwVideoCodecs,
            audioCodecs = audioCodecs,
            maxVideoWidth = maxVideoWidth
        )

        cachedProfile = profile
        cachedPassthroughMode = audioPassthroughMode
        return profile
    }

    /**
     * Construcción pura del DeviceProfile, separada del sondeo de hardware para poder
     * verificarla en tests JVM con un golden JSON exacto.
     */
    internal fun buildProfileFromCodecs(
        videoCodecs: Set<String>,
        audioCodecs: Set<String>,
        maxVideoWidth: Int
    ): JellyfinDeviceProfile {
        val videoCodecStr = videoCodecs.joinToString(",")
        val audioCodecStr = audioCodecs.joinToString(",")

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
        if ("hevc" in videoCodecs) {
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
                    // Lista "todo terreno": incluye strm (Docker/rclone) y todos los contenedores
                    // que Media3 puede abrir. AVI/ASF/WMV quedan fuera para que Jellyfin los
                    // transcodifique en lugar de provocar un DirectPlay que ExoPlayer no soporta.
                    container = JellyfinPlaybackSourceResolver.DIRECT_PLAY_CONTAINERS.joinToString(","),
                    type = "Video",
                    videoCodec = videoCodecStr,
                    audioCodec = audioCodecStr
                )
            ),
            transcodingProfiles = listOf(
                JellyfinTranscodingProfile(
                    container = "ts",
                    type = "Video",
                    // HLS con HEVC en TS no está soportado por todos los clientes; h264 es la
                    // salida universal cuando el dispositivo no puede con DirectPlay.
                    videoCodec = "h264",
                    audioCodec = "aac",
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

    /**
     * Política de audio del perfil, separada de Android para poder testearla en JVM.
     *
     * - OFF: PCM local. AC3/EAC3 se decodifican con FFmpeg; DTS/TrueHD se dejan al servidor.
     * - AUTO: lo anterior + lo que el sink/HDMI confirme como passthrough.
     * - DIRECT: solo passthrough confirmado (más decodificadores hardware detectados).
     */
    internal fun resolveAudioCodecs(
        audioPassthroughMode: Int,
        hardwareDecoders: Set<String>,
        passthroughCodecs: Set<String>
    ): Set<String> {
        // Baseline fijo de la spec: aac, mp3, flac, opus, ac3, eac3.
        val result = (SOFTWARE_AUDIO_CODECS + FFMPEG_SURROUND_AUDIO_CODECS).toMutableSet()

        // Los decodificadores hardware solo aportan códecs decodificables localmente.
        // DTS/TrueHD nunca se declaran por hardware: o hay passthrough real o se transcodea.
        result += hardwareDecoders - PASSTHROUGH_ONLY_AUDIO_CODECS

        when (audioPassthroughMode) {
            AUDIO_PASSTHROUGH_OFF -> Unit
            AUDIO_PASSTHROUGH_AUTO, AUDIO_PASSTHROUGH_DIRECT -> result += passthroughCodecs
        }
        return result
    }

    private fun audioPassthroughCodecs(caps: AudioCapabilities): Set<String> = buildSet {
        if (caps.supportsEncoding(AudioFormat.ENCODING_AC3)) add("ac3")
        if (caps.supportsEncoding(AudioFormat.ENCODING_E_AC3)) add("eac3")
        if (caps.supportsEncoding(AudioFormat.ENCODING_DTS) ||
            caps.supportsEncoding(AudioFormat.ENCODING_DTS_HD)
        ) {
            add("dts")
            add("dca")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            caps.supportsEncoding(AudioFormat.ENCODING_DOLBY_TRUEHD)
        ) {
            add("truehd")
            add("mlp")
        }
    }

    private fun audioCodecForMime(type: String): String? {
        val mime = type.lowercase().trim()
        return when {
            mime == "audio/mp4a-latm" -> "aac"
            mime == "audio/mpeg" -> "mp3"
            mime == "audio/flac" -> "flac"
            mime == "audio/opus" -> "opus"
            mime == "audio/vorbis" -> "vorbis"
            mime == "audio/ac3" -> "ac3"
            mime == "audio/eac3" -> "eac3"
            // DTS/TrueHD se excluyen expresamente: nunca se decodifican en software ni se
            // declaran por tener un decoder hardware (la spec exige passthrough real).
            else -> null
        }
    }

    /**
     * Verifica si un códec de audio es compatible de forma directa con el perfil activo.
     */
    fun isAudioCodecSupported(codec: String?, profile: JellyfinDeviceProfile): Boolean {
        if (codec.isNullOrBlank()) return true
        val c = codec.lowercase().trim()
        val directAudioCodecs = profile.directPlayProfiles
            .firstOrNull { it.type.equals("Video", ignoreCase = true) }
            ?.audioCodec?.lowercase()?.split(",")?.map { it.trim() } ?: emptyList()

        val normalized = when {
            c.contains("dts") || c.contains("dca") -> "dts"
            c.contains("truehd") -> "truehd"
            c.contains("eac3") || c.contains("ec-3") -> "eac3"
            c.contains("ac3") || c.contains("ac-3") -> "ac3"
            c.contains("aac") -> "aac"
            c.contains("mp3") -> "mp3"
            c.contains("flac") -> "flac"
            c.contains("opus") -> "opus"
            c.contains("vorbis") -> "vorbis"
            else -> c
        }
        return directAudioCodecs.contains(normalized) || directAudioCodecs.contains(c)
    }

    /**
     * Crea el DTO de capacidades de sesión para registrar con POST /Sessions/Capabilities/Full.
     * SupportsMediaControl se fija a false y SupportedCommands a vacío porque Medusa es un
     * reproductor local para TV y no un receptor de casting remoto; declararlo a true haría que
     * Jellyfin mostrara controles de control remoto en la web/móvil que la TV no respondería.
     */
    fun createDefaultCapabilities(
        context: Context,
        audioPassthroughMode: Int = AUDIO_PASSTHROUGH_OFF
    ): JellyfinClientCapabilitiesDto {
        return JellyfinClientCapabilitiesDto(
            playableMediaTypes = listOf("Video", "Audio"),
            supportedCommands = emptyList(),
            supportsMediaControl = false,
            supportsPersistentIdentifier = true,
            deviceProfile = createDeviceProfile(context, audioPassthroughMode)
        )
    }

    /**
     * Combina una URL base (que puede tener subrutas como /jellyfin) con una ruta relativa
     * devuelta por Jellyfin, evitando duplicar prefijos de subruta.
     */
    fun combineBaseUrlAndPath(baseUrl: String, pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://", ignoreCase = true) || pathOrUrl.startsWith("https://", ignoreCase = true)) {
            val base = baseUrl.toHttpUrlOrNull()
            val absolute = pathOrUrl.toHttpUrlOrNull()
            if (base != null && absolute != null &&
                absolute.host.equals(base.host, ignoreCase = true) &&
                absolute.port == base.port &&
                absolute.scheme.equals(base.scheme, ignoreCase = true)
            ) {
                return pathOrUrl
            }
            // El servidor entregó una URL absoluta a otro host: NUNCA le enviamos el token.
            // Conservamos solo la ruta y la resolvemos contra el servidor configurado.
            val safePath = absolute?.encodedPath.orEmpty().ifBlank { "/" } +
                (absolute?.query?.let { "?$it" }.orEmpty())
            return combineBaseUrlAndPath(baseUrl, safePath)
        }
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"

        val baseHttp = cleanBase.toHttpUrlOrNull()
        val basePath = baseHttp?.encodedPath?.trimEnd('/') ?: ""

        return if (basePath.isNotBlank() && cleanPath.startsWith("$basePath/")) {
            val root = "${baseHttp?.scheme}://${baseHttp?.host}:${baseHttp?.port}"
            "$root$cleanPath"
        } else {
            "$cleanBase$cleanPath"
        }
    }

    /**
     * Asegura que los parámetros PlaySessionId, api_key y MediaSourceId estén presentes
     * en la URL de stream de forma robusta, codificando correctamente los valores y
     * sin duplicar parámetros ya existentes (comprobación case-insensitive de query).
     */
    fun appendPlaybackParams(
        url: String,
        playSessionId: String? = null,
        apiKey: String? = null,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null
    ): String {
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl != null) {
            val builder = httpUrl.newBuilder()

            // 1. PlaySessionId (comprobación case-insensitive)
            val existingSessionKey = httpUrl.queryParameterNames.firstOrNull { it.equals("PlaySessionId", ignoreCase = true) }
            if (existingSessionKey == null && !playSessionId.isNullOrBlank()) {
                builder.addQueryParameter("PlaySessionId", playSessionId)
            } else if (existingSessionKey != null && !playSessionId.isNullOrBlank() && httpUrl.queryParameter(existingSessionKey).isNullOrBlank()) {
                builder.setQueryParameter(existingSessionKey, playSessionId)
            }

            // 2. api_key (comprobación case-insensitive)
            val existingApiKey = httpUrl.queryParameterNames.firstOrNull { it.equals("api_key", ignoreCase = true) || it.equals("ApiKey", ignoreCase = true) }
            if (existingApiKey == null && !apiKey.isNullOrBlank()) {
                builder.addQueryParameter("api_key", apiKey)
            }

            // 3. MediaSourceId (comprobación case-insensitive)
            if (!mediaSourceId.isNullOrBlank()) {
                val existingMsKey = httpUrl.queryParameterNames.firstOrNull { it.equals("MediaSourceId", ignoreCase = true) }
                if (existingMsKey == null) {
                    builder.addQueryParameter("MediaSourceId", mediaSourceId)
                }
            }

            // 4. AudioStreamIndex: necesario para DirectStream/Transcode cuando el servidor
            //    no lo ha incrustado ya en la URL de reproducción.
            if (audioStreamIndex != null) {
                val existingAudioKey = httpUrl.queryParameterNames.firstOrNull { it.equals("AudioStreamIndex", ignoreCase = true) }
                if (existingAudioKey == null) {
                    builder.addQueryParameter("AudioStreamIndex", audioStreamIndex.toString())
                }
            }

            return builder.build().toString()
        }

        // Fallback para URIs relativas o no-HTTP estándar
        val uri = Uri.parse(url)
        val queryNames = uri.queryParameterNames
        val builder = uri.buildUpon()

        if (!queryNames.any { it.equals("PlaySessionId", ignoreCase = true) } && !playSessionId.isNullOrBlank()) {
            builder.appendQueryParameter("PlaySessionId", playSessionId)
        }
        if (!queryNames.any { it.equals("api_key", ignoreCase = true) || it.equals("ApiKey", ignoreCase = true) } && !apiKey.isNullOrBlank()) {
            builder.appendQueryParameter("api_key", apiKey)
        }
        if (!mediaSourceId.isNullOrBlank() && !queryNames.any { it.equals("MediaSourceId", ignoreCase = true) }) {
            builder.appendQueryParameter("MediaSourceId", mediaSourceId)
        }
        if (audioStreamIndex != null && !queryNames.any { it.equals("AudioStreamIndex", ignoreCase = true) }) {
            builder.appendQueryParameter("AudioStreamIndex", audioStreamIndex.toString())
        }
        return builder.build().toString()
    }

    /**
     * Sanitiza URLs para evitar fugar api_key o tokens en Logcat.
     */
    fun sanitizeUrlForLog(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url.replace(
            Regex("(?i)(api_key|apikey|token|x-emby-token|x-mediabrowser-token|secret|code|sig|trakt-api-key)=([^&\\s]+)"),
            "$1=REDACTED"
        )
    }
}

