package heizige.kk.khatkit.app.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.card.CardManifest
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 快捷设置磁贴：点击运行绑定的卡片。
 *
 * 一个 TileService 组件在系统里只能对应一个磁贴，因此固定用 3 个子类做槽位
 * （见 [TriggerTileRegistry]），卡片与槽位的绑定在设置页里通过「添加到快捷设置」完成。
 * 磁贴名称显示绑定卡片；未绑定或卡片被停用时置为 INACTIVE。
 */
@AndroidEntryPoint
abstract class TriggerTileServiceBase : TileService() {

    @Inject
    lateinit var controller: TriggerController

    /** 槽位下标，决定读取哪一条卡片绑定。 */
    protected abstract val slot: Int

    protected fun boundCard(): String? =
        TriggerTileRegistry(this).cardFor(slot)?.takeIf { it.isNotBlank() }

    override fun onStartListening() {
        super.onStartListening()
        val card = boundCard()
        qsTile?.apply {
            label = when {
                card == null -> getString(R.string.trigger_tile_label)
                else -> "${getString(R.string.trigger_tile_label)}：$card"
            }
            state = if (card != null && controller.settings.isCardEnabled(card)) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val card = boundCard() ?: return
        controller.runExternal(card, CardManifest.EVENT_TILE)
    }
}

/** 磁贴槽位 0。 */
class TriggerTileService : TriggerTileServiceBase() {
    override val slot: Int = 0
}

/** 磁贴槽位 1。 */
class TriggerTileService2 : TriggerTileServiceBase() {
    override val slot: Int = 1
}

/** 磁贴槽位 2。 */
class TriggerTileService3 : TriggerTileServiceBase() {
    override val slot: Int = 2
}
