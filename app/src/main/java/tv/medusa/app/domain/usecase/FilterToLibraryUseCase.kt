package tv.medusa.app.domain.usecase

import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.remote.tmdb.TmdbItemDto
import tv.medusa.app.data.remote.trakt.TraktMediaDto
import tv.medusa.app.data.repository.MediaRepository

/**
 * Resultado del cruce de un título de TMDB contra la biblioteca: la entidad local lista para
 * pintar/reproducir y el ítem original de TMDB (popularidad, voto, posición) que usa el
 * indexador de colecciones para ordenar.
 */
data class TmdbMatch(
    val entity: JellyfinMediaEntity,
    val source: TmdbItemDto,
    val position: Int
)

class FilterToLibraryUseCase(
    private val mediaRepository: MediaRepository
) {
    /**
     * Filters Trakt items against the user's Jellyfin library.
     * Uses strictly local SQLite (Room) index lookups (0.1ms per item).
     * NEVER sends remote search queries to Jellyfin to prevent server CPU overload.
     */
    suspend fun filterTraktItems(
        traktItems: List<TraktMediaDto>,
        serverUrl: String = "",
        userId: String = "",
        token: String = "",
        maxCandidates: Int = 20
    ): List<JellyfinMediaEntity> {
        val seenIds = mutableSetOf<String>()
        val results = mutableListOf<JellyfinMediaEntity>()

        for (item in traktItems) {
            if (results.size >= maxCandidates) break
            val tmdbId = item.ids.tmdb?.toString()
            val imdbId = item.ids.imdb
            val title = item.title
            val year = item.year

            val entity = mediaRepository.findLocal(
                tmdbId = tmdbId,
                imdbId = imdbId,
                title = title,
                year = year
            )
            if (entity != null && seenIds.add(entity.id)) {
                results.add(entity)
            }
        }
        return results
    }

    /**
     * Filtra ítems de TMDb (Trending, Streaming Providers, Recommendations) contra la
     * biblioteca de Jellyfin y conserva el ítem original de TMDB para poder indexar por
     * popularidad/posición.
     *
     * El cruce principal es por lotes: una consulta `tmdbId IN (...)` resuelve cientos de
     * candidatos de golpe. Solo los pocos que no tengan TMDB ID en la biblioteca caen al
     * fallback individual (título + año + fuzzy), que sigue siendo estrictamente local.
     */
    suspend fun filterTmdbItemsWithSource(
        tmdbItems: List<TmdbItemDto>,
        serverUrl: String = "",
        userId: String = "",
        token: String = "",
        maxCandidates: Int = 20
    ): List<TmdbMatch> {
        if (tmdbItems.isEmpty() || maxCandidates <= 0) return emptyList()

        data class Parsed(
            val item: TmdbItemDto,
            val position: Int,
            val isTv: Boolean,
            val tmdbId: String,
            val title: String?,
            val originalTitle: String?,
            val year: Int?
        )

        val parsed = tmdbItems.mapIndexed { index, item ->
            Parsed(
                item = item,
                position = index,
                isTv = !item.name.isNullOrBlank() || item.firstAirDate != null,
                tmdbId = item.id.toString(),
                title = item.title ?: item.name,
                originalTitle = item.originalTitle ?: item.originalName,
                year = (item.releaseDate ?: item.firstAirDate)?.take(4)?.toIntOrNull()
            )
        }

        // 1. Cruce por lotes: una consulta para cientos de TMDB IDs.
        val byTmdbId: Map<String, List<JellyfinMediaEntity>> = runCatching {
            mediaRepository.findLocalByTmdbIds(parsed.map { it.tmdbId }.distinct())
        }.getOrDefault(emptyList())
            .filter { !it.tmdbId.isNullOrBlank() }
            .groupBy { it.tmdbId!! }

        // 2. Fallback por título también por lotes: cubre bibliotecas sin TMDB ID sin hacer
        //    una consulta por título (lo que haría inviable indexar miles de candidatos).
        val unmatchedForTitle = parsed.filter { p ->
            byTmdbId[p.tmdbId].isNullOrEmpty() && !p.title.isNullOrBlank()
        }
        val byTitle: Map<String, List<JellyfinMediaEntity>> = if (unmatchedForTitle.isNotEmpty()) {
            runCatching {
                mediaRepository.findLocalByTitles(
                    unmatchedForTitle.mapNotNull { it.title }.distinct()
                )
            }.getOrDefault(emptyList()).groupBy { normalizeTitle(it.title) }
        } else {
            emptyMap()
        }

        // El fallback individual (fuzzy incluido) solo se usa en listas pequeñas de la UI.
        // Para el catálogo completo del indexador sería miles de queries: ahí manda el lote.
        val allowPerItemFallback = tmdbItems.size <= 500

        val seenIds = mutableSetOf<String>()
        val results = mutableListOf<TmdbMatch>()

        for (p in parsed) {
            if (results.size >= maxCandidates) break

            // 2a. Match directo por ID, respetando el tipo (Movie/BoxSet vs Series/Episode).
            val typed = byTmdbId[p.tmdbId]
                ?.firstOrNull { matchesType(it, p.isTv) }
                ?.takeIf { p.title.isNullOrBlank() || isPlausibleTitle(p.title, it) }
            var entity = typed

            // 2b. Match por título por lotes (sin queries individuales).
            if (entity == null && !p.title.isNullOrBlank()) {
                entity = byTitle[normalizeTitle(p.title)]
                    ?.firstOrNull { candidate ->
                        matchesType(candidate, p.isTv) &&
                            isYearCompatible(candidate, p.year) &&
                            isPlausibleTitle(p.title, candidate)
                    }
            }
            if (entity == null && !p.originalTitle.isNullOrBlank()) {
                entity = byTitle[normalizeTitle(p.originalTitle)]
                    ?.firstOrNull { candidate ->
                        matchesType(candidate, p.isTv) &&
                            isYearCompatible(candidate, p.year) &&
                            isPlausibleTitle(p.originalTitle, candidate)
                    }
            }

            // 2c. Último recurso individual: título exacto + año, título y fuzzy.
            //     Se exige compatibilidad de tipo: una película de TMDB no puede emparejar con
            //     una serie de la biblioteca solo porque compartan título (caso "Resident Evil").
            if (entity == null && allowPerItemFallback && !p.title.isNullOrBlank()) {
                val found = mediaRepository.findLocal(
                    tmdbId = p.tmdbId,
                    imdbId = null,
                    title = p.title,
                    year = p.year,
                    isTv = p.isTv
                )
                if (found != null && matchesType(found, p.isTv)) {
                    entity = found
                }
                if (entity == null && !p.originalTitle.isNullOrBlank() && p.originalTitle != p.title) {
                    val foundOriginal = mediaRepository.findLocal(
                        tmdbId = p.tmdbId,
                        imdbId = null,
                        title = p.originalTitle,
                        year = p.year,
                        isTv = p.isTv
                    )
                    if (foundOriginal != null && matchesType(foundOriginal, p.isTv)) {
                        entity = foundOriginal
                    }
                }
            }
            if (entity == null) continue

            // 4. Un episodio se resuelve a su serie padre para pintar la tarjeta correcta.
            if (entity.type.equals("Episode", ignoreCase = true)) {
                entity = mediaRepository.findLocal(
                    tmdbId = p.tmdbId,
                    imdbId = null,
                    title = p.title,
                    year = p.year,
                    isTv = true
                ) ?: entity
            }

            if (!seenIds.add(entity.id)) continue

            var updated = entity
            if (p.item.voteAverage != null && p.item.voteAverage > 0f) {
                updated = updated.copy(communityRating = p.item.voteAverage)
            }
            if (updated.primaryImageTag.isNullOrEmpty() && !p.item.posterPath.isNullOrBlank()) {
                updated = updated.copy(primaryImageTag = "tmdb:${p.item.posterPath}")
            }
            if (updated.backdropImageTag.isNullOrEmpty() && !p.item.backdropPath.isNullOrBlank()) {
                updated = updated.copy(backdropImageTag = "tmdb:${p.item.backdropPath}")
            }
            results.add(TmdbMatch(entity = updated, source = p.item, position = p.position))
        }
        return results
    }

    suspend fun filterTmdbItems(
        tmdbItems: List<TmdbItemDto>,
        serverUrl: String = "",
        userId: String = "",
        token: String = "",
        maxCandidates: Int = 20
    ): List<JellyfinMediaEntity> =
        filterTmdbItemsWithSource(tmdbItems, serverUrl, userId, token, maxCandidates)
            .map { it.entity }

    private fun matchesType(entity: JellyfinMediaEntity, isTv: Boolean): Boolean =
        if (isTv) {
            entity.type.equals("Series", ignoreCase = true) ||
                entity.type.equals("Episode", ignoreCase = true)
        } else {
            entity.type.equals("Movie", ignoreCase = true) ||
                entity.type.equals("BoxSet", ignoreCase = true)
        }

    private fun isPlausibleTitle(query: String, entity: JellyfinMediaEntity): Boolean {
        val cleanQuery = query.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        if (cleanQuery.isEmpty()) return true
        val cleanMatched = entity.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanOrig = (entity.originalTitle ?: "").trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return cleanMatched.contains(cleanQuery) || cleanQuery.contains(cleanMatched) ||
            (cleanOrig.isNotEmpty() &&
                (cleanOrig.contains(cleanQuery) || cleanQuery.contains(cleanOrig)))
    }

    private fun normalizeTitle(title: String): String = title.trim().lowercase()

    private fun isYearCompatible(entity: JellyfinMediaEntity, year: Int?): Boolean {
        if (year == null || entity.productionYear == null) return true
        return kotlin.math.abs(entity.productionYear - year) <= 1
    }
}
