package heizige.kk.khatkit.app.core.data.repository

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class RoleHistoryExtensionsTest {
    private val a = Uuid.random()
    private val b = Uuid.random()
    private val user = UIMessage.user("Common request")
    private val own = UIMessage.assistant("A private group message").copy(roleId = "a")
    private val foreign = UIMessage.assistant("B private group message").copy(roleId = "b")
    private val mentioned = UIMessage.assistant("B visible to A").copy(roleId = "b", mentionRoleIds = listOf("a"))
    private val config = GroupConfig(roles = listOf(
        GroupRole("a", assistantId = a.toString()), GroupRole("b", assistantId = b.toString()),
    ))
    private fun group() = Conversation(assistantId = b, type = GroupChat.TYPE_GROUP,
        groupConfig = config, messageNodes = listOf(user, own, foreign, mentioned).map { it.toMessageNode() })

    @Test fun `a role sees only its own direct history`() {
        val chat = Conversation(assistantId = a, messageNodes = listOf(user.toMessageNode()))
        assertTrue(chat.includesRole(a))
        assertFalse(chat.includesRole(b))
        assertEquals(listOf(user), chat.messagesVisibleToRole(a))
        assertTrue(chat.messagesVisibleToRole(b).isEmpty())
    }

    @Test fun `group membership comes from roles not the conversation owner`() {
        val chat = group()
        assertTrue(chat.includesRole(a))
        assertTrue(chat.includesRole(b))
        assertFalse(chat.includesRole(Uuid.random()))
    }

    @Test fun `group history applies viewer filtering before recall`() {
        assertEquals(listOf(user.id, own.id, mentioned.id), group().messagesVisibleToRole(a).map { it.id })
        assertFalse(group().messagesVisibleToRole(a).any { it.id == foreign.id })
        assertFalse(group().messagesVisibleToRole(b).any { it.id == own.id })
    }

    @Test fun `malformed group never falls back to direct owner history`() {
        val chat = group().copy(groupConfig = null)
        assertFalse(chat.includesRole(b))
        assertTrue(chat.messagesVisibleToRole(b).isEmpty())
    }

    @Test fun `nonmember gets neither group messages nor summaries`() {
        assertTrue(group().messagesVisibleToRole(Uuid.random()).isEmpty())
    }
}
