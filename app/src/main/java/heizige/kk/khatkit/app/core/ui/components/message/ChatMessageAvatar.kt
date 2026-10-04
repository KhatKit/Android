package heizige.kk.khatkit.app.core.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.isEmptyUIMessage
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Avatar
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.kedge.theme.KedgeTextStyles

/**
 * 气泡上方要不要给用户头像留一行。与 [ChatMessageUserAvatar] 的渲染条件是同一份：
 * 调用方靠它决定这一行渲不渲染，否则会出现「留了空隙却没有内容」的行。
 */
internal fun shouldShowUserAvatar(message: UIMessage, showUserAvatar: Boolean): Boolean =
    message.role == MessageRole.USER && !message.parts.isEmptyUIMessage() && showUserAvatar

/**
 * 气泡上方要不要给助手头像 / 模型图标留一行。与 [ChatMessageAssistantAvatar] 的渲染条件是
 * 同一份：必须是助手消息，且要么有模型、要么助手开了「用助手头像」。
 */
internal fun shouldShowAssistantAvatar(message: UIMessage, model: Model?, assistant: Assistant?): Boolean =
    message.role == MessageRole.ASSISTANT && (model != null || assistant?.useAssistantAvatar == true)

@Composable
fun ChatMessageUserAvatar(
    message: UIMessage,
    avatar: Avatar,
    nickname: String,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    if (shouldShowUserAvatar(message, settings.displaySetting.showUserAvatar)) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = nickname.ifEmpty { stringResource(R.string.user_default_name) },
                style = KedgeTextStyles.body(),
                maxLines = 1,
            )
            UIAvatar(
                name = nickname,
                modifier = Modifier.size(28.dp),
                value = avatar,
                loading = false,
            )
        }
    }
}

/**
 * 助手侧的头像 + 名字行。渲染与否由 [shouldShowAssistantAvatar] 决定，别处不要再写一遍。
 *
 * [showName] 只管名字那一段：群聊里气泡上方已经有角色名（说话者是谁），这时关掉它，
 * 免得同一条消息上并排出现「角色名」和「助手名」两个名字。单聊传 true，行为不变。
 */
@Composable
fun ChatMessageAssistantAvatar(
    message: UIMessage,
    loading: Boolean,
    model: Model?,
    assistant: Assistant?,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
) {
    val settings = LocalSettings.current
    val showIcon = settings.displaySetting.showModelIcon
    val useAssistantAvatar = assistant?.useAssistantAvatar == true
    if (shouldShowAssistantAvatar(message, model, assistant)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            if (useAssistantAvatar) {
                if (showIcon) {
                    UIAvatar(
                        name = assistant.name,
                        modifier = Modifier.size(28.dp),
                        value = assistant.avatar,
                        loading = loading,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showName && settings.displaySetting.showModelName) {
                        Text(
                            text = assistant.name.ifEmpty { stringResource(R.string.assistant_page_default_assistant) },
                            style = KedgeTextStyles.body(),
                            maxLines = 1,
                        )
                    }
                }
            } else if (model != null) {
                if (showIcon) {
                    AutoAIIcon(
                        name = model.modelId,
                        modifier = Modifier.size(28.dp),
                        loading = loading
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showName && settings.displaySetting.showModelName) {
                        Text(
                            text = model.displayName,
                            style = KedgeTextStyles.body(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
