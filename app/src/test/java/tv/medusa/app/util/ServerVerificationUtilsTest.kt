package tv.medusa.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ServerVerificationUtilsTest {

    // Dominios de EJEMPLO. El dominio real de producción no se escribe nunca en el código:
    // sólo existe como hash SHA-256 en docs/api/version.json (público) y en la memoria local.
    private val testTargetHost = "jellyfin.example.com"
    private val testTargetRoot = "example.com"

    @Before
    fun setup() {
        val hashHost = ServerVerificationUtils.sha256(testTargetHost)
        val hashRoot = ServerVerificationUtils.sha256(testTargetRoot)
        ServerVerificationUtils.updateSignatures(setOf(hashHost, hashRoot))
    }

    @Test
    fun extractHost_normalizesVariousFormatsCorrectly() {
        assertEquals("sample.media.org", ServerVerificationUtils.extractHost("https://sample.media.org"))
        assertEquals("sample.media.org", ServerVerificationUtils.extractHost("https://sample.media.org/"))
        assertEquals("sample.media.org", ServerVerificationUtils.extractHost("http://sample.media.org:8096"))
        assertEquals("sample.media.org", ServerVerificationUtils.extractHost("http://sample.media.org:8096/web/index.html"))
        assertEquals("sample.media.org", ServerVerificationUtils.extractHost("sample.media.org"))
        assertEquals("sample.media.org", ServerVerificationUtils.extractHost("  https://www.sample.media.org/  "))
    }

    @Test
    fun extractHost_supportsIpv6Literals() {
        assertEquals("2001:db8::1", ServerVerificationUtils.extractHost("http://[2001:db8::1]:8096"))
        assertEquals("2001:db8::1", ServerVerificationUtils.extractHost("https://[2001:db8::1]:8096/web/index.html"))
        assertEquals("::1", ServerVerificationUtils.extractHost("http://[::1]:8096"))
        assertEquals("2001:db8::1", ServerVerificationUtils.extractHost("[2001:db8::1]"))
    }

    @Test
    fun isAuthorizedServer_returnsTrueForAuthorizedDomainAndSubdomains() {
        assertTrue(ServerVerificationUtils.isAuthorizedServer("https://$testTargetHost"))
        assertTrue(ServerVerificationUtils.isAuthorizedServer("https://$testTargetHost/"))
        assertTrue(ServerVerificationUtils.isAuthorizedServer("http://$testTargetHost:8096"))
        assertTrue(ServerVerificationUtils.isAuthorizedServer("https://sub.$testTargetHost"))
        assertTrue(ServerVerificationUtils.isAuthorizedServer("https://$testTargetRoot"))
    }

    @Test
    fun isAuthorizedServer_returnsFalseForUnauthorizedDomains() {
        assertFalse(ServerVerificationUtils.isAuthorizedServer("https://random-jellyfin.org"))
        assertFalse(ServerVerificationUtils.isAuthorizedServer("http://192.168.1.100:8096"))
        assertFalse(ServerVerificationUtils.isAuthorizedServer("https://google.com"))
        assertFalse(ServerVerificationUtils.isAuthorizedServer(""))
        assertFalse(ServerVerificationUtils.isAuthorizedServer(null))
    }

    @Test
    fun sha256File_hashesFileContentCorrectly() {
        val file = File.createTempFile("medusa_test", ".bin")
        try {
            file.writeText("hello")
            // SHA-256 de "hello" (sin salto de línea).
            assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                ServerVerificationUtils.sha256File(file)
            )
        } finally {
            file.delete()
        }
    }
}
