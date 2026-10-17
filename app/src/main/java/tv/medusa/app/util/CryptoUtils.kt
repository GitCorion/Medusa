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

    /**
     * Cifra [plaintext]. Devuelve "" si está vacío. Si el Keystore fallara, devuelve el texto en
     * claro como último recurso para no bloquear el guardado (no debería ocurrir en API 26+).
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
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            plaintext
        }
    }

    /**
     * Descifra un valor almacenado:
     * - Vacío → "".
     * - Sin prefijo `enc:` → valor legacy en claro, se devuelve tal cual.
     * - Con prefijo → se descifra; si falla, devuelve "" (obliga a re-autenticar).
     */
    fun decryptStored(stored: String): String {
        if (stored.isBlank()) return ""
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val combined = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
            val ciphertext = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

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
