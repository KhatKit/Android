package heizige.kk.khatkit.app.feature.chat.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.entity.WorkspaceEntity
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.feature.workspace.toShellStatusLabel
import heizige.kk.khatkit.app.core.ui.icons.utility.deployedCode
import heizige.kk.khatkit.app.core.ui.icons.files.folder
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.mochiListSection
import ui.feice.mochi.list.rows

@Composable
internal fun WorkspaceSelectSheet(
    sheetState: MochiSheetState,
    assistant: Assistant,
    workspaces: List<WorkspaceEntity>,
    onSelect: (String?) -> Unit,
    onChooseDirectory: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    PrimaryBottomSheet(
        state = sheetState,
        title = stringResource(R.string.workspace_select),
        imageVector = deployedCode,
        onDismiss = onDismiss,
        scrollable = false,
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            flingBehavior = MochiScrollDefaults.flingBehavior(),
        ) {
            mochiListSection("workspaces") {
                row {
                    WorkspaceSelectRow(
                        title = stringResource(R.string.workspace_no_binding),
                        selected = assistant.workspaceId == null,
                        onClick = {
                            onSelect(null)
                            onDismiss()
                        },
                    )
                }
                rows(workspaces, key = { it.id }) { workspace ->
                    WorkspaceSelectRow(
                        title = workspace.name,
                        status = workspace.shellStatus.toShellStatusLabel(),
                        selected = workspace.id == assistant.workspaceId?.toString(),
                        onClick = {
                            onSelect(workspace.id)
                            onDismiss()
                        },
                    )
                }
            }
            mochiListSection("workspace-actions") {
                row {
                    MochiListRow(
                        title = stringResource(R.string.workspace_choose_directory),
                        onClick = onChooseDirectory,
                        leading = { Icon(folder, contentDescription = null) },
                        leadingStyle = MochiListLeadingStyle.Symbol,
                        chevron = true,
                    )
                }
                row {
                    MochiListRow(
                        title = stringResource(R.string.workspace_manage),
                        onClick = onManage,
                        leading = { Icon(deployedCode, contentDescription = null) },
                        leadingStyle = MochiListLeadingStyle.Symbol,
                        chevron = true,
                    )
                }
            }
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
    MochiListRow(
        title = title,
        subtitle = status?.takeIf { it.isNotBlank() },
        onClick = onClick,
        leading = { Icon(deployedCode, contentDescription = null) },
        leadingStyle = MochiListLeadingStyle.Symbol,
        checked = selected,
    )
}
