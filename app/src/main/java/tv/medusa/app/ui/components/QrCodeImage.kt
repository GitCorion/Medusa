package tv.medusa.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Código QR generado en el propio dispositivo.
 *
 * Sustituye a los generadores HTTP de terceros (`api.qrserver.com`) que recibían la URL del
 * servidor Jellyfin y el código QuickConnect. Aquí el dato nunca sale del dispositivo.
 */
@Composable
fun QrCodeImage(
    data: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(data) { encodeQr(data) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

/** QR de [sizePx] × [sizePx]; null si el dato no se puede codificar. */
internal fun encodeQrMatrix(data: String, sizePx: Int = 512): com.google.zxing.common.BitMatrix? = runCatching {
    if (data.isBlank()) return null
    QRCodeWriter().encode(
        data,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(
            // 1 módulo de margen: el contenedor ya pinta el fondo blanco alrededor.
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
    )
}.getOrNull()

internal fun encodeQr(data: String, sizePx: Int = 512): Bitmap? = runCatching {
    val matrix = encodeQrMatrix(data, sizePx) ?: return null
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            pixels[y * sizePx + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}.getOrNull()
