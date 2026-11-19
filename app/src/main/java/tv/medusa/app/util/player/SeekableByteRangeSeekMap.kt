package tv.medusa.app.util.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint

/**
 * SeekMap que mapea proporcionalmente el tiempo a rangos de bytes de un fichero HTTP estático.
 * Permite que ExoPlayer marque el stream como seekable (window.isSeekable = true) y envíe peticiones
 * HTTP Range nativas (HTTP 206) sin requerir re-transcodificación en el servidor Jellyfin.
 */
@OptIn(UnstableApi::class)
class SeekableByteRangeSeekMap(
    private val durationUs: Long,
    private val streamLength: Long,
    private val headerOffset: Long = 52L
) : SeekMap {

    override fun isSeekable(): Boolean = true

    override fun getDurationUs(): Long = durationUs

    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        if (durationUs <= 0L || streamLength <= 0L) {
            return SeekMap.SeekPoints(SeekPoint(0L, headerOffset))
        }

        val clampedTimeUs = timeUs.coerceIn(0L, durationUs)
        val fraction = clampedTimeUs.toDouble() / durationUs.toDouble()

        val usableLength = (streamLength - headerOffset).coerceAtLeast(1L)
        val estimatedOffset = headerOffset + (fraction * usableLength).toLong()
        val clampedOffset = estimatedOffset.coerceIn(headerOffset, (streamLength - 1024L).coerceAtLeast(headerOffset))

        val seekPoint = SeekPoint(clampedTimeUs, clampedOffset)
        return SeekMap.SeekPoints(seekPoint)
    }
}
