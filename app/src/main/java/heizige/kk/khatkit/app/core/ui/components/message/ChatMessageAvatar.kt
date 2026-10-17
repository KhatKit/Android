package heizige.kk.khatkit.app.core.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
 * 助手消息左边要不要放头像图标。与 [ChatMessageAssistantAvatarIcon] 的渲染条件是同一份：
 * 必须是助手消息，且要么有模型、要么助手开了「用助手头像」。调用方还要再叠加一次
 * `showModelIcon`：关掉图标时整列都不占宽度，否则气泡会莫名多缩进 8dp。
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
 * 助手侧的头像图标本身（28dp）。
 *
 * 从原先「头像 + 名字同一个 Row」的助手头像行里拆出来，是为了让头像**单独占左列**：助手消息现在是
 * 「头像在左、气泡在右」的横排，头像不能再和名字挤在同一个 Row 里（那个 Row 里的名字带
 * `weight(1f)`，一旦横排会把整行宽度吃掉，气泡就没地方了）。
 *
 * 渲染条件不在这里重复判定：调用方用 [shouldShowAssistantAvatar] + `showModelIcon` 决定。
 */
@Composable
fun ChatMessageAssistantAvatarIcon(
    loading: Boolean,
    model: Model?,
    assistant: Assistant?,
    modifier: Modifier = Modifier,
) {
    if (assistant?.useAssistantAvatar == true) {
        UIAvatar(
            name = assistant.name,
            modifier = modifier.size(28.dp),
            value = assistant.avatar,
            loading = loading,
        )
    } else if (model != null) {
        AutoAIIcon(
            name = model.modelId,
            modifier = modifier.size(28.dp),
            loading = loading,
        )
    }
}

/**
 * 气泡左上方那一行要显示的名字。群聊用 [speakerName]（说话者角色名）压过助手名，
 * 免得同一条消息上并排出现「角色名」和「助手名」两个名字；单聊沿用旧口径
 * （`showModelName` 开关 + 助手名优先于模型名）。返回 null 表示这一行没有名字。
 */
@Composable
internal fun assistantMessageLabel(
    speakerName: String?,
    model: Model?,
    assistant: Assistant?,
): String? {
    speakerName?.let { return it }
    if (!LocalSettings.current.displaySetting.showModelName) return null
    return if (assistant?.useAssistantAvatar == true) {
        assistant.name.ifEmpty { stringResource(R.string.assistant_page_default_assistant) }
    } else {
        model?.displayName
    }
}
