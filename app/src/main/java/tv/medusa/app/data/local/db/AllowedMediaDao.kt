package tv.medusa.app.data.local.db

import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * DAO para la tabla de IDs permitidos (restricciones parentales).
 *
 * Se usa en dos momentos:
 * 1. **Reconciliación** (sync): [replaceAll] vacía y rellena la tabla con los IDs
 *    que el usuario puede ver según Jellyfin.
 * 2. **Consultas de catálogo**: las queries restricted de [JellyfinDao] hacen
 *    `IN (SELECT mediaId FROM allowed_media_ids)` contra esta tabla.
 */
@Dao
interface AllowedMediaDao {

    /**
     * Reemplaza atómicamente todos los IDs permitidos. Se usa en la reconciliación
     * completa (full sync o primera vez).
     */
    @Transaction
    suspend fun replaceAll(ids: List<String>) {
        clearAll()
        if (ids.isNotEmpty()) {
            insertAll(ids.map { AllowedMediaIdEntity(it) })
        }
    }

    /**
     * Añade IDs sin borrar los existentes. Se usa en el delta sync para incorporar
     * ítems nuevos al conjunto permitido.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAll(entities: List<AllowedMediaIdEntity>)

    /**
     * Devuelve `true` si hay al menos un ID en la tabla (restricciones activas).
     * Si la tabla está vacía significa que no hay restricciones configuradas.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM allowed_media_ids LIMIT 1)")
    suspend fun hasRestrictions(): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM allowed_media_ids LIMIT 1)")
    fun hasRestrictionsFlow(): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM allowed_media_ids WHERE mediaId = :id)")
    suspend fun isAllowed(id: String): Boolean

    @Query("SELECT COUNT(*) FROM allowed_media_ids")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<AllowedMediaIdEntity>)

    @Query("DELETE FROM allowed_media_ids")
    suspend fun clearAll()
}
