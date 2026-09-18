package heizige.kk.khatkit.bridge.impl

import android.content.pm.PackageManager
import heizige.kk.khatkit.bridge.ShizukuBridge
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * shizuku bridge（L1）：动作级 API 通过参数数组直接 exec（不经 shell），参数由白名单校验；
 * [shell] 是显式声明的通用命令入口（`/system/bin/sh -c`），高风险，仅 elevated 卡片可用。
 *
 * 远程进程由 Shizuku 13.x 的私有 `Shizuku.newProcess` 反射创建。
 */
class ShizukuBridgeImpl : ShizukuBridge {

    override fun setAppEnabled(pkg: String, enabled: Boolean): String =
        exec(arrayOf("pm", if (enabled) "enable" else "disable", "--user", "0", requirePackage(pkg)))

    override fun settingsPut(namespace: String, key: String, value: String): String {
        require(namespace in NAMESPACES) { "不支持的 settings namespace：$namespace" }
        require(KEY_REGEX.matches(key)) { "非法的 settings key：$key" }
        return exec(arrayOf("settings", "put", namespace, key, value))
    }

    override fun pm(action: String, pkg: String): String {
        require(action in PM_ACTIONS) { "不支持的 pm 动作：$action" }
        return exec(arrayOf("pm", action, requirePackage(pkg)))
    }

    /**
     * 以 shell 身份执行通用命令，返回合并后的 stdout/stderr 与退出码。
     * 命令经 `/system/bin/sh -c`，支持管道/重定向；仅限 elevated 卡片。
     */
    override fun shell(cmd: String): String = runCatching {
        require(cmd.isNotBlank()) { "命令不能为空" }
        val process = newRemoteProcess(arrayOf(SHELL_PATH, "-c", cmd))
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutReader = Thread {
            runCatching { process.inputStream.bufferedReader().use { stdout.append(it.readText()) } }
        }
        val stderrReader = Thread {
            runCatching { process.errorStream.bufferedReader().use { stderr.append(it.readText()) } }
        }
        stdoutReader.start()
        stderrReader.start()
        val finished = process.waitFor(SHELL_TIMEOUT_SEC, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            return@runCatching "[error] 命令超时（${SHELL_TIMEOUT_SEC}s）"
        }
        stdoutReader.join(JOIN_TIMEOUT_MS)
        stderrReader.join(JOIN_TIMEOUT_MS)
        buildString {
            append("[exit ").append(process.exitValue()).append("]\n")
            if (stdout.isNotEmpty()) append(stdout)
            if (stderr.isNotEmpty()) {
                if (stdout.isNotEmpty() && !stdout.endsWith("\n")) append('\n')
                append(stderr)
            }
        }
    }.getOrElse { "[error] Shizuku shell 执行失败：${it.message ?: it.javaClass.simpleName}" }

    /**
     * 执行 engine:command 卡片渲染后的命令。
     * CardExecutor 已对 `{}` 占位符做白名单转义（值不含空白），这里按空白切分后直接 exec。
     */
    fun runCommand(command: String): String {
        val tokens = command.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        require(tokens.isNotEmpty()) { "空命令" }
        return exec(tokens.toTypedArray())
    }

    private fun exec(command: Array<String>): String = runCatching {
        val process = newRemoteProcess(command)
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        when {
            code != 0 -> "[exit $code] ${stderr.ifBlank { stdout }}"
            stdout.isBlank() -> "[ok]"
            else -> stdout
        }
    }.getOrElse { "[error] ${it.message}" }

    /**
     * Shizuku 13.x 未公开 newProcess，只能反射调用其私有入口；
     * 免去 UserService + AIDL 的跨进程样板。
     */
    private fun newRemoteProcess(command: Array<String>): Process {
        val method = newProcessMethod ?: error("当前 Shizuku 版本不支持 newProcess")
        return method.invoke(null, command, null, null) as Process
    }

    private fun requirePackage(pkg: String): String {
        require(PACKAGE_REGEX.matches(pkg)) { "非法包名：$pkg" }
        return pkg
    }

    companion object {
        private const val SHELL_PATH = "/system/bin/sh"
        private const val SHELL_TIMEOUT_SEC = 30L
        private const val JOIN_TIMEOUT_MS = 3_000L

        private val PACKAGE_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
        private val KEY_REGEX = Regex("^[A-Za-z0-9_.:-]{1,128}$")
        private val NAMESPACES = setOf("system", "secure", "global")
        private val PM_ACTIONS = setOf(
            "disable", "enable", "disable-user", "clear", "uninstall",
            "install-existing", "grant", "revoke", "path", "suspend", "unsuspend",
        )

        private val newProcessMethod by lazy {
            runCatching {
                Shizuku::class.java
                    .getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java,
                    )
                    .apply { isAccessible = true }
            }.getOrNull()
        }

        /** Shizuku 服务在线且已授权才挂这个 bridge。 */
        fun isAvailable(): Boolean = runCatching {
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        fun requestPermission(requestCode: Int) = runCatching {
            if (!Shizuku.isPreV11() && Shizuku.shouldShowRequestPermissionRationale()) return@runCatching
            Shizuku.requestPermission(requestCode)
        }
    }
}
