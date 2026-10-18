package heizige.kk.khatkit.app.core.data.ai.tools

import heizige.kk.khatkit.ui.UiMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 运行期 UI 策略的默认推导（`docs/lua-card-development.md` §3.5）。
 *
 * 契约：有人看着的入口允许弹层，无人值守的入口一律 HEADLESS；未知来源按无人值守处理
 * （宁可静默跑完，也不要在后台挂一个没人能点的弹层）。
 */
class CardUiModeTest {
    @Test
    fun `interactive triggers keep the UI`() {
        listOf("AI", "用户", "聊天小组件").forEach { trigger ->
            assertEquals(trigger, UiMode.INTERACTIVE, defaultUiModeForTrigger(trigger))
        }
    }

    @Test
    fun `unattended triggers run headless`() {
        listOf("定时任务", "事件触发", "flow").forEach { trigger ->
            assertEquals(trigger, UiMode.HEADLESS, defaultUiModeForTrigger(trigger))
        }
    }

    @Test
    fun `unknown trigger falls back to headless`() {
        assertEquals(UiMode.HEADLESS, defaultUiModeForTrigger(""))
        assertEquals(UiMode.HEADLESS, defaultUiModeForTrigger("debug"))
    }

    @Test
    fun `ai switch parses headless only`() {
        assertEquals(UiMode.HEADLESS, UiMode.fromId("headless"))
        assertEquals(UiMode.HEADLESS, UiMode.fromId(" HEADLESS "))
        assertEquals(UiMode.INTERACTIVE, UiMode.fromId("interactive"))
        assertEquals(null, UiMode.fromId(null))
        assertEquals(null, UiMode.fromId("bogus"))
    }
}
