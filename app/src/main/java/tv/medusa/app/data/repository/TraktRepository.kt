package tv.medusa.app.data.repository

import android.util.Log
import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.trakt.TraktApiService
import tv.medusa.app.data.remote.trakt.TraktMediaDto
import tv.medusa.app.data.remote.trakt.TraktUserDto
import tv.medusa.app.data.remote.trakt.TraktUserListDto

/**
 * Acceso a Trakt (recomendaciones, watchlist, tendencias, listas del usuario).
 *
 * Extraído de `MediaRepository`. No toca Room: los resultados se filtran luego
 * contra la biblioteca local con [tv.medusa.app.domain.usecase.FilterToLibraryUseCase].
 */
class TraktRepository {

    suspend fun getTraktRecommendations(traktToken: String, clientId: String): Result<List<TraktMediaDto>> {
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TRAKT_BASE_URL, TraktApiService::class.java)
            val authHeader = "Bearer $traktToken"
            val movies = runCatching { api.getRecommendedMovies(authHeader, clientId) }.getOrDefault(emptyList())
            val shows = runCatching { api.getRecommendedShows(authHeader, clientId) }.getOrDefault(emptyList())
            movies + shows
        }
    }

    suspend fun getTraktTrending(clientId: String): Result<List<TraktMediaDto>> {
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TRAKT_BASE_URL, TraktApiService::class.java)
            val movies = runCatching { api.getTrendingMovies(clientId) }.getOrDefault(emptyList()).mapNotNull { it.movie }
            val shows = runCatching { api.getTrendingShows(clientId) }.getOrDefault(emptyList()).mapNotNull { it.show }
            movies + shows
        }
    }

    suspend fun getTraktWatchlist(traktToken: String, clientId: String): Result<List<TraktMediaDto>> {
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TRAKT_BASE_URL, TraktApiService::class.java)
            val authHeader = "Bearer $traktToken"
            val items = api.getWatchlist(authHeader, clientId)
            items.mapNotNull { it.movie ?: it.show }
        }
    }

    suspend fun getTraktUserProfile(traktToken: String, clientId: String): Result<TraktUserDto> {
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TRAKT_BASE_URL, TraktApiService::class.java)
            val authHeader = "Bearer $traktToken"
            api.getUserProfile(authHeader = authHeader, clientId = clientId)
        }
    }

    suspend fun getTraktUserLists(traktToken: String, clientId: String): Result<List<TraktUserListDto>> {
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TRAKT_BASE_URL, TraktApiService::class.java)
            val authHeader = "Bearer $traktToken"

            val personalLists = runCatching {
                api.getUserLists(authHeader = authHeader, clientId = clientId)
            }.onFailure {
                Log.w("TraktRepo", "Error fetching user personal lists: ${it.localizedMessage}")
            }.getOrDefault(emptyList())

            val likedLists = runCatching {
                api.getLikedLists(authHeader = authHeader, clientId = clientId)
            }.onFailure {
                Log.w("TraktRepo", "Error fetching user liked lists: ${it.localizedMessage}")
            }.getOrDefault(emptyList()).map { it.list }

            val combined = (personalLists + likedLists)
                .filter { it.name.isNotBlank() }
                .distinctBy { it.ids.trakt?.toString() ?: it.ids.slug ?: it.name }

            Log.d("TraktRepo", "Found ${personalLists.size} personal lists and ${likedLists.size} liked lists (total: ${combined.size})")
            combined
        }
    }

    suspend fun getTraktCustomListItems(
        traktToken: String,
        clientId: String,
        listId: String,
        username: String? = null
    ): Result<List<TraktMediaDto>> {
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TRAKT_BASE_URL, TraktApiService::class.java)
            val authHeader = "Bearer $traktToken"

            val targetUser = username?.takeIf { it.isNotBlank() } ?: "me"

            // 1. Intentar endpoint de usuario (/users/{user}/lists/{list_id}/items) que soporta tanto slugs como IDs
            val userItems = runCatching {
                api.getUserListItems(authHeader = authHeader, clientId = clientId, user = targetUser, listId = listId)
            }.onFailure {
                Log.w("TraktRepo", "Error fetching user list items ($targetUser / $listId): ${it.localizedMessage}")
            }.getOrNull()

            val items = if (!userItems.isNullOrEmpty()) {
                userItems
            } else {
                // 2. Fallback a endpoint global de listas (/lists/{list_id}/items)
                runCatching {
                    api.getCustomListItems(authHeader = authHeader, clientId = clientId, listId = listId)
                }.onFailure {
                    Log.w("TraktRepo", "Fallback error fetching list items ($listId): ${it.localizedMessage}")
                }.getOrDefault(emptyList())
            }

            Log.d("TraktRepo", "List $listId (user: $targetUser) returned ${items.size} raw items")
            items.mapNotNull { it.movie ?: it.show }
        }
    }
}
