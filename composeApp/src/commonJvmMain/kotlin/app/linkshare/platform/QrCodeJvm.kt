package app.linkshare.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

actual object QrCode {
    actual fun encode(value: String): Array<BooleanArray>? {
        return try {
            val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 256, 256)
            Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y) } }
        } catch (_: Exception) { null }
    }
}
