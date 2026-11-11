package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.kkScrollEdgeFade
import ui.feice.mochi.navigation.MochiSidebarItem
import ui.feice.mochi.navigation.MochiSidebarSectionHeader
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.groups
import kotlin.uuid.Uuid
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
 * **SIDE1 起样式统一走 Mochi 侧栏**：分区标题 [MochiSidebarSectionHeader]，行 [MochiSidebarItem]
 * （胶囊高亮 / 按下反馈 / 选中底色淡入淡出 / 单行省略都由 Mochi 负责，页面不再自绘卡片、
 * 圆角与分隔）。UI-3 起行内只放「40dp 头像 + 单行标题」，行尾 trailing 只留
 * 「正在回复… / 时间 + 当前角色勾」——会话标题与角色描述不再挤进这一行
 * （MochiSidebarItem 只有单行标题槽，没有 supporting 第二行）。
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
    // 渐隐/渐进模糊跟着滚动位置走（滚到顶时顶边是实的），否则首行 —— 列表头里的
    // 「探索市场 / 下载中心」—— 会一直被顶边的固定渐隐 + 模糊糊住。
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("drawer_role_list").kkScrollEdgeFade(listState),
        state = listState,
        contentPadding = contentPadding,
    ) {
        item("header") { header() }

        // SIDE1：行列改用 Mochi 的侧栏组件（MochiSidebarSectionHeader / MochiSidebarItem）。
        // 胶囊高亮、按下反馈、选中底色淡入淡出、单行省略都由 Mochi 负责，这里只给数据与回调。
        item("section-roles") { MochiSidebarSectionHeader("角色") }

        if (visibleRoles.isEmpty()) {
            item("roles-empty") {
                MochiSidebarItem(
                    title = if (keyword.isNotBlank()) "未找到角色" else "暂无角色",
                    onClick = {},
                    leading = { Icon(groups, null) },
                    modifier = Modifier.testTag("drawer_roles_empty"),
                )
            }
        }
        visibleRoles.forEach { role ->
            val recent = chatsByRole[role.id]
            val active = role.id in activeRoles
            item("role-${role.id}") {
                val displayName = role.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }
                MochiSidebarItem(
                    title = displayName,
                    onClick = { onRole(role) },
                    selected = role.id == selected,
                    leading = {
                        // UI-3：头像按 MochiSidebarItem 度量给 40dp（原来 28dp，比行高小一半，
                        // 圆点像「状态点」不像头像）。形状 / 底色 / 默认供应商图标与角色设置页
                        // 的 AssistantItem 同一套参数，两处头像长得一样。
                        UIAvatar(
                            name = displayName,
                            value = role.avatar,
                            avatarShape = role.avatarShape,
                            defaultIconName = providerName(role),
                            avatarColor = role.avatarColor,
                            modifier = Modifier.size(40.dp),
                        )
                    },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // UI-3：行尾只留「状态/时间 + 当前勾」。原来这里还塞了会话标题或
                            // 角色描述（96dp 上限），一行里同时出现名称、描述、时间、勾，
                            // 主次不分（见 artifacts/ui-review/2026-10-31/sidebar-roles.jpg）。
                            if (active) {
                                Text(
                                    text = "正在回复…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            // 注意：这里不能写 `if (selected)` —— 外层的 `selected` 是 Uuid 参数，
                            // 名字与 MochiSidebarItem 的 selected 形参同一个词，必须显式比较。
                            if (role.id == selected) {
                                Icon(
                                    imageVector = check,
                                    contentDescription = "当前角色",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("role_${role.id}"),
                )
            }
        }
        item("manage-role") {
            MochiSidebarItem(
                title = "管理角色",
                onClick = onManage,
                leading = { Icon(groups, null) },
                trailing = { Icon(arrowForward, null) },
                modifier = Modifier.testTag("drawer_manage_role"),
            )
        }
    }
}


