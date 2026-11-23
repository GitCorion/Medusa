package tv.medusa.app.data.remote.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MedusaCapabilitiesDto(
    @SerialName("api_version") val apiVersion: Int = 0,
    @SerialName("plugin_version") val pluginVersion: String? = null,
    @SerialName("features") val features: List<String> = emptyList(),
    @SerialName("proxy_configured") val proxyConfigured: Boolean = false
) {
    val isSupported: Boolean
        get() = apiVersion == 1 &&
                features.any { it.equals("status", ignoreCase = true) } &&
                features.any { it.equals("prepare", ignoreCase = true) }
}

@Serializable
data class TodTorrentStatusDto(
    @SerialName("state") val state: String = "",
    @SerialName("progress") val progress: Float? = null,
    @SerialName("downloaded_bytes") val downloadedBytes: Long? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("speed_bps") val speedBps: Long? = null,
    @SerialName("eta_seconds") val etaSeconds: Long? = null,
    @SerialName("seeds") val seeds: Int? = null,
    @SerialName("peers") val peers: Int? = null,
    @SerialName("stream_ready") val streamReady: Boolean = false,
    @SerialName("release_id") val releaseId: Long? = null,
    @SerialName("quality") val quality: String? = null,
    @SerialName("audio") val audio: String? = null,
    @SerialName("tracker") val tracker: String? = null,
    @SerialName("buffer_required") val bufferRequired: Long? = null,
    @SerialName("buffer_bytes") val bufferBytes: Long? = null,
    @SerialName("tail_ready") val tailReady: Boolean? = null,
    @SerialName("stream_generation") val streamGeneration: Long? = null,
    @SerialName("stream_switched_at") val streamSwitchedAt: String? = null,
    @SerialName("stream_switch_reason") val streamSwitchReason: String? = null
) {
    val safeProgress: Float
        get() = (progress ?: 0f).coerceIn(0f, 1f)
    val safeSpeedBps: Long
        get() = speedBps ?: 0L
    val safeEtaSeconds: Long
        get() = etaSeconds ?: 0L
    val safeSeeds: Int
        get() = seeds ?: 0
    val safePeers: Int
        get() = peers ?: 0
}

@Serializable
data class MedusaStatusDto(
    @SerialName("is_catalog") val isCatalog: Boolean = false,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("key") val key: String? = null,
    @SerialName("release_id") val releaseId: Long? = null,
    @SerialName("status") val status: TodTorrentStatusDto? = null
)

@Serializable
data class MedusaStatusResponse(
    @SerialName("is_catalog") val isCatalog: Boolean = false,
    @SerialName("status") val status: TodTorrentStatusDto? = null
) {
    val is_catalog: Boolean get() = isCatalog
}

@Serializable
data class MedusaErrorDto(
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("detail") val detail: String? = null,
    @SerialName("attempts") val attempts: List<MedusaAttemptDto> = emptyList()
)

@Serializable
data class MedusaAttemptDto(
    @SerialName("release_id") val releaseId: Long? = null,
    @SerialName("tracker") val tracker: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("detail") val detail: String? = null
)

val medusaJson: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

fun medusaErrorMessage(body: String?): String? = runCatching {
    if (body.isNullOrBlank()) return null
    medusaJson.decodeFromString<MedusaErrorDto>(body).message
}.getOrNull()

class MedusaHttpException(
    val statusCode: Int,
    val serverMessage: String?,
    cause: Throwable? = null
) : java.io.IOException(serverMessage ?: "Medusa HTTP $statusCode", cause)

@Serializable
data class MedusaPrepareDetailDto(
    @SerialName("prepared") val prepared: Boolean = false,
    @SerialName("status") val status: TodTorrentStatusDto? = null
)

@Serializable
data class MedusaPrepareResponseDto(
    @SerialName("is_catalog") val isCatalog: Boolean = false,
    @SerialName("key") val key: String? = null,
    @SerialName("release_id") val releaseId: Long? = null,
    @SerialName("prepared") val prepared: MedusaPrepareDetailDto? = null
)

/**
 * Utilidades para formatear velocidad, bytes y ETA en el overlay del torrent.
 */
object TodFormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            val gb = mb / 1024.0
            String.format(java.util.Locale.US, "%.1f GB", gb)
        } else if (mb >= 10.0) {
            String.format(java.util.Locale.US, "%.0f MB", mb)
        } else {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val mbps = bytesPerSec / (1024.0 * 1024.0)
        return if (mbps >= 1.0) {
            String.format(java.util.Locale.US, "%.1f MB/s", mbps)
        } else {
            val kbps = bytesPerSec / 1024.0
            String.format(java.util.Locale.US, "%.0f KB/s", kbps)
        }
    }

    fun formatEta(etaSeconds: Long): String {
        if (etaSeconds <= 0) return ""
        val minutes = etaSeconds / 60
        val remSec = etaSeconds % 60
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60
                val remMin = minutes % 60
                "${hours} h ${remMin} min"
            }
            minutes > 0 -> "${minutes} min"
            else -> "${remSec} s"
        }
    }
}

/**
 * Estado UI del overlay de preparación de torrent en PlayerScreen.
 */
data class TorrentOverlayUiState(
    val itemId: String,
    val title: String = "",
    val backdropUrl: String? = null,
    val state: String = "COLD", // COLD, DOWNLOADING, PAUSED, SEEDING, AVAILABLE, ERROR, UNAVAILABLE
    val progress: Float = 0f, // 0f..1f buffer progress
    val downloadedBytes: Long = 0L,
    val targetBufferBytes: Long = 50L * 1024L * 1024L,
    val speedBps: Long = 0L,
    val etaSeconds: Long = 0L, // buffer ETA
    val seeds: Int = 0,
    val peers: Int = 0,
    val streamReady: Boolean = false,
    val quality: String? = null,
    val audio: String? = null,
    val statusText: String = "Preparando búfer de reproducción…",
    val reasonText: String = "Almacenando los primeros megas para una reproducción fluida sin cortes.",
    val detailText: String = "",
    val isZeroProgressFor10s: Boolean = false,
    val isError: Boolean = false,
    val isUnavailable: Boolean = false,
    val errorMessage: String? = null,
    val availableVersions: List<JellyfinMediaSourceDto> = emptyList(),
    val currentMediaSourceId: String? = null
)
