package tv.medusa.app.data.repository

import android.content.Context
import tv.medusa.app.data.local.db.JellyfinDao
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.R
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.jellyfin.JellyfinApiService
import tv.medusa.app.data.remote.jellyfin.JellyfinItemDto
import tv.medusa.app.domain.model.EpisodeItem
import tv.medusa.app.domain.model.SeasonItem
import tv.medusa.app.domain.model.SeriesEpisodeStats
import tv.medusa.app.util.JellyfinImageUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Lecturas/escrituras contra el servidor Jellyfin que además refrescan la caché local.
 *
 * Extraído de `MediaRepository`. Agrupa las consultas puntuales (continuar viendo,
 * últimos, detalle, episodios, temporadas, colecciones, búsqueda) y el marcado de
 * favorito/visto. Los métodos de sincronización masiva viven en `MediaSyncRepository`.
 */
class JellyfinMediaRemoteRepository(
    private val jellyfinDao: JellyfinDao,
    private val accessPolicy: MediaAccessPolicy,
    private val localMediaRepository: LocalMediaRepository,
    private val appContext: Context? = null
) {

    suspend fun searchAndCache(
        serverUrl: String,
        userId: String,
        token: String,
        title: String,
        year: Int? = null,
        tmdbId: String? = null,
        imdbId: String? = null
    ): JellyfinMediaEntity? {
        // 1. Check local Room database first
        val local = localMediaRepository.findLocal(tmdbId = tmdbId, imdbId = imdbId, title = title, year = year)
        if (local != null) return local

        // 2. Query remote Jellyfin with fast indexed search
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val cleanTitle = title.trim()
                .substringBefore(":")
                .substringBefore("-")
                .trim()
            if (cleanTitle.isEmpty()) return null

            val response = api.searchLibraryItems(
                authHeader = authHeader,
                userId = userId,
                searchTerm = cleanTitle,
                limit = 5
            )

            val matches = response.items.map { it.toEntity() }
            if (matches.isNotEmpty()) {
                jellyfinDao.insertOrUpdateAll(matches)
            }

            matches.firstOrNull { entity ->
                val titleMatch = entity.title.contains(cleanTitle, ignoreCase = true) ||
                        cleanTitle.contains(entity.title, ignoreCase = true)
                val yearMatch = year == null || entity.productionYear == null || Math.abs(entity.productionYear - year) <= 1
                val idMatch = (tmdbId != null && entity.tmdbId == tmdbId) || (imdbId != null && entity.imdbId == imdbId)
                idMatch || (titleMatch && yearMatch)
            } ?: matches.firstOrNull()
        }.getOrNull()
    }

    suspend fun searchRemoteAndCache(
        serverUrl: String,
        userId: String,
        token: String,
        query: String,
        limit: Int = 30
    ): Result<List<JellyfinMediaEntity>> {
        val cleanQuery = query.trim()
        if (serverUrl.isBlank() || userId.isBlank() || token.isBlank() || cleanQuery.isBlank()) {
            return Result.success(emptyList())
        }
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val response = api.searchLibraryItems(
                authHeader = authHeader,
                userId = userId,
                searchTerm = cleanQuery,
                limit = limit
            )
            val matches = response.items.map { it.toEntity() }
            if (matches.isNotEmpty()) {
                jellyfinDao.insertOrUpdateAll(matches)
            }
            matches
        }
    }

    suspend fun getUserViews(serverUrl: String, userId: String, token: String): Result<List<JellyfinItemDto>> {
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            api.getUserViews(authHeader = authHeader, userId = userId).items
        }
    }

    suspend fun getContinueWatching(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> {
        val cacheKey = "$serverUrl-$userId"
        MediaCaches.serverContinueWatchingCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)

            coroutineScope {
                val resumeDeferred = async {
                    runCatching {
                        api.getResumeItems(authHeader = authHeader, userId = userId).items.map { it.toEntity() }
                    }.getOrDefault(emptyList())
                }
                val nextUpDeferred = async {
                    runCatching {
                        api.getNextUpItems(authHeader = authHeader, userId = userId).items.map { it.toEntity() }
                    }.getOrDefault(emptyList())
                }

                val resumeEntities = resumeDeferred.await()
                val nextUpEntities = nextUpDeferred.await()

                val allFetched = (resumeEntities + nextUpEntities)
                if (allFetched.isNotEmpty()) {
                    jellyfinDao.insertOrUpdateAll(allFetched)
                }

                val seenKeys = mutableSetOf<String>()
                val combined = mutableListOf<JellyfinMediaEntity>()

                for (item in resumeEntities) {
                    val key = item.seriesId ?: item.id
                    if (seenKeys.add(key)) {
                        combined.add(item)
                    }
                }

                for (item in nextUpEntities) {
                    val key = item.seriesId ?: item.id
                    if (seenKeys.add(key)) {
                        combined.add(item)
                    }
                }

                val deduplicated = combined.deduplicateMediaEntities()
                if (deduplicated.isNotEmpty()) {
                    MediaCaches.serverContinueWatchingCache.put(cacheKey, deduplicated)
                }
                deduplicated
            }
        }
    }

    suspend fun getNextUp(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> {
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val response = api.getNextUpItems(authHeader = authHeader, userId = userId)
            val entities = response.items.map { it.toEntity() }
            if (entities.isNotEmpty()) {
                jellyfinDao.insertOrUpdateAll(entities)
            }
            entities.deduplicateMediaEntities()
        }
    }

    suspend fun getLatest(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> {
        val cacheKey = "$serverUrl-$userId"
        MediaCaches.serverLatestCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val response = api.getLatestItems(authHeader = authHeader, userId = userId)
            val entities = response.map { it.toEntity() }
            if (entities.isNotEmpty()) {
                jellyfinDao.insertOrUpdateAll(entities)
            }
            val deduplicated = entities.deduplicateMediaEntities()
            if (deduplicated.isNotEmpty()) {
                MediaCaches.serverLatestCache.put(cacheKey, deduplicated)
            }
            deduplicated
        }
    }

    suspend fun getItemDetail(serverUrl: String, userId: String, token: String, itemId: String): Result<JellyfinMediaEntity> {
        return runCatching {
            val local = jellyfinDao.getItemById(itemId)
            if (local != null && !local.overview.isNullOrBlank()) return@runCatching local
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val dto = api.getItemDetail(authHeader = authHeader, userId = userId, itemId = itemId)
            val entity = dto.toEntity()
            if (entity.title.isNotBlank()) {
                val mergedEntity = if (local != null) {
                    entity.copy(
                        overview = if (entity.overview.isNullOrBlank()) local.overview else entity.overview,
                        genres = if (entity.genres.isNullOrBlank()) local.genres else entity.genres,
                        officialRating = if (entity.officialRating.isNullOrBlank()) local.officialRating else entity.officialRating
                    )
                } else entity
                jellyfinDao.insertOrUpdate(mergedEntity)
                return@runCatching mergedEntity
            }
            local ?: entity
        }
    }

    suspend fun getRecentlyPlayed(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> {
        val cacheKey = "$serverUrl-$userId"
        MediaCaches.serverRecentlyPlayedCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val response = api.getRecentlyPlayedItems(authHeader = authHeader, userId = userId)
            val entities = response.items.map { it.toEntity() }
            if (entities.isNotEmpty()) {
                jellyfinDao.insertOrUpdateAll(entities)
                MediaCaches.serverRecentlyPlayedCache.put(cacheKey, entities)
            }
            entities
        }
    }

    suspend fun getFavorites(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> {
        return runCatching {
            val local = jellyfinDao.getFavorites().deduplicateMediaEntities()
            if (serverUrl.isBlank() || token.isBlank()) return@runCatching local
            try {
                val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
                val authHeader = buildJellyfinAuthHeader(token = token)
                val response = api.getFavoriteItems(authHeader = authHeader, userId = userId)
                val entities = response.items.map { it.toEntity().copy(isFavorite = true) }
                if (entities.isNotEmpty()) {
                    jellyfinDao.insertOrUpdateAll(entities)
                    entities.deduplicateMediaEntities()
                } else local
            } catch (e: Exception) {
                local
            }
        }
    }

    suspend fun toggleFavorite(serverUrl: String, userId: String, token: String, itemId: String, makeFavorite: Boolean): Result<Boolean> {
        return runCatching {
            jellyfinDao.updateFavoriteStatus(itemId, makeFavorite)
            if (serverUrl.isNotBlank() && token.isNotBlank() && userId.isNotBlank()) {
                val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
                val authHeader = buildJellyfinAuthHeader(token = token)
                if (makeFavorite) {
                    api.markFavorite(authHeader, userId, itemId)
                } else {
                    api.unmarkFavorite(authHeader, userId, itemId)
                }
            }
            makeFavorite
        }
    }

    suspend fun togglePlayed(serverUrl: String, userId: String, token: String, itemId: String, makePlayed: Boolean): Result<Boolean> {
        return runCatching {
            jellyfinDao.updatePlayedStatus(itemId, makePlayed)
            if (serverUrl.isNotBlank() && token.isNotBlank() && userId.isNotBlank()) {
                val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
                val authHeader = buildJellyfinAuthHeader(token = token)
                if (makePlayed) {
                    api.markPlayed(authHeader, userId, itemId)
                } else {
                    api.unmarkPlayed(authHeader, userId, itemId)
                }
            }
            makePlayed
        }
    }

    /**
     * Selección dinámica de películas para la cabecera — lógica estilo streaming.
     *
     * Prioridad (igual que Netflix/Disney+):
     *   1. Añadidas recientemente que NO has visto → lo más relevante: es nuevo y pendiente
     *   2. Tus favoritas que NO has visto (o que no terminas) → lo que tú mismo has marcado
     *   3. Fallback: lo mejor valorado que aún no has visto → descubrir joyas pendientes
     *
     * NO muestra películas que ya has visto completamente (IsPlayed).
     * NO hace random puro (daría viejas películas sin criterio).
     */
    suspend fun getTopRatedMoviesServer(serverUrl: String, userId: String, token: String, limit: Int = 20): List<JellyfinMediaEntity> {
        if (serverUrl.isBlank() || token.isBlank()) return localMediaRepository.getTopMoviesLocal(limit)
        val cacheKey = "$serverUrl-$userId-$limit"
        MediaCaches.serverTopMoviesCache.get(cacheKey)?.let { return it }

        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val fields = "Overview,ProviderIds,PrimaryImageTag,BackdropImageTags,CommunityRating,OfficialRating,UserData,Genres,Tags"

            // 1. Añadidas recientemente y NO vistas → lo más fresco de tu biblioteca pendiente
            val recentUnplayed = api.getLibraryItems(
                authHeader = authHeader,
                userId = userId,
                includeItemTypes = "Movie",
                fields = fields,
                recursive = true,
                limit = 40, // reducido de 100: solo necesitamos 20, 40 da margen suficiente
                sortBy = "DateCreated",
                sortOrder = "Descending",
                filters = "IsUnplayed"
            ).items.map { it.toEntity() }
                .filter { !it.backdropImageTag.isNullOrEmpty() && (it.communityRating ?: 0f) >= 6.5f }

            // 2. Favoritas no vistas → preferencia explícita del usuario pendiente de ver
            val favoritesUnplayed = runCatching {
                api.getLibraryItems(
                    authHeader = authHeader,
                    userId = userId,
                    includeItemTypes = "Movie",
                    fields = fields,
                    recursive = true,
                    limit = 15,
                    sortBy = "DateCreated",
                    sortOrder = "Descending",
                    filters = "IsFavorite,IsUnplayed"
                ).items.map { it.toEntity() }
                    .filter { !it.backdropImageTag.isNullOrEmpty() }
            }.getOrDefault(emptyList())

            // 3. Fallback: mejor valoradas sin ver → descubrir pendientes de calidad
            val topUnplayed = if ((favoritesUnplayed + recentUnplayed).distinctBy { it.id }.size < limit) {
                runCatching {
                    api.getLibraryItems(
                        authHeader = authHeader,
                        userId = userId,
                        includeItemTypes = "Movie",
                        fields = fields,
                        recursive = true,
                        limit = limit * 2,
                        sortBy = "CommunityRating",
                        sortOrder = "Descending",
                        filters = "IsUnplayed"
                    ).items.map { it.toEntity() }
                        .filter { !it.backdropImageTag.isNullOrEmpty() && (it.communityRating ?: 0f) >= 7.0f }
                }.getOrDefault(emptyList())
            } else emptyList()

            // Favoritas primero, luego recientes, luego top valoradas — sin duplicados
            val merged = (favoritesUnplayed + recentUnplayed + topUnplayed)
                .distinctBy { it.id }
                .take(limit)

            if (merged.isNotEmpty()) {
                jellyfinDao.insertOrUpdateAll(merged)
                MediaCaches.serverTopMoviesCache.put(cacheKey, merged)
                merged
            } else {
                localMediaRepository.getTopMoviesLocal(limit)
            }
        }.getOrDefault(localMediaRepository.getTopMoviesLocal(limit))
    }

    /**
     * Selección dinámica de series para la cabecera — misma lógica que películas.
     *
     * Prioridad:
     *   1. Series añadidas recientemente que NO has empezado
     *   2. Tus series favoritas no vistas
     *   3. Fallback: series mejor valoradas sin ver
     */
    suspend fun getTopRatedSeriesServer(serverUrl: String, userId: String, token: String, limit: Int = 20): List<JellyfinMediaEntity> {
        if (serverUrl.isBlank() || token.isBlank()) return localMediaRepository.getTopSeriesLocal(limit)
        val cacheKey = "$serverUrl-$userId-$limit"
        MediaCaches.serverTopSeriesCache.get(cacheKey)?.let { return it }

        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val fields = "Overview,ProviderIds,PrimaryImageTag,BackdropImageTags,CommunityRating,OfficialRating,UserData,Genres,Tags"

            // 1. Series añadidas recientemente y no empezadas
            val recentUnplayed = api.getLibraryItems(
                authHeader = authHeader,
                userId = userId,
                includeItemTypes = "Series",
                fields = fields,
                recursive = true,
                limit = 40, // reducido de 100
                sortBy = "DateCreated",
                sortOrder = "Descending",
                filters = "IsUnplayed"
            ).items.map { it.toEntity() }
                .filter { !it.backdropImageTag.isNullOrEmpty() && (it.communityRating ?: 0f) >= 6.5f }

            // 2. Favoritas no empezadas
            val favoritesUnplayed = runCatching {
                api.getLibraryItems(
                    authHeader = authHeader,
                    userId = userId,
                    includeItemTypes = "Series",
                    fields = fields,
                    recursive = true,
                    limit = 15,
                    sortBy = "DateCreated",
                    sortOrder = "Descending",
                    filters = "IsFavorite,IsUnplayed"
                ).items.map { it.toEntity() }
                    .filter { !it.backdropImageTag.isNullOrEmpty() }
            }.getOrDefault(emptyList())

            // 3. Fallback: mejor valoradas sin empezar
            val topUnplayed = if ((favoritesUnplayed + recentUnplayed).distinctBy { it.id }.size < limit) {
                runCatching {
                    api.getLibraryItems(
                        authHeader = authHeader,
                        userId = userId,
                        includeItemTypes = "Series",
                        fields = fields,
                        recursive = true,
                        limit = limit * 2,
                        sortBy = "CommunityRating",
                        sortOrder = "Descending",
                        filters = "IsUnplayed"
                    ).items.map { it.toEntity() }
                        .filter { !it.backdropImageTag.isNullOrEmpty() && (it.communityRating ?: 0f) >= 7.0f }
                }.getOrDefault(emptyList())
            } else emptyList()

            val merged = (favoritesUnplayed + recentUnplayed + topUnplayed)
                .distinctBy { it.id }
                .take(limit)

            if (merged.isNotEmpty()) {
                jellyfinDao.insertOrUpdateAll(merged)
                MediaCaches.serverTopSeriesCache.put(cacheKey, merged)
                merged
            } else {
                localMediaRepository.getTopSeriesLocal(limit)
            }
        }.getOrDefault(localMediaRepository.getTopSeriesLocal(limit))
    }

    suspend fun getSeasons(serverUrl: String, userId: String, token: String, seriesId: String): List<SeasonItem> {
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val response = api.getSeasonsForSeries(authHeader = authHeader, seriesId = seriesId, userId = userId)
            response.items.mapNotNull { item ->
                val seasonNum = item.indexNumber ?: 0
                val defaultName = appContext?.let {
                    if (seasonNum == 0) it.getString(R.string.season_specials) else it.getString(R.string.season_default, seasonNum)
                } ?: ""
                SeasonItem(
                    id = item.id,
                    name = item.name ?: defaultName,
                    seasonNumber = seasonNum
                )
            }.sortedWith(compareBy({ if (it.seasonNumber == 0) 9999 else it.seasonNumber }))
        }.getOrDefault(emptyList())
    }

    suspend fun getSeriesEpisodeStats(serverUrl: String, userId: String, token: String, seriesId: String): SeriesEpisodeStats? {
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val response = api.getEpisodesForSeries(
                authHeader = authHeader,
                seriesId = seriesId,
                userId = userId,
                fields = "UserData,IndexNumber,ParentIndexNumber"
            )
            val validEpisodes = response.items.filter { (it.parentIndexNumber ?: 0) > 0 && (it.indexNumber ?: 0) > 0 }
            if (validEpisodes.isEmpty()) return@runCatching null

            // Deduplicate across multi-sources and versions by (seasonNumber, episodeNumber)
            val uniqueEpisodes = validEpisodes.groupBy { Pair(it.parentIndexNumber ?: 1, it.indexNumber ?: 1) }
            val total = uniqueEpisodes.size
            val played = uniqueEpisodes.count { (_, duplicates) ->
                duplicates.any { it.userData?.isPlayed == true }
            }
            val unplayed = (total - played).coerceAtLeast(0)

            jellyfinDao.getItemById(seriesId)?.let { localSeries ->
                val updated = localSeries.copy(
                    totalItemCount = total,
                    unplayedItemCount = unplayed,
                    isPlayed = unplayed == 0 && total > 0
                )
                jellyfinDao.insertOrUpdate(updated)
            }

            SeriesEpisodeStats(
                totalUniqueEpisodes = total,
                playedUniqueEpisodes = played,
                unplayedUniqueEpisodes = unplayed
            )
        }.getOrNull()
    }

    suspend fun getCollectionItems(
        serverUrl: String,
        userId: String,
        token: String,
        collectionId: String
    ): List<JellyfinMediaEntity> {
        if (serverUrl.isBlank() || userId.isBlank() || token.isBlank() || collectionId.isBlank()) return emptyList()
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val response = api.getCollectionItems(
                authHeader = authHeader,
                userId = userId,
                parentId = collectionId
            )
            val entities = response.items.map { it.toEntity() }
            if (entities.isNotEmpty()) {
                jellyfinDao.insertOrUpdateAll(entities)
            }
            entities
        }.getOrDefault(emptyList())
    }

    suspend fun getEpisodes(
        serverUrl: String,
        userId: String,
        token: String,
        seriesId: String,
        seasonId: String? = null
    ): List<EpisodeItem> {
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val response = api.getEpisodesForSeries(
                authHeader = authHeader,
                seriesId = seriesId,
                userId = userId,
                seasonId = seasonId
            )
            val entities = response.items.map { it.toEntity() }
            if (entities.isNotEmpty()) {
                jellyfinDao.insertOrUpdateAll(entities)
            }
            val rawEpisodes = response.items.map { item ->
                val epNum = item.indexNumber ?: 1
                val seasonNum = item.parentIndexNumber ?: 1
                val durationMin = item.runTimeTicks?.let { (it / 10_000_000L / 60L).toInt() }
                val imageTag = item.imageTags?.get("Primary")
                val imageUrl = imageTag?.let {
                    JellyfinImageUtils.getEpisodeThumbnailUrl(
                        baseUrl = serverUrl,
                        itemId = item.id,
                        imageTag = it,
                        token = token
                    )
                }

                EpisodeItem(
                    id = item.id,
                    name = item.name ?: "Episodio $epNum",
                    episodeNumber = epNum,
                    seasonNumber = seasonNum,
                    overview = item.overview,
                    imageUrl = imageUrl,
                    durationMinutes = durationMin,
                    isPlayed = item.userData?.isPlayed ?: false,
                    playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0L
                )
            }

            // Deduplicate episodes with the same (seasonNumber, episodeNumber)
            rawEpisodes.groupBy { Pair(it.seasonNumber, it.episodeNumber) }
                .map { (_, duplicates) ->
                    if (duplicates.size == 1) {
                        duplicates.first()
                    } else {
                        val bestPlayed = duplicates.any { it.isPlayed }
                        val maxTicks = duplicates.maxOf { it.playbackPositionTicks }

                        // Prefer item with clean title (doesn't contain filename formatting like S01E01 or trailing hyphen)
                        val cleanTitleItem = duplicates.firstOrNull { ep ->
                            val name = ep.name.trim()
                            !name.contains("S0", ignoreCase = true) &&
                            !name.contains("S1", ignoreCase = true) &&
                            !name.contains("S2", ignoreCase = true) &&
                            !name.endsWith("-")
                        } ?: duplicates.first()

                        val primaryItem = duplicates.firstOrNull { it.isPlayed || it.playbackPositionTicks > 0 }
                            ?: cleanTitleItem

                        cleanTitleItem.copy(
                            id = primaryItem.id,
                            isPlayed = bestPlayed,
                            playbackPositionTicks = maxTicks,
                            overview = cleanTitleItem.overview ?: duplicates.firstNotNullOfOrNull { it.overview },
                            imageUrl = cleanTitleItem.imageUrl ?: duplicates.firstNotNullOfOrNull { it.imageUrl },
                            durationMinutes = cleanTitleItem.durationMinutes ?: duplicates.firstNotNullOfOrNull { it.durationMinutes }
                        )
                    }
                }
                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
        }.getOrDefault(emptyList())
    }

    suspend fun getNextUpEpisode(
        serverUrl: String,
        userId: String,
        token: String,
        seriesId: String
    ): EpisodeItem? {
        return runCatching {
            val api = NetworkClientFactory.createService(serverUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val nextUpResponse = api.getNextUpItems(
                authHeader = authHeader,
                userId = userId,
                seriesId = seriesId,
                limit = 1
            )
            val nextItem = nextUpResponse.items.firstOrNull()
            if (nextItem != null) {
                val epNum = nextItem.indexNumber ?: 1
                val seasonNum = nextItem.parentIndexNumber ?: 1
                EpisodeItem(
                    id = nextItem.id,
                    name = nextItem.name ?: "Episodio $epNum",
                    episodeNumber = epNum,
                    seasonNumber = seasonNum,
                    overview = nextItem.overview,
                    isPlayed = nextItem.userData?.isPlayed ?: false,
                    playbackPositionTicks = nextItem.userData?.playbackPositionTicks ?: 0L
                )
            } else {
                null
            }
        }.getOrNull()
    }
}

