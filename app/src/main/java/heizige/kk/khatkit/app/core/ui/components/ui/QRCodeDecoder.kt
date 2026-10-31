package heizige.kk.khatkit.app.core.ui.components.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/**
 * 用 zxing 从相机帧的亮度（Y）平面里识别二维码（上游 d8ba9cf67 的 QRCodeDecoder）。
 *
 * 抽成纯 JVM 类是为了能单测：[rowStride] 可以大于 [width]（相机帧每行末尾可能有填充），
 * 这一条在 Quickie / ML Kit 那套里没法在 JVM 上覆盖。
 *
 * @param luminance 连续的 Y 平面字节
 * @param rowStride 每行实际字节数（含行尾填充）
 * @param width 有效像素宽度
 * @param height 帧高
 */
internal class QRCodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }

    fun decode(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: ReaderException) {
            // 这一帧里没有可识别的二维码
            null
        } finally {
            reader.reset()
        }
    }
}
