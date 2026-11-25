package tv.medusa.app.util.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.medusa.app.data.remote.NetworkClientFactory

/**
 * ExtractorsFactory optimizada para streams HTTP en Jellyfin (incluyendo .strm y torrents).
 * Garantiza que todo stream progresivo con longitud y duración conocidas sea considerado
 * seekable por ExoPlayer, permitiendo reposicionamiento nativo con HTTP Range (código 206)
 * sin reiniciar la descarga ni invocar transcodificación ffmpeg en el servidor.
 */
@OptIn(UnstableApi::class)
class SeekableExtractorsFactory(
    private val fallbackDurationMs: Long = 0L,
    private val authHeader: String? = null,
    private val trustedHost: String? = null,
    private val okHttpClient: OkHttpClient = NetworkClientFactory.okHttpClient,
    private val allowAsyncCuesFetch: Boolean = true
) : ExtractorsFactory {

    private val defaultFactory = DefaultExtractorsFactory()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun createExtractors(): Array<Extractor> {
        val original = defaultFactory.createExtractors()
        return original.map { wrapExtractor(it, null) }.toTypedArray()
    }

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
        val original = defaultFactory.createExtractors(uri, responseHeaders)
        return original.map { wrapExtractor(it, uri.toString()) }.toTypedArray()
    }

    private fun wrapExtractor(extractor: Extractor, streamUrl: String?): Extractor {
        return SeekableExtractorWrapper(
            delegate = extractor,
            fallbackDurationMs = fallbackDurationMs,
            streamUrl = streamUrl,
            authHeader = authHeader,
            trustedHost = trustedHost,
            okHttpClient = okHttpClient,
            backgroundScope = backgroundScope,
            allowAsyncCuesFetch = allowAsyncCuesFetch
        )
    }

    private class SeekableExtractorWrapper(
        private val delegate: Extractor,
        private val fallbackDurationMs: Long,
        private val streamUrl: String?,
        private val authHeader: String?,
        private val trustedHost: String?,
        private val okHttpClient: OkHttpClient,
        private val backgroundScope: CoroutineScope,
        private val allowAsyncCuesFetch: Boolean
    ) : Extractor {

        private var streamLength: Long = C.LENGTH_UNSET.toLong()
        private var reportedSeekMap: SeekMap? = null
        private var currentOutput: ExtractorOutput? = null
        private var cuesFetchAttempted = false
        private var isReleased = false

        override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

        override fun init(output: ExtractorOutput) {
            this.currentOutput = output

            val wrappedOutput = object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = output.track(id, type)
                override fun endTracks() = output.endTracks()

                override fun seekMap(seekMap: SeekMap) {
                    reportedSeekMap = seekMap
                    if (seekMap.isSeekable) {
                        output.seekMap(seekMap)
                    } else {
                        val durationUs = when {
                            seekMap.durationUs > 0 -> seekMap.durationUs
                            fallbackDurationMs > 0 -> fallbackDurationMs * 1000L
                            else -> C.TIME_UNSET
                        }

                        if (durationUs > 0 && streamLength > 0) {
                            Log.i("SeekableExtractor", "SeekMap original unseekable. Emitiendo SeekableByteRangeSeekMap (dur=${durationUs / 1000}ms, len=$streamLength)")
                            output.seekMap(SeekableByteRangeSeekMap(durationUs, streamLength))
                            attemptAsyncCuesExtraction(durationUs)
                        } else {
                            output.seekMap(seekMap)
                        }
                    }
                }
            }

            delegate.init(wrappedOutput)
        }

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
            if (streamLength <= 0 && input.length > 0) {
                streamLength = input.length
                val lastMap = reportedSeekMap
                val out = currentOutput
                if (out != null && (lastMap == null || !lastMap.isSeekable)) {
                    val durationUs = when {
                        lastMap != null && lastMap.durationUs > 0 -> lastMap.durationUs
                        fallbackDurationMs > 0 -> fallbackDurationMs * 1000L
                        else -> C.TIME_UNSET
                    }
                    if (durationUs > 0) {
                        Log.i("SeekableExtractor", "Stream length detectada ($streamLength bytes). Emitiendo SeekableByteRangeSeekMap.")
                        out.seekMap(SeekableByteRangeSeekMap(durationUs, streamLength))
                        attemptAsyncCuesExtraction(durationUs)
                    }
                }
            }

            return delegate.read(input, seekPosition)
        }

        override fun seek(position: Long, timeUs: Long) {
            delegate.seek(position, timeUs)
        }

        override fun release() {
            isReleased = true
            currentOutput = null
            delegate.release()
        }

        private fun attemptAsyncCuesExtraction(durationUs: Long) {
            if (!allowAsyncCuesFetch || cuesFetchAttempted || streamUrl.isNullOrBlank() || streamLength <= 65536L) return
            if (delegate !is MatroskaExtractor) return
            cuesFetchAttempted = true

            backgroundScope.launch {
                try {
                    val readBytes = 131072L.coerceAtMost(streamLength)
                    val startOffset = streamLength - readBytes
                    val endOffset = streamLength - 1L

                    val reqBuilder = Request.Builder()
                        .url(streamUrl)
                        .header("Range", "bytes=$startOffset-$endOffset")

                    // Nunca enviar credenciales a un host distinto al del stream original.
                    val requestHost = reqBuilder.build().url.host
                    if (!authHeader.isNullOrBlank() &&
                        (trustedHost.isNullOrBlank() || requestHost.equals(trustedHost, ignoreCase = true))
                    ) {
                        reqBuilder.header("Authorization", authHeader)
                        reqBuilder.header("X-Emby-Authorization", authHeader)
                    }

                    val response = okHttpClient.newCall(reqBuilder.build()).execute()
                    response.use { resp ->
                        if (resp.isSuccessful && resp.body != null) {
                            val bytes = resp.body!!.bytes()
                            val chunkIndex = MatroskaCuesParser.parseCuesFromBuffer(
                                data = bytes,
                                bufferStartOffset = startOffset,
                                segmentDataOffset = 52L,
                                totalDurationUs = durationUs
                            )
                            if (chunkIndex != null && !isReleased) {
                                currentOutput?.let { out ->
                                    Log.i("SeekableExtractor", "SeekMap actualizado a ChunkIndex con tabla Cues exacta de EOF")
                                    out.seekMap(chunkIndex)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("SeekableExtractor", "No se pudo recuperar Cues desde EOF de forma asíncrona: ${e.message}")
                }
            }
        }
    }
}
