package tv.medusa.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CollectionDao {

    @Query("SELECT COUNT(*) FROM collection_items WHERE collectionKey = :key")
    suspend fun count(key: String): Int

    @Query("SELECT COUNT(*) FROM jellyfin_media jm INNER JOIN collection_items ci ON ci.mediaId = jm.id " +
        "WHERE ci.collectionKey = :key AND (:type IS NULL OR jm.type = :type) " +
        "AND (:hideWatched = 0 OR (jm.isPlayed = 0 AND jm.playbackPositionTicks = 0))")
    suspend fun countJoined(key: String, type: String?, hideWatched: Boolean = false): Int

    @Query("SELECT * FROM collection_meta WHERE collectionKey = :key LIMIT 1")
    suspend fun getMeta(key: String): CollectionMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: CollectionMetaEntity)

    @Query("DELETE FROM collection_items WHERE collectionKey = :key")
    suspend fun clearItems(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<CollectionItemEntity>)

    /**
     * Sustituye una colección de forma atómica: la UI sigue viendo la anterior hasta que la
     * transacción termina, así que nunca se queda sin contenido mientras se reconstruye.
     */
    @Transaction
    suspend fun replaceCollection(
        key: String,
        items: List<CollectionItemEntity>,
        meta: CollectionMetaEntity
    ) {
        clearItems(key)
        items.chunked(500).forEach { insertItems(it) }
        upsertMeta(meta)
    }

    /**
     * Página ordenada de una colección. Las colecciones con [CollectionItemEntity.rank]
     * (Top 10) se ordenan por puesto; el resto por [CollectionItemEntity.score] descendente.
     */
    @Query(
        """
        SELECT jm.* FROM jellyfin_media jm
        INNER JOIN collection_items ci ON ci.mediaId = jm.id
        WHERE ci.collectionKey = :key
          AND (:type IS NULL OR jm.type = :type)
          AND (:hideWatched = 0 OR (jm.isPlayed = 0 AND jm.playbackPositionTicks = 0))
        ORDER BY
          CASE WHEN ci.rank IS NULL THEN 1 ELSE 0 END,
          ci.rank ASC,
          ci.score DESC,
          jm.id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getPage(
        key: String,
        type: String?,
        limit: Int,
        offset: Int,
        hideWatched: Boolean = false
    ): List<JellyfinMediaEntity>

    @Query("SELECT collectionKey FROM collection_items WHERE mediaId = :mediaId")
    suspend fun collectionsForMedia(mediaId: String): List<String>

    @Query("DELETE FROM collection_items")
    suspend fun clearAllItems()

    @Query("DELETE FROM collection_meta")
    suspend fun clearAllMeta()

    @Transaction
    suspend fun clearAll() {
        clearAllItems()
        clearAllMeta()
    }
}
