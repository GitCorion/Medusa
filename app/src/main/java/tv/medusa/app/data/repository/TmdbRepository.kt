package tv.medusa.app.data.repository

import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.data.local.db.TmdbVoteCacheDao
import tv.medusa.app.data.local.db.TmdbVoteCacheEntity
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.tmdb.TmdbApiService
import tv.medusa.app.data.remote.tmdb.TmdbItemDto
import tv.medusa.app.data.remote.tmdb.TmdbMovieDetailDto
import tv.medusa.app.data.remote.tmdb.TmdbTvDetailDto
import tv.medusa.app.domain.model.SeriesStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Acceso a TMDB (tendencias, descubrimiento por género/proveedor, recomendaciones,
 * detalles, vídeos y estado de emisión).
 *
 * Extraído de `MediaRepository`. Los votos se cachean en Room 24h para evitar el N+1.
 */
class TmdbRepository(
    private val tmdbVoteCacheDao: TmdbVoteCacheDao? = null
) {
    suspend fun getTmdbTrending(apiKey: String): Result<List<TmdbItemDto>> {
        MediaCaches.tmdbTrendingCache.get(apiKey)?.let { return Result.success(it) }
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            val response = api.getTrending(apiKey = apiKey)
            if (response.results.isNotEmpty()) {
                MediaCaches.tmdbTrendingCache.put(apiKey, response.results)
            }
            response.results
        }
    }

    suspend fun getTmdbTrendingDay(apiKey: String): Result<List<TmdbItemDto>> {
        MediaCaches.tmdbTrendingDayCache.get(apiKey)?.let { return Result.success(it) }
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            val response = api.getTrendingDay(apiKey = apiKey)
            if (response.results.isNotEmpty()) {
                MediaCaches.tmdbTrendingDayCache.put(apiKey, response.results)
            }
            response.results
        }
    }

    suspend fun getTmdbRecommendations(apiKey: String, tmdbId: Long, isTv: Boolean = false): Result<List<TmdbItemDto>> {
        val cacheKey = "$tmdbId-$isTv"
        MediaCaches.tmdbRecommendationsCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            val results = if (isTv) {
                api.getTvRecommendations(seriesId = tmdbId, apiKey = apiKey).results
            } else {
                api.getMovieRecommendations(movieId = tmdbId, apiKey = apiKey).results
            }
            if (results.isNotEmpty()) {
                MediaCaches.tmdbRecommendationsCache.put(cacheKey, results)
            }
            results
        }
    }

    /**
     * Devuelve stats de votación de TMDB con caché de 24h.
     *
     * Antes: una request HTTP por cada película/serie (N+1, hasta 200 requests/sesión).
     * Ahora: SQLite lookup en ~0.1ms si el dato es fresco; HTTP solo si está expirado.
     */
    suspend fun getTmdbVoteStats(apiKey: String, tmdbId: Long, isTv: Boolean = false): TmdbVoteStats? {
        // 1. Intentar desde caché local (0.1ms)
        val cached = tmdbVoteCacheDao?.get(tmdbId, isTv)
        if (cached != null && !cached.isExpired()) {
            return TmdbVoteStats(voteCount = cached.voteCount, voteAverage = cached.voteAverage)
        }

        // 2. Cache miss o expirado → ir a red
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            val stats = if (isTv) {
                val d = api.getTvDetails(seriesId = tmdbId, apiKey = apiKey)
                TmdbVoteStats(voteCount = d.voteCount ?: 0, voteAverage = d.voteAverage ?: 0f)
            } else {
                val d = api.getMovieDetails(movieId = tmdbId, apiKey = apiKey)
                TmdbVoteStats(voteCount = d.voteCount ?: 0, voteAverage = d.voteAverage ?: 0f)
            }
            // Guardar en caché para las próximas 24h
            tmdbVoteCacheDao?.upsert(
                TmdbVoteCacheEntity(
                    tmdbId = tmdbId,
                    isTv = isTv,
                    voteAverage = stats.voteAverage,
                    voteCount = stats.voteCount
                )
            )
            stats
        }.getOrNull()
    }

    suspend fun getTmdbTrailerUrl(apiKey: String, tmdbId: Long, isTv: Boolean = false): String? {
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            val response = if (isTv) {
                api.getTvVideos(seriesId = tmdbId, apiKey = apiKey)
            } else {
                api.getMovieVideos(movieId = tmdbId, apiKey = apiKey)
            }
            val trailer = response.results.firstOrNull { it.type.equals("Trailer", ignoreCase = true) && it.site.equals("YouTube", ignoreCase = true) }
                ?: response.results.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }
            trailer?.key?.let { key -> "${ApiConfig.YOUTUBE_WATCH_URL}$key" }
        }.getOrNull()
    }

    suspend fun getTmdbByGenre(apiKey: String, genreId: String, isTv: Boolean = false): Result<List<TmdbItemDto>> {
        val cacheKey = "$genreId-$isTv"
        MediaCaches.tmdbGenreCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            val results = if (isTv) {
                api.discoverTvByGenre(apiKey = apiKey, genreId = genreId).results
            } else {
                api.discoverMoviesByGenre(apiKey = apiKey, genreId = genreId).results
            }
            if (results.isNotEmpty()) {
                MediaCaches.tmdbGenreCache.put(cacheKey, results)
            }
            results
        }
    }

    suspend fun getTmdbByProvider(apiKey: String, providerId: String, region: String = ApiConfig.TMDB_WATCH_REGION): Result<List<TmdbItemDto>> {
        val cacheKey = "$providerId-$region"
        MediaCaches.tmdbProviderCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            val (movies, tv) = coroutineScope {
                val m = async {
                    runCatching { api.discoverMoviesByProvider(apiKey = apiKey, providerId = providerId, watchRegion = region) }
                        .getOrNull()?.results ?: emptyList()
                }
                val t = async {
                    runCatching { api.discoverTvByProvider(apiKey = apiKey, providerId = providerId, watchRegion = region) }
                        .getOrNull()?.results ?: emptyList()
                }
                Pair(m.await(), t.await())
            }
            val combined = movies + tv
            if (combined.isNotEmpty()) {
                MediaCaches.tmdbProviderCache.put(cacheKey, combined)
            }
            combined
        }
    }

    suspend fun getTmdbMovieDetail(apiKey: String, movieId: Long): TmdbMovieDetailDto? {
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            api.getMovieDetails(movieId = movieId, apiKey = apiKey)
        }.getOrNull()
    }

    suspend fun getTmdbTvDetail(apiKey: String, seriesId: Long): TmdbTvDetailDto? {
        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            api.getTvDetails(seriesId = seriesId, apiKey = apiKey)
        }.getOrNull()
    }

    suspend fun getTmdbTopRated(apiKey: String): Result<List<TmdbItemDto>> {
        MediaCaches.tmdbTopRatedCache.get(apiKey)?.let { return Result.success(it) }

        return runCatching {
            val api = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
            val (movies, tv) = coroutineScope {
                val m = async {
                    runCatching { api.getTopRatedMovies(apiKey = apiKey) }.getOrNull()?.results ?: emptyList()
                }
                val t = async {
                    runCatching { api.getTopRatedTv(apiKey = apiKey) }.getOrNull()?.results ?: emptyList()
                }
                Pair(m.await(), t.await())
            }
            val combined = movies + tv
            if (combined.isNotEmpty()) {
                MediaCaches.tmdbTopRatedCache.put(apiKey, combined)
            }
            combined
        }
    }

    suspend fun getSeriesStatus(
        tmdbApiKey: String,
        tmdbId: Long?,
        jellyfinStatus: String?
    ): SeriesStatus {
        if (tmdbId != null && tmdbApiKey.isNotBlank()) {
            val tmdbStatus = runCatching {
                val tmdbApi = NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
                val detail = tmdbApi.getTvDetails(seriesId = tmdbId, apiKey = tmdbApiKey)
                detail.status
            }.getOrNull()
            if (!tmdbStatus.isNullOrBlank()) {
                return SeriesStatus.from(tmdbStatus = tmdbStatus, jellyfinStatus = jellyfinStatus)
            }
        }
        return SeriesStatus.from(tmdbStatus = null, jellyfinStatus = jellyfinStatus)
    }
}

data class TmdbVoteStats(val voteCount: Int, val voteAverage: Float)
