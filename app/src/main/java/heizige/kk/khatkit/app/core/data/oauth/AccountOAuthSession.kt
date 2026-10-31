package heizige.kk.khatkit.app.core.data.oauth

import android.content.Context
import heizige.kk.khatkit.ai.provider.OAuthCredential
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.oauth.CustomTabsOAuthAuthorizationLauncher
import heizige.kk.khatkit.oauth.OAuthHttpClient
import heizige.kk.khatkit.oauth.OAuthLoopbackCallbackServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

/** 支持订阅账号登录的厂商（上游 318cffef6 的 UI 部分只认这两家）。 */
enum class AccountVendor(val label: String) {
    ChatGPT("ChatGPT"),
    Grok("Grok"),
}

/** 登录过程的状态，UI 只读它；真正的凭据在 [AccountOAuthSession.credential]。 */
sealed interface AccountLoginState {
    /** 还没登录过，也没有进行中的流程 */
    data object Idle : AccountLoginState

    /**
     * 等用户授权。[userCode] 只有 Grok 的设备码流程会有（打开 [verificationUri] 后输入）；
     * ChatGPT 走浏览器回调，[verificationUri] 是打开的授权地址。
     */
    data class Waiting(
        val userCode: String? = null,
        val verificationUri: String? = null,
    ) : AccountLoginState

    data class LoggedIn(val credential: OAuthCredential) : AccountLoginState

    data class Failed(val message: String) : AccountLoginState
}

/**
 * 额度条要的数据：窗口内已用 / 上限。
 *
 * ai 侧的 `ProviderQuota` / `QuotaQueryRunner` 本仓还没有（见报告），所以这里先把结构定死：
 * 拿到真实用量后只要填这个 data class，UI（账号区的进度条）不用改。
 */
data class AccountQuota(
    val used: Long,
    val limit: Long,
    val windowLabel: String,
) {
    val fraction: Float get() = if (limit <= 0L) 0f else (used.toFloat() / limit).coerceIn(0f, 1f)
}

/**
 * 订阅账号（ChatGPT / Grok）登录会话：协议参数来自 [ChatGPTAuth] / [GrokAuth]（提交 41339f2d5），
 * 联网走 oauth 模块的 [OAuthHttpClient]，浏览器回调走 [OAuthLoopbackCallbackServer]。
 *
 * 凭据落在本应用私有 SharedPreferences（[AccountCredentialStore]）里 —— ai 侧 `ProviderSetting`
 * 目前没有账号字段（AI-1 棒负责），等字段到位后把这个 store 换成 `ProviderSetting` 里的字段即可，
 * 本类的对外 API 不用变。
 *
 * 线程：所有 suspend 函数内部都是网络/IO，调用方用主线程 scope 直接调即可。
 */
