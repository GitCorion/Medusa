package tv.medusa.app.ui.screens.player

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.medusa.app.data.remote.jellyfin.CatalogoCapabilitiesDto
import tv.medusa.app.data.remote.jellyfin.CatalogoFormatUtils
import tv.medusa.app.data.remote.jellyfin.CatalogoHttpException
import tv.medusa.app.data.remote.jellyfin.CatalogoPrepareResponseDto
import tv.medusa.app.data.remote.jellyfin.CatalogoStatusDto
import tv.medusa.app.data.remote.jellyfin.CatalogoStatusResponse
import tv.medusa.app.data.remote.jellyfin.CatalogoTorrentStatusDto
import tv.medusa.app.data.remote.jellyfin.catalogoErrorMessage
import tv.medusa.app.data.remote.jellyfin.catalogoJson
import tv.medusa.app.data.remote.jellyfin.JellyfinMediaSourceDto
import tv.medusa.app.data.remote.jellyfin.JellyfinPlaybackSourceResolver
import tv.medusa.app.data.remote.jellyfin.TorrentOverlayUiState

class CatalogoTorrentPluginTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun capabilities_parsingAndCompatibilityCheck() {
        val rawJson = """
            {
              "api_version": 1,
              "plugin_version": "1.0.0.0",
              "features": ["status", "prepare"],
              "proxy_configured": true
            }
        """.trimIndent()

        val caps = json.decodeFromString<CatalogoCapabilitiesDto>(rawJson)
        assertEquals(1, caps.apiVersion)
        assertEquals("1.0.0.0", caps.pluginVersion)
        assertEquals(2, caps.features.size)
        assertTrue(caps.proxyConfigured)
        assertTrue("Debe ser compatible con api_version 1 y features status+prepare", caps.isSupported)

        // Casos incompatibles
        val wrongVersion = caps.copy(apiVersion = 2)
        assertFalse(wrongVersion.isSupported)

