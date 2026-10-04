package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 群聊「导出 Tavern 群聊」入口（[GroupTavernExportCard]）里那些**不依赖 Context / Compose**
 * 的判定与取值口径。
 *
 * 为什么只测这些：[GroupTavernExportCard] 本体是 `KedgeCard` + `LocalContext` +
 * `rememberCoroutineScope`，JVM 单测跑不了；而它真正可能出错、又最难在真机上复现的部分
 * 全在纯逻辑里——消息反查（会不会静默丢、会不会写出重复行）、群名/用户名/文件名/mime 的取值、
 * 角色卡降级、以及「反查全失败时必须报错而不是导出空文件」这条硬约束。所以把这些抽成顶层
 * 函数在这里钉死；**卡片本身的可点性、分享面板是否真的弹出、Toast 文案在真机上的呈现
 * 都没有在这里验证**，见 `GroupExportCard.kt` 的说明。
 *
 * 卡片是否出现的判定直接用 [isGroupConversation]，本文件从导出入口的视角复述一遍它的口径
 * （`GroupTurnCoordinatorTest.kt` 里已有一份同样的断言）。
 */
class GroupTavernExportTest {

    // ---------------- 卡片可见性 ----------------

    @Test
    fun `group export card shows only for strict group conversations`() {
        val group = conversation(
            config = groupConfig(),
            type = GroupChat.TYPE_GROUP,
        )
        assertTrue(
            "群聊会话（group_config 非空 + type=GROUP）必须出现群聊导出卡片",
            isGroupConversation(group),
        )
        // 老数据可能残留 group_config 却已被改回单聊，这时绝不能给用户一个群聊导出入口。
        assertFalse(isGroupConversation(group.copy(type = GroupChat.TYPE_DIRECT)))
        assertFalse(isGroupConversation(group.copy(groupConfig = null)))
    }

    @Test
    fun `group export card shows for a group config that has no roles`() {
        // 判定只看 group_config 与 type，不看角色是否配全：配置没配完也照样给入口，
        // 否则用户在一个刚建好还没加角色的群里连导出文件都做不到。
        assertTrue(
            isGroupConversation(
                conversation(config = GroupConfig(), type = GroupChat.TYPE_GROUP),
            )
        )
    }

    // ---------------- 消息反查 ----------------

    @Test
    fun `every selected message in the conversation is resolved back to its node`() {
        val conversation = conversation(
            nodes = listOf(
                MessageNode.of(message(MessageRole.USER, "hi")),
                MessageNode.of(message(MessageRole.ASSISTANT, "hello", roleId = "ada")),
            ),
        )
        val selected = conversation.messageNodes.map { it.currentMessage }

        val selection = resolveGroupExportNodes(conversation, selected)

        assertEquals(listOf("hi", "hello"), selection.nodes.map { it.currentMessage.toText() })
        assertEquals(0, selection.skipped)
        assertEquals(2, selection.requested)
        assertFalse(selection.isEmpty)
    }

    @Test
    fun `messages that are not in the conversation are skipped and counted, not dropped silently`() {
        val known = message(MessageRole.USER, "in conversation")
        val conversation = conversation(nodes = listOf(MessageNode.of(known)))
        val orphan = message(MessageRole.USER, "not in conversation")

        val selection = resolveGroupExportNodes(conversation, listOf(known, orphan))

        assertEquals(1, selection.nodes.size)
        assertEquals(1, selection.skipped)
        assertEquals(2, selection.requested)
        assertFalse(
            "只要有一条能反查到就必须继续导出，不能整批失败",
            selection.isEmpty,
        )
    }

    @Test
    fun `a selection where nothing resolves is reported as empty so the UI can refuse to export`() {
        val conversation = conversation(nodes = listOf(MessageNode.of(message(MessageRole.USER, "x"))))
        val orphans = List(3) { message(MessageRole.USER, "orphan-$it") }

        val selection = resolveGroupExportNodes(conversation, orphans)

        assertTrue(selection.isEmpty)
        assertEquals(3, selection.skipped)
        assertEquals(3, selection.requested)
        assertEquals(0, selection.nodes.size)
    }

