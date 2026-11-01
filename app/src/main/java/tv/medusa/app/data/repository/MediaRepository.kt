package tv.medusa.app.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.AppDatabase
import tv.medusa.app.data.local.db.AllowedMediaDao
import tv.medusa.app.data.local.db.JellyfinDao
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.local.db.LibraryStats
import tv.medusa.app.data.local.db.TmdbVoteCacheDao
import tv.medusa.app.data.remote.jellyfin.JellyfinItemDto
import tv.medusa.app.data.remote.tmdb.TmdbItemDto
import tv.medusa.app.data.remote.tmdb.TmdbMovieDetailDto
import tv.medusa.app.data.remote.tmdb.TmdbTvDetailDto
import tv.medusa.app.data.remote.trakt.TraktMediaDto
import tv.medusa.app.data.remote.trakt.TraktUserDto
import tv.medusa.app.data.remote.trakt.TraktUserListDto
import tv.medusa.app.domain.model.EpisodeItem
import tv.medusa.app.domain.model.SeasonItem
import tv.medusa.app.domain.model.SeriesEpisodeStats
import tv.medusa.app.domain.model.SeriesStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Punto de entrada único del acceso a datos (catálogo Jellyfin, TMDB y Trakt).
 *
 * Históricamente era una clase-dios de ~1.900 líneas. Ahora es una **fachada delgada**
 * que compone repositorios especializados y delega en ellos, manteniendo la misma API
 * pública para no tocar a los consumidores (ViewModels, UseCases, Worker):
 *
 * - [LocalMediaRepository]        → consultas Room, deduplicado y resolución local
 * - [JellyfinMediaRemoteRepository] → lecturas/escrituras puntuales contra Jellyfin
 * - [MediaSyncRepository]         → sincronización masiva y progreso
 * - [TmdbRepository]              → TMDB
 * - [TraktRepository]             → Trakt
 * - [MediaAccessPolicy]           → restricciones parentales (IDs permitidos)
 * - [MediaCaches]                 → cachés en memoria (TTL)
 *
 * Es el "composition root" de estos colaboradores: al ser un `@Singleton` de Hilt,
 * compartir la misma instancia de [MediaAccessPolicy] entre los repositorios evita
 * duplicar el fichero de IDs permitidos. Un paso futuro puede promover los
 * colaboradores a providers de Hilt si conviene inyectarlos por separado.
 */
