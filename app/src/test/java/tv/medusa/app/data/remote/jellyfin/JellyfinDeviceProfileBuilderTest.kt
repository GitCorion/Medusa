package tv.medusa.app.data.remote.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_AUTO
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_DIRECT
import tv.medusa.app.data.local.AUDIO_PASSTHROUGH_OFF

class JellyfinDeviceProfileBuilderTest {

    // ─── Política de audio (OFF / AUTO / DIRECT) ────────────────────────────

    @Test
    fun offMode_declaresFfmpegSurroundCodecs() {
        val codecs = JellyfinDeviceProfileBuilder.resolveAudioCodecs(
            audioPassthroughMode = AUDIO_PASSTHROUGH_OFF,
            hardwareDecoders = emptySet(),
            passthroughCodecs = emptySet()
        )

        assertTrue("AC3 debe decodificarse con FFmpeg", codecs.contains("ac3"))
        assertTrue("EAC3 debe decodificarse con FFmpeg", codecs.contains("eac3"))
        assertTrue(codecs.contains("aac"))
        assertTrue(codecs.contains("mp3"))
        assertTrue(codecs.contains("flac"))
        assertTrue(codecs.contains("opus"))
    }

    @Test
    fun offMode_doesNotDeclareDtsOrTrueHd() {
        val codecs = JellyfinDeviceProfileBuilder.resolveAudioCodecs(
            audioPassthroughMode = AUDIO_PASSTHROUGH_OFF,
            hardwareDecoders = emptySet(),
            passthroughCodecs = setOf("ac3", "dts", "truehd")
        )

        // DTS/TrueHD se dejan al servidor para evitar stutter en equipos de gama baja,
        // aunque AudioCapabilities los anuncie.
        assertFalse(codecs.contains("dts"))
        assertFalse(codecs.contains("dca"))
        assertFalse(codecs.contains("truehd"))
    }

    @Test
    fun autoMode_addsPassthroughCodecs() {
        val codecs = JellyfinDeviceProfileBuilder.resolveAudioCodecs(
            audioPassthroughMode = AUDIO_PASSTHROUGH_AUTO,
            hardwareDecoders = setOf("vorbis"),
            passthroughCodecs = setOf("ac3", "dts", "truehd")
        )

        assertTrue(codecs.contains("ac3"))
        assertTrue(codecs.contains("dts"))
        assertTrue(codecs.contains("truehd"))
        assertTrue("Los decodificadores hardware detectados también cuentan", codecs.contains("vorbis"))
    }

    @Test
    fun directMode_usesOnlyPassthroughAndHardware() {
        val codecs = JellyfinDeviceProfileBuilder.resolveAudioCodecs(
            audioPassthroughMode = AUDIO_PASSTHROUGH_DIRECT,
            hardwareDecoders = setOf("ac3"),
            passthroughCodecs = setOf("eac3")
        )

        assertTrue(codecs.contains("ac3"))
        assertTrue(codecs.contains("eac3"))
        assertFalse("AC3/EAC3 no se añaden por defecto en DIRECT", codecs.contains("dts"))
        assertFalse(codecs.contains("truehd"))
    }

    // ─── Construcción de URLs ──────────────────────────────────────────────

    @Test
    fun combineBaseUrlAndPath_joinsRelativePath() {
        assertEquals(
            "http://192.168.1.10:8096/Videos/42/stream",
            JellyfinDeviceProfileBuilder.combineBaseUrlAndPath(
                "http://192.168.1.10:8096",
                "/Videos/42/stream"
            )
        )
    }

    @Test
    fun combineBaseUrlAndPath_keepsAbsoluteUrlFromConfiguredServer() {
        val absolute = "http://192.168.1.10:8096/Videos/42/stream?static=true"
        assertEquals(
            absolute,
            JellyfinDeviceProfileBuilder.combineBaseUrlAndPath("http://192.168.1.10:8096", absolute)
        )
    }

    @Test
    fun combineBaseUrlAndPath_rewritesForeignAbsoluteUrl() {
        val foreign = "https://cdn.example.com/video.m3u8?token=abc"
        val result = JellyfinDeviceProfileBuilder.combineBaseUrlAndPath(
            "http://192.168.1.10:8096",
            foreign
        )
        // Nunca devolvemos un host ajeno: el token de Jellyfin viajaría a ese host.
        assertEquals("http://192.168.1.10:8096/video.m3u8?token=abc", result)
    }

    @Test
    fun combineBaseUrlAndPath_doesNotDuplicatePathPrefix() {
        assertEquals(
            "http://host:8096/jellyfin/Videos/7/stream",
            JellyfinDeviceProfileBuilder.combineBaseUrlAndPath(
                "http://host:8096/jellyfin",
                "/jellyfin/Videos/7/stream"
            )
        )
    }

    @Test
    fun appendPlaybackParams_addsSessionTokenMediaSourceAndAudioIndex() {
        val result = JellyfinDeviceProfileBuilder.appendPlaybackParams(
            url = "http://host:8096/Videos/7/stream",
            playSessionId = "session123",
            apiKey = "secret",
            mediaSourceId = "ms1",
            audioStreamIndex = 3
        )

        assertTrue(result.contains("PlaySessionId=session123"))
        assertTrue(result.contains("api_key=secret"))
        assertTrue(result.contains("MediaSourceId=ms1"))
        assertTrue(result.contains("AudioStreamIndex=3"))
    }

    @Test
    fun appendPlaybackParams_doesNotDuplicateExistingParams() {
        val result = JellyfinDeviceProfileBuilder.appendPlaybackParams(
            url = "http://host:8096/Videos/7/stream?MediaSourceId=ms1&AudioStreamIndex=1",
            playSessionId = "session123",
            apiKey = "secret",
            mediaSourceId = "ms1",
            audioStreamIndex = 1
        )

        assertEquals(1, Regex("MediaSourceId=").findAll(result).count())
        assertEquals(1, Regex("AudioStreamIndex=").findAll(result).count())
    }

    // ─── Redacción de logs ─────────────────────────────────────────────────

    @Test
    fun sanitizeUrlForLog_redactsAllTokenParams() {
        val sanitized = JellyfinDeviceProfileBuilder.sanitizeUrlForLog(
            "http://host/stream?api_key=abc&X-Emby-Token=def&token=ghi&MediaSourceId=ms1"
        )

        assertFalse(sanitized.contains("abc"))
        assertFalse(sanitized.contains("def"))
        assertFalse(sanitized.contains("ghi"))
        assertTrue(sanitized.contains("MediaSourceId=ms1"))
    }
}
