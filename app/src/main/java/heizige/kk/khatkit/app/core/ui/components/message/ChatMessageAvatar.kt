package heizige.kk.khatkit.app.core.ui.components.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.context.LocalSettings

/**
 * 气泡旁的头像（用户头像 / AI 图标）已按需求整体删除：AI 头像只在顶栏保留一处，
 * 用户头像不再出现在消息流里。原先这个文件里的 `shouldShowUserAvatar` /
 * `ChatMessageUserAvatarIcon` / `shouldShowAssistantAvatar` / `ChatMessageAssistantAvatarIcon`
 * 都没有调用方了，随渲染路径一起删除（而不是留成「恒返回 false」的死代码）。
 *
 * 只留下「气泡上方那一行名字」的口径函数 —— 它跟头像无关，消费的是 `showModelName` /
 * 群聊 `speakerName`，仍在用。
 *
 * 持久化字段 `DisplaySetting.showUserAvatar` / `showModelIcon` 保持不动（旧数据、导入导出
 * 不受影响），只是不再有消费方；对应的两行设置开关已从 MD3 / Miuix 两个设置页删除。
 */

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
