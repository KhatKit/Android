package heizige.kk.khatkit.app.feature.automation.helper

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.util.showTransientNotice
import heizige.kk.khatkit.app.feature.automation.TriggerTileService
import heizige.kk.khatkit.app.feature.automation.TriggerTileService2
import heizige.kk.khatkit.app.feature.automation.TriggerTileService3
import heizige.kk.khatkit.app.feature.automation.data.TriggerTileRepository

/**
 * 从设置页发起「添加到快捷设置」。API 33+ 走 [StatusBarManager.requestAddTileService]；
 * 更低版本只能提示用户去系统快捷设置手动添加（磁贴会出现在可选列表里）。
 *
 * 槽位映射持久化在 [TriggerTileRepository]；本 Helper 只做系统适配。
 */
object TriggerTileHelper {

    private const val TAG = "TriggerTile"

    fun requestAdd(context: Context, card: String, label: String) {
        val repository = TriggerTileRepository(context)
        val slot = repository.assign(card)
        if (slot == null) {
            toast(context, context.getString(R.string.trigger_tile_slots_full, TriggerTileRepository.MAX_SLOTS))
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast(context, context.getString(R.string.trigger_tile_add_hint, context.getString(R.string.trigger_tile_label)))
            return
        }
        val statusBarManager = context.getSystemService(StatusBarManager::class.java) ?: return
        val component = ComponentName(context, componentFor(slot))
        val tileLabel = context.getString(R.string.trigger_tile_card_label, label.ifBlank { card })
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
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> context.getString(R.string.trigger_tile_added)
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> context.getString(R.string.trigger_tile_already_added)
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> context.getString(R.string.trigger_tile_not_added)
                        else -> context.getString(R.string.trigger_tile_add_failed_code, result)
                    },
                )
            }
        }.onFailure {
            Log.e(TAG, "requestAddTileService failed", it)
            toast(context, context.getString(R.string.trigger_tile_add_failed, it.message ?: ""))
        }
    }

    /** 槽位对应的 TileService 组件（顺序与槽位下标一致，平台注册不变）。 */
    private fun componentFor(slot: Int): Class<*> = when (slot) {
        0 -> TriggerTileService::class.java
        1 -> TriggerTileService2::class.java
        else -> TriggerTileService3::class.java
    }

    private fun toast(context: Context, message: String) {
        context.showTransientNotice(message)
    }
}
