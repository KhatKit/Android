package heizige.kk.khatkit.app.core.data.terminal

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File

object TerminalPluginLoader {
    @Volatile private var loadedVersion: String? = null

    fun isLoadable(context: Context, record: TerminalPluginRecord?): Boolean {
        if (record == null) return false
        if (loadedVersion == record.version) return true
        return runCatching {
            val dex = File(TerminalPluginInstaller.versionDir(context, record.version), "classes.dex")
            require(dex.isFile)
            if (dex.canWrite()) dex.setReadOnly()
            val loader = DexClassLoader(dex.absolutePath, context.codeCacheDir.absolutePath, TerminalPluginInstaller.nativeLibDir(context, record)?.absolutePath, context.classLoader)
            TerminalPluginInstaller.nativeLibDir(context, record)?.let { nativeDir ->
                val native = File(nativeDir, "libtermux.so")
                require(native.isFile)
                System.load(native.absolutePath)
            }
            loader.loadClass(record.entryClass).getDeclaredConstructor().newInstance()
            loadedVersion = record.version
            true
        }.onFailure { Log.w("TerminalPluginLoader", "terminal plugin load failed", it) }.getOrDefault(false)
    }

    fun invalidate() {
        loadedVersion = null
    }
}
