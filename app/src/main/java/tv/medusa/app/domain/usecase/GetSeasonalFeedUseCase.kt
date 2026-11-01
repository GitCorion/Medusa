package tv.medusa.app.domain.usecase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.medusa.app.data.local.UserPreferences
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.local.db.SeasonalDao
import tv.medusa.app.data.local.db.SeasonalIndexMetaEntity
import tv.medusa.app.data.local.db.SeasonalItemEntity
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaFormatFilter
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.model.SeasonalEvent
import tv.medusa.app.domain.model.SeasonalEventsManager
import tv.medusa.app.util.toOptimizedMediaItem
import javax.inject.Inject
import kotlin.math.roundToInt

data class SeasonalFeedResult(
    val event: SeasonalEvent,
    val sections: List<HomeSection>,
    val featuredItem: MediaItem? = null,
    val allCount: Int = 0
)

data class SeasonalPage(
    val items: List<MediaItem>,
    val hasMore: Boolean,
    val total: Int
)

data class SeasonalIndexStatus(
    val itemCount: Int,
    val isFresh: Boolean,
    val updatedAt: Long
)

/**
 * Construye y consulta el especial estacional sin volcar el catálogo completo en memoria.
 *
 * Estrategia:
 * 1. Un índice persistente (`seasonal_items`) guarda solo los títulos que pertenecen al evento.
 * 2. La landing pide como máximo 5 filas de 20 elementos mediante consultas indexadas.
 * 3. El grid "Ver todo" pagina de 60 en 60 desde Room.
 * 4. La construcción del índice se hace en `Dispatchers.Default` y en segundo plano; si ya
 *    existe uno anterior, la UI lo muestra al instante mientras se refresca.
 */
class GetSeasonalFeedUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val seasonalDao: SeasonalDao
) {
    companion object {
        const val PAGE_SIZE = 60
        private const val LANDING_ROW_SIZE = 24
        private const val TAG = "SeasonalFeed"
    }

    private val indexMutex = Mutex()

    private suspend fun resolveEvent(eventId: String): SeasonalEvent? =
        SeasonalEventsManager.ALL_EVENTS.firstOrNull { it.id == eventId }
            ?: SeasonalEventsManager.getActiveEvent()

    private fun eventType(format: MediaFormatFilter): String? = when (format) {
        MediaFormatFilter.ALL -> null
        MediaFormatFilter.MOVIES -> "Movie"
        MediaFormatFilter.SERIES -> "Series"
    }

    private fun catalogVersion(prefs: UserPreferences, count: Int): String =
        "${prefs.jellyfinLastSync}|$count"

    suspend fun getIndexStatus(eventId: String): SeasonalIndexStatus {
        val count = runCatching { seasonalDao.count(eventId) }.getOrDefault(0)
        val meta = runCatching { seasonalDao.getMeta(eventId) }.getOrNull()
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val version = catalogVersion(prefs, mediaRepository.getLocalCount())
        return SeasonalIndexStatus(
            itemCount = count,
            isFresh = meta != null && meta.catalogVersion == version && count > 0,
            updatedAt = meta?.updatedAt ?: 0L
        )
    }

    /**
     * Construye (o refresca) el índice de pertenencia al evento. Devuelve el número de títulos.
     */
    suspend fun ensureIndex(eventId: String, force: Boolean = false): Int = indexMutex.withLock {
        val event = resolveEvent(eventId) ?: return 0
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val localCount = mediaRepository.getLocalCount()
        val version = catalogVersion(prefs, localCount)
        val meta = runCatching { seasonalDao.getMeta(eventId) }.getOrNull()
        if (!force && meta != null && meta.catalogVersion == version && meta.itemCount > 0) {
            return meta.itemCount
        }

        val startedAt = System.currentTimeMillis()
        // Filtrar en SQLite: solo salen de la BD las ~3.500 coincidencias, no las 32.000 filas.
        // El filtro Kotlin posterior es barato y mantiene la precisión del evento.
        val candidates = runCatching {
            val restricted = mediaRepository.hasRealRestrictions()
            mediaRepository.getSeasonalCandidates(buildCandidateQuery(event, restricted))
        }.getOrDefault(emptyList())

        val catalog = if (candidates.isEmpty() && event.searchTerms.isEmpty()) {
            runCatching { mediaRepository.getCatalogLight() }.getOrDefault(emptyList())
        } else {
            candidates
        }
        if (catalog.isEmpty()) return runCatching { seasonalDao.count(eventId) }.getOrDefault(0)

        val items = withContext(Dispatchers.Default) {
            catalog.asSequence()
                .distinctBy { it.id }
                .filter { isItemRelevantForEvent(it, event) }
                .map { entity ->
                    SeasonalItemEntity(
                        eventId = eventId,
                        mediaId = entity.id,
                        score = scoreFor(entity),
                        editorial = false
                    )
                }
                .toList()
        }

        val newMeta = SeasonalIndexMetaEntity(
            eventId = eventId,
            catalogVersion = version,
            itemCount = items.size
        )
        runCatching { seasonalDao.replaceIndex(eventId, items, newMeta) }
        Log.d(
            TAG,
            "Índice $eventId: ${items.size} de ${catalog.size} candidatos en " +
                "${System.currentTimeMillis() - startedAt} ms"
        )
        return items.size
    }

    /**
     * Landing del especial: filas curadas + contador. Cada consulta devuelve 20-24 elementos
     * como máximo, nunca las 3.500 filas completas.
     */
    suspend fun getLanding(
        eventId: String,
        format: MediaFormatFilter = MediaFormatFilter.ALL,
        hideWatched: Boolean = false
    ): SeasonalFeedResult? {
        val event = resolveEvent(eventId) ?: return null
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val type = eventType(format)
        val currentYear = java.time.Year.now().value

        val sections = mutableListOf<HomeSection>()
        val seen = mutableSetOf<String>()

        fun addSection(title: String, items: List<JellyfinMediaEntity>) {
            val unique = items.filter { seen.add(it.id) }
            if (unique.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        title = title,
                        items = unique.map { it.toMediaItem(prefs) }
                    )
                )
            }
        }

        // Las cinco filas se consultan en paralelo: cada una es independiente y solo devuelve
        // 24 elementos. En la TV de 2 GB esto baja la landing de ~1,6 s a unos cientos de ms.
        val rows = coroutineScope {
            val top = async {
                event.homeSectionTitle to seasonalDao.getPage(
                    eventId = eventId, type = type, hideWatched = hideWatched,
                    minYear = null, maxYear = null, minRating = null,
                    unplayedOnly = hideWatched, limit = LANDING_ROW_SIZE, offset = 0
                )
            }
            val recent = async {
                "Recién llegado" to seasonalDao.getPage(
                    eventId = eventId, type = type, hideWatched = hideWatched,
                    minYear = currentYear - 3, maxYear = null, minRating = null,
                    unplayedOnly = hideWatched, limit = LANDING_ROW_SIZE, offset = 0
                )
            }
            val classics = async {
                "Clásicos que dan miedo" to seasonalDao.getPage(
                    eventId = eventId, type = type, hideWatched = hideWatched,
                    minYear = null, maxYear = 2000, minRating = null,
                    unplayedOnly = hideWatched, limit = LANDING_ROW_SIZE, offset = 0
                )
            }
            val series = if (format == MediaFormatFilter.ALL || format == MediaFormatFilter.SERIES) {
                async {
                    "Series para maratón" to seasonalDao.getPage(
                        eventId = eventId, type = "Series", hideWatched = hideWatched,
                        minYear = null, maxYear = null, minRating = null,
                        unplayedOnly = hideWatched, limit = LANDING_ROW_SIZE, offset = 0
                    )
                }
            } else null
            val gems = async {
                "Joyas ocultas" to seasonalDao.getPage(
                    eventId = eventId, type = type, hideWatched = hideWatched,
                    minYear = null, maxYear = null, minRating = 7.5f,
                    unplayedOnly = true, limit = LANDING_ROW_SIZE, offset = 0
                )
            }
            buildList {
                add(top.await())
                add(recent.await())
                add(classics.await())
                series?.let { add(it.await()) }
                add(gems.await())
            }
        }

        val total = seasonalDao.countFiltered(eventId, type, hideWatched)
        rows.forEach { (title, items) -> addSection(title, items) }
        return SeasonalFeedResult(
            event = event,
            sections = sections,
            featuredItem = sections.firstOrNull()?.items?.firstOrNull(),
            allCount = total
        )
    }

    /**
     * Página del grid "Ver todo". [offset] es el número de elementos ya cargados.
     */
    suspend fun getPage(
        eventId: String,
        format: MediaFormatFilter,
        hideWatched: Boolean,
        offset: Int,
        limit: Int = PAGE_SIZE
    ): SeasonalPage {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val type = eventType(format)
        val entities = seasonalDao.getPage(
            eventId = eventId,
            type = type,
            hideWatched = hideWatched,
            minYear = null,
            maxYear = null,
            minRating = null,
            unplayedOnly = false,
            limit = limit,
            offset = offset
        )
        val total = seasonalDao.countFiltered(eventId, type, hideWatched)
        return SeasonalPage(
            items = withContext(Dispatchers.Default) { entities.map { it.toMediaItem(prefs) } },
            hasMore = offset + entities.size < total,
            total = total
        )
    }

    /**
     * Enriquece el índice con la selección editorial de Trakt/TMDb. Se ejecuta en segundo plano
     * y nunca bloquea la primera pintura. Si falla la red, el especial sigue siendo usable con
     * la selección local.
     */
    suspend fun refreshEditorial(eventId: String) {
        val event = resolveEvent(eventId) ?: return
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val tmdbKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() }
            ?: tv.medusa.app.data.local.DEFAULT_TMDB_API_KEY
        val traktClientId = prefs.traktClientId.takeIf { it.isNotBlank() }
            ?: tv.medusa.app.data.local.DEFAULT_TRAKT_CLIENT_ID

        val matched = mutableListOf<JellyfinMediaEntity>()
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(3500L) {
                if (event.traktCuratedLists.isNotEmpty() && traktClientId.isNotBlank()) {
                    val raw = mutableListOf<tv.medusa.app.data.remote.trakt.TraktMediaDto>()
                    for ((user, listId) in event.traktCuratedLists) {
                        raw += runCatching {
                            mediaRepository.getTraktCustomListItems(
                                traktToken = prefs.traktAccessToken,
                                clientId = traktClientId,
                                listId = listId,
                                username = user.takeIf { it.isNotBlank() }
                            ).getOrDefault(emptyList())
                        }.getOrDefault(emptyList())
                    }
                    matched += filterToLibraryUseCase.filterTraktItems(
                        traktItems = raw.distinctBy { it.ids.trakt ?: it.ids.tmdb ?: it.title },
                        serverUrl = prefs.jellyfinServerUrl,
                        userId = prefs.jellyfinUserId,
                        token = prefs.jellyfinAccessToken,
                        maxCandidates = 120
                    )
                }
                if (tmdbKey.isNotBlank() && event.tmdbKeywords.isNotEmpty()) {
                    val combined = event.tmdbKeywords.joinToString("|")
                    val raw = mediaRepository.getTmdbSeasonalDiscover(
                        apiKey = tmdbKey,
                        withKeywords = combined,
                        maxPages = 2
                    ).getOrDefault(emptyList())
                    matched += filterToLibraryUseCase.filterTmdbItems(
                        tmdbItems = raw,
                        serverUrl = prefs.jellyfinServerUrl,
                        userId = prefs.jellyfinUserId,
                        token = prefs.jellyfinAccessToken,
                        maxCandidates = 120
                    )
                }
            }
        }

        val ids = matched.map { it.id }.distinct()
        if (ids.isEmpty()) return
        runCatching {
            val existing = seasonalDao.count(eventId)
            if (existing == 0) return@runCatching
            ids.chunked(500).forEach { chunk -> seasonalDao.markEditorial(eventId, chunk) }
        }
    }

    /**
     * Consulta SQL con proyección completa pero **solo de las coincidencias**. Para Halloween
     * pasa de leer/mapear 32.000 entidades a leer ~3.500, y el filtro de texto lo hace SQLite.
     */
    private fun buildCandidateQuery(
        event: SeasonalEvent,
        restricted: Boolean
    ): androidx.sqlite.db.SimpleSQLiteQuery {
        val (genreTerms, titleTerms) = when (event.id) {
            "halloween" -> listOf("terror", "horror", "slasher") to (
                event.searchTerms + listOf(
                    "halloween", "noche de brujas", "bruja", "witch", "vampiro", "vampire",
                    "fantasma", "ghost", "zombie", "zombi", "slasher", "exorcista", "exorcism",
                    "posesión", "posension", "demonio", "demon", "hombre lobo", "werewolf",
                    "casa encantada", "espeluznante", "chucky", "freddy krueger", "jason voorhees",
                    "bitelchús", "beetlejuice", "hocus pocus", "coraline",
                    "pesadilla antes de navidad", "monster house", "la casa de cera",
                    "campamento miasma", "scary movie"
                )
                )
            "valentines" -> listOf("romance", "romántica", "romantica") to
                listOf("san valentín", "san valentin", "valentín", "valentin")
            "christmas" -> emptyList<String>() to (
                event.searchTerms + listOf(
                    "navidad", "christmas", "santa", "papá noel", "papa noel", "reyes magos",
                    "navideño", "navideña", "espíritu navideño", "grinch"
                )
                )
            else -> emptyList<String>() to event.searchTerms
        }
        val cleanGenres = genreTerms.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val cleanTitles = titleTerms.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val restriction = if (restricted) " AND jm.id IN (SELECT mediaId FROM allowed_media_ids)" else ""
        val projection = """
            jm.id, jm.title, NULL AS originalTitle, jm.type,
            NULL AS tmdbId, NULL AS imdbId, NULL AS tvdbId, NULL AS overview,
            jm.primaryImageTag, jm.backdropImageTag, jm.communityRating, jm.productionYear,
            jm.genres, NULL AS officialRating, jm.tags, jm.isPlayed, jm.playbackPositionTicks,
            jm.isFavorite, jm.seriesId, jm.seriesName, jm.seriesPrimaryImageTag,
            jm.seasonNumber, jm.episodeNumber, jm.totalItemCount, jm.unplayedItemCount,
            jm.lastPlayedTicks, jm.premiereDateMs, jm.titleLower, jm.originalTitleLower, jm.seriesNameLower
        """.trimIndent()

        if (cleanGenres.isEmpty() && cleanTitles.isEmpty()) {
            val sql = "SELECT $projection FROM jellyfin_media jm WHERE jm.type IN ('Movie','Series')$restriction"
            return androidx.sqlite.db.SimpleSQLiteQuery(sql)
        }

        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (cleanGenres.isNotEmpty()) {
            parts.add(
                "SELECT $projection FROM jellyfin_media jm " +
                    "JOIN jellyfin_media_genres_fts ON jm.rowid = jellyfin_media_genres_fts.rowid " +
                    "WHERE jellyfin_media_genres_fts MATCH ? AND jm.type IN ('Movie','Series')$restriction"
            )
            args.add(cleanGenres.joinToString(" OR ") { "\"${it.replace("\"", " ")}\"" })
        }
        if (cleanTitles.isNotEmpty()) {
            parts.add(
                "SELECT $projection FROM jellyfin_media jm " +
                    "JOIN jellyfin_media_fts ON jm.rowid = jellyfin_media_fts.rowid " +
                    "WHERE jellyfin_media_fts MATCH ? AND jm.type IN ('Movie','Series')$restriction"
            )
            args.add(cleanTitles.joinToString(" OR ") { "\"${it.replace("\"", " ")}\"" })
        }
        return androidx.sqlite.db.SimpleSQLiteQuery(parts.joinToString("\nUNION\n"), args.toTypedArray())
    }

    private fun JellyfinMediaEntity.toMediaItem(prefs: UserPreferences): MediaItem =
        toOptimizedMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)

    private fun scoreFor(item: JellyfinMediaEntity): Double {
        var score = (item.communityRating ?: 0f) * 10.0
        if (item.isFavorite) score += 6.0
        if (!item.isPlayed) score += 2.0
        val year = item.productionYear ?: 0
        if (year >= 2020) score += 5.0 else if (year >= 2010) score += 2.0
        if (item.backdropImageTag.isNullOrBlank()) score -= 10.0
        return (score * 100).roundToInt() / 100.0
    }

    /**
     * Valida estrictamente si un ítem de la biblioteca es temáticamente relevante para el evento.
     * Evita falsos positivos (como comedias dramáticas o documentales no relacionados).
     */
    internal fun isItemRelevantForEvent(item: JellyfinMediaEntity, event: SeasonalEvent): Boolean {
        val genresLower = item.genres?.lowercase() ?: ""
        val titleLower = item.title.lowercase()

        return when (event.id) {
            "halloween" -> {
                val isHorrorGenre = genresLower.contains("terror") ||
                    genresLower.contains("horror") ||
                    genresLower.contains("slasher")

                val explicitHalloweenKeywords = listOf(
                    "halloween", "noche de brujas", "bruja", "witch", "vampiro", "vampire",
                    "fantasma", "ghost", "zombie", "zombi", "slasher", "exorcista", "exorcism",
                    "posensión", "posesión", "demonio", "demon", "hombre lobo", "werewolf",
                    "casa encantada", "espeluznante", "chucky", "freddy krueger", "jason voorhees",
                    "bitelchús", "beetlejuice", "hocus pocus", "coraline", "pesadilla antes de navidad",
                    "monster house", "la casa de cera", "campamento miasma", "scary movie"
                )

                isHorrorGenre || explicitHalloweenKeywords.any { titleLower.contains(it) }
            }
            "christmas" -> {
                val explicitChristmasKeywords = listOf(
                    "navidad", "christmas", "santa", "papá noel", "papa noel", "reyes magos",
                    "navideño", "navideña", "espíritu navideño", "grinch"
                )
                explicitChristmasKeywords.any { term ->
                    titleLower.contains(term) || genresLower.contains(term)
                }
            }
            "valentines" -> {
                val isRomanceGenre = genresLower.contains("romance") ||
                    genresLower.contains("romántica") ||
                    genresLower.contains("romantica")
                val explicitValentineKeywords = listOf(
                    "san valentín", "san valentin", "valentín", "valentin"
                )
                isRomanceGenre || explicitValentineKeywords.any { titleLower.contains(it) }
            }
            else -> true
        }
    }
}