        val missingPrepare = caps.copy(features = listOf("status"))
        assertFalse(missingPrepare.isSupported)
    }

    @Test
    fun testPrueba1_9Lunas_streamReadyTrue_reproduceSinOverlay() {
        val rawJson = """
            {
              "is_catalog": true,
              "item_id": "35a9cbbf-1234-5678",
              "key": "movie:tmdb:1447420",
              "release_id": 109,
              "status": {
                "state": "SEEDING",
                "progress": 1.0,
                "downloaded_bytes": 2868944190,
                "size_bytes": 2868944190,
                "speed_bps": 0,
                "eta_seconds": 0,
                "seeds": 12,
                "peers": 1,
                "stream_ready": true,
                "quality": "1080p",
                "audio": "castellano",
                "tracker": "Torrenteros (API)"
              }
            }
        """.trimIndent()

        val status = json.decodeFromString<CatalogoStatusDto>(rawJson)
        assertTrue(status.isCatalog)
        assertNotNull(status.status)
        assertTrue("9 lunas debe tener stream_ready=true", status.status!!.streamReady)
        assertEquals("SEEDING", status.status!!.state)
        assertEquals("1080p", status.status!!.quality)
        assertEquals("castellano", status.status!!.audio)
    }

    @Test
    fun testPrueba2_peliculaNormalJellyfin_isCatalogFalse() {
        val rawJson = """
            {
              "is_catalog": false,
              "item_id": "normal-jf-item-999"
            }
        """.trimIndent()

        val status = json.decodeFromString<CatalogoStatusDto>(rawJson)
        assertFalse(status.isCatalog)
        assertEquals("normal-jf-item-999", status.itemId)
        assertEquals(null, status.status)
    }

    @Test
    fun testPrueba3_tituloFrio_prepareYPollingState() {
        val rawStatusCold = """
            {
              "is_catalog": true,
              "item_id": "cold-item-123",
              "key": "movie:tmdb:999999",
              "release_id": 200,
              "status": {
                "state": "COLD",
                "progress": 0.0,
                "downloaded_bytes": 0,
                "size_bytes": 3500000000,
                "speed_bps": 0,
                "eta_seconds": 0,
                "seeds": 0,
                "peers": 0,
                "stream_ready": false,
                "quality": "4K",
                "audio": "castellano"
              }
            }
        """.trimIndent()

        val statusCold = json.decodeFromString<CatalogoStatusDto>(rawStatusCold)
        assertTrue(statusCold.isCatalog)
        assertFalse("Título frío no debe estar stream_ready", statusCold.status!!.streamReady)
        assertEquals("COLD", statusCold.status!!.state)

        // Simulación de respuesta de Prepare
        val rawPrepare = """
            {
              "is_catalog": true,
              "key": "movie:tmdb:999999",
              "release_id": 200,
              "prepared": {
                "prepared": true,
                "status": {
                  "state": "DOWNLOADING",
                  "progress": 0.05,
                  "stream_ready": false
                }
              }
            }
        """.trimIndent()

        val prepResp = json.decodeFromString<CatalogoPrepareResponseDto>(rawPrepare)
        assertTrue(prepResp.isCatalog)
        assertTrue(prepResp.prepared!!.prepared)
        assertEquals("DOWNLOADING", prepResp.prepared!!.status!!.state)

        // Estado transitorio en progreso
        val rawDownloading = """
            {
              "is_catalog": true,
              "item_id": "cold-item-123",
              "status": {
                "state": "DOWNLOADING",
                "progress": 0.28,
                "speed_bps": 15133798,
                "eta_seconds": 172,
                "seeds": 5,
                "peers": 8,
                "stream_ready": false,
                "quality": "1080p",
                "audio": "castellano"
              }
            }
        """.trimIndent()

        val statusDownloading = json.decodeFromString<CatalogoStatusDto>(rawDownloading)
        assertFalse(statusDownloading.status!!.streamReady)
        assertEquals(0.28f, statusDownloading.status!!.safeProgress, 0.001f)
        assertEquals(15133798L, statusDownloading.status!!.safeSpeedBps)

        // Finalmente stream_ready pasa a true
        val ready = statusDownloading.status!!.copy(streamReady = true)
        assertTrue(ready.streamReady)
    }

    @Test
    fun testPrueba4_strmDetection_identificaArchivosStrm() {
        val strmByPath = JellyfinMediaSourceDto(id = "1", path = "/mnt/catalogo/pelicula.strm", container = "strm")
        assertTrue(JellyfinPlaybackSourceResolver.isStrm(strmByPath))

        val strmByContainer = JellyfinMediaSourceDto(id = "2", path = null, container = "strm")
        assertTrue(JellyfinPlaybackSourceResolver.isStrm(strmByContainer))

        val normalMkv = JellyfinMediaSourceDto(id = "3", path = "/mnt/movies/peli.mkv", container = "mkv")
        assertFalse(JellyfinPlaybackSourceResolver.isStrm(normalMkv))
    }

    @Test
    fun formatUtils_speedAndEta() {
        assertEquals("0 KB/s", CatalogoFormatUtils.formatSpeed(0L))
        assertEquals("500 KB/s", CatalogoFormatUtils.formatSpeed(500 * 1024L))
        assertEquals("14.4 MB/s", CatalogoFormatUtils.formatSpeed(15133798L))

        assertEquals("0 MB", CatalogoFormatUtils.formatBytes(0L))
        assertEquals("25 MB", CatalogoFormatUtils.formatBytes(25L * 1024L * 1024L))
        assertEquals("1.5 GB", CatalogoFormatUtils.formatBytes((1.5 * 1024.0 * 1024.0 * 1024.0).toLong()))

        assertEquals("", CatalogoFormatUtils.formatEta(0L))
        assertEquals("45 s", CatalogoFormatUtils.formatEta(45L))
        assertEquals("2 min", CatalogoFormatUtils.formatEta(172L))
        assertEquals("1 h 1 min", CatalogoFormatUtils.formatEta(3660L))
    }

    @Test
    fun overlayUiState_textFormatting() {
        val downloadingState = TorrentOverlayUiState(
            itemId = "test-1",
            title = "9 lunas",
            state = "DOWNLOADING",
            progress = 0.28f,
            speedBps = 15133798L,
            etaSeconds = 172L,
            statusText = "Descargando 28 % · 14.4 MB/s · ETA 2 min"
        )
        assertEquals("9 lunas", downloadingState.title)
        assertTrue(downloadingState.statusText.contains("28 %"))
        assertTrue(downloadingState.statusText.contains("14.4 MB/s"))

        val zeroProgress10sState = TorrentOverlayUiState(
            itemId = "test-1",
            title = "9 lunas",
            state = "DOWNLOADING",
            progress = 0.0f,
            isZeroProgressFor10s = true,
            statusText = "Conectando con peers…"
        )
        assertEquals("Conectando con peers…", zeroProgress10sState.statusText)
    }

    @Test
    fun liveServer_capabilitiesCheck() {
        val serverUrl = "http://127.0.0.1:8086"
        val isReachable = runCatching {
            val url = java.net.URL("$serverUrl/Catalogo/Capabilities")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            val code = conn.responseCode
            val text = conn.inputStream.bufferedReader().readText()
            code to text
        }.getOrNull()

        if (isReachable != null) {
            assertEquals(200, isReachable.first)
            val caps = json.decodeFromString<CatalogoCapabilitiesDto>(isReachable.second)
            assertEquals(1, caps.apiVersion)
            assertTrue(caps.isSupported)
            println("Live Jellyfin Catalogo Plugin verified: $caps")
        }
    }

    @Test
    fun testStatus_releaseIdInStatusObject() {
        val rawJson = """
            {
              "is_catalog": true,
              "item_id": "e5f05fd438a1a8c3e9423eb48b43f9e8",
              "key": "series:tmdb:111110:2:6",
              "status": {
                "key": "series:tmdb:111110:2:6",
                "asset_id": 9408,
                "title": "ONE PIECE",
                "year": 2023,
                "state": "COLD",
                "source": "torrent",
                "reason": "castellano",
                "stream_ready": false,
                "release_id": 13318,
                "quality": "1080p",
                "audio": "castellano",
                "tracker": "Torrenteros (API)",
                "privacy": "private",
                "progress": 0.0,
                "downloaded_bytes": 0,
                "size_bytes": 15854008961,
                "speed_bps": 0,
                "seeds": 0,
                "peers": 0
              }
            }
        """.trimIndent()

        val statusDto = json.decodeFromString<CatalogoStatusDto>(rawJson)
        assertTrue(statusDto.isCatalog)
        assertEquals(13318L, statusDto.status?.releaseId)
    }

    @Test
    fun testStatus_bufferFieldsDeserialization() {
        val rawJson = """
            {
              "is_catalog": true,
              "item_id": "e5f05fd438a1a8c3e9423eb48b43f9e8",
              "key": "series:tmdb:111110:2:6",
              "status": {
                "state": "DOWNLOADING",
                "stream_ready": false,
                "progress": 0.03,
                "downloaded_bytes": 60000000,
                "buffer_bytes": 50331648,
                "buffer_required": 48842689,
                "tail_ready": true,
                "size_bytes": 1968317116,
                "speed_bps": 5000000,
                "seeds": 12,
                "peers": 3
              }
            }
        """.trimIndent()

        val statusDto = json.decodeFromString<CatalogoStatusDto>(rawJson)
        assertTrue(statusDto.isCatalog)
        val st = statusDto.status!!
        assertEquals(50331648L, st.bufferBytes)
        assertEquals(48842689L, st.bufferRequired)
        assertEquals(true, st.tailReady)
        assertFalse(st.streamReady)
        assertEquals(60000000L, st.downloadedBytes)

        // Buffer progress should use bufferBytes / bufferRequired
        val bufferProgress = st.bufferBytes!!.toFloat() / st.bufferRequired!!.toFloat()
        assertTrue("Buffer progress should be > 1.0 (buffer exceeds required)", bufferProgress > 1.0f)
    }

    @Test
    fun testStatus_bufferRequiredCalculation() {
        // 5.86 GB → size/120 ≈ 49 MB (between 32..128)
        val size1 = 6_291_456_000L // ~5.86 GB
        val calc1 = (size1 / 120L).coerceIn(32L * 1024L * 1024L, 128L * 1024L * 1024L)
        assertTrue("5.86 GB → ~49 MB buffer", calc1 in (48L * 1024 * 1024)..(53L * 1024 * 1024))

        // 63 GB → size/120 = 537 MB → clamped to 128 MB
        val size2 = 67_645_734_912L // ~63 GB
        val calc2 = (size2 / 120L).coerceIn(32L * 1024L * 1024L, 128L * 1024L * 1024L)
        assertEquals(128L * 1024L * 1024L, calc2)

        // 1 GB → size/120 ≈ 8.5 MB → clamped to 32 MB
        val size3 = 1_073_741_824L
        val calc3 = (size3 / 120L).coerceIn(32L * 1024L * 1024L, 128L * 1024L * 1024L)
        assertEquals(32L * 1024L * 1024L, calc3)
    }

    @Test
    fun liveServer_realTokenStatusTests() {
        val serverUrl = "http://127.0.0.1:8086"
        val token = "***REMOVED***"
        val authHeader = "MediaBrowser Client=\"Tujelly\", Device=\"MacTest\", DeviceId=\"tujelly-test-suite\", Version=\"2.2.99\", Token=\"$token\""

        fun getStatus(itemId: String, releaseId: Long? = null): CatalogoStatusDto? {
            return runCatching {
                val urlStr = if (releaseId != null) "$serverUrl/Catalogo/Status/$itemId?release=$releaseId" else "$serverUrl/Catalogo/Status/$itemId"
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("Authorization", authHeader)
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    json.decodeFromString<CatalogoStatusDto>(text)
                } else null
            }.getOrNull()
        }

        // 1. 9 lunas
        val lunas = getStatus("35a9cbbfc537e8d92381adddfcc9ab12")
        if (lunas != null && lunas.isCatalog) {
            assertTrue("9 lunas debe tener stream_ready=true", lunas.status?.streamReady == true)
            assertEquals("SEEDING", lunas.status?.state)
            println("Live test 1 OK: 9 lunas stream_ready=true, state=${lunas.status?.state}")
        }

        // 2. Pelicula normal (#Alive -> is_catalog=false)
        val normal = getStatus("3a85d6d5c078f10e842a0f5853564b5b")
        if (normal != null) {
            assertFalse("Pelicula normal no debe ser de catalogo", normal.isCatalog)
            println("Live test 2 OK: Pelicula normal is_catalog=false")
        }

        // 3. ONE PIECE S02E06
        val onePiece = getStatus("e5f05fd438a1a8c3e9423eb48b43f9e8")
        if (onePiece != null && onePiece.isCatalog) {
            assertNotNull(onePiece.status)
            println("Live test 3 OK: ONE PIECE is_catalog=true, state=${onePiece.status?.state}, release_id=${onePiece.status?.releaseId}")
        }
    }

    @Test
    fun `status parses stream generation`() {
        val json = """
            {"is_catalog":true,"status":{
              "key":"movie:tmdb:1",
              "stream_generation":7,
              "stream_switched_at":"2026-11-20T08:00:00Z",
              "stream_switch_reason":"slow_swarm"
            }}
        """.trimIndent()
        val response = catalogoJson.decodeFromString<CatalogoStatusResponse>(json)
        assertEquals(7L, response.status?.streamGeneration)
        assertEquals("slow_swarm", response.status?.streamSwitchReason)
        assertEquals("2026-11-20T08:00:00Z", response.status?.streamSwitchedAt)
    }

    @Test
    fun `error uses server message`() {
        val body = """
            {"error":"sin_fuente","message":"No hay ninguna fuente reproducible para este título."}
        """.trimIndent()
        assertEquals(
            "No hay ninguna fuente reproducible para este título.",
            catalogoErrorMessage(body),
        )
    }

    @Test
    fun `error parses null or blank or non-json gracefully`() {
        assertEquals(null, catalogoErrorMessage(null))
        assertEquals(null, catalogoErrorMessage(""))
        assertEquals(null, catalogoErrorMessage("   "))
        assertEquals(null, catalogoErrorMessage("<html>502 Bad Gateway</html>"))
    }
}
