package heizige.kk.khatkit.bridge.impl

import android.content.pm.PackageManager
import heizige.kk.khatkit.bridge.ShizukuBridge
import rikka.shizuku.Shizuku

/**
 * shizuku bridge（L1）：只暴露动作级 API，不暴露通用 exec，脚本无法拼命令。
 *
 * 命令以参数数组交给 Shizuku.newProcess 直接 exec（不经 shell），参数由白名单校验，注入面为零。
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
