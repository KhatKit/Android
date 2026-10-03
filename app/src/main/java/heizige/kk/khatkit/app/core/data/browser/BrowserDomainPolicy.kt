package heizige.kk.khatkit.app.core.data.browser

import java.net.URI
import java.net.IDN
import java.util.Locale

/** Same host/subdomain convention as the card network allow-list. */
class BrowserDomainPolicy(domains: Set<String>) {
    val domains: Set<String> = domains.map(::normalizeDomain).toSet()

    fun check(url: String) {
        BrowserSessionManager.validateBrowserUrl(url)
        if (url == "about:blank") return
        val host = URI(url).host?.lowercase(Locale.ROOT)?.trimEnd('.')
            ?: error("Browser URL requires a host")
        require(domains.any { it == "*" || host == it || host.endsWith(".$it") }) {
            "Browser domain is not allowed: $host"
        }
    }

    companion object {
        fun parse(text: String): Set<String> = text.split(Regex("[,，\\s]+"))
            .filter { it.isNotBlank() }.map(::normalizeDomain).toSet()

        private fun normalizeDomain(value: String): String {
            val domain = value.trim().trimEnd('.').lowercase(Locale.ROOT)
            if (domain == "*") return domain
            require(domain.isNotEmpty() && domain.none { it in "/:@?#\\*" }) {
                "请输入域名（例如 example.com），不要填写网址、端口或通配符；全部允许请填 *"
            }
            val ascii = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES)
            require(ascii.length <= 253 && ascii.split('.').all { it.isNotEmpty() && it.length <= 63 }) {
                "无效域名：$value"
            }
            return ascii
        }
    }
}
