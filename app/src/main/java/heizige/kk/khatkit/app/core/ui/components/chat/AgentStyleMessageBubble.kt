package heizige.kk.khatkit.app.core.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import ui.feice.mochi.basic.Text
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme

/**
 * Agent 风格消息气泡
 *
 * 设计理念：
 * - 用户消息：靠右、主题色胶囊气泡（18dp 圆角）
 * - AI 消息：靠左、无气泡纯文字
 * - 简洁的对话体验
 *
 * 这是一个可选的样式组件，可以在设置中切换是否启用
 */
@Composable
fun AgentStyleMessageBubble(
    message: UIMessage,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    val isUser = message.role == MessageRole.USER

    // 提取文本内容
    val text = message.parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }

    if (text.isBlank()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            // 用户消息：靠右、主题色胶囊
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = cs.primary,
                        shape = MochiRoundedRectangle(18.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    style = ts.body,
                    color = cs.onPrimary
                )
            }
        } else {
            // AI 消息：靠左、无气泡纯文字
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = text,
                    style = ts.body,
                    color = cs.onSurface
                )
            }
        }
    }
}
