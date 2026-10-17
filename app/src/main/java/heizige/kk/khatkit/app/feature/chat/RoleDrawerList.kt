package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.ui.components.ai.AssistantItem
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.groups
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import kotlin.uuid.Uuid
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 行左右缩进：与 `CardGroup` / 角色面板的 option item 同一档（8dp）。 */
private val RoleRowInset = 8.dp
private val RoleTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val RoleDateFormatter = DateTimeFormatter.ofPattern("M/d")

/**
 * 抽屉唯一的一份列表：**角色**。
 *
 * 布局口径（2026-10 主人确认）：抽屉不再有「角色 / 聊天记录」切换，也不再单独列会话，
 * 会话历史统一从角色面板进；文件夹功能整体移除。所以这里只有一份**整体**列表：
 * 全部角色按原顺序排成一组，最后一行是「管理角色」；当前角色不单独置顶、也不分区，
 * 只用行尾一个勾标记。没有角色时，组首是一行不可点的说明（未找到 / 暂无角色）。
 *
 * **样式统一走 option item**：行的容器一律是 [AssistantItem] → [KedgeOptionItem]
 * （Miuix/MD3 由 Kedge 分流），圆角与行间空隙取自 [listCardStyle] 的
 * `indexedShape(index, count)` / `gap` —— 首项只圆上边（大圆角）、末项只圆下边
 * （大圆角）、中间项四角小圆角（MD3 小圆角 4dp / 项间 2dp 轻微间距；Miuix 直角 / 无缝），
 * 缩进与角色面板（[ChatRolePanelPage]）一致 —— 抽屉里不再自绘卡片、底色和分区分隔。
 */
@Composable
internal fun RoleDrawerList(
    roles: List<Assistant>,
    selected: Uuid,
    chats: List<Conversation>,
    running: Set<Uuid>,
    keyword: String,
    contentPadding: PaddingValues,
    onRole: (Assistant) -> Unit,
    providerName: (Assistant) -> String?,
    onManage: () -> Unit,
    header: @Composable () -> Unit,
) {
    val cards = listCardStyle()
    val visibleRoles = remember(roles, keyword) {
        roles.filter {
            keyword.isBlank() || it.name.contains(keyword, true) || it.description.contains(keyword, true)
        }
    }
    // Index the light conversation summaries once. The old per-row firstOrNull/any scans
    // multiplied work by roles * chats during every drawer recomposition.
    val chatsByRole = remember(chats) {
        buildMap<Uuid, Conversation> {
            chats.forEach { chat ->
                val roleIds = if (chat.groupConfig != null) {
                    chat.groupConfig.roles.mapNotNull { role ->
                        runCatching { Uuid.parse(role.assistantId) }.getOrNull()
                    }
                } else if (chat.type == GroupChat.TYPE_DIRECT) {
                    listOf(chat.assistantId)
                } else {
                    emptyList()
                }
                roleIds.forEach { roleId -> putIfAbsent(roleId, chat) }
            }
        }
    }
    val activeRoles = remember(chats, running) {
        buildSet<Uuid> {
            chats.filter { it.id in running }.forEach { chat ->
                val roleIds = if (chat.groupConfig != null) {
                    chat.groupConfig.roles.mapNotNull { role ->
                        runCatching { Uuid.parse(role.assistantId) }.getOrNull()
                    }
                } else if (chat.type == GroupChat.TYPE_DIRECT) {
                    listOf(chat.assistantId)
                } else {
                    emptyList()
                }
                addAll(roleIds)
            }
        }
    }
    // 一个整体：所有角色 + 末尾「管理角色」共用一个分组，只为首/末项算大圆角。
    val groupCount = visibleRoles.size + 1

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("drawer_role_list"),
        contentPadding = contentPadding,
    ) {
        item("header") { header() }

        if (visibleRoles.isEmpty()) {
            item("roles-empty") {
                KedgeOptionItem(
                    onClick = null,
                    modifier = Modifier
                        .padding(horizontal = RoleRowInset)
                        .padding(top = 8.dp, bottom = cards.gap),
                    shape = cards.indexedShape(0, groupCount),
                    leadingContent = { Icon(groups, null) },
                    titleContent = {
                        Text(text = if (keyword.isNotBlank()) "未找到角色" else "暂无角色")
                    },
                )
            }
        }
        visibleRoles.forEachIndexed { index, role ->
            val isLast = index == visibleRoles.lastIndex
            item("role-${role.id}") {
                RoleRow(
                    role = role,
                    recent = chatsByRole[role.id],
                    active = role.id in activeRoles,
                    selected = role.id == selected,
                    shape = cards.indexedShape(index, groupCount),
                    // 行间空隙只加在非末项上（末项要接「管理角色」，它自己带 gap）。
                    modifier = Modifier
                        .padding(horizontal = RoleRowInset)
                        .padding(top = if (index == 0) 8.dp else 0.dp)
                        .padding(bottom = if (isLast) 0.dp else cards.gap),
                    onClick = { onRole(role) },
                    providerName = providerName(role),
                )
            }
        }
        item("manage-role") {
            KedgeOptionItem(
                onClick = onManage,
                modifier = Modifier
                    .padding(horizontal = RoleRowInset)
                    .padding(top = if (visibleRoles.isEmpty()) 0.dp else cards.gap),
                shape = cards.indexedShape(groupCount - 1, groupCount),
                leadingContent = { Icon(groups, null) },
                titleContent = { Text("管理角色") },
                trailingContent = { Icon(arrowForward, null) },
            )
        }
    }
}

