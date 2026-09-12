package heizige.kk.khatkit.bridge.impl

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 在 Shizuku（shell uid）进程里执行动作级命令的 UserService。
 *
 * 只接受参数数组、直接 exec（不经 shell），注入面为零。
 */
class KhatKitUserService : Service() {

    private val binder = object : IKhatKitUserService.Stub() {
        override fun exec(command: Array<out String>?): String {
            val args = command?.toList().orEmpty()
            if (args.isEmpty()) return "[error] empty command"
            return runCatching {
                val process = ProcessBuilder(args).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                if (exitCode == 0) output else "[exit $exitCode]\n$output"
            }.getOrElse { "[error] ${it.message}" }
        }

        override fun destroy() {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
