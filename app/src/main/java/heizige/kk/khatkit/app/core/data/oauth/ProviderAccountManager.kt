package heizige.kk.khatkit.app.core.data.oauth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import heizige.kk.khatkit.ai.provider.OAuthCredential
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 账号态（OA-3，上游 318cffef6 的 ProviderAccountManager 里界面需要的那部分）。
 *
 * 凭据本体不在这里：它是 [ProviderSetting.ChatGPT.credential] / [ProviderSetting.Grok.credential]，
 * 存在设置里、由 [ProviderAccountCredentialProvider] 在请求前刷新。这里只做「界面能显示什么」。
 */
data class ProviderAccountState(
    val loggedIn: Boolean = false,
    val email: String? = null,
    /** 已经映射成显示名的套餐（ChatGPT 用 [ChatGPTAuth.planDisplayName]） */
    val plan: String? = null,
    /** access token 过期时间（毫秒时间戳），0 表示未知 */
    val expiresAt: Long = 0L,
    /** Grok 设备码流程里要用户输入/打开的验证地址 */
    val userCode: String? = null,
    val verificationUri: String? = null,
    /** 上一次登录/续期的失败原因 */
    val error: String? = null,
) {
    val isExpired: Boolean
        get() = expiresAt > 0L && expiresAt <= System.currentTimeMillis()
}

/**
 * 订阅账号（ChatGPT / Grok）的登录与凭据写回（OA-3）。
 *
 * 职责三分工：
 * - 登录/续期的网络流程在 [AccountOAuthSession]（oauth 模块 + [ChatGPTAuth]/[GrokAuth]）；
 * - 请求前按需刷新 + 写回在 [ProviderAccountCredentialProvider]（接进 ProviderManager，401 能自动续期）；
 * - 本类是唯一写入口：登录成功、登出、手动续期都要经过这里，写回 Settings.providers 里对应
 *   [ProviderSetting.Account] 的 credential 字段，于是设置流、Provider 请求、界面三处是同一份凭据。
 *
 * @param context 只用于给 [AccountOAuthSession] 落一份会话缓存（SharedPreferences），设置才是权威。
 */
@Singleton
class ProviderAccountManager @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
    httpClient: OkHttpClient,
) {
    private val session = AccountOAuthSession(context, httpClient)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _accounts = MutableStateFlow<Map<AccountVendor, ProviderAccountState>>(emptyMap())

    /** 两家厂商的账号态，UI 直接 collect。 */
    val accounts: StateFlow<Map<AccountVendor, ProviderAccountState>> = _accounts.asStateFlow()

    init {
        // 设置流是凭据的唯一真相：登录/登出/刷新写回后这里自动跟着变
        scope.launch { settingsRepository.settingsFlow.collect { refreshStates() } }
        // 进行中的登录 / 失败原因来自会话
        scope.launch { session.states.collect { refreshStates() } }
        // 启动时把已过期或快过期的账号续期一次，免得第一次请求先吃 401
        scope.launch {
            AccountVendor.entries.forEach { vendor ->
                runCatching { refreshIfNeeded(vendor) }
            }
        }
    }

    /** 设置里该厂商的账号提供商（用户在提供商页添加过的那个）；没有就是 null。 */
    fun accountSetting(vendor: AccountVendor): ProviderSetting.Account? =
        settingsRepository.settingsFlow.value.providers
            .filterIsInstance<ProviderSetting.Account>()
            .firstOrNull { it.vendor() == vendor }

    /** 当前凭据（含 accountId / plan），没登录时为 null。 */
    fun credential(vendor: AccountVendor): OAuthCredential? = accountSetting(vendor)?.credential

    /** 套餐显示名：ChatGPT 走 planDisplayName 映射，Grok 原样。 */
    fun planLabel(vendor: AccountVendor): String? {
        val plan = credential(vendor)?.plan ?: return null
        return if (vendor == AccountVendor.ChatGPT) ChatGPTAuth.planDisplayName(plan) else plan
    }

    /**
     * 登录并把凭据写回设置。设置里还没有对应账号提供商时返回失败 —— 先让用户在提供商页添加
     * ChatGPT / Grok 账号（否则没有可写的目标）。
     */
    suspend fun login(vendor: AccountVendor): Result<OAuthCredential> = withContext(Dispatchers.IO) {
        val target = accountSetting(vendor)
        if (target == null) {
            return@withContext Result.failure(
                IllegalStateException("请先在提供商里添加 " + vendor.label + " 账号再登录")
            )
        }
        val result = when (vendor) {
            AccountVendor.ChatGPT -> session.loginChatGpt()
            AccountVendor.Grok -> session.loginGrok()
        }
        result.mapCatching { credential ->
            settingsRepository.setAccountCredential(target.id, credential)
            refreshStates()
            credential
        }
    }

    /** 登出：清掉设置里的凭据（同时清会话缓存）。 */
    suspend fun logout(vendor: AccountVendor) = withContext(Dispatchers.IO) {
        session.logout(vendor)
        accountSetting(vendor)?.let { settingsRepository.setAccountCredential(it.id, null) }
        refreshStates()
    }

    /** 手动续期：拿 refresh_token 换新凭据并写回；失败时账号态带上原因。 */
    suspend fun refresh(vendor: AccountVendor): Result<OAuthCredential> = withContext(Dispatchers.IO) {
        val target = accountSetting(vendor)
        if (target == null) {
            return@withContext Result.failure(
                IllegalStateException("设置里没有 " + vendor.label + " 账号提供商")
            )
        }
        val result = session.refresh(vendor).mapCatching { credential ->
            settingsRepository.setAccountCredential(target.id, credential)
            credential
        }
        refreshStates()
        result
    }

    /** 快过期（或已过期）才续期；返回 null 表示不需要动。 */
    suspend fun refreshIfNeeded(vendor: AccountVendor): Result<OAuthCredential>? {
        val credential = credential(vendor) ?: return null
        if (credential.expiresAt <= 0L) return null
        if (credential.expiresAt - System.currentTimeMillis() > REFRESH_SKEW_MS) return null
        if (credential.refreshToken.isNullOrBlank()) return null
        return refresh(vendor)
    }

    private fun refreshStates() {
        _accounts.value = AccountVendor.entries.associateWith { vendor -> buildState(vendor) }
    }

    private fun buildState(vendor: AccountVendor): ProviderAccountState {
        val credential = credential(vendor)
        val sessionState = session.states.value[vendor]
        return ProviderAccountState(
            loggedIn = credential != null,
            email = credential?.email,
            plan = planLabel(vendor),
            expiresAt = credential?.expiresAt ?: 0L,
            userCode = (sessionState as? AccountLoginState.Waiting)?.userCode,
            verificationUri = (sessionState as? AccountLoginState.Waiting)?.verificationUri,
            error = (sessionState as? AccountLoginState.Failed)?.message,
        )
    }

    /** 账号提供商属于哪家厂商。 */
    private fun ProviderSetting.Account.vendor(): AccountVendor? = when (this) {
        is ProviderSetting.ChatGPT -> AccountVendor.ChatGPT
        is ProviderSetting.Grok -> AccountVendor.Grok
        else -> null
    }

    private companion object {
        /** 距过期不足 5 分钟就提前续期 */
        const val REFRESH_SKEW_MS = 5 * 60 * 1000L
    }
}
