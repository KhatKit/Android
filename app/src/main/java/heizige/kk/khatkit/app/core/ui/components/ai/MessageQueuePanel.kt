package heizige.kk.khatkit.app.core.ui.components.ai

import heizige.kk.khatkit.uikit.mochi.Md3TextFieldWithState
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.persistentListOf
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.feature.chat.data.MessageQueueState
import heizige.kk.khatkit.app.feature.chat.data.QueuedMessage
import heizige.kk.khatkit.app.core.ui.hooks.ChatInputState
import heizige.kk.khatkit.app.core.ui.icons.arrowUpward
import ui.feice.mochi.basic.MochiConfirmationDialog
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole

@Composable
internal fun MessageQueuePanel(
    state: MessageQueueState,
    onRemove: (Uuid) -> Unit,
    onBeginEdit: (Uuid) -> QueuedMessage?,
    onFinishEdit: (Uuid, List<UIMessagePart>?) -> Unit,
    onResume: () -> Unit,
    onSendNow: (Uuid) -> Unit = {},
    generating: Boolean = false,
) {
    var editing by remember { mutableStateOf<QueuedMessage?>(null) }
    // 正在生成时立即发送必然要打断本回合，先确认再动手。
    var confirmingSendNow by remember { mutableStateOf<QueuedMessage?>(null) }
    if (state.messages.isNotEmpty()) {
        KkSurface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_message_queue"),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (state.paused) R.string.chat_page_queue_paused_count
                            else R.string.chat_page_queue_pending_count,
                            state.messages.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                    )
                    if (state.paused) {
                        MochiTextButton(onClick = onResume, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.chat_page_queue_resume)) }
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                    itemsIndexed(
                        state.messages,
                        key = { _, message -> message.id }) { index, message ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val attachmentLabels = listOf(
                                stringResource(R.string.chat_page_queue_image),
                                stringResource(R.string.chat_page_queue_file),
                                stringResource(R.string.chat_page_queue_audio),
                                stringResource(R.string.chat_page_queue_video),
                            )
                            Text(
                                text = "${index + 1}. " + message.parts.joinToString(" ") {
                                    when (it) {
                                        is UIMessagePart.Text -> it.text
                                        is UIMessagePart.Image -> attachmentLabels[0]
                                        is UIMessagePart.Document -> attachmentLabels[1]
                                        is UIMessagePart.Audio -> attachmentLabels[2]
                                        is UIMessagePart.Video -> attachmentLabels[3]
                                        else -> ""
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MochiIconButton(
                                enabled = !message.isEditing,
                                onClick = {
                                    if (generating) confirmingSendNow = message
                                    else onSendNow(message.id)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("chat_queue_send_now"),
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = arrowUpward,
                                    contentDescription = stringResource(R.string.chat_page_queue_send_now),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            MochiTextButton(
                                enabled = !message.isEditing,
                                onClick = { editing = onBeginEdit(message.id) },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text(if (message.isEditing) stringResource(R.string.chat_page_queue_editing) else stringResource(R.string.edit)) }
                            MochiTextButton(
                                enabled = !message.isEditing,
                                onClick = { onRemove(message.id) },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text(stringResource(R.string.chat_page_queue_remove)) }
                        }
                    }
                }
            }
        }
    }

    editing?.let { message ->
        val input = remember(message.id) {
            ChatInputState().apply {
                editingMessage = message.id
                setContents(message.parts)
            }
        }
        val finishEdit by rememberUpdatedState(onFinishEdit)
        // Leaving the page or dismissing the dialog must release the queue item.
        DisposableEffect(message.id) {
            onDispose { finishEdit(message.id, null) }
        }
        // DG1：material3 AlertDialog → MochiDialog（输入框作为内容，保存/取消是胶囊按钮）
        MochiDialog(
            visible = true,
            onDismissRequest = { editing = null },
            title = stringResource(R.string.chat_page_queue_edit_title),
            actions = persistentListOf(
                MochiDialogAction(
                    label = stringResource(R.string.cancel),
                    role = MochiDialogActionRole.Cancel,
                ),
                MochiDialogAction(
                    label = stringResource(R.string.chat_page_save),
                    role = MochiDialogActionRole.Primary,
                    enabled = !input.isEmpty(),
                    onClick = { onFinishEdit(message.id, input.getContents()) },
                ),
            ),
            dismissOnOutsideTap = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (input.messageContent.isNotEmpty()) MediaFileInputRow(input)
                Md3TextFieldWithState(
                    state = input.textContent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_queue_edit_text"),
                    lineLimits = TextFieldLineLimits.MultiLine(
                        minHeightInLines = 3,
                        maxHeightInLines = 8
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }

    confirmingSendNow?.let { message ->
        // DG1：确认类（标题 + 说明 + 两个动作）直接用 MochiConfirmationDialog
        MochiConfirmationDialog(
            visible = true,
            onDismissRequest = { confirmingSendNow = null },
            title = stringResource(R.string.chat_page_queue_send_now),
            message = stringResource(R.string.chat_page_queue_send_now_interrupt),
            actions = persistentListOf(
                MochiDialogAction(
                    label = stringResource(R.string.cancel),
                    role = MochiDialogActionRole.Cancel,
                ),
                MochiDialogAction(
                    label = stringResource(R.string.chat_page_queue_send_now_confirm),
                    role = MochiDialogActionRole.Primary,
                    onClick = { onSendNow(message.id) },
                ),
            ),
        )
    }
}
