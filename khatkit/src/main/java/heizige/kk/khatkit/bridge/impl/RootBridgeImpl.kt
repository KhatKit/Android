package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.RootBridge
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * root bridge（L2）。桌面端映射为 sudo，默认禁用；Android 上只有显式开启才注入。
 */
class RootBridgeImpl : RootBridge {

    override fun shell(cmd: String): String {
        val process = ProcessBuilder("su", "-c", cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val finished = process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return "[timeout] 命令超时"
        }
        return if (process.exitValue() == 0) output else "[exit ${process.exitValue()}]\n$output"
    }

    companion object {
        private const val ROOT_TIMEOUT_SECONDS = 30L

        /** 主动探测 su；会阻塞，必须在 IO 线程调用。 */
        fun isAvailable(): Boolean = runCatching {
            val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrDefault(false)
    }
}