    @Test
    fun `an empty selection resolves to nothing`() {
        val conversation = conversation(nodes = listOf(MessageNode.of(message(MessageRole.USER, "x"))))

        val selection = resolveGroupExportNodes(conversation, emptyList())

        assertTrue(selection.isEmpty)
        assertEquals(0, selection.skipped)
        assertEquals(0, selection.requested)
    }

    @Test
    fun `two selected swipes of the same node are written once not twice`() {
        val first = message(MessageRole.ASSISTANT, "swipe 0", roleId = "ada")
        val second = message(MessageRole.ASSISTANT, "swipe 1", roleId = "ada")
        val node = MessageNode(messages = listOf(first, second), selectIndex = 0)
        val conversation = conversation(nodes = listOf(node))

        val selection = resolveGroupExportNodes(conversation, listOf(first, second))

        assertEquals(
            "同一个节点的多个 swipe 反查到同一个节点，去重后只写一行，" +
                "否则 JSONL 里会出现两条相同的行、往返后变成两条重复消息",
            1,
            selection.nodes.size,
        )
        assertEquals(1, selection.skipped)
    }

    @Test
    fun `resolution keeps the order the user selected`() {
        val conversation = conversation(
            nodes = listOf(
                MessageNode.of(message(MessageRole.USER, "one")),
                MessageNode.of(message(MessageRole.USER, "two")),
                MessageNode.of(message(MessageRole.USER, "three")),
            ),
        )
        val reversed = conversation.messageNodes.reversed().map { it.currentMessage }

        val selection = resolveGroupExportNodes(conversation, reversed)

        assertEquals(
            listOf("three", "two", "one"),
            selection.nodes.map { it.currentMessage.toText() },
        )
    }

    // ---------------- 文件名 / 扩展名 / mime ----------------

    @Test
    fun `export file name follows the same chat-export stamp convention as the other exporters`() {
        val now = LocalDateTime.of(2026, 10, 4, 21, 5, 9)

        assertEquals(
            "chat-export-2026-10-04_21-05-09.jsonl",
            groupExportFileName(now),
        )
    }

    @Test
    fun `export extension and mime type`() {
        assertEquals("jsonl", GROUP_EXPORT_EXTENSION)
        assertTrue(
            "文件名必须以 .jsonl 结尾",
            groupExportFileName(LocalDateTime.of(2026, 1, 2, 3, 4, 5)).endsWith(".jsonl"),
        )
        // 仓库里 JSON 分享 mime 的唯一既有约定是 application/json（ExportHooks.kt:57/:85），
        // 没有 jsonl 专用 mime 的用法；用 application/json 才不会被 share chooser 过滤掉。
        assertEquals("application/json", GROUP_EXPORT_MIME_TYPE)
    }

    // ---------------- 群名 / 用户名 ----------------

    @Test
    fun `group name is the conversation title`() {
        assertEquals(
            "圆桌",
            groupExportName(conversation(title = "圆桌")),
        )
    }

    @Test
    fun `group name falls back when the conversation has no title`() {
        assertEquals(
            GROUP_EXPORT_FALLBACK_NAME,
            groupExportName(conversation(title = "   ")),
        )
    }

    @Test
    fun `user name is the display nickname`() {
        val settings = Settings(
            displaySetting = Settings().displaySetting.copy(userNickname = "阿泽"),
        )
        assertEquals("阿泽", groupExportUserName(settings))
    }

    @Test
    fun `user name falls back to User when the nickname is blank`() {
        val blank = Settings(
            displaySetting = Settings().displaySetting.copy(userNickname = ""),
        )
        assertEquals(GROUP_EXPORT_FALLBACK_USER, groupExportUserName(blank))
        assertEquals(
            "兜底值必须与 TavernMacroTransformer / PromptInjectionTransformer 的 User 同一个",
            "User",
            groupExportUserName(blank),
        )
    }

    // ---------------- 角色卡降级 ----------------

