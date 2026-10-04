package heizige.kk.khatkit.app.feature.chat

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import heizige.kk.khromia.helper.Toast as KhromiaToast
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.getAssistantById
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.core.ui.icons.groups
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.uuid.Uuid

/**
 * 群聊「导出 Tavern 群聊」卡片——C1-09 的 UI 入口。
 *
 * [TavernChatCodec.exportGroupJsonl] / [TavernChatCodec.importGroup] 早就写好了往返内核，
 * 但在那之前 `app/src/main` 里零调用点，用户根本点不到。本文件就是那个入口：把选中的
 * 消息反查成 [MessageNode]，交给 codec，落成 `.jsonl` 再走分享面板。
 *
 * 卡片**只在群聊会话出现**，判定用 [isGroupConversation] 的严格口径（`group_config` 非空
 * **且** `type == GROUP`），和气泡层、轮次内核对同一个会话的判断不会漂移。单聊会话算出来
 * 恒为 false，面板与 C1 之前逐字相同。
 *
 * 本文件只碰**一个** UI 入口，不新增导航页、不改导入路径（恢复侧由另一个包负责）。
 */

/** 群聊导出文件的扩展名。酒馆群聊文件本身就是 JSONL（每行一个 JSON 对象）。 */
internal const val GROUP_EXPORT_EXTENSION = "jsonl"

/**
 * 分享用的 mimeType。
 *
 * 依据：仓库里关于 JSON 分享 mime 的**唯一**既有约定是 `application/json`
 * （`core/data/export/ExportHooks.kt:57` 的 `ACTION_SEND` 与同文件 `:85` 的
 * `ActivityResultContracts.CreateDocument`）；全仓库检索不到任何 jsonl / ndjson 专用
 * mime 的既有用法（`grep -rn "x-ndjson\|json-seq" --include=*.kt app/src/main` 无命中），
 * Android 的 `MimeTypeMap` 也没有 `.jsonl` 条目。
 *
 * 所以这里沿用 `application/json` 而不是 `application/x-ndjson`：share chooser 是按 mime
 * 过滤候选目标的，一个多数目标不认的 mime 会让导出的文件在大部分 App 里根本不出现。
 * 扩展名仍是 `.jsonl`，扩展名与 mime 不一致在这里是**刻意**的——文件名要让人认出格式，
 * mime 要让文件能被分享出去。
 */
internal const val GROUP_EXPORT_MIME_TYPE = "application/json"

/** 群名兜底：会话没标题时用（与「存为卡片」的 `对话存档` 同一做法）。 */
internal const val GROUP_EXPORT_FALLBACK_NAME = "群聊存档"

/** 用户名兜底：昵称为空时用。与 `TavernMacroTransformer` / `PromptInjectionTransformer` 的 `User` 同一口径。 */
internal const val GROUP_EXPORT_FALLBACK_USER = "User"

/**
 * 导出面板给的 `List<UIMessage>` 反查成 `List<MessageNode>` 的结果。
 *
 * [skipped] 是**反查不到**的消息条数，必须带着走：这些消息不在当前会话的 `messageNodes` 里
 * （预览态、跨会话残留、被并发改写），静默丢掉的话用户会以为导出成功了而文件里少了东西。
 */
internal data class GroupExportSelection(
    val nodes: List<MessageNode>,
    val skipped: Int,
) {
    /** 用户一共选了多少条（成功 + 跳过）。 */
    val requested: Int get() = nodes.size + skipped

    /** 一条都没反查到。UI 见到 true 就该直接报错，不要导出空文件。 */
    val isEmpty: Boolean get() = nodes.isEmpty()
}

/**
 * 把导出面板给的 `List<UIMessage>` 反查成 [MessageNode] 列表——[TavernChatCodec.exportGroupJsonl]
 * 要的是节点（要拿到 `swipes` / `swipe_id` 分支），面板手上只有消息。
 *
 * 反查口径只有一份：[Conversation.getMessageNodeByMessage]（按 `UIMessage` 的数据类相等
 * 在 `messageNodes` 里找命中的节点）。
 *
 * 三条规则：
 * 1. **反查不到就跳过并计数**，绝不静默丢，也不整批失败——选 20 条里 18 条在会话里仍然要导。
 * 2. **按节点 id 去重**。同一个节点的多个 swipe 都在选中集合里时，
 *    `getMessageNodeByMessage` 会把同一个节点返回多次；不去重的话 JSONL 里会出现两条
 *    完全相同的行，往返导入后变成两条重复消息。面板另外两条导出（Markdown / PNG）是直接遍历
 *    `selectedMessages` 的，所以那边不会出现这个形状；群聊导出按节点写行，必须自己去重。
 * 3. 保持 `selectedMessages` 的原始顺序，不重排、不排序——用户选的就是他要的顺序。
 */
