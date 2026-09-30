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

    override fun openLogin(url: String, title: String, actions: List<Map<String, Any?>>): String {
        require(url.startsWith("http://") || url.startsWith("https://")) { "仅支持 http/https 登录地址" }
        val safeActions = if (actions.isEmpty()) listOf(
            mapOf("id" to "save_cookie", "event" to "save_cookie", "label" to "登录并保存 Cookie", "icon" to "key")
        ) else actions
        val result = ui?.webSheet(title, url, safeActions) ?: return "{\"error\":\"UI 不可用\"}"
        val event = result["event"]?.toString().orEmpty()
        val values = result["values"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val currentUrl = values["currentUrl"]?.toString().orEmpty().ifBlank { url }
        return when (event) {
            "save_cookie" -> saveCookie(currentUrl)
            "cookie_status" -> cookieStatus(currentUrl)
            "clear_cookie" -> if (clearCookie(currentUrl)) "{\"cleared\":true}" else "{\"cleared\":false}"
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

    private fun saveCookie(url: String): String {
        val host = key(url)
        val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
        if (cookie.isBlank()) return "{\"saved\":false,\"host\":\"${escape(host)}\",\"reason\":\"当前页面没有 Cookie\"}"
        store.set(host, cookie)
        return "{\"saved\":true,\"host\":\"${escape(host)}\",\"length\":${cookie.length}}"
    }

    private fun key(url: String): String = runCatching {
        URI(url).host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: throw IllegalArgumentException("无效 URL")

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
