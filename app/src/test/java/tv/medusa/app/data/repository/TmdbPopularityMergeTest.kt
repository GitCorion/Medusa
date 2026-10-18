package tv.medusa.app.data.repository

import tv.medusa.app.data.remote.tmdb.TmdbItemDto
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbPopularityMergeTest {

    @Test
    fun `merges movies and tv into a single ranking ordered by popularity desc`() {
        val movies = listOf(
            TmdbItemDto(id = 1, title = "Movie low", popularity = 10.0),
            TmdbItemDto(id = 2, title = "Movie high", popularity = 50.0)
        )
        val tv = listOf(
            TmdbItemDto(id = 3, name = "Tv mid", popularity = 30.0),
            TmdbItemDto(id = 4, name = "Tv top", popularity = 80.0)
        )

        val merged = mergeTmdbByPopularity(movies, tv)

        // Un único ranking de plataforma: no "todas las pelis y luego todas las series".
        assertEquals(listOf(4L, 2L, 3L, 1L), merged.map { it.id })
    }

    @Test
    fun `keeps the most popular entry when the same title is duplicated`() {
        val movies = listOf(TmdbItemDto(id = 7, title = "Duplicated", popularity = 5.0))
        val tv = listOf(TmdbItemDto(id = 7, name = "Duplicated", popularity = 90.0))

        val merged = mergeTmdbByPopularity(movies, tv)

        assertEquals(1, merged.size)
        assertEquals(90.0, merged.first().popularity!!, 0.001)
    }

    @Test
    fun `items without popularity are placed last`() {
        val movies = listOf(TmdbItemDto(id = 1, title = "No popularity"))
        val tv = listOf(TmdbItemDto(id = 2, name = "Has popularity", popularity = 3.0))

        val merged = mergeTmdbByPopularity(movies, tv)

        assertEquals(listOf(2L, 1L), merged.map { it.id })
    }
}
