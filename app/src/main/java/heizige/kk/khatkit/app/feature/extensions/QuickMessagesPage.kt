package heizige.kk.khatkit.app.feature.extensions

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.model.QuickMessage
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.RikkaConfirmDialog
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.util.plus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.kedge.components.KedgeFloatingActionButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun QuickMessagesPage(vm: QuickMessagesViewModel = hiltViewModel()) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<QuickMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<QuickMessage?>(null) }

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.assistant_page_quick_messages),
        scrollBehavior = scrollBehavior,
        floatingActionButton = {
            KedgeFloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(add, contentDescription = null)
            }
        },
    ) { innerPadding ->
        // 与助手列表/提供商列表同款分组：整列看着是一张卡片，圆角按 index/count 逐项算
        // （首项只圆上边、末项只圆下边、中间项直角），项间距取全局列表卡片样式。
        // 刻意不用 CardGroup —— 那会把所有项收进一个 Column，丢掉「每项一个 lazy item」。
        val cards = listCardStyle()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(cards.gap),
        ) {
            if (settings.quickMessages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = bolt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.quick_messages_page_empty_title),
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.quick_messages_page_empty_hint),
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            lazyItemsIndexed(settings.quickMessages, key = { _, quickMessage -> quickMessage.id }) { index, quickMessage ->
                QuickMessageItem(
                    quickMessage = quickMessage,
                    shape = cards.indexedShape(index, settings.quickMessages.size),
                    onEdit = { editTarget = quickMessage },
                    onDelete = { deleteTarget = quickMessage },
                )
            }
        }
    }

    if (showAddDialog) {
        EditQuickMessageDialog(
            title = stringResource(R.string.quick_messages_page_add_title),
            initialQuickMessage = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, content ->
                vm.addQuickMessage(title, content)
                showAddDialog = false
            },
        )
    }

    editTarget?.let { quickMessage ->
        EditQuickMessageDialog(
            title = stringResource(R.string.quick_messages_page_edit_title),
            initialQuickMessage = quickMessage,
            onDismiss = { editTarget = null },
            onConfirm = { title, content ->
                vm.updateQuickMessage(
                    quickMessage.copy(
                        title = title,
                        content = content,
                    )
                )
                editTarget = null
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.quick_messages_page_delete_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { vm.deleteQuickMessage(it.id) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.quick_messages_page_delete_message, deleteTarget?.title ?: ""))
    }
}

@Composable
private fun QuickMessageItem(
    quickMessage: QuickMessage,
    shape: Shape,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    KedgeOptionItem(
        // 整行原本就不可点（编辑/删除都走右侧「更多」菜单），所以传 null：
        // 两种风格下都是纯信息行，没有按压回弹。底色走组件默认，不再自己上 cardColors。
        onClick = null,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        leadingContent = {
            Icon(
                imageVector = bolt,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        titleContent = {
            Text(
                text = quickMessage.title.ifBlank { stringResource(R.string.quick_messages_page_untitled) },
                style = KedgeTextStyles.title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = quickMessage.content.ifBlank { stringResource(R.string.quick_messages_page_empty_content) },
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                KedgeIconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = moreVert,
                        contentDescription = stringResource(R.string.skills_page_more_actions),
                    )
                }
                KedgeDropdownMenuSlots(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    KedgeDropdownItemSlot(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = {
                            Icon(
                                imageVector = edit,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    KedgeDropdownItemSlot(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
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
private fun EditQuickMessageDialog(
    title: String,
    initialQuickMessage: QuickMessage?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String) -> Unit,
) {
    var quickMessageTitle by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.title ?: "")
    }
    var quickMessageContent by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.content ?: "")
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KedgeOutlinedTextFieldWithSlots(
                    value = quickMessageTitle,
                    onValueChange = { quickMessageTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_quick_message_title)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                KedgeOutlinedTextFieldWithSlots(
                    value = quickMessageContent,
                    onValueChange = { quickMessageContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_quick_message_content)) },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            KedgeTextButton(
                onClick = { onConfirm(quickMessageTitle.trim(), quickMessageContent.trim()) },
                enabled = quickMessageTitle.isNotBlank() && quickMessageContent.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.assistant_page_save))
            }
        },
        dismissButton = {
            KedgeTextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
