package tv.medusa.app.data.remote.tmdb

import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.data.remote.ApiConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbItemDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("release_date") val releaseDate: String? = null
)

fun TmdbItemDto.toDirectMediaItem(
    source: MediaSource = MediaSource.TMDB_TRENDING
): MediaItem {
    val isTv = mediaType?.equals("tv", ignoreCase = true) == true || name != null
    val titleText = title ?: name ?: "Título"
    val poster = if (!posterPath.isNullOrBlank()) "${ApiConfig.TMDB_IMAGE_BASE_W500}$posterPath" else null
    val backdrop = if (!backdropPath.isNullOrBlank()) "${ApiConfig.TMDB_IMAGE_BASE_W1280}$backdropPath" else null
    val releaseYear = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()

    return MediaItem(
        id = "tmdb-$id",
        title = titleText,
        overview = overview,
        type = if (isTv) "Series" else "Movie",
        posterUrl = poster,
        backdropUrl = backdrop,
        rating = voteAverage,
        year = releaseYear,
        source = source
    )
}

@Serializable
data class TmdbTrendingResponse(
    @SerialName("results") val results: List<TmdbItemDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1
)

@Serializable
data class TmdbVideoDto(
    @SerialName("key") val key: String,
    @SerialName("site") val site: String = "YouTube",
    @SerialName("type") val type: String = "Trailer"
)

@Serializable
data class TmdbVideosResponse(
    @SerialName("results") val results: List<TmdbVideoDto> = emptyList()
)

@Serializable
data class TmdbTvDetailDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("in_production") val inProduction: Boolean? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("popularity") val popularity: Double? = null
)

@Serializable
data class TmdbMovieDetailDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("popularity") val popularity: Double? = null
)
