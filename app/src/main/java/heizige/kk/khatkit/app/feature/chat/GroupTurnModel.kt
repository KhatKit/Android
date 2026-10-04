package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.data.model.GroupRole
import kotlin.uuid.Uuid

/**
 * 生成侧「本轮发言**要用哪个**模型」的判定：**角色绑定优先，绑不上回落助手**。
 *
 * ## 为什么要有这个函数
 *
 * C1 契约里 `roles[]` 的最小 schema 含「模型绑定」，落点就是 [GroupRole.modelId]。
 * 但生成侧（`ChatManager.handleMessageComplete` 的群聊分支）此前只读
 * `assistant.chatModelId`，`role.modelId` 根本没进模型解析；而显示侧
 * （`ChatList.kt` 的 [resolveMessageModel]）当时是角色绑定优先。两边口径不一致的后果是：
 * 用户给角色 A 绑了模型 X，气泡上写 X，实际发出去的请求走的是助手绑的 Y。
 * C1-02 的验收标准是「实际模型调用序列与日志一致」，按那个状态过不了。
 * 本函数就是把这半个缺口补上：**调用侧**按角色绑定选模型。
 *
 * ## 与显示侧是两件事，不要再合并
 *
 * 本函数回答「**现在要用哪个模型**」（前瞻），[resolveMessageModel] 回答
 * 「**这条消息当时是被哪个模型答的**」（回溯）。两者在群聊里**必然**会分叉，且分叉是
 * 正确的，不是不一致：
 *
 * - 本函数的结果经 `TaskRoutes.resolve` 落到 `GenerationLoop`，被写进
 *   `message.modelId`（`GenerationLoop.kt:465`）——那才是显示侧要读的**记录**。
 * - 用户改 `GroupRole.modelId` 之后，本函数下一次发言就用新模型，而历史消息的
 *   `message.modelId` 保持不动。显示侧读记录，于是老消息显示老模型，正确。
 * - 反过来，`41642ecd`（本函数落地）之前生成的群聊消息，`message.modelId` 记的是**助手**
 *   绑的模型。显示侧若按角色绑定优先，就会给这批历史消息显示一个当时没被调用过的模型——
 *   所以 `c7535ca8` 把显示侧翻成 `message.modelId` 优先。两侧判据**故意**不再逐条对齐。
 *
 * ## 判据
 *
 * 角色的 `model_id` 依次通过三关才被采用：
 * 1. 非 null 且 `isNotBlank()`（空串 / 纯空白不算绑定）；
 * 2. 是合法 [Uuid]（配置是用户可写的字符串，不能假设它合法）；
 * 3. [isKnownModel] 说这个模型**当前真实存在**（模型被删掉后 id 仍留在角色配置里）。
 *
 * 任何一关不过就回落 [assistantChatModelId]，**逐字**回落：C1 之前就是把它原样交给
 * `TaskRoutes.resolve` 的，所以回落路径的路由、预算、冷却、cost-rank 降级与计数行为
 * 一个字节都不变。这里**不**因为「助手这个 id 也解析不出模型」就改成别的值——那会悄悄
 * 换掉路由结果，属于未经要求的行为变更。
 *
 * ## 单聊完全不受影响
 *
 * [role] 为 null（单聊，以及群聊分支之外的任何路径）时结果就是 [assistantChatModelId] 本身，
 * 与 C1 之前 `TaskRoutes.resolve(settings, ModelTaskType.CHAT, assistant.chatModelId)`
 * 传进去的值逐字等价。调用点只在 `step != null && groupConfig != null` 的群聊分支里传非空
 * role，单聊根本走不到这里。
 *
 * ## 为什么不直接在这里返回 `Model`
 *
 * [heizige.kk.khatkit.app.core.data.ai.TaskRoutes.resolve] 还负责预算拦截、按成本降级、
 * 冷却与调用计数。绕过它自己 `findModelById` 等于把这些语义整段丢掉，所以本函数只回答
 * 「偏好哪个模型 id」，选型与降级仍然交给 TaskRoutes。
 *
 * 纯函数：不碰 Compose / Hilt / 数据库（存在性由调用方以 [isKnownModel] 注入），
 * 因此可用 JVM 单测钉死（见 `GroupTurnModelTest`）。
 *
 * @param role 本轮发言角色；单聊传 null。
 * @param assistantChatModelId 该角色所属助手的 `chatModelId`，即回落目标，原样返回。
 * @param isKnownModel 该模型 id 当前是否还能在配置里查到模型。
 * @return 要交给 `TaskRoutes.resolve` 的 `preferredId`；角色绑不上且助手没绑时为 null
 *   （此时 TaskRoutes 自行按会话默认模型选，与 C1 之前一致）。
 */
internal fun resolveGroupTurnModelId(
    role: GroupRole?,
    assistantChatModelId: Uuid?,
    isKnownModel: (Uuid) -> Boolean,
): Uuid? {
    val roleModelId = role?.modelId
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        ?.takeIf(isKnownModel)
    return roleModelId ?: assistantChatModelId
}