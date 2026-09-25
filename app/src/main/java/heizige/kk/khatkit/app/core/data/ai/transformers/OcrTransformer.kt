package heizige.kk.khatkit.app.core.data.ai.transformers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Modality
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.common.cache.LruCache
import heizige.kk.khatkit.common.cache.SingleFileCacheStore
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.datastore.findProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import kotlin.time.Duration.Companion.days

private const val TAG = "OcrTransformer"

@Singleton
class OcrTransformer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsRepository,
    private val providerManager: ProviderManager,
) : InputMessageTransformer {
    private val cache by lazy {
        val json = Json { allowStructuredMapKeys = true }
        val store = SingleFileCacheStore(
            file = File(context.cacheDir, "ocr_cache.json"),
            keySerializer = String.serializer(),
            valueSerializer = String.serializer(),
            json = json
        )
        LruCache(
            capacity = 64,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = true,
            expireAfterWriteMillis = 3.days.inWholeMilliseconds,
        )
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.model.inputModalities.contains(Modality.IMAGE)) {
            return messages
        }

        val hasImages = messages.any { message ->
            message.parts.any { it is UIMessagePart.Image }
        }
        if (!hasImages) return messages

        return withContext(Dispatchers.IO) {
            try {
                ctx.processingStatus.value = "正在识别图片..."
                messages.map { message ->
                    message.copy(
                        parts = message.parts.map { part ->
                            when (part) {
                                // 文本模型：图片统一转成「本机路径 + 类型/大小 + OCR 描述」，
                                // 让模型能直接带路径调用 OCR/图像处理/文件工具，而不是反问用户要路径。
                                is UIMessagePart.Image -> UIMessagePart.Text(
                                    describeImageForTextModel(part, fromUser = message.role == MessageRole.USER)
                                )

                                else -> part
                            }
                        }
                    )
                }
            } finally {
                ctx.processingStatus.value = null
            }
        }
    }

    private suspend fun describeImageForTextModel(part: UIMessagePart.Image, fromUser: Boolean): String {
        // 仅本机文件能给出绝对路径；内联/远程图片用简短占位，避免 data: base64 淹没上下文
        val local = localImageFile(part.url) != null
        val note = imageAttachmentNote(part.url, fromUser = fromUser)
            ?: imageAttachmentLabel(part.url, fromUser = fromUser)
        val ocr = if (isLocalOrInline(part.url)) performOcr(part) else null
        return buildString {
            append(note)
            ocr?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
            if (local) append('\n').append(IMAGE_PATH_TOOL_HINT)
        }
    }

    private fun isLocalOrInline(url: String): Boolean =
        url.startsWith("file:") || url.startsWith("data:")

    /** OCR/图片描述；未配置 OCR 模型或失败时返回 null（不影响路径提示）。 */
    suspend fun performOcr(part: UIMessagePart.Image): String? {
        cache.get(part.url)?.let { cachedResult ->
            Log.i(TAG, "performOcr: Using cached result for ${part.url}")
            return cachedResult
        }

        val settings = settingsStore.settingsFlow.value
        val model = settings.findModelById(settings.ocrModelId) ?: return null
        val providerSetting = model.findProvider(settings.providers) ?: return null
        return runCatching {
            val provider = providerManager.getProviderByType(providerSetting)
            val result = provider.generateText(
                providerSetting = providerSetting,
                messages = listOf(
                    UIMessage.system(settings.ocrPrompt),
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Image(part.url))
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies,
                ),
            )
            val content = result.message.toText().ifBlank { return@runCatching null }
            Log.i(TAG, "performOcr: $content")
            val ocrResult = """
                <image_file_ocr>
                   $content
                </image_file_ocr>
                * The image_file_ocr tag contains a description of an image that the user uploaded to you, not the user's prompt.
            """.trimIndent()

            cache.put(part.url, ocrResult)
            ocrResult
        }.onFailure {
            Log.w(TAG, "performOcr failed: ${part.url}", it)
        }.getOrNull()
    }

    companion object {
        private const val IMAGE_PATH_TOOL_HINT =
            "提示：以上是本机图片路径，需要看图、OCR 或图像处理时请直接调用工具并传入该路径，不要反问用户要图片路径。"
    }
}
