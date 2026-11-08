package tv.medusa.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientFactoryLogRedactionTest {

    @Test
    fun redactLogMessage_hidesQueryTokens() {
        val line = "--> GET http://host/stream?api_key=abcdef&X-Emby-Token=zyxw&MediaSourceId=1"
        val redacted = NetworkClientFactory.redactLogMessage(line)

        assertFalse(redacted.contains("abcdef"))
        assertFalse(redacted.contains("zyxw"))
        assertTrue(redacted.contains("api_key=REDACTED"))
        assertTrue(redacted.contains("MediaSourceId=1"))
    }

    @Test
    fun redactLogMessage_hidesAuthorizationTokenHeader() {
        val line = """Authorization: MediaBrowser Client="Medusa", Token="supersecret""""
        val redacted = NetworkClientFactory.redactLogMessage(line)

        assertFalse(redacted.contains("supersecret"))
        assertTrue(redacted.contains("Token=\"REDACTED\""))
    }

    @Test
    fun redactLogMessage_leavesSafeLinesUntouched() {
        val line = "--> GET https://api.themoviedb.org/3/movie/popular"
        assertEquals(line, NetworkClientFactory.redactLogMessage(line))
    }

    @Test
    fun sha256Fingerprint_formatsEveryByteAsTwoHexDigits() {
        val fingerprint = NetworkClientFactory.sha256Fingerprint(byteArrayOf())

        assertEquals(
            "E3:B0:C4:42:98:FC:1C:14:9A:FB:F4:C8:99:6F:B9:24:27:AE:41:E4:64:9B:93:4C:A4:95:99:1B:78:52:B8:55",
            fingerprint
        )
    }

    @Test
    fun sameFingerprint_ignoresSeparatorsAndCase() {
        assertTrue(NetworkClientFactory.sameFingerprint("AA:BB:CC", "aa bb cc"))
        assertTrue(NetworkClientFactory.sameFingerprint("aabbcc", "AA:BB:CC"))
        assertFalse(NetworkClientFactory.sameFingerprint("AA:BB:CC", "AA:BB:CD"))
        assertFalse(NetworkClientFactory.sameFingerprint(null, "AA:BB:CC"))
    }
}
