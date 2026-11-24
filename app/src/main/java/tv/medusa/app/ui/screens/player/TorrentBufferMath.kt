package tv.medusa.app.ui.screens.player

/**
 * Cálculo del búfer del overlay TOD, separado de la vista para poder probarlo.
 *
 * El backend puede devolver `buffer_bytes` a 0 mientras `downloaded_bytes` ya
 * avanza (o al revés). La barra, la ETA y el aviso de "conectando con peers"
 * tienen que mirar el mismo valor o la interfaz se contradice.
 */
internal data class TorrentBufferInfo(
    val bufferBytes: Long,
    val displayedBytes: Long,
    val targetBytes: Long,
    val progress: Float,
    val etaSeconds: Long,
    val isZeroProgress: Boolean
)

private const val MIN_TARGET_BYTES = 32L * 1024L * 1024L
private const val MAX_TARGET_BYTES = 128L * 1024L * 1024L
private const val DEFAULT_TARGET_BYTES = 50L * 1024L * 1024L
private const val ZERO_PROGRESS_BYTES = 1024L

internal fun torrentBufferInfo(
    bufferBytes: Long?,
    downloadedBytes: Long?,
    sizeBytes: Long,
    progress: Float,
    bufferRequired: Long?,
    speedBps: Long,
    streamReady: Boolean
): TorrentBufferInfo {
    val safeProgress = progress.coerceIn(0f, 1f)
    val estimatedDownloaded = if (sizeBytes > 0L) {
        (safeProgress * sizeBytes).toLong()
    } else {
        0L
    }
    val downloaded = downloadedBytes?.takeIf { it > 0L } ?: estimatedDownloaded
    val contiguous = bufferBytes?.takeIf { it > 0L } ?: downloaded
    // Lo que se enseña: si hay descarga, es lo que el usuario ve avanzar.
    val displayed = downloaded.takeIf { it > 0L } ?: contiguous

    val target = when {
        (bufferRequired ?: 0L) > 0L -> bufferRequired!!
        sizeBytes > 0L -> (sizeBytes / 120L).coerceIn(MIN_TARGET_BYTES, MAX_TARGET_BYTES)
        else -> DEFAULT_TARGET_BYTES
    }

    val bufferProgress = when {
        streamReady -> 1.0f
        target > 0L -> (displayed.toFloat() / target.toFloat()).coerceIn(0f, 0.98f)
        else -> safeProgress
    }

    val etaSeconds = if (!streamReady && speedBps > 0L && displayed < target) {
        ((target - displayed) / speedBps).coerceAtLeast(1L)
    } else {
        0L
    }

    return TorrentBufferInfo(
        bufferBytes = contiguous,
        displayedBytes = displayed,
        targetBytes = target,
        progress = bufferProgress,
        etaSeconds = etaSeconds,
        isZeroProgress = displayed <= ZERO_PROGRESS_BYTES
    )
}

/** El watchdog de resolución es mucho más largo en On-Demand: hay torrent de por medio. */
internal fun playerResolveTimeoutMs(isOnDemand: Boolean): Long =
    if (isOnDemand) TORRENT_PREP_TIMEOUT_MS else 75_000L