internal fun resolveGroupExportNodes(
    conversation: Conversation,
    selectedMessages: List<UIMessage>,
): GroupExportSelection {
    val nodes = ArrayList<MessageNode>(selectedMessages.size)
    val seen = HashSet<Uuid>(selectedMessages.size)
    var skipped = 0
    selectedMessages.forEach { message ->
        val node = conversation.getMessageNodeByMessage(message)
        if (node == null || !seen.add(node.id)) {
            skipped++
        } else {
            nodes.add(node)
        }
    }
    return GroupExportSelection(nodes = nodes, skipped = skipped)
}

/**
 * 导出文件名。与 `ConversationExport.kt` 里 Markdown / PNG 两条导出同一口径
 * （`chat-export-` 前缀 + 同一份时间戳格式），所以同一次导出里的三种文件在下载目录里排在一起。
 *
 * 刻意**不**把群名拼进文件名：会话标题是用户自由输入，可能带 `/`、`:`、换行，落进文件名
 * 要么建文件失败、要么在系统分享面板里显示成一串路径分隔符。群名本身已经写进文件头部
 * （`character_name` 与 `khatkit_group.name`，见 [TavernChatCodec.exportGroup]），
 * 不靠文件名承载。
 */
internal fun groupExportFileName(now: LocalDateTime): String =
    "chat-export-${now.format(ExportTimeFormatter)}.$GROUP_EXPORT_EXTENSION"

/** 群名口径：会话标题，空标题回落到 [GROUP_EXPORT_FALLBACK_NAME]。 */
internal fun groupExportName(conversation: Conversation): String =
    conversation.title.ifBlank { GROUP_EXPORT_FALLBACK_NAME }

/**
 * 用户名口径：[Settings.displaySetting] 的 `userNickname`，空昵称回落到
 * [GROUP_EXPORT_FALLBACK_USER]。
 *
 * 字段名与兜底值都照抄酒馆侧的既有写法：`TavernMacroTransformer.kt:16` 与
 * `PromptInjectionTransformer.kt:34` 都是
 * `ctx.settings.displaySetting.userNickname.ifBlank { "User" }`。所以导出的
 * `user_name` 与宏展开时用的是同一个名字，导出到酒馆再导入回来不会换人。
 */
internal fun groupExportUserName(settings: Settings): String =
    settings.displaySetting.userNickname.ifBlank { GROUP_EXPORT_FALLBACK_USER }

/**
 * 群成员摊成角色卡元数据（[RoleCardMeta]），交给 [TavernChatCodec.exportGroupJsonl] 写进
 * 顶层 `khatkit_group.cards`。
 *
 * **实际取到什么**（逐字段）：
 * - `role_id` / `assistant_id` / `card_id`：直接来自群配置的 [GroupRole]，不加工；
 * - `name`：优先 `role.name`，为空时退回它绑定的助手的 `name`；
 * - `persona`：取 `role.assistantId` 指向的助手的 `systemPrompt`，解析方式
 *   （`Uuid.parse(role.assistantId)` → `settings.getAssistantById`）与 `ChatManager`
 *   发言时挑角色助手完全一致，所以导出文件里的 persona 就是该角色真正会用的那段系统提示词；
 * - `avatar_ref`：恒为 null。助手头像存的是 `Avatar`（本地文件 URI 或远程 URL），
 *   不是可移植引用，写进去只会让对方拿到一个指不到东西的路径。
 *
 * `role.cardId` 只是**原样透传**的 Tavern 引用：全仓库没有任何地方把 `cardId` 解析成角色卡
 * 实体（`grep -rn "cardId" --include=*.kt app/src/main` 只命中 `GroupChatPage.kt:101` 这一处
 * 赋值），真正能拿到的「真实角色卡」只有 `assistantId` 指向的助手。助手已被删除或
 * `assistantId` 不是合法 Uuid 时**照样导出**，退化成 `role.name` + 空 `persona`，
 * 不因为缺角色卡就把整个导出入口禁掉。
 *
 * ⚠️ **已知重复**：`GroupChatPage.kt` 里那个私有 `roleCards` 做的是同一件事（同样按
 * `assistantId` 取 `systemPrompt`、同样 `avatar_ref` 留空）。那个文件属于并行子包，
 * 本次不跨文件统一；两份口径目前一致，后续应合成一份。
 */
