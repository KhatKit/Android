package heizige.kk.khatkit.app.core.ui.floating

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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var draft by remember(state.conversationId) { mutableStateOf("") }
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
                    text = state.title.ifBlank { "会话" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCollapse) { Text("收起") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }

            Text(
                text = state.statusText(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.pendingToolCount > 0) {
                Text(text = "待审批工具调用：" + state.pendingToolCount, style = MaterialTheme.typography.bodySmall)
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
                    text = state.preview.ifBlank { if (state.hasReply) "" else "等待回复…" },
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
                    text = "排队 " + state.queuedCount + " 条" + if (state.queuePaused) "（已暂停）" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("回复…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (state.generating) {
                    TextButton(onClick = onStop) { Text("停止") }
                } else {
                    Button(
                        onClick = {
                            val text = draft.trim()
                            if (text.isNotEmpty()) {
                                draft = ""
                                onSend(text)
                            }
                        },
                    ) { Text("发送") }
                }
            }
            TextButton(onClick = onOpenConversation) { Text("打开会话") }
        }
    }
}

private fun FloatingChatUiState.statusText(): String = when {
    errorMessage != null -> "生成失败"
    !generating && queuedCount > 0 && queuePaused -> "队列暂停"
    generating -> when (val p = phase) {
        is GenerationPhase.RunningTool -> "正在执行工具：" + p.toolName
        is GenerationPhase.Thinking -> "正在思考…"
        is GenerationPhase.Writing -> processingStatus ?: "正在回复…"
        GenerationPhase.Waiting -> "准备中…"
    }
    hasReply -> "回复完成"
    else -> "等待回复…"
}
