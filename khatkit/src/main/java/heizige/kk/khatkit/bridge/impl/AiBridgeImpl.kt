package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.AiBridge
import heizige.kk.khatkit.bridge.BridgeContext

/** `ai.chat` 归一化后的调用参数（脚本侧的默认值都在这里收敛）。 */
data class AiChatRequest(
    val cardName: String,
    val prompt: String,
    val system: String,
    /** 卡片指定的供应商名；空 = 用用户当前聊天模型所在的供应商。 */
    val provider: String,
    /** 卡片指定的模型 ID；空 = 用用户当前聊天模型。 */
    val model: String,
    val imagePaths: List<String>,
    /** 0 = 不限制。 */
    val maxTokens: Int,
    /** 0 = 不覆盖用户配置。 */
    val temperature: Double,
    val timeoutSeconds: Int,
    /** 本次调用的硬超时（毫秒）：取 `timeoutSeconds` 与卡片运行剩余预算的较小值。 */
    val timeoutMs: Long,
)

/**
 * 宿主侧的模型调用能力。
 *
 * `khatkit` 不依赖 `app`，因此真正的供应商调用由宿主实现本接口并注入
 * （见 `BridgeFactory.create(ai = …)`）。实现里负责：解析模型、调用供应商、
 * 把失败翻译成中文错误。
 */
fun interface AiEngine {
    /** @return 模型输出文本；失败抛中文异常。 */
    fun chat(request: AiChatRequest): String
}

/**
 * `ai` bridge 的宿主侧实现（真正的模型调用委托给 app 注入的 [AiEngine]）。
 *
 * 与 `MediaPickerHost` 同模式：这里是进程级单例，由 `BridgeRegistry.inject` 按
 * manifest 换成绑定 [BridgeContext] 的视图，审批策略与运行超时都按卡片生效。
 */
class CardAiBridge(private val engine: AiEngine) : AiBridge {

    /** 绑定本次运行的视图。 */
    fun forRun(context: BridgeContext): AiBridge = RunScopedAiBridge(context, engine)

    override fun chat(
        prompt: String,
        system: String,
        provider: String,
        model: String,
        imagePaths: List<String>,
        maxTokens: Int,
        temperature: Double,
        timeoutSeconds: Int,
    ): String = throw IllegalStateException(UNAVAILABLE)

    override fun complete(prompt: String, maxTokens: Int): String =
        throw IllegalStateException(UNAVAILABLE)

    companion object {
        const val UNAVAILABLE = "卡片 AI 能力尚未接入：请先在设置中配置模型供应商"
    }
}

/** 绑定运行上下文的 `ai` 视图：审批 → 预算裁剪 → 调用宿主 [AiEngine]。 */
private class RunScopedAiBridge(
    private val context: BridgeContext,
    private val engine: AiEngine,
) : AiBridge {

    override fun chat(
        prompt: String,
        system: String,
        provider: String,
        model: String,
        imagePaths: List<String>,
        maxTokens: Int,
        temperature: Double,
        timeoutSeconds: Int,
    ): String = invoke(CHAT_KEY, prompt, system, provider, model, imagePaths, maxTokens, temperature, timeoutSeconds)

    override fun complete(prompt: String, maxTokens: Int): String =
        invoke(completeKey(), prompt, "", "", "", emptyList(), maxTokens, 0.0, DEFAULT_TIMEOUT_SECONDS)

    /** 审批 → 预算裁剪 → 委托宿主。`chat` 与 `complete` 共用，避免重复审批。 */
    private fun invoke(
        policyKey: String,
        prompt: String,
        system: String,
        provider: String,
        model: String,
        imagePaths: List<String>,
        maxTokens: Int,
        temperature: Double,
        timeoutSeconds: Int,
    ): String {
        require(prompt.isNotBlank()) { "$policyKey 的 prompt 不能为空" }
        val target = model.ifBlank { provider.ifBlank { "当前聊天模型" } }
        require(approve(policyKey, target, prompt)) {
            "用户拒绝了卡片 ${context.cardName} 的模型调用请求：$target"
        }
        return engine.chat(
            AiChatRequest(
                cardName = context.cardName,
                prompt = prompt,
                system = system,
                provider = provider.trim(),
                model = model.trim(),
                imagePaths = imagePaths,
                maxTokens = maxTokens.coerceAtLeast(0),
                temperature = temperature.coerceAtLeast(0.0),
                timeoutSeconds = timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS),
                timeoutMs = budget(timeoutSeconds),
            ),
        )
    }

    /** `complete` 优先看自己的声明，没声明时沿用 `ai.chat`。 */
    private fun completeKey(): String =
        if (context.permissions.containsKey(COMPLETE_KEY)) COMPLETE_KEY else CHAT_KEY

    /**
     * 方法级策略：默认 `ask`，走 [heizige.kk.khatkit.bridge.ApprovalGate]；`deny` 直接拒。
     */
    private fun approve(key: String, target: String, prompt: String): Boolean {
        return when (context.permissions[key]) {
            "allow" -> true
            "deny" -> false
            else -> context.approvalGate?.request(
                "卡片调用模型：${context.cardName}",
                "卡片「${context.cardName}」请求调用模型 $target（${prompt.length} 字，$key）",
                APPROVAL_CATEGORY,
            ) ?: false
        }
    }

    /** 单次调用超时取 `timeoutSeconds` 与卡片运行剩余预算的较小值。 */
    private fun budget(timeoutSeconds: Int): Long {
        val seconds = timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS).toLong() * 1000
        val remaining = context.deadlineAt.takeIf { it > 0 }?.minus(System.currentTimeMillis())
        require(remaining == null || remaining > 0) { "卡片运行超时，已终止" }
        return minOf(seconds, remaining ?: seconds)
    }

    private companion object {
        const val CHAT_KEY = "ai.chat"
        const val COMPLETE_KEY = "ai.complete"
        const val APPROVAL_CATEGORY = "ai_invoke"
        const val DEFAULT_TIMEOUT_SECONDS = 120
        const val MAX_TIMEOUT_SECONDS = 600
    }
}
