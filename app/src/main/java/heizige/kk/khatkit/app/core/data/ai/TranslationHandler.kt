package heizige.kk.khatkit.app.core.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.ai.core.TokenUsage
import heizige.kk.khatkit.ai.provider.CustomBody
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.registry.ModelRegistry
import heizige.kk.khatkit.ai.ui.StreamChunk
import heizige.kk.khatkit.ai.ui.StreamChunkHandler
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import heizige.kk.khatkit.app.core.util.applyPlaceholders
import java.util.Locale

class TranslationHandler(
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
) {
    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null,
    ): Flow<String> = flow {
        val model = TaskRoutes.resolve(settings, ModelTaskType.TRANSLATION)
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            val streamChunkHandler = StreamChunkHandler(model)
            var translationUsage: TokenUsage? = null

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                ),
            ).collect { chunk ->
                if (chunk is StreamChunk.Usage) translationUsage = chunk.usage
                messages = streamChunkHandler.handle(messages, chunk)
                val translatedText = messages.lastOrNull()?.toText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
            runCatching {
                usageRecorder.record(
                    source = UsageRecorder.SOURCE_TRANSLATE,
                    usage = translationUsage,
                    providerId = provider.id.toString(),
                    modelId = model.id.toString(),
                    modelName = model.modelId,
                )
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put(
                                    "target_lang",
                                    JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH)),
                                )
                            },
                        )
                    ),
                ),
            )
            runCatching {
                usageRecorder.record(
                    source = UsageRecorder.SOURCE_TRANSLATE,
                    usage = result.usage,
                    providerId = provider.id.toString(),
                    modelId = model.id.toString(),
                    modelName = model.modelId,
                )
            }
            val translatedText = result.message.toText()

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)
}
