package tv.medusa.app.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.medusa.app.data.remote.jellyfin.JellyfinMediaSourceDto
import tv.medusa.app.data.remote.jellyfin.JellyfinMediaStreamDto
import tv.medusa.app.data.remote.jellyfin.JellyfinPlaybackSourceResolver

/**
 * Tests reales del resolvedor usado por PlayerViewModel. No duplican la lógica: llaman al
 * código de producción que construye los candidatos y las URLs de subtítulos.
 */
class PlayerResolutionTest {

    private val baseUrl = "http://192.168.0.100:8096"
    private val playableId = "12345"
    private val token = "mytoken"
    private val playSessionId = "session123"

    private fun source(
        id: String = "ms-abc",
        container: String? = "mkv",
        path: String? = null,
        protocol: String? = "File",
        directStreamUrl: String? = null,
        transcodingUrl: String? = null,
        supportsDirectPlay: Boolean = true
    ) = JellyfinMediaSourceDto(
        id = id,
        path = path,
        protocol = protocol,
        container = container,
        directStreamUrl = directStreamUrl,
        transcodingUrl = transcodingUrl,
        supportsDirectPlay = supportsDirectPlay,
        supportsDirectStream = true,
        supportsTranscoding = true
    )

    private fun candidates(
        source: JellyfinMediaSourceDto,
        alternatives: List<JellyfinMediaSourceDto> = emptyList(),
        urlBase: String = baseUrl
    ) = JellyfinPlaybackSourceResolver.buildCandidates(
        baseUrl = urlBase,
        playableId = playableId,
        token = token,
        playSessionId = playSessionId,
        audioStreamIndex = 2,
        mediaSource = source,
        alternativeMediaSources = alternatives
    )

    @Test
    fun strmWithoutServerUrls_usesStaticProxyFirst() {
        val result = candidates(
            source = source(
                container = "strm",
                path = "http://172.21.0.2:8765/play?ids=abc",
                protocol = "Http"
            )
        )

        assertTrue(
            "El primer candidato debe ser el proxy static de Jellyfin",
            result.first().startsWith("$baseUrl/Videos/$playableId/stream?static=true")
        )
        assertTrue(result.any { it.contains("PlaySessionId=$playSessionId") })
        assertTrue(result.any { it.contains("api_key=$token") })
        assertFalse(
            "Una IP interna de Docker no debe llegar nunca al TV",
            result.any { it.contains("172.21.0.2") }
        )
        assertFalse(
            "No se deben construir HLS a mano",
            result.any { it.contains("master.m3u8") || it.contains("main.m3u8") }
        )
        assertTrue(
            "Debe incluir candidato con extensión mkv para remux/transcode sin error 500",
            result.any { it.contains("/stream.mkv?") && it.contains("MediaSourceId=ms-abc") }
        )
    }

    @Test
    fun rcloneDirectPlayWithoutServerUrls_usesStaticStream() {
        val result = candidates(
            source = source(
                container = "mkv",
                path = "/mnt/rclone/pelicula.mkv"
            )
        )

        assertTrue(result.first().contains("stream?static=true", ignoreCase = true))
        assertTrue(result.first().contains("MediaSourceId=ms-abc"))
    }

    @Test
    fun normalMedia_prefersTranscodingUrlFromServer() {
        val result = candidates(
            source = source(
                container = "mkv",
                transcodingUrl = "/Videos/12345/master.m3u8?Transcode=true"
            )
        )

        assertTrue(result.first().contains("Transcode=true"))
        assertTrue(result.first().contains("master.m3u8"))
    }

    @Test
    fun normalMedia_neverUsesMediaSourcePathDirectly() {
        val result = candidates(
            source = source(
                container = "mkv",
                path = "https://cdn.example.com/pelicula.mkv"
            )
        )

        assertFalse(result.any { it.contains("cdn.example.com") })
    }

    @Test
    fun candidates_carrySessionTokenMediaSourceAndAudioIndex() {
        val result = candidates(source = source(container = "mkv"))

        result.forEach { url ->
            assertTrue(url, url.contains("PlaySessionId=$playSessionId"))
            assertTrue(url, url.contains("api_key=$token"))
            assertTrue(url, url.contains("MediaSourceId=ms-abc"))
            assertTrue(url, url.contains("AudioStreamIndex=2"))
        }
    }

