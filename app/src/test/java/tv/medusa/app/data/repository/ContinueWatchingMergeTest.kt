package tv.medusa.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.medusa.app.data.local.db.JellyfinMediaEntity

/**
 * Reglas de la mezcla de "Continuar viendo" (Resume + NextUp).
 */
class ContinueWatchingMergeTest {

    private fun movie(id: String, lastPlayed: Long = 0L) = JellyfinMediaEntity(
        id = id,
        title = id,
        type = "Movie",
        lastPlayedTicks = lastPlayed
    )

    private fun episode(
        id: String,
        seriesId: String,
        lastPlayed: Long = 0L,
        positionTicks: Long = 0L
    ) = JellyfinMediaEntity(
        id = id,
        title = id,
        type = "Episode",
        seriesId = seriesId,
        lastPlayedTicks = lastPlayed,
        playbackPositionTicks = positionTicks
    )

    @Test
    fun `ordena por fecha real descendente`() {
        val merged = mergeContinueWatching(
            resume = listOf(movie("vieja", 1_000L), movie("nueva", 5_000L), movie("media", 3_000L)),
            nextUp = emptyList()
        )

        assertEquals(listOf("nueva", "media", "vieja"), merged.map { it.id })
    }

    @Test
    fun `un capitulo de NextUp hereda la fecha de su serie y se intercala`() {
        val merged = mergeContinueWatching(
            resume = listOf(
                episode("cap-pausado", seriesId = "A", lastPlayed = 300L, positionTicks = 100L),
                movie("peli", 500L)
            ),
            nextUp = listOf(episode("cap-siguiente", seriesId = "B")),
            seriesDates = mapOf("B" to 900L)
        )

        // La serie B (900) va arriba por su fecha real, intercalada entre la película (500) y A (300).
        assertEquals(listOf("cap-siguiente", "peli", "cap-pausado"), merged.map { it.id })
    }

    @Test
    fun `en empate gana el episodio que dejaste a medias`() {
        val merged = mergeContinueWatching(
            resume = listOf(episode("cap-pausado", seriesId = "A", lastPlayed = 700L, positionTicks = 42L)),
            nextUp = listOf(episode("cap-siguiente", seriesId = "A")),
            seriesDates = mapOf("A" to 700L)
        )

        assertEquals(listOf("cap-pausado"), merged.map { it.id })
    }

    @Test
    fun `cada serie aparece una sola vez`() {
        val merged = mergeContinueWatching(
            resume = listOf(
                episode("cap-1", seriesId = "A", lastPlayed = 200L),
                episode("cap-2", seriesId = "A", lastPlayed = 100L),
                movie("peli", 150L)
            ),
            nextUp = listOf(episode("cap-3", seriesId = "A"))
        )

        assertEquals(listOf("cap-1", "peli"), merged.map { it.id })
    }

    @Test
    fun `las peliculas no se agrupan entre si`() {
        val merged = mergeContinueWatching(
            resume = listOf(movie("peli-1", 200L), movie("peli-2", 100L)),
            nextUp = emptyList()
        )

        assertEquals(listOf("peli-1", "peli-2"), merged.map { it.id })
    }

    @Test
    fun `al guardar usa la fecha heredada de la serie`() {
        val seriesDates = mapOf("A" to 900L)

        val toPersist = continueWatchingEntitiesToPersist(
            items = listOf(
                episode("cap-sin-fecha", seriesId = "A"),
                episode("cap-huerfano", seriesId = "Z"),
                movie("peli-sin-fecha")
            ),
            seriesDates = seriesDates
        )

        assertEquals(900L, toPersist[0].lastPlayedTicks)
    }

    @Test
    fun `una serie sin fecha propia sube por encima de otra mas antigua usando la fecha de sus capitulos`() {
        val merged = mergeContinueWatching(
            resume = listOf(episode("cap-en-curso", seriesId = "A", lastPlayed = 100L, positionTicks = 10L)),
            nextUp = listOf(episode("cap-siguiente-b", seriesId = "B")),
            seriesDates = mapOf("B" to 9_000L)
        )

        assertEquals(listOf("cap-siguiente-b", "cap-en-curso"), merged.map { it.id })
        val toPersist = continueWatchingEntitiesToPersist(merged, mapOf("B" to 9_000L))
        assertEquals(9_000L, toPersist.first { it.id == "cap-siguiente-b" }.lastPlayedTicks)
    }

    @Test
    fun `las fechas por serie salen del Resume y si no de la base de datos o recencia del servidor`() {
        val dates = seriesLastPlayedDates(
            resume = listOf(
                episode("cap-1", seriesId = "A", lastPlayed = 500L),
                episode("cap-2", seriesId = "A", lastPlayed = 900L)
            ),
            nextUp = listOf(episode("cap-3", seriesId = "A"), episode("cap-4", seriesId = "B")),
            knownFromDb = mapOf("A" to 100L, "B" to 800L)
        )

        assertEquals(900L, dates["A"])
        assertEquals(800L, dates["B"])
    }

    @Test
    fun `nextUp sin fechas preserva el orden de recencia del servidor`() {
        val dates = seriesLastPlayedDates(
            resume = emptyList(),
            nextUp = listOf(
                episode("cap-a", seriesId = "A"),
                episode("cap-b", seriesId = "B"),
                episode("cap-c", seriesId = "C")
            )
        )

        assertTrue("A debe ser mayor que B", dates["A"]!! > dates["B"]!!)
        assertTrue("B debe ser mayor que C", dates["B"]!! > dates["C"]!!)
    }

    @Test
    fun `nextUp recalcula fechas monotonamente si el orden del servidor es mas reciente`() {
        val dates = seriesLastPlayedDates(
            resume = emptyList(),
            nextUp = listOf(
                episode("cap-a", seriesId = "A"),
                episode("cap-b", seriesId = "B")
            ),
            knownFromDb = mapOf("A" to 100L, "B" to 500L) // A vista más recientemente en el servidor pero B con fecha más vieja guardada
        )

        assertTrue("A debe ser mayor que B para respetar el orden del servidor", dates["A"]!! > dates["B"]!!)
        assertEquals(500L, dates["B"])
    }
}
