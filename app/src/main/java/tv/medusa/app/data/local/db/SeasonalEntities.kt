package tv.medusa.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Índice persistente de pertenencia de un título a un evento estacional.
 *
 * Guardar solo la pertenencia (y una puntuación) permite abrir el especial de Halloween con
 * consultas paginadas indexadas en lugar de cargar las 32.000 filas del catálogo y filtrarlas
 * en memoria cada vez.
 *
 * [editorial] marca los títulos que llegan de las listas curadas de Trakt/TMDb: se ordenan
 * primero para que la selección editorial siga teniendo presencia.
 */
@Entity(
    tableName = "seasonal_items",
    primaryKeys = ["eventId", "mediaId"],
    indices = [
        Index(value = ["eventId", "score"]),
        Index(value = ["eventId", "editorial"])
    ]
)
data class SeasonalItemEntity(
    val eventId: String,
    val mediaId: String,
    val score: Double,
    val editorial: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Metadatos del índice de un evento: permite invalidarlo cuando cambia el catálogo
 * (nuevo sync, nuevos títulos) sin recalcularlo en cada apertura.
 */
@Entity(tableName = "seasonal_index_meta")
data class SeasonalIndexMetaEntity(
    @PrimaryKey val eventId: String,
    val catalogVersion: String,
    val itemCount: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