    @Test
    fun candidates_areDeduplicated() {
        val result = candidates(
            source = source(
                container = "mkv",
                transcodingUrl = "/Videos/12345/master.m3u8"
            )
        )

        assertEquals(result.size, result.toSet().size)
    }

    @Test
    fun serverWithSubpath_keepsSubpathInCandidates() {
        val result = candidates(
            source = source(container = "strm", protocol = "Http"),
            urlBase = "https://example.com/jellyfin"
        )

        assertTrue(result.first().startsWith("https://example.com/jellyfin/Videos/$playableId/stream?static=true"))
        assertFalse(result.any { it.contains("/jellyfin/jellyfin/") })
    }

    @Test
    fun forcedTranscode_usesOnlyServerTranscodingUrl() {
        val result = JellyfinPlaybackSourceResolver.buildTranscodeOnlyCandidates(
            baseUrl = baseUrl,
            playableId = playableId,
            token = token,
            playSessionId = playSessionId,
            audioStreamIndex = 2,
            mediaSource = source(
                container = "mkv",
                directStreamUrl = "/Videos/12345/stream?static=true",
                transcodingUrl = "/Videos/12345/master.m3u8?Transcode=true"
            )
        )

        assertEquals(1, result.size)
        assertTrue(result.first().contains("Transcode=true"))
        assertFalse(result.any { it.contains("static=true", ignoreCase = true) })
    }

