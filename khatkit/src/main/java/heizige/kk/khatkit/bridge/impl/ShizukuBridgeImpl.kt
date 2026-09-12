package heizige.kk.khatkit.bridge.impl

import android.content.Context
import android.content.pm.PackageManager
import heizige.kk.khatkit.bridge.ShizukuBridge
import rikka.shizuku.Shizuku

/**
 * shizuku bridge（L1）：只暴露动作级 API，不暴露通用 exec，脚本无法拼命令。
 *
 * 命令以参数数组直接 exec（不经 shell），并由白名单校验参数，注入面为零。
 */
class ShizukuBridgeImpl(private val context: Context) : ShizukuBridge {

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
        ShizukuUserServiceManager.exec(command)
    }.getOrElse { "[error] ${it.message}" }

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

        /** Shizuku 服务在线且已授权才挂这个 bridge，并预绑定 UserService。 */
        fun isAvailable(context: Context): Boolean = runCatching {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return false
            ShizukuUserServiceManager.ensureBound(context.applicationContext)
            true
        }.getOrDefault(false)

        fun requestPermission(requestCode: Int) = runCatching {
            if (!Shizuku.isPreV11() && Shizuku.shouldShowRequestPermissionRationale()) return@runCatching
            Shizuku.requestPermission(requestCode)
        }
    }
}
