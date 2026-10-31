package heizige.kk.khatkit.app.core.data.oauth

import heizige.kk.khatkit.ai.provider.AccountCredentialProvider
import heizige.kk.khatkit.ai.provider.OAuthCredential
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.oauth.OAuthHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * OA-4：把 ai 的 [AccountCredentialProvider] 接到设置里存着的凭据上。
 *
 * 职责（接口注释里写明「实现方负责按需刷新并写回设置」）：
 *  1. 从**最新**设置里取该账号提供商的凭据（不是构造时那个快照）；
 *  2. 快过期、或服务端刚用 401 拒了同一个 token ⇒ 用 refresh_token 换新的；
 *  3. 换到的新凭据写回 [SettingsRepository]，其它请求/界面立刻能看到。
 *
 * 并发：一个进程一个互斥锁。并发的 401 只会产生一次刷新，后来者拿到新的 token 直接放行
 * （避免同一个 refresh_token 被并发消费导致整账号掉登录）。
 *
 * 未登录/刷新失败一律抛异常 —— [AccountCredentialProvider] 的契约如此，由 Provider 侧冒泡成请求错误。
 */
@Singleton
class ProviderAccountCredentialProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
    httpClient: OkHttpClient,
) : AccountCredentialProvider {
    private val client = OAuthHttpClient(httpClient)
    private val refreshMutex = Mutex()

    override suspend fun getCredential(
        setting: ProviderSetting.Account,
        rejectedAccessToken: String?,
    ): OAuthCredential = refreshMutex.withLock {
        // 拿到锁之后再读一次设置：可能已经被并发的请求刷新过了
        val current = latestAccount(setting.id) ?: setting
        val credential = current.credential ?: error("${vendorLabel(current)} 尚未登录")

        if (!needsRefresh(credential, rejectedAccessToken)) return credential

        val refreshToken = credential.refreshToken
            ?: error("${vendorLabel(current)} 登录已过期，请重新登录")

        val request = when (current) {
            is ProviderSetting.ChatGPT -> ChatGPTAuth.refreshRequest(refreshToken)
            is ProviderSetting.Grok -> GrokAuth.refreshRequest(refreshToken)
            else -> error("不支持的账号提供商类型：${current::class.simpleName}")
        }
        val token = client.refreshToken(request)
        val refreshed = when (current) {
            is ProviderSetting.ChatGPT -> ChatGPTAuth.toCredential(token, credential)
            is ProviderSetting.Grok -> GrokAuth.toCredential(token, credential)
            else -> error("不支持的账号提供商类型：${current::class.simpleName}")
        }
        writeBack(current.id, refreshed)
        refreshed
    }

    private fun latestAccount(id: Uuid): ProviderSetting.Account? =
        settingsRepository.settingsFlow.value.providers
            .filterIsInstance<ProviderSetting.Account>()
            .firstOrNull { it.id == id }

    /** 距过期不足 60 秒、或服务端刚拒掉的就是这个 token ⇒ 必须刷新。 */
    private fun needsRefresh(credential: OAuthCredential, rejectedAccessToken: String?): Boolean {
        if (rejectedAccessToken != null && rejectedAccessToken == credential.accessToken) return true
        if (credential.expiresAt <= 0L) return false
        return credential.expiresAt - System.currentTimeMillis() < EXPIRY_SKEW_MS
    }

    private suspend fun writeBack(id: Uuid, credential: OAuthCredential) {
        settingsRepository.update { settings ->
            settings.copy(
                providers = settings.providers.map { provider ->
                    if (provider.id == id && provider is ProviderSetting.Account) {
                        provider.withCredential(credential)
                    } else {
                        provider
                    }
                }
            )
        }
    }

    private fun vendorLabel(setting: ProviderSetting.Account): String = when (setting) {
        is ProviderSetting.ChatGPT -> "ChatGPT"
        is ProviderSetting.Grok -> "Grok"
        else -> setting.name
    }

    private companion object {
        const val EXPIRY_SKEW_MS = 60_000L
    }
}

/**
 * 登录成功 / 退出后把凭据写回设置（设置页用这个，避免各处各写一份 providers 映射）。
 * 返回写回后的完整设置，调用方一般直接忽略。
 */
suspend fun SettingsRepository.setAccountCredential(
    providerId: Uuid,
    credential: OAuthCredential?,
) = update { settings ->
    settings.copy(
        providers = settings.providers.map { provider ->
            if (provider.id == providerId && provider is ProviderSetting.Account) {
                provider.withCredential(credential)
            } else {
                provider
            }
        }
    )
}