    @Test
    fun forcedTranscode_withoutTranscodingUrl_returnsEmpty() {
        val result = JellyfinPlaybackSourceResolver.buildTranscodeOnlyCandidates(
            baseUrl = baseUrl,
            playableId = playableId,
            token = token,
            playSessionId = playSessionId,
            audioStreamIndex = null,
            mediaSource = source(container = "mkv")
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun strmDetection_coversContainerPathAndProtocol() {
        assertTrue(JellyfinPlaybackSourceResolver.isStrm(source(container = "strm")))
        assertTrue(JellyfinPlaybackSourceResolver.isStrm(source(container = null, path = "/media/x.STRM")))
        assertTrue(JellyfinPlaybackSourceResolver.isStrm(source(container = null, protocol = "Http")))
        assertFalse(JellyfinPlaybackSourceResolver.isStrm(source(container = "mkv", path = "/media/x.mkv")))
    }

    @Test
    fun subtitleWithoutDeliveryUrl_usesOfficialStreamCodecEndpoint() {
        val url = JellyfinPlaybackSourceResolver.buildSubtitleUrl(
            baseUrl = baseUrl,
            playableId = playableId,
            mediaSourceId = "ms-abc",
            stream = JellyfinMediaStreamDto(
                type = "Subtitle",
                index = 3,
                codec = "ass",
                deliveryUrl = null
            ),
            token = token
        )

        assertEquals(
            "$baseUrl/Videos/$playableId/ms-abc/Subtitles/3/Stream.ass?api_key=$token",
            url
        )
    }

    @Test
    fun subtitleWithDeliveryUrl_usesIt() {
        val url = JellyfinPlaybackSourceResolver.buildSubtitleUrl(
            baseUrl = baseUrl,
            playableId = playableId,
            mediaSourceId = "ms-abc",
            stream = JellyfinMediaStreamDto(
                type = "Subtitle",
                index = 3,
                codec = "srt",
                deliveryUrl = "/Videos/$playableId/ms-abc/Subtitles/3/Stream.srt"
            ),
            token = token
        )

        assertTrue(url.startsWith("$baseUrl/Videos/$playableId/ms-abc/Subtitles/3/Stream.srt"))
        assertTrue(url.contains("api_key=$token"))
    }

    @Test
    fun deviceProfileDeclaresStrmAndSupportedContainersButNotAvi() {
        val containers = JellyfinPlaybackSourceResolver.DIRECT_PLAY_CONTAINERS
        assertTrue("strm debe estar declarado para Docker/rclone", containers.contains("strm"))
        assertTrue(containers.contains("mkv"))
        assertTrue(containers.contains("mp4"))
        assertTrue(containers.contains("ts"))
        assertFalse("Media3 no tiene extractor AVI: debe transcodificarlo el servidor", containers.contains("avi"))
    }

    @Test
    fun strmRequiringTranscode_prefersTranscodingUrlFirst() {
        val result = candidates(
            source = JellyfinMediaSourceDto(
                id = "ms-4k-dv",
                path = "http://127.0.0.1:8773/play?key=abc",
                protocol = "Http",
                container = "strm",
                transcodingUrl = "/Videos/12345/master.m3u8?Transcode=true",
                supportsDirectPlay = false,
                supportsDirectStream = false,
                supportsTranscoding = true
            )
        )

        assertTrue(
            "Cuando Jellyfin rechaza DirectPlay/DirectStream, debe priorizar master.m3u8",
            result.first().contains("master.m3u8")
        )
    }

    @Test
    fun selectPreferredMediaSource_non4kTablet_prefers1080pOver4kOrDolbyVision() {
        val ms4k = JellyfinMediaSourceDto(
            id = "ms-4k",
            name = "4K Cast",
            mediaStreams = listOf(
                JellyfinMediaStreamDto(type = "Video", codec = "hevc", width = 3840, height = 1600, videoRangeType = "DOVIWithHDR10Plus")
            )
        )
        val ms1080 = JellyfinMediaSourceDto(
            id = "ms-1080",
            name = "1080p Cast",
            mediaStreams = listOf(
                JellyfinMediaStreamDto(type = "Video", codec = "h264", width = 1920, height = 800, videoRangeType = "SDR")
            )
        )

        val tabletChoice = JellyfinPlaybackSourceResolver.selectPreferredMediaSource(
            mediaSources = listOf(ms4k, ms1080),
            preferredMediaSourceId = null,
            supports4K = false,
            supportsDolbyVision = false
        )
        assertEquals("ms-1080", tabletChoice?.id)

        val tvChoice = JellyfinPlaybackSourceResolver.selectPreferredMediaSource(
            mediaSources = listOf(ms4k, ms1080),
            preferredMediaSourceId = null,
            supports4K = true,
            supportsDolbyVision = true
        )
        assertEquals("ms-4k", tvChoice?.id)
    }

    @Test
    fun selectPreferredMediaSource_detects4kAndDolbyVisionFromMediaStreamsEvenWithoutNameTag() {
        val msGeneric4k = JellyfinMediaSourceDto(
            id = "ms-default",
            name = "Blade Runner 2099 S01E01",
            mediaStreams = listOf(
                JellyfinMediaStreamDto(type = "Video", codec = "hevc", width = 3840, height = 1600, videoRangeType = "DOVI")
            )
        )
        val ms1080 = JellyfinMediaSourceDto(
            id = "ms-1080",
            name = "1080p Cast"
        )

        val choice = JellyfinPlaybackSourceResolver.selectPreferredMediaSource(
            mediaSources = listOf(msGeneric4k, ms1080),
            preferredMediaSourceId = null,
            supports4K = false,
            supportsDolbyVision = false
        )
        assertEquals("ms-1080", choice?.id)
    }

    @Test
    fun selectFallbackMediaSource_skipsAttemptedAnd4kOnTablet() {
        val msDefault = JellyfinMediaSourceDto(id = "ms-default", name = "Blade Runner 2099 S01E01")
        val ms1080 = JellyfinMediaSourceDto(id = "ms-1080", name = "1080p Cast")
        val ms4k = JellyfinMediaSourceDto(id = "ms-4k", name = "4K Cast")

        val firstFallback = JellyfinPlaybackSourceResolver.selectFallbackMediaSource(
            availableVersions = listOf(msDefault, ms1080, ms4k),
            attemptedIds = setOf("ms-default"),
            supports4K = false,
            supportsDolbyVision = false
        )
        assertEquals("ms-1080", firstFallback?.id)

        val secondFallback = JellyfinPlaybackSourceResolver.selectFallbackMediaSource(
            availableVersions = listOf(msDefault, ms1080, ms4k),
            attemptedIds = setOf("ms-default", "ms-1080"),
            supports4K = false,
            supportsDolbyVision = false
        )
        assertEquals(null, secondFallback)
    }
}
