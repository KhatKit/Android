package heizige.kk.khatkit.app.feature.assistant.detail

import heizige.kk.khatkit.app.core.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantEditManagerTest {
    private val original = Assistant(name = "旧名")

    @Test
    fun `edit is visible synchronously before persistence`() {
        val edits = AssistantEditManager(original)
        val renamed = original.copy(name = "旧名新")
        edits.beginEdit(renamed)
        assertEquals(renamed, edits.state.value)
    }

    @Test
    fun `older persistence echoes cannot replace newer typing`() {
        val edits = AssistantEditManager(original)
        val first = original.copy(name = "旧名新")
        val second = original.copy(name = "旧名新名")
        edits.beginEdit(first)
        edits.beginEdit(second)
        edits.acceptStored(original)
        edits.acceptStored(first)
        edits.finishEdit(first)
        assertEquals(second, edits.state.value)
        edits.acceptStored(first)
        assertEquals(second, edits.state.value)
        edits.finishEdit(second)
        assertEquals(second, edits.state.value)
    }

    @Test
    fun `external changes resume after all writes complete`() {
        val edits = AssistantEditManager(original)
        val renamed = original.copy(name = "新名")
        edits.beginEdit(renamed)
        edits.finishEdit(renamed)
        val external = renamed.copy(name = "外部更新")
        edits.acceptStored(external)
        assertEquals(external, edits.state.value)
    }

    @Test
    fun `failed final write restores last stored snapshot`() {
        val edits = AssistantEditManager(original)
        edits.beginEdit(original.copy(name = "未保存"))
        edits.finishEdit(original)
        assertEquals(original, edits.state.value)
    }

    @Test
    fun `temporary missing role does not invent a new editing identity`() {
        val edits = AssistantEditManager(original)
        edits.acceptStored(null)
        assertEquals(original, edits.state.value)
    }
}
