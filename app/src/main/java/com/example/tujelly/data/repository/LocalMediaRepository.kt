package com.example.tujelly.data.repository

import com.example.tujelly.data.local.db.JellyfinDao
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import com.example.tujelly.data.local.db.LibraryStats
import kotlinx.coroutines.flow.Flow
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
    fun getAllLocalLibrary(): Flow<List<JellyfinMediaEntity>> =
        jellyfinDao.getAllItems().map { it.filterAllowed(accessPolicy) }

    suspend fun getLocalMovies(): List<JellyfinMediaEntity> =
        jellyfinDao.getMovies().deduplicateMediaEntities().filterAllowed(accessPolicy)

    suspend fun getLocalSeries(): List<JellyfinMediaEntity> =
        jellyfinDao.getSeries().deduplicateMediaEntities().filterAllowed(accessPolicy)

    suspend fun getAllCatalog(): List<JellyfinMediaEntity> =
        jellyfinDao.getAllCatalog().deduplicateMediaEntities().filterAllowed(accessPolicy)

    suspend fun getItemById(id: String): JellyfinMediaEntity? = jellyfinDao.getItemById(id)

    suspend fun getItemsByIds(ids: List<String>): List<JellyfinMediaEntity> {
        if (ids.isEmpty()) return emptyList()
        return jellyfinDao.getItemsByIds(ids).filterAllowed(accessPolicy)
    }

    suspend fun getContinueWatchingLocal(limit: Int = 20): List<JellyfinMediaEntity> {
        val raw = jellyfinDao.getContinueWatchingLocal(limit * 2)
        val seenKeys = mutableSetOf<String>()
        val filtered = raw.filter { item ->
            val key = item.seriesId ?: item.id
            seenKeys.add(key)
        }.take(limit)
        return filtered.deduplicateMediaEntities().filterAllowed(accessPolicy)
    }

    suspend fun getLatestLocal(serverUrl: String = "", userId: String = "", limit: Int = 20): List<JellyfinMediaEntity> {
        val cacheKey = "$serverUrl-$userId"
        MediaCaches.serverLatestCache.get(cacheKey)?.let { return it.take(limit) }
        return jellyfinDao.getLatestLocal(limit).deduplicateMediaEntities().filterAllowed(accessPolicy)
    }

    suspend fun getTopMoviesLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        jellyfinDao.getTopMoviesLocal(limit).deduplicateMediaEntities().filterAllowed(accessPolicy)

    suspend fun getTopSeriesLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        jellyfinDao.getTopSeriesLocal(limit).deduplicateMediaEntities().filterAllowed(accessPolicy)

    suspend fun getTopRatedLocal(limit: Int = 20): List<JellyfinMediaEntity> =
        jellyfinDao.getTopRatedLocal(limit).deduplicateMediaEntities().filterAllowed(accessPolicy)

    suspend fun searchLocalMedia(query: String, limit: Int = 40): List<JellyfinMediaEntity> =
        jellyfinDao.searchLocalMedia(query.trim(), limit).deduplicateMediaEntities().filterAllowed(accessPolicy)

    suspend fun getAllLocalTitles(): List<String> = jellyfinDao.getAllLocalTitles()

    suspend fun getLocalCount(): Int = jellyfinDao.getCount()

    fun getLibraryStatsFlow(): Flow<LibraryStats> = jellyfinDao.getLibraryStatsFlow()

    suspend fun getFavoritesLocal(): List<JellyfinMediaEntity> =
        jellyfinDao.getFavorites().deduplicateMediaEntities().filterAllowed(accessPolicy)

    fun getFavoritesFlow(): Flow<List<JellyfinMediaEntity>> =
        jellyfinDao.getFavoritesFlow().map { it.filterAllowed(accessPolicy) }

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
            val cleanTitle = title.trim().substringBefore(":").substringBefore("-").trim()
            if (year != null && year > 1900) {
                val matchedByTitleAndYear = jellyfinDao.findByTitleAndYear(cleanTitle, year)
                    ?: jellyfinDao.findByTitleAndYear(title.trim(), year)
                if (matchedByTitleAndYear != null) return matchedByTitleAndYear
            }
            val matchedByTitle = jellyfinDao.findByTitle(cleanTitle)
                ?: jellyfinDao.findByTitle(title.trim())
                ?: jellyfinDao.findByFuzzyTitle(cleanTitle)
            if (matchedByTitle != null) return matchedByTitle
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

    suspend fun getItemsByGenre(genre: String): List<JellyfinMediaEntity> {
        return jellyfinDao.getItemsByGenre(genre).filterAllowed(accessPolicy)
    }

    suspend fun getMoviesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity> {
        return jellyfinDao.getMoviesByGenre(genre, limit).filterAllowed(accessPolicy)
    }

    suspend fun getSeriesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity> {
        return jellyfinDao.getSeriesByGenre(genre, limit).filterAllowed(accessPolicy)
    }

    suspend fun getRecentMoviesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity> {
        return jellyfinDao.getRecentMoviesByGenre(genre, limit).filterAllowed(accessPolicy)
    }

    suspend fun getRecentSeriesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity> {
        return jellyfinDao.getRecentSeriesByGenre(genre, limit).filterAllowed(accessPolicy)
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

        // Recoger candidatos de cada género por separado, luego intercalar
        val buckets: List<List<JellyfinMediaEntity>> = topGenres.map { genre ->
            jellyfinDao.getItemsByGenre(genre, limit = maxPerGenre * 3).filterAllowed(accessPolicy) // pedir más de los que necesitamos para tener margen
                .filter {
                    it.type.equals("Movie", ignoreCase = true)
                        && it.id !in addedIds
                        && !it.isPlayed  // ya vista → no mostrar
                        && (it.communityRating ?: 0f) >= 5.5f // calidad mínima
                }
                .take(maxPerGenre)
        }

        // Intercalar: primero un item de cada género, luego el segundo de cada género, etc.
        // Resultado: Acción, Drama, Thriller, Comedia, Acción, Drama, Thriller, Comedia...
        // En vez de: Comedia, Comedia, Comedia, Comedia, Acción, Acción...
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

        // Fallback: rellenar con películas bien valoradas no mostradas aún (evitando escanear toda la DB)
        if (resultList.size < limit) {
            jellyfinDao.getTopMoviesLocal(limit = 50).filterAllowed(accessPolicy)
                .filter { it.id !in addedIds && !it.isPlayed && (it.communityRating ?: 0f) >= 6.0f }
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
