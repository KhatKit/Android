package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.repository.includesRole
import heizige.kk.khatkit.app.core.ui.components.ai.AssistantItem
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeTextTooltipBox
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.kedge.theme.KedgeTextStyles
import kotlin.uuid.Uuid
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun RoleDrawerList(
    roles: List<Assistant>,
    selected: Uuid,
    chats: List<Conversation>,
    running: Set<Uuid>,
    keyword: String,
    contentPadding: PaddingValues,
    onRole: (Assistant) -> Unit,
    onManage: () -> Unit,
    header: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("drawer_role_list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item("header") { header() }
        item("roles-heading") { RoleListHeading("角色", "管理角色", onManage) }
        val visibleRoles = roles.filter { keyword.isBlank() || it.name.contains(keyword, true) ||
            it.description.contains(keyword, true) }
        if (visibleRoles.isEmpty()) {
            item("empty") {
                Text(
                    if (keyword.isBlank()) "暂无角色" else "未找到角色",
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        }
        items(visibleRoles, key = { it.id.toString() }) { role ->
            val recent = chats.firstOrNull { it.includesRole(role.id) }
            val active = chats.any { it.id in running && it.includesRole(role.id) }
            AssistantItem(
                assistant = role,
                defaultAssistantName = "角色",
                shape = RoundedCornerShape(8.dp),
                avatarSize = 44.dp,
                showDescription = false,
                backgroundColor = if (role.id == selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                supportingContent = {
                    Text(
                        text = if (active) "正在回复…" else recent?.title?.ifBlank { null }
                            ?: role.description.ifBlank { "暂无消息" },
                        style = KedgeTextStyles.body(),
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    recent?.let {
                        val local = it.updateAt.atZone(ZoneId.systemDefault())
                        val today = LocalDate.now()
                        Text(
                            text = when (local.toLocalDate()) {
                                today -> local.format(DateTimeFormatter.ofPattern("HH:mm"))
                                today.minusDays(1) -> "昨天"
                                else -> local.format(DateTimeFormatter.ofPattern("M/d"))
                            },
                            style = KedgeTextStyles.footnoteSmall(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                modifier = Modifier.heightIn(min = 76.dp).testTag("role_${role.id}"),
                onClick = { onRole(role) },
            )
        }
    }
}

@Composable
private fun RoleListHeading(title: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = KedgeTextStyles.footnoteSmall(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        KedgeTextTooltipBox(action) {
            KedgeIconButton(onClick = onAction) { Icon(add, action, modifier = Modifier.size(20.dp)) }
        }
    }
}
