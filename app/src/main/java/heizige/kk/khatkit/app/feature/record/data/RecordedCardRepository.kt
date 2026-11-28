package heizige.kk.khatkit.app.feature.record.data

import heizige.kk.khatkit.app.R
import android.content.Context
import heizige.kk.khatkit.hub.CardCache
import heizige.kk.khatkit.record.RecordedStep
import heizige.kk.khatkit.record.RecordingCardFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把录制结果写成 Lua 卡片。
 *
 * 复用与卡片市场 / 事件触发器同一份本地缓存目录（filesDir/khatkit/cards），
 * 不经过 Hub 下载，也不改 KhatKitTools 的私有写入路径。
 */
object RecordedCardRepository {

    suspend fun save(context: Context, title: String, steps: List<RecordedStep>): RecordCardSaveResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val card = RecordingCardFactory.create(title, steps)
                val root = File(context.applicationContext.filesDir, "khatkit")
                val dir = CardCache(root).cardDir(card.name, card.version)
                dir.mkdirs()
                File(dir, "card.json").writeText(card.manifestJson)
                File(dir, "main.lua").writeText(card.scriptText)
                RecordCardSaveResult(ok = true, cardName = card.name, message = context.getString(R.string.record_card_saved, card.name))
            }.getOrElse {
                RecordCardSaveResult(ok = false, message = context.getString(R.string.record_card_save_failed, it.message ?: it.javaClass.simpleName))
            }
        }
}

/** 一次录制卡片保存的结果（数据模型，独立于仓储流程）。 */
data class RecordCardSaveResult(
    val ok: Boolean,
    val cardName: String = "",
    val message: String = "",
)
