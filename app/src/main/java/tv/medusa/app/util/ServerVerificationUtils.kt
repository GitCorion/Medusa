package tv.medusa.app.util

import java.io.File
import java.security.MessageDigest

/**
 * Utilidades de verificación: hash SHA-256 de texto y de ficheros, y normalización de hosts.
 */
object ServerVerificationUtils {

    /**
     * Calcula el hash SHA-256 en formato hexadecimal de una cadena de texto.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calcula el hash SHA-256 en hexadecimal del contenido de un fichero.
     * Se usa para verificar la integridad del APK descargado por OTA antes de instalarlo.
     */
    fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Extrae el nombre de host limpio de una URL o dominio (IPv6 entre corchetes incluida).
     */
    fun extractHost(serverUrl: String): String {
        var clean = serverUrl.trim().lowercase()
        if (clean.startsWith("https://")) {
            clean = clean.substring("https://".length)
        } else if (clean.startsWith("http://")) {
            clean = clean.substring("http://".length)
        }

        clean = clean.substringBefore('/').substringBefore('?').substringBefore('#')

        val host = if (clean.startsWith("[")) {
            val end = clean.indexOf(']')
            if (end > 0) clean.substring(1, end) else clean.removePrefix("[")
        } else {
            clean.substringBefore(':')
        }

        return host.removePrefix("www.")
    }
}
