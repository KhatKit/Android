package heizige.kk.khatkit.app.feature.settings

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import heizige.kk.khatkit.app.feature.settings.helper.DonationPaymentLinkHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DonationPaymentLinkHelperTest {
    @Test
    fun `decodes payment link without changing Alipay destination`() {
        assertDecoded("https://qr.alipay.com/fkx12240cdgdefoj6flf078")
    }

    @Test
    fun `preserves WeChat scheme and case sensitive payment token`() {
        assertDecoded("wxp://f2f0BYJU7FHYqAPrDQLmsw41Jt4NGKO94p_yrAHiQeUJPJ8")
    }

    @Test
    fun `unreadable QR returns no link rather than a fabricated success`() {
        assertNull(DonationPaymentLinkHelper.decode(240, 240, IntArray(240 * 240) { -1 }))
    }

    private fun assertDecoded(link: String) {
        val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 360, 360)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) 0xFF000000.toInt() else -1
        }
        assertEquals(link, DonationPaymentLinkHelper.decode(matrix.width, matrix.height, pixels))
    }
}