class MediaRepository(
    private val jellyfinDao: JellyfinDao,
    userPreferencesRepository: UserPreferencesRepository? = null,
    tmdbVoteCacheDao: TmdbVoteCacheDao? = null,
    private val allowedMediaDao: AllowedMediaDao? = null,
    private val appContext: Context? = null,
    private val appDatabase: AppDatabase? = null
) {
    private val accessPolicy = MediaAccessPolicy(userPreferencesRepository, allowedMediaDao)
    private val local = LocalMediaRepository(jellyfinDao, accessPolicy)
    private val jellyfinRemote = JellyfinMediaRemoteRepository(jellyfinDao, accessPolicy, local, appContext)
    private val sync = MediaSyncRepository(jellyfinDao, userPreferencesRepository, tmdbVoteCacheDao, accessPolicy, appContext)
    private val tmdb = TmdbRepository(tmdbVoteCacheDao)
    private val trakt = TraktRepository()

    companion object {
        val syncProgress: StateFlow<SyncProgress> = MediaSyncRepository.syncProgress

        fun invalidateServerCaches() = MediaCaches.invalidateServerCaches()

        fun updateProgress(progress: SyncProgress) = MediaSyncRepository.updateProgress(progress)

        fun checkActiveWorkerSync(context: Context, localCount: Int = 0) =
            MediaSyncRepository.checkActiveWorkerSync(context, localCount)
    }

    /**
     * Borra el catálogo local y todas sus cachés cuando el usuario cambia de servidor o de
     * usuario Jellyfin. Sin esto, los títulos, favoritos, progreso y especiales del servidor
     * anterior se mezclaban con los del nuevo.
     */
    suspend fun clearLocalCatalog() {
        withContext(Dispatchers.IO) {
            jellyfinDao.clearAll()
            allowedMediaDao?.clearAll()
            appDatabase?.homeSectionCacheDao()?.clearAll()
            appDatabase?.seasonalDao()?.clearAll()
            accessPolicy.resetCaches()

            appContext?.filesDir?.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("allowed_ids_") ||
                    name == "home_snapshot_v1.dat" ||
                    name == "home_snapshot_v1.dat.tmp"
                ) {
                    file.delete()
                }
            }
            // Las imágenes del servidor anterior no deben ocupar espacio ni mostrarse por error.
            appContext?.cacheDir?.resolve("image_cache")?.deleteRecursively()
            appContext?.let { coil.Coil.imageLoader(it).memoryCache?.clear() }
        }
        MediaCaches.invalidateServerCaches()
    }

    // =========================================================================
    // SINCRONIZACIÓN
    // =========================================================================

    fun startBackgroundOverviewEnrichment(serverUrl: String, userId: String, token: String) =
        sync.startBackgroundOverviewEnrichment(serverUrl, userId, token)

    fun startBackgroundSync(
        serverUrl: String,
        userId: String,
        token: String,
        lastSyncTimestamp: String? = null,
        forceFullSync: Boolean = false,
        context: Context? = null,
        onSyncCompleted: ((String) -> Unit)? = null
    ) = sync.startBackgroundSync(serverUrl, userId, token, lastSyncTimestamp, forceFullSync, context, onSyncCompleted)

    suspend fun syncQuickInit(serverUrl: String, userId: String, token: String): Result<Unit> =
        sync.syncQuickInit(serverUrl, userId, token)

    /** Caché local de portada, en segundo plano y sin bloquear al llamante. */
    fun primeLocalCache(serverUrl: String, userId: String, token: String, force: Boolean = false) =
        sync.primeLocalCache(serverUrl, userId, token, force)

    suspend fun syncJellyfinLibrary(
        serverUrl: String,
        userId: String,
        token: String,
        lastSyncTimestamp: String? = null,
        forceFullSync: Boolean = false,
        onSyncCompleted: (suspend (String) -> Unit)? = null
    ): Result<Unit> = sync.syncJellyfinLibrary(serverUrl, userId, token, lastSyncTimestamp, forceFullSync, onSyncCompleted)

    // =========================================================================
    // JELLYFIN (remoto)
    // =========================================================================

    suspend fun searchAndCache(
        serverUrl: String,
        userId: String,
        token: String,
        title: String,
        year: Int? = null,
        tmdbId: String? = null,
        imdbId: String? = null
    ): JellyfinMediaEntity? = jellyfinRemote.searchAndCache(serverUrl, userId, token, title, year, tmdbId, imdbId)

    suspend fun searchRemoteAndCache(
        serverUrl: String,
        userId: String,
        token: String,
        query: String,
        limit: Int = 30
    ): Result<List<JellyfinMediaEntity>> = jellyfinRemote.searchRemoteAndCache(serverUrl, userId, token, query, limit)

    suspend fun getUserViews(serverUrl: String, userId: String, token: String): Result<List<JellyfinItemDto>> =
        jellyfinRemote.getUserViews(serverUrl, userId, token)

    suspend fun getContinueWatching(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> =
        jellyfinRemote.getContinueWatching(serverUrl, userId, token)

    suspend fun getNextUp(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> =
        jellyfinRemote.getNextUp(serverUrl, userId, token)

    suspend fun getLatest(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> =
        jellyfinRemote.getLatest(serverUrl, userId, token)

    suspend fun getRecentlyPlayed(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> =
        jellyfinRemote.getRecentlyPlayed(serverUrl, userId, token)

    suspend fun getItemDetail(serverUrl: String, userId: String, token: String, itemId: String): Result<JellyfinMediaEntity> {
        if (tv.medusa.app.data.demo.DemoCatalogManager.isDemoServer(serverUrl) || tv.medusa.app.data.demo.DemoCatalogManager.isDemoMediaId(itemId)) {
            val local = jellyfinDao.getItemById(itemId)
            return if (local != null) Result.success(local) else Result.failure(Exception("Item no encontrado"))
        }
        return jellyfinRemote.getItemDetail(serverUrl, userId, token, itemId)
    }

    suspend fun getFavorites(serverUrl: String, userId: String, token: String): Result<List<JellyfinMediaEntity>> =
        jellyfinRemote.getFavorites(serverUrl, userId, token)

    suspend fun toggleFavorite(serverUrl: String, userId: String, token: String, itemId: String, makeFavorite: Boolean): Result<Boolean> {
        if (tv.medusa.app.data.demo.DemoCatalogManager.isDemoServer(serverUrl) || tv.medusa.app.data.demo.DemoCatalogManager.isDemoMediaId(itemId)) {
            jellyfinDao.updateFavoriteStatus(itemId, makeFavorite)
            return Result.success(makeFavorite)
        }
        return jellyfinRemote.toggleFavorite(serverUrl, userId, token, itemId, makeFavorite)
    }

    suspend fun togglePlayed(serverUrl: String, userId: String, token: String, itemId: String, makePlayed: Boolean): Result<Boolean> {
        if (tv.medusa.app.data.demo.DemoCatalogManager.isDemoServer(serverUrl) || tv.medusa.app.data.demo.DemoCatalogManager.isDemoMediaId(itemId)) {
            jellyfinDao.updatePlayedStatus(itemId, makePlayed)
            return Result.success(makePlayed)
        }
        return jellyfinRemote.togglePlayed(serverUrl, userId, token, itemId, makePlayed)
    }

    suspend fun clearPlaybackPosition(serverUrl: String, userId: String, token: String, itemId: String): Result<Unit> =
        jellyfinRemote.clearPlaybackPosition(serverUrl, userId, token, itemId)

    suspend fun getTopRatedMoviesServer(serverUrl: String, userId: String, token: String, limit: Int = 20): List<JellyfinMediaEntity> =
        jellyfinRemote.getTopRatedMoviesServer(serverUrl, userId, token, limit)

    suspend fun getTopRatedSeriesServer(serverUrl: String, userId: String, token: String, limit: Int = 20): List<JellyfinMediaEntity> =
        jellyfinRemote.getTopRatedSeriesServer(serverUrl, userId, token, limit)

    suspend fun getSeasons(serverUrl: String, userId: String, token: String, seriesId: String): List<SeasonItem> {
        if (tv.medusa.app.data.demo.DemoCatalogManager.isDemoServer(serverUrl) || tv.medusa.app.data.demo.DemoCatalogManager.isDemoMediaId(seriesId)) {
            return tv.medusa.app.data.demo.DemoCatalogManager.getDemoSeasons(seriesId)
        }
        return jellyfinRemote.getSeasons(serverUrl, userId, token, seriesId)
    }

    suspend fun getSeriesEpisodeStats(serverUrl: String, userId: String, token: String, seriesId: String): SeriesEpisodeStats? {
        if (tv.medusa.app.data.demo.DemoCatalogManager.isDemoServer(serverUrl) || tv.medusa.app.data.demo.DemoCatalogManager.isDemoMediaId(seriesId)) {
            return SeriesEpisodeStats(totalUniqueEpisodes = 3, playedUniqueEpisodes = 0, unplayedUniqueEpisodes = 3)
        }
        return jellyfinRemote.getSeriesEpisodeStats(serverUrl, userId, token, seriesId)
    }

    suspend fun getCollectionItems(serverUrl: String, userId: String, token: String, collectionId: String): List<JellyfinMediaEntity> =
        jellyfinRemote.getCollectionItems(serverUrl, userId, token, collectionId)

    suspend fun getEpisodes(
        serverUrl: String,
        userId: String,
        token: String,
        seriesId: String,
        seasonId: String? = null
    ): List<EpisodeItem> {
        if (tv.medusa.app.data.demo.DemoCatalogManager.isDemoServer(serverUrl) || tv.medusa.app.data.demo.DemoCatalogManager.isDemoMediaId(seriesId)) {
            return tv.medusa.app.data.demo.DemoCatalogManager.getDemoEpisodes(seriesId, seasonId ?: "")
        }
        return jellyfinRemote.getEpisodes(serverUrl, userId, token, seriesId, seasonId)
    }

    suspend fun getNextUpEpisode(serverUrl: String, userId: String, token: String, seriesId: String): EpisodeItem? {
        if (tv.medusa.app.data.demo.DemoCatalogManager.isDemoServer(serverUrl) || tv.medusa.app.data.demo.DemoCatalogManager.isDemoMediaId(seriesId)) {
            return tv.medusa.app.data.demo.DemoCatalogManager.getDemoEpisodes(seriesId, "").firstOrNull()
        }
        return jellyfinRemote.getNextUpEpisode(serverUrl, userId, token, seriesId)
    }

    // =========================================================================
    // TMDB
    // =========================================================================

    suspend fun getTmdbTrending(apiKey: String): Result<List<TmdbItemDto>> = tmdb.getTmdbTrending(apiKey)

    suspend fun getTmdbTrendingDay(apiKey: String, maxPages: Int = 5): Result<List<TmdbItemDto>> =
        tmdb.getTmdbTrendingDay(apiKey, maxPages)

    suspend fun getTmdbRecommendations(apiKey: String, tmdbId: Long, isTv: Boolean = false): Result<List<TmdbItemDto>> =
        tmdb.getTmdbRecommendations(apiKey, tmdbId, isTv)

    suspend fun getTmdbVoteStats(apiKey: String, tmdbId: Long, isTv: Boolean = false): TmdbVoteStats? =
        tmdb.getTmdbVoteStats(apiKey, tmdbId, isTv)

    suspend fun getTmdbTrailerUrl(apiKey: String, tmdbId: Long, isTv: Boolean = false): String? =
        tmdb.getTmdbTrailerUrl(apiKey, tmdbId, isTv)

    suspend fun getTmdbByGenre(apiKey: String, genreId: String, isTv: Boolean = false): Result<List<TmdbItemDto>> =
        tmdb.getTmdbByGenre(apiKey, genreId, isTv)

    suspend fun getTmdbByProvider(apiKey: String, providerId: String, region: String = tv.medusa.app.data.remote.ApiConfig.TMDB_WATCH_REGION, maxPages: Int = 4): Result<List<TmdbItemDto>> =
        tmdb.getTmdbByProvider(apiKey, providerId, region, maxPages)

    suspend fun getTmdbMovieDetail(apiKey: String, movieId: Long): TmdbMovieDetailDto? =
        tmdb.getTmdbMovieDetail(apiKey, movieId)

    suspend fun getTmdbTvDetail(apiKey: String, seriesId: Long): TmdbTvDetailDto? =
        tmdb.getTmdbTvDetail(apiKey, seriesId)

    suspend fun getTmdbTopRated(apiKey: String, maxPages: Int = 3): Result<List<TmdbItemDto>> = tmdb.getTmdbTopRated(apiKey, maxPages)

    suspend fun getSeriesStatus(tmdbApiKey: String, tmdbId: Long?, jellyfinStatus: String?): SeriesStatus =
        tmdb.getSeriesStatus(tmdbApiKey, tmdbId, jellyfinStatus)

    suspend fun getTmdbSeasonalDiscover(
        apiKey: String,
        withKeywords: String? = null,
        withGenres: String? = null,
        withoutGenres: String? = null,
        maxPages: Int = 3
    ): Result<List<TmdbItemDto>> = tmdb.getTmdbSeasonalDiscover(apiKey, withKeywords, withGenres, withoutGenres, maxPages)

    // =========================================================================
    // TRAKT
    // =========================================================================

    suspend fun getTraktRecommendations(traktToken: String, clientId: String): Result<List<TraktMediaDto>> =
        trakt.getTraktRecommendations(traktToken, clientId)

    suspend fun getTraktTrending(clientId: String): Result<List<TraktMediaDto>> = trakt.getTraktTrending(clientId)

    suspend fun getTraktWatchlist(traktToken: String, clientId: String): Result<List<TraktMediaDto>> =
        trakt.getTraktWatchlist(traktToken, clientId)

    suspend fun getTraktUserProfile(traktToken: String, clientId: String): Result<TraktUserDto> =
        trakt.getTraktUserProfile(traktToken, clientId)

    suspend fun getTraktUserLists(traktToken: String, clientId: String): Result<List<TraktUserListDto>> =
        trakt.getTraktUserLists(traktToken, clientId)

    suspend fun getTraktCustomListItems(
        traktToken: String,
        clientId: String,
        listId: String,
        username: String? = null
    ): Result<List<TraktMediaDto>> = trakt.getTraktCustomListItems(traktToken, clientId, listId, username)

    // =========================================================================
    // CATÁLOGO LOCAL (Room)
    // =========================================================================

    fun getAllLocalLibrary(): Flow<List<JellyfinMediaEntity>> = local.getAllLocalLibrary()

    suspend fun getLocalMovies(): List<JellyfinMediaEntity> = local.getLocalMovies()

    suspend fun getLocalSeries(): List<JellyfinMediaEntity> = local.getLocalSeries()

    suspend fun getAllCatalog(): List<JellyfinMediaEntity> = local.getAllCatalog()

    /** Proyección ligera para índices de eventos; no usar para pintar filas completas. */
    suspend fun getCatalogLight(): List<JellyfinMediaEntity> = local.getCatalogLight()

    suspend fun hasRealRestrictions(): Boolean = local.hasRealRestrictions()

    suspend fun getSeasonalCandidates(query: androidx.sqlite.db.SupportSQLiteQuery): List<JellyfinMediaEntity> =
        local.getSeasonalCandidates(query)

    suspend fun getItemById(id: String): JellyfinMediaEntity? = local.getItemById(id)

    suspend fun getItemsByIds(ids: List<String>): List<JellyfinMediaEntity> = local.getItemsByIds(ids)

    suspend fun getContinueWatchingLocal(limit: Int = 20): List<JellyfinMediaEntity> = local.getContinueWatchingLocal(limit)

    suspend fun getRecentlyPlayedLocal(limit: Int = 20): List<JellyfinMediaEntity> = local.getRecentlyPlayedLocal(limit)

    suspend fun getLatestLocal(serverUrl: String = "", userId: String = "", limit: Int = 20): List<JellyfinMediaEntity> =
        local.getLatestLocal(serverUrl, userId, limit)

    suspend fun getNewReleasesLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        local.getNewReleasesLocal(limit)

    suspend fun getTopMoviesLocal(limit: Int = 20): List<JellyfinMediaEntity> = local.getTopMoviesLocal(limit)

    suspend fun getTopSeriesLocal(limit: Int = 20): List<JellyfinMediaEntity> = local.getTopSeriesLocal(limit)

    suspend fun getTopRatedLocal(limit: Int = 20): List<JellyfinMediaEntity> = local.getTopRatedLocal(limit)
    suspend fun getRewatchableMoviesLocal(limit: Int = 20): List<JellyfinMediaEntity> = local.getRewatchableMoviesLocal(limit)
    suspend fun getBoxSetsLocal(limit: Int = 20): List<JellyfinMediaEntity> = local.getBoxSetsLocal(limit)
    suspend fun getDocumentariesLocal(limit: Int = 20): List<JellyfinMediaEntity> = local.getDocumentariesLocal(limit)
    suspend fun getNationalCinemaLocal(pattern: String, limit: Int = 20): List<JellyfinMediaEntity> = local.getNationalCinemaLocal(pattern, limit)
    suspend fun getSpanishCinemaLocal(limit: Int = 20): List<JellyfinMediaEntity> = local.getSpanishCinemaLocal(limit)

    suspend fun searchLocalMedia(query: String, limit: Int = 40): List<JellyfinMediaEntity> = local.searchLocalMedia(query, limit)

    suspend fun getAllLocalTitles(): List<String> = local.getAllLocalTitles()

    suspend fun getLocalCount(): Int = local.getLocalCount()

    fun getLibraryStatsFlow(): Flow<LibraryStats> = local.getLibraryStatsFlow()

    suspend fun getFavoritesLocal(): List<JellyfinMediaEntity> = local.getFavoritesLocal()

    fun getFavoritesFlow(): Flow<List<JellyfinMediaEntity>> = local.getFavoritesFlow()

    suspend fun findLocal(tmdbId: String?, imdbId: String?, title: String? = null, year: Int? = null): JellyfinMediaEntity? =
        local.findLocal(tmdbId, imdbId, title, year)

    suspend fun getMostWatchedGenres(): List<String> = local.getMostWatchedGenres()

    suspend fun getItemsByGenre(genre: String): List<JellyfinMediaEntity> = local.getItemsByGenre(genre)

    suspend fun getMoviesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity> = local.getMoviesByGenre(genre, limit)

    suspend fun getSeriesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity> = local.getSeriesByGenre(genre, limit)

    suspend fun getRecentMoviesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity> = local.getRecentMoviesByGenre(genre, limit)

    suspend fun getRecentSeriesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity> = local.getRecentSeriesByGenre(genre, limit)

    suspend fun getRecommendedMovies(
        excludeIds: Set<String>,
        limit: Int = 20,
        genres: List<String>? = null
    ): List<JellyfinMediaEntity> = local.getRecommendedMovies(excludeIds, limit, genres)
}
