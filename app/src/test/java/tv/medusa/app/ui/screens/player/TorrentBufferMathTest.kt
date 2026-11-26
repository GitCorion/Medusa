package tv.medusa.app.ui.screens.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El overlay TOD no puede contradecirse: la barra, la ETA y el aviso de
 * "conectando con peers" tienen que salir del mismo valor.
 */
class TorrentBufferMathTest {

    private val mb = 1024L * 1024L
    private val size = 4000L * mb

    @Test
    fun `usa buffer_bytes cuando es el unico dato`() {
        val info = torrentBufferInfo(
            bufferBytes = 8 * mb,
            downloadedBytes = null,
            sizeBytes = size,
            progress = 0f,
            bufferRequired = 16 * mb,
            speedBps = 0L,
            streamReady = false
        )

        assertEquals(8 * mb, info.displayedBytes)
        assertEquals(8 * mb, info.bufferBytes)
        assertEquals(0.5f, info.progress, 0.001f)
        assertFalse(info.isZeroProgress)
    }

    @Test
    fun `cae a downloaded_bytes cuando buffer_bytes es cero`() {
        val info = torrentBufferInfo(
            bufferBytes = 0L,
            downloadedBytes = 500 * mb,
            sizeBytes = size,
            progress = 0.12f,
            bufferRequired = 16 * mb,
            speedBps = 2 * mb,
            streamReady = false
        )

        assertEquals(500 * mb, info.displayedBytes)
        assertEquals(0.98f, info.progress, 0.001f)
        assertFalse(info.isZeroProgress)
        // Ya ha pasado el objetivo mostrado: no tiene sentido enseñar ETA.
        assertEquals(0L, info.etaSeconds)
    }

    @Test
    fun `la eta usa el mismo valor que la barra`() {
        val info = torrentBufferInfo(
            bufferBytes = 0L,
            downloadedBytes = 4 * mb,
            sizeBytes = size,
            progress = 0.001f,
            bufferRequired = 16 * mb,
            speedBps = 2 * mb,
            streamReady = false
        )

        assertEquals(4 * mb, info.displayedBytes)
        assertEquals(6L, info.etaSeconds)
    }

    @Test
    fun `stream_ready completa la barra y quita la eta`() {
        val info = torrentBufferInfo(
            bufferBytes = 1 * mb,
            downloadedBytes = 1 * mb,
            sizeBytes = size,
            progress = 0.001f,
            bufferRequired = 16 * mb,
            speedBps = 5 * mb,
            streamReady = true
        )

        assertEquals(1.0f, info.progress, 0.0f)
        assertEquals(0L, info.etaSeconds)
    }

    @Test
    fun `sin datos ni tamano avisa de cero progreso`() {
        val info = torrentBufferInfo(
            bufferBytes = null,
            downloadedBytes = null,
            sizeBytes = 0L,
            progress = 0f,
            bufferRequired = null,
            speedBps = 0L,
            streamReady = false
        )

        assertTrue(info.isZeroProgress)
        assertEquals(0L, info.displayedBytes)
        assertEquals(50 * mb, info.targetBytes)
    }

    @Test
    fun `el objetivo por tamano se queda en el rango 32-128 MB`() {
        val info = torrentBufferInfo(
            bufferBytes = null,
            downloadedBytes = null,
            sizeBytes = size,
            progress = 0f,
            bufferRequired = null,
            speedBps = 0L,
            streamReady = false
        )

        assertEquals(size / 120, info.targetBytes)
        assertTrue(info.targetBytes in (32 * mb)..(128 * mb))
    }

    @Test
    fun `el watchdog es de 15 minutos solo en on-demand`() {
        assertEquals(900_000L, playerResolveTimeoutMs(isOnDemand = true))
        assertEquals(75_000L, playerResolveTimeoutMs(isOnDemand = false))
    }

