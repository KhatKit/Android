package heizige.kk.khatkit.app.feature.automation.data

import android.content.Context
import heizige.kk.khatkit.app.feature.automation.helper.TriggerTileHelper

/**
 * 快捷设置磁贴槽位映射持久化（SharedPreferences，文件与 key 保持既有格式）。
 *
 * slot 持久化归本 Repository；系统「添加到快捷设置」适配见
 * [heizige.kk.khatkit.app.feature.automation.helper.TriggerTileHelper]。
 */
class TriggerTileRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun slotFor(card: String): Int? =
        (0 until MAX_SLOTS).firstOrNull { prefs.getString(key(it), null) == card }

    fun cardFor(slot: Int): String? = prefs.getString(key(slot), null)

    /** 给卡片分配槽位（已分配则复用）；无空闲槽位返回 null。 */
    fun assign(card: String): Int? {
        slotFor(card)?.let { return it }
        val free = (0 until MAX_SLOTS).firstOrNull { prefs.getString(key(it), null).isNullOrBlank() }
            ?: return null
        prefs.edit().putString(key(free), card).apply()
        return free
    }

    /** 按当前仍有效的磁贴卡片列表回收/分配槽位。 */
    fun sync(cards: List<String>) {
        val editor = prefs.edit()
        val keep = mutableMapOf<Int, String>()
        (0 until MAX_SLOTS).forEach { slot ->
            val card = prefs.getString(key(slot), null)
            when {
                card.isNullOrBlank() -> Unit
                card in cards -> keep[slot] = card
                else -> editor.remove(key(slot))
            }
        }
        cards.filterNot { it in keep.values }.forEach { card ->
            val free = (0 until MAX_SLOTS).firstOrNull { it !in keep } ?: return@forEach
            editor.putString(key(free), card)
            keep[free] = card
        }
        editor.apply()
    }

    companion object {
        const val MAX_SLOTS = 3
        private const val PREFS_NAME = "khatkit_trigger_tiles"
        private const val KEY_PREFIX = "slot_"

        private fun key(slot: Int) = "$KEY_PREFIX$slot"
    }
}