internal fun buildJellyfinAuthHeader(
    clientName: String = "Medusa",
    deviceId: String = "MedusaTV",
    version: String = "1.0.0",
    token: String = ""
): String {
    return "MediaBrowser Client=\"$clientName\", Device=\"AndroidTV\", DeviceId=\"$deviceId\", Version=\"$version\"" +
            if (token.isNotEmpty()) ", Token=\"$token\"" else ""
}

internal fun JellyfinItemDto.toEntity(): JellyfinMediaEntity {
    val totalCount = effectiveItemCount
    val unplayedCount = userData?.unplayedItemCount
    val itemType = type ?: "Unknown"
    val playedStatus = userData?.isPlayedForType(itemType) ?: false

    return JellyfinMediaEntity(
        id = id,
        title = name ?: "",
        originalTitle = originalTitle,
        type = itemType,
        tmdbId = providerIds?.get("Tmdb") ?: providerIds?.get("tmdb"),
        imdbId = providerIds?.get("Imdb") ?: providerIds?.get("imdb"),
        tvdbId = providerIds?.get("Tvdb") ?: providerIds?.get("tvdb"),
        overview = overview,
        primaryImageTag = primaryImageTag ?: imageTags?.get("Primary"),
        backdropImageTag = backdropImageTags?.firstOrNull(),
        communityRating = communityRating,
        productionYear = productionYear,
        genres = genres?.joinToString(", "),
        officialRating = officialRating,
        tags = tags?.joinToString(", "),
        isPlayed = playedStatus,
        playbackPositionTicks = userData?.effectivePositionTicks ?: 0L,
        isFavorite = userData?.effectiveIsFavorite ?: false,
        seriesId = seriesId,
        seriesName = seriesName,
        seriesPrimaryImageTag = seriesPrimaryImageTag,
        seasonNumber = parentIndexNumber,
        episodeNumber = indexNumber,
        totalItemCount = totalCount,
        unplayedItemCount = unplayedCount
    )
}
