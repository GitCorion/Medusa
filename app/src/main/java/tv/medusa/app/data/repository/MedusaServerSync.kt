package tv.medusa.app.data.repository

import retrofit2.HttpException
import tv.medusa.app.data.local.ServerSyncPreferencesStore
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.medusa.MedusaConfigDto
import tv.medusa.app.data.remote.medusa.TraktCredentialsDto

/**
 * Aplica la configuración compartida del plugin Medusa al arrancar la app o tras iniciar sesión.
 *
 * Si el plugin no responde por red o todavía no está configurado, conserva la preferencia del
 * usuario (`tmdbUseServer` / `traktUseServer`) para reintentarlo en el siguiente ciclo. La clave
 * TMDB nunca se descarga: solo se activa el proxy cuando el servidor la tiene configurada.
 */
object MedusaServerSync {

    suspend fun apply(
        userPreferencesRepository: ServerSyncPreferencesStore,
        fetchConfig: suspend (serverUrl: String, token: String) -> Result<MedusaConfigDto> =
            MedusaSyncRepository::getConfig,
        fetchTraktCredentials: suspend (serverUrl: String, token: String) -> Result<TraktCredentialsDto> =
            MedusaSyncRepository::getTraktCredentials
    ): Result<MedusaConfigDto> {
        val prefs = userPreferencesRepository.readPreferences()
        val serverUrl = prefs.jellyfinServerUrl
        val token = prefs.jellyfinAccessToken
        if (serverUrl.isBlank() || token.isBlank()) {
            return Result.failure(IllegalStateException("Sin sesión de Jellyfin"))
        }

        // Registrar el pin TLS antes de lanzar la petición al servidor para evitar carreras con el
        // colector de preferencias durante el arranque.
        NetworkClientFactory.setTrustedServer(
            serverUrl = serverUrl,
            allowSelfSigned = prefs.jellyfinAllowSelfSigned,
            certificateFingerprintSha256 = prefs.jellyfinCertificateFingerprint
        )

        val configResult = fetchConfig(serverUrl, token)
        if (configResult.isFailure) {
            val err = configResult.exceptionOrNull()
            if (err is HttpException && err.code() == 404 && prefs.tmdbServerConfigured) {
                userPreferencesRepository.setTmdbServerConfigured(false)
            }
            return configResult
        }

        val config = configResult.getOrThrow()
        if (prefs.tmdbServerConfigured != config.tmdbConfigured) {
            userPreferencesRepository.setTmdbServerConfigured(config.tmdbConfigured)
            MediaCaches.invalidateTmdbCaches()
        }

        // Si el usuario ha elegido sus propias credenciales Trakt, prevalecen: el vínculo del
        // servidor no debe sobrescribirlas.
        if (prefs.traktUseServer) {
            if (config.traktSynced) {
                val credentials = fetchTraktCredentials(serverUrl, token).getOrNull()
                val serverToken = credentials?.accessToken
                val hasCredentials = credentials != null &&
                    (credentials.configured || !serverToken.isNullOrBlank()) &&
                    !serverToken.isNullOrBlank()
                if (hasCredentials && credentials != null) {
                    userPreferencesRepository.applyServerTraktCredentials(
                        accessToken = serverToken!!,
                        clientId = credentials.clientId?.takeIf { it.isNotBlank() } ?: prefs.traktClientId,
                        clientSecret = credentials.clientSecret?.takeIf { it.isNotBlank() } ?: prefs.traktClientSecret,
                        revision = credentials.revision
                    )
                }
            } else if (prefs.traktAccessToken.isNotBlank() || prefs.traktServerRevision != 0) {
                // Otro dispositivo desvinculó la cuenta en el servidor.
                userPreferencesRepository.clearServerSyncedTraktCredentials()
            }
        }
        return Result.success(config)
    }
}
