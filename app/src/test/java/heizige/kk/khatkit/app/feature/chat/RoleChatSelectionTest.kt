package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

class RoleChatSelectionTest {
    private val a = Uuid.random()
    private val b = Uuid.random()
    private val current = Conversation(assistantId = a, messageNodes = emptyList())
    private val old = Conversation(assistantId = b, updateAt = Instant.ofEpochSecond(1), messageNodes = emptyList())
    private val recent = Conversation(assistantId = b, updateAt = Instant.ofEpochSecond(10), messageNodes = emptyList())

    @Test fun `reselecting current role keeps its current chat`() {
        assertEquals(current.id, selectRoleChat(a, current, listOf(recent, old), emptySet()))
    }

    @Test fun `switching role resumes latest direct chat without moving ownership`() {
        assertEquals(recent.id, selectRoleChat(b, current, listOf(old, recent), emptySet()))
        assertEquals(a, current.assistantId)
    }

    @Test fun `running chat is preferred to a newer inactive chat`() {
        assertEquals(old.id, selectRoleChat(b, current, listOf(recent, old), setOf(old.id)))
    }

    @Test fun `other roles and groups are never mistaken for the role direct chat`() {
        assertNull(selectRoleChat(b, current, listOf(current, recent.copy(type = GroupChat.TYPE_GROUP)), emptySet()))
    }
}
