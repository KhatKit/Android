package heizige.kk.khatkit.app.feature.settings.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

internal object DonationPaymentLinkHelper {
    /** Read the actual bundled QR, so copying and scanning always use the same destination. */
    fun read(context: Context, @DrawableRes resource: Int): String? {
        val drawable = ContextCompat.getDrawable(context, resource)?.mutate() ?: return null
        val bitmap = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            drawable.setTint(Color.BLACK)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(canvas)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            decode(bitmap.width, bitmap.height, pixels)
        } finally {
            bitmap.recycle()
        }
    }

    internal fun decode(width: Int, height: Int, pixels: IntArray): String? {
        val reader = MultiFormatReader()
        return try {
            reader.decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))),
                mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true),
            ).text.takeIf { it.isNotBlank() }
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}