/**
 * 一行角色 = 一张 option item：头像 / 角色名 / 最近会话（或描述、正在回复）/ 时间，
 * 当前角色在行尾多一个勾。
 *
 * 容器与圆角全部交给 [AssistantItem] 与 [listCardStyle]，本组件只管槽位内容。
 */
@Composable
private fun RoleRow(
    role: Assistant,
    recent: Conversation?,
    active: Boolean,
    selected: Boolean,
    shape: Shape,
    modifier: Modifier,
    onClick: () -> Unit,
    providerName: String?,
) {
    // 名称为空时的兜底文案必须与顶栏 / 角色选择弹层 / 管理角色页**同一个资源**：
    // 这里原来写死 "角色"，而 ChatPage 顶栏用的是 assistant_page_default_assistant
    //（中文 "默认角色"），同一个空名角色在抽屉里叫「角色」、在顶栏叫「默认角色」，
    // 看起来就是「顶栏角色 ≠ 侧边栏已选角色」—— 其实就是同一个角色两种兜底文案。
    AssistantItem(
        assistant = role,
        defaultAssistantName = stringResource(R.string.assistant_page_default_assistant),
        shape = shape,
        avatarSize = 36.dp,
        providerName = providerName,
        showDescription = false,
        supportingContent = {
            Text(
                text = if (active) "正在回复…" else recent?.title?.ifBlank { null }
                    ?: role.description.ifBlank { "暂无消息" },
                // 不显式给 style/color：option item 的 supporting 槽由 KedgeOptionItem
                // 统一套字阶与取色（Miuix 走 body2，MD3 走 Khromia 的同档），
                // 与角色面板、设置页那些行逐像素一致。只有「正在回复」用 primary 强调。
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (selected) {
                    Icon(
                        imageVector = check,
                        contentDescription = "当前角色",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                recent?.let {
                    val local = it.updateAt.atZone(ZoneId.systemDefault())
                    val today = LocalDate.now()
                    Text(
                        text = when (local.toLocalDate()) {
                            today -> local.format(RoleTimeFormatter)
                            today.minusDays(1) -> "昨天"
                            else -> local.format(RoleDateFormatter)
                        },
                        style = KedgeTextStyles.footnoteSmall(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        modifier = modifier.testTag("role_${role.id}"),
        onClick = onClick,
    )
}
