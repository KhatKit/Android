package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [resolveMessageModel] 的用例——群聊气泡上「模型名 / 模型图标 / 更多面板里的模型行」用哪个模型。
 *
 * ## 口径：消息自己记的调用记录优先
 *
 * 这个函数回答「**这条消息当时是被哪个模型答的**」。`message.modelId` 是生成侧写进去的
 * **实际**调用模型（`GenerationLoop.kt:465` 写 `modelId = model.id`），而 `GroupRole.modelId`
 * 是**用户可改的配置**——今天的绑定不等于当初那次调用。三条，全部在这里钉死：
 *
 * 1. `messageModelId` 非 null 就用它，**角色绑定不能覆盖它**。`41642ecd` 之前生成的群聊
 *    消息记的是助手绑的模型，让角色绑定优先就会给这批历史消息显示一个当时没被调用过的
 *    模型——那是本文件第 1 条用例钉的 bug；
 * 2. 它指向的模型已被删掉就返回 **null**，不拿角色绑定顶替：确知有模型答过这条消息、只是
 *    叫不出名字，比显示一个编出来的名字诚实；
 * 3. 只有 `messageModelId` 为 null 时才回落角色绑定，兜底的四种坏配置（null / 空串 /
 *    非法 Uuid / 指向已删模型）都返回 null。这条分支在群聊里**不是死代码**：
 *    `GroupTurnCoordinator.errorNode`（`:368`）造的失败节点带着真实 `roleId` 却没有
 *    `modelId`（见 `a group failure node with no recorded model falls back to the role`）。
 *
 * 单聊（`role == null`）逐字等价于 `messageModelId?.let(modelById::get)`，由
 * `the single chat resolution is verbatim for every shape of the message model id` 穷举。
 *
 * 与生成侧的分工见 [GroupTurnModelTest]：那边回答「现在要用哪个模型」，是前瞻；这边回答
 * 「当时用了哪个模型」，是回溯。两侧判据**故意**不一致，别再合并。
 */
class GroupMessageModelTest {

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 角色当前绑着的模型。 */
    private val boundModel = Model(modelId = "role-bound-model")

    /** 消息自己记的调用模型（生成侧写进去的那个）。 */
    private val messageModel = Model(modelId = "message-model")

    /** 另一个真实存在的模型，给「角色绑了另一个」用。 */
    private val unknownModel = Model(modelId = "removed-model")

    /** 曾经存在、现在配置里查不到的模型。 */
    private val goneModelId: Uuid = Uuid.random()

    private val modelById: Map<Uuid, Model> = mapOf(
        boundModel.id to boundModel,
        messageModel.id to messageModel,
        unknownModel.id to unknownModel,
    )

    private fun role(modelId: String?, roleId: String = "alice") =
        GroupRole(id = roleId, name = "爱丽丝", assistantId = "asst-1", modelId = modelId)

    private fun resolve(messageModelId: Uuid?, role: GroupRole?) =
        resolveMessageModel(messageModelId, role, modelById)

    // ------------------------------------------------------------------
    // 1. 消息自己记的调用记录优先于角色绑定（本轮修的 bug 就在这里）
    // ------------------------------------------------------------------

    @Test
    fun `the message model id wins over the role binding`() {
        // 这就是 bug 场景：这条消息当时是助手绑的 `messageModel` 答的（`41642ecd` 之前生成侧
        // 只读 `assistant.chatModelId`），而角色现在绑的是 `boundModel`。
        // 显示侧必须显示 `messageModel`——显示 `boundModel` 就是显示一个当时没被调用过的模型。
        assertSame(messageModel, resolve(messageModel.id, role(boundModel.id.toString())))
    }

    @Test
    fun `role model id is used even when the message carries no model id`() {
        assertSame(boundModel, resolve(null, role(boundModel.id.toString())))
    }

    // ------------------------------------------------------------------
    // 2. 角色绑定只在消息**没有**记录时才被采纳
    // ------------------------------------------------------------------

    @Test
    fun `the message model id is used when the role has no model binding`() {
        assertSame(messageModel, resolve(messageModel.id, role(null)))
    }

