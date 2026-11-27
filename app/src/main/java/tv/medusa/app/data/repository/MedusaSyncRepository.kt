package tv.medusa.app.data.repository

import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.medusa.MedusaConfigDto
import tv.medusa.app.data.remote.medusa.MedusaSyncApiService
import tv.medusa.app.data.remote.medusa.TraktCredentialsDto
import tv.medusa.app.data.remote.medusa.TraktCredentialsUploadDto

/**
 * Acceso a la configuración compartida del plugin Medusa instalado en el servidor Jellyfin.
 *
 * La app funciona igual si el plugin no está presente: todos los métodos devuelven `Result` y
 * los fallos de red se ignoran (fail-open), de modo que el modo local sigue operativo.
 */
object MedusaSyncRepository {

    private fun api(serverUrl: String): MedusaSyncApiService {
        val baseUrl = "${NetworkClientFactory.normalizeUrl(serverUrl)}/Medusa/"
        return NetworkClientFactory.createService(baseUrl, MedusaSyncApiService::class.java)
    }

    suspend fun getConfig(serverUrl: String, token: String): Result<MedusaConfigDto> = runCatching {
        api(serverUrl).getConfig(buildJellyfinAuthHeader(token = token))
    }

    suspend fun getTraktCredentials(serverUrl: String, token: String): Result<TraktCredentialsDto> =
        runCatching {
            api(serverUrl).getTraktCredentials(buildJellyfinAuthHeader(token = token))
        }

    suspend fun saveTraktCredentials(
        serverUrl: String,
        token: String,
        dto: TraktCredentialsUploadDto
    ): Result<TraktCredentialsDto> = runCatching {
        api(serverUrl).saveTraktCredentials(buildJellyfinAuthHeader(token = token), dto)
    }

    suspend fun deleteTraktCredentials(serverUrl: String, token: String): Result<Unit> = runCatching {
        api(serverUrl).deleteTraktCredentials(buildJellyfinAuthHeader(token = token))
    }
}
