package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.ai.ui.isEmptyUIMessage
import heizige.kk.khatkit.app.core.data.model.MessageNode
import kotlin.uuid.Uuid

/**
 * 判断这次生成是否真的产出了内容，即消息里是否存在任何**非空载荷**。
 *
 * 直接复用 `List<UIMessagePart>.isEmptyUIMessage()`（`ai` 模块 `ui/Message.kt`）作为主判据：
 * 它已经覆盖 `parts` 为空、文本空白、推理空白、附件 url 空白这些「流式一个 token 都没吐」的情况，
 * 与 UI 层（`ChatMessage.kt`）和导出层（`ConversationExport.kt`）的语义保持一致，不另立一套判定。
 *
 * 这里只补齐 `isEmptyUIMessage()` 在持久化场景下的两个缺口：
 *
 * 1. 它的 `when` 没有覆盖 `ToolCall` / `ToolResult` / `Search`，会落到 `else -> true` 即「视为空」。
 *    但这三个 part 本身就是模型或服务端返回的载荷，出现即代表有输出：
 *    `ToolCall` 是模型已经发出的工具调用，`ToolResult` 是工具返回的结果，`Search` 是检索结果。
 *    若沿用 `else -> true`，一条只带工具调用的助手消息会被误判成「没产出」而丢掉。
 * 2. 反过来，`Tool` / `ServerTool` 在 `isEmptyUIMessage()` 里直接判为「非空」，本函数保持这一口径：
 *    只要 part 存在就说明模型已经产出了工具调用，哪怕 `output` 还空着（等待审批或被用户取消）。
 *
 * 因此本函数只在 `isEmptyUIMessage()` 说「空」的前提下，再把「不是靠空白字段判空的那几类 part」
 * 翻回有产出。由于 `UIMessagePart` 是密封类，`when` 由编译器保证穷尽，且新增 part 类型默认落在
 * `else -> true`（有产出、保留），属于失败时偏向保留数据的一侧。
 */
fun List<UIMessagePart>.hasGeneratedOutput(): Boolean {
    if (!isEmptyUIMessage()) return true
    return any { part ->
        when (part) {
            // 这些类型的「有无内容」由空白字段决定，已被 isEmptyUIMessage 判过，全部为空。
            is UIMessagePart.Text,
            is UIMessagePart.Image,
            is UIMessagePart.Document,
            is UIMessagePart.Reasoning,
            is UIMessagePart.Video,
            is UIMessagePart.Audio,
                -> false

            else -> true
        }
    }
}

/**
 * 是否是一条「本次生成一个 token 都没产出」的助手消息。
 *
 * 只看助手消息：`GenerationLoop` 预建的占位消息（`role = ASSISTANT, parts = emptyList()`）需要被判掉，
 * 而用户消息、system 消息即使内容为空也不是生成产物，删掉就是数据丢失。
 */
fun UIMessage.isUngeneratedAssistantMessage(): Boolean =
    role == MessageRole.ASSISTANT && !parts.hasGeneratedOutput()

/**
 * 丢弃「本次生成新增且完全没有产出」的助手消息。
 *
 * 契约条款：取消/超时不得写入未生成的消息。
 *
 * 取消（`ChatManager.stopGeneration`）和超时（`GenerationLoop.awaitNetworkRetryOrThrow` 抛出后走
 * `onCompletion`）最终都会落到同一个 `finishGeneration`，而流式请求发出之前 `GenerationLoop` 已经
 * 预建了一条空助手消息。若不加过滤，用户在首个 chunk 之前停止生成，就会在库里留下一条空气泡。
 *
 * 反过来，已经吐过内容的助手消息（哪怕只有 reasoning 或只有 tool call）必须保留：
 * 契约同时要求「已完成输出保留」，「失败和取消也必须保存已收到的内容」优先级高于丢弃空气泡。
 * 因此判据只看**内容是否为空**，与「正常结束 / 失败 / 取消 / 超时」无关——四者的处置完全一致，
 * 差异只体现在「有没有内容」这一个维度上。
 *
 * @param existingMessageIds 本次生成之前就已经存在的消息 ID（存量）。
 *        只有不在这个集合里的消息才可能被判为「本次生成新增」，从而避免把历史遗留的空助手消息
 *        在某次无关生成后静默删掉。调用方负责在每次落库后把该集合推进到最新。
 *
 * 幂等：连续应用两次与应用一次结果相同——被丢弃的消息已经不在列表里，剩余的空助手消息必然都在
 * [existingMessageIds] 内，不会被二次命中。
 */
fun List<MessageNode>.dropUngeneratedAssistantMessages(
    existingMessageIds: Set<Uuid>,
): List<MessageNode> = mapNotNull { node ->
    val kept = node.messages.filterNot { message ->
        message.id !in existingMessageIds && message.isUngeneratedAssistantMessage()
    }
    when {
        // 没有命中过滤，原样返回，保证未变更的节点不产生新的对象。
        kept.size == node.messages.size -> node
        // 节点被清空：MessageNode.currentMessage / Conversation.currentMessages 会直接越界，
        // 因此整个节点一起丢掉，而不是留一个空壳。
        kept.isEmpty() -> null
        else -> {
            // selectIndex 是按位置存的，删除后必须重新对齐，否则 currentMessages 会取错消息或越界。
            val selectedId = node.messages.getOrNull(node.selectIndex)?.id
            val newSelectIndex = selectedId?.let { id -> kept.indexOfFirst { it.id == id } } ?: -1
            node.copy(messages = kept, selectIndex = if (newSelectIndex >= 0) newSelectIndex else 0)
        }
    }
}