    @Test
    fun `a blank role model id is not parsed into garbage`() {
        // messageModelId 为 null 时才有角色绑定这条分支；空串 / 纯空白都不算绑定 → null。
        assertNull(resolve(null, role("")))
        assertNull(resolve(null, role("   ")))
    }

    @Test
    fun `an unparseable role model id resolves to null`() {
        assertNull(resolve(null, role("not-a-uuid")))
    }

    @Test
    fun `a role model id pointing at a deleted model resolves to null`() {
        assertNull(resolve(null, role(goneModelId.toString())))
    }

    @Test
    fun `a broken role binding never invents a model name`() {
        // 四种坏配置 × 有无 message.modelId：绑不上时给 null，一个坏 id 不许编出模型名；
        // 有记录时给记录，一个坏 id 也不许把记录顶掉。
        val brokenRoles = listOf(null, "", "   ", "not-a-uuid", goneModelId.toString())

        brokenRoles.forEach { raw ->
            assertNull("raw=$raw 无记录时应为 null", resolve(null, role(raw)))
            assertSame("raw=$raw 有记录时应显示记录", messageModel, resolve(messageModel.id, role(raw)))
        }
    }

    // ------------------------------------------------------------------
    // 3. 单聊路径逐字不变
    // ------------------------------------------------------------------

    @Test
    fun `a message with no role keeps the single chat resolution`() {
        // role == null 就是单聊（以及群聊里用户自己那条没盖角色戳的消息）。
        assertSame(messageModel, resolve(messageModel.id, null))
        assertNull(resolve(null, null))
    }

    @Test
    fun `a single chat model id that no longer exists resolves to null like before`() {
        // C1 之前是 `message.modelId?.let(modelById::get)`：查不到就是 null，不回落、不报错。
        // 群聊侧现在也是同一个决定（见下一条），单群一致。
        assertNull(resolve(goneModelId, null))
    }

    // ------------------------------------------------------------------
    // 与群配置的对齐：角色查找的键是 role_id
    // ------------------------------------------------------------------

    @Test
    fun `the role model id is picked by role id from the group config`() {
        // 调用点按 `speaker.roleId` 去 `config.roles` 里取角色；这里钉死「取到的那个角色
        // 的 model_id 才算数」，不会串到别的角色身上。
        val config = GroupConfig(
            roles = listOf(
                role(boundModel.id.toString(), roleId = "alice"),
                role(unknownModel.id.toString(), roleId = "bob"),
            ),
            mode = GroupChat.MODE_PIPELINE,
            tokenBudgetPerRound = 1000,
        )
        val bob = config.roles.first { it.id == "bob" }

        // 无记录时各回各的：bob 绑 unknownModel，不串到 alice 的 boundModel。
        assertSame(unknownModel, resolve(null, bob))
        assertSame(boundModel, resolve(null, config.roles.first { it.id == "alice" }))
        // 有记录时两条都显示记录。
        assertSame(messageModel, resolve(messageModel.id, bob))
        assertSame(messageModel, resolve(messageModel.id, config.roles.first { it.id == "alice" }))
    }

    // ------------------------------------------------------------------
    // 4. 这一轮真正要修的场景
    // ------------------------------------------------------------------

    @Test
    fun `the recorded model survives a later rebinding of the role`() {
        // 用户在消息生成之后改了角色绑定：老消息必须继续显示当时那个模型。
        // 这一条与 `the message model id wins over the role binding` 的差别在于它钉的是
        // 「时间顺序」——记录在先、改绑定在后，两者今天恰好相等，但下一次发言就会分叉。
        val thenModel = Model(modelId = "assistant-bound-at-the-time")
        val nowModel = Model(modelId = "role-bound-afterwards")
        val index = mapOf(thenModel.id to thenModel, nowModel.id to nowModel)

        val shown = resolveMessageModel(thenModel.id, role(nowModel.id.toString()), index)

        assertSame("老消息显示当时的调用模型，不是今天的新绑定", thenModel, shown)
        assertNotEquals(nowModel.id, shown?.id)
    }

