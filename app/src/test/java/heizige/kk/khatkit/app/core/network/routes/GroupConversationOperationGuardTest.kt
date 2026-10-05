package heizige.kk.khatkit.app.core.network.routes

import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.network.ConflictException
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * HTTP 会话操作端点的群聊门禁（`ConversationRoutes.kt` 的 edit / fork / delete / select / regenerate）。
 *
 * 这五个操作都不是群聊感知的：它们直接改会话的消息节点，而群聊轮次的账面
 * （`committed_role_ids` / `spent_tokens`，见 `group_runs`）由群轮次内核独占维护。
 * 外部调用方（`jwtEnabled` 可关，关掉后整个 `/api/` 前缀完全无鉴权）打进来就会让账面与实际产出错位。
 *
 * 本文件只覆盖**判定纯函数**：`groupConversationOperationRejection` / `requireConversationOperationAllowed`。
 * 「五个端点各自真的调用了守卫」由 `GroupOperationApiGuardSourceGuardTest`（源码文本护栏）盯住 ——
 * 仓库 testImplementation 只有 junit，没有 Ktor test host，端点级行为无法在 JVM 里跑起来。
 */
class GroupConversationOperationGuardTest {

    // ---------------- 群聊：一律拒绝 ----------------

    @Test
    fun `group conversation rejects all five blocked operations`() {
        val group = conversation(config = GroupConfig(), type = GroupChat.TYPE_GROUP)
        val reasons = ConversationOperation.entries.associateWith { operation ->
            groupConversationOperationRejection(group, operation)
        }
        val missing = reasons.filterValues { it == null }.keys
        assertTrue(
            "群聊会话必须拒绝这五个操作，实际放行了：$missing",
            missing.isEmpty(),
        )
    }

    // ---------------- 单聊：行为逐字不变 ----------------

    @Test
    fun `direct conversation allows all five operations`() {
        val direct = conversation(config = null, type = GroupChat.TYPE_DIRECT)
        val allowed = ConversationOperation.entries.associateWith { operation ->
            groupConversationOperationRejection(direct, operation)
        }
        val blocked = allowed.filterValues { it != null }.keys
        assertTrue(
            "单聊会话必须原样放行这五个操作，实际拒绝了：$blocked",
            blocked.isEmpty(),
        )
    }

    // ---------------- 边界：isGroupConversation 要求两个条件同时成立 ----------------

    @Test
    fun `groupConfig null but type GROUP is not a group conversation`() {
        // 老数据可能只留了 type 没留 group_config（或反过来）。
        // 判定必须与 ChatManager.isGroupConversation 同口径：两个条件缺一不可，
        // 否则一次普通单聊调用会误伤同 id 的群聊行。
        val stale = conversation(config = null, type = GroupChat.TYPE_GROUP)
        val reasons = ConversationOperation.entries.associateWith { operation ->
            groupConversationOperationRejection(stale, operation)
        }
        val blocked = reasons.filterValues { it != null }.keys
        assertTrue(
            "group_config 为 null 时（哪怕 type=GROUP）必须放行，实际拒绝了：$blocked",
            blocked.isEmpty(),
        )
    }

    @Test
    fun `groupConfig present but type DIRECT is not a group conversation`() {
        val stale = conversation(config = GroupConfig(), type = GroupChat.TYPE_DIRECT)
        assertNull(
            groupConversationOperationRejection(stale, ConversationOperation.EditMessage),
        )
    }

    // ---------------- 拒绝原因可读、可判断 ----------------

    @Test
    fun `every rejection reason is non blank and names its own operation`() {
        val group = conversation(config = GroupConfig(), type = GroupChat.TYPE_GROUP)
        val messages = ConversationOperation.entries.map { operation ->
            val reason = groupConversationOperationRejection(group, operation)
            assertNotNull("${operation.name} 缺少拒绝原因", reason)
            reason!!
        }
        assertEquals(
            "五个操作各自的拒绝原因必须不同，否则调用方分不清自己踩了哪个端点",
            messages.size,
            messages.toSet().size,
        )
        messages.forEach { message ->
            assertTrue("拒绝原因不能是空白：'$message'", message.isNotBlank())
            // 一句能看懂的中文原因：至少要说明「为什么被拒」并给出替代入口。
            assertTrue(
                "拒绝原因要指明替代 API，否则调用方无法自己判断换什么：'$message'",
                message.contains("/api/conversations"),
            )
        }
    }

    // ---------------- 抛错形态沿用既有范式 ----------------

    @Test
    fun `require guard throws ConflictException carrying 409 on group conversations`() {
        val group = conversation(config = GroupConfig(), type = GroupChat.TYPE_GROUP)
        ConversationOperation.entries.forEach { operation ->
            try {
                requireConversationOperationAllowed(group, operation)
                fail("${operation.name} 在群聊会话上必须抛错，实际静默放行了")
            } catch (e: ConflictException) {
                // 与 FolderRoutes「Folder has a generating conversation」同范式：
                // 不是请求畸形（400），而是资源当前状态不让做这件事（409）。
                assertEquals(
                    HttpStatusCode.Conflict,
                    e.status,
                )
                assertEquals(e.message, groupConversationOperationRejection(group, operation))
            }
        }
    }

    @Test
    fun `require guard is silent on direct conversations`() {
        val direct = conversation(config = null, type = GroupChat.TYPE_DIRECT)
        ConversationOperation.entries.forEach { operation ->
            requireConversationOperationAllowed(direct, operation)
        }
    }

    private fun conversation(config: GroupConfig?, type: String) = Conversation(
        id = Uuid.random(),
        assistantId = Uuid.random(),
        title = "群",
        messageNodes = emptyList<MessageNode>(),
        type = type,
        groupConfig = config,
    )
}