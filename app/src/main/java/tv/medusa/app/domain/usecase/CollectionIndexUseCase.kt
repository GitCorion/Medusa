package tv.medusa.app.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.medusa.app.data.local.DEFAULT_TMDB_API_KEY
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.CollectionDao
import tv.medusa.app.data.local.db.CollectionItemEntity
import tv.medusa.app.data.local.db.CollectionMetaEntity
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.model.SUPPORTED_PLATFORMS
import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.data.remote.catalog.CatalogRemoteDataSource
import tv.medusa.app.data.remote.tmdb.TmdbItemDto
import tv.medusa.app.data.repository.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indexador genérico de colecciones de descubrimiento.
 *
 * Para cada colección externa (plataforma, trending, popular regional) pide el catálogo
 * COMPLETO a TMDB, lo cruza por lotes contra la biblioteca local y guarda solo la pertenencia
 * + clave de orden en `collection_items`. La app no conoce plataformas concretas: itera
 * `SUPPORTED_PLATFORMS` y usa la región del usuario, así que funciona con cualquier catálogo
 * (50 o 500.000 títulos) y cualquier país.
 *
 * Se refresca a diario (worker) y se invalida solo cuando cambia servidor, sync o región.
 */
@Singleton
class CollectionIndexUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase,
    private val collectionDao: CollectionDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val catalogRemote: CatalogRemoteDataSource
) {

    companion object {
        const val PLATFORM_PREFIX = "platform:"
        const val TRENDING_PREFIX = "trending:day:"
        const val POPULAR_PREFIX = "popular:"
        private const val TTL_MS = 24L * 60 * 60 * 1000
        // Súbela cuando cambie el algoritmo de matching/orden: invalida los índices existentes.
        private const val INDEX_VERSION = 4
        private val INDEX_MUTEX = Mutex()

        fun platformKey(platformId: String): String = PLATFORM_PREFIX + platformId.lowercase()
        fun trendingKey(region: String): String = TRENDING_PREFIX + region.uppercase()
        fun popularKey(region: String): String = POPULAR_PREFIX + region.uppercase()
    }

    suspend fun ensureIndex(force: Boolean = false, onlyPlatformId: String? = null): Int =
        INDEX_MUTEX.withLock {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (prefs.jellyfinServerUrl.isBlank() || prefs.jellyfinAccessToken.isBlank()) return 0

            val apiKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY

            val region = (prefs.watchRegion.takeIf { it.isNotBlank() }
                ?: ApiConfig.TMDB_WATCH_REGION).uppercase()

            // 1. Catálogo centralizado en Firebase Hosting. Si está disponible, el dispositivo
            //    no llama a TMDB: descarga el fichero ya preparado y solo cruza con su biblioteca.
            val manifest = catalogRemote.fetchManifest(region)
            if (apiKey.isBlank() && manifest == null) return 0

            val version = "v$INDEX_VERSION|remote:${manifest?.updatedAt ?: 0L}|" +
                "${prefs.jellyfinServerUrl}|${prefs.jellyfinLastSync}|" +
                "${mediaRepository.getLocalCount()}|$region"

            var built = 0

            // 2. Tendencias y popular regional: alimentan el Top 10 y la cabecera.
            built += ensureCollection(
                key = trendingKey(region),
                region = region,
                version = version,
                force = force,
                ranked = true,
                remotePath = manifest?.trendingDay
            ) {
                mediaRepository.getTmdbTrendingDay(apiKey, maxPages = 10).getOrDefault(emptyList())
            }
            built += ensureCollection(
                key = popularKey(region),
                region = region,
                version = version,
                force = force,
                ranked = true,
                remotePath = manifest?.popular
            ) {
                mediaRepository.getTmdbPopularByRegion(apiKey, region, maxPages = 10)
                    .getOrDefault(emptyList())
            }

            // 3. Plataformas: catálogo COMPLETO de cada una. Sin límite fijo de páginas.
            val selected = prefs.selectedPlatforms
            val platforms = SUPPORTED_PLATFORMS
                .filter { it.id != "trakt" && it.providerId.isNotBlank() }
                .filter { selected.isEmpty() || it.id in selected }
                .filter { it.isAvailableIn(region) }
                .filter { onlyPlatformId == null || it.id.equals(onlyPlatformId, ignoreCase = true) }

            for (platform in platforms) {
                built += ensureCollection(
                    key = platformKey(platform.id),
                    region = region,
                    version = version,
                    force = force,
                    ranked = true,
                    remotePath = manifest?.platforms?.get(platform.id)
                ) {
                    mediaRepository.getTmdbProviderCatalog(apiKey, platform.providerId, region)
                        .getOrDefault(emptyList())
                }
            }

            built
        }

    private suspend fun ensureCollection(
        key: String,
        region: String,
        version: String,
        force: Boolean,
        ranked: Boolean,
        remotePath: String? = null,
        fetch: suspend () -> List<TmdbItemDto>
    ): Int {
        val meta = collectionDao.getMeta(key)
        val now = System.currentTimeMillis()
        if (!force && meta != null && meta.catalogVersion == version && now - meta.updatedAt < TTL_MS) {
            return meta.itemCount
        }

        // Preferimos el catálogo centralizado (1 descarga) y dejamos TMDB directo como fallback.
        val remoteItems = remotePath?.let {
            runCatching { catalogRemote.fetchItems(region, it) }.getOrNull()
        }
        val items = remoteItems?.takeIf { it.isNotEmpty() } ?: fetch()
        // Un fallo de red no debe borrar un índice que ya era válido.
        if (items.isEmpty()) return meta?.itemCount ?: 0

        val matches = filterToLibraryUseCase.filterTmdbItemsWithSource(
            tmdbItems = items,
            maxCandidates = Int.MAX_VALUE
        )
        if (matches.isEmpty() && meta != null) return meta.itemCount

        val entities = matches.map { match ->
            CollectionItemEntity(
                collectionKey = key,
                mediaId = match.entity.id,
                rank = if (ranked) match.position + 1 else null,
                score = match.source.popularity ?: 0.0
            )
        }
        collectionDao.replaceCollection(
            key = key,
            items = entities,
            meta = CollectionMetaEntity(
                collectionKey = key,
                region = region,
                catalogVersion = version,
                itemCount = entities.size
            )
        )
        return entities.size
    }

    suspend fun count(key: String): Int = collectionDao.count(key)

    suspend fun countJoined(key: String, type: String? = null, hideWatched: Boolean = false): Int =
        collectionDao.countJoined(key, type, hideWatched)

    suspend fun top(
        key: String,
        limit: Int,
        type: String? = null,
        hideWatched: Boolean = false
    ): List<JellyfinMediaEntity> =
        collectionDao.getPage(key, type, limit, offset = 0, hideWatched = hideWatched)

    suspend fun topRated(
        key: String,
        limit: Int,
        type: String? = null,
        hideWatched: Boolean = false
    ): List<JellyfinMediaEntity> =
        collectionDao.getTopRated(key, type, limit, offset = 0, hideWatched = hideWatched)

    suspend fun recent(
        key: String,
        limit: Int,
        type: String? = null,
        hideWatched: Boolean = false
    ): List<JellyfinMediaEntity> =
        collectionDao.getRecent(key, type, limit, offset = 0, hideWatched = hideWatched)

    suspend fun page(
        key: String,
        offset: Int,
        limit: Int,
        type: String? = null,
        hideWatched: Boolean = false
    ): List<JellyfinMediaEntity> =
        collectionDao.getPage(key, type, limit, offset, hideWatched)

    suspend fun invalidate() {
        collectionDao.clearAll()
    }
}
