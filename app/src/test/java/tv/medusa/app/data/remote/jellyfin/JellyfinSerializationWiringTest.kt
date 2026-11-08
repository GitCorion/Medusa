package tv.medusa.app.data.remote.jellyfin

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.medusa.app.data.remote.NetworkClientFactory

/**
 * Wiring real: JellyfinApiService se construye con `createJellyfinService` y el cuerpo que
 * sale por Retrofit debe llevar el DeviceProfile completo. MockWebServer captura el body
 * exacto de POST /Sessions/Capabilities/Full, PlaybackStart y PlaybackProgress.
 */
class JellyfinSerializationWiringTest {

    private lateinit var server: MockWebServer
    private lateinit var api: JellyfinApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = NetworkClientFactory.createJellyfinService(
            baseUrl = server.url("/").toString(),
            serviceClass = JellyfinApiService::class.java
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun capabilities() = JellyfinClientCapabilitiesDto(
        playableMediaTypes = listOf("Video", "Audio"),
        deviceProfile = JellyfinDeviceProfileBuilder.buildProfileFromCodecs(
            videoCodecs = setOf("h264", "hevc", "av1"),
            audioCodecs = setOf("aac", "mp3", "flac", "opus", "ac3", "eac3"),
            maxVideoWidth = 3840
        )
    )

    @Test
    fun capabilitiesPost_sendsCompleteDeviceProfile() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        api.postCapabilities(
            authHeader = "MediaBrowser Token=\"test\"",
            capabilities = capabilities()
        )

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/Sessions/Capabilities/Full", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

        assertEquals(
            listOf("Video", "Audio"),
            body["PlayableMediaTypes"]!!.jsonArray.map { it.jsonPrimitive.content }
        )

        val deviceProfile = body["DeviceProfile"]!!.jsonObject
        val directPlay = deviceProfile["DirectPlayProfiles"]!!.jsonArray[0].jsonObject
        assertEquals("Video", directPlay["Type"]!!.jsonPrimitive.content)
        assertTrue(directPlay["Container"]!!.jsonPrimitive.content.contains("strm"))

        val transcoding = deviceProfile["TranscodingProfiles"]!!.jsonArray[0].jsonObject
        assertEquals("ts", transcoding["Container"]!!.jsonPrimitive.content)
        assertEquals("Video", transcoding["Type"]!!.jsonPrimitive.content)
        assertEquals("h264", transcoding["VideoCodec"]!!.jsonPrimitive.content)
        assertEquals("aac", transcoding["AudioCodec"]!!.jsonPrimitive.content)
        assertEquals("hls", transcoding["Protocol"]!!.jsonPrimitive.content)
    }

    @Test
    fun playbackProgress_sendsSessionFields() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        api.reportPlaybackProgress(
            authHeader = "MediaBrowser Token=\"test\"",
            request = JellyfinPlaybackProgressRequest(
                itemId = "item-1",
                playSessionId = "session-1",
                mediaSourceId = "ms-1",
                audioStreamIndex = 2,
                positionTicks = 620_000_000L,
                isPaused = false,
                playMethod = "DirectPlay",
                eventName = "timeupdate",
                canSeek = true
            )
        )

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/Sessions/Playing/Progress", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

        assertEquals("session-1", body["PlaySessionId"]!!.jsonPrimitive.content)
        assertEquals("ms-1", body["MediaSourceId"]!!.jsonPrimitive.content)
        assertEquals("DirectPlay", body["PlayMethod"]!!.jsonPrimitive.content)
        assertEquals(620_000_000L, body["PositionTicks"]!!.jsonPrimitive.content.toLong())
        assertTrue(body["CanSeek"]!!.jsonPrimitive.boolean)
        assertFalse(body["IsPaused"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun playbackStart_sendsSessionFields() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        api.reportPlaybackStart(
            authHeader = "MediaBrowser Token=\"test\"",
            request = JellyfinPlaybackProgressRequest(
                itemId = "item-1",
                playSessionId = "session-1",
                mediaSourceId = "ms-1",
                audioStreamIndex = 2,
                positionTicks = 0L,
                isPaused = false,
                playMethod = "DirectStream",
                eventName = "timeupdate",
                canSeek = true
            )
        )

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/Sessions/Playing", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

        assertEquals("session-1", body["PlaySessionId"]!!.jsonPrimitive.content)
        assertEquals("DirectStream", body["PlayMethod"]!!.jsonPrimitive.content)
        assertTrue(body["CanSeek"]!!.jsonPrimitive.boolean)
        assertFalse(body["IsPaused"]!!.jsonPrimitive.boolean)
    }
}
