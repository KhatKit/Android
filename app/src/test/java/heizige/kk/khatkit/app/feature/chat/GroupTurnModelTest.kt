package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [resolveGroupTurnModelId] 的用例——群聊本轮发言**实际调用**哪个模型。
 *
 * 这是 [resolveMessageModel]（显示侧，[ChatList.kt]）的调用侧孪生：两条判据必须一模一样，
 * 否则用户给角色绑了模型 X 就会看到「气泡写 X、实际发 Y」。`GroupMessageModelTest` 钉死
 * 显示侧，本文件钉死调用侧，两者一起构成 C1-02「实际模型调用序列与日志一致」的前置条件。
 *
 * 口径只有三条：
 * 1. 角色绑了合法且仍存在的 `model_id` 就用它（哪怕消息/助手绑的是另一个）；
 * 2. null / 空串 / 纯空白 / 非法 Uuid / 指向已删模型，五种都**逐字**回落助手 `chatModelId`，
 *    一个坏配置不许让整轮生成失败，也不许改变 C1 之前的路由行为；
 * 3. 单聊（`role == null`）返回的就是 `assistantChatModelId` 本身，与改动前那行
 *    `TaskRoutes.resolve(settings, CHAT, assistant.chatModelId)` 逐字等价。
 *
 * 纯 JVM：不碰 Compose / Hilt / 数据库，模型"存在性"用夹具里的 id 集合冒充
 * （`ChatManager` 那边注入的是 `settings.findModelById(it) != null`）。
 */
class GroupTurnModelTest {

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 角色绑定的模型。 */
    private val roleModel = Uuid.random()

    /** 助手绑定的模型（回落目标）。 */
    private val assistantModel = Uuid.random()

    /** "已被删掉"的模型：曾经存在，现在配置里查不到了。 */
    private val deletedModel = Uuid.random()

    /** 当前真实存在的模型集合，等价于 `settings.providers.flatMap { it.models }.map { it.id }`。 */
    private val knownModels: Set<Uuid> = setOf(roleModel, assistantModel)

    private val isKnownModel: (Uuid) -> Boolean = knownModels::contains

    private fun role(modelId: String?, roleId: String = "alice") =
        GroupRole(id = roleId, name = "爱丽丝", assistantId = "asst-1", modelId = modelId)

    private fun resolve(role: GroupRole?, assistantChatModelId: Uuid? = assistantModel) =
        resolveGroupTurnModelId(
            role = role,
            assistantChatModelId = assistantChatModelId,
            isKnownModel = isKnownModel,
        )

    // ------------------------------------------------------------------
    // 1. 角色有合法且存在的 modelId → 选中它
    // ------------------------------------------------------------------

    @Test
    fun `a valid existing role model id is the one that gets called`() {
        assertEquals(
            roleModel,
            resolve(role(roleModel.toString())),
        )
    }

    // ------------------------------------------------------------------
    // 2. 角色绑了 X 时，即使助手/消息侧记的是另一个，也仍然用 X
    //    （显示侧与调用侧一致的关键断言）
    // ------------------------------------------------------------------

    @Test
    fun `the role binding wins over whatever the assistant was bound to`() {
        // 助手 chatModelId 指向另一个真实存在的模型：角色绑定仍然优先。
        // 这条与显示侧的 `role model id wins over the message model id` 是同一个口径的两面。
        assertEquals(roleModel, resolve(role(roleModel.toString()), assistantChatModelId = assistantModel))
    }

    @Test
    fun `the role binding is used even when the assistant has no model bound at all`() {
        assertEquals(roleModel, resolve(role(roleModel.toString()), assistantChatModelId = null))
    }

    // ------------------------------------------------------------------
    // 3~6. 五种绑不上的情况，一律回落助手 chatModelId
    // ------------------------------------------------------------------

    @Test
    fun `a null role model id falls back to the assistant model`() {
        assertEquals(assistantModel, resolve(role(modelId = null)))
    }

    @Test
    fun `an empty or blank role model id falls back to the assistant model`() {
        assertEquals(assistantModel, resolve(role(modelId = "")))
        assertEquals(assistantModel, resolve(role(modelId = "   ")))
        assertEquals(assistantModel, resolve(role(modelId = "\t\n ")))
    }

    @Test
    fun `an unparseable role model id falls back instead of throwing`() {
        // 配置是用户可写的字符串，"不是合法 Uuid" 是常态而不是异常。
        listOf("not-a-uuid", "123", "role-x", "  ${roleModel}  ", roleModel.toString().dropLast(1))
            .forEach { raw ->
                assertEquals("raw=$raw", assistantModel, resolve(role(raw)))
            }
    }

    @Test
    fun `a role model id pointing at a deleted model falls back to the assistant model`() {
        // 模型被删后 id 仍留在角色配置里：必须回落，而不是把一个查不到的 id 交给路由。
        assertEquals(assistantModel, resolve(role(deletedModel.toString())))
    }

    // ------------------------------------------------------------------
    // 7. 助手也没绑 / 助手绑的模型已删：不抛异常，且不动原值
    // ------------------------------------------------------------------

    @Test
    fun `no assistant model and no role binding resolves to null instead of throwing`() {
        // C1 之前传进去的就是 null：`TaskRoutes.resolve` 收到 null 会按 `settings.chatModelId`
        // 自己选默认模型。所以"返回 null"就是**保持原行为**，不是新增兜底。
        assertNull(resolve(role(modelId = null), assistantChatModelId = null))
        assertNull(resolve(role(modelId = ""), assistantChatModelId = null))
        assertNull(resolve(role(modelId = "not-a-uuid"), assistantChatModelId = null))
        assertNull(resolve(role(modelId = deletedModel.toString()), assistantChatModelId = null))
    }

