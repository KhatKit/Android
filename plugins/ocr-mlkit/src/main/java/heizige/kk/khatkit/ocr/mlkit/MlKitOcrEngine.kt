package heizige.kk.khatkit.ocr.mlkit

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import heizige.kk.khatkit.ocr.api.OcrBlock
import heizige.kk.khatkit.ocr.api.OcrEngine
import heizige.kk.khatkit.ocr.api.OcrResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR1：从 khatkit 模块的 AndroidToolBridge 迁出的 ML Kit 实现。
 * 类名必须与 OcrEngineProvider.IMPL_CLASS 一致（host 靠反射加载）。
 */
class MlKitOcrEngine : OcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text -> cont.resume(text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
        val blocks = buildList {
            result.textBlocks.forEach { block ->
                val lines = block.lines
                if (lines.isEmpty()) {
                    block.boundingBox?.let { b ->
                        add(OcrBlock(block.text, b.left, b.top, b.right, b.bottom))
                    }
                } else {
                    lines.forEach { line ->
                        line.boundingBox?.let { b ->
                            add(OcrBlock(line.text, b.left, b.top, b.right, b.bottom))
                        }
                    }
                }
            }
        }
        return OcrResult(result.text, blocks)
    }
}
