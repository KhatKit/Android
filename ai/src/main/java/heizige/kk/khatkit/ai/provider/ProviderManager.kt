package heizige.kk.khatkit.ai.provider

import android.content.Context
import heizige.kk.khatkit.ai.provider.providers.chatgpt.ChatGPTProvider
import heizige.kk.khatkit.ai.provider.providers.claude.ClaudeProvider
import heizige.kk.khatkit.ai.provider.providers.google.GoogleProvider
import heizige.kk.khatkit.ai.provider.providers.openai.OpenAIProvider
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient

/**
 * Provider管理器，负责注册和获取Provider实例
 */
class ProviderManager(
    client: OkHttpClient,
    context: Context,
    /**
     * 账号类提供商（ChatGPT/Grok）的凭据来源；null 表示账号类型尚未接线。
     * 由 app 层注入，ai 层只消费。
     */
    private val accountCredentials: AccountCredentialProvider? = null,
) {
    // 存储已注册的Provider实例
    private val providers = mutableMapOf<String, Provider<*>>()

    init {
        // 注册默认Provider
        registerProvider("openai", OpenAIProvider(client, context))
        registerProvider("google", GoogleProvider(client, context))
        registerProvider("claude", ClaudeProvider(client, context))
        // 账号类提供商需要凭据来源；app 层未接线前不注册（getProviderByType 调用会抛 Provider not found）
        accountCredentials?.let { registerProvider("chatgpt", ChatGPTProvider(client, it)) }
    }

    /**
     * 注册Provider实例
     *
     * @param name Provider名称
     * @param provider Provider实例
     */
    fun registerProvider(name: String, provider: Provider<*>) {
        providers[name] = provider
    }

    /**
     * 获取Provider实例
     *
     * @param name Provider名称
     * @return Provider实例，如果不存在则返回null
     */
    fun getProvider(name: String): Provider<*> {
        return providers[name] ?: throw IllegalArgumentException("Provider not found: $name")
    }

    /**
     * 根据ProviderSetting获取对应的Provider实例
     *
     * @param setting Provider设置
     * @return Provider实例，如果不存在则返回null
     */
    fun <T : ProviderSetting> getProviderByType(setting: T): Provider<T> {
        @Suppress("UNCHECKED_CAST")
        return when (setting) {
            is ProviderSetting.OpenAI -> getProvider("openai")
            is ProviderSetting.Google -> getProvider("google")
            is ProviderSetting.Claude -> getProvider("claude")
            // 账号类提供商由 OA-2b/OA-2c 注册；未注册前调用会抛 Provider not found
            is ProviderSetting.ChatGPT -> getProvider("chatgpt")
            is ProviderSetting.Grok -> getProvider("grok")
            // 中间抽象类未封闭（ApiKey/Account 非 sealed），编译器无法判定穷尽；兜底报错。
            else -> throw IllegalArgumentException(
                "Unsupported provider setting: ${setting::class.simpleName}"
            )
        } as Provider<T>
    }
}
