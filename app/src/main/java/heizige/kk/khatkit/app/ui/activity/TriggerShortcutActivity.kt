package heizige.kk.khatkit.app.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import heizige.kk.khatkit.app.service.TriggerController
import heizige.kk.khatkit.app.service.TriggerShortcutPublisher
import heizige.kk.khatkit.card.CardManifest
import org.koin.android.ext.android.inject

/**
 * 桌面动态快捷方式的落地 Activity：读取卡片名并交给 [TriggerController] 运行。
 *
 * 仅接受声明了 `shortcut` 事件的卡片（[TriggerController.runExternal] 内部校验），
 * 运行后立即结束，不展示界面。
 */
class TriggerShortcutActivity : ComponentActivity() {

    private val controller: TriggerController by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val card = intent?.getStringExtra(TriggerShortcutPublisher.EXTRA_CARD)
        if (!card.isNullOrBlank()) {
            controller.runExternal(card, CardManifest.EVENT_SHORTCUT)
        }
        finish()
    }
}