internal fun groupExportRoleCards(roles: List<GroupRole>, settings: Settings): List<RoleCardMeta> =
    roles.map { role ->
        val assistant = runCatching { Uuid.parse(role.assistantId) }.getOrNull()
            ?.let { settings.getAssistantById(it) }
        RoleCardMeta(
            roleId = role.id,
            name = role.name.ifBlank { assistant?.name.orEmpty() },
            assistantId = role.assistantId,
            cardId = role.cardId,
            persona = assistant?.systemPrompt.orEmpty(),
        )
    }

/**
 * 一条都没反查到时的提示。这不是「导出成功」，必须明确报错并说明为什么。
 *
 * 常见成因是会话在导出面板打开之后被并发改写（消息已删 / 已切分支），选中的消息全成了孤儿。
 */
internal fun groupExportAllFailedMessage(selection: GroupExportSelection): String =
    "选中的 ${selection.requested} 条消息都不在当前会话里，无法导出 Tavern 群聊"

/**
 * 导出成功的提示。**有跳过时必须说出来**：文件里少了消息而用户不知道，下一次导入就会
 * 静默少一段历史。没有跳过时只报条数。
 */
internal fun groupExportSuccessMessage(selection: GroupExportSelection): String =
    if (selection.skipped == 0) {
        "已导出 Tavern 群聊（${selection.nodes.size} 条）"
    } else {
        "已导出 Tavern 群聊（${selection.nodes.size} 条，跳过 ${selection.skipped} 条找不到的消息）"
    }

/**
 * 「导出 Tavern 群聊」卡片。形状照 `ConversationExport.kt` 里「存为卡片」那张卡
 * （`KedgeCard(onClick = …) { KedgeListItem(headline / supporting / leading) }`）。
 *
 * @param onFinished 导出流程走完（成功或失败）后回调，用来关掉底部面板——与另外几张卡
 *   在 `onClick` 里 `dismiss()` 的做法一致。
 */
@Composable
internal fun GroupTavernExportCard(
    conversation: Conversation,
    selectedMessages: List<UIMessage>,
    settings: Settings,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // isGroupConversation 已经保证非 null，这里的兜底只是让编译器知道类型；
    // 真为 null 说明调用方绕过了那层判定，宁可什么都不做也不要导出一个没有群配置的半成品。
    val config = conversation.groupConfig ?: return

    KedgeCard(
        onClick = {
            scope.launch {
                val selection = resolveGroupExportNodes(conversation, selectedMessages)
                if (selection.isEmpty) {
                    KhromiaToast.show(
                        message = groupExportAllFailedMessage(selection),
                        isError = true,
                    )
                    onFinished()
                    return@launch
                }
                val uri = runCatching {
                    withContext(Dispatchers.IO) {
                        writeExportTempFile(
                            context = context,
                            fileName = groupExportFileName(LocalDateTime.now()),
                        ) { stream ->
                            stream.write(
                                TavernChatCodec.exportGroupJsonl(
                                    nodes = selection.nodes,
                                    config = config,
                                    cards = groupExportRoleCards(config.roles, settings),
                                    userName = groupExportUserName(settings),
                                    groupName = groupExportName(conversation),
                                ).toByteArray()
                            )
                        }
                    }
                }
                // 分享面板必须在主线程起，IO 结果拿回来之后再调。
                uri.onSuccess { shareFile(context, it, GROUP_EXPORT_MIME_TYPE) }
                    .onFailure {
                        it.printStackTrace()
                        KhromiaToast.show(
                            message = "导出 Tavern 群聊失败：${it.message}",
                            isError = true,
                        )
                    }
                uri.getOrNull()?.let {
                    KhromiaToast.show(groupExportSuccessMessage(selection))
                }
                onFinished()
            }
        },
        modifier = modifier,
    ) {
        KedgeListItem(
            headlineContent = { Text("导出 Tavern 群聊") },
            supportingContent = {
                Text("把选中的群聊导出成酒馆群聊文件（.jsonl），可在 SillyTavern 打开并回导")
            },
            leadingContent = { Icon(groups, contentDescription = null) },
        )
    }
}