package tv.medusa.app.ui.components

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El QR se genera en local; este test hace el viaje completo: codificar el payload
 * (URL + código QuickConnect) y volver a leerlo con el decoder de zxing.
 */
class QrCodeImageTest {

    @Test
    fun quickConnectPayload_roundTripsThroughQr() {
        val payload = "http://192.168.1.20:8096/web/index.html#!/quickconnect?code=ABC123XY"
        assertEquals(payload, decode(encodeQrMatrix(payload, 256)))
    }

    @Test
    fun httpsPayloadWithQuery_roundTripsThroughQr() {
        val payload = "https://jellyfin.example.com/web/index.html#!/quickconnect?code=9Z8Y7X&server=homeserver"
        assertEquals(payload, decode(encodeQrMatrix(payload, 320)))
    }

    @Test
    fun blankPayload_producesNoMatrix() {
        assertNull(encodeQrMatrix(""))
        assertNull(encodeQrMatrix("   "))
    }

    private fun decode(matrix: BitMatrix?): String {
        assertNotNull(matrix)
        val source = MatrixLuminanceSource(matrix!!)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    private class MatrixLuminanceSource(private val matrix: BitMatrix) :
        LuminanceSource(matrix.width, matrix.height) {

        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = if (row != null && row.size >= width) row else ByteArray(width)
            for (x in 0 until width) {
                out[x] = if (matrix.get(x, y)) BLACK else WHITE
            }
            return out
        }

        override fun getMatrix(): ByteArray {
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    out[y * width + x] = if (matrix.get(x, y)) BLACK else WHITE
                }
            }
            return out
        }
    }

    private companion object {
        const val BLACK: Byte = 0
        const val WHITE: Byte = 0xFF.toByte()
    }
}
