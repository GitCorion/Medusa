package tv.medusa.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SeasonalDao {

    @Query("SELECT COUNT(*) FROM seasonal_items WHERE eventId = :eventId")
    suspend fun count(eventId: String): Int

    @Query("SELECT * FROM seasonal_index_meta WHERE eventId = :eventId LIMIT 1")
    suspend fun getMeta(eventId: String): SeasonalIndexMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: SeasonalIndexMetaEntity)

    @Query("DELETE FROM seasonal_items WHERE eventId = :eventId")
    suspend fun clearItems(eventId: String)

    @Query("DELETE FROM seasonal_items")
    suspend fun clearAllItems()

    @Query("DELETE FROM seasonal_index_meta")
    suspend fun clearAllMeta()

    @Transaction
    suspend fun clearAll() {
        clearAllItems()
        clearAllMeta()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<SeasonalItemEntity>)

    /** Marca títulos de la selección editorial para que encabecen el especial. */
    @Query(
        "UPDATE seasonal_items SET editorial = 1, score = score + 1000 " +
            "WHERE eventId = :eventId AND mediaId IN (:ids)"
    )
    suspend fun markEditorial(eventId: String, ids: List<String>)

    /**
     * Sustituye el índice de un evento de forma atómica. La UI sigue consultando el índice
     * anterior hasta que la transacción termina; nunca se queda sin contenido.
     */
    @Transaction
    suspend fun replaceIndex(
        eventId: String,
        items: List<SeasonalItemEntity>,
        meta: SeasonalIndexMetaEntity
    ) {
        clearItems(eventId)
        items.chunked(500).forEach { insertItems(it) }
        upsertMeta(meta)
    }

    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        INNER JOIN seasonal_items si ON si.mediaId = jm.id
        WHERE si.eventId = :eventId
          AND (:type IS NULL OR jm.type = :type)
          AND (:hideWatched = 0 OR (jm.isPlayed = 0 AND jm.playbackPositionTicks = 0))
          AND (:minYear IS NULL OR jm.productionYear >= :minYear)
          AND (:maxYear IS NULL OR jm.productionYear < :maxYear)
          AND (:minRating IS NULL OR jm.communityRating >= :minRating)
          AND (:unplayedOnly = 0 OR jm.isPlayed = 0)
        ORDER BY si.editorial DESC, si.score DESC, jm.id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getPage(
        eventId: String,
        type: String?,
        hideWatched: Boolean,
        minYear: Int?,
        maxYear: Int?,
        minRating: Float?,
        unplayedOnly: Boolean,
        limit: Int,
        offset: Int
    ): List<JellyfinMediaEntity>

    @Query(
        """
        SELECT COUNT(*) FROM jellyfin_media jm
        INNER JOIN seasonal_items si ON si.mediaId = jm.id
        WHERE si.eventId = :eventId
          AND (:type IS NULL OR jm.type = :type)
          AND (:hideWatched = 0 OR (jm.isPlayed = 0 AND jm.playbackPositionTicks = 0))
        """
    )
    suspend fun countFiltered(
        eventId: String,
        type: String?,
        hideWatched: Boolean
    ): Int
}
