package tv.medusa.app.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.medusa.app.util.player.MatroskaCuesParser
import tv.medusa.app.util.player.SeekableByteRangeSeekMap

class MatroskaCuesParserTest {

    @Test
    fun `parseCuesFromBuffer returns null for empty or non-cues buffer`() {
        assertNull(MatroskaCuesParser.parseCuesFromBuffer(ByteArray(0), 0L, 52L, 1000L))
        assertNull(MatroskaCuesParser.parseCuesFromBuffer(byteArrayOf(1, 2, 3, 4), 0L, 52L, 1000L))
    }

    @Test
    fun `parseCuesFromBuffer parses synthetically created CuePoint`() {
        // Build synthetic Cues element (0x1C53BB6B) containing 1 CuePoint (0xBB)
        // 0xBB (CuePoint):
        //   0xB3 (CueTime): 5000 ms (value: 0x1388)
        //   0xB7 (CueTrackPositions):
        //     0xF1 (CueClusterPosition): 100000 bytes (value: 0x0186A0)
        val cuesHeader = byteArrayOf(0x1C.toByte(), 0x53.toByte(), 0xBB.toByte(), 0x6B.toByte())
        val cueTime = byteArrayOf(0xB3.toByte(), 0x82.toByte(), 0x13.toByte(), 0x88.toByte())
        val cueClusterPos = byteArrayOf(0xF1.toByte(), 0x83.toByte(), 0x01.toByte(), 0x86.toByte(), 0xA0.toByte())
        val cueTrackPositions = byteArrayOf(0xB7.toByte(), (0x80 or cueClusterPos.size).toByte()) + cueClusterPos

        val cuePointContent = cueTime + cueTrackPositions
        val cuePoint = byteArrayOf(0xBB.toByte(), (0x80 or cuePointContent.size).toByte()) + cuePointContent

        val cuesContent = cuePoint
        val cuesElement = cuesHeader + byteArrayOf((0x80 or cuesContent.size).toByte()) + cuesContent

        val seekMap = MatroskaCuesParser.parseCuesFromBuffer(
            data = cuesElement,
            bufferStartOffset = 0L,
            segmentDataOffset = 52L,
            totalDurationUs = 10_000_000L
        )

        assertNotNull("SeekMap no debe ser nulo para un buffer de Cues válido", seekMap)
        assertTrue("SeekMap generado a partir de Cues debe ser seekable", seekMap!!.isSeekable)
        val seekPoints = seekMap.getSeekPoints(5_000_000L)
        assertNotNull(seekPoints.first)
        assertEquals(5_000_000L, seekPoints.first.timeUs)
        assertEquals(52L + 100000L, seekPoints.first.position)
    }

    @Test
    fun `SeekableByteRangeSeekMap is always seekable and calculates proportional offsets`() {
        val durationUs = 60_000_000L // 60 segundos
        val streamLength = 1_000_000_000L // 1 GB
        val headerOffset = 52L

        val seekMap = SeekableByteRangeSeekMap(
            durationUs = durationUs,
            streamLength = streamLength,
            headerOffset = headerOffset
        )

        assertTrue(seekMap.isSeekable)
        assertEquals(durationUs, seekMap.durationUs)

        // Al 0%: debe dar el headerOffset
        val at0 = seekMap.getSeekPoints(0L).first
        assertEquals(0L, at0.timeUs)
        assertEquals(headerOffset, at0.position)

        // Al 50% (30 segundos): debe dar aproximadamente la mitad de la longitud
        val at50 = seekMap.getSeekPoints(30_000_000L).first
        assertEquals(30_000_000L, at50.timeUs)
        val expectedOffset = headerOffset + (0.5 * (streamLength - headerOffset)).toLong()
        assertEquals(expectedOffset, at50.position)
    }
}
