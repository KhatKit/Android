package heizige.kk.khatkit.ocr.api

import android.graphics.Bitmap

/**
 * OCR SPI（OCR1）：基础包只依赖这个接口，具体引擎由独立插件提供。
 * 类型刻意只用 Android/Java 与 Kotlin 标准库，插件模块可以脱离 app 单独编译。
 */
interface OcrEngine {
    /** 识别一张位图；实现方自行决定是本地模型还是远程服务。 */
    suspend fun recognize(bitmap: Bitmap): OcrResult
}

/** 一块识别结果（一行或一个文本块），坐标是位图像素。 */
data class OcrBlock(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

data class OcrResult(val text: String, val blocks: List<OcrBlock> = emptyList())

/**
 * 插件解析：基础包不打包任何 OCR 实现，启动后按类名尝试加载已安装插件提供的实现。
 *
 * 插件（plugins/ocr-mlkit）在 app 进程内可用时，其类名形如
 * heizige.kk.khatkit.ocr.mlkit.MlKitOcrEngine；未安装/未加载则返回 null，
 * 调用方据此降级为「需要安装 OCR 插件」提示（中英文），不抛异常。
 */
object OcrEngineProvider {
    /** 插件实现的类名；改插件包名时只改这里。 */
    private const val IMPL_CLASS = "heizige.kk.khatkit.ocr.mlkit.MlKitOcrEngine"

    private var resolved = false
    private var engine: OcrEngine? = null

    /** 当前可用的引擎；没有安装插件时为 null。解析结果会被缓存。 */
    fun engine(): OcrEngine? {
        if (resolved) return engine
        resolved = true
        engine = runCatching {
            val cls = Class.forName(IMPL_CLASS)
            val instance = cls.getDeclaredConstructor().newInstance()
            instance as? OcrEngine
        }.getOrNull()
        return engine
    }

    /** 是否已装 OCR 插件（界面据此决定是否显示「安装插件」入口）。 */
    fun isAvailable(): Boolean = engine() != null
}
