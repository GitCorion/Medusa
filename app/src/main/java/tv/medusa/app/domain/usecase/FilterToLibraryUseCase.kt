package tv.medusa.app.domain.usecase

import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.remote.tmdb.TmdbItemDto
import tv.medusa.app.data.remote.trakt.TraktMediaDto
import tv.medusa.app.data.repository.MediaRepository

class FilterToLibraryUseCase(
    private val mediaRepository: MediaRepository
) {
    /**
     * Filters Trakt items against the user's Jellyfin library.
     * Uses strictly local SQLite (Room) index lookups (0.1ms per item).
     * NEVER sends remote search queries to Jellyfin to prevent server CPU overload.
     */
    suspend fun filterTraktItems(
        traktItems: List<TraktMediaDto>,
        serverUrl: String = "",
        userId: String = "",
        token: String = "",
        maxCandidates: Int = 20
    ): List<JellyfinMediaEntity> {
        val seenIds = mutableSetOf<String>()
        val results = mutableListOf<JellyfinMediaEntity>()

        for (item in traktItems) {
            if (results.size >= maxCandidates) break
            val tmdbId = item.ids.tmdb?.toString()
            val imdbId = item.ids.imdb
            val title = item.title
            val year = item.year

            val entity = mediaRepository.findLocal(
                tmdbId = tmdbId,
                imdbId = imdbId,
                title = title,
                year = year
            )
            if (entity != null && seenIds.add(entity.id)) {
                results.add(entity)
            }
        }
        return results
    }

    /**
     * Filters TMDb items (Trending, Streaming Providers, Recommendations) against Jellyfin library.
     * Uses strictly local SQLite (Room) index lookups (0.1ms per item).
     * NEVER sends remote search queries to Jellyfin to prevent server CPU overload.
     */
    suspend fun filterTmdbItems(
        tmdbItems: List<TmdbItemDto>,
        serverUrl: String = "",
        userId: String = "",
        token: String = "",
        maxCandidates: Int = 20
    ): List<JellyfinMediaEntity> {
        val seenIds = mutableSetOf<String>()
        val results = mutableListOf<JellyfinMediaEntity>()

        for (item in tmdbItems) {
            if (results.size >= maxCandidates) break
            val tmdbId = item.id.toString()
            val title = item.title ?: item.name ?: continue
            val year = (item.releaseDate ?: item.firstAirDate)?.take(4)?.toIntOrNull()

            val entity = mediaRepository.findLocal(
                tmdbId = tmdbId,
                imdbId = null,
                title = title,
                year = year
            )
            if (entity != null && seenIds.add(entity.id)) {
                var updated = entity
                if (item.voteAverage != null && item.voteAverage > 0f) {
                    updated = updated.copy(communityRating = item.voteAverage)
                }
                if (updated.primaryImageTag.isNullOrEmpty() && !item.posterPath.isNullOrBlank()) {
                    updated = updated.copy(primaryImageTag = "tmdb:${item.posterPath}")
                }
                if (updated.backdropImageTag.isNullOrEmpty() && !item.backdropPath.isNullOrBlank()) {
                    updated = updated.copy(backdropImageTag = "tmdb:${item.backdropPath}")
                }
                results.add(updated)
            }
        }
        return results
    }
}
