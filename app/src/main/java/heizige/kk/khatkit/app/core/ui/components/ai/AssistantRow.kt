package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar

/**
 * 助手行的**唯一**实现：助手管理页（[heizige.kk.khatkit.app.feature.assistant.AssistantPage]）、
 * 助手选择器（[AssistantPicker]）与抽屉的「移动到助手」都渲染它。
 *
 * 以前同一个「头像 + 名称 + 右侧操作」的一行各写一遍（助手页一份 `KedgeOptionItem`、
 * 选择器一份 `KedgeListItem`、抽屉一份 `KedgeSurface`），改一处漏两处。现在只有这一份，
 * 容器一律走 [KedgeOptionItem]（它自己按 `LocalKedgeStyle` 分流 MD3 的 Khromia `OptionItem`
 * 与 Miuix 的 `KedgeCard` 行），风格差异全部由组件库决定，调用点不再各自排布。
 *
 * 调用点各自传自己的槽位：
 * - 助手页：40dp 头像 + heroAnimation（[avatarModifier]）、记忆角标与标签
 *   （[supportingContent]）、moreVert（[trailingContent]）；
 * - 选择器：32dp 头像（[avatarSize]）、editNote（[trailingContent]）、选中态
 *   （[backgroundColor] / [contentColor] = primaryContainer / onPrimaryContainer）；
 * - 抽屉：当前助手副标题（[supportingContent]）、当前项底色（[backgroundColor]）。
 *
 * 拖拽排序 / `animateItem` / `scale` 这类列表语义留在调用点：它们属于列表，不属于行，
 * 由 [modifier] 原样透传（`ReorderableItem` 需要「每项一个 lazy item」）。
 *
 * ⚠️ 本组件在 `core/`，签名里**不得**出现 `feature.*` 类型；只依赖 `core.data.model.Assistant`。
 *
 * @param assistant 头像与 id 的来源。
 * @param defaultAssistantName 名称为空时的兜底文案（`stringResource` 由调用点取，
 *   本组件不猜测用哪个字符串资源）。
 * @param shape **必填**：分组圆角由调用点按 `listCardStyle().indexedShape(index, count)`
 *   算好传下来（单行分组传 `indexedShape(0, 1)`，即整张全圆角）。这里不给默认值——
 *   以前默认的 20dp 让选择器/抽屉各自漏出硬编码圆角，与同页其他行不一致。
 * @param avatarSize 头像尺寸档：助手页 40dp、选择器 32dp、抽屉 40dp。
 * @param avatarModifier 头像附加 modifier（助手页的共享元素 `heroAnimation`）。
 * @param backgroundColor 行底色；`Color.Unspecified` 交给 [KedgeOptionItem] 取默认
 *   （MD3 是 surfaceVariant@0.26，Miuix 是卡片底色）。
 * @param contentColor 标题色；`Color.Unspecified` 表示跟随 `LocalContentColor`
 *   （选中态需要 primaryContainer 上的 onPrimaryContainer）。
 * @param supportingContent 标题下方的补充槽（角标 / 标签 / 副标题）。
 * @param trailingContent 行尾槽（moreVert / editNote）。
 * @param onClick `null` 表示纯信息行：整行不可点，也没有按压回弹。
 */
@Composable
fun AssistantItem(
    assistant: Assistant,
    defaultAssistantName: String,
    modifier: Modifier = Modifier,
    shape: Shape,
    avatarSize: Dp = 40.dp,
    providerName: String? = null,
    avatarModifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showDescription: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val displayName = assistant.name.ifBlank { defaultAssistantName }

    KedgeOptionItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        backgroundColor = backgroundColor,
        leadingContent = {
            UIAvatar(
                name = displayName,
                value = assistant.avatar,
                avatarShape = assistant.avatarShape,
                defaultIconName = providerName,
                avatarColor = assistant.avatarColor,
                modifier = Modifier
                    .size(avatarSize)
                    .then(avatarModifier),
            )
        },
        titleContent = {
            Text(
                text = displayName,
                style = KedgeTextStyles.title(),
                // Unspecified 时 Text 会回落到 LocalContentColor，与不传等价；
                // 选中态显式给 onPrimaryContainer，两种风格下都不会漏出默认字色。
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if ((showDescription && assistant.description.isNotBlank()) || supportingContent != null) {
            {
                Column {
                    if (showDescription && assistant.description.isNotBlank()) {
                        Text(
                            text = assistant.description,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = KedgeTextStyles.footnoteSmall(),
                        )
                    }
                    supportingContent?.invoke()
                }
            }
        } else null,
        trailingContent = trailingContent,
    )
}
