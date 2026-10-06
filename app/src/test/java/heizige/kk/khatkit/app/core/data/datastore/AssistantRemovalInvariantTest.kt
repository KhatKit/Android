package heizige.kk.khatkit.app.core.data.datastore

import heizige.kk.khatkit.app.core.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 回归护栏：真机（OnePlus PKG110 / Android 16）删除最后一个助手时，
 * settingsFlow 会在 persist 之前短暂持有空 assistants 列表，
 * 令 ChatViewModel 的收集器调用 getCurrentAssistant() 时抛 NoSuchElementException 并进入 SafeMode。
 *
 * 修复后的不变量：设置里的助手列表**永不为空**（写入路径 [normalizeAssistants] 兜底），
 * 且 [getCurrentAssistant] 是全函数。
 */
class AssistantRemovalInvariantTest {

    private fun assistant(name: String) = Assistant(id = Uuid.random(), name = name)

    @Test
    fun `removing the last assistant does not crash and falls back to defaults`() {
        val only = assistant("only")
        val settings = Settings(assistants = listOf(only), assistantId = only.id)

        val result = settings.removeAssistant(only)

        assertFalse("助手列表必须不为空", result.assistants.isEmpty())
        assertEquals(DEFAULT_ASSISTANTS, result.assistants)
        // 核心回归：读取当前助手不得抛 NoSuchElementException
        assertEquals(DEFAULT_ASSISTANTS.first(), result.getCurrentAssistant())
    }

    @Test
    fun `getCurrentAssistant tolerates empty list from any source`() {
        // 即使绕过写入路径拿到空列表（C 兜底），也必须有确定返回值而不是抛异常
        val settings = Settings(assistants = emptyList(), assistantId = Uuid.random())

        assertEquals(DEFAULT_ASSISTANTS.first(), settings.getCurrentAssistant())
    }

    @Test
    fun `removing one of many keeps the rest unchanged`() {
        val a = assistant("a")
        val b = assistant("b")
        val c = assistant("c")
        val settings = Settings(assistants = listOf(a, b, c), assistantId = a.id)

        val result = settings.removeAssistant(b)

        assertEquals(listOf(a, c), result.assistants)
        assertEquals(a, result.getCurrentAssistant())
    }

    @Test
    fun `removing an unknown assistant is a no-op`() {
        val a = assistant("a")
        val b = assistant("b")
        val settings = Settings(assistants = listOf(a, b), assistantId = a.id)

        assertEquals(settings.assistants, settings.removeAssistant(assistant("x")).assistants)
    }

    @Test
    fun `normalizeAssistants replaces only empty lists`() {
        assertEquals(DEFAULT_ASSISTANTS, normalizeAssistants(emptyList()))
        val list = listOf(assistant("a"))
        assertSame(list, normalizeAssistants(list))
    }

    @Test
    fun `update write path never persists empty assistants`() {
        // 模拟 SettingsRepository.update 的写入路径：空列表必须先被兜底
        val emptied = Settings(assistants = emptyList())
        val written = emptied.copy(assistants = normalizeAssistants(emptied.assistants))

        assertFalse(written.assistants.isEmpty())
        assertEquals(DEFAULT_ASSISTANTS.first().id, written.getCurrentAssistant().id)
    }
}
