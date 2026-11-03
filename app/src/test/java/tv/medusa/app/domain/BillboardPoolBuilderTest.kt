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
        posterUrl: String? = "https://example.com/po/$id.jpg",
        isPlayed: Boolean = false,
        rank: Int? = null,
        year: Int = java.time.Year.now().value
    ): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            backdropUrl = backdropUrl,
            posterUrl = posterUrl,
            isPlayed = isPlayed,
            source = MediaSource.JELLYFIN,
            rank = rank,
            year = year
        )
    }

    @Test
    fun `excludes Continuar Viendo when there are trending candidates`() {
        val resumeSection = HomeSection(
            title = "Continuar Viendo",
            items = listOf(createMediaItem("resume_1", "Batman"))
        )
        val trending = HomeSection(
            title = "Top 10 hoy",
            items = listOf(createMediaItem("top_1", "Dune", rank = 1))
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(
            listOf(resumeSection, trending),
            seed = 0L
        )

        assertFalse(pool.any { it.id == "resume_1" })
        assertTrue(pool.any { it.id == "top_1" })
    }

    @Test
    fun `ignores Añadido recientemente even if it has valid backdrops`() {
        val recentlyAdded = HomeSection(
            title = "Añadido recientemente",
            items = listOf(createMediaItem("old_but_recently_added", "Serie antigua"))
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(listOf(recentlyAdded), seed = 0L)

        assertFalse(
            "'Añadido recientemente' no debe alimentar la cabecera",
            pool.any { it.id == "old_but_recently_added" }
        )
    }

    @Test
    fun `excludes items without any image`() {
        val newReleases = HomeSection(
            title = "Últimos estrenos",
            items = listOf(
                createMediaItem("no_image", "No Image", backdropUrl = null, posterUrl = null),
                createMediaItem("poster_only", "Poster Only", backdropUrl = null),
                createMediaItem("has_backdrop", "Has Backdrop")
            )
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(listOf(newReleases), seed = 0L)

        assertFalse(pool.any { it.id == "no_image" })
        assertTrue("Un póster también sirve para el hero", pool.any { it.id == "poster_only" })
        assertTrue(pool.any { it.id == "has_backdrop" })
    }

    @Test
    fun `excludes played items when unplayed are available`() {
        val newReleases = HomeSection(
            title = "Últimos estrenos",
            items = listOf(
                createMediaItem("already_watched", "Watched", isPlayed = true),
                createMediaItem("unwatched_1", "Unwatched", isPlayed = false)
            )
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(listOf(newReleases), seed = 0L)

        assertFalse(pool.any { it.id == "already_watched" })
        assertTrue(pool.any { it.id == "unwatched_1" })
    }

    @Test
    fun `uses trending and new releases and ignores static and platform catalog`() {
        val top10 = HomeSection(
            title = "Top 10 hoy",
            items = listOf(createMediaItem("top_1", "Top 1", rank = 1))
        )
        val recent = HomeSection(
            title = "Añadido recientemente",
            items = listOf(createMediaItem("rec_1", "Rec 1"))
        )
        val newReleases = HomeSection(
            title = "Últimos estrenos",
            items = listOf(createMediaItem("new_1", "New 1"))
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
            listOf(top10, recent, newReleases, platformNetflix, staticCatalog),
            seed = 0L
        )

        assertTrue(pool.any { it.id == "top_1" })
        assertTrue(pool.any { it.id == "new_1" })
        assertFalse("La cabecera es tendencia/novedad, no catálogo de plataforma", pool.any { it.id == "netflix_1" })
        assertFalse(pool.any { it.id == "old_godfather" })
        assertFalse(pool.any { it.id == "rec_1" })
    }

    @Test
    fun `uses new releases as fallback before static catalog`() {
        val newReleases = HomeSection(
            title = "Últimos estrenos",
            items = listOf(
                createMediaItem("new_1", "New 1"),
                createMediaItem("new_2", "New 2")
            )
        )
        val staticCatalog = HomeSection(
            title = "Series Destacadas",
            items = listOf(createMediaItem("old_series", "Old Series"))
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(
            listOf(newReleases, staticCatalog),
            seed = 0L
        )

        assertTrue(pool.any { it.id == "new_1" })
        assertTrue(pool.any { it.id == "new_2" })
        assertFalse(pool.any { it.id == "old_series" })
    }

    @Test
    fun `uses Continuar Viendo only as last resort`() {
        val resumeSection = HomeSection(
            title = "Continuar Viendo",
            items = listOf(createMediaItem("resume_1", "Batman"))
        )

        val pool = BillboardPoolBuilder.buildBillboardPool(listOf(resumeSection), seed = 0L)

        assertEquals(listOf("resume_1"), pool.map { it.id })
    }

    @Test
    fun `rotates dynamic candidates with daily seed`() {
        val top10 = HomeSection(
            title = "Top 10 hoy",
            items = listOf(
                createMediaItem("top_1", "Top 1", rank = 1),
                createMediaItem("top_2", "Top 2", rank = 2)
            )
        )
        val newReleases = HomeSection(
            title = "Últimos estrenos",
            items = listOf(
                createMediaItem("new_1", "New 1"),
                createMediaItem("new_2", "New 2")
            )
        )

        val poolDay0 = BillboardPoolBuilder.buildBillboardPool(listOf(top10, newReleases), seed = 0L)
        val poolDay1 = BillboardPoolBuilder.buildBillboardPool(listOf(top10, newReleases), seed = 1L)

        assertNotEquals(poolDay0.first().id, poolDay1.first().id)
    }
}
