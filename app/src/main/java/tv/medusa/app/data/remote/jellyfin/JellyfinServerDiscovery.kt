package tv.medusa.app.data.remote.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** Servidor Jellyfin descubierto automáticamente en la red local. */
data class DiscoveredJellyfinServer(
    val name: String,
    val address: String,
    val id: String = ""
)

/**
 * Descubrimiento de servidores Jellyfin mediante el protocolo UDP de broadcast que
 * implementa el propio servidor en el puerto 7359.
 *
 * Se envía la sonda `who is JellyfinServer?` a la dirección de broadcast global y a la
 * de cada interfaz activa (algunos routers/AP filtran 255.255.255.255 pero no el broadcast
 * de subred). Cada servidor responde con un JSON con `Name`, `Address`/`EndpointAddress`
 * e `Id`.
 */
object JellyfinServerDiscovery {

    private const val DISCOVERY_PORT = 7359
    private const val PROBE = "who is JellyfinServer?"
    private const val MAX_RESPONSE_BYTES = 4096
    private const val RECEIVE_TIMEOUT_MS = 400

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Serializable
    private data class DiscoveryResponse(
        @SerialName("Address") val address: String? = null,
        @SerialName("Id") val id: String? = null,
        @SerialName("Name") val name: String? = null,
        @SerialName("EndpointAddress") val endpointAddress: String? = null
    )

    suspend fun discover(timeoutMs: Long = 4000L): List<DiscoveredJellyfinServer> =
        withContext(Dispatchers.IO) {
            val found = LinkedHashMap<String, DiscoveredJellyfinServer>()

            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = RECEIVE_TIMEOUT_MS

                    val payload = PROBE.toByteArray(Charsets.UTF_8)
                    broadcastAddresses().forEach { address ->
                        runCatching {
                            socket.send(
                                DatagramPacket(payload, payload.size, address, DISCOVERY_PORT)
                            )
                        }
                    }

                    val deadline = System.currentTimeMillis() + timeoutMs
                    val buffer = ByteArray(MAX_RESPONSE_BYTES)
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue
                        } catch (_: Exception) {
                            break
                        }

                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        val server = runCatching {
                            parseResponse(text, packet.address.hostAddress)
                        }.getOrNull() ?: continue
                        found[server.address] = server
                    }
                }
            }

            found.values.sortedBy { it.name.lowercase() }
        }

    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()
        runCatching { addresses.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .forEach { addresses.add(it) }
        }
        return addresses.toList()
    }

    internal fun parseResponse(text: String, sourceHost: String?): DiscoveredJellyfinServer? {
        val response = json.decodeFromString<DiscoveryResponse>(text)

        // `Address` es la dirección local del servidor; `EndpointAddress` puede venir a null
        // o ser la dirección pública. Se prefiere la local y se descartan los valores nulos
        // que algunos servidores envían como la cadena "null".
        val address = sequenceOf(response.address, response.endpointAddress)
            .mapNotNull { normalizeAddress(it) }
            .firstOrNull()
            ?: sourceHost?.takeIf { it.isNotBlank() }?.let { "http://$it:8096" }
            ?: return null

        val name = response.name
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: address

        return DiscoveredJellyfinServer(
            name = name,
            address = address,
            id = response.id.orEmpty()
        )
    }

    internal fun normalizeAddress(raw: String?): String? {
        val clean = raw
            ?.trim()
            ?.takeIf {
                it.isNotBlank() &&
                    !it.equals("null", ignoreCase = true) &&
                    !it.equals("undefined", ignoreCase = true)
            }
            ?: return null

        val withScheme = if (
            clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true)
        ) {
            clean
        } else {
            "http://$clean"
        }
        return withScheme.trimEnd('/')
    }
}
