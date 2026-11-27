package tv.medusa.app.data.remote.medusa

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Respuesta de `GET /Medusa/Config`. La clave TMDB nunca se expone: solo se indica si existe. */
@Serializable
data class MedusaConfigDto(
    @SerialName("tmdb_configured") val tmdbConfigured: Boolean = false,
    @SerialName("trakt_synced") val traktSynced: Boolean = false,
    @SerialName("trakt_username") val traktUsername: String? = null,
    @SerialName("trakt_revision") val traktRevision: Int = 0
)

/**
 * Credenciales Trakt guardadas en el servidor. Cuando no están configuradas el plugin responde
 * únicamente `{"configured": false}`, por eso todos los campos son opcionales.
 */
@Serializable
data class TraktCredentialsDto(
    @SerialName("configured") val configured: Boolean = false,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_secret") val clientSecret: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("revision") val revision: Int = 0
)

/** Cuerpo de `PUT /Medusa/Trakt/Credentials`. `base_revision` permite al plugin detectar conflictos. */
@Serializable
data class TraktCredentialsUploadDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String = "",
    @SerialName("username") val username: String? = null,
    @SerialName("base_revision") val baseRevision: Int = 0
)
