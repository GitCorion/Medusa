package tv.medusa.app.data.local.db

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Índice FTS4 de géneros (contenido externo de [JellyfinMediaEntity]).
 *
 * Se usa para resolver especiales estacionales ("terror", "romance", "navidad") con MATCH
 * en lugar de `LIKE '%…%'` sobre las 32.000 filas del catálogo.
 */
@Fts4(contentEntity = JellyfinMediaEntity::class)
@Entity(tableName = "jellyfin_media_genres_fts")
data class JellyfinMediaGenresFts(
    val genres: String?
)
