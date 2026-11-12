package heizige.kk.khatkit.ssh.api

import android.content.Context

/**
 * 远程 SSH 插件 SPI。
 *
 * 基础包只依赖这个接口与 [SshPluginInfo] 常量；真正的实现（sshj 客户端）由
 * `plugins/ssh` 模块打成可下载的 dependency 插件包提供，安装后经 DexClassLoader 注入。
 */
interface SshEngine {
    /** 在远程主机上执行一条命令；连接失败/超时抛出异常，由调用方展示中文错误。 */
    fun execute(context: Context, request: SshRequest, timeoutMillis: Long = 30_000L): SshResult
}

/** 一次 SSH 执行请求；密码只在内存中，不落盘、不写日志。 */
data class SshRequest(
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String,
    val command: String,
)

data class SshResult(
    val exitCode: Int,
    val output: String,
)

/** 插件身份常量：宿主 UI、安装器、市场包 manifest 都以这里为准。 */
object SshPluginInfo {
    /** 扩展页里的插件 id（也是路由 Screen.Plugin 的参数）。 */
    const val ID = "remote-ssh"

    /** 市场条目名（/api/items?kind=dependency 的 name）。 */
    const val MARKET_NAME = "remote-ssh"

    /** 插件包 manifest 的 plugin_id（安装器校验用）。 */
    const val PACKAGE_ID = "ssh"

    const val VERSION = "1.0.1"
    const val DISPLAY_NAME = "远程 SSH"
    const val DESCRIPTION = "连接远程 Linux 主机并执行命令"

    /** 插件实现类名；改包名时只改这里（打包脚本里的 entry_class 必须一致）。 */
    const val ENTRY_CLASS = "heizige.kk.khatkit.plugins.ssh.SshEngineImpl"
}

/**
 * 插件解析：基础包不打包 SSH 实现，按类名尝试加载已安装插件提供的实现。
 *
 * 解析顺序：app 注入的 resolver（DexClassLoader 动态插件，每次调用都问）→ classpath 反射 → null。
 * 未安装插件时返回 null，调用方据此展示「需要安装插件」，不抛异常。
 */
object SshEngineProvider {
    private var resolved = false
    private var engine: SshEngine? = null

    private var appContext: Context? = null
    private var resolver: ((Context) -> SshEngine?)? = null

    /** 注入 app 侧的动态插件解析器；传 null 取消注入（退回 classpath 反射）。 */
    fun installResolver(context: Context?, resolver: ((Context) -> SshEngine?)?) {
        appContext = context?.applicationContext
        this.resolver = resolver
        resolved = false
        engine = null
    }

    fun engine(): SshEngine? {
        val ctx = appContext
        val injected = resolver
        if (ctx != null && injected != null) {
            injected(ctx)?.let { return it }
        }
        if (resolved) return engine
        resolved = true
        engine = runCatching {
            val cls = Class.forName(SshPluginInfo.ENTRY_CLASS)
            cls.getDeclaredConstructor().newInstance() as? SshEngine
        }.getOrNull()
        return engine
    }

    /** 已安装插件（界面据此决定是否显示「安装」入口）。 */
    fun isAvailable(): Boolean = engine() != null
}
