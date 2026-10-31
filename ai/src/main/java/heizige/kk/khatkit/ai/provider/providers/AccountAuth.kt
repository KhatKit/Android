package heizige.kk.khatkit.ai.provider.providers

import heizige.kk.khatkit.ai.provider.AccountCredentialProvider
import heizige.kk.khatkit.ai.provider.CustomHeader
import heizige.kk.khatkit.ai.provider.OAuthCredential
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.util.mergeCustomHeaders
import heizige.kk.khatkit.common.http.okhttp.Authenticator
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khatkit.common.http.okhttp.Response
import heizige.kk.khatkit.common.http.okhttp.Route
import kotlinx.coroutines.runBlocking

/** 账号类请求在 Bearer token 之外要带的鉴权头，401 重试时用新凭据重新写一遍 */
internal typealias AccountHeaders = Request.Builder.(OAuthCredential) -> Unit

private class AccountRequestTag(val setting: ProviderSetting.Account, val applyHeaders: AccountHeaders)

/** 账号类提供商的 client：请求被 401 拒绝时刷新一次 token 再重试（移植自上游 318cffef6） */
internal fun OkHttpClient.withAccountAuthenticator(credentials: AccountCredentialProvider): OkHttpClient =
    newBuilder().authenticator(AccountTokenAuthenticator(credentials)).build()

/**
 * 给账号类请求加上鉴权：先放自定义头，鉴权头后写，保证不会被覆盖；
 * 再打上 tag，让 [AccountTokenAuthenticator] 认出这是账号类请求。
 */
internal suspend fun Request.Builder.configureAccountAuth(
    credentials: AccountCredentialProvider,
    setting: ProviderSetting.Account,
    customHeaders: List<CustomHeader>,
    applyHeaders: AccountHeaders,
) {
    val credential = credentials.getCredential(setting, rejectedAccessToken = null)
    headers(setting.mergeCustomHeaders(customHeaders))
    applyCredential(credential, applyHeaders)
    tag(AccountRequestTag::class.java, AccountRequestTag(setting, applyHeaders))
}

private fun Request.Builder.applyCredential(credential: OAuthCredential, applyHeaders: AccountHeaders) {
    header("Authorization", "Bearer " + credential.accessToken)
    applyHeaders(credential)
}

/** 覆盖流式、非流式、模型列表和额度查询请求 */
private class AccountTokenAuthenticator(
    private val credentials: AccountCredentialProvider,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val tag = response.request.tag(AccountRequestTag::class.java) ?: return null
        // 已经重试过一次还是 401，说明不是 token 过期的问题
        if (response.priorResponse != null) return null
        val rejected = response.request.header("Authorization")?.removePrefix("Bearer ")
        // 鉴权回调在请求线程上同步执行
        val credential = runCatching {
            runBlocking { credentials.getCredential(tag.setting, rejectedAccessToken = rejected) }
        }.getOrNull() ?: return null
        if (credential.accessToken == rejected) return null
        return response.request.newBuilder()
            .apply { applyCredential(credential, tag.applyHeaders) }
            .build()
    }
}
