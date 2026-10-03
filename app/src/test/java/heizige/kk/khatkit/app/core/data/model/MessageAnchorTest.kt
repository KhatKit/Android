package heizige.kk.khatkit.app.core.data.model

import heizige.kk.khatkit.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class MessageAnchorTest {
    @Test
    fun `locates the node and swipe that contain the source message`() {
        val target = UIMessage.user("the fact")
        val other = UIMessage.assistant("other")
        val node = MessageNode(messages = listOf(other, target), selectIndex = 0)
        val conversationId = Uuid.random()
        val anchor = locateMessage(conversationId, listOf(node), target.id)
        assertEquals(conversationId, anchor?.conversationId)
        assertEquals(node.id, anchor?.nodeId)
        assertEquals(1, anchor?.selectIndex)
    }

    @Test
    fun `ignores a node that only mentions the id in text`() {
        val decoy = UIMessage.user("see 00000000-0000-0000-0000-000000000099")
        val node = MessageNode(messages = listOf(decoy), selectIndex = 0)
        assertNull(locateMessage(Uuid.random(), node, Uuid.parse("00000000-0000-0000-0000-000000000099")))
    }
}
