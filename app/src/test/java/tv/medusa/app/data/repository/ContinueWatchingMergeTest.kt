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
        positionTicks: Long = 0L,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        premiereDateMs: Long? = null
    ) = JellyfinMediaEntity(
        id = id,
        title = id,
        type = "Episode",
        seriesId = seriesId,
        lastPlayedTicks = lastPlayed,
        playbackPositionTicks = positionTicks,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        premiereDateMs = premiereDateMs
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
    fun `series mid-season miran la fecha real del ultimo reproducido en el pasado sin adivinar el futuro`() {
        val dates = seriesLastPlayedDates(
            resume = emptyList(),
            nextUp = listOf(
                episode("cap-a", seriesId = "A", seasonNumber = 1, episodeNumber = 5),
                episode("cap-b", seriesId = "B", seasonNumber = 1, episodeNumber = 3)
            ),
            knownFromDb = mapOf("A" to 100L, "B" to 500L)
        )

        // B se reprodujo en 500L (más reciente en el pasado que A en 100L)
        assertEquals(500L, dates["B"])
        assertEquals(100L, dates["A"])
        assertTrue("B debe ser mayor que A según su fecha real pasada", dates["B"]!! > dates["A"]!!)
    }

    @Test
    fun `nueva temporada en NextUp se prioriza al frente para no perderla`() {
        val pausedMovie = movie("peli-hoy", lastPlayed = 100_000L)
        val newSeasonEp = episode("cap-s2e1", seriesId = "SerieNuevaTemporada", seasonNumber = 2, episodeNumber = 1)
        val regularEp = episode("cap-s1e5", seriesId = "SerieNormal", seasonNumber = 1, episodeNumber = 5)

        val seriesDates = seriesLastPlayedDates(
            resume = listOf(pausedMovie),
            nextUp = listOf(newSeasonEp, regularEp),
            knownFromDb = mapOf("SerieNuevaTemporada" to 10_000L, "SerieNormal" to 50_000L),
            nowMs = 200_000L
        )

        val merged = mergeContinueWatching(
            resume = listOf(pausedMovie),
            nextUp = listOf(newSeasonEp, regularEp),
            seriesDates = seriesDates
        )

        // La nueva temporada se prioriza la primera
        assertEquals("cap-s2e1", merged[0].id)
        // La película que el usuario dejó a medias hoy va en su posición real (segunda)
        assertEquals("peli-hoy", merged[1].id)
        // La serie regular que no tiene nueva temporada va tercera según su última reproducción
        assertEquals("cap-s1e5", merged[2].id)
    }

    @Test
    fun `capitulo nuevo estrenado recientemente posterior a la reproduccion se prioriza al frente`() {
        val pausedMovie = movie("peli-hoy", lastPlayed = 100_000L)
        val newPremieredEp = episode(
            "cap-nuevo-estreno",
            seriesId = "SerieSemanal",
            seasonNumber = 1,
            episodeNumber = 6,
            premiereDateMs = 150_000L // Estrenado posterior a cuando se vio la serie y la peli
        )

        val seriesDates = seriesLastPlayedDates(
            resume = listOf(pausedMovie),
            nextUp = listOf(newPremieredEp),
            knownFromDb = mapOf("SerieSemanal" to 20_000L),
            nowMs = 200_000L
        )

        val merged = mergeContinueWatching(
            resume = listOf(pausedMovie),
            nextUp = listOf(newPremieredEp),
            seriesDates = seriesDates
        )

        assertEquals("cap-nuevo-estreno", merged[0].id)
        assertEquals("peli-hoy", merged[1].id)
    }

    @Test
    fun `series recientemente vistas en recentlyPlayed se colocan por delante de pelicula pausada mas vieja`() {
        val resumeMovie = movie("peli-vieja", lastPlayed = 1_000L)
        val nextUpEp = episode("cap-siguiente", seriesId = "SerieReciente")
        val finishedEp = episode("cap-terminado", seriesId = "SerieReciente", lastPlayed = 9_000L)

        val seriesDates = seriesLastPlayedDates(
            resume = listOf(resumeMovie),
            nextUp = listOf(nextUpEp),
            recentlyPlayed = listOf(finishedEp)
        )

        val merged = mergeContinueWatching(
            resume = listOf(resumeMovie),
            nextUp = listOf(nextUpEp),
            seriesDates = seriesDates
        )

        assertEquals(9_000L, seriesDates["SerieReciente"])
        assertEquals(listOf("cap-siguiente", "peli-vieja"), merged.map { it.id })
    }

    @Test
    fun `pelicula pausada recientemente se coloca por delante de series vistas antes`() {
        val resumeMovie = movie("peli-reciente", lastPlayed = 10_000L)
        val nextUpEp = episode("cap-siguiente", seriesId = "SerieVieja")
        val finishedEp = episode("cap-terminado", seriesId = "SerieVieja", lastPlayed = 2_000L)

        val seriesDates = seriesLastPlayedDates(
            resume = listOf(resumeMovie),
            nextUp = listOf(nextUpEp),
            recentlyPlayed = listOf(finishedEp)
        )

        val merged = mergeContinueWatching(
            resume = listOf(resumeMovie),
            nextUp = listOf(nextUpEp),
            seriesDates = seriesDates
        )

        assertEquals(listOf("peli-reciente", "cap-siguiente"), merged.map { it.id })
    }

    @Test
    fun `pelicula pausada hoy se coloca por delante de series de NextUp sin fecha de reproduccion`() {
        val resumeMovie = movie("peli-hoy", lastPlayed = 100_000L)
        val nextUpEp1 = episode("cap-siguiente-1", seriesId = "SerieA")
        val nextUpEp2 = episode("cap-siguiente-2", seriesId = "SerieB")

        val seriesDates = seriesLastPlayedDates(
            resume = listOf(resumeMovie),
            nextUp = listOf(nextUpEp1, nextUpEp2)
        )

        val merged = mergeContinueWatching(
            resume = listOf(resumeMovie),
            nextUp = listOf(nextUpEp1, nextUpEp2),
            seriesDates = seriesDates
        )

        assertEquals("peli-hoy", merged.first().id)
        assertTrue(seriesDates["SerieA"]!! < 100_000L)
        assertTrue(seriesDates["SerieB"]!! < seriesDates["SerieA"]!!)
    }
}
