package com.example.tujelly.util

import java.security.MessageDigest

/**
 * Utilidad de seguridad para verificar servidores autorizados (VIP / Familiares)
 * de forma completamente opaca.
 *
 * Los dominios y URLs NO se almacenan en texto plano en la aplicación ni en el repositorio.
 * Solo se almacenan resúmenes criptográficos SHA-256 irreversibles, impidiendo que
 * herramientas de descompilación (como Jadx o apktool) o curiosos puedan extraer
 * la URL o el dominio del servidor.
 */
object ServerVerificationUtils {

    // Hashes criptográficos SHA-256 de los hosts/dominios autorizados (en minúsculas y sin 'www.')
    // Los valores originales NUNCA se incluyen en el código fuente ni en los recursos compilados.
    private val AUTHORIZED_SERVER_HASHES = setOf(
        "26bccb39ed122a69ecf7a8d0b50deeabeb15bc1a4b2a8b77941b47a96fdd3fea", // Host completo
        "78a588afcc90d8ea59e008c9cbd1e8bbed3b2831518184d5a9935948b9d245ac"  // Dominio raíz
    )

    /**
     * Calcula el hash SHA-256 en formato hexadecimal de una cadena de texto.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Extrae el nombre de host limpio de una URL o dominio (sin protocolo http/https, sin puerto ni rutas).
     */
    fun extractHost(serverUrl: String): String {
        var clean = serverUrl.trim().lowercase()
        if (clean.startsWith("https://")) {
            clean = clean.substring("https://".length)
        } else if (clean.startsWith("http://")) {
            clean = clean.substring("http://".length)
        }

        val slashIndex = clean.indexOf('/')
        if (slashIndex != -1) {
            clean = clean.substring(0, slashIndex)
        }

        val colonIndex = clean.indexOf(':')
        if (colonIndex != -1) {
            clean = clean.substring(0, colonIndex)
        }

        return clean.removePrefix("www.")
    }

    /**
     * Comprueba si la URL del servidor Jellyfin coincide con alguno de los servidores
     * autorizados comparando únicamente sus hashes criptográficos unidireccionales.
     */
    fun isAuthorizedServer(serverUrl: String?): Boolean {
        if (serverUrl.isNullOrBlank()) return false
        return try {
            val host = extractHost(serverUrl)
            if (host.isBlank()) return false

            // 1. Coincidencia directa del host completo
            if (AUTHORIZED_SERVER_HASHES.contains(sha256(host))) {
                return true
            }

            // 2. Coincidencia de subdominios con el dominio raíz autorizado
            val parts = host.split('.')
            for (i in 1 until parts.size) {
                val subDomain = parts.drop(i).joinToString(".")
                if (AUTHORIZED_SERVER_HASHES.contains(sha256(subDomain))) {
                    return true
                }
            }

            false
        } catch (_: Exception) {
            false
        }
    }
}