class AccountOAuthSession(
    context: Context,
    httpClient: OkHttpClient,
) {
    private val appContext = context.applicationContext
    private val client = OAuthHttpClient(httpClient)
    private val store = AccountCredentialStore(appContext)

    private val _states = MutableStateFlow<Map<AccountVendor, AccountLoginState>>(
        AccountVendor.entries.associateWith { vendor ->
            store.load(vendor)?.let { AccountLoginState.LoggedIn(it) } ?: AccountLoginState.Idle
        }
    )

    /** 两家厂商各自的登录状态。 */
    val states: StateFlow<Map<AccountVendor, AccountLoginState>> = _states.asStateFlow()

    fun credential(vendor: AccountVendor): OAuthCredential? = store.load(vendor)

    /** ChatGPT 计划名（`chatgpt_plan_type` → "Plus"/"Pro"…）；没登录时为 null。 */
    fun planLabel(vendor: AccountVendor): String? {
        val plan = store.load(vendor)?.plan ?: return null
        return if (vendor == AccountVendor.ChatGPT) ChatGPTAuth.planDisplayName(plan) else plan
    }

    /**
     * ChatGPT 登录：PKCE + 本地回环回调（端口/路径按 Codex CLI 注册值原样，见 [ChatGPTAuth]）。
     * 打开浏览器后挂起等回调，最多 5 分钟。
     */
    suspend fun loginChatGpt(): Result<OAuthCredential> {
        publish(AccountVendor.ChatGPT, AccountLoginState.Waiting())
        return runCatching {
            val previous = store.load(AccountVendor.ChatGPT)
            val server = OAuthLoopbackCallbackServer(
                port = ChatGPTAuth.CALLBACK_PORT,
                callbackPath = ChatGPTAuth.CALLBACK_PATH,
                redirectHost = ChatGPTAuth.CALLBACK_HOST,
            )
            val pkce = client.generatePkce()
            val state = client.generateState()
            val session = server.openSession(appContext, state)
            try {
                val authorizationUrl = client.buildAuthorizationUrl(
                    ChatGPTAuth.authorizationRequest(pkce = pkce, state = state, redirectUri = session.redirectUri)
                )
                publish(
                    AccountVendor.ChatGPT,
                    AccountLoginState.Waiting(verificationUri = authorizationUrl),
                )
                CustomTabsOAuthAuthorizationLauncher.launch(appContext, authorizationUrl)

                val callback = session.awaitCallback(5.minutes)
                    ?: error("授权超时，请重新登录")
                callback.error?.let { error(callback.errorDescription ?: it) }
                val code = callback.code ?: error("授权回调里没有 code")

                val token = client.exchangeAuthorizationCode(
                    OAuthHttpClient.AuthorizationCodeTokenRequest(
                        tokenEndpoint = ChatGPTAuth.TOKEN_ENDPOINT,
                        clientId = ChatGPTAuth.CLIENT_ID,
                        code = code,
                        codeVerifier = pkce.verifier,
                        redirectUri = session.redirectUri,
                    )
                )
                val credential = ChatGPTAuth.toCredential(token, previous)
                store.save(AccountVendor.ChatGPT, credential)
                publish(AccountVendor.ChatGPT, AccountLoginState.LoggedIn(credential))
                credential
            } finally {
                session.close()
            }
        }.onFailure { publish(AccountVendor.ChatGPT, AccountLoginState.Failed(it.message ?: "登录失败")) }
    }

    /**
     * Grok 登录：RFC 8628 设备码。先拿到 user_code 让 UI 显示（[AccountLoginState.Waiting]），
     * 用户打开 verification_uri 确认后这里自动轮询到令牌。
     */
    suspend fun loginGrok(): Result<OAuthCredential> = runCatching {
        val previous = store.load(AccountVendor.Grok)
        val device = client.requestDeviceAuthorization(GrokAuth.deviceAuthorizationRequest())
        publish(
            AccountVendor.Grok,
            AccountLoginState.Waiting(
                userCode = device.userCode,
                verificationUri = device.verificationUriComplete ?: device.verificationUri,
            ),
        )
        val token = client.awaitDeviceToken(GrokAuth.deviceTokenRequest(device.deviceCode), device)
        val credential = GrokAuth.toCredential(token, previous)
        store.save(AccountVendor.Grok, credential)
        publish(AccountVendor.Grok, AccountLoginState.LoggedIn(credential))
        credential
    }.onFailure { publish(AccountVendor.Grok, AccountLoginState.Failed(it.message ?: "登录失败")) }

    /** 用 refresh_token 续期；没有 refresh_token 时返回失败，让 UI 提示重新登录。 */
    suspend fun refresh(vendor: AccountVendor): Result<OAuthCredential> = runCatching {
        val previous = store.load(vendor) ?: error("尚未登录")
        val refreshToken = previous.refreshToken ?: error("凭据里没有 refresh token，请重新登录")
        val request = when (vendor) {
            AccountVendor.ChatGPT -> ChatGPTAuth.refreshRequest(refreshToken)
            AccountVendor.Grok -> GrokAuth.refreshRequest(refreshToken)
        }
        val token = client.refreshToken(request)
        val credential = when (vendor) {
            AccountVendor.ChatGPT -> ChatGPTAuth.toCredential(token, previous)
            AccountVendor.Grok -> GrokAuth.toCredential(token, previous)
        }
        store.save(vendor, credential)
        publish(vendor, AccountLoginState.LoggedIn(credential))
        credential
    }.onFailure { publish(vendor, AccountLoginState.Failed(it.message ?: "续期失败")) }

    fun logout(vendor: AccountVendor) {
        store.clear(vendor)
        publish(vendor, AccountLoginState.Idle)
    }

    private fun publish(vendor: AccountVendor, state: AccountLoginState) {
        _states.update { it + (vendor to state) }
    }
}

/**
 * 凭据的落盘格式。`OAuthCredential`（ai 模块）不是 @Serializable，所以这里单独存一份 DTO，
 * 字段与它一一对应；以后 ai 侧加上序列化可以直接换掉，不改调用方。
 */
@Serializable
private data class StoredCredential(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long = 0L,
    val accountId: String? = null,
    val email: String? = null,
    val plan: String? = null,
)

private class AccountCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("oauth_accounts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun load(vendor: AccountVendor): OAuthCredential? =
        prefs.getString(key(vendor), null)?.let { raw ->
            runCatching { json.decodeFromString<StoredCredential>(raw) }.getOrNull()?.let { stored ->
                OAuthCredential(
                    accessToken = stored.accessToken,
                    refreshToken = stored.refreshToken,
                    expiresAt = stored.expiresAt,
                    accountId = stored.accountId,
                    email = stored.email,
                    plan = stored.plan,
                )
            }
        }

    fun save(vendor: AccountVendor, credential: OAuthCredential) {
        val stored = StoredCredential(
            accessToken = credential.accessToken,
            refreshToken = credential.refreshToken,
            expiresAt = credential.expiresAt,
            accountId = credential.accountId,
            email = credential.email,
            plan = credential.plan,
        )
        prefs.edit().putString(key(vendor), json.encodeToString(stored)).apply()
    }

    fun clear(vendor: AccountVendor) {
        prefs.edit().remove(key(vendor)).apply()
    }

    private fun key(vendor: AccountVendor): String = "account_${vendor.name.lowercase()}"
}
