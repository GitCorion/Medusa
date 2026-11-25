package tv.medusa.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.medusa.app.data.remote.jellyfin.JellyfinItemDto
import tv.medusa.app.data.remote.jellyfin.JellyfinUserDataDto

class JellyfinSyncFixTest {

    @Test
    fun `empty series with unplayedItemCount 0 and played true is not marked as watched`() {
        val dto = JellyfinItemDto(
            id = "series-empty",
            name = "Serie Vacía",
            type = "Series",
            recursiveItemCount = 0,
            userData = JellyfinUserDataDto(
                played = true,
                unplayedItemCount = 0
            )
        )

        val entity = dto.toEntity()
        assertFalse(entity.isPlayed)
        assertEquals(0, entity.totalItemCount)
    }

    @Test
    fun `series with episodes and 0 unplayed is marked as watched`() {
        val dto = JellyfinItemDto(
            id = "series-done",
            name = "Serie Completa",
            type = "Series",
            recursiveItemCount = 12,
            userData = JellyfinUserDataDto(
                played = true,
                unplayedItemCount = 0
            )
        )

        val entity = dto.toEntity()
        assertTrue(entity.isPlayed)
        assertEquals(12, entity.totalItemCount)
    }

    @Test
    fun `series with remaining unplayed episodes is not marked as watched`() {
        val dto = JellyfinItemDto(
            id = "series-ongoing",
            name = "Serie En Curso",
            type = "Series",
            recursiveItemCount = 12,
            userData = JellyfinUserDataDto(
                played = true,
                unplayedItemCount = 3
            )
        )

        val entity = dto.toEntity()
        assertFalse(entity.isPlayed)
    }

    @Test
    fun `movie without recursiveItemCount still respects played flag`() {
        val dto = JellyfinItemDto(
            id = "movie-1",
            name = "Película",
            type = "Movie",
            userData = JellyfinUserDataDto(played = true)
        )

        val entity = dto.toEntity()
        assertTrue(entity.isPlayed)
    }

    @Test
    fun `computeDeletedIds returns only local ids missing from server`() {
        val localIds = listOf("m1", "m2", "s1", "s2")
        val serverIds = setOf("m1", "s1", "m3")

        val deleted = computeDeletedIds(localIds, serverIds)
        assertEquals(listOf("m2", "s2"), deleted)
    }

    @Test
    fun `computeDeletedIds returns empty when all local ids exist on server`() {
        val localIds = listOf("m1", "s1")
        val serverIds = setOf("m1", "s1", "m2")

        assertTrue(computeDeletedIds(localIds, serverIds).isEmpty())
    }
}
