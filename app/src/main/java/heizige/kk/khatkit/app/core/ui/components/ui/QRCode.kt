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
import com.google.zxing.EncodeHintType
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
 * 往返无法在 androidTest 中独立执行。提取只为得到可调用入口；逐像素写法逐字未改
 * （同 `BarcodeFormat.QR_CODE`、同 `size x size`、同 `createBitmap` + `this[x, y] = ...`）。
 *
 * ## 字符集：显式 UTF-8（修复非 Latin-1 内容被有损替换成 `?`）
 *
 * 不带 hints 的 4 参 `encode` 在 zxing 3.5.4 下默认按 **ISO-8859-1**
 * （`Encoder.DEFAULT_BYTE_MODE_ENCODING`）取字节，中文 / emoji 等非 Latin-1 字符会被替换成
 * `?`，二维码往返不闭环（纯 JVM 探针实测：`{"persona":"热血解说"}` → `{"persona":"????"}`）。
 * 这里改用 5 参重载并显式给 `EncodeHintType.CHARACTER_SET = "UTF-8"`。
 *
 * ⚠️ **副作用（实测）**：字符集非 ISO-8859-1 时 zxing 会在字节模式载荷前写入 **ECI 头**
 * （12 bit = 4 bit ECI 模式指示 + 8 bit ECI 设计值 26），因此 QR v40-L 的字节模式有效容量
 * 从 **2953** 降到 **2952** 字节（`Encoder` 源码路径 `appendECI`，经 `QRCodeWriter.encode`
 * 二分实测：不带 hint `"a".repeat(2953)` 成功、带 UTF-8 hint `2953` 抛 `WriterException`、
 * `2952` 成功且为 v40/185 模块）。
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
    val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
    val bitMatrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints)
    return createBitmap(size, size).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                this[x, y] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
            }
        }
    }
}
