package tv.medusa.app.util.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ChunkIndex
import androidx.media3.extractor.SeekMap

@OptIn(UnstableApi::class)
object MatroskaCuesParser {

    private const val TAG = "MatroskaCuesParser"

    /**
     * Parsea la tabla Cues (0x1C53BB6B) de Matroska a partir de un búfer binario
     * (habitualmente los últimos 128 KB del fichero).
     *
     * @param data Búfer que contiene la sección final del fichero MKV.
     * @param bufferStartOffset Offset absoluto en bytes del fichero donde empieza este búfer.
     * @param segmentDataOffset Offset absoluto del inicio de los datos del Segmento (normalmente ~52-59).
     * @param totalDurationUs Duración total en microsegundos (para el ChunkIndex).
     * @return ChunkIndex seekable si se encuentran y parsean los CuePoints, o null si no hay Cues.
     */
    fun parseCuesFromBuffer(
        data: ByteArray,
        bufferStartOffset: Long,
        segmentDataOffset: Long,
        totalDurationUs: Long
    ): SeekMap? {
        if (data.isEmpty()) return null

        val cuesIdx = indexOfBytes(data, byteArrayOf(0x1C.toByte(), 0x53.toByte(), 0xBB.toByte(), 0x6B.toByte()))
        if (cuesIdx == -1) {
            Log.d(TAG, "No se encontró el elemento Cues (0x1C53BB6B) en el búfer")
            return null
        }

        var pos = cuesIdx + 4
        if (pos >= data.size) return null

        val (cuesLen, nextPos) = readEbmlVarint(data, pos)
        pos = nextPos
        val endPos = if (cuesLen > 0) minOf(pos + cuesLen.toInt(), data.size) else data.size

        val timesUsList = ArrayList<Long>(500)
        val offsetsList = ArrayList<Long>(500)

        while (pos < endPos) {
            val elementByte = data[pos].toInt() and 0xFF
            if (elementByte == 0xBB) { // CuePoint
                pos++
                val (cpLen, cpStart) = readEbmlVarint(data, pos)
                pos = cpStart
                val cpEnd = if (cpLen > 0) minOf(pos + cpLen.toInt(), endPos) else endPos

                var cueTimeMs: Long = -1L
                var cueClusterOffset: Long = -1L

                while (pos < cpEnd) {
                    val subIdByte = data[pos].toInt() and 0xFF
                    pos++
                    val (elemLenLong, valStart) = readEbmlVarint(data, pos)
                    pos = valStart
                    val elemLen = elemLenLong.toInt()
                    if (pos + elemLen > cpEnd) {
                        pos = cpEnd
                        break
                    }

                    if (subIdByte == 0xB3) { // CueTime
                        cueTimeMs = readBigEndianUInt(data, pos, elemLen)
                    } else if (subIdByte == 0xB7) { // CueTrackPositions
                        val trackPosEnd = pos + elemLen
                        var trackSubPos = pos
                        while (trackSubPos < trackPosEnd) {
                            val trackElemId = data[trackSubPos].toInt() and 0xFF
                            trackSubPos++
                            val (trackElemLenL, trackValStart) = readEbmlVarint(data, trackSubPos)
                            trackSubPos = trackValStart
                            val trackElemLen = trackElemLenL.toInt()
                            if (trackSubPos + trackElemLen > trackPosEnd) break

                            if (trackElemId == 0xF1) { // CueClusterPosition
                                cueClusterOffset = readBigEndianUInt(data, trackSubPos, trackElemLen)
                            }
                            trackSubPos += trackElemLen
                        }
                    }
                    pos += elemLen
                }

                if (cueTimeMs >= 0L && cueClusterOffset >= 0L) {
                    val timeUs = cueTimeMs * 1000L
                    val absoluteOffset = segmentDataOffset + cueClusterOffset
                    timesUsList.add(timeUs)
                    offsetsList.add(absoluteOffset)
                }
            } else {
                pos++
            }
        }

        if (timesUsList.isEmpty()) {
            Log.w(TAG, "Elemento Cues encontrado pero no se pudieron extraer CuePoints válidos")
            return null
        }

        val count = timesUsList.size
        val sizes = IntArray(count)
        val offsets = LongArray(count)
        val durationsUs = LongArray(count)
        val timesUs = LongArray(count)

        for (i in 0 until count) {
            timesUs[i] = timesUsList[i]
            offsets[i] = offsetsList[i]
        }

        for (i in 0 until count - 1) {
            val sz = offsets[i + 1] - offsets[i]
            sizes[i] = if (sz in 1..Int.MAX_VALUE) sz.toInt() else 65536
            val dur = timesUs[i + 1] - timesUs[i]
            durationsUs[i] = if (dur > 0) dur else 1000L
        }

        // Último bloque
        if (count > 0) {
            val lastIdx = count - 1
            sizes[lastIdx] = 65536
            val remainingDur = totalDurationUs - timesUs[lastIdx]
            durationsUs[lastIdx] = if (remainingDur > 0) remainingDur else 1000L
        }

        Log.i(TAG, "Tabla Cues parseada con éxito: $count CuePoints extraídos (duración total: ${totalDurationUs / 1000}ms)")
        return ChunkIndex(sizes, offsets, durationsUs, timesUs)
    }

    private fun readEbmlVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        if (start >= data.size) return Pair(0L, start)
        val b0 = data[start].toInt() and 0xFF
        var mask = 0x80
        var length = 1
        while (mask > 0 && (b0 and mask) == 0) {
            mask = mask shr 1
            length++
        }
        if (length > 8 || start + length > data.size) return Pair(0L, start + 1)

        var value = (b0 and (mask - 1)).toLong()
        for (i in 1 until length) {
            value = (value shl 8) or ((data[start + i].toInt() and 0xFF).toLong())
        }
        return Pair(value, start + length)
    }

    private fun readBigEndianUInt(data: ByteArray, start: Int, length: Int): Long {
        var value = 0L
        val safeLen = minOf(length, 8)
        for (i in 0 until safeLen) {
            value = (value shl 8) or ((data[start + i].toInt() and 0xFF).toLong())
        }
        return value
    }

    private fun indexOfBytes(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        val max = source.size - target.size
        for (i in 0..max) {
            var found = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }
}
