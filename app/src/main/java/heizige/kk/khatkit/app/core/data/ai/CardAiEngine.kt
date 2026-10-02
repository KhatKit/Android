package heizige.kk.khatkit.app.core.data.ai

import android.util.Log
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.data.datastore.getCurrentChatModel
import heizige.kk.khatkit.bridge.impl.AiChatRequest
import heizige.kk.khatkit.bridge.impl.AiEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** 一次卡片内模型调用的结果快照，用于上报 Hub 统计。 */
data class AiCallReport(
    val cardName: String,
    val model: String,
    val ok: Boolean,
    val durationMs: Long,
    val message: String = "",
)

/**
 * 卡片 `ai` bridge 的宿主实现（`ai.chat` / `ai.complete` 真正调模型的地方）。
 *
 * 凭据一律走用户已在设置里配好的供应商，卡片不能自带 key：
 * - `provider` / `model` 都为空 → 用当前聊天模型；
 * - 只给 `model` → 在已配置供应商里按模型 ID / 显示名匹配；
 * - 给了 `provider` → 额外要求供应商名匹配。
 *
 * 调用是**非流式**的（Rust bridge 只有同步契约），超时由 [AiChatRequest.timeoutMs] 兜底；
 * 结果 best-effort 经 [onCall] 上报 Hub，失败不阻塞脚本。
 */
class CardAiEngine(
    private val providerManager: ProviderManager,
    private val settings: () -> Settings,
    private val onCall: (AiCallReport) -> Unit = {},
) : AiEngine {

    override fun chat(request: AiChatRequest): String {
        logBlockingThread("ai.chat")
        val startedAt = System.currentTimeMillis()
        return try {
            val text = runBlocking { withTimeout(request.timeoutMs) { generate(request) } }
            report(request, startedAt, ok = true, message = text.take(40))
            text
        } catch (e: TimeoutCancellationException) {
            report(request, startedAt, ok = false, message = "超时")
            throw IllegalStateException(
                "模型响应超时（${request.timeoutMs / 1000} 秒）：请缩短内容，或调大 timeoutSeconds 后重试",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(request, startedAt, ok = false, message = e.message.orEmpty().take(80))
            throw IllegalStateException("模型调用失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun generate(request: AiChatRequest): String {
        val settings = settings()
        val (provider, model) = resolveModel(request, settings)
        val result = providerManager.getProviderByType(provider).generateText(
            providerSetting = provider,
            messages = buildMessages(request),
            params = TextGenerationParams(
                model = model,
                temperature = request.temperature.takeIf { it > 0.0 }?.toFloat(),
                maxTokens = request.maxTokens.takeIf { it > 0 },
            ),
        )
        return result.message.toText().ifBlank {
            throw IllegalStateException("模型返回了空内容：可重试或改用其他模型")
        }
    }

    private fun buildMessages(request: AiChatRequest): List<UIMessage> = buildList {
        if (request.system.isNotBlank()) add(UIMessage.system(request.system))
        add(
            UIMessage(
                role = MessageRole.USER,
                parts = buildList {
                    request.imagePaths.forEach { add(UIMessagePart.Image(url = it)) }
                    add(UIMessagePart.Text(request.prompt))
                },
            ),
        )
    }

    /** 解析卡片请求的模型；找不到时抛带可操作指引的中文错误。 */
    private fun resolveModel(request: AiChatRequest, settings: Settings): Pair<ProviderSetting, Model> {
        val enabled = settings.providers.filter { it.enabled }
        if (enabled.isEmpty()) {
            throw IllegalStateException(NOT_CONFIGURED)
        }
        val modelName = request.model
        if (modelName.isBlank()) {
            val current = settings.getCurrentChatModel()
                ?: throw IllegalStateException(NOT_CONFIGURED)
            val provider = current.findProvider(settings.providers)
                ?: throw IllegalStateException(NOT_CONFIGURED)
            return provider to current
        }
        val hit = enabled.firstNotNullOfOrNull { provider ->
            provider.models.firstOrNull { it.matches(modelName) }?.let { provider to it }
        }?.takeIf { (provider, _) -> request.provider.isBlank() || provider.matches(request.provider) }
        return hit ?: throw IllegalStateException(
            "未找到模型「$modelName」：请在设置里先添加该模型，或改用当前聊天模型（可用模型：" +
                enabled.flatMap { p -> p.models.map { it.modelId } }.distinct().take(8).joinToString(" / ") + "）",
        )
    }

    private fun report(request: AiChatRequest, startedAt: Long, ok: Boolean, message: String) {
        runCatching {
            onCall(
                AiCallReport(
                    cardName = request.cardName,
                    model = request.model.ifBlank { request.provider.ifBlank { "当前聊天模型" } },
                    ok = ok,
                    durationMs = System.currentTimeMillis() - startedAt,
                    message = message,
                ),
            )
        }
    }

    private fun Model.matches(name: String): Boolean =
        modelId.equals(name, ignoreCase = true) ||
            displayName.equals(name, ignoreCase = true) ||
            id.toString().equals(name, ignoreCase = true)

    private fun ProviderSetting.matches(name: String): Boolean =
        this.name.equals(name, ignoreCase = true) || id.toString().equals(name, ignoreCase = true)

    /**
     * 首次阻塞前记一次线程名：bridge 是同步契约，这里会阻塞调用线程，
     * 必须是 `CardExecutor` 的 `Dispatchers.Default` 而不是主线程（规格 §1.4 约束 8）。
     */
    private fun logBlockingThread(api: String) {
        if (!threadLogged.compareAndSet(false, true)) return
        Log.d(TAG, "$api 阻塞调用线程：${Thread.currentThread().name}")
    }

    companion object {
        private const val TAG = "CardAiEngine"
        private val threadLogged = java.util.concurrent.atomic.AtomicBoolean(false)
        const val NOT_CONFIGURED = "卡片调用模型前请先在设置中配置供应商与模型"
    }
}
