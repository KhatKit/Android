package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.app.core.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 后台「生成完成」通知标题的取值口径（[resolveNotificationSenderName]）。
 *
 * 这条通知的标题是**唯一**会把模型/助手名字显示到系统通知栏里的地方（消费方
 * `ChatNotificationManager.sendGenerationDoneNotification`：`title = senderName`）。
 * 群聊一轮里每个角色的 `assistant_id` / `model_id` 都可能不同，会话级的值只说明
 * 「这个群属于谁」，不说明「这条回复是谁写的」——所以标题必须按**本轮实际使用**的
 * (assistant, model) 求。
 *
 * 公式本身不是新发明的：它逐字来自 `d61eefde`，那条 commit 的原话是「与消息列表中
 * 头像区域的名称显示逻辑保持一致」，气泡侧那段在 `ChatMessageAvatar.kt:86-131`。
 * 这里钉死的是公式，加上「群聊分支必须重算」这条源码护栏（调用顺序钉不住，就用文本钉）。
 */
class ChatManagerNotificationSenderNameTest {

    private fun senderNameOf(assistant: Assistant, model: Model = Model(modelId = "m")) =
        resolveNotificationSenderName(
            assistant = assistant,
            model = model,
            defaultAssistantName = DEFAULT_NAME,
        )

    // 1. 助手没开 useAssistantAvatar → 显示模型名（气泡头像区显示的也是模型名）
    @Test
    fun withoutAssistantAvatar_showsModelDisplayName() {
        val model = Model(modelId = "gpt-x", displayName = "GPT X")
        assertEquals("GPT X", senderNameOf(Assistant(useAssistantAvatar = false), model))
    }

    // 2. 开了 useAssistantAvatar → 显示助手名，与模型名无关
    @Test
    fun withAssistantAvatar_showsAssistantName_ignoringModel() {
        val model = Model(modelId = "gpt-x", displayName = "GPT X")
        val assistant = Assistant(name = "赛博阿婆", useAssistantAvatar = true)
        assertEquals("赛博阿婆", senderNameOf(assistant, model))
    }

    // 3. 助手名为空 → 落默认文案，不留空白标题（d61eefde 起就有这条 ifEmpty）
    @Test
    fun blankAssistantName_fallsBackToDefault() {
        val assistant = Assistant(name = "", useAssistantAvatar = true)
        assertEquals(DEFAULT_NAME, senderNameOf(assistant))
    }

    // 4. 群聊的核心场景：同一群，三个角色各用一个模型，标题必须各跟各的。
    //    这条就是 B4 那个错位（标题显示会话级那个模型）的判据 —— 只要生成侧还在用
    //    会话级的 (assistant, model) 求值，这条就会得到三个相同的名字。
    @Test
    fun groupTurnTitle_followsTheSpeakerNotTheConversation() {
        val conversationAssistant = Assistant(name = "群主助手", useAssistantAvatar = false)
        val conversationModel = Model(modelId = "conv-model", displayName = "会话级模型")

        val roleA = Assistant(name = "角色A", useAssistantAvatar = false)
        val roleAModel = Model(modelId = "role-a", displayName = "模型A")
        val roleB = Assistant(name = "角色B", useAssistantAvatar = false)
        val roleBModel = Model(modelId = "role-b", displayName = "模型B")
        val roleC = Assistant(name = "角色C", useAssistantAvatar = true)
        val roleCModel = Model(modelId = "role-c", displayName = "模型C")

        val titles = listOf(
            senderNameOf(roleA, roleAModel),
            senderNameOf(roleB, roleBModel),
            senderNameOf(roleC, roleCModel),
        )

        assertEquals(listOf("模型A", "模型B", "角色C"), titles)
        // 会话级那个名字一个都不该出现在这三轮标题里
        assertEquals("会话级模型", senderNameOf(conversationAssistant, conversationModel))
        assertFalse(titles.contains(senderNameOf(conversationAssistant, conversationModel)))
    }

    /**
     * 源码护栏：生成侧必须在群聊分支里**重算** `senderName`，且重算位置在
     * `model = TaskRoutes.resolve(`（群聊分支那一次）之后。
     *
     * 这条路径（`handleMessageComplete`）是 `private suspend` 且要 Hilt 注入的一堆协作者，
     * JVM 单测构造不出来；纯函数只能证明公式，证明不了「调用点在正确的位置」。
     * 文本护栏是这里唯一能在零设备条件下钉住调用顺序的手段（与
     * `ConversationDrawerFolderScopeTest` 同一取舍）。
     */
    @Test
    fun groupBranch_recomputesSenderName_afterResolvingModel() {
        val source = java.io.File(
            "src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt",
        ).readText()

        val groupModelResolve = source.indexOf(
            "                model = TaskRoutes.resolve(",
        )
        val recompute = source.indexOf(
            "senderName = resolveNotificationSenderName(",
            startIndex = groupModelResolve,
        )
        assertTrue("找不到群聊分支里的 model = TaskRoutes.resolve(", groupModelResolve >= 0)
        assertTrue(
            "群聊分支重算 model 之后必须重算 senderName，否则通知标题显示的是会话级模型",
            recompute > groupModelResolve,
        )
        // 两处**赋值**：会话级初值 + 群聊分支重算（定义那一行不算，它是 `fun ` 开头）。
        assertEquals(
            "senderName 应该恰好被 resolveNotificationSenderName 赋值两次",
            2,
            Regex("senderName = resolveNotificationSenderName\\(").findAll(source).count(),
        )
    }

    companion object {
        private const val DEFAULT_NAME = "默认助手"
    }
}