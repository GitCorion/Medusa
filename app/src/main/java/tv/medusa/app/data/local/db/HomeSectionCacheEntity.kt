package tv.medusa.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caché persistente para secciones del Home Feed que dependen de APIs externas.
 *
 * Problema resuelto: secciones como "Populares en Netflix" o "Top 10 en España hoy"
 * se calculaban con TMDB/Trakt API + match local, pero el resultado nunca se persistía.
 * Cuando Android mataba el proceso (Doze mode nocturno), al re-abrir la app estas
 * secciones aparecían vacías hasta que la red respondiera.
 *
 * Con esta tabla, el resultado matcheado se guarda en Room y se restaura instantáneamente.
 */
@Entity(tableName = "home_section_cache")
data class HomeSectionCacheEntity(
    /** Clave única: "platform_netflix", "tmdb_trending_day", "trakt_recs", etc. */
    @PrimaryKey val sectionKey: String,
    /** Título visible de la sección (ej. "Populares en Netflix") */
    val title: String,
    /** JSON array de IDs de jellyfin_media matcheados (ej. ["id1","id2","id3"]) */
    val itemIds: String,
    /** Badge opcional (ej. "TRAKT LISTA") */
    val badge: String? = null,
    /** Si la sección muestra ranking numérico (Top 10) */
    val isRanked: Boolean = false,
    /** Timestamp Unix en ms de la última actualización */
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Secciones de plataformas: válidas 24 horas (sobreviven reposo nocturno) */
        const val TTL_PLATFORMS = 24L * 3600 * 1000

        /** Trending diario: válido 24 horas */
        const val TTL_TRENDING = 24L * 3600 * 1000

        /** Recomendaciones Trakt/TMDB: válidas 12 horas */
        const val TTL_RECOMMENDATIONS = 12L * 3600 * 1000

        /** Watchlist/Listas Trakt: válidas 12 horas */
        const val TTL_TRAKT_LISTS = 12L * 3600 * 1000

        /** Máximo tiempo para descartar datos obsoletos (48 horas) */
        const val TTL_MAX_STALE = 48L * 3600 * 1000
    }

    fun isExpired(ttlMs: Long): Boolean = System.currentTimeMillis() - updatedAt > ttlMs
}
