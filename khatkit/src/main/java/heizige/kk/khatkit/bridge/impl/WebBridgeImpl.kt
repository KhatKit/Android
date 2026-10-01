package heizige.kk.khatkit.bridge.impl

import android.content.Context
import android.webkit.CookieManager
import heizige.kk.khatkit.bridge.UiBridge
import heizige.kk.khatkit.bridge.WebBridge
import java.net.URI

/** WebView 登录桥：Cookie 只按 host 加密保存，不上传服务器。 */
internal class WebBridgeImpl(
    context: Context,
    private val ui: UiBridge?,
) : WebBridge {
    private val store = SecretStore(context.applicationContext, "__web_cookie_store")

    override fun openLogin(
        url: String,
        title: String,
        actions: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): String {
        require(url.startsWith("http://") || url.startsWith("https://")) { "仅支持 http/https 登录地址" }
        val safeActions = actions.orEmpty().ifEmpty { listOf(defaultSaveAction()) }
        val host = ui ?: return unavailable()
        // 用户关闭弹层时 webSheet 返回 null：明确回传取消态，脚本据此不启动下载。
        val result = host.webSheet(title, url, safeActions, options) ?: return cancelled()
        val event = result["event"]?.toString().orEmpty().ifBlank { return cancelled() }
        val values = result["values"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        // 顶栏按钮回传当前 WebView URL（登录后可能已跳到别的域名），只按它的 host 存 Cookie。
        val currentUrl = values["currentUrl"]?.toString().orEmpty().ifBlank { url }
        return when (event) {
            "save_cookie" -> saveCookie(currentUrl)
            "cookie_status" -> cookieStatus(currentUrl)
            "clear_cookie" -> if (clearCookie(currentUrl)) cleared() else notSaved(currentUrl, "没有已保存的 Cookie")
            else -> "{\"event\":\"${escape(event)}\"}"
        }
    }

    override fun savedCookie(url: String): String = store.get(key(url)).orEmpty()

    override fun cookieStatus(url: String): String {
        val host = key(url)
        val cookie = store.get(host).orEmpty()
        return "{\"host\":\"${escape(host)}\",\"saved\":${cookie.isNotBlank()},\"length\":${cookie.length}}"
    }

    override fun clearCookie(url: String): Boolean {
        val host = key(url)
        val existed = store.get(host) != null
        store.remove(host)
        return existed
    }

    /** 脚本没给 actions 时的兜底动作：右上角钥匙图标按钮，点击即保存当前页 Cookie。 */
    private fun defaultSaveAction(): Map<String, Any?> = mapOf(
        "id" to "save_cookie",
        "event" to "save_cookie",
        "label" to "登录并保存 Cookie",
        "icon" to "key",
        "placement" to "top",
    )

    private fun saveCookie(url: String): String {
        val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
        if (cookie.isBlank()) return notSaved(url, "当前页面没有 Cookie，请先在页面内完成登录")
        val host = key(url)
        store.set(host, cookie)
        // 只回传 host 与长度，绝不回传 Cookie 内容。
        return "{\"saved\":true,\"host\":\"${escape(host)}\",\"length\":${cookie.length}}"
    }

    private fun notSaved(url: String, reason: String): String {
        val host = runCatching { key(url) }.getOrDefault("")
        return "{\"saved\":false,\"host\":\"${escape(host)}\",\"reason\":\"${escape(reason)}\"}"
    }

    private fun cleared(): String = "{\"cleared\":true}"

    private fun unavailable(): String = "{\"error\":\"UI 不可用\",\"state\":\"login_unavailable\"}"

    private fun cancelled(): String = "{\"event\":\"cancelled\",\"cancelled\":true,\"state\":\"login_cancelled\"}"

    private fun key(url: String): String = runCatching {
        URI(url).host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: throw IllegalArgumentException("无效 URL")

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}