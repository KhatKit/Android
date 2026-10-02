package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.AiBridge

/** AI provider 尚未由宿主注入时的明确错误实现。 */
class UnavailableAiBridge : AiBridge {
    override fun chat(
        prompt: String,
        system: String,
        provider: String,
        model: String,
        imagePaths: List<String>,
        maxTokens: Int,
        temperature: Double,
        timeoutSeconds: Int,
    ): String = throw IllegalStateException("卡片 AI 能力尚未配置：请先在设置中配置模型供应商")

    override fun complete(prompt: String, maxTokens: Int): String =
        throw IllegalStateException("卡片 AI 能力尚未配置：请先在设置中配置模型供应商")
}
