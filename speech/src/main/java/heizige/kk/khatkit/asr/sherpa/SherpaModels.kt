package heizige.kk.khatkit.asr.sherpa

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File

/** 可下载的 sherpa-onnx 本地识别模型。 */
data class SherpaModelPreset(
    val id: String,
    val displayName: String,
    /** paraformer / whisper / transducer / sense_voice */
    val type: String,
    val sizeBytes: Long,
    val url: String,
    val defaultLanguage: String = "auto",
    /** true = 流式（边说边出字，走 OnlineRecognizer）；false = 录完再识别 */
    val streaming: Boolean = false,
)

object SherpaModels {
    private const val BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

    /** 本地识别引擎（原生库）下载地址：官方 static-link AAR，设备端只提取 arm64/对应 ABI 的 .so。 */
    const val ENGINE_VERSION = "1.13.8"
    const val ENGINE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"
    const val ENGINE_SIZE_BYTES = 36L * 1024 * 1024

    val PRESETS = listOf(
        SherpaModelPreset(
            id = "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23",
            displayName = "中文 · 流式 Zipformer 14M",
            type = "transducer",
            streaming = true,
            sizeBytes = 73_400_320,
            url = "$BASE/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2",
        ),
        SherpaModelPreset(
            id = "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17",
            displayName = "英文 · 流式 Zipformer 20M",
            type = "transducer",
            streaming = true,
            sizeBytes = 126_877_696,
            url = "$BASE/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2",
        ),
        SherpaModelPreset(
            id = "sherpa-onnx-paraformer-zh-small-2024-03-09",
            displayName = "中文 · Paraformer Small",
            type = "paraformer",
            sizeBytes = 77_920_048,
            url = "$BASE/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2",
        ),
        SherpaModelPreset(
            id = "sherpa-onnx-whisper-tiny",
            displayName = "多语言 · Whisper Tiny",
            type = "whisper",
            sizeBytes = 115_343_360,
            url = "$BASE/sherpa-onnx-whisper-tiny.tar.bz2",
        ),
        SherpaModelPreset(
            id = "sherpa-onnx-whisper-tiny.en",
            displayName = "英文 · Whisper Tiny",
            type = "whisper",
            sizeBytes = 123_731_968,
            url = "$BASE/sherpa-onnx-whisper-tiny.en.tar.bz2",
        ),
    )

    fun find(id: String): SherpaModelPreset? = PRESETS.firstOrNull { it.id == id }
}

/** 模型下载 / 解压 / 删除，落在 filesDir/sherpa-models/<id>。 */
class SherpaModelManager(private val context: Context) {

    val rootDir: File get() = File(context.filesDir, "sherpa-models")

    fun modelDir(id: String): File = File(rootDir, id)

    fun isDownloaded(id: String): Boolean {
        val dir = modelDir(id)
        if (!dir.isDirectory) return false
        val hasOnnx = dir.walkTopDown().any { it.isFile && it.name.endsWith(".onnx") }
        val hasTokens = dir.walkTopDown().any { it.isFile && it.name == "tokens.txt" }
        return hasOnnx && hasTokens
    }

    fun delete(id: String) {
        modelDir(id).deleteRecursively()
    }

    // ---- 引擎（原生库）按需下载 ----

    val engineDir: File get() = File(context.filesDir, "sherpa-engine")

    val engineLib: File get() = File(engineDir, "libsherpa-onnx-jni.so")

    fun isEngineReady(): Boolean = engineLib.isFile && engineLib.length() > 0

    fun deleteEngine() {
        engineDir.deleteRecursively()
    }

    suspend fun installEngineArchive(archive: File): File = withContext(Dispatchers.IO) {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull {
            it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        } ?: "arm64-v8a"
        engineDir.mkdirs()
        var extracted = false
        java.util.zip.ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "jni/$abi/libsherpa-onnx-jni.so") {
                    engineLib.outputStream().use { zip.copyTo(it) }
                    extracted = true
                }
                entry = zip.nextEntry
            }
        }
        require(extracted) { "引擎包中未找到 $abi 原生库" }
        engineLib.setExecutable(true)
        engineLib
    }

    suspend fun installModelArchive(preset: SherpaModelPreset, archive: File): File = withContext(Dispatchers.IO) {
        val target = modelDir(preset.id)
        target.deleteRecursively()
        target.mkdirs()
        BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())).use { bz ->
            TarArchiveInputStream(bz).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("./")
                    val relative = if ('/' in name) name.substringAfter('/') else name
                    if (!entry.isDirectory && relative.isNotBlank() && !relative.startsWith(".")) {
                        val out = File(target, relative)
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                    }
                    entry = tar.nextEntry
                }
            }
        }
        target
    }

}
