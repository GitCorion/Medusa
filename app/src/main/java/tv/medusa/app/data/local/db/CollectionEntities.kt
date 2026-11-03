package tv.medusa.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pertenencia de un título de la biblioteca a una colección externa (plataforma, tendencia,
 * popular regional...). Es un índice genérico: la app no sabe qué significa cada
 * [collectionKey]; añadir una plataforma o un tipo de colección nuevo es solo dato.
 *
 * - [rank] solo se usa en colecciones ordenadas (Top 10); null en el resto.
 * - [score] es la clave de orden natural (popularidad de TMDB, etc.).
 */
@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionKey", "mediaId"],
    indices = [
        Index(value = ["collectionKey", "score"]),
        Index(value = ["collectionKey", "rank"]),
        Index(value = ["mediaId"])
    ]
)
data class CollectionItemEntity(
    val collectionKey: String,
    val mediaId: String,
    val rank: Int? = null,
    val score: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Metadatos de una colección: permiten invalidarla cuando cambia el catálogo, la región o
 * el servidor sin recalcularla en cada apertura.
 */
@Entity(tableName = "collection_meta")
data class CollectionMetaEntity(
    @PrimaryKey val collectionKey: String,
    val region: String = "",
    val catalogVersion: String = "",
    val itemCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
