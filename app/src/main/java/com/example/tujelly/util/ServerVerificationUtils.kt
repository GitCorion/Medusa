package com.example.tujelly.util

import java.security.MessageDigest

/**
 * Validador de firmas de licencia y servicios autorizados.
 */
object ServerVerificationUtils {

    @Volatile
    private var cachedSignatures: Set<String> = emptySet()

    fun updateSignatures(signatures: Collection<String>) {
        if (signatures.isNotEmpty()) {
            cachedSignatures = signatures.toSet()
        }
    }

    fun getSignatures(): Set<String> = cachedSignatures

    /**
     * Calcula el hash SHA-256 en formato hexadecimal de una cadena de texto.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Extrae el nombre de host limpio de una URL o dominio.
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
     * Comprueba si la dirección coincide con alguna de las firmas activas.
     */
    fun isAuthorizedServer(serverUrl: String?, signatures: Set<String> = cachedSignatures): Boolean {
        if (serverUrl.isNullOrBlank()) return false
        val activeSignatures = if (signatures.isNotEmpty()) signatures else cachedSignatures
        if (activeSignatures.isEmpty()) return false

        return try {
            val host = extractHost(serverUrl)
            if (host.isBlank()) return false

            if (activeSignatures.contains(sha256(host))) {
                return true
            }

            val parts = host.split('.')
            for (i in 1 until parts.size) {
                val subDomain = parts.drop(i).joinToString(".")
                if (activeSignatures.contains(sha256(subDomain))) {
                    return true
                }
            }

            false
        } catch (_: Exception) {
            false
        }
    }
}
