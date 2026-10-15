package com.example.tujelly.data.local.db

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

    @Query("SELECT * FROM jellyfin_media WHERE tmdbId = :tmdbId ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByTmdbId(tmdbId: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE imdbId = :imdbId ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByImdbId(imdbId: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE (:tmdbId IS NOT NULL AND tmdbId = :tmdbId) OR (:imdbId IS NOT NULL AND imdbId = :imdbId) ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByTmdbOrImdb(tmdbId: String?, imdbId: String?): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE (LOWER(title) = LOWER(:title) OR LOWER(originalTitle) = LOWER(:title) OR LOWER(seriesName) = LOWER(:title)) AND productionYear = :year ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByTitleAndYear(title: String, year: Int): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE LOWER(title) = LOWER(:title) OR LOWER(originalTitle) = LOWER(:title) OR LOWER(seriesName) = LOWER(:title) ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByTitle(title: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE LOWER(title) LIKE '%' || LOWER(:title) || '%' OR LOWER(originalTitle) LIKE '%' || LOWER(:title) || '%' OR LOWER(seriesName) LIKE '%' || LOWER(:title) || '%' ORDER BY CASE WHEN type = 'Series' THEN 0 WHEN type = 'Movie' THEN 1 ELSE 2 END LIMIT 1")
    suspend fun findByFuzzyTitle(title: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE seriesId = :seriesId AND type = 'Episode'")
    suspend fun getEpisodesForSeries(seriesId: String): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE (LOWER(title) = LOWER(:title) OR LOWER(originalTitle) = LOWER(:title)) AND type = 'Series' LIMIT 1")
    suspend fun getSeriesByTitle(title: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND (LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(originalTitle) LIKE '%' || LOWER(:query) || '%') ORDER BY communityRating DESC LIMIT :limit")
    suspend fun searchLocalMedia(query: String, limit: Int = 40): List<JellyfinMediaEntity>

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

    @Query("SELECT DISTINCT title FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND title IS NOT NULL AND title != '' ORDER BY title ASC")
    suspend fun getAllLocalTitles(): List<String>

    @Query("SELECT * FROM jellyfin_media WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): JellyfinMediaEntity?

    @Query("SELECT * FROM jellyfin_media WHERE id IN (:ids)")
    suspend fun getItemsByIds(ids: List<String>): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' ORDER BY title ASC")
    suspend fun getMovies(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' ORDER BY title ASC")
    suspend fun getSeries(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series')")
    suspend fun getAllCatalog(): List<JellyfinMediaEntity>

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
               seasonNumber, episodeNumber, totalItemCount, unplayedItemCount
        FROM jellyfin_media
        WHERE type IN ('Movie', 'Series')
        """
    )
    suspend fun getCatalogForMatrix(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE playbackPositionTicks > 0 AND isPlayed = 0 AND type IN ('Movie', 'Episode', 'Series') ORDER BY rowid DESC LIMIT :limit")
    suspend fun getContinueWatchingLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("UPDATE jellyfin_media SET playbackPositionTicks = :positionTicks, isPlayed = :isPlayed WHERE id = :itemId")
    suspend fun updatePlaybackPosition(itemId: String, positionTicks: Long, isPlayed: Boolean)

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') ORDER BY rowid DESC LIMIT :limit")
    suspend fun getLatestLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND communityRating >= 7.0 AND communityRating <= 9.5 ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getTopRatedLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND isPlayed = 0 AND playbackPositionTicks = 0 AND communityRating >= 6.8 AND communityRating <= 9.5 ORDER BY communityRating DESC, COALESCE(productionYear, 0) DESC LIMIT :limit")
    suspend fun getTopMoviesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND isPlayed = 0 AND playbackPositionTicks = 0 AND communityRating >= 6.8 AND communityRating <= 9.5 ORDER BY communityRating DESC, COALESCE(productionYear, 0) DESC LIMIT :limit")
    suspend fun getTopSeriesLocal(limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type IN ('Movie', 'Series') AND (communityRating IS NULL OR communityRating <= 9.5) AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getItemsByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getMoviesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY communityRating DESC LIMIT :limit")
    suspend fun getSeriesByGenre(genre: String, limit: Int = 40): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Movie' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY productionYear DESC, communityRating DESC LIMIT :limit")
    suspend fun getRecentMoviesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE type = 'Series' AND (LOWER(genres) LIKE '%' || LOWER(:genre) || '%' OR LOWER(title) LIKE '%' || LOWER(:genre) || '%') ORDER BY productionYear DESC, communityRating DESC LIMIT :limit")
    suspend fun getRecentSeriesByGenre(genre: String, limit: Int = 20): List<JellyfinMediaEntity>

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

    @Query("SELECT * FROM jellyfin_media WHERE isFavorite = 1 ORDER BY title ASC")
    suspend fun getFavorites(): List<JellyfinMediaEntity>

    @Query("SELECT * FROM jellyfin_media WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoritesFlow(): Flow<List<JellyfinMediaEntity>>

    @Query("UPDATE jellyfin_media SET isFavorite = :isFavorite WHERE id = :itemId")
    suspend fun updateFavoriteStatus(itemId: String, isFavorite: Boolean)

    @Query("UPDATE jellyfin_media SET isPlayed = :isPlayed, playbackPositionTicks = 0, unplayedItemCount = CASE WHEN :isPlayed = 1 THEN 0 ELSE totalItemCount END WHERE id = :itemId")
    suspend fun updatePlayedStatus(itemId: String, isPlayed: Boolean)

    @Query("DELETE FROM jellyfin_media")
    suspend fun clearAll()

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
