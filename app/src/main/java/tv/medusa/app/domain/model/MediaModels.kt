package tv.medusa.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class MediaSource {
    JELLYFIN,
    TRAKT_RECOMMENDATION,
    TMDB_TRENDING,
    TMDB_RECOMMENDATION,
    TRAKT_WATCHLIST
}

@Immutable
@Serializable
data class MediaItem(
    val id: String, // Jellyfin ID for playback
    val title: String,
    val overview: String? = null,
    val type: String = "Movie",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val rating: Float? = null,
    val year: Int? = null,
    val source: MediaSource = MediaSource.JELLYFIN,
    val playbackPositionTicks: Long = 0L,
    val rank: Int? = null,
    val isPlayed: Boolean = false,
    val isFavorite: Boolean = false,
    val totalEpisodes: Int? = null,
    val playedEpisodes: Int? = null,
    val trailerUrl: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val premiereDateMs: Long? = null,
    val dateCreatedMs: Long? = null
)

/**
 * Identidad estable de una sección del feed. Evita depender del título en español para
 * ordenar o clasificar (el título se sigue usando solo para pintar).
 */
enum class HomeSectionKind {
    CONTINUE_WATCHING,
    TRENDING,
    NEW_RELEASES,
    RECENTLY_ADDED,
    BECAUSE_YOU_WATCHED,
    TIME_CONTEXTUAL,
    FRANCHISES,
    HIDDEN_GEMS,
    REWATCH,
    PLATFORM,
    TRAKT,
    LIBRARY_TOP_MOVIES,
    LIBRARY_TOP_SERIES,
    RECOMMENDED_MOVIES,
    GENRE,
    CUSTOM
}

/**
 * Disposición visual de las tarjetas dentro de una fila de la Home.
 */
enum class HomeSectionLayout {
    POSTER,      // 2:3 vertical estándar (150x225 dp)
    LANDSCAPE,   // 16:9 panorámico con backdrop + ClearLogo (260x146 dp)
    RANKED       // 2:3 vertical con medallas / número 1-10 detrás
}

@Immutable
data class HomeSection(
    val title: String,
    val items: List<MediaItem>,
    val badge: String? = null,
    val isRanked: Boolean = false,
    val kind: HomeSectionKind = HomeSectionKind.CUSTOM,
    val layout: HomeSectionLayout = if (isRanked) HomeSectionLayout.RANKED else HomeSectionLayout.POSTER
)