    @Test
    fun `a stale assistant model id is passed through untouched so routing is unchanged`() {
        // 助手这个 id 也查不到模型时**不改写**它：C1 之前就是原样交给 TaskRoutes 的，
        // 由 taskBinding / router 决定回落（会退回 `settings.chatModelId` 与各 provider 候选）。
        // 这里要是"顺手"改成 null，就等于换掉了路由结果——那是未经要求的行为变更。
        val staleAssistant = Uuid.random()

        assertEquals(staleAssistant, resolve(role(modelId = null), assistantChatModelId = staleAssistant))
        assertEquals(staleAssistant, resolve(role(modelId = "not-a-uuid"), assistantChatModelId = staleAssistant))
    }

    // ------------------------------------------------------------------
    // 8. 四种异常 × 有/无助手回落，穷举且不抛异常
    // ------------------------------------------------------------------

    @Test
    fun `every broken role binding survives with and without an assistant fallback`() {
        val broken = listOf(null, "", "   ", "not-a-uuid", deletedModel.toString())

        broken.forEach { raw ->
            val role = role(raw)
            // 有助手回落：一律拿到助手那个 id
            assertEquals("raw=$raw", assistantModel, resolve(role, assistantChatModelId = assistantModel))
            // 无助手回落：合法地得到 null，且上面每一次调用都没有抛异常
            assertNull("raw=$raw", resolve(role, assistantChatModelId = null))
        }
    }

    // ------------------------------------------------------------------
    // 9. 单聊路径逐字等价
    // ------------------------------------------------------------------

    @Test
    fun `no role keeps the single chat resolution verbatim`() {
        // 单聊（以及群聊分支之外的任何路径）传进来的就是 assistant.chatModelId 本身，
        // 与改动前 `TaskRoutes.resolve(settings, CHAT, assistant.chatModelId)` 一致。
        assertEquals(assistantModel, resolve(role = null))
        assertEquals(assistantModel, resolve(role = null, assistantChatModelId = assistantModel))
        assertEquals(deletedModel, resolve(role = null, assistantChatModelId = deletedModel))
        assertNull(resolve(role = null, assistantChatModelId = null))
    }

    // ------------------------------------------------------------------
    // 角色查找的键是 role_id（与显示侧同一份 GroupConfig）
    // ------------------------------------------------------------------

    @Test
    fun `the model binding comes from the role that is actually speaking`() {
        // 调用点传的是 `step.role`，也就是这一轮**真正发言**的那个角色：不能串到别人身上。
        val config = GroupConfig(
            roles = listOf(
                role(roleModel.toString(), roleId = "alice"),
                role(assistantModel.toString(), roleId = "bob"),
                role(modelId = null, roleId = "carol"),
            ),
            mode = GroupChat.MODE_PIPELINE,
            tokenBudgetPerRound = 1000,
        )

        assertEquals(roleModel, resolve(config.roles.first { it.id == "alice" }))
        assertEquals(assistantModel, resolve(config.roles.first { it.id == "bob" }))
        assertEquals(assistantModel, resolve(config.roles.first { it.id == "carol" }))
    }

    @Test
    fun `the existence check decides whether the role binding is adopted`() {
        // 存在性判据来自注入的 `isKnownModel`：同一个非空合法 id，判据说有就用、说没有就回落。
        // 防止"只看非空就采用"——模型被删后 id 仍留在角色配置里，那是最容易漏的一种。
        val probe = Uuid.random()
        val role = role(probe.toString())

        assertEquals(
            probe,
            resolveGroupTurnModelId(role, assistantModel) { it == probe },
        )
        assertEquals(
            assistantModel,
            resolveGroupTurnModelId(role, assistantModel) { false },
        )
    }

    // ------------------------------------------------------------------
    // 与显示侧的逐条对照：同一组角色配置，两侧必须给出同一个模型
    // ------------------------------------------------------------------

    @Test
    fun `the calling side agrees with the display side on the same group config`() {
        // 这是整件事的目的。展示 `resolveMessageModel(messageModelId, role, modelById)` 的结果，
        // 对同一份配置与同一条消息，比较「气泡显示的模型」与「实际调用的模型」。
        val boundModel = Model(modelId = "role-bound-model")
        val assistantBound = Model(modelId = "assistant-bound-model")
        val modelById = mapOf(boundModel.id to boundModel, assistantBound.id to assistantBound)
        val config = GroupConfig(
            roles = listOf(
                role(boundModel.id.toString(), roleId = "alice"),
                role(modelId = null, roleId = "bob"),
                role(modelId = "not-a-uuid", roleId = "carol"),
                role(Uuid.random().toString(), roleId = "dave"),
            ),
            mode = GroupChat.MODE_ROUNDTABLE,
            tokenBudgetPerRound = 1000,
        )

        config.roles.forEach { speaker ->
            val shown = resolveMessageModel(assistantBound.id, speaker, modelById)
            val called = resolveGroupTurnModelId(speaker, assistantBound.id, isKnownModel = { it in modelById })

            assertEquals(
                "role=${speaker.id} 显示侧与调用侧必须一致",
                shown?.id,
                called,
            )
        }
    }
}