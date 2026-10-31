package heizige.kk.khatkit.app.core.data.ai

import heizige.kk.khatkit.app.core.data.db.dao.UsageRecordDAO
import heizige.kk.khatkit.app.core.data.db.entity.UsageRecordEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * US1（上游 5a90c0548）：统一记用量流水。各请求点在拿到 usage 后调 [record]，失败只打日志不影响主流程。
 *
 * 来源（source）用字符串常量，UI 侧按它分组：CHAT / COMPRESS / TITLE / SUGGEST / TRANSLATE / MEMORY / OCR。
 */
@Singleton
class UsageRecorder @Inject constructor(
    private val dao: UsageRecordDAO,
) {
    suspend fun record(
        source: String,
        promptTokens: Int,
        completionTokens: Int,
        cachedTokens: Int = 0,
        providerId: String? = null,
        modelId: String? = null,
        modelName: String? = null,
        assistantId: String? = null,
        conversationId: String? = null,
    ) {
        if (promptTokens <= 0 && completionTokens <= 0) return
        runCatching {
            dao.insert(
                UsageRecordEntity(
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                    source = source,
                    providerId = providerId,
                    modelId = modelId,
                    modelName = modelName,
                    assistantId = assistantId,
                    conversationId = conversationId,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    cachedTokens = cachedTokens,
                )
            )
        }
    }

    companion object {
        const val SOURCE_CHAT = "CHAT"
        const val SOURCE_COMPRESS = "COMPRESS"
        const val SOURCE_TITLE = "TITLE"
        const val SOURCE_SUGGEST = "SUGGEST"
        const val SOURCE_TRANSLATE = "TRANSLATE"
        const val SOURCE_MEMORY = "MEMORY"
        const val SOURCE_OCR = "OCR"
    }
}
