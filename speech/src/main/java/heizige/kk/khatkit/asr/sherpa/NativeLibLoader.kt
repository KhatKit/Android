package heizige.kk.khatkit.asr.sherpa

import java.io.File

/**
 * sherpa-onnx classes.jar 已被补丁：所有 `System.loadLibrary("sherpa-onnx-jni")`
 * 都会走这里。引擎 .so 由用户在设置页按需下载，存在时用绝对路径加载。
 */
object NativeLibLoader {
    @Volatile
    var libPath: String? = null

    @JvmStatic
    fun load(name: String) {
        val path = libPath
        if (path != null && File(path).isFile) {
            System.load(path)
        } else {
            System.loadLibrary(name)
        }
    }
}
