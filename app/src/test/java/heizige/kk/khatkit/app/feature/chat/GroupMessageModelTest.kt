package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [resolveMessageModel] 的用例——群聊气泡上「模型名 / 模型图标 / 更多面板里的模型行」用哪个模型。
 *
 * 口径只有三条，全部在这里钉死：
 * 1. 角色绑了 `model_id` 就用角色那个（群配置的 `roles[]` 里「模型绑定」这条契约的落点）；
 * 2. 角色没绑、绑的是空串 / 非法 Uuid / 一个已被删掉的模型，四种都回落到消息自己记的
 *    `modelId`，不能让一个坏配置把模型名整个抹掉；
 * 3. 单聊（`role == null`）的结果与 C1 之前那句 `messageModelId?.let(modelById::get)`
 *    逐字等价。
 *
 * ⚠️ 已知口径边界（**不是**这个函数的 bug）：生成侧目前按 `assistant.chatModelId` 选模型
 * （`ChatManager.takeGroupTurn` 那段），还没读 `role.modelId`。所以在生成侧接上之前，
 * 「角色绑了另一个模型」时气泡显示的是角色绑定、而 `message.modelId` 记的是实际调用模型，
 * 两者可能不同。这里只保证**显示口径**本身确定且可测。
 */
class GroupMessageModelTest {

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private val boundModel = Model(modelId = "role-bound-model")
    private val messageModel = Model(modelId = "message-model")
    private val unknownModel = Model(modelId = "removed-model")

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
    // 1. 角色有 modelId 时用它
    // ------------------------------------------------------------------

    @Test
    fun `role model id wins over the message model id`() {
        val resolved = resolve(messageModel.id, role(boundModel.id.toString()))

        assertSame(boundModel, resolved)
    }

    @Test
    fun `role model id is used even when the message carries no model id`() {
        assertSame(boundModel, resolve(null, role(boundModel.id.toString())))
    }

    // ------------------------------------------------------------------
    // 2. 角色没有绑定时回落到 message.modelId
    // ------------------------------------------------------------------

    @Test
    fun `falls back to the message model id when the role has no model binding`() {
        assertSame(messageModel, resolve(messageModel.id, role(null)))
    }

    @Test
    fun `a blank role model id falls back instead of parsing to garbage`() {
        assertSame(messageModel, resolve(messageModel.id, role("")))
        assertSame(messageModel, resolve(messageModel.id, role("   ")))
    }

    @Test
    fun `an unparseable role model id falls back`() {
        assertSame(messageModel, resolve(messageModel.id, role("not-a-uuid")))
    }

    @Test
    fun `a role model id pointing at a deleted model falls back`() {
        val gone = Uuid.random()

        assertSame(messageModel, resolve(messageModel.id, role(gone.toString())))
    }

    @Test
    fun `a broken role binding never hides the message model`() {
        // 四种坏配置 × 有无 message.modelId：一个坏 id 都不许把模型名整个抹掉。
        val brokenRoles = listOf(null, "", "   ", "not-a-uuid", Uuid.random().toString())

        brokenRoles.forEach { raw ->
            assertSame(messageModel, resolve(messageModel.id, role(raw)))
        }

        val withoutMessageModel = resolve(null, role("not-a-uuid"))

        assertNull(withoutMessageModel)
    }

    // ------------------------------------------------------------------
    // 3. 单聊路径不受影响
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
        assertNull(resolve(Uuid.random(), null))
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

        assertSame(unknownModel, resolve(messageModel.id, bob))
        assertSame(boundModel, resolve(messageModel.id, config.roles.first { it.id == "alice" }))
    }
}
