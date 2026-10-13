package com.example.tujelly.domain

import com.example.tujelly.domain.model.HomeSection
import com.example.tujelly.domain.model.MediaItem
import com.example.tujelly.domain.model.MediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillboardPoolBuilderTest {

    private fun createMediaItem(id: String, title: String, backdropUrl: String? = "https://example.com/bd/$id.jpg", rank: Int? = null): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            backdropUrl = backdropUrl,
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
        val moviesSection = HomeSection(
            title = "Grandes Producciones de tu colección",
            items = listOf(createMediaItem("movie_1", "Interstellar"))
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(listOf(resumeSection, moviesSection), seed = 0L)

        assertFalse(pool.any { it.id == "resume_1" })
        assertTrue(pool.any { it.id == "movie_1" })
    }

    @Test
    fun `excludes items without backdropUrl`() {
        val moviesSection = HomeSection(
            title = "Grandes Producciones de tu colección",
            items = listOf(
                createMediaItem("no_backdrop", "No Backdrop", backdropUrl = null),
                createMediaItem("has_backdrop", "Has Backdrop", backdropUrl = "https://image.jpg")
            )
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(listOf(moviesSection), seed = 0L)

        assertFalse(pool.any { it.id == "no_backdrop" })
        assertTrue(pool.any { it.id == "has_backdrop" })
    }

    @Test
    fun `combines candidates across multiple sections and rotates with seed`() {
        val top10 = HomeSection(
            title = "Top 10 en España hoy",
            items = listOf(createMediaItem("top_1", "Top Movie 1", rank = 1), createMediaItem("top_2", "Top Movie 2", rank = 2))
        )
        val blockbusters = HomeSection(
            title = "Grandes Producciones de tu colección",
            items = listOf(createMediaItem("bb_1", "Blockbuster 1"), createMediaItem("bb_2", "Blockbuster 2"))
        )
        val series = HomeSection(
            title = "Series Destacadas",
            items = listOf(createMediaItem("series_1", "Series 1"))
        )

        val poolDay0 = BillboardPoolBuilder.buildBillboardPool(listOf(top10, blockbusters, series), seed = 0L)
        val poolDay1 = BillboardPoolBuilder.buildBillboardPool(listOf(top10, blockbusters, series), seed = 1L)

        // Multiple categories represented
        assertTrue(poolDay0.any { it.id == "top_1" })
        assertTrue(poolDay0.any { it.id == "bb_1" })
        assertTrue(poolDay0.any { it.id == "series_1" })

        // Rotation ensures leading item is different on different days
        assertNotEquals(poolDay0.first().id, poolDay1.first().id)
    }
}
