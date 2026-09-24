package heizige.kk.khatkit.app.service

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.widget.Toast
import heizige.kk.khatkit.app.R

/**
 * 快捷设置磁贴槽位注册表。
 *
 * 一个 TileService 组件只能对应一个磁贴，所以这里固定提供 [MAX_SLOTS] 个槽位
 * （TriggerTileService / TriggerTileService2 / TriggerTileService3）。
 * 卡片 -> 槽位映射持久化在 SharedPreferences；[sync] 释放不再声明 `tile` 事件的卡片，
 * 并按需要给新卡片分配空闲槽位。用户只能从系统 UI 添加磁贴本体。
 */
class TriggerTileRegistry(context: Context) {

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

        /** 槽位对应的 TileService 组件（顺序与 [slot] 一致）。 */
        fun componentFor(slot: Int): Class<*> = when (slot) {
            0 -> TriggerTileService::class.java
            1 -> TriggerTileService2::class.java
            else -> TriggerTileService3::class.java
        }
    }
}

/**
 * 从设置页发起「添加到快捷设置」。API 33+ 走 [StatusBarManager.requestAddTileService]；
 * 更低版本只能提示用户去系统快捷设置手动添加（磁贴会出现在可选列表里）。
 */
object TriggerTilePublisher {

    private const val TAG = "TriggerTile"

    fun requestAdd(context: Context, card: String, label: String) {
        val registry = TriggerTileRegistry(context)
        val slot = registry.assign(card)
        if (slot == null) {
            toast(context, "磁贴槽位已满（最多 ${TriggerTileRegistry.MAX_SLOTS} 个），请先在其它卡片上移除磁贴")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast(context, "Android 13 以下请到系统快捷设置中添加「${context.getString(R.string.trigger_tile_label)}」磁贴")
            return
        }
        val statusBarManager = context.getSystemService(StatusBarManager::class.java) ?: return
        val component = ComponentName(context, TriggerTileRegistry.componentFor(slot))
        val tileLabel = "卡片：${label.ifBlank { card }}"
        runCatching {
            statusBarManager.requestAddTileService(
                component,
                tileLabel,
                Icon.createWithResource(context, R.drawable.ic_trigger_tile),
                context.mainExecutor,
            ) { result ->
                toast(
                    context,
                    when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "磁贴已添加"
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "该磁贴已存在"
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "用户未添加磁贴"
                        else -> "添加磁贴失败（代码 $result）"
                    },
                )
            }
        }.onFailure {
            Log.e(TAG, "requestAddTileService failed", it)
            toast(context, "添加磁贴失败：${it.message}")
        }
    }

    private fun toast(context: Context, message: String) {
        runCatching { Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show() }
    }
}
