package tv.medusa.app.data.repository

import tv.medusa.app.data.local.UserPreferencesRepository
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withPermit

/**
 * Acceso a TMDB (tendencias, descubrimiento por género/proveedor, recomendaciones,
 * detalles, vídeos y estado de emisión).
 *
 * Extraído de `MediaRepository`. Los votos se cachean en Room 24h para evitar el N+1.
 */
class TmdbRepository(
    private val tmdbVoteCacheDao: TmdbVoteCacheDao? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) {
    /**
     * El plugin del servidor inyecta la clave TMDB, así que las peticiones al proxy se hacen sin
     * `api_key` y con el token Jellyfin. Si no hay plugin o el usuario escribió su propia clave,
     * se mantiene el acceso directo a TMDB (fail-open).
     */
    private fun useServerProxy(): Boolean {
        val prefs = userPreferencesRepository?.userPreferences?.value ?: return false
        return prefs.tmdbUseServer &&
            prefs.jellyfinServerUrl.isNotBlank() &&
            prefs.jellyfinAccessToken.isNotBlank()
    }

    private fun effectiveApiKey(apiKey: String): String = if (useServerProxy()) "" else apiKey

    private fun tmdbService(): TmdbApiService {
        if (!useServerProxy()) {
            return NetworkClientFactory.createService(ApiConfig.TMDB_BASE_URL, TmdbApiService::class.java)
        }
        val prefs = requireNotNull(userPreferencesRepository?.userPreferences?.value)
        val proxyUrl = "${NetworkClientFactory.normalizeUrl(prefs.jellyfinServerUrl)}/Medusa/Tmdb/"
        return NetworkClientFactory.createAuthenticatedService(
            baseUrl = proxyUrl,
            serviceClass = TmdbApiService::class.java,
            authHeader = buildJellyfinAuthHeader(token = prefs.jellyfinAccessToken)
        )
    }

    suspend fun getTmdbTrending(apiKey: String): Result<List<TmdbItemDto>> {
        MediaCaches.tmdbTrendingCache.get(apiKey)?.let { return Result.success(it) }
        return runCatching {
            val api = tmdbService()
            val response = api.getTrending(apiKey = effectiveApiKey(apiKey))
            if (response.results.isNotEmpty()) {
                MediaCaches.tmdbTrendingCache.put(apiKey, response.results)
            }
            response.results
        }
    }

    suspend fun getTmdbTrendingDay(apiKey: String, maxPages: Int = 3): Result<List<TmdbItemDto>> {
        val cacheKey = "trending-day-es-$apiKey-$maxPages"
        MediaCaches.tmdbTrendingDayCache.get(cacheKey)?.let { return Result.success(it) }
        return runCatching {
            val api = tmdbService()
            val (moviePages, tvPages) = coroutineScope {
                val m = async {
                    (1..maxPages).flatMap { p ->
                        runCatching { api.getTrendingMoviesDay(apiKey = effectiveApiKey(apiKey), page = p, language = "es-ES") }.getOrNull()?.results ?: emptyList()
                    }
                }
                val t = async {
                    (1..maxPages).flatMap { p ->
                        runCatching { api.getTrendingTvDay(apiKey = effectiveApiKey(apiKey), page = p, language = "es-ES") }.getOrNull()?.results ?: emptyList()
                    }
                }
                Pair(m.await(), t.await())
            }

            val interleaved = mutableListOf<TmdbItemDto>()
            val maxLen = maxOf(moviePages.size, tvPages.size)
            for (i in 0 until maxLen) {
                if (i < moviePages.size) interleaved.add(moviePages[i])
                if (i < tvPages.size) interleaved.add(tvPages[i])
            }
            val distinct = interleaved.distinctBy { it.typeAwareKey() }
            if (distinct.isNotEmpty()) {
                MediaCaches.tmdbTrendingDayCache.put(cacheKey, distinct)
            }
            distinct
        }
    }

    suspend fun getTmdbRecommendations(apiKey: String, tmdbId: Long, isTv: Boolean = false): Result<List<TmdbItemDto>> {
        val cacheKey = "$tmdbId-$isTv"
        MediaCaches.tmdbRecommendationsCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = tmdbService()
            val results = if (isTv) {
                api.getTvRecommendations(seriesId = tmdbId, apiKey = effectiveApiKey(apiKey)).results
            } else {
                api.getMovieRecommendations(movieId = tmdbId, apiKey = effectiveApiKey(apiKey)).results
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

        // 2. Cache miss o expirado → ir a red, pero agrupando los N+1 concurrentes
        //    (200 tarjetas pidiendo el mismo TMDB id al arrancar) en una sola llamada.
        val fetched: Result<TmdbVoteStats?> = MediaCaches.remoteFetchFlight.run("vote|$isTv|$tmdbId") {
            runCatching {
                val api = tmdbService()
                val stats = if (isTv) {
                    val d = api.getTvDetails(seriesId = tmdbId, apiKey = effectiveApiKey(apiKey))
                    TmdbVoteStats(voteCount = d.voteCount ?: 0, voteAverage = d.voteAverage ?: 0f)
                } else {
                    val d = api.getMovieDetails(movieId = tmdbId, apiKey = effectiveApiKey(apiKey))
                    TmdbVoteStats(voteCount = d.voteCount ?: 0, voteAverage = d.voteAverage ?: 0f)
                }
                tmdbVoteCacheDao?.upsert(
                    TmdbVoteCacheEntity(
                        tmdbId = tmdbId,
                        isTv = isTv,
                        voteAverage = stats.voteAverage,
                        voteCount = stats.voteCount
                    )
                )
                stats
            }
        }
        return fetched.getOrNull()
    }

    suspend fun getTmdbTrailerUrl(apiKey: String, tmdbId: Long, isTv: Boolean = false): String? {
        return runCatching {
            val api = tmdbService()
            val response = if (isTv) {
                api.getTvVideos(seriesId = tmdbId, apiKey = effectiveApiKey(apiKey))
            } else {
                api.getMovieVideos(movieId = tmdbId, apiKey = effectiveApiKey(apiKey))
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
            val api = tmdbService()
            val results = if (isTv) {
                api.discoverTvByGenre(apiKey = effectiveApiKey(apiKey), genreId = genreId).results
            } else {
                api.discoverMoviesByGenre(apiKey = effectiveApiKey(apiKey), genreId = genreId).results
            }
            if (results.isNotEmpty()) {
                MediaCaches.tmdbGenreCache.put(cacheKey, results)
            }
            results
        }
    }

    suspend fun getTmdbByProvider(apiKey: String, providerId: String, region: String = ApiConfig.TMDB_WATCH_REGION, maxPages: Int = 4): Result<List<TmdbItemDto>> {
        val cacheKey = "$providerId-$region-$maxPages"
        MediaCaches.tmdbProviderCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = tmdbService()
            val (movies, tv) = coroutineScope {
                val m = async {
                    (1..maxPages).map { p ->
                        async {
                            runCatching {
                                api.discoverMoviesByProvider(apiKey = effectiveApiKey(apiKey), providerId = providerId, watchRegion = region, page = p)
                            }.getOrNull()?.results ?: emptyList()
                        }
                    }.awaitAll().flatten()
                }
                val t = async {
                    (1..maxPages).map { p ->
                        async {
                            runCatching {
                                api.discoverTvByProvider(apiKey = effectiveApiKey(apiKey), providerId = providerId, watchRegion = region, page = p)
                            }.getOrNull()?.results ?: emptyList()
                        }
                    }.awaitAll().flatten()
                }
                Pair(m.await(), t.await())
            }
            val combined = mergeTmdbByPopularity(movies, tv)
            if (combined.isNotEmpty()) {
                MediaCaches.tmdbProviderCache.put(cacheKey, combined)
            }
            combined
        }
    }

    /**
     * Catálogo COMPLETO de un proveedor en la región: descubre `total_pages` con la primera
     * página (películas y series) y pide el resto con concurrencia acotada. Es la fuente del
     * índice de plataformas, así que no lleva tope fijo de páginas: se adapta a cualquier
     * catálogo (Netflix ~500 páginas, Filmin ~40, una plataforma regional puede tener 3).
     */
    suspend fun getTmdbProviderCatalog(
        apiKey: String,
        providerId: String,
        region: String = ApiConfig.TMDB_WATCH_REGION
    ): Result<List<TmdbItemDto>> {
        return runCatching {
            val api = tmdbService()

            val first = coroutineScope {
                val m = async {
                    runCatching {
                        api.discoverMoviesByProvider(apiKey = effectiveApiKey(apiKey), providerId = providerId, watchRegion = region, page = 1)
                    }.getOrNull()
                }
                val t = async {
                    runCatching {
                        api.discoverTvByProvider(apiKey = effectiveApiKey(apiKey), providerId = providerId, watchRegion = region, page = 1)
                    }.getOrNull()
                }
                Pair(m.await(), t.await())
            }

            val moviePages = first.first?.totalPages ?: 0
            val tvPages = first.second?.totalPages ?: 0

            val all = mutableListOf<TmdbItemDto>()
            first.first?.results?.let { all.addAll(it) }
            first.second?.results?.let { all.addAll(it) }

            val tasks = buildList {
                for (page in 2..moviePages) add("movie" to page)
                for (page in 2..tvPages) add("tv" to page)
            }
            if (tasks.isNotEmpty()) {
                val semaphore = kotlinx.coroutines.sync.Semaphore(6)
                val rest = coroutineScope {
                    tasks.map { (kind, page) ->
                        async {
                            semaphore.withPermit {
                                runCatching {
                                    if (kind == "movie") {
                                        api.discoverMoviesByProvider(apiKey = effectiveApiKey(apiKey), providerId = providerId, watchRegion = region, page = page).results
                                    } else {
                                        api.discoverTvByProvider(apiKey = effectiveApiKey(apiKey), providerId = providerId, watchRegion = region, page = page).results
                                    }
                                }.getOrDefault(emptyList())
                            }
                        }
                    }.awaitAll()
                }
                rest.forEach { all.addAll(it) }
            }

            val distinct = all.distinctBy { it.typeAwareKey() }
            // No se guarda en la caché en memoria: son ~10k DTOs por plataforma y el índice
            // persistente (Room) ya es la caché. Así no se hincha la RAM de la TV.
            distinct
        }
    }

    /**
     * "Lo más popular en tu país" como respaldo del Top 10 cuando el trending global no
     * cruza con la biblioteca (catálogos locales/regionales). Usa `discover` con
     * `watch_region` y ordena por popularidad, que es el equivalente más cercano a
     * "lo más visto de la semana" sin depender de un proveedor concreto.
     */
    suspend fun getTmdbPopularByRegion(
        apiKey: String,
        region: String = ApiConfig.TMDB_WATCH_REGION,
        maxPages: Int = 4
    ): Result<List<TmdbItemDto>> {
        val cacheKey = "$region-$maxPages-$apiKey"
        MediaCaches.tmdbPopularRegionCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = tmdbService()
            val (movies, tv) = coroutineScope {
                val m = async {
                    (1..maxPages).flatMap { p ->
                        runCatching {
                            api.discoverPopularMovies(apiKey = effectiveApiKey(apiKey), watchRegion = region, page = p)
                        }.getOrNull()?.results ?: emptyList()
                    }
                }
                val t = async {
                    (1..maxPages).flatMap { p ->
                        runCatching {
                            api.discoverPopularTv(apiKey = effectiveApiKey(apiKey), watchRegion = region, page = p)
                        }.getOrNull()?.results ?: emptyList()
                    }
                }
                Pair(m.await(), t.await())
            }
            val combined = mergeTmdbByPopularity(movies, tv)
            if (combined.isNotEmpty()) {
                MediaCaches.tmdbPopularRegionCache.put(cacheKey, combined)
            }
            combined
        }
    }

    suspend fun getTmdbMovieDetail(apiKey: String, movieId: Long): TmdbMovieDetailDto? {
        return runCatching {
            val api = tmdbService()
            api.getMovieDetails(movieId = movieId, apiKey = effectiveApiKey(apiKey))
        }.getOrNull()
    }

    suspend fun getTmdbTvDetail(apiKey: String, seriesId: Long): TmdbTvDetailDto? {
        return runCatching {
            val api = tmdbService()
            api.getTvDetails(seriesId = seriesId, apiKey = effectiveApiKey(apiKey))
        }.getOrNull()
    }

    suspend fun getTmdbTopRated(apiKey: String, maxPages: Int = 3): Result<List<TmdbItemDto>> {
        val cacheKey = "$apiKey-$maxPages"
        MediaCaches.tmdbTopRatedCache.get(cacheKey)?.let { return Result.success(it) }

        return runCatching {
            val api = tmdbService()
            val (movies, tv) = coroutineScope {
                val m = async {
                    (1..maxPages).map { p ->
                        async { runCatching { api.getTopRatedMovies(apiKey = effectiveApiKey(apiKey), page = p) }.getOrNull()?.results ?: emptyList() }
                    }.awaitAll().flatten()
                }
                val t = async {
                    (1..maxPages).map { p ->
                        async { runCatching { api.getTopRatedTv(apiKey = effectiveApiKey(apiKey), page = p) }.getOrNull()?.results ?: emptyList() }
                    }.awaitAll().flatten()
                }
                Pair(m.await(), t.await())
            }
            val combined = (movies + tv).distinctBy { it.id }
            if (combined.isNotEmpty()) {
                MediaCaches.tmdbTopRatedCache.put(cacheKey, combined)
            }
            combined
        }
    }

    suspend fun getSeriesStatus(
        tmdbApiKey: String,
        tmdbId: Long?,
        jellyfinStatus: String?
    ): SeriesStatus {
        if (tmdbId != null && (tmdbApiKey.isNotBlank() || useServerProxy())) {
            val tmdbStatus = runCatching {
                val tmdbApi = tmdbService()
                val detail = tmdbApi.getTvDetails(seriesId = tmdbId, apiKey = effectiveApiKey(tmdbApiKey))
                detail.status
            }.getOrNull()
            if (!tmdbStatus.isNullOrBlank()) {
                return SeriesStatus.from(tmdbStatus = tmdbStatus, jellyfinStatus = jellyfinStatus)
            }
        }
        return SeriesStatus.from(tmdbStatus = null, jellyfinStatus = jellyfinStatus)
    }

    suspend fun getTmdbSeasonalDiscover(
        apiKey: String,
        withKeywords: String? = null,
        withGenres: String? = null,
        withoutGenres: String? = null,
        maxPages: Int = 3
    ): Result<List<TmdbItemDto>> {
        return runCatching {
            val api = tmdbService()
            val pages = coroutineScope {
                (1..maxPages).map { p ->
                    async {
                        runCatching {
                            api.discoverSeasonalMovies(
                                apiKey = effectiveApiKey(apiKey),
                                withKeywords = withKeywords,
                                withGenres = withGenres,
                                withoutGenres = withoutGenres,
                                page = p
                            )
                        }.getOrNull()?.results ?: emptyList()
                    }
                }.awaitAll()
            }
            pages.flatten().distinctBy { it.id }
        }
    }
}

