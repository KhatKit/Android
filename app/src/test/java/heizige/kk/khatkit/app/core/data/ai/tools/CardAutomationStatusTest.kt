package heizige.kk.khatkit.app.core.data.ai.tools

import heizige.kk.khatkit.card.CardManifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardAutomationStatusTest {
    @Test fun ordinaryCardsAndWidgetsStayQuiet() {
        val card = CardManifest("codex_resets",
            requires = CardManifest.Requires(bridges = listOf("net", "ui", "store")))
        for (trigger in listOf("用户", "AI", "聊天小组件")) {
            assertFalse(trigger, showsAutomationStatus(card, trigger))
        }
    }

    @Test fun deviceOperationsAndAutomaticRunsShowStatus() {
        for (bridge in listOf("accessibility", "root", "shizuku")) {
            assertTrue(bridge, showsAutomationStatus(CardManifest("device",
                requires = CardManifest.Requires(bridges = listOf(bridge))), "用户"))
        }
        assertTrue(showsAutomationStatus(CardManifest("shell", engine = "command"), "AI"))
        assertTrue(showsAutomationStatus(CardManifest("shell", privilege = "elevated"), "用户"))
        for (trigger in listOf("定时任务", "事件触发", "flow")) {
            assertTrue(trigger, showsAutomationStatus(CardManifest("task"), trigger))
        }
    }
}
