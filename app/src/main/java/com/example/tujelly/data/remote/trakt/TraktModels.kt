package com.example.tujelly.data.remote.trakt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TraktIdsDto(
    @SerialName("trakt") val trakt: Int? = null,
    @SerialName("slug") val slug: String? = null,
    @SerialName("imdb") val imdb: String? = null,
    @SerialName("tmdb") val tmdb: Int? = null
)

@Serializable
data class TraktImagesSizesDto(
    @SerialName("poster") val poster: List<String>? = null,
    @SerialName("fanart") val fanart: List<String>? = null,
    @SerialName("thumb") val thumb: List<String>? = null
)

@Serializable
data class TraktMediaDto(
    @SerialName("title") val title: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("ids") val ids: TraktIdsDto,
    @SerialName("overview") val overview: String? = null,
    @SerialName("rating") val rating: Float? = null,
    @SerialName("images") val images: TraktImagesSizesDto? = null
)

fun TraktMediaDto.toDirectMediaItem(
    source: com.example.tujelly.domain.model.MediaSource = com.example.tujelly.domain.model.MediaSource.TRAKT_WATCHLIST
): com.example.tujelly.domain.model.MediaItem {
    val rawPoster = images?.poster?.firstOrNull() ?: images?.thumb?.firstOrNull()
    val poster = rawPoster?.let { if (it.startsWith("http")) it else "https://$it" }
    val rawBackdrop = images?.fanart?.firstOrNull()
    val backdrop = rawBackdrop?.let { if (it.startsWith("http")) it else "https://$it" }

    return com.example.tujelly.domain.model.MediaItem(
        id = "trakt-${ids.trakt ?: ids.tmdb ?: ids.slug ?: title.hashCode()}",
        title = title,
        overview = overview,
        type = "Movie",
        posterUrl = poster,
        backdropUrl = backdrop,
        rating = rating,
        year = year,
        source = source
    )
}

@Serializable
data class TraktWatchlistItemDto(
    @SerialName("listed_at") val listedAt: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("movie") val movie: TraktMediaDto? = null,
    @SerialName("show") val show: TraktMediaDto? = null
)

@Serializable
data class TraktTrendingDto(
    @SerialName("watchers") val watchers: Int? = null,
    @SerialName("movie") val movie: TraktMediaDto? = null,
    @SerialName("show") val show: TraktMediaDto? = null
)

@Serializable
data class TraktUserDto(
    @SerialName("username") val username: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("private") val isPrivate: Boolean? = null
)

@Serializable
data class TraktUserListDto(
    @SerialName("name") val name: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("item_count") val itemCount: Int? = 0,
    @SerialName("ids") val ids: TraktIdsDto = TraktIdsDto(),
    @SerialName("user") val user: TraktUserDto? = null
) {
    /**
     * Comprueba si el usuario ha marcado esta lista como oculta en Ajustes.
     * Comprueba por ID de Trakt, slug o nombre.
     */
    fun isUserHidden(hiddenKeys: Set<String>): Boolean {
        if (hiddenKeys.isEmpty()) return false
        val idStr = ids.trakt?.toString()?.lowercase(java.util.Locale.ROOT)
        val slugStr = ids.slug?.trim()?.lowercase(java.util.Locale.ROOT)
        val nameStr = name.trim().lowercase(java.util.Locale.ROOT)
        return (idStr != null && idStr in hiddenKeys) ||
               (slugStr != null && slugStr in hiddenKeys) ||
               nameStr in hiddenKeys
    }
}

@Serializable
data class TraktLikedListDto(
    @SerialName("liked_at") val likedAt: String? = null,
    @SerialName("list") val list: TraktUserListDto
)

@Serializable
data class TraktListItemDto(
    @SerialName("type") val type: String? = null,
    @SerialName("movie") val movie: TraktMediaDto? = null,
    @SerialName("show") val show: TraktMediaDto? = null
)

@Serializable
data class TraktErrorResponse(
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class TraktDeviceCodeRequest(
    @SerialName("client_id") val clientId: String
)

@Serializable
data class TraktDeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("interval") val interval: Int = 5
)

@Serializable
data class TraktDeviceTokenRequest(
    @SerialName("code") val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null
)

@Serializable
data class TraktTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null,
    @SerialName("created_at") val createdAt: Long = 0L
)

