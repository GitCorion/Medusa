package tv.medusa.app.domain.usecase

import tv.medusa.app.data.local.DEFAULT_TMDB_API_KEY
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.model.SeasonalEvent
import tv.medusa.app.domain.model.SeasonalEventsManager
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.util.toOptimizedMediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class SeasonalFeedResult(
    val event: SeasonalEvent,
    val sections: List<HomeSection>,
    val featuredItem: MediaItem? = null
)

class GetSeasonalFeedUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun getSeasonalFeed(eventId: String): SeasonalFeedResult? {
        val event = SeasonalEventsManager.ALL_EVENTS.firstOrNull { it.id == eventId }
            ?: SeasonalEventsManager.getActiveEvent()
            ?: return null

        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val effectiveTmdbKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY

        return coroutineScope {
            // 1. Trending temático desde TMDb por keyword (3335 para Halloween, 207317 para Navidad...)
            val tmdbItemsDeferred = async {
                if (effectiveTmdbKey.isNotBlank()) {
                    runCatching {
                        mediaRepository.getTmdbSeasonalDiscover(
                            apiKey = effectiveTmdbKey,
                            withKeywords = event.tmdbKeywordId,
                            maxPages = 3
                        ).getOrDefault(emptyList())
                    }.getOrDefault(emptyList())
                } else emptyList()
            }

            // 2. Títulos locales del género principal de la biblioteca
            val localGenreDeferred = async {
                runCatching {
                    mediaRepository.getItemsByGenre(event.primaryGenreName)
                }.getOrDefault(emptyList())
            }

            val rawTmdbItems = tmdbItemsDeferred.await()
            val matchedTrending = if (rawTmdbItems.isNotEmpty()) {
                filterToLibraryUseCase.filterTmdbItems(
                    tmdbItems = rawTmdbItems,
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    maxCandidates = 40
                )
            } else emptyList()

            val localGenreEntities = localGenreDeferred.await()
            val seenIds = matchedTrending.map { it.id }.toSet()
            val remainingLocal = localGenreEntities.filter { it.id !in seenIds }

            val sections = mutableListOf<HomeSection>()

            if (matchedTrending.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        title = "Tendencias de la temporada",
                        items = matchedTrending.map {
                            it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
                        },
                        badge = event.badgeLabel
                    )
                )
            }

            if (remainingLocal.isNotEmpty()) {
                val sortedLocal = remainingLocal.sortedByDescending { it.communityRating ?: 0f }
                sections.add(
                    HomeSection(
                        title = "De tu colección",
                        items = sortedLocal.take(30).map {
                            it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
                        }
                    )
                )
            }

            val featured = matchedTrending.firstOrNull()?.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
                ?: remainingLocal.firstOrNull()?.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)

            SeasonalFeedResult(
                event = event,
                sections = sections,
                featuredItem = featured
            )
        }
    }
}

private fun JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String, source: MediaSource): MediaItem =
    toOptimizedMediaItem(baseUrl, token, source)

