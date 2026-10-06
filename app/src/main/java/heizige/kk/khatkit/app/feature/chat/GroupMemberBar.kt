package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.model.Avatar
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.hooks.ChatInputState
import kotlin.uuid.Uuid

/**
 * 群聊输入框上方的成员头像组：一排成员头像，点一下就把 `@角色名 ` 插进输入框。
 *
 * 挂在 [ChatScaffold] 的 `bottomBarAboveInput` 插槽上（排在 [ChatInput] 之前），
 * 这样它在布局上就在输入框正上方，并被输入框的毛玻璃一起采样。
 *
 * ## 头像从哪来
 *
 * 每个角色自己带一个 `assistantId`，头像取那个助手的真实头像，而不是会话级助手的
 * ——与 [ChatList] 里按 `roleId` 解析角色助手是**同一份口径**（`assistantById` 映射 +
 * `Uuid.parse(role.assistantId)`）。解析必须 `runCatching`：`assistantId` 不是合法 Uuid
 * 或助手已被删除时退回 `null`，让 [UIAvatar] 渲染程序生成的占位头像，不让整排崩掉。
 *
 * ## 议长的视觉区分
 *
 * 外圈描一圈主题色（[CircleShape]，与 `rememberAvatarShape(false)` 用的同一个形状，
 * 非加载态头像就是正圆，描边严丝合缝）。不用角标是因为角标会盖住头像本身。
 *
 * ## 为什么不用 `ChatInputState.appendText`
 *
 * [ChatInputState.appendText] 走 `setTextAndPlaceCursorAtEnd`，**无视光标**、一律拼到末尾。
 * C1 之前的群聊页就是这么插 @ 的，于是「先打一半、退回来改中间那句」时，@ 会被插到最末尾，
 * 跟用户正在改的那句话完全脱节。这里改用 `TextFieldState.edit` 在**光标处**插入，
 * 并处理两件 `appendText` 不管的事（见 [mentionInsertion]）。
 */
@Composable
fun GroupMemberBar(
    config: GroupConfig?,
    settings: Settings,
    inputState: ChatInputState,
    modifier: Modifier = Modifier,
) {
    val roles = config?.roles.orEmpty()
    if (roles.isEmpty()) return

    // 与 ChatList 同一个 assistantById 写法：按 id 建索引，避免每个头像都线性扫 assistants。
    val assistantById = remember(settings.assistants) {
        settings.assistants.associateBy { it.id }
    }

    Row(
        modifier = modifier
            // C1 真机 UI 测试的定位锚点：UIAvatar 自身不带 contentDescription/testTag
            // （语义只有内部 KedgeSurface 的 clickable），头像组只能靠容器定位。
            // 仅新增语义属性，不改布局/行为。
            .testTag("group_member_bar")
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        roles.forEach { role ->
            val name = role.name.ifBlank { role.id }
            val assistant = runCatching { Uuid.parse(role.assistantId) }.getOrNull()
                ?.let { assistantById[it] }
            UIAvatar(
                name = name,
                value = assistant?.avatar ?: Avatar.Dummy,
                loading = false,
                modifier = if (role.chair) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
                onClick = { insertMention(inputState, name) },
            )
        }
    }
}

/**
 * 在**光标处**插入 `@<name> `。名字为空则什么都不做。
 *
 * 纯插入（`replace(cursor, cursor, …)`），光标之后的字原样留在后面。
 */
private fun insertMention(inputState: ChatInputState, name: String) {
    val text = inputState.textContent.text.toString()
    val cursor = inputState.textContent.selection.max.coerceIn(0, text.length)
    val insert = mentionInsertion(text, cursor, name) ?: return
    inputState.textContent.edit {
        replace(cursor, cursor, insert)
        selection = TextRange(cursor + insert.length)
    }
}

/**
 * 算出要在光标处插入的字符串；名字为空返回 null（不插入）。
 *
 * 提成纯函数是为了让「前导空格那条规则」可单测 —— 它是这里唯一有 nontrivial 判定的地方。
 *
 * **为什么要补前导空格**：`GroupChat.parseMentions` 的边界是
 * `(?<![\\p{L}\\p{N}_])@`。光标前一个字是汉字时，`你好@Alice ` **不算** mention ——
 * 无脑拼接产出的文本路由不到任何角色。假 @ 比不 @ 更糟（用户以为点名了，实际没人被点名），
 * 所以光标前不是空白时必须补一个空格。
 */
internal fun mentionInsertion(text: String, cursor: Int, name: String): String? {
    if (name.isBlank()) return null
    val at = cursor.coerceIn(0, text.length)
    val needsLeadingSpace = at > 0 && !text[at - 1].isWhitespace()
    return buildString {
        if (needsLeadingSpace) append(' ')
        append('@').append(name).append(' ')
    }
}
