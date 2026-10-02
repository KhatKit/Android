package heizige.kk.khatkit.app.core.data.ai

import heizige.kk.khatkit.ai.provider.EmbeddingGenerationParams
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.bridge.impl.EmbeddingEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * 卡片向量能力（`store.embedInsert` / `store.embedSearch`）的宿主实现。
 *
 * 同样只用用户已配置的供应商：挑第一个类型为 [ModelType.EMBEDDING] 的模型，
 * 卡片不能自带 key，也不能指定用哪个模型。审批与配额由 khatkit 侧按 `ai.chat` 策略处理。
 */
class CardEmbeddingEngine(
    private val providerManager: ProviderManager,
    private val settings: () -> Settings,
    private val timeoutMs: Long = 30_000L,
) : EmbeddingEngine {

    override fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        return try {
            runBlocking { withTimeout(timeoutMs) { generate(texts) } }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("向量化超时（${timeoutMs / 1000} 秒）：请缩短文本或换更快的 embedding 模型")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("向量化失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun generate(texts: List<String>): List<FloatArray> {
        val settings = settings()
        val (provider, model) = resolveEmbeddingModel(settings)
        val result = providerManager.getProviderByType(provider).generateEmbedding(
            providerSetting = provider,
            params = EmbeddingGenerationParams(model = model, input = texts),
        )
        return result.embeddings.map { it.toFloatArray() }.also {
            require(it.size == texts.size) { "embedding 数量与输入不一致（${it.size} / ${texts.size}）" }
        }
    }

    private fun resolveEmbeddingModel(settings: Settings): Pair<ProviderSetting, Model> {
        val hit = settings.providers.filter { it.enabled }.firstNotNullOfOrNull { provider ->
            provider.models.firstOrNull { it.type == ModelType.EMBEDDING }?.let { provider to it }
        } ?: throw IllegalStateException(NOT_CONFIGURED)
        val provider = hit.second.findProvider(settings.providers) ?: hit.first
        return provider to hit.second
    }

    companion object {
        const val NOT_CONFIGURED =
            "卡片向量检索需要一个 embedding 模型：请在设置里给某个供应商添加 embedding 类型的模型"
    }
}