package heizige.kk.khatkit.app.core.data.ocr

import android.content.Context
import dalvik.system.DexClassLoader
import heizige.kk.khatkit.ocr.api.OcrEngine
import java.io.File

/**
 * OCR2：已安装插件的动态加载器。
 *
 * DexClassLoader 的 dex 路径是 <files>/plugins/ocr/<version>/classes.dex，
 * librarySearchPath 指向插件包里当前 ABI 的 lib 目录（解压后的 lib/<abi>），
 * 这样插件里的 .so 能按 System.load 的常规路径被找到；parent 用 app 的 classloader，
 * 让插件实现 heizige.kk.khatkit.ocr.api.OcrEngine（基础包里的 SPI 接口）。
 * 实例化失败（未安装 / 损坏 / ABI 不匹配）返回 null，调用方按「需要安装 OCR 插件」降级。
 */
object OcrPluginLoader {
    @Volatile private var record: OcrPluginRecord? = null
    @Volatile private var engine: OcrEngine? = null
    @Volatile private var loadedVersion: String? = null

    /** 安装器写完记录后同步进来，避免每次加载都读 DataStore。 */
    fun updateRecord(value: OcrPluginRecord?) {
        record = value
        if (value == null) invalidate()
    }

    /** 冷启动时从 DataStore 取一次记录（installer/UI 都可调用）。 */
    suspend fun prepare(context: Context) {
        if (record == null) updateRecord(OcrPluginStore(context).read())
    }

    /** 当前可用的插件引擎；没有安装、dex/so 缺失或实例化失败都返回 null。 */
    fun engineOrNull(context: Context): OcrEngine? {
        engine?.let { return it }
        val current = record ?: return null
        if (loadedVersion == current.version && engine != null) return engine
        return runCatching {
            val dir = OcrPluginInstaller.versionDir(context, current.version)
            val dex = File(dir, "classes.dex")
            if (!dex.isFile) return null
            // Android 14+ 拒绝加载可写的 dex；老安装器留下的可写文件加载前修一次权限。
            if (dex.canWrite()) dex.setReadOnly()
            val nativeDir = OcrPluginInstaller.nativeLibDir(context, current)
            val optimized = File(context.codeCacheDir, "ocr_plugins").apply { mkdirs() }
            val loader = DexClassLoader(
                dex.absolutePath,
                optimized.absolutePath,
                nativeDir?.absolutePath,
                context.classLoader,
            )
            val instance = loader.loadClass(current.entryClass).getDeclaredConstructor().newInstance()
            (instance as? OcrEngine)?.also {
                engine = it
                loadedVersion = current.version
            }
        }.getOrNull()
    }

    /** 卸载 / 安装新版本后清缓存。 */
    fun invalidate() {
        engine = null
        loadedVersion = null
    }
}
