package tv.medusa.app.data.remote.medusa

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT

/**
 * Endpoints de sincronización del plugin Medusa. La URL base es `<servidor Jellyfin>/Medusa/`.
 * Todas las llamadas requieren el token Jellyfin en la cabecera `Authorization`.
 */
interface MedusaSyncApiService {

    @GET("Config")
    suspend fun getConfig(
        @Header("Authorization") authHeader: String
    ): MedusaConfigDto

    @GET("Trakt/Credentials")
    suspend fun getTraktCredentials(
        @Header("Authorization") authHeader: String
    ): TraktCredentialsDto

    @PUT("Trakt/Credentials")
    suspend fun saveTraktCredentials(
        @Header("Authorization") authHeader: String,
        @Body credentials: TraktCredentialsUploadDto
    ): TraktCredentialsDto

    @DELETE("Trakt/Credentials")
    suspend fun deleteTraktCredentials(
        @Header("Authorization") authHeader: String
    )
}
