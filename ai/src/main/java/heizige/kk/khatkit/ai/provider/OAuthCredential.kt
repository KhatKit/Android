package heizige.kk.khatkit.ai.provider

/**
 * 账号类提供商（ChatGPT / Grok 订阅）的 OAuth 凭据（上游 318cffef6）。
 *
 * 由 app 层的账号登录/刷新逻辑持有并写回，Provider 只管拿来用；
 * [toString] 会把 token 打码，避免整份凭据进日志。
 */
data class OAuthCredential(
    val accessToken: String,
    val refreshToken: String? = null,
    // access token 过期时间（毫秒时间戳），0 表示未知
    val expiresAt: Long = 0L,
    // 厂商侧的账号/工作区 ID，ChatGPT 请求需要带上
    val accountId: String? = null,
    // 以下仅用于界面展示
    val email: String? = null,
    val plan: String? = null,
) {
    override fun toString(): String =
        "OAuthCredential(accessToken=${accessToken.masked()}, refreshToken=${refreshToken?.masked()}, " +
            "expiresAt=$expiresAt, accountId=$accountId, email=$email, plan=$plan)"

    private fun String.masked(): String = if (length <= 8) "***" else "${take(4)}***${takeLast(4)}"
}
