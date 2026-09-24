package heizige.kk.khatkit.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.ui.activity.TriggerShortcutActivity
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.trigger.TriggerCard

/**
 * 把声明了 `shortcut` 事件的卡片同步成桌面动态快捷方式（ShortcutManager）。
 *
 * - 每个启用的 shortcut 事件生成一个快捷方式：id = trigger_card_<卡片>_<事件下标>
 * - 名称取事件的 `name`（留空回退卡片名）；点击经 [TriggerShortcutActivity] 运行卡片
 * - 仅管理 `trigger_card_` 前缀的动态快捷方式，不影响宿主其他快捷方式
 * - 同步失败（限流/系统限制）静默忽略，不影响卡片本身
 */
object TriggerShortcutPublisher {

    const val ACTION_RUN = "heizige.kk.khatkit.app.action.TRIGGER_SHORTCUT"
    const val EXTRA_CARD = "trigger_card"

    private const val TAG = "TriggerShortcut"
    private const val ID_PREFIX = "trigger_card_"

    fun sync(context: Context, cards: List<TriggerCard>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val wanted = buildList {
            cards.forEach { card ->
                card.events.forEachIndexed { index, event ->
                    if (index in card.disabledIndexes) return@forEachIndexed
                    if (event.type != CardManifest.EVENT_SHORTCUT) return@forEachIndexed
                    val label = event.name.ifBlank { card.name }
                    add(Triple("$ID_PREFIX${card.name}_$index", label, card.name))
                }
            }
        }
        runCatching {
            val existing = manager.dynamicShortcuts.orEmpty().filterNot { it.id.startsWith(ID_PREFIX) }
            val freeSlots = (manager.maxShortcutCountPerActivity - existing.size).coerceAtLeast(0)
            val shortcuts = wanted.take(freeSlots).map { (id, label, cardName) ->
                ShortcutInfo.Builder(context, id)
                    .setShortLabel(label)
                    .setLongLabel("运行卡片：$cardName")
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(intentFor(context, cardName))
                    .build()
            }
            manager.setDynamicShortcuts(existing + shortcuts)
        }.onFailure { Log.e(TAG, "sync shortcuts failed", it) }
    }

    private fun intentFor(context: Context, cardName: String): Intent =
        Intent(context, TriggerShortcutActivity::class.java)
            .setAction(ACTION_RUN)
            .putExtra(EXTRA_CARD, cardName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
