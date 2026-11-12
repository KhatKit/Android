package heizige.kk.khatkit.app.core.data.ssh

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import heizige.kk.khatkit.ssh.api.SshEngine
import java.io.File

/**
 * 已安装远程 SSH 插件的动态加载器。
 *
 * DexClassLoader 的 dex 路径是 <files>/plugins/ssh/<version>/classes.dex；parent 用 app 的
 * classloader，让插件实现 [SshEngine]（基础包里的 SPI 接口）。sshj / BouncyCastle 都在
 * 插件的 classes.dex 里，基础 APK 不再打包它们。
 */
object SshPluginLoader {
    private const val TAG = "SshPluginLoader"
    @Volatile private var record: SshPluginRecord? = null
    @Volatile private var engine: SshEngine? = null
    @Volatile private var loadedVersion: String? = null

    /** 安装器写完记录后同步进来，避免每次加载都读 DataStore。 */
    fun updateRecord(value: SshPluginRecord?) {
        record = value
        if (value == null) invalidate()
    }

    /** 冷启动时从 DataStore 取一次记录（installer/UI 都可调用）。 */
    suspend fun prepare(context: Context) {
        if (record == null) updateRecord(SshPluginStore(context).read())
    }

    /** 当前可用的插件引擎；没有安装、dex 缺失或实例化失败都返回 null。 */
    fun engineOrNull(context: Context): SshEngine? {
        engine?.let { return it }
        val current = record ?: return null
        if (loadedVersion == current.version && engine != null) return engine
        return runCatching {
            val dir = SshPluginInstaller.versionDir(context, current.version)
            val dex = File(dir, "classes.dex")
            if (!dex.isFile) {
                Log.w(TAG, "classes.dex missing for " + current.version)
                return null
            }
            // 老版本安装器写出来的是可写 dex，Android 14+ 会拒绝加载；加载前修一次权限。
            if (dex.canWrite()) dex.setReadOnly()
            val nativeDir = SshPluginInstaller.nativeLibDir(context, current)
            val optimized = File(context.codeCacheDir, "ssh_plugins").apply { mkdirs() }
            val loader = DexClassLoader(
                dex.absolutePath,
                optimized.absolutePath,
                nativeDir?.absolutePath,
                context.classLoader,
            )
            val instance = loader.loadClass(current.entryClass).getDeclaredConstructor().newInstance()
            val resolved = instance as? SshEngine
            if (resolved == null) Log.w(TAG, "entry class is not an SshEngine: " + current.entryClass)
            resolved?.also {
                engine = it
                loadedVersion = current.version
            }
        }.onFailure { Log.w(TAG, "ssh plugin engine load failed", it) }.getOrNull()
    }

    /** 卸载 / 安装新版本后清缓存。 */
    fun invalidate() {
        engine = null
        loadedVersion = null
    }
}
