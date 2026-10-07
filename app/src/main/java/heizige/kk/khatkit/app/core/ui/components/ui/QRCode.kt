package heizige.kk.khatkit.app.core.ui.components.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QRCode(
    value: String,
    modifier: Modifier = Modifier,
    size: Int = 512,
    color: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified
) {
    val actualColor = color.takeOrElse { MaterialTheme.colorScheme.secondary }
    val actualBackgroundColor = backgroundColor.takeOrElse { Color.Transparent }

    // Compose 才有的 Color -> ARGB 在调用点算好后传入，提取出的函数只收普通类型。
    val foregroundArgb = actualColor.toArgb()
    val backgroundArgb = actualBackgroundColor.toArgb()
    val bitmap = remember(value, size, foregroundArgb, backgroundArgb) {
        encodeQrBitmap(
            value = value,
            size = size,
            foregroundColor = foregroundArgb,
            backgroundColor = backgroundArgb,
        )
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "qrcode:$value",
        modifier = modifier
    )
}

/**
 * 把 [value] 编码成二维码位图。
 *
 * 从 [QRCode] 的 `@Composable` 体内**原样提取**：此前 `QRCodeWriter.encode(value,
 * BarcodeFormat.QR_CODE, size, size)` 与逐像素写 [Bitmap] 的逻辑内联在 Composable 里，
 * 仓库没有任何可直接调用的 `String -> Bitmap` 生产函数，导致「位图 → MLKit 解码」的
 * 往返无法在 androidTest 中独立执行。提取只为得到可调用入口，**编码调用与逐像素写法
 * 逐字未改**（同 `BarcodeFormat.QR_CODE`、同 `size x size`、同 `createBitmap` +
 * `this[x, y] = ...`）。
 *
 * @param foregroundColor 前景色 ARGB（bitMatrix 位为 1 的模块）。
 * @param backgroundColor 背景色 ARGB（bitMatrix 位为 0 的模块）。
 */
internal fun encodeQrBitmap(
    value: String,
    size: Int,
    foregroundColor: Int,
    backgroundColor: Int,
): Bitmap {
    val bitMatrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    return createBitmap(size, size).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                this[x, y] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
            }
        }
    }
}