data class TmdbVoteStats(val voteCount: Int, val voteAverage: Float)

/**
 * Fusiona los resultados de películas y series de `discover` en **un único ranking ordenado por
 * popularidad de TMDB**. Es lo que hace que "Populares en Netflix" sea de verdad una lista
 * ordenada de la plataforma y no "todas las pelis y después todas las series".
 *
 * El desempate por popularidad deja como representante la coincidencia con mayor popularidad
 * cuando un mismo título llega duplicado.
 */
internal fun mergeTmdbByPopularity(
    movies: List<TmdbItemDto>,
    tv: List<TmdbItemDto>
): List<TmdbItemDto> =
    (movies + tv)
        .sortedByDescending { it.popularity ?: 0.0 }
        .distinctBy { it.typeAwareKey() }

/**
 * Clave de identidad que respeta el espacio de nombres de TMDB: un id numérico puede existir
 * a la vez como película y como serie. Deduplicar solo por `id` haría desaparecer una de las
 * dos (p. ej. "Resident Evil" película 2026 vs. serie 2022).
 */
private fun TmdbItemDto.typeAwareKey(): String {
    val isTv = !name.isNullOrBlank() || !firstAirDate.isNullOrBlank()
    return "${if (isTv) "tv" else "movie"}:$id"
}
