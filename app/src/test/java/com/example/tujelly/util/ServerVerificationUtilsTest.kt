package com.example.tujelly.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ServerVerificationUtilsTest {

    // Dominio de prueba codificado para evitar cadenas en texto plano en el repositorio
    private val testTargetHost = String(Base64.getDecoder().decode("amVsbHlmaW4udHV0ZXV2ZWJveC51aw=="))
    private val testTargetRoot = String(Base64.getDecoder().decode("dHV0ZXV2ZWJveC51aw=="))

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
}
