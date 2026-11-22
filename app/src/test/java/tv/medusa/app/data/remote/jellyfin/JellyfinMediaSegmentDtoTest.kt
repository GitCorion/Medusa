package tv.medusa.app.data.remote.jellyfin

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regresión: Jellyfin serializa StartTicks/EndTicks, no StartPositionTicks. */
class JellyfinMediaSegmentDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesStartTicksFromTheIntroDbPluginPayload() {
        val payload = """
            {
              "Items": [
                { "Id": "a", "ItemId": "ep-1", "Type": "Intro", "StartTicks": 150000000, "EndTicks": 210000000 },
                { "Id": "b", "ItemId": "ep-1", "Type": "Recap", "StartTicks": 0, "EndTicks": 20000000 },
                { "Id": "c", "ItemId": "ep-1", "Type": "Outro", "StartTicks": 3000000000, "EndTicks": 3200000000 },
                { "Id": "d", "ItemId": "ep-1", "Type": "Preview", "StartTicks": 900000000, "EndTicks": 920000000 }
              ],
              "TotalRecordCount": 4
            }
        """.trimIndent()

        val parsed = json.decodeFromString<JellyfinMediaSegmentsResponse>(payload)

        assertEquals(4, parsed.items.size)
        assertEquals(150_000_000L, parsed.items[0].effectiveStartTicks)
        assertEquals(210_000_000L, parsed.items[0].effectiveEndTicks)
        assertEquals("Intro", parsed.items[0].type)
        assertEquals("Recap", parsed.items[1].type)
        assertEquals("Outro", parsed.items[2].type)
        assertEquals("Preview", parsed.items[3].type)
    }

    @Test
    fun keepsLegacyFieldNamesForOlderServers() {
        val payload = """
            { "Items": [ { "Type": "Intro", "StartPositionTicks": 10000000, "EndPositionTicks": 20000000 } ], "TotalRecordCount": 1 }
        """.trimIndent()

        val parsed = json.decodeFromString<JellyfinMediaSegmentsResponse>(payload)

        assertEquals(10_000_000L, parsed.items[0].effectiveStartTicks)
        assertEquals(20_000_000L, parsed.items[0].effectiveEndTicks)
    }
}
