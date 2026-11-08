package tv.medusa.app.data.remote.jellyfin

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.medusa.app.data.remote.NetworkClientFactory

/**
 * Golden test del JSON real que genera JellyfinDeviceProfileBuilder. No se construye un DTO
 * a mano: se llama a la misma función pura que usa la app y se serializa con el mismo Json
 * que usa el Retrofit de Jellyfin. Esto es lo que evita que vuelvan los defaults de
 * kotlinx.serialization (`Type: "Audio"`, listas vacías, códecs vacíos…).
 */
class JellyfinDeviceProfileGoldenTest {

    private val profile = JellyfinDeviceProfileBuilder.buildProfileFromCodecs(
        videoCodecs = setOf("h264", "hevc", "av1"),
        audioCodecs = setOf("aac", "mp3", "flac", "opus", "ac3", "eac3"),
        maxVideoWidth = 3840
    )

    private val capabilities = JellyfinClientCapabilitiesDto(
        playableMediaTypes = listOf("Video", "Audio"),
        deviceProfile = profile
    )

    private fun rootJson() = NetworkClientFactory.jellyfinJson.parseToJsonElement(
        NetworkClientFactory.jellyfinJson.encodeToString(capabilities)
    ).jsonObject

    @Test
    fun playableMediaTypes_areEncodedAndNotEmpty() {
        val playable = rootJson()["PlayableMediaTypes"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("Video", "Audio"), playable)
    }

    @Test
    fun directPlayProfile_isVideoAndIncludesStrm() {
        val directPlay = rootJson()["DeviceProfile"]!!
            .jsonObject["DirectPlayProfiles"]!!.jsonArray[0].jsonObject

        assertEquals("Video", directPlay["Type"]!!.jsonPrimitive.content)
        assertTrue(directPlay["Container"]!!.jsonPrimitive.content.split(",").contains("strm"))
        assertTrue(directPlay["Container"]!!.jsonPrimitive.content.split(",").contains("mkv"))
        assertFalse(directPlay["Container"]!!.jsonPrimitive.content.split(",").contains("avi"))
        assertTrue(directPlay["VideoCodec"]!!.jsonPrimitive.content.contains("h264"))
        assertTrue(directPlay["VideoCodec"]!!.jsonPrimitive.content.contains("hevc"))
        assertTrue(directPlay["VideoCodec"]!!.jsonPrimitive.content.contains("av1"))
        assertTrue(directPlay["AudioCodec"]!!.jsonPrimitive.content.contains("ac3"))
        assertTrue(directPlay["AudioCodec"]!!.jsonPrimitive.content.contains("eac3"))
    }

    @Test
    fun transcodingProfile_isComplete() {
        val transcoding = rootJson()["DeviceProfile"]!!
            .jsonObject["TranscodingProfiles"]!!.jsonArray[0].jsonObject

        assertEquals("ts", transcoding["Container"]!!.jsonPrimitive.content)
        assertEquals("Video", transcoding["Type"]!!.jsonPrimitive.content)
        assertEquals("h264", transcoding["VideoCodec"]!!.jsonPrimitive.content)
        assertEquals("aac", transcoding["AudioCodec"]!!.jsonPrimitive.content)
        assertEquals("hls", transcoding["Protocol"]!!.jsonPrimitive.content)
    }

    @Test
    fun subtitleProfiles_haveMethod() {
        val subtitles = rootJson()["DeviceProfile"]!!
            .jsonObject["SubtitleProfiles"]!!.jsonArray

        assertTrue(subtitles.isNotEmpty())
        subtitles.forEach { item ->
            assertTrue(item.jsonObject["Method"]!!.jsonPrimitive.content.isNotBlank())
        }
    }

    @Test
    fun bitrates_arePresent() {
        val device = rootJson()["DeviceProfile"]!!.jsonObject
        assertEquals(120_000_000L, device["MaxStreamingBitrate"]!!.jsonPrimitive.long)
        assertEquals(120_000_000L, device["MaxStaticBitrate"]!!.jsonPrimitive.long)
        assertEquals(128_000L, device["MusicStreamingTranscodingBitrate"]!!.jsonPrimitive.long)
    }

    @Test
    fun codecProfiles_keepVideoType() {
        val codecProfiles = rootJson()["DeviceProfile"]!!
            .jsonObject["CodecProfiles"]!!.jsonArray

        assertTrue(codecProfiles.isNotEmpty())
        codecProfiles.forEach { item ->
            assertEquals("Video", item.jsonObject["Type"]!!.jsonPrimitive.content.take(5))
        }
    }
}
