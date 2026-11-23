package tv.medusa.app.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado de credenciales con AES/GCM usando una clave guardada en el Android Keystore
 * (respaldada por hardware cuando el dispositivo lo soporta).
 *
 * Los valores cifrados se guardan con el prefijo `enc:` seguido de `base64(iv + ciphertext)`.
 * Los valores legacy en claro (sin prefijo) se devuelven tal cual, de modo que la migración
 * es transparente.
 */
object CryptoUtils {

    private const val KEY_ALIAS = "medusa_credentials_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128
    private const val PREFIX = "enc:"

    // Caché en memoria para evitar pérdida de credenciales por fallos transitorios
    // del KeyStore durante suspensión o despertar de Android TV (deep sleep / HDMI switch).
    private val decryptedCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Cifra [plaintext]. Devuelve "" si está vacío.
     *
     * Fail-closed: si el Keystore falla, se lanza en lugar de guardar la credencial en claro.
     * Es preferible que la operación falle (y se informe) a dejar un token utilizable en
     * `medusa_settings` sin que el usuario lo sepa.
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isBlank()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            val result = PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
            decryptedCache[result] = plaintext
            result
        } catch (e: Exception) {
            android.util.Log.e("CryptoUtils", "Keystore no disponible; no se guarda la credencial en claro", e)
            throw IllegalStateException("No se pudo cifrar la credencial con Keystore", e)
        }
    }

    /**
     * Descifra un valor almacenado:
     * - Vacío → "".
     * - Sin prefijo `enc:` → valor legacy en claro, se devuelve tal cual.
     * - Con prefijo → se descifra con Keystore o se recupera de la caché en memoria.
     */
    fun decryptStored(stored: String): String {
        if (stored.isBlank()) return ""
        if (!stored.startsWith(PREFIX)) return stored

        // 1. Comprobar caché en memoria primero
        decryptedCache[stored]?.let { return it }

        // 2. Intentar descifrado con Keystore
        val decrypted = tryDecrypt(stored)
        if (decrypted != null) {
            decryptedCache[stored] = decrypted
            return decrypted
        }

        // 3. Reintento tras recargar KeyStore si falló por despertar del dispositivo
        val retryDecrypted = try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            tryDecrypt(stored)
        } catch (_: Exception) {
            null
        }

        if (retryDecrypted != null) {
            decryptedCache[stored] = retryDecrypted
            return retryDecrypted
        }

        android.util.Log.w("CryptoUtils", "No se pudo descifrar credencial almacenada tras reintento.")
        return ""
    }

    private fun tryDecrypt(stored: String): String? {
        return try {
            val combined = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            if (combined.size <= IV_SIZE_BYTES) return null
            val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
            val ciphertext = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
            // Fail-closed: si la clave no existe (p. ej. Keystore desincronizado tras un
            // reinicio de TV), NO se genera una nueva. Generarla sobrescribiría la anterior y
            // dejaría ilegibles todas las credenciales guardadas.
            val key = getExistingKey() ?: run {
                android.util.Log.w("CryptoUtils", "Clave del Keystore no disponible; se aborta el descifrado.")
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Vacía la caché de credenciales en claro. Llamar al cerrar sesión. */
    fun clearCache() {
        decryptedCache.clear()
    }

    private fun getExistingKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    } catch (_: Exception) {
        null
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
