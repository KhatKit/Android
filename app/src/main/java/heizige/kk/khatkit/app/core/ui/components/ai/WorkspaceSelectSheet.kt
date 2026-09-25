package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import heizige.kk.khromia.components.PrimaryBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.entity.WorkspaceEntity
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.ui.pages.extensions.workspace.toShellStatusLabel
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.deployedCode
import heizige.kk.khatkit.app.core.ui.icons.doneAll
import heizige.kk.khatkit.app.core.ui.icons.terminal

@Composable
internal fun WorkspaceSelectSheet(
    assistant: Assistant,
    workspaces: List<WorkspaceEntity>,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.workspace_select),
        imageVector = terminal,
        onDismiss = onDismiss,
        scrollable = false,
    ) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 不绑定
                WorkspaceSelectRow(
                    title = stringResource(R.string.workspace_no_binding),
                    selected = assistant.workspaceId == null,
                    onClick = {
                        onSelect(null)
                        dismiss()
                    },
                )
                workspaces.forEach { workspace ->
                    WorkspaceSelectRow(
                        title = workspace.name,
                        status = workspace.shellStatus.toShellStatusLabel(),
                        selected = workspace.id == assistant.workspaceId?.toString(),
                        onClick = {
                            onSelect(workspace.id)
                            dismiss()
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 管理工作区
            ListItem(
                leadingContent = {
                    Icon(deployedCode, contentDescription = null)
                },
                headlineContent = {
                    Text(stringResource(R.string.workspace_manage))
                },
                trailingContent = {
                    Icon(
                        imageVector = arrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onManage() },
            )
        }
    }
}

@Composable
private fun WorkspaceSelectRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    status: String? = null,
) {
    ListItem(
        leadingContent = {
            Icon(deployedCode, contentDescription = null)
        },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = status?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = if (selected) {
            {
                Icon(
                    imageVector = doneAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else null,
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                Color.Transparent
            }
        ),
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() },
    )
}
