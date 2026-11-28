package heizige.kk.khatkit.app.feature.chat.floating

import heizige.kk.khatkit.app.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.feature.chat.data.GenerationPhase
import kotlin.uuid.Uuid

/**
 * 悬浮窗要显示的一份状态（移植上游 f048bed65 的 FloatingChatUiState，按本 fork 可用数据裁剪）。
 *
 * @param preview 本轮回复的纯文本预览（正文优先，没有正文时用思考内容）
 * @param previewIsReasoning true 表示 preview 是思考内容
 * @param queuedCount 排队消息数
 */
data class FloatingChatUiState(
    val conversationId: Uuid,
    val title: String,
    val generating: Boolean,
    val processingStatus: String?,
    val phase: GenerationPhase,
    val hasReply: Boolean,
    val preview: String,
    val previewIsReasoning: Boolean,
    val pendingToolCount: Int,
    val queuedCount: Int,
    val queuePaused: Boolean,
    val errorMessage: String?,
)

/** 收起态：贴着屏幕边缘的小圆点，点一下展开。 */
@Composable
fun ChatFloatingBubble(state: FloatingChatUiState, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = if (state.generating) "•••" else "AI", style = MaterialTheme.typography.labelLarge)
            if (state.queuedCount > 0) {
                Text(text = "+" + state.queuedCount, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 展开态：标题 + 进度/预览 + 快速回复。 */
@Composable
fun ChatFloatingPanel(
    state: FloatingChatUiState,
    maxHeight: Dp,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    onOpenConversation: () -> Unit,
    onReply: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 10.dp,
        modifier = Modifier.widthIn(max = 520.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.title.ifBlank { stringResource(R.string.floating_conversation) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCollapse) { Text(stringResource(R.string.floating_collapse)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.floating_close)) }
            }

            Text(
                text = state.statusText(context),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.pendingToolCount > 0) {
                Text(text = pluralStringResource(R.plurals.floating_pending_tools, state.pendingToolCount, state.pendingToolCount), style = MaterialTheme.typography.bodySmall)
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = state.preview.ifBlank { if (state.hasReply) "" else stringResource(R.string.floating_waiting_reply) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.previewIsReasoning) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.heightIn(max = maxHeight),
                )
            }

            if (state.queuedCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.floating_queued, state.queuedCount, state.queuedCount) + if (state.queuePaused) stringResource(R.string.floating_queue_paused_suffix) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 悬浮窗拿不到输入焦点（需要 floatingx-system 的 systemHost/layoutParams，本 fork 不引新依赖），
            // 所以面板不放输入框：点「回复」直接打开会话，用系统输入法接着聊。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onReply, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.floating_reply)) }
                if (state.generating) {
                    TextButton(onClick = onStop) { Text(stringResource(R.string.record_stop)) }
                }
            }
            TextButton(onClick = onOpenConversation) { Text(stringResource(R.string.floating_open_conversation)) }
        }
    }
}

private fun FloatingChatUiState.statusText(context: android.content.Context): String = when {
    errorMessage != null -> context.getString(R.string.floating_generation_failed)
    !generating && queuedCount > 0 && queuePaused -> context.getString(R.string.floating_queue_paused)
    generating -> when (val p = phase) {
        is GenerationPhase.RunningTool -> context.getString(R.string.floating_running_tool, p.toolName)
        is GenerationPhase.Thinking -> context.getString(R.string.floating_thinking)
        is GenerationPhase.Writing -> processingStatus ?: context.getString(R.string.floating_writing)
        GenerationPhase.Waiting -> context.getString(R.string.floating_preparing)
    }
    hasReply -> context.getString(R.string.floating_reply_done)
    else -> context.getString(R.string.floating_waiting_reply)
}