    @Test
    fun `role cards take name and persona from the bound assistant`() {
        val assistantId = kotlin.uuid.Uuid.random()
        val settings = Settings(
            assistants = listOf(
                Assistant(
                    id = assistantId,
                    name = "艾达",
                    systemPrompt = "你是艾达。",
                )
            )
        )
        // role.name 为空 -> 退回助手名；persona 取助手 systemPrompt。
        val cards = groupExportRoleCards(
            listOf(GroupRole(id = "ada", name = "", assistantId = assistantId.toString())),
            settings,
        )

        assertEquals(1, cards.size)
        assertEquals("ada", cards[0].roleId)
        assertEquals("艾达", cards[0].name)
        assertEquals("你是艾达。", cards[0].persona)
        assertEquals(assistantId.toString(), cards[0].assistantId)
        assertNull(
            "头像存的是本地 URI / 远程 URL，不是可移植引用，恒为 null",
            cards[0].avatarRef,
        )
    }

    @Test
    fun `role cards pass cardId and role name through without touching them`() {
        val cards = groupExportRoleCards(
            listOf(GroupRole(id = "ada", name = "阿达", cardId = "card-7")),
            Settings(),
        )

        assertEquals("阿达", cards[0].name)
        assertEquals("card-7", cards[0].cardId)
        assertEquals("", cards[0].assistantId)
    }

    @Test
    fun `role cards still export when the assistant is missing or unparseable`() {
        val settings = Settings(assistants = listOf(Assistant(name = "别人")))

        val deleted = groupExportRoleCards(
            listOf(GroupRole(id = "a", name = "甲", assistantId = kotlin.uuid.Uuid.random().toString())),
            settings,
        )
        val garbage = groupExportRoleCards(
            listOf(GroupRole(id = "b", name = "乙", assistantId = "not-a-uuid")),
            settings,
        )

        assertEquals(1, deleted.size)
        assertEquals("甲", deleted[0].name)
        assertEquals("", deleted[0].persona)
        assertEquals(1, garbage.size)
        assertEquals("乙", garbage[0].name)
        assertEquals("", garbage[0].persona)
    }

    @Test
    fun `role cards fall back to the assistant name when the role itself is unnamed`() {
        val assistantId = kotlin.uuid.Uuid.random()
        val settings = Settings(assistants = listOf(Assistant(id = assistantId, name = "无名角色")))

        val cards = groupExportRoleCards(
            listOf(GroupRole(id = "anon", name = "", assistantId = assistantId.toString())),
            settings,
        )

        assertEquals("无名角色", cards[0].name)
    }

    @Test
    fun `role cards keep one entry per role even when there are no roles`() {
        assertEquals(
            emptyList<RoleCardMeta>(),
            groupExportRoleCards(emptyList(), Settings()),
        )
    }

    // ---------------- 提示文案 ----------------

    @Test
    fun `the all-failed message names how many messages could not be resolved`() {
        val selection = GroupExportSelection(nodes = emptyList(), skipped = 4)

        val message = groupExportAllFailedMessage(selection)

        assertTrue(message.contains("4"))
        assertTrue(message.contains("无法导出"))
    }

    @Test
    fun `the success message reports the skipped count only when something was skipped`() {
        val clean = GroupExportSelection(nodes = List(3) { MessageNode.of(message(MessageRole.USER, "m$it")) }, skipped = 0)
        val partial = GroupExportSelection(nodes = clean.nodes, skipped = 2)

        assertFalse(
            "没有跳过时不该提「跳过」，否则用户会以为漏了东西",
            groupExportSuccessMessage(clean).contains("跳过"),
        )
        assertTrue(groupExportSuccessMessage(clean).contains("3"))
        assertTrue(groupExportSuccessMessage(partial).contains("跳过"))
        assertTrue(groupExportSuccessMessage(partial).contains("2"))
    }

    // ---------------- 端到端：从面板选中到可回导的文件 ----------------