    @Test
    fun `recuperacion on-demand intercepta 503 desde el primer candidato`() {
        assertTrue(
            shouldRecoverOnDemandError(
                isOnDemand = true,
                isAudioDecoderFailure = false,
                isDecoderInitFailure = false,
                isContainerError = false,
                httpCode = 503,
                isTimeoutOrSeekError = false,
                isLastCandidate = false,
                recoveryAttempts = 0
            )
        )
    }

    @Test
    fun `recuperacion on-demand no intercepta codecs ni contenido normal ni supera el limite`() {
        assertFalse(
            shouldRecoverOnDemandError(
                isOnDemand = false,
                isAudioDecoderFailure = false,
                isDecoderInitFailure = false,
                isContainerError = false,
                httpCode = 503,
                isTimeoutOrSeekError = true,
                isLastCandidate = true,
                recoveryAttempts = 0
            )
        )
        assertFalse(
            shouldRecoverOnDemandError(
                isOnDemand = true,
                isAudioDecoderFailure = true,
                isDecoderInitFailure = false,
                isContainerError = false,
                httpCode = 503,
                isTimeoutOrSeekError = false,
                isLastCandidate = true,
                recoveryAttempts = 0
            )
        )
        assertFalse(
            shouldRecoverOnDemandError(
                isOnDemand = true,
                isAudioDecoderFailure = false,
                isDecoderInitFailure = false,
                isContainerError = true,
                httpCode = 503,
                isTimeoutOrSeekError = false,
                isLastCandidate = true,
                recoveryAttempts = 0
            )
        )
        assertFalse(
            shouldRecoverOnDemandError(
                isOnDemand = true,
                isAudioDecoderFailure = false,
                isDecoderInitFailure = false,
                isContainerError = false,
                httpCode = 503,
                isTimeoutOrSeekError = false,
                isLastCandidate = false,
                recoveryAttempts = MAX_ON_DEMAND_RECOVERY_ATTEMPTS
            )
        )
    }

    @Test
    fun `recuperacion on-demand rescata timeout o seek en ultimo candidato`() {
        assertFalse(
            shouldRecoverOnDemandError(
                isOnDemand = true,
                isAudioDecoderFailure = false,
                isDecoderInitFailure = false,
                isContainerError = false,
                httpCode = 0,
                isTimeoutOrSeekError = true,
                isLastCandidate = false,
                recoveryAttempts = 0
            )
        )
        assertTrue(
            shouldRecoverOnDemandError(
                isOnDemand = true,
                isAudioDecoderFailure = false,
                isDecoderInitFailure = false,
                isContainerError = false,
                httpCode = 0,
                isTimeoutOrSeekError = true,
                isLastCandidate = true,
                recoveryAttempts = 1
            )
        )
    }

    @Test
    fun `clasifica ERROR_CODE_DECODING_FAILED de MediaCodec video como fallo de video`() {
        assertEquals(
            DecoderFailureKind.VIDEO,
            classifyDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
                causeMessage = "Decoder failed: c2.mtk.hevc.decoder",
                errorMessage = "MediaCodecVideoRenderer error, format=video/dolby-vision"
            )
        )
        assertEquals(
            DecoderFailureKind.VIDEO,
            classifyDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                causeMessage = "NO_EXCEEDS_CAPABILITIES"
            )
        )
        assertEquals(
            DecoderFailureKind.VIDEO,
            classifyDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                causeMessage = null
            )
        )
    }

    @Test
    fun `clasifica fallos de decoder de audio y red sin confundirlos`() {
        assertEquals(
            DecoderFailureKind.AUDIO,
            classifyDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                causeMessage = null
            )
        )
        assertEquals(
            DecoderFailureKind.AUDIO,
            classifyDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                causeMessage = "Decoder init failed for audio/ac3"
            )
        )
        assertEquals(
            DecoderFailureKind.NONE,
            classifyDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                causeMessage = "HTTP 503"
            )
        )
    }
}
