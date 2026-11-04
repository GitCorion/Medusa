package tv.medusa.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
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

    /**
     * Cruce por lotes para el descubrimiento: una sola consulta resuelve cientos de IDs TMDB
     * en vez de una query por título. El llamador debe trocear en bloques &lt; 999 (límite de
     * variables de SQLite).
     */
    @Query("SELECT * FROM jellyfin_media WHERE tmdbId IN (:tmdbIds)")
    suspend fun findByTmdbIds(tmdbIds: List<String>): List<JellyfinMediaEntity>

    /** Fallback por lotes por título normalizado (`titleLower`) para bibliotecas sin TMDB ID. */
    @Query("SELECT * FROM jellyfin_media WHERE titleLower IN (:titles)")
    suspend fun findByTitles(titles: List<String>): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Series', 'Episode') AND ((:tmdbId IS NOT NULL AND tmdbId = :tmdbId) OR (:imdbId IS NOT NULL AND imdbId = :imdbId)) ORDER BY CASE WHEN type = 'Series' THEN 0 ELSE 1 END LIMIT 1")
    suspend fun findTvByTmdbOrImdb(tmdbId: String?, imdbId: String?): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'BoxSet') AND ((:tmdbId IS NOT NULL AND tmdbId = :tmdbId) OR (:imdbId IS NOT NULL AND imdbId = :imdbId)) LIMIT 1")
    suspend fun findMovieByTmdbOrImdb(tmdbId: String?, imdbId: String?): JellyfinMediaEntity?

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

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
          AND (LOWER(jm.title) LIKE '%' || LOWER(:query) || '%' OR LOWER(jm.originalTitle) LIKE '%' || LOWER(:query) || '%')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun searchLocalMedia(query: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
          AND (LOWER(jm.title) LIKE '%' || LOWER(:query) || '%' OR LOWER(jm.originalTitle) LIKE '%' || LOWER(:query) || '%')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun searchLocalMediaRestricted(query: String, limit: Int = 40): List<JellyfinMediaEntity>

    /**
     * Búsqueda de texto completo (FTS4) sobre título y título original. [:match] debe venir ya
     * formateado como expresión MATCH (ver `LocalMediaRepository.buildFtsQuery`).
     */
    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        JOIN jellyfin_media_fts ON jm.rowid = jellyfin_media_fts.rowid
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jellyfin_media_fts MATCH :match
        AND jm.type IN ('Movie', 'Series')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
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
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jellyfin_media_fts MATCH :match
        AND jm.type IN ('Movie', 'Series')
        AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
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
        SELECT jm.id, jm.title, NULL AS originalTitle, jm.type,
               NULL AS tmdbId, NULL AS imdbId, NULL AS tvdbId,
               NULL AS overview, jm.primaryImageTag, jm.backdropImageTag,
               jm.communityRating, jm.productionYear, jm.genres,
               NULL AS officialRating, jm.tags, jm.isPlayed, jm.playbackPositionTicks,
               jm.isFavorite, jm.seriesId, jm.seriesName, jm.seriesPrimaryImageTag,
               jm.seasonNumber, jm.episodeNumber, jm.totalItemCount, jm.unplayedItemCount,
               0 AS lastPlayedTicks,
               '' AS titleLower, '' AS originalTitleLower, '' AS seriesNameLower,
               NULL AS premiereDateMs
        FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        """
    )
    suspend fun getCatalogForMatrix(): List<JellyfinMediaEntity>

    /**
     * Misma proyección ligera que [getCatalogForMatrix], pero limitada a los IDs permitidos.
     */
    @Query(
        """
        SELECT jm.id, jm.title, NULL AS originalTitle, jm.type,
               NULL AS tmdbId, NULL AS imdbId, NULL AS tvdbId,
               NULL AS overview, jm.primaryImageTag, jm.backdropImageTag,
               jm.communityRating, jm.productionYear, jm.genres,
               NULL AS officialRating, jm.tags, jm.isPlayed, jm.playbackPositionTicks,
               jm.isFavorite, jm.seriesId, jm.seriesName, jm.seriesPrimaryImageTag,
               jm.seasonNumber, jm.episodeNumber, jm.totalItemCount, jm.unplayedItemCount,
               0 AS lastPlayedTicks,
               '' AS titleLower, '' AS originalTitleLower, '' AS seriesNameLower,
               NULL AS premiereDateMs
        FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        """
    )
    suspend fun getCatalogForMatrixRestricted(): List<JellyfinMediaEntity>

    /**
     * Candidatos de un evento estacional, filtrados **en SQLite** por género/título. Devuelve
     * solo la proyección ligera (unas 3.500 filas para Halloween), no las 32.000 del catálogo.
     */
    @RawQuery
    suspend fun getSeasonalCandidates(query: SupportSQLiteQuery): List<JellyfinMediaEntity>

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
    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') ORDER BY COALESCE(premiereDateMs, (CAST(COALESCE(productionYear, 1970) AS INTEGER) - 1970) * 31536000000) DESC, rowid DESC LIMIT :limit")
    suspend fun getNewReleasesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY COALESCE(premiereDateMs, (CAST(COALESCE(productionYear, 1970) AS INTEGER) - 1970) * 31536000000) DESC, rowid DESC LIMIT :limit")
    suspend fun getNewReleasesLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getTopRatedLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getTopRatedLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Movie'
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getTopMoviesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Movie'
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getTopMoviesLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Series'
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getTopSeriesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Series'
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getTopSeriesLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND isPlayed = 1 AND (isFavorite = 1 OR (communityRating IS NOT NULL AND communityRating >= 7.5)) AND (backdropImageTag IS NOT NULL AND backdropImageTag != '') ORDER BY isFavorite DESC, lastPlayedTicks ASC, communityRating DESC LIMIT :limit")
    suspend fun getRewatchableMoviesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND isPlayed = 1 AND (isFavorite = 1 OR (communityRating IS NOT NULL AND communityRating >= 7.5)) AND (backdropImageTag IS NOT NULL AND backdropImageTag != '') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY isFavorite DESC, lastPlayedTicks ASC, communityRating DESC LIMIT :limit")
    suspend fun getRewatchableMoviesLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'BoxSet' AND ((backdropImageTag IS NOT NULL AND backdropImageTag != '') OR (primaryImageTag IS NOT NULL AND primaryImageTag != '')) ORDER BY (backdropImageTag IS NOT NULL AND backdropImageTag != '') DESC, rowid DESC LIMIT :limit")
    suspend fun getBoxSetsLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'BoxSet' AND ((backdropImageTag IS NOT NULL AND backdropImageTag != '') OR (primaryImageTag IS NOT NULL AND primaryImageTag != '')) AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY (backdropImageTag IS NOT NULL AND backdropImageTag != '') DESC, rowid DESC LIMIT :limit")
    suspend fun getBoxSetsLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    /**
     * Joyas ocultas / Obras maestras de tu colección que tienes pendientes (no vistas, playback=0).
     */
    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
          AND jm.isPlayed = 0 AND jm.playbackPositionTicks = 0
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getHiddenGemsLocal(limit: Int = 30): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
          AND jm.isPlayed = 0 AND jm.playbackPositionTicks = 0
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getHiddenGemsLocalRestricted(limit: Int = 30): List<JellyfinMediaEntity>

    /**
     * Búsqueda por afinidad de etiquetas (Tags/Keywords) para las filas semilla "Porque viste X".
     */
    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.id != :excludeId
          AND jm.type IN ('Movie', 'Series')
          AND (
            (:tag1 != '' AND LOWER(jm.tags) LIKE '%' || LOWER(:tag1) || '%') OR
            (:tag2 != '' AND LOWER(jm.tags) LIKE '%' || LOWER(:tag2) || '%') OR
            (:genre != '' AND LOWER(jm.genres) LIKE '%' || LOWER(:genre) || '%')
          )
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getSimilarByKeywords(tag1: String, tag2: String, genre: String, excludeId: String, limit: Int = 30): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.id != :excludeId
          AND jm.type IN ('Movie', 'Series')
          AND (
            (:tag1 != '' AND LOWER(jm.tags) LIKE '%' || LOWER(:tag1) || '%') OR
            (:tag2 != '' AND LOWER(jm.tags) LIKE '%' || LOWER(:tag2) || '%') OR
            (:genre != '' AND LOWER(jm.genres) LIKE '%' || LOWER(:genre) || '%')
          )
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          jm.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun getSimilarByKeywordsRestricted(tag1: String, tag2: String, genre: String, excludeId: String, limit: Int = 30): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE (LOWER(jm.genres) LIKE '%documental%' OR LOWER(jm.genres) LIKE '%documentary%')
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          COALESCE(jm.productionYear, 0) DESC
        LIMIT :limit
        """
    )
    suspend fun getDocumentariesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE (LOWER(jm.genres) LIKE '%documental%' OR LOWER(jm.genres) LIKE '%documentary%')
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC,
          COALESCE(jm.productionYear, 0) DESC
        LIMIT :limit
        """
    )
    suspend fun getDocumentariesLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Movie'
          AND (LOWER(jm.genres) LIKE '%' || LOWER(:pattern) || '%' OR LOWER(jm.tags) LIKE '%' || LOWER(:pattern) || '%')
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getNationalCinemaLocal(pattern: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Movie'
          AND (LOWER(jm.genres) LIKE '%' || LOWER(:pattern) || '%' OR LOWER(jm.tags) LIKE '%' || LOWER(:pattern) || '%')
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getNationalCinemaLocalRestricted(pattern: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Movie'
          AND (LOWER(jm.genres) LIKE '%españ%' OR LOWER(jm.tags) LIKE '%españ%' OR LOWER(jm.tags) LIKE '%spain%' OR LOWER(jm.tags) LIKE '%spanish%')
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getSpanishCinemaLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Movie'
          AND (LOWER(jm.genres) LIKE '%españ%' OR LOWER(jm.tags) LIKE '%españ%' OR LOWER(jm.tags) LIKE '%spain%' OR LOWER(jm.tags) LIKE '%spanish%')
          AND (jm.backdropImageTag IS NOT NULL AND jm.backdropImageTag != '')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getSpanishCinemaLocalRestricted(limit: Int = 20): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
          AND (LOWER(jm.genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(jm.title) LIKE '%' || LOWER(:genre) || '%')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getItemsByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type IN ('Movie', 'Series')
          AND (LOWER(jm.genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(jm.title) LIKE '%' || LOWER(:genre) || '%')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getItemsByGenreRestricted(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Movie'
          AND (LOWER(jm.genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(jm.title) LIKE '%' || LOWER(:genre) || '%')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getMoviesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Movie'
          AND (LOWER(jm.genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(jm.title) LIKE '%' || LOWER(:genre) || '%')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getMoviesByGenreRestricted(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Series'
          AND (LOWER(jm.genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(jm.title) LIKE '%' || LOWER(:genre) || '%')
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getSeriesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        LEFT JOIN (SELECT mediaId, MAX(score) AS maxScore FROM collection_items GROUP BY mediaId) ci ON ci.mediaId = jm.id
        WHERE jm.type = 'Series'
          AND (LOWER(jm.genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(jm.title) LIKE '%' || LOWER(:genre) || '%')
          AND jm.id IN (SELECT mediaId FROM allowed_media_ids)
        ORDER BY
          (COALESCE(ci.maxScore, 0.0) / (COALESCE(ci.maxScore, 0.0) + 12.0) * COALESCE(jm.communityRating, 6.0) +
           12.0 / (COALESCE(ci.maxScore, 0.0) + 12.0) * 6.5) DESC,
          COALESCE(ci.maxScore, 0.0) DESC,
          COALESCE(jm.communityRating, 0.0) DESC
        LIMIT :limit
        """
    )
    suspend fun getSeriesByGenreRestricted(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY COALESCE(premiereDateMs, (CAST(COALESCE(productionYear, 1970) AS INTEGER) - 1970) * 31536000000) DESC, communityRating DESC LIMIT :limit")
    suspend fun getRecentMoviesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY COALESCE(premiereDateMs, (CAST(COALESCE(productionYear, 1970) AS INTEGER) - 1970) * 31536000000) DESC, communityRating DESC LIMIT :limit")
    suspend fun getRecentMoviesByGenreRestricted(genre: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY COALESCE(premiereDateMs, (CAST(COALESCE(productionYear, 1970) AS INTEGER) - 1970) * 31536000000) DESC, communityRating DESC LIMIT :limit")
    suspend fun getRecentSeriesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') AND id IN (SELECT mediaId FROM allowed_media_ids) ORDER BY COALESCE(premiereDateMs, (CAST(COALESCE(productionYear, 1970) AS INTEGER) - 1970) * 31536000000) DESC, communityRating DESC LIMIT :limit")
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

    @Query("SELECT * FROM jellyfin_media WHERE (overview IS NULL OR overview = '') LIMIT :limit")
    suspend fun getItemsMissingOverview(limit: Int = 50): List<JellyfinMediaEntity>

    @Query("UPDATE jellyfin_media SET overview = :overview WHERE id = :id")
    suspend fun updateOverview(id: String, overview: String)

    @Query("SELECT * FROM jellyfin_media WHERE (primaryImageTag IS NULL OR primaryImageTag = '') LIMIT :limit")
    suspend fun getItemsMissingPoster(limit: Int = 50): List<JellyfinMediaEntity>

    @Query("UPDATE jellyfin_media SET primaryImageTag = :posterTag WHERE id = :id")
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
