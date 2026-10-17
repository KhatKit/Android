package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import heizige.kk.khatkit.app.core.data.model.Assistant

/**
 * 聊天页正文底色：角色自定义背景图 / 流光渐变。
 *
 * **角色由调用方传入，这里不再自己 `setting.getCurrentAssistant()`**。这正是本任务修的
 * 那个 bug：顶栏按 `conversation.assistantId` 解析角色（`ChatScaffold` 的 `assistant`），
 * 正文却按全局 `currentAssistant` 画背景 —— 会话属于角色 A、全局当前角色是 B 时，
 * 顶栏显示 A、正文画的是 B 的图。现在两边共用 `ChatScaffold` 里解析好的**同一个**
 * `assistant`，从根上不可能再分叉。
 *
 * 没配背景的角色（`background == null` 且没开流光）这里**什么都不画**，底色交给外层
 * `KedgeSurface`（`KedgeColors.surface`），与改造前一致。
 */
@Composable
fun AssistantBackground(assistant: Assistant, modifier: Modifier) {
    if (assistant.useGradientBackground) {
        MeshGradientBackground(modifier = modifier)
        return
    }
    if (assistant.background != null) {
        val backgroundColor = MaterialTheme.colorScheme.background
        val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
        Box(modifier = modifier) {
            AsyncImage(
                model = assistant.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(backgroundOpacity)
            )

            // 全屏渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.2f),
                                backgroundColor.copy(alpha = 0.5f)
                            )
                        )
                    )
            )
        }
    }
}
