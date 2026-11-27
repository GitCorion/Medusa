package tv.medusa.app.data.repository

import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.remote.medusa.MedusaConfigDto

/**
 * Aplica la configuración compartida del plugin Medusa al arrancar la app.
 *
 * Si el plugin no responde, se fuerza el modo local de TMDB para que la app siga funcionando
 * igual que antes (fail-open). La clave TMDB nunca se descarga: solo se activa el proxy.
 */
object MedusaServerSync {

    suspend fun apply(userPreferencesRepository: UserPreferencesRepository): Result<MedusaConfigDto> {
        val prefs = userPreferencesRepository.readPreferences()
        val serverUrl = prefs.jellyfinServerUrl
        val token = prefs.jellyfinAccessToken
        if (serverUrl.isBlank() || token.isBlank()) {
            return Result.failure(IllegalStateException("Sin sesión de Jellyfin"))
        }

        val configResult = MedusaSyncRepository.getConfig(serverUrl, token)
        if (configResult.isFailure) {
            // Plugin ausente o red caída: se mantiene el acceso directo a TMDB.
            if (userPreferencesRepository.readPreferences().tmdbUseServer) {
                userPreferencesRepository.setTmdbUseServer(false)
            }
            return configResult
        }

        val config = configResult.getOrThrow()
        // Si el usuario escribió su propia clave TMDB, su elección prevalece.
        if (prefs.tmdbUseServer) {
            userPreferencesRepository.setTmdbUseServer(config.tmdbConfigured)
        }

        // Si el usuario ha elegido sus propias credenciales Trakt, prevalecen: el vínculo del
        // servidor no debe sobrescribirlas al arrancar.
        if (config.traktSynced && prefs.traktUseServer) {
            val credentials = MedusaSyncRepository.getTraktCredentials(serverUrl, token).getOrNull()
            if (credentials != null && credentials.configured && !credentials.accessToken.isNullOrBlank()) {
                userPreferencesRepository.applyServerTraktCredentials(
                    accessToken = credentials.accessToken,
                    clientId = credentials.clientId?.takeIf { it.isNotBlank() } ?: prefs.traktClientId,
                    clientSecret = credentials.clientSecret?.takeIf { it.isNotBlank() } ?: prefs.traktClientSecret,
                    revision = credentials.revision
                )
            }
        } else if (prefs.traktUseServer) {
            userPreferencesRepository.setTraktUseServer(false)
        }
        return Result.success(config)
    }
}