    @Test
    fun `the ui path produces a file that imports back with roles rounds and config intact`() {
        // 必须用 groupConfig() 里绑定的那个 assistant id，否则角色卡取不到助手、persona 为空。
        val settings = Settings(
            displaySetting = Settings().displaySetting.copy(userNickname = "阿泽"),
            assistants = listOf(
                Assistant(id = ASSISTANT_ADA, name = "艾达", systemPrompt = "你是艾达。"),
                Assistant(id = ASSISTANT_BOB, name = "鲍勃", systemPrompt = "你是鲍勃。"),
            )
        )
        val config = groupConfig()
        val conversation = conversation(
            title = "圆桌",
            config = config,
            type = GroupChat.TYPE_GROUP,
            nodes = listOf(
                MessageNode.of(message(MessageRole.USER, "开始吧")),
                MessageNode.of(
                    message(
                        MessageRole.ASSISTANT, "我来", roleId = "ada", roundId = "round-1",
                    )
                ),
                MessageNode.of(
                    message(
                        MessageRole.ASSISTANT, "我也来", roleId = "bob", roundId = "round-1",
                        turnKind = GroupChat.TURN_CHAIR, mentions = listOf("ada"),
                    )
                ),
            ),
        )
        val selected = conversation.messageNodes.map { it.currentMessage }
        val selection = resolveGroupExportNodes(conversation, selected)

        val jsonl = TavernChatCodec.exportGroupJsonl(
            nodes = selection.nodes,
            config = config,
            cards = groupExportRoleCards(config.roles, settings),
            userName = groupExportUserName(settings),
            groupName = groupExportName(conversation),
        )

        // 面板写文件时就是一个字节数组，编码固定 UTF-8，这里按同样口径读回来。
        val restored = requireNotNull(
            TavernChatCodec.importGroup(jsonl.toByteArray().decodeToString()),
        ) { "面板导出的文件必须能被 importGroup 认回来，否则 C1-09 往返不成立" }
        assertEquals("圆桌", restored.groupName)
        assertEquals("阿泽", restored.userName)
        assertEquals(listOf("艾达", "鲍勃"), restored.characterNames)
        assertEquals("ada", restored.messages[1].roleId)
        assertEquals("round-1", restored.messages[1].roundId)
        assertEquals(GroupChat.TURN_CHAIR, restored.messages[2].turnKind)
        assertEquals(listOf("ada"), restored.messages[2].mentionRoleIds)
        assertEquals("你是艾达。", restored.cards.first { it.roleId == "ada" }.persona)
        assertEquals("艾达", restored.cards.first { it.roleId == "ada" }.name)
        assertEquals(config.roles.map { it.id }, restored.config?.roles?.map { it.id })
    }

    // ---------------- fixtures ----------------

    private fun conversation(
        title: String = "群",
        config: GroupConfig? = null,
        type: String = GroupChat.TYPE_GROUP,
        nodes: List<MessageNode> = emptyList(),
    ) = Conversation(
        assistantId = kotlin.uuid.Uuid.random(),
        title = title,
        messageNodes = nodes,
        type = type,
        groupConfig = config,
    )

    private fun groupConfig() = GroupConfig(
        roles = listOf(
            GroupRole(id = "ada", name = "艾达", assistantId = ASSISTANT_ADA.toString(), chair = true),
            GroupRole(id = "bob", name = "鲍勃", assistantId = ASSISTANT_BOB.toString()),
        ),
        mode = GroupChat.MODE_ROUNDTABLE,
        chairRoleId = "ada",
        tokenBudgetPerRound = 4096,
    )

    private fun message(
        role: MessageRole,
        text: String,
        roleId: String? = null,
        roundId: String? = null,
        turnKind: String? = null,
        mentions: List<String> = emptyList(),
    ) = UIMessage(
        role = role,
        parts = listOf(UIMessagePart.Text(text)),
        roleId = roleId,
        roundId = roundId,
        turnKind = turnKind,
        mentionRoleIds = mentions,
    )

    private companion object {
        val ASSISTANT_ADA = kotlin.uuid.Uuid.random()
        val ASSISTANT_BOB = kotlin.uuid.Uuid.random()
    }
}