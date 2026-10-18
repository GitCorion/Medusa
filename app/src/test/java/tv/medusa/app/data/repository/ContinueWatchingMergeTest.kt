package tv.medusa.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.medusa.app.data.local.db.JellyfinMediaEntity

/**
 * Reglas de la mezcla de "Continuar viendo" (Resume + NextUp).
 *
 * Lo que se protege aquí: **no inventar fechas**. Antes, un ítem sin fecha del servidor recibía una
 * marca "ahora", que es más nueva que cualquier fecha real y lo colocaba por delante de todo.
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
    fun `los items sin fecha conocida van al final y conservan el orden del servidor`() {
        val merged = mergeContinueWatching(
            resume = listOf(movie("con-fecha", 1_000L)),
            nextUp = listOf(episode("next-a", seriesId = "A"), episode("next-b", seriesId = "B"))
        )

        assertEquals(listOf("con-fecha", "next-a", "next-b"), merged.map { it.id })
        // Los ítems sin fecha siguen sin fecha: no se ha inventado ninguna.
        assertEquals(listOf(1_000L, 0L, 0L), merged.map { it.lastPlayedTicks })
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
    fun `al guardar usa la fecha heredada de la serie pero nunca una inventada`() {
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
        assertEquals(0L, toPersist[1].lastPlayedTicks)
        assertEquals(0L, toPersist[2].lastPlayedTicks)
    }

    @Test
    fun `las fechas por serie salen del Resume y si no de la base de datos`() {
        val dates = seriesLastPlayedDates(
            resume = listOf(
                episode("cap-1", seriesId = "A", lastPlayed = 500L),
                episode("cap-2", seriesId = "A", lastPlayed = 900L)
            ),
            nextUp = listOf(episode("cap-3", seriesId = "A"), episode("cap-4", seriesId = "B")),
            knownFromDb = mapOf("A" to 100L, "B" to 800L)
        )

        // A: gana la fecha más reciente de sus propios capítulos; B: la de su fila en Room.
        assertEquals(900L, dates["A"])
        assertEquals(800L, dates["B"])
    }
}
