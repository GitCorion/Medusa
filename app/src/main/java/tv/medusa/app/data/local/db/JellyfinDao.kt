package tv.medusa.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JellyfinDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(items: List<JellyfinMediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: JellyfinMediaEntity)

    @Query("SELECT * FROM jellyfin_media")
    fun getAllItems(): Flow<List<JellyfinMediaEntity>>

    @Query("SELECT * FROM jellyfin_media WHERE id IN (SELECT mediaId FROM allowed_media_ids)")
    fun getAllItemsRestricted(): Flow<List<JellyfinMediaEntity>>

    @Query("SELECT * FROM jellyfin_media WHERE tmdbId = :tmdbId ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByTmdbId(tmdbId: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE imdbId = :imdbId ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByImdbId(imdbId: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE (:tmdbId IS NOT NULL AND tmdbId = :tmdbId) OR (:imdbId IS NOT NULL AND imdbId = :imdbId) ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByTmdbOrImdb(tmdbId: String?, imdbId: String?): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE (titleLower = LOWER(:title) OR originalTitleLower = LOWER(:title) OR seriesNameLower = LOWER(:title)) AND productionYear = :year ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByTitleAndYear(title: String, year: Int): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE titleLower = LOWER(:title) OR originalTitleLower = LOWER(:title) OR seriesNameLower = LOWER(:title) ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByTitle(title: String): JellyfinMediaEntity?

    /**
     * Último recurso de emparejamiento por subcadena de título, **acotado por año**.
     *
     * El `LIKE '%título%'` era demasiado laxo: un título de TMDB como "Super Mario" podía colarse
     * en la fila de una plataforma ajena al coincidir con la subcadena de "Super Mario 3". Ahora
     * solo se acepta la coincidencia si el año es compatible (mismo año, ±1 o desconocido en
     * alguno de los dos lados) y, en empate, se prioriza el título local más corto (la coincidencia
     * más ajustada) sobre la coincidencia más larga.
     */
    @Query(
        """
        SELECT * FROM jellyfin_media
        WHERE (LOWER(title) LIKE '%' || LOWER(:title) || '%'
            OR LOWER(originalTitle) LIKE '%' || LOWER(:title) || '%'
            OR LOWER(seriesName) LIKE '%' || LOWER(:title) || '%')
          AND (:year IS NULL OR productionYear IS NULL OR ABS(productionYear - :year) <= 1)
        ORDER BY
            CASE WHEN :year IS NOT NULL AND productionYear = :year THEN 0
                 WHEN productionYear IS NULL THEN 1
                 ELSE 2 END,
            LENGTH(title) ASC
        LIMIT 1
        """
    )
    suspend fun findByFuzzyTitle(title: String, year: Int?): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE seriesId = :seriesId AND type = 'Episode'")
    suspend fun getEpisodesForSeries(seriesId: String): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE (titleLower = LOWER(:title) OR originalTitleLower = LOWER(:title)) AND type = 'Series' LIMIT 1")
    suspend fun getSeriesByTitle(title: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND (LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(originalTitle) LIKE '%' || LOWER(:query) || '%') ORDER BY communityRating DESC LIMIT :limit")
    suspend fun searchLocalMedia(query: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND (LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(originalTitle) LIKE '%' || LOWER(:query) || '%') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY communityRating DESC LIMIT :limit")
    suspend fun searchLocalMediaRestricted(query: String, limit: Int = 40): List<JellyfinMediaEntity>

    /**
     * Búsqueda de texto completo (FTS4) sobre título y título original. [:match] debe venir ya
     * formateado como expresión MATCH (ver `LocalMediaRepository.buildFtsQuery`).
     */
    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        JOIN jellyfin_media_fts ON jm.rowid = jellyfin_media_fts.rowid
        WHERE jellyfin_media_fts MATCH :match
        AND jm.type IN ('Movie', 'Series')
        ORDER BY jm.communityRating DESC
        LIMIT :limit
        """
    )
    suspend fun searchLocalMediaFts(match: String, limit: Int = 40): List<JellyfinMediaEntity>

    /**
     * Búsqueda FTS4 restringida a los IDs permitidos (control parental).
     */
    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        JOIN jellyfin_media_fts ON jm.rowid = jellyfin_media_fts.rowid
        WHERE jellyfin_media_fts MATCH :match
        AND jm.type IN ('Movie', 'Series')
        AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY jm.communityRating DESC
        LIMIT :limit
        """
    )
    suspend fun searchLocalMediaFtsRestricted(match: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT DISTINCT title FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND title IS NOT NULL AND title != '' ORDER BY title ASC")
    suspend fun getAllLocalTitles(): List<String>

    @Query("SELECT id FROM jellyfin_media")
    suspend fun getAllIds(): List<String>

    @Query("SELECT COUNT(*) FROM jellyfin_media")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT * FROM jellyfin_media WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE id IN (:ids)")
    suspend fun getItemsByIds(ids: List<String>): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE id IN (:ids) AND id IN (SELECT mediaId FROM allowed_media_ids)")
    suspend fun getItemsByIdsRestricted(ids: List<String>): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' ORDER BY title ASC")
    suspend fun getMovies(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY title ASC")
    suspend fun getMoviesRestricted(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' ORDER BY title ASC")
    suspend fun getSeries(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY title ASC")
    suspend fun getSeriesRestricted(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series')")
    suspend fun getAllCatalog(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND id IN (SELECT mediaId FROM allowed_media_ids)")
    suspend fun getAllCatalogRestricted(): List<JellyfinMediaEntity>

    /**
     * Catálogo ligero para el motor de etiquetas de Medusa. El índice de co-ocurrencia necesita
     * TODAS las filas (Movie/Series) para el recuento y la matriz, pero NO las columnas pesadas:
     * se omiten `overview` (el grueso del payload, párrafos de texto por cada una de las ~33k
     * filas), `originalTitle`, los ids de scrapers y `officialRating`. Los ítems resultantes
     * siguen sirviendo para el motor y para `toOptimizedMediaItem` (que solo lee título, tipo,
     * imagen, rating, año, progreso, favorito y datos de serie/episodio).
     */
    @Query(
        """
        SELECT id, title, NULL AS originalTitle, type,
               NULL AS tmdbId, NULL AS imdbId, NULL AS tvdbId,
               NULL AS overview, primaryImageTag, backdropImageTag,
               communityRating, productionYear, genres,
               NULL AS officialRating, tags, isPlayed, playbackPositionTicks,
               isFavorite, seriesId, seriesName, seriesPrimaryImageTag,
               seasonNumber, episodeNumber, totalItemCount, unplayedItemCount,
               0 AS lastPlayedTicks,
               '' AS titleLower, '' AS originalTitleLower, '' AS seriesNameLower
        FROM jellyfin_media
        WHERE type IN ('Movie', 'Series')
        """
    )
    suspend fun getCatalogForMatrix(): List<JellyfinMediaEntity>

    /**
     * "Continuar viendo" local: lo que está a medias (`playbackPositionTicks > 0`) **más** los
     * siguientes capítulos de las series que se están viendo (NextUp, cuya fecha se hereda de su
     * serie porque el episodio en sí todavía no se ha reproducido).
     *
     * Las filas de tipo `Series` quedan fuera del segundo grupo a propósito: su fecha es la de la
     * serie, no la de un capítulo pendiente, y se colarían como tarjeta de serie en la fila.
     *
     * En empate de fecha gana el que está a medias (el capítulo que dejaste por la mitad) frente al
     * siguiente capítulo de la misma serie; el desduplicado por serie lo hace después el repositorio.
     */
    @Query("SELECT * FROM jellyfin_media WHERE isPlayed = 0 AND (playbackPositionTicks > 0 OR (lastPlayedTicks > 0 AND type IN ('Movie', 'Episode'))) AND type IN ('Movie', 'Episode', 'Series') ORDER BY lastPlayedTicks DESC, (playbackPositionTicks > 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getContinueWatchingLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE isPlayed = 0 AND (playbackPositionTicks > 0 OR (lastPlayedTicks > 0 AND type IN ('Movie', 'Episode'))) AND type IN ('Movie', 'Episode', 'Series') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY lastPlayedTicks DESC, (playbackPositionTicks > 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getContinueWatchingLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    /**
     * Fechas reales de última reproducción ya guardadas para esos ids (series o ítems concretos).
     * Sirve para dos cosas: heredar la fecha de una serie en los capítulos de NextUp, y no pisar con
     * un `0` la fecha que ya había cuando el servidor no la manda.
     */
    @Query("SELECT id, lastPlayedTicks FROM jellyfin_media WHERE lastPlayedTicks > 0 AND id IN (:ids)")
    suspend fun getKnownLastPlayedTicks(ids: Collection<String>): List<LastPlayedTicksRow>

    /**
     * Fecha real de última reproducción **por serie**, tomando el máximo entre la propia fila de la
     * serie y las de sus capítulos ya reproducidos.
     *
     * Jellyfin **no** rellena `LastPlayedDate` en las series (`null` aunque se hayan visto
     * capítulos), así que mirar solo la fila de la serie nunca da fecha. Sin este dato, los ítems de
     * NextUp —cuyo episodio todavía no se ha reproducido y por tanto no trae fecha propia— se
     * quedaban sin fecha y caían al final de "Continuar viendo" aunque la serie se hubiera visto
     * minutos antes (p. ej. un capítulo terminado del todo).
     */
    @Query(
        """
        SELECT COALESCE(seriesId, id) AS id, MAX(lastPlayedTicks) AS lastPlayedTicks
        FROM jellyfin_media
        WHERE lastPlayedTicks > 0 AND (id IN (:seriesIds) OR seriesId IN (:seriesIds))
        GROUP BY COALESCE(seriesId, id)
        """
    )
    suspend fun getKnownSeriesLastPlayedTicks(seriesIds: Collection<String>): List<LastPlayedTicksRow>

    // Orden por rowid: el sync inserta los vistos en el orden que devuelve el servidor
    // (DatePlayed descendente), así que rowid DESC = visto más recientemente primero.
    @Query("SELECT * FROM jellyfin_media WHERE isPlayed = 1 AND type IN ('Movie', 'Series', 'Episode') ORDER BY rowid DESC LIMIT :limit")
    suspend fun getRecentlyPlayedLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE isPlayed = 1 AND type IN ('Movie', 'Series', 'Episode') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY rowid DESC LIMIT :limit")
    suspend fun getRecentlyPlayedLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("UPDATE jellyfin_media SET playbackPositionTicks = :positionTicks, isPlayed = :isPlayed, lastPlayedTicks = :lastPlayedTicks WHERE id = :itemId")
    suspend fun updatePlaybackPosition(itemId: String, positionTicks: Long, isPlayed: Boolean, lastPlayedTicks: Long = System.currentTimeMillis())

    // "Añadido recientemente" = orden de incorporación al servidor (rowid), no año de estreno.
    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') ORDER BY rowid DESC LIMIT :limit")
    suspend fun getLatestLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY rowid DESC LIMIT :limit")
    suspend fun getLatestLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    /**
     * "Últimos estrenos": lo más reciente por **año de estreno** que hay en la biblioteca,
     * independientemente de cuándo se incorporó al servidor. Es distinto de [getLatestLocal]
     * (añadido recientemente) y de [getTopRatedLocal] (mejor valorado).
     */
    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') ORDER BY COALESCE(productionYear, 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getNewReleasesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY COALESCE(productionYear, 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getNewReleasesLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') ORDER BY CASE WHEN communityRating IS NOT NULL AND communityRating > 0 THEN communityRating ELSE 0 END DESC, COALESCE(productionYear, 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getTopRatedLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY CASE WHEN communityRating IS NOT NULL AND communityRating > 0 THEN communityRating ELSE 0 END DESC, COALESCE(productionYear, 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getTopRatedLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' ORDER BY (isPlayed = 0 AND playbackPositionTicks = 0) DESC, CASE WHEN communityRating IS NOT NULL AND communityRating > 0 THEN communityRating ELSE 0 END DESC, COALESCE(productionYear, 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getTopMoviesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY (isPlayed = 0 AND playbackPositionTicks = 0) DESC, CASE WHEN communityRating IS NOT NULL AND communityRating > 0 THEN communityRating ELSE 0 END DESC, COALESCE(productionYear, 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getTopMoviesLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' ORDER BY (isPlayed = 0 AND playbackPositionTicks = 0) DESC, CASE WHEN communityRating IS NOT NULL AND communityRating > 0 THEN communityRating ELSE 0 END DESC, COALESCE(productionYear, 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getTopSeriesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY (isPlayed = 0 AND playbackPositionTicks = 0) DESC, CASE WHEN communityRating IS NOT NULL AND communityRating > 0 THEN communityRating ELSE 0 END DESC, COALESCE(productionYear, 0) DESC, rowid DESC LIMIT :limit")
    suspend fun getTopSeriesLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND (communityRating IS NULL OR communityRating <= 9.5) AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getItemsByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND (communityRating IS NULL OR communityRating <= 9.5) AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getItemsByGenreRestricted(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getMoviesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getMoviesByGenreRestricted(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getSeriesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getSeriesByGenreRestricted(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY productionYear DESC, communityRating DESC LIMIT :limit")
    suspend fun getRecentMoviesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY productionYear DESC, communityRating DESC LIMIT :limit")
    suspend fun getRecentMoviesByGenreRestricted(genre: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY productionYear DESC, communityRating DESC LIMIT :limit")
    suspend fun getRecentSeriesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY productionYear DESC, communityRating DESC LIMIT :limit")
    suspend fun getRecentSeriesByGenreRestricted(genre: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE isFavorite = 1 ORDER BY title ASC")
    suspend fun getFavorites(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE isFavorite = 1 AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY title ASC")
    suspend fun getFavoritesRestricted(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoritesFlow(): Flow<List<JellyfinMediaEntity>>

    @Query("SELECT * FROM jellyfin_media WHERE isFavorite = 1 AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY title ASC")
    fun getFavoritesFlowRestricted(): Flow<List<JellyfinMediaEntity>>

    @Query("SELECT genres FROM jellyfin_media WHERE (isPlayed = 1 OR playbackPositionTicks > 0) AND genres IS NOT NULL AND genres != '' LIMIT 300")
    suspend fun getWatchedGenres(): List<String>

    @Query("SELECT genres FROM jellyfin_media WHERE genres IS NOT NULL AND genres != '' LIMIT 500")
    suspend fun getAllGenresRaw(): List<String>

    @Query("SELECT COUNT(*) FROM jellyfin_media")
    suspend fun getCount(): Int

    /**
     * Estadísticas de la biblioteca en una sola consulta (antes eran 5 `Flow` de conteo
     * observados en paralelo → 5 COUNT/SUM por cada escritura en la tabla).
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type IN ('Movie','Series') THEN 1 ELSE 0 END), 0) AS total,
            COALESCE(SUM(CASE WHEN type = 'Movie' THEN 1 ELSE 0 END), 0) AS movies,
            COALESCE(SUM(CASE WHEN type = 'Series' THEN 1 ELSE 0 END), 0) AS series,
            COALESCE(SUM(CASE WHEN type = 'Episode' THEN 1 ELSE 0 END), 0) AS episodesDirect,
            COALESCE(SUM(CASE WHEN type = 'Series' THEN totalItemCount ELSE 0 END), 0) AS totalSeriesEpisodes
        FROM jellyfin_media
        """
    )
    fun getLibraryStatsFlow(): Flow<LibraryStats>

    @Query("UPDATE jellyfin_media SET isFavorite = :isFavorite WHERE id = :itemId")
    suspend fun updateFavoriteStatus(itemId: String, isFavorite: Boolean)

    @Query("UPDATE jellyfin_media SET isPlayed = :isPlayed, playbackPositionTicks = 0, unplayedItemCount = CASE WHEN :isPlayed = 1 THEN 0 ELSE totalItemCount END WHERE id = :itemId")
    suspend fun updatePlayedStatus(itemId: String, isPlayed: Boolean)

    @Query("DELETE FROM jellyfin_media")
    suspend fun clearAll()

    @Query("DELETE FROM jellyfin_media WHERE id LIKE 'demo_%'")
    suspend fun deleteDemoItems()

    @Query("SELECT id, overview, backdropImageTag FROM jellyfin_media WHERE overview IS NOT NULL")
    suspend fun getCachedOverviews(): List<CachedOverviewDto>

    @Query("SELECT id FROM jellyfin_media WHERE overview IS NULL OR overview = '' LIMIT :limit")
    suspend fun getIdsMissingOverview(limit: Int = 50): List<String>

    @Query("UPDATE jellyfin_media SET overview = :overview WHERE id = :id AND (overview IS NULL OR overview = '')")
    suspend fun updateOverview(id: String, overview: String)

    @Query("SELECT * FROM jellyfin_media WHERE (primaryImageTag IS NULL OR primaryImageTag = '') AND tmdbId IS NOT NULL AND tmdbId != '' LIMIT :limit")
    suspend fun getItemsMissingPoster(limit: Int = 50): List<JellyfinMediaEntity>

    @Query("UPDATE jellyfin_media SET primaryImageTag = :posterTag WHERE id = :id AND (primaryImageTag IS NULL OR primaryImageTag = '')")
    suspend fun updatePrimaryImageTag(id: String, posterTag: String)
}

data class CachedOverviewDto(
    val id: String,
    val overview: String?,
    val backdropImageTag: String? = null
)

data class LibraryStats(
    val total: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
    val episodesDirect: Int = 0,
    val totalSeriesEpisodes: Int = 0
) {
    /** Episodios mostrados: el mayor entre los contados directamente y la suma de episodios de series. */
    val episodes: Int get() = maxOf(episodesDirect, totalSeriesEpisodes)
}

/** Proyección mínima para leer fechas de última reproducción sin cargar la fila entera. */
data class LastPlayedTicksRow(
    val id: String,
    val lastPlayedTicks: Long
)

/**
 * Evita que un ítem que llega **sin** fecha del servidor borre la fecha ya guardada en Room:
 * `insertOrUpdateAll` es un REPLACE, así que un `lastPlayedTicks = 0` pisaría el valor real.
 *
 * Solo copia fechas que **ya existían**: nunca inventa una nueva. Los ids se consultan en bloques
 * para no acercarse al límite de variables de SQLite (999).
 */
internal suspend fun JellyfinDao.preserveKnownLastPlayedDates(
    items: List<JellyfinMediaEntity>
): List<JellyfinMediaEntity> {
    val undated = items.filter { it.lastPlayedTicks <= 0L }
    if (undated.isEmpty()) return items

    val known = HashMap<String, Long>()
    undated.map { it.id }.distinct().chunked(MAX_SQL_IDS_PER_QUERY).forEach { batch ->
        getKnownLastPlayedTicks(batch).forEach { known[it.id] = it.lastPlayedTicks }
    }
    if (known.isEmpty()) return items

    return items.map { item ->
        val saved = known[item.id]
        if (item.lastPlayedTicks <= 0L && saved != null && saved > 0L) {
            item.copy(lastPlayedTicks = saved)
        } else {
            item
        }
    }
}

/** Variante de un solo ítem, para los sitios que guardan de uno en uno. */
internal suspend fun JellyfinDao.preserveKnownLastPlayedDate(
    item: JellyfinMediaEntity
): JellyfinMediaEntity = preserveKnownLastPlayedDates(listOf(item)).first()

private const val MAX_SQL_IDS_PER_QUERY = 500
