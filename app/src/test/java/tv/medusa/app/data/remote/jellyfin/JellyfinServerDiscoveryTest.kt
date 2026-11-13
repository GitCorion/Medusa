package tv.medusa.app.data.remote.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinServerDiscoveryTest {

    @Test
    fun `usa Address cuando EndpointAddress viene null`() {
        val json = """
            {"Address":"http://192.168.1.50:8096","Id":"abc","Name":"Salon","EndpointAddress":null}
        """.trimIndent()

        val server = JellyfinServerDiscovery.parseResponse(json, "192.168.1.50")

        assertEquals("http://192.168.1.50:8096", server?.address)
        assertEquals("Salon", server?.name)
        assertEquals("abc", server?.id)
    }

    @Test
    fun `usa Address cuando EndpointAddress es la cadena null`() {
        val json = """
            {"Address":"http://192.168.1.50:8096","Name":"Salon","EndpointAddress":"null"}
        """.trimIndent()

        val server = JellyfinServerDiscovery.parseResponse(json, "192.168.1.50")

        assertEquals("http://192.168.1.50:8096", server?.address)
    }

    @Test
    fun `cae a EndpointAddress si Address no es utilizable`() {
        val json = """
            {"Address":null,"Name":"Salon","EndpointAddress":"https://jellyfin.example.com"}
        """.trimIndent()

        val server = JellyfinServerDiscovery.parseResponse(json, "192.168.1.50")

        assertEquals("https://jellyfin.example.com", server?.address)
    }

    @Test
    fun `cae al host de origen si no hay direccion`() {
        val json = """{"Name":"Salon"}"""

        val server = JellyfinServerDiscovery.parseResponse(json, "192.168.1.50")

        assertEquals("http://192.168.1.50:8096", server?.address)
    }

    @Test
    fun `anade esquema http a direcciones sin protocolo`() {
        assertEquals("http://192.168.1.50:8096", JellyfinServerDiscovery.normalizeAddress("192.168.1.50:8096"))
        assertEquals("http://192.168.1.50:8096", JellyfinServerDiscovery.normalizeAddress("http://192.168.1.50:8096/"))
    }

    @Test
    fun `descarta direcciones nulas`() {
        assertNull(JellyfinServerDiscovery.normalizeAddress(null))
        assertNull(JellyfinServerDiscovery.normalizeAddress(""))
        assertNull(JellyfinServerDiscovery.normalizeAddress("null"))
        assertNull(JellyfinServerDiscovery.normalizeAddress("undefined"))
    }
}
