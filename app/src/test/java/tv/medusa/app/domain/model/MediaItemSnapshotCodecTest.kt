package tv.medusa.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaItemSnapshotCodecTest {

    @Test
    fun `round trips cards with nulls and metadata`() {
        val items = listOf(
            MediaItem(
                id = "abc",
                title = "Pesadilla antes de Navidad",
                type = "Movie",
                posterUrl = "https://example/poster.jpg",
                backdropUrl = null,
                rating = 8.2f,
                year = 1993,
                source = MediaSource.JELLYFIN,
                playbackPositionTicks = 1200L,
                isPlayed = true,
                isFavorite = true,
                totalEpisodes = null,
                playedEpisodes = null
            ),
            MediaItem(
                id = "def",
                title = "Serie Ñ",
                overview = "Una descripción con\nsaltos de línea",
                type = "Series",
                source = MediaSource.TRAKT_RECOMMENDATION,
                rank = 3,
                isPlayed = false,
                isFavorite = false,
                totalEpisodes = 10,
                playedEpisodes = 4,
                seriesId = "def",
                seriesName = "Serie Ñ"
            )
        )

        val decoded = MediaItemSnapshotCodec.decode(MediaItemSnapshotCodec.encode(items))

        assertEquals(items, decoded)
    }

    @Test
    fun `ignores empty payload`() {
        assertEquals(emptyList<MediaItem>(), MediaItemSnapshotCodec.decode(""))
    }
}
