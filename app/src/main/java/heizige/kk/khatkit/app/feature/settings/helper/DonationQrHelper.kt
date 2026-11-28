package heizige.kk.khatkit.app.feature.settings.helper

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import heizige.kk.khatkit.app.R
import java.io.IOException
import java.io.OutputStream

object DonationQrHelper {
    /** Writes a PNG without closing [output]; the caller owns the stream. */
    fun writePng(context: Context, @DrawableRes drawableRes: Int, output: OutputStream) {
        require(
            drawableRes == R.drawable.donate_alipay_qr ||
                drawableRes == R.drawable.donate_wechat_qr
        ) { "Unsupported donation QR drawable: $drawableRes" }

        val drawable = ContextCompat.getDrawable(context, drawableRes)
            ?: throw Resources.NotFoundException("Donation QR drawable not found: $drawableRes")
        drawable.mutate().apply {
            setTint(Color.BLACK)
            setBounds(0, 0, OUTPUT_SIZE, OUTPUT_SIZE)
        }

        val bitmap = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            drawable.draw(canvas)
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("Failed to encode donation QR as PNG")
            }
        } finally {
            bitmap.recycle()
        }
    }

    private const val OUTPUT_SIZE = 1024
}