    @Test
    fun `a message model id naming a deleted model is not replaced by the role binding`() {
        // 决定：查不到就是查不到，不回落角色绑定。
        // 我们确知有个模型答过这条消息（记录在），只是它被删了；拿今天角色绑的模型填上去
        // 等于再造一条同样的假记录——这正是本轮要修的那类错。
        assertNull(resolve(goneModelId, role(boundModel.id.toString())))
    }

    @Test
    fun `two messages from one role show their own recorded models`() {
        // 同一个角色、同一个绑定，两条消息各自记录了不同模型（改过绑定、或路由降级过）：
        // 显示必须各归各，不能一律用角色绑定把它们抹平成同一个。
        val first = Model(modelId = "recorded-first")
        val second = Model(modelId = "recorded-second")
        val index = mapOf(first.id to first, second.id to second)
        val speaker = role(boundModel.id.toString())

        assertSame(first, resolveMessageModel(first.id, speaker, index))
        assertSame(second, resolveMessageModel(second.id, speaker, index))
    }

    @Test
    fun `the single chat resolution is verbatim for every shape of the message model id`() {
        // 硬约束：**role == null 时**逐字等价于 C1 之前那句
        // `messageModelId?.let(modelById::get)`。这里穷举 messageModelId 的每种形状
        // （null、指向三个存在的模型、指向已删模型），断言新旧两式按值相等，
        // 而不只是「都不抛异常」。
        val messageIds = listOf<Uuid?>(null, messageModel.id, boundModel.id, unknownModel.id, goneModelId)

        messageIds.forEach { messageModelId ->
            assertEquals(
                "messageModelId=$messageModelId",
                messageModelId?.let(modelById::get),
                resolveMessageModel(messageModelId, null, modelById),
            )
        }
    }

    @Test
    fun `a non null role deliberately breaks that verbatim equivalence`() {
        // 与上一条互为反面：role 非 null 且消息**没有**记录时，显示侧会去读角色绑定，
        // 于是结果**不等于** `messageModelId?.let(modelById::get)`（那是 null）。
        // 这条差异就是群聊兜底分支本身，把它钉住，免得有人日后把兜底删掉「对齐」回去。
        val legacySingleChatExpression: Uuid? = null

        assertEquals(null, legacySingleChatExpression?.let(modelById::get))
        assertNotEquals(
            legacySingleChatExpression?.let(modelById::get),
            resolveMessageModel(legacySingleChatExpression, role(boundModel.id.toString()), modelById),
        )
    }

    @Test
    fun `an unparseable role model id never wins over the record and never throws`() {
        // 角色配置是用户可写的字符串，「不是合法 Uuid」是常态。`messageModelId` 是 `Uuid?`
        // 类型、装不下非法字符串，所以「非法」这一关只存在于角色侧——这里两侧都覆盖：
        // 有记录时非法角色 id 不许顶掉记录，无记录时合法地得到 null。
        listOf("not-a-uuid", "123", "  ", "", goneModelId.toString()).forEach { raw ->
            assertSame("raw=$raw", messageModel, resolve(messageModel.id, role(raw)))
            assertNull("raw=$raw", resolve(null, role(raw)))
        }
    }

    @Test
    fun `a group failure node with no recorded model falls back to the role`() {
        // 兜底分支在群聊里真的会被走到，不是死代码：`GroupTurnCoordinator.errorNode`
        // （`:368`）造的失败节点带真实 `roleId`、却没有 `modelId`。
        assertSame(boundModel, resolve(null, role(boundModel.id.toString())))
    }

    @Test
    fun `an unresolvable speaker role does not fall back to a sibling role binding`() {
        // 群聊里 `role` 查不到（用户自己那条没盖角色戳的消息、`__summary__` 合成节点、
        // 配置里已删掉的角色）时 role 为 null，走的就是单聊那一句。
        assertSame(messageModel, resolve(messageModel.id, null))
        assertNull(resolve(goneModelId, null))
    }
}
