package heizige.kk.khatkit.app.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeAssistChip
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.ui.Greeting
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar

/**
 * 空会话的欢迎层：角色头像 + 时段问候 + 一句说明 + 三条起步建议。
 *
 * **挂载条件由调用点决定**（目前是 `conversation.messageNodes.isEmpty()`），一旦发出第一条
 * 消息就整层 `fadeOut` 退出——它不参与 LazyColumn 的滚动度量，也不改写消息列表的 item 数，
 * 所以不会打乱 `ChatList` 里那套「自动滚到底部 / 滚动条比例 / 首项索引」的既有算术。
 *
 * 三条建议只**填充输入框**（`ChatInputState.setMessageText`），不直接发送：
 * 发送路径上还有「角色没配模型 → 弹 toast」之类的守卫，欢迎层不该替用户跨过它们。
 *
 * 视觉全部走 `:kedge`（[KedgeTextStyles] / [KedgeColors] / [KedgeAssistChip]），
 * MD3Exp 与 Miuix 两种风格各自成立，页面上不出现裸 `material3.Surface`。
 */
@Composable
internal fun ChatWelcomeHost(
    visible: Boolean,
    assistant: Assistant,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 进场从下方 1/8 高度滑入、退场只做淡出：发消息那一瞬消息列表刚插入一条，
    // 加位移动画会让两层同时动，观感上是「抖一下」。
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 }),
        exit = fadeOut(),
    ) {
        ChatWelcome(assistant = assistant, onSuggestionClick = onSuggestionClick)
    }
}

@Composable
private fun ChatWelcome(
    assistant: Assistant,
    onSuggestionClick: (String) -> Unit,
) {
    val defaultName = stringResource(R.string.assistant_page_default_assistant)
    val displayName = assistant.name.ifBlank { defaultName }
    // 建议文案固定三条，全部走 stringResource 在组合期取——放 remember 外面是为了
    // 运行中切换语言能立刻重建（这层只在空会话存在，重建代价可忽略）。
    val suggestions = listOf(
        stringResource(R.string.chat_welcome_suggestion_summarize),
        stringResource(R.string.chat_welcome_suggestion_idea),
        stringResource(R.string.chat_welcome_suggestion_translate),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 只读头像：不传 onUpdate / onClick，就不会挂出编辑弹层与裁切启动器。
        UIAvatar(
            name = displayName,
            value = assistant.avatar,
            avatarShape = assistant.avatarShape,
            avatarColor = assistant.avatarColor,
            modifier = Modifier.size(84.dp),
        )
        Spacer(Modifier.height(20.dp))
        Greeting(style = KedgeTextStyles.title())
        Spacer(Modifier.height(6.dp))
        Text(
            // 角色自带的介绍优先；没填就回退成一句通用提示，避免这里出现空行。
            text = assistant.description.ifBlank { stringResource(R.string.chat_welcome_hint) },
            style = KedgeTextStyles.footnote(),
            color = KedgeColors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            suggestions.forEach { suggestion ->
                KedgeAssistChip(
                    onClick = { onSuggestionClick(suggestion) },
                    label = suggestion,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
