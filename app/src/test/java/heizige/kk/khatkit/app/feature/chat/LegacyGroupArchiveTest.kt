package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.feature.chat.data.LEGACY_GROUP_ARCHIVE_MESSAGE
import heizige.kk.khatkit.app.feature.chat.data.requireActiveRoleConversation
import heizige.kk.khatkit.app.feature.chat.data.isArchivedGroupConversation

class LegacyGroupArchiveTest {
    private fun conversation(type: String, config: GroupConfig? = null) = Conversation(
        assistantId = Uuid.random(), messageNodes = emptyList(), type = type, groupConfig = config,
    )

    @Test
    fun directRoleChatRemainsActive() {
        val chat = conversation(GroupChat.TYPE_DIRECT)
        assertFalse(chat.isArchivedGroupConversation())
        requireActiveRoleConversation(chat)
    }

    @Test
    fun groupIsArchivedEvenWithoutConfig() {
        listOf(null, GroupConfig()).forEach { config ->
            val chat = conversation(GroupChat.TYPE_GROUP, config)
            assertTrue(chat.isArchivedGroupConversation())
            val error = assertThrows(IllegalStateException::class.java) { requireActiveRoleConversation(chat) }
            assertEquals(LEGACY_GROUP_ARCHIVE_MESSAGE, error.message)
        }
    }

    @Test
    fun archiveDoesNotRewriteStoredData() {
        val chat = conversation(GroupChat.TYPE_GROUP, GroupConfig())
        val snapshot = chat.copy()
        assertThrows(IllegalStateException::class.java) { requireActiveRoleConversation(chat) }
        assertEquals(snapshot, chat)
    }

    @Test
    fun directChatWithStaleConfigDoesNotBecomeArchived() {
        requireActiveRoleConversation(conversation(GroupChat.TYPE_DIRECT, GroupConfig()))
    }

    @Test
    fun retiredEntryPointsAreRemovedAndArchiveIsEnforced() {
        var root = File("").absoluteFile
        while (!File(root, "settings.gradle.kts").isFile) root = root.parentFile ?: error("Missing repo")
        val feature = File(root, "app/src/main/java/heizige/kk/khatkit/app/feature/chat")
        listOf("GroupChatPage.kt", "GroupMemberBar.kt", "GroupRoleCompletionProvider.kt", "GroupTavernImportCard.kt").forEach {
            assertFalse("$it must not return", File(feature, it).exists())
        }
        val drawer = File(feature, "ChatDrawer.kt").readText()
        assertFalse(drawer.contains("createGroup"))
        assertFalse(File(feature, "RoleDrawerList.kt").readText().contains("新建群聊"))
        val manager = File(feature, "ChatManager.kt").readText()
        assertFalse(manager.contains("fun createGroup("))
        assertTrue(manager.contains("requireActiveRoleConversation(initialConversation)"))
        assertTrue(manager.contains("requireActiveRoleConversation(currentConversation)"))
        val page = File(feature, "ChatPage.kt").readText()
        assertTrue(page.contains("if (archived)"))
        assertFalse(page.contains("GroupConfigSheet("))
        val routes = File(root, "app/src/main/java/heizige/kk/khatkit/app/core/network/routes/ConversationRoutes.kt").readText()
        assertTrue(routes.contains("ConversationOperation.SendMessage"))
    }
}
