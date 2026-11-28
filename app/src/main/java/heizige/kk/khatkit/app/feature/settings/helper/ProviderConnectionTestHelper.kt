package heizige.kk.khatkit.app.feature.settings.helper

import heizige.kk.khatkit.ai.core.Tool
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.Provider
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.ui.StreamChunk
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart

/** 连接诊断的网络请求；加载状态、并发调度与结果文案由调用界面持有。 */
internal class ProviderConnectionTestHelper(
    private val provider: Provider<ProviderSetting>,
) {
    suspend fun nonStreaming(setting: ProviderSetting, model: Model): String {
        val result = provider.generateText(
            providerSetting = setting,
            messages = greetingMessages(),
            params = params(model),
        )
        return result.message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
    }

    suspend fun streaming(
        setting: ProviderSetting,
        model: Model,
        onTextDelta: (String) -> Unit,
    ) {
        provider.streamText(
            providerSetting = setting,
            messages = greetingMessages(),
            params = params(model),
        ).collect { chunk ->
            if (chunk is StreamChunk.TextDelta) onTextDelta(chunk.text)
        }
    }

    suspend fun tools(setting: ProviderSetting, model: Model): UIMessage {
        val testTool = Tool(
            name = "get_current_time",
            description = "Get the current date and time.",
            execute = { emptyList() },
        )
        return provider.generateText(
            providerSetting = setting,
            messages = listOf(
                UIMessage.system("You are a helpful assistant"),
                UIMessage.user("Use the get_current_time tool."),
            ),
            params = params(model).copy(tools = listOf(testTool)),
        ).message
    }

    private fun params(model: Model) = TextGenerationParams(
        model = model,
        customHeaders = model.customHeaders,
        customBody = model.customBodies,
    )

    private fun greetingMessages() = listOf(
        UIMessage.system("You are a helpful assistant"),
        UIMessage.user("hello"),
    )
}
