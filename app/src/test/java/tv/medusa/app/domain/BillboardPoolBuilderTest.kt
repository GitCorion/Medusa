package tv.medusa.app.domain

import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillboardPoolBuilderTest {

    private fun createMediaItem(
        id: String,
        title: String,
        backdropUrl: String? = "https://example.com/bd/$id.jpg",
        isPlayed: Boolean = false,
        rank: Int? = null
    ): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            backdropUrl = backdropUrl,
            isPlayed = isPlayed,
            source = MediaSource.JELLYFIN,
            rank = rank
        )
    }

    @Test
    fun `excludes items from Continuar Viendo`() {
        val resumeSection = HomeSection(
            title = "Continuar Viendo",
            items = listOf(createMediaItem("resume_1", "Batman"))
        )
        val recentSection = HomeSection(
            title = "Añadido recientemente",
            items = listOf(createMediaItem("recent_1", "Dune"))
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(listOf(resumeSection, recentSection), seed = 0L)

        assertFalse(pool.any { it.id == "resume_1" })
        assertTrue(pool.any { it.id == "recent_1" })
    }

    @Test
    fun `excludes items without backdropUrl`() {
        val recentSection = HomeSection(
            title = "Añadido recientemente",
            items = listOf(
                createMediaItem("no_backdrop", "No Backdrop", backdropUrl = null),
                createMediaItem("has_backdrop", "Has Backdrop", backdropUrl = "https://image.jpg")
            )
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(listOf(recentSection), seed = 0L)

        assertFalse(pool.any { it.id == "no_backdrop" })
        assertTrue(pool.any { it.id == "has_backdrop" })
    }

    @Test
    fun `excludes played items when unplayed are available`() {
        val recentSection = HomeSection(
            title = "Añadido recientemente",
            items = listOf(
                createMediaItem("already_watched", "Watched", isPlayed = true),
                createMediaItem("unwatched_1", "Unwatched", isPlayed = false)
            )
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(listOf(recentSection), seed = 0L)

        assertFalse(pool.any { it.id == "already_watched" })
        assertTrue(pool.any { it.id == "unwatched_1" })
    }

    @Test
    fun `prioritizes trending and recents over static catalog sections`() {
        val top10 = HomeSection(
            title = "Top 10 en España hoy",
            items = listOf(createMediaItem("top_1", "Top 1", rank = 1))
        )
        val recents = HomeSection(
            title = "Añadido recientemente",
            items = listOf(createMediaItem("rec_1", "Rec 1"))
        )
        val platformNetflix = HomeSection(
            title = "Populares en Netflix",
            items = listOf(createMediaItem("netflix_1", "Stranger Things"))
        )
        val staticCatalog = HomeSection(
            title = "Grandes Producciones de tu colección",
            items = listOf(createMediaItem("old_godfather", "The Godfather 1972"))
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(
            listOf(top10, recents, platformNetflix, staticCatalog),
            seed = 0L
        )

        assertTrue(pool.any { it.id == "top_1" })
        assertTrue(pool.any { it.id == "rec_1" })
        assertTrue(pool.any { it.id == "netflix_1" })
        // Cero títulos del catálogo estático antiguo en el escaparate
        assertFalse(pool.any { it.id == "old_godfather" })
    }

    @Test
    fun `rotates dynamic candidates with daily seed`() {
        val top10 = HomeSection(
            title = "Top 10 en España hoy",
            items = listOf(createMediaItem("top_1", "Top 1", rank = 1), createMediaItem("top_2", "Top 2", rank = 2))
        )
        val recents = HomeSection(
            title = "Añadido recientemente",
            items = listOf(createMediaItem("rec_1", "Recent 1"), createMediaItem("rec_2", "Recent 2"))
        )

        val poolDay0 = BillboardPoolBuilder.buildBillboardPool(listOf(top10, recents), seed = 0L)
        val poolDay1 = BillboardPoolBuilder.buildBillboardPool(listOf(top10, recents), seed = 1L)

        assertNotEquals(poolDay0.first().id, poolDay1.first().id)
    }
}
