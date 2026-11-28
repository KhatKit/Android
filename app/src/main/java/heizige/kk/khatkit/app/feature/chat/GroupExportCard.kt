package heizige.kk.khatkit.app.feature.chat

import androidx.compose.ui.platform.LocalResources
import heizige.kk.khatkit.uikit.mochi.KkCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.uikit.mochi.Toast as KhromiaToast
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.feature.chat.data.TavernChatCodec
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.icons.chat.groups
import heizige.kk.khatkit.uikit.mochi.MochiListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.feature.chat.data.GROUP_EXPORT_MIME_TYPE
import heizige.kk.khatkit.app.feature.chat.helper.groupExportFileName
import heizige.kk.khatkit.app.feature.chat.helper.groupExportName
import heizige.kk.khatkit.app.feature.chat.helper.groupExportRoleCards
import heizige.kk.khatkit.app.feature.chat.helper.groupExportUserName
import heizige.kk.khatkit.app.feature.chat.helper.resolveGroupExportNodes
import heizige.kk.khatkit.app.feature.chat.helper.shareFile
import heizige.kk.khatkit.app.feature.chat.helper.writeExportTempFile



/**
 * 「导出 Tavern 群聊」卡片。形状照 `ConversationExport.kt` 里「存为卡片」那张卡
 * （`KkCard(onClick = …) { MochiListItem(headline / supporting / leading) }`）。
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
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // isGroupConversation 已经保证非 null，这里的兜底只是让编译器知道类型；
    // 真为 null 说明调用方绕过了那层判定，宁可什么都不做也不要导出一个没有群配置的半成品。
    val config = conversation.groupConfig ?: return


    KkCard(
        onClick = {
            scope.launch {
                val selection = resolveGroupExportNodes(conversation, selectedMessages)
                if (selection.isEmpty) {
                    KhromiaToast.show(
                        message = resources.getString(R.string.chat_group_export_all_failed, selection.requested),
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
                            message = resources.getString(R.string.chat_group_export_failed, it.message),
                            isError = true,
                        )
                    }
                uri.getOrNull()?.let {
                    KhromiaToast.show(
                    if (selection.skipped == 0) resources.getString(R.string.chat_group_export_success, selection.nodes.size)
                    else resources.getString(R.string.chat_group_export_success_skipped, selection.nodes.size, selection.skipped),
                )
                }
                onFinished()
            }
        },
        modifier = modifier,
    ) {
        MochiListItem(
            headlineContent = { Text(stringResource(R.string.chat_group_export_title)) },
            supportingContent = {
                Text(stringResource(R.string.chat_group_export_desc))
            },
            leadingContent = { Icon(groups, contentDescription = null) },
        )
    }
}
