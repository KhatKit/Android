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

    /**
     * OCR2b：应用启动时注入的动态插件解析器（app 侧传 OcrPluginLoader.engineOrNull）。
     * 解析器每次 engine() 都会被调用 —— 安装器/卸载时会清加载器缓存，这里不做二次缓存，
     * 否则卸载后仍会拿到旧引擎。context 在注入时固定为 application context。
     */
    private var appContext: android.content.Context? = null
    private var resolver: ((android.content.Context) -> OcrEngine?)? = null

    /** 注入解析器；传 null 取消注入（回到只用 [IMPL_CLASS] 反射的回退路径）。 */
    fun installResolver(context: android.content.Context?, resolver: ((android.content.Context) -> OcrEngine?)?) {
        appContext = context?.applicationContext
        this.resolver = resolver
        resolved = false
        engine = null
    }

    /**
     * 当前可用的引擎；没有安装插件时为 null。
     *
     * 解析顺序：注入的 resolver（动态插件，每次调用都问）→ 缓存的 classpath 反射结果 → null。
     */
    fun engine(): OcrEngine? {
        val ctx = appContext
        val injected = resolver
        if (ctx != null && injected != null) {
            injected(ctx)?.let { return it }
        }
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
