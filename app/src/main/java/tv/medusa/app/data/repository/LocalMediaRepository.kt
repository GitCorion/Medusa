package tv.medusa.app.data.repository

import tv.medusa.app.data.local.db.JellyfinDao
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.local.db.LibraryStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Consultas locales (Room) sobre el catálogo sincronizado: listados, búsquedas,
 * resolución de un título a su entidad local, géneros y recomendaciones offline.
 *
 * Extraído de `MediaRepository`. Todas las lecturas que alimentan la UI pasan por
 * [filterAllowed] para respetar las restricciones parentales.
 */
class LocalMediaRepository(
    private val jellyfinDao: JellyfinDao,
    private val accessPolicy: MediaAccessPolicy
) {
    /**
     * ¿Hay que filtrar de verdad por la lista de IDs permitidos?
     *
     * La lista se siembra desde el propio catálogo (`getAllIds`), así que una lista **completa**
     * es un no-op y una lista **parcial** (de versiones antiguas o de un sync interrumpido) solo
     * puede ocultar títulos que sí están en Room. Si no cubre el catálogo, se ignora: el próximo
     * sync la reconstruye. Jellyfin ya aplica las restricciones por usuario en el servidor.
     */
    private suspend fun restrictionsActive(): Boolean {
        if (!accessPolicy.hasRestrictions()) return false
        val catalog = jellyfinDao.getCount()
        if (catalog <= 0) return false
        return accessPolicy.allowedCount() >= catalog
    }

    private fun restrictionsActiveFlow(): Flow<Boolean> =
        combine(
            accessPolicy.hasRestrictionsFlow(),
            jellyfinDao.getCountFlow(),
            accessPolicy.allowedCountFlow()
        ) { has, catalog, allowed ->
            has && catalog > 0 && allowed >= catalog
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllLocalLibrary(): Flow<List<JellyfinMediaEntity>> =
        restrictionsActiveFlow().flatMapLatest { restricted ->
            if (restricted) jellyfinDao.getAllItemsRestricted()
            else jellyfinDao.getAllItems()
        }

    suspend fun getLocalMovies(): List<JellyfinMediaEntity> {
        val items = if (restrictionsActive()) {
            jellyfinDao.getMoviesRestricted()
        } else {
            jellyfinDao.getMovies()
        }
        return items.deduplicateMediaEntities()
    }

    suspend fun getLocalSeries(): List<JellyfinMediaEntity> {
        val items = if (restrictionsActive()) {
            jellyfinDao.getSeriesRestricted()
        } else {
            jellyfinDao.getSeries()
        }
        return items.deduplicateMediaEntities()
    }

    suspend fun getAllCatalog(): List<JellyfinMediaEntity> {
        val items = if (restrictionsActive()) {
            jellyfinDao.getAllCatalogRestricted()
        } else {
            jellyfinDao.getAllCatalog()
        }
        return items.deduplicateMediaEntities()
    }

    suspend fun getItemById(id: String): JellyfinMediaEntity? = jellyfinDao.getItemById(id)

    suspend fun getItemsByIds(ids: List<String>): List<JellyfinMediaEntity> {
        if (ids.isEmpty()) return emptyList()
        return if (restrictionsActive()) {
            jellyfinDao.getItemsByIdsRestricted(ids)
        } else {
            jellyfinDao.getItemsByIds(ids)
        }
    }

    suspend fun getContinueWatchingLocal(limit: Int = 20): List<JellyfinMediaEntity> {
        val raw = if (restrictionsActive()) {
            jellyfinDao.getContinueWatchingLocalRestricted(limit * 2)
        } else {
            jellyfinDao.getContinueWatchingLocal(limit * 2)
        }
        val seenKeys = mutableSetOf<String>()
        val filtered = raw.filter { item ->
            val key = item.seriesId ?: item.id
            seenKeys.add(key)
        }.take(limit)
        return filtered.deduplicateMediaEntities()
    }

    /**
     * Vistos recientemente leídos de Room (instantáneo).
     *
     * Sustituye a la consulta remota de vistos allí donde solo hacía falta una referencia (p. ej.
     * las secciones "Porque viste X"): en catálogos grandes esa consulta tardaba decenas de
     * segundos contra el servidor y llegaba tarde a quien la necesitaba.
     */
    suspend fun getRecentlyPlayedLocal(limit: Int = 20): List<JellyfinMediaEntity> {
        val raw = if (restrictionsActive()) {
            jellyfinDao.getRecentlyPlayedLocalRestricted(limit * 2)
        } else {
            jellyfinDao.getRecentlyPlayedLocal(limit * 2)
        }
        val seenKeys = mutableSetOf<String>()
        val filtered = raw.filter { item ->
            val key = item.seriesId ?: item.id
            seenKeys.add(key)
        }.take(limit)
        return filtered.deduplicateMediaEntities()
    }

    suspend fun getLatestLocal(serverUrl: String = "", userId: String = "", limit: Int = 20): List<JellyfinMediaEntity> {
        val cacheKey = "$serverUrl-$userId"
        MediaCaches.serverLatestCache.get(cacheKey)?.let { return it.take(limit) }
        val items = if (restrictionsActive()) {
            jellyfinDao.getLatestLocalRestricted(limit)
        } else {
            jellyfinDao.getLatestLocal(limit)
        }
        return items.deduplicateMediaEntities()
    }

    suspend fun getNewReleasesLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        if (restrictionsActive()) {
            jellyfinDao.getNewReleasesLocalRestricted(limit).deduplicateMediaEntities()
        } else {
            jellyfinDao.getNewReleasesLocal(limit).deduplicateMediaEntities()
        }

    suspend fun getTopMoviesLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        if (restrictionsActive()) {
            jellyfinDao.getTopMoviesLocalRestricted(limit).deduplicateMediaEntities()
        } else {
            jellyfinDao.getTopMoviesLocal(limit).deduplicateMediaEntities()
        }

    suspend fun getTopSeriesLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        if (restrictionsActive()) {
            jellyfinDao.getTopSeriesLocalRestricted(limit).deduplicateMediaEntities()
        } else {
            jellyfinDao.getTopSeriesLocal(limit).deduplicateMediaEntities()
        }

    suspend fun getTopRatedLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        if (restrictionsActive()) {
            jellyfinDao.getTopRatedLocalRestricted(limit).deduplicateMediaEntities()
        } else {
            jellyfinDao.getTopRatedLocal(limit).deduplicateMediaEntities()
        }

    suspend fun getRewatchableMoviesLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        if (restrictionsActive()) {
            jellyfinDao.getRewatchableMoviesLocalRestricted(limit).deduplicateMediaEntities()
        } else {
            jellyfinDao.getRewatchableMoviesLocal(limit).deduplicateMediaEntities()
        }

    suspend fun getBoxSetsLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        if (restrictionsActive()) {
            jellyfinDao.getBoxSetsLocalRestricted(limit).deduplicateMediaEntities()
        } else {
            jellyfinDao.getBoxSetsLocal(limit).deduplicateMediaEntities()
        }

    suspend fun getDocumentariesLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        if (restrictionsActive()) {
            jellyfinDao.getDocumentariesLocalRestricted(limit).deduplicateMediaEntities()
        } else {
            jellyfinDao.getDocumentariesLocal(limit).deduplicateMediaEntities()
        }

    suspend fun getNationalCinemaLocal(pattern: String, limit: Int = 20): List<JellyfinMediaEntity> =
        if (restrictionsActive()) {
            jellyfinDao.getNationalCinemaLocalRestricted(pattern, limit).deduplicateMediaEntities()
        } else {
            jellyfinDao.getNationalCinemaLocal(pattern, limit).deduplicateMediaEntities()
        }

    suspend fun getSpanishCinemaLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        getNationalCinemaLocal("españ", limit)

    suspend fun searchLocalMedia(query: String, limit: Int = 40): List<JellyfinMediaEntity> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()

        val isRestricted = restrictionsActive()

        // Capa rápida: FTS4 (índice de texto completo) sobre título/título original.
        val ftsQuery = buildFtsQuery(clean)
        val ftsResults = if (ftsQuery != null) {
            runCatching {
                if (isRestricted) {
                    jellyfinDao.searchLocalMediaFtsRestricted(ftsQuery, limit)
                } else {
                    jellyfinDao.searchLocalMediaFts(ftsQuery, limit)
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // Fallback a la búsqueda LIKE original si el FTS no devuelve nada (tolerancia a subcadenas).
        val results = ftsResults.ifEmpty {
            if (isRestricted) {
                jellyfinDao.searchLocalMediaRestricted(clean, limit)
            } else {
                jellyfinDao.searchLocalMedia(clean, limit)
            }
        }
        return results.deduplicateMediaEntities()
    }

    /**
     * Convierte la consulta del usuario en una expresión MATCH segura para FTS4. Se extraen los
     * tramos alfanuméricos (Unicode) y se les añade `*` para búsqueda por prefijo; al no quedar
     * caracteres de sintaxis (`"`, `-`, `*`, operadores...) la expresión no puede romper el parser.
     */
    private fun buildFtsQuery(raw: String): String? {
        val tokens = Regex("[\\p{L}\\p{N}]+").findAll(raw)
            .map { it.value }
            .filter { it.isNotBlank() }
            .toList()
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    suspend fun getAllLocalTitles(): List<String> = jellyfinDao.getAllLocalTitles()

    suspend fun getLocalCount(): Int = jellyfinDao.getCount()

    fun getLibraryStatsFlow(): Flow<LibraryStats> = jellyfinDao.getLibraryStatsFlow()

    suspend fun getFavoritesLocal(): List<JellyfinMediaEntity> {
        val items = if (restrictionsActive()) {
            jellyfinDao.getFavoritesRestricted()
        } else {
            jellyfinDao.getFavorites()
        }
        return items.deduplicateMediaEntities()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFavoritesFlow(): Flow<List<JellyfinMediaEntity>> =
        restrictionsActiveFlow().flatMapLatest { restricted ->
            if (restricted) jellyfinDao.getFavoritesFlowRestricted()
            else jellyfinDao.getFavoritesFlow()
        }

    suspend fun findLocal(tmdbId: String?, imdbId: String?, title: String? = null, year: Int? = null): JellyfinMediaEntity? {
        val matched = findLocalInternal(tmdbId, imdbId, title, year) ?: return null
        return resolveToSeriesIfEpisode(matched)
    }

    private suspend fun findLocalInternal(tmdbId: String?, imdbId: String?, title: String? = null, year: Int? = null): JellyfinMediaEntity? {
        // 1. Try matching by TMDB or IMDB ID
        if (tmdbId != null || imdbId != null) {
            val matchedById = jellyfinDao.findByTmdbOrImdb(tmdbId, imdbId)
            if (matchedById != null) return matchedById
        }

        // 2. Fallback: match by title and year
        if (!title.isNullOrBlank()) {
            val trimmedTitle = title.trim()
            val cleanTitle = trimmedTitle.substringBefore(":").substringBefore("-").trim()
            // Evita repetir el mismo escaneo cuando colon/guion no cambian el título.
            val titleVariants = if (cleanTitle == trimmedTitle) listOf(cleanTitle) else listOf(cleanTitle, trimmedTitle)
            if (year != null && year > 1900) {
                for (candidate in titleVariants) {
                    val matchedByTitleAndYear = jellyfinDao.findByTitleAndYear(candidate, year)
                    if (matchedByTitleAndYear != null) return matchedByTitleAndYear
                }
            }
            for (candidate in titleVariants) {
                val matchedByTitle = jellyfinDao.findByTitle(candidate)
                if (matchedByTitle != null) return matchedByTitle
            }
            // Fuzzy SOLO como último recurso y con señal suficiente: títulos muy cortos ("Up",
            // "It") o de menos de 4 caracteres producen coincidencias por subcadena con cualquier
            // cosa. El DAO además exige compatibilidad de año, lo que evita contaminaciones como
            // "Super Mario 3" en la fila de una plataforma que emite un estreno reciente.
            val fuzzyCandidate = titleVariants.firstOrNull { it.length >= 4 }
            if (fuzzyCandidate != null) {
                val matchedByFuzzy = jellyfinDao.findByFuzzyTitle(fuzzyCandidate, year)
                if (matchedByFuzzy != null) return matchedByFuzzy
            }
        }

        return null
    }

    private suspend fun resolveToSeriesIfEpisode(entity: JellyfinMediaEntity): JellyfinMediaEntity {
        if (!entity.type.equals("Episode", ignoreCase = true)) {
            return entity
        }

        // 1. Look up parent series in local Room DB by seriesId
        if (!entity.seriesId.isNullOrBlank()) {
            val parent = jellyfinDao.getItemById(entity.seriesId)
            if (parent != null) return parent
        }

        // 2. Look up parent series in local Room DB by seriesName or title
        val sName = entity.seriesName ?: entity.title.substringBefore("(").substringBefore("-").trim()
        val parentByName = jellyfinDao.getSeriesByTitle(sName)
        if (parentByName != null) return parentByName

        // 3. Synthesize Series entity from Episode data
        val eps = if (!entity.seriesId.isNullOrBlank()) {
            jellyfinDao.getEpisodesForSeries(entity.seriesId)
        } else emptyList()

        val uniqueEps = eps.filter { (it.seasonNumber ?: 0) > 0 && (it.episodeNumber ?: 0) > 0 }
            .groupBy { Pair(it.seasonNumber, it.episodeNumber) }
        val playedCount = if (uniqueEps.isNotEmpty()) {
            uniqueEps.count { (_, duplicates) -> duplicates.any { it.isPlayed } }
        } else {
            if (entity.isPlayed) 1 else 0
        }
        val totalCount = maxOf(
            entity.totalItemCount ?: 0,
            if (uniqueEps.isNotEmpty()) uniqueEps.size else 0
        )
        val unplayedCount = (totalCount - playedCount).coerceAtLeast(0)

        return JellyfinMediaEntity(
            id = entity.seriesId ?: entity.id,
            title = sName,
            originalTitle = entity.originalTitle,
            type = "Series",
            tmdbId = entity.tmdbId,
            imdbId = entity.imdbId,
            tvdbId = entity.tvdbId,
            overview = entity.overview,
            primaryImageTag = entity.seriesPrimaryImageTag ?: entity.primaryImageTag,
            backdropImageTag = entity.backdropImageTag,
            communityRating = entity.communityRating,
            productionYear = entity.productionYear,
            genres = entity.genres,
            isPlayed = unplayedCount == 0 && playedCount > 0,
            playbackPositionTicks = 0L,
            isFavorite = entity.isFavorite,
            seriesId = null,
            seriesName = null,
            seriesPrimaryImageTag = null,
            seasonNumber = null,
            episodeNumber = null,
            totalItemCount = totalCount,
            unplayedItemCount = unplayedCount
        )
    }

    suspend fun getMostWatchedGenres(): List<String> {
        val watchedRaw = runCatching { jellyfinDao.getWatchedGenres() }.getOrDefault(emptyList())
        val allRaw = if (watchedRaw.size < 5) runCatching { jellyfinDao.getAllGenresRaw() }.getOrDefault(emptyList()) else emptyList()

        val rawList = watchedRaw.ifEmpty { allRaw }
        val counts = mutableMapOf<String, Int>()

        for (raw in rawList) {
            if (raw.isNullOrBlank()) continue
            val parts = raw.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
            for (part in parts) {
                val clean = part.replaceFirstChar { it.uppercase() }
                counts[clean] = (counts[clean] ?: 0) + 1
            }
        }

        val defaultGenres = listOf("Acción", "Comedia", "Drama", "Ciencia ficción", "Terror", "Aventura", "Animación", "Thriller", "Romance", "Fantasía", "Documental")
        for (g in defaultGenres) {
            if (!counts.containsKey(g)) {
                counts[g] = 1
            }
        }

        return counts.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(12)
    }

    private fun getGenreSynonyms(genre: String): List<String> {
        val clean = genre.trim().lowercase()
        val synonyms = mutableListOf(clean)
        when {
            clean.contains("acción") || clean.contains("accion") -> synonyms.add("action")
            clean == "action" -> synonyms.addAll(listOf("acción", "accion"))

            clean.contains("comedia") -> synonyms.add("comedy")
            clean == "comedy" -> synonyms.add("comedia")

            clean.contains("ciencia ficción") || clean.contains("ciencia ficcion") || clean.contains("sci-fi") || clean.contains("science fiction") ->
                synonyms.addAll(listOf("sci-fi", "science fiction", "ciencia ficción", "ciencia ficcion"))

            clean.contains("terror") || clean.contains("miedo") -> synonyms.add("horror")
            clean == "horror" -> synonyms.add("terror")

            clean.contains("aventura") -> synonyms.add("adventure")
            clean == "adventure" -> synonyms.add("aventura")

            clean.contains("animación") || clean.contains("animacion") -> synonyms.addAll(listOf("animation", "anime"))
            clean == "animation" || clean == "anime" -> synonyms.addAll(listOf("animación", "animacion"))

            clean.contains("thriller") || clean.contains("suspense") || clean.contains("misterio") ->
                synonyms.addAll(listOf("thriller", "suspense", "mystery", "misterio"))

            clean.contains("romance") || clean.contains("romántica") || clean.contains("romantica") ->
                synonyms.addAll(listOf("romance", "romántica", "romantica"))

            clean.contains("fantasía") || clean.contains("fantasia") -> synonyms.add("fantasy")
            clean == "fantasy" -> synonyms.addAll(listOf("fantasía", "fantasia"))

            clean.contains("documental") || clean.contains("documentary") -> synonyms.addAll(listOf("documentary", "documental"))

            clean.contains("familia") || clean.contains("familiar") || clean.contains("family") -> synonyms.addAll(listOf("family", "familia", "familiar"))

            clean.contains("crimen") || clean.contains("crime") -> synonyms.addAll(listOf("crime", "crimen"))
            clean.contains("drama") -> synonyms.add("drama")
        }
        return synonyms.distinct()
    }

    suspend fun getItemsByGenre(genre: String): List<JellyfinMediaEntity> {
        val synonyms = getGenreSynonyms(genre)
        val combined = mutableListOf<JellyfinMediaEntity>()
        val seen = mutableSetOf<String>()
        val isRestricted = restrictionsActive()
        for (syn in synonyms) {
            val items = if (isRestricted) jellyfinDao.getItemsByGenreRestricted(syn)
            else jellyfinDao.getItemsByGenre(syn)
            for (item in items) {
                if (seen.add(item.id)) combined.add(item)
            }
            if (combined.size >= 40) break
        }
        return combined.deduplicateMediaEntities()
    }

    suspend fun getMoviesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity> {
        val synonyms = getGenreSynonyms(genre)
        val combined = mutableListOf<JellyfinMediaEntity>()
        val seen = mutableSetOf<String>()
        val isRestricted = restrictionsActive()
        for (syn in synonyms) {
            val items = if (isRestricted) jellyfinDao.getMoviesByGenreRestricted(syn, limit)
            else jellyfinDao.getMoviesByGenre(syn, limit)
            for (item in items) {
                if (seen.add(item.id)) combined.add(item)
            }
            if (combined.size >= limit) break
        }
        return combined.deduplicateMediaEntities()
    }

    suspend fun getSeriesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity> {
        val synonyms = getGenreSynonyms(genre)
        val combined = mutableListOf<JellyfinMediaEntity>()
        val seen = mutableSetOf<String>()
        val isRestricted = restrictionsActive()
        for (syn in synonyms) {
            val items = if (isRestricted) jellyfinDao.getSeriesByGenreRestricted(syn, limit)
            else jellyfinDao.getSeriesByGenre(syn, limit)
            for (item in items) {
                if (seen.add(item.id)) combined.add(item)
            }
            if (combined.size >= limit) break
        }
        return combined.deduplicateMediaEntities()
    }

    suspend fun getRecentMoviesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity> {
        val synonyms = getGenreSynonyms(genre)
        val combined = mutableListOf<JellyfinMediaEntity>()
        val seen = mutableSetOf<String>()
        val isRestricted = restrictionsActive()
        for (syn in synonyms) {
            val items = if (isRestricted) jellyfinDao.getRecentMoviesByGenreRestricted(syn, limit)
            else jellyfinDao.getRecentMoviesByGenre(syn, limit)
            for (item in items) {
                if (seen.add(item.id)) combined.add(item)
            }
            if (combined.size >= limit) break
        }
        return combined.deduplicateMediaEntities()
    }

    suspend fun getRecentSeriesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity> {
        val synonyms = getGenreSynonyms(genre)
        val combined = mutableListOf<JellyfinMediaEntity>()
        val seen = mutableSetOf<String>()
        val isRestricted = restrictionsActive()
        for (syn in synonyms) {
            val items = if (isRestricted) jellyfinDao.getRecentSeriesByGenreRestricted(syn, limit)
            else jellyfinDao.getRecentSeriesByGenre(syn, limit)
            for (item in items) {
                if (seen.add(item.id)) combined.add(item)
            }
            if (combined.size >= limit) break
        }
        return combined.deduplicateMediaEntities()
    }

    suspend fun getRecommendedMovies(
        excludeIds: Set<String>,
        limit: Int = 20,
        genres: List<String>? = null
    ): List<JellyfinMediaEntity> {
        val topGenres = (genres ?: getMostWatchedGenres()).take(4)
        val addedIds = mutableSetOf<String>().apply { addAll(excludeIds) }

        // Máximo de ítems por género para garantizar variedad real.
        // Con 4 géneros × 4 = 16, completamos hasta 20 con el fallback.
        val maxPerGenre = 4

        val isRestricted = restrictionsActive()

        // Recoger candidatos de cada género por separado, luego intercalar
        val buckets: List<List<JellyfinMediaEntity>> = topGenres.map { genre ->
            val genreItems = getItemsByGenre(genre)
            genreItems.filter {
                it.type.equals("Movie", ignoreCase = true)
                    && it.id !in addedIds
                    && !it.isPlayed
                    && it.playbackPositionTicks <= 0L
                    && ((it.communityRating ?: 0f) >= 5.0f || it.communityRating == null)
            }.take(maxPerGenre)
        }

        // Intercalar: primero un item de cada género, luego el segundo de cada género, etc.
        val resultList = mutableListOf<JellyfinMediaEntity>()
        val maxRound = buckets.maxOfOrNull { it.size } ?: 0
        for (round in 0 until maxRound) {
            for (bucket in buckets) {
                if (round < bucket.size) {
                    val item = bucket[round]
                    if (item.id !in addedIds) {
                        resultList.add(item)
                        addedIds.add(item.id)
                        if (resultList.size >= limit) return resultList
                    }
                }
            }
        }

        // Fallback: rellenar con películas locales no mostradas aún
        if (resultList.size < limit) {
            val topCandidates = if (isRestricted) {
                jellyfinDao.getTopMoviesLocalRestricted(limit = 50)
            } else {
                jellyfinDao.getTopMoviesLocal(limit = 50)
            }
            topCandidates.filter { it.id !in addedIds && !it.isPlayed && it.playbackPositionTicks <= 0L }
                .take(limit - resultList.size)
                .forEach {
                    resultList.add(it)
                    addedIds.add(it.id)
                }
        }

        return resultList
    }
}

/**
 * Deduplica entidades que representan el mismo título (distintas versiones/multifuente).
 *
 * Agrupa en un solo paso por clave (tipo + tmdbId/imdbId/título+año) para evitar el
 * doble bucle O(n²) original, preservando el orden y eligiendo el mismo representante.
 */
fun List<JellyfinMediaEntity>.deduplicateMediaEntities(): List<JellyfinMediaEntity> {
    if (size <= 1) return this

    // Índices agrupados por cada clave de coincidencia (tipo + tmdbId/imdbId/título+año).
    // Permite agrupar en un paso en lugar del doble bucle O(n²) original.
    val byTmdb = HashMap<String, MutableList<Int>>()
    val byImdb = HashMap<String, MutableList<Int>>()
    val byMovieTitleYear = HashMap<String, MutableList<Int>>()
    val bySeriesTitle = HashMap<String, MutableList<Int>>()

    for (i in indices) {
        val item = this[i]
        val type = item.type.lowercase()
        if (!item.tmdbId.isNullOrBlank()) {
            byTmdb.getOrPut("$type|${item.tmdbId}") { mutableListOf() }.add(i)
        }
        if (!item.imdbId.isNullOrBlank()) {
            byImdb.getOrPut("$type|${item.imdbId}") { mutableListOf() }.add(i)
        }
        when {
            type == "movie" && item.productionYear != null ->
                byMovieTitleYear.getOrPut("$type|${item.productionYear}|${item.title.trim().lowercase()}") { mutableListOf() }.add(i)
            type == "series" ->
                bySeriesTitle.getOrPut("$type|${item.title.trim().lowercase()}") { mutableListOf() }.add(i)
        }
    }

    val processed = BooleanArray(size)
    val result = ArrayList<JellyfinMediaEntity>(size)

    for (i in indices) {
        if (processed[i]) continue
        val itemA = this[i]
        val type = itemA.type.lowercase()

        // Grupo = itemA + los índices posteriores que coinciden con él por cualquier clave.
        val groupIndices = sortedSetOf(i)

        if (!itemA.tmdbId.isNullOrBlank()) {
            byTmdb["$type|${itemA.tmdbId}"]?.forEach { j -> if (j > i && !processed[j]) groupIndices.add(j) }
        }
        if (!itemA.imdbId.isNullOrBlank()) {
            byImdb["$type|${itemA.imdbId}"]?.forEach { j -> if (j > i && !processed[j]) groupIndices.add(j) }
        }
        when {
            type == "movie" && itemA.productionYear != null ->
                byMovieTitleYear["$type|${itemA.productionYear}|${itemA.title.trim().lowercase()}"]
                    ?.forEach { j -> if (j > i && !processed[j]) groupIndices.add(j) }
            type == "series" ->
                bySeriesTitle["$type|${itemA.title.trim().lowercase()}"]
                    ?.forEach { j -> if (j > i && !processed[j]) groupIndices.add(j) }
        }

        groupIndices.forEach { processed[it] = true }

        if (groupIndices.size == 1) {
            result.add(itemA)
            continue
        }

        val duplicates = groupIndices.map { this[it] }

        val bestPlayed = duplicates.any { it.isPlayed }
        val maxTicks = duplicates.maxOf { it.playbackPositionTicks }

        val cleanTitleItem = duplicates.firstOrNull { item ->
            val name = item.title.trim()
            !name.contains("1080p", ignoreCase = true) &&
            !name.contains("4k", ignoreCase = true) &&
            !name.contains("hdr", ignoreCase = true) &&
            !name.contains("remux", ignoreCase = true)
        } ?: duplicates.first()

        val bestTotal = duplicates.mapNotNull { it.totalItemCount }.filter { it > 0 }.minOrNull() ?: cleanTitleItem.totalItemCount
        val bestUnplayed = duplicates.mapNotNull { it.unplayedItemCount }.minOrNull() ?: cleanTitleItem.unplayedItemCount

        val primaryItem = duplicates.firstOrNull { it.isPlayed || it.playbackPositionTicks > 0 }
            ?: cleanTitleItem

        result.add(
            cleanTitleItem.copy(
                id = primaryItem.id,
                isPlayed = bestPlayed,
                playbackPositionTicks = maxTicks,
                totalItemCount = bestTotal,
                unplayedItemCount = bestUnplayed,
                overview = cleanTitleItem.overview ?: duplicates.firstNotNullOfOrNull { it.overview },
                primaryImageTag = cleanTitleItem.primaryImageTag ?: duplicates.firstNotNullOfOrNull { it.primaryImageTag },
                backdropImageTag = cleanTitleItem.backdropImageTag ?: duplicates.firstNotNullOfOrNull { it.backdropImageTag }
            )
        )
    }

    return result
}
