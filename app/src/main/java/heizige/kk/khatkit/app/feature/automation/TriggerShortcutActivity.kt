package heizige.kk.khatkit.app.feature.automation

import android.os.Bundle
import androidx.activity.ComponentActivity
import heizige.kk.khatkit.app.feature.automation.TriggerController
import heizige.kk.khatkit.app.feature.automation.TriggerShortcutPublisher
import heizige.kk.khatkit.card.CardManifest
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 桌面动态快捷方式的落地 Activity：读取卡片名并交给 [TriggerController] 运行。
 *
 * 仅接受声明了 `shortcut` 事件的卡片（[TriggerController.runExternal] 内部校验），
 * 运行后立即结束，不展示界面。
 */
@AndroidEntryPoint
class TriggerShortcutActivity : ComponentActivity() {

    @Inject
    lateinit var controller: TriggerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val card = intent?.getStringExtra(TriggerShortcutPublisher.EXTRA_CARD)
        if (!card.isNullOrBlank()) {
            controller.runExternal(card, CardManifest.EVENT_SHORTCUT)
        }
        finish()
    }
}
