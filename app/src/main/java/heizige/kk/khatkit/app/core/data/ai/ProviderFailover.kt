package heizige.kk.khatkit.app.core.data.ai

import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.data.datastore.Settings
import java.io.IOException

/**
 * Provider 链式 failover（设计文档 10.3）。
 *
 * 主 provider 限流/过载/网络不可用时，按配置顺序自动切到下一个启用的 provider。
 * 鉴权、参数类错误切换也没用，直接抛出。
 */
object ProviderFailover {

    private val RETRYABLE_KEYWORDS = listOf(
        "429", "rate limit", "too many requests", "quota", "insufficient",
        "overloaded", "capacity", "503", "502", "500", "timeout",
    )

    /** 候选链：主 (provider, model) 在前，其后是按配置顺序的其它启用 provider。 */
    fun buildChain(
        settings: Settings,
        primaryProvider: ProviderSetting,
        primaryModel: Model,
    ): List<Pair<ProviderSetting, Model>> {
        val chain = mutableListOf(primaryProvider to primaryModel)
        settings.providers
            .asSequence()
            .filter { it.enabled && it.id != primaryProvider.id }
            .forEach { candidate ->
                val candidateModel = candidate.models.firstOrNull {
                    it.type == ModelType.CHAT && ModelAbility.TOOL in it.abilities
                } ?: candidate.models.firstOrNull { it.type == ModelType.CHAT } ?: return@forEach
                if (chain.none { it.second.id == candidateModel.id }) {
                    chain += candidate to candidateModel
                }
            }
        return chain
    }

    fun isEligible(error: Throwable): Boolean {
        if (error is IOException) return true
        val message = (error.message ?: "").lowercase()
        return RETRYABLE_KEYWORDS.any { message.contains(it) }
    }
}
