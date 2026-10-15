package com.example.tujelly.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jellyfin_media",
    indices = [
        Index(value = ["tmdbId"]),
        Index(value = ["imdbId"]),
        Index(value = ["title"]),
        Index(value = ["titleLower"]),
        Index(value = ["originalTitleLower"]),
        Index(value = ["seriesNameLower"]),
        Index(value = ["type"]),
        Index(value = ["isFavorite"]),
        Index(value = ["type", "communityRating"]),
        Index(value = ["type", "isPlayed", "communityRating"]),
        Index(value = ["isPlayed", "playbackPositionTicks"]),
        Index(value = ["seriesId", "type"]),
        Index(value = ["playbackPositionTicks"]),
        Index(value = ["productionYear"])
    ]
)
data class JellyfinMediaEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val type: String, // "Movie", "Series", "Episode"
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
    val overview: String? = null,
    val primaryImageTag: String? = null,
    val backdropImageTag: String? = null,
    val communityRating: Float? = null,
    val productionYear: Int? = null,
    val genres: String? = null,
    val officialRating: String? = null,
    val tags: String? = null,
    val isPlayed: Boolean = false,
    val playbackPositionTicks: Long = 0L,
    val isFavorite: Boolean = false,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seriesPrimaryImageTag: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val totalItemCount: Int? = null,
    val unplayedItemCount: Int? = null,
    /**
     * Columnas normalizadas (ASCII lower, igual que el `LOWER()` de SQLite) para que las búsquedas
     * exactas por título (`findByTitle*`, `getSeriesByTitle`) usen índice en lugar de un full-scan
     * con `LOWER(columna)`. Se calculan en el constructor; `MIGRATION_13_14` rellena las filas
     * existentes.
     */
    val titleLower: String = asciiLowercase(title),
    val originalTitleLower: String = asciiLowercase(originalTitle),
    val seriesNameLower: String = asciiLowercase(seriesName)
)

/**
 * Normaliza a minúsculas **solo ASCII** (A-Z), replicando el `LOWER()` de SQLite, para que las
 * columnas `*Lower` coincidan tanto con el backfill de la migración como con las consultas que
 * comparan contra `LOWER(:param)`.
 */
internal fun asciiLowercase(value: String?): String {
    if (value.isNullOrEmpty()) return ""
    val sb = StringBuilder(value.length)
    for (c in value) {
        sb.append(if (c in 'A'..'Z') (c.code + 32).toChar() else c)
    }
    return sb.toString()
}
