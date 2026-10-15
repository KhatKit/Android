package heizige.kk.khatkit.app.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.db.entity.WorkspaceEntity
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.RikkaConfirmDialog
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.insertDriveFile
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.kedge.components.KedgeFloatingActionButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun WorkspacePage(vm: WorkspaceViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.workspace_page_title),
        scrollBehavior = scrollBehavior,
        floatingActionButton = {
            KedgeFloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(add, contentDescription = null)
            }
        },
    ) { innerPadding ->
        // 与助手/提供商列表同一套分组：整列看着是一张卡片，圆角按 index/count 逐项算
        // （首项只圆上边、末项只圆下边、中间项直角），项间距取全局列表卡片样式。
        // 不用 CardGroup —— 那会把所有项收进一个 Column，丢掉「每项一个 lazy item」
        // 与稳定 key。
        val cards = listCardStyle()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(cards.gap),
        ) {
            if (workspaces.isEmpty()) {
                item {
                    EmptyWorkspaceState()
                }
            }

            lazyItemsIndexed(workspaces, key = { _, workspace -> workspace.id }) { index, workspace ->
                WorkspaceItem(
                    workspace = workspace,
                    shape = cards.indexedShape(index, workspaces.size),
                    onRename = { editTarget = workspace },
                    onDelete = { deleteTarget = workspace },
                    onOpen = { navController.navigate(Screen.WorkspaceDetail(workspace.id)) },
                )
            }
        }
    }

    if (showAddDialog) {
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_create),
            initialName = "",
            existingNames = workspaces.map { it.name.trim() }.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                vm.create(name)
                showAddDialog = false
            },
        )
    }

    editTarget?.let { workspace ->
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_rename),
            initialName = workspace.name,
            existingNames = workspaces.filter { it.id != workspace.id }.map { it.name.trim() }.toSet(),
            onDismiss = { editTarget = null },
            onConfirm = { name ->
                vm.rename(workspace, name)
                editTarget = null
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.workspace_page_delete),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            deleteTarget?.let { vm.delete(it) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.workspace_page_delete_confirm))
    }
}

@Composable
private fun EmptyWorkspaceState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = insertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.workspace_page_empty),
            style = KedgeTextStyles.body(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.workspace_page_empty_desc),
            style = KedgeTextStyles.body(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkspaceItem(
    workspace: WorkspaceEntity,
    shape: Shape,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // 行内容全部走 KedgeOptionItem 槽位，双风格分流在组件内部（见 KedgeOptionItem.kt），
    // 调用点不再写风格分支、也不再自己上 cardColors。
    KedgeOptionItem(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        leadingContent = {
            Icon(
                imageVector = insertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        titleContent = {
            Text(
                text = workspace.name,
                style = KedgeTextStyles.title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = workspace.shellStatus.toShellStatusLabel(),
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                KedgeIconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(moreVert, contentDescription = null)
                }
                KedgeDropdownMenuSlots(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    KedgeDropdownItemSlot(
                        text = { Text(stringResource(R.string.common_rename)) },
                        leadingIcon = { Icon(edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    KedgeDropdownItemSlot(
                        text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                imageVector = delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun EditWorkspaceDialog(
    title: String,
    initialName: String,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val trimmedName = name.trim()
    val isDuplicate = trimmedName.isNotEmpty() && trimmedName in existingNames

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            KedgeOutlinedTextFieldWithSlots(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workspace_page_name)) },
                singleLine = true,
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text(stringResource(R.string.workspace_page_name_duplicate)) }
                } else null,
                shape = RoundedCornerShape(16.dp)
            )
        },
        confirmButton = {
            KedgeTextButton(
                onClick = { onConfirm(trimmedName) },
                enabled = name.isNotBlank() && !isDuplicate,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            KedgeTextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
