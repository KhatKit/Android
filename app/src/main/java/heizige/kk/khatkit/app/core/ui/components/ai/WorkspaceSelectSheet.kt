package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import heizige.kk.khatkit.app.core.ui.icons.deployedCode
import heizige.kk.khatkit.app.core.ui.icons.folder
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection

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
    // U7b-6：直连 MochiSheet（state 由调用方持有），不再用 visible 兼容层。
    MochiSheet(state = sheetState, onDismiss = onDismiss) {
        MochiSheetHeader(title = stringResource(R.string.workspace_select))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MochiListSection {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // 不绑定
                    WorkspaceSelectRow(
                        title = stringResource(R.string.workspace_no_binding),
                        selected = assistant.workspaceId == null,
                        onClick = {
                            onSelect(null)
                            onDismiss()
                        },
                    )
                    workspaces.forEach { workspace ->
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
            }

            MochiListSection {
                MochiListRow(
                    title = stringResource(R.string.workspace_choose_directory),
                    onClick = onChooseDirectory,
                    leading = { Icon(folder, contentDescription = null) },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    chevron = true,
                )
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
