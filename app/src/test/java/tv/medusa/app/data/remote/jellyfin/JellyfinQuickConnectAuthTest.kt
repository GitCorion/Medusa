package tv.medusa.app.data.remote.jellyfin

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinQuickConnectAuthTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `el canje de quick connect envia el secreto en el campo Secret`() {
        val body = json.encodeToString(JellyfinQuickConnectAuthRequest(secret = "abc123"))

        assertEquals("""{"Secret":"abc123"}""", body)
    }

    @Test
    fun `la respuesta del canje expone el token de acceso`() {
        val response = json.decodeFromString<JellyfinAuthResponse>(
            """
            {
              "User": {"Id": "user-1", "Name": "alejandro"},
              "AccessToken": "token-123",
              "ServerId": "server-1"
            }
            """.trimIndent()
        )

        assertEquals("token-123", response.effectiveToken)
        assertEquals("user-1", response.effectiveUser.effectiveId)
        assertEquals("alejandro", response.effectiveUser.effectiveName)
    }

    @Test
    fun `QuickConnect Connect no devuelve token pero si el estado autenticado`() {
        val result = json.decodeFromString<JellyfinQuickConnectResult>(
            """
            {
              "Authenticated": true,
              "Secret": "abc123",
              "Code": "123456",
              "DeviceId": "device-1"
            }
            """.trimIndent()
        )

        assertTrue(result.isAuthed)
        assertEquals("abc123", result.effectiveSecret)
        assertEquals("", result.effectiveToken.orEmpty())
    }
}
