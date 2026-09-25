package heizige.kk.khatkit.app.feature.record

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
object RecordCardSaver {

    data class Result(
        val ok: Boolean,
        val cardName: String = "",
        val message: String = "",
    )

    suspend fun save(context: Context, title: String, steps: List<RecordedStep>): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val card = RecordingCardFactory.create(title, steps)
                val root = File(context.applicationContext.filesDir, "khatkit")
                val dir = CardCache(root).cardDir(card.name, card.version)
                dir.mkdirs()
                File(dir, "card.json").writeText(card.manifestJson)
                File(dir, "main.lua").writeText(card.scriptText)
                Result(ok = true, cardName = card.name, message = "已保存卡片：${card.name}")
            }.getOrElse {
                Result(ok = false, message = "保存卡片失败：${it.message ?: it.javaClass.simpleName}")
            }
        }
}
