package heizige.kk.khatkit.app.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeFilterChip
import heizige.kk.kedge.components.KedgeListItem
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_ASSISTANTS_IDS
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.Tag
import heizige.kk.khatkit.app.core.ui.components.ui.TagType
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.hooks.EditState
import heizige.kk.khatkit.app.core.ui.hooks.EditStateContent
import heizige.kk.khatkit.app.core.ui.hooks.heroAnimation
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.core.ui.modifier.onClick
import heizige.kk.khatkit.app.feature.assistant.detail.AssistantImporter
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid
import androidx.compose.foundation.lazy.items as lazyItems
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.contentCopy
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.neurology
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormRow
import heizige.kk.kedge.components.KedgeTextField
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import heizige.kk.kedge.components.KedgeHorizontalDivider
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface

@Composable
fun AssistantPage(vm: AssistantViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val createState = useEditState<Assistant> {
        vm.addAssistant(it)
    }
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 搜索关键词状态
    var searchQuery by remember { mutableStateOf("") }
    // 标签过滤状态
    var selectedTagIds by remember { mutableStateOf(emptySet<Uuid>()) }
    // 操作菜单状态
    var actionSheetAssistant by remember { mutableStateOf<Assistant?>(null) }

    // 根据搜索关键词和选中的标签过滤助手
    val filteredAssistants = remember(settings.assistants, selectedTagIds, searchQuery) {
        settings.assistants.filter { assistant ->
            val matchesSearch = searchQuery.isBlank() ||
                assistant.name.contains(searchQuery, ignoreCase = true)
            val matchesTags = selectedTagIds.isEmpty() ||
                assistant.tags.any { tagId -> tagId in selectedTagIds }
            matchesSearch && matchesTags
        }
    }

    KedgePageScaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.assistant_page_title),
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
                        MiuixIconButton(onClick = { createState.open(Assistant()) }) {
                            MiuixIcon(add, contentDescription = stringResource(R.string.assistant_page_add))
                        }
                    } else {
                        KedgeIconButton(
                            onClick = { createState.open(Assistant()) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(add, stringResource(R.string.assistant_page_add))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.pageContainerColor,
    
        md3ScrollBehavior = scrollBehavior,
    ) { it ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(top = 16.dp)
                .consumeWindowInsets(it),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val lazyListState = rememberLazyListState()
            val isFiltering = selectedTagIds.isNotEmpty() || searchQuery.isNotBlank()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                if (!isFiltering) {
                    val newAssistants = settings.assistants.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                    vm.updateSettings(settings.copy(assistants = newAssistants))
                }
            }
            val haptic = LocalHapticFeedback.current

            // 搜索框：Miuix 下用 KedgeTextField（双风格组件，内部按 style 分支）
            if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
                KedgeTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = stringResource(R.string.assistant_page_search_placeholder),
                    leadingIcon = {
                        MiuixIcon(search, contentDescription = null)
                    },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        {
                            MiuixIconButton(onClick = { searchQuery = "" }) {
                                MiuixIcon(close, contentDescription = null)
                            }
                        }
                    } else null,
                    singleLine = true,
                )
            } else KedgeOutlinedTextFieldWithSlots(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.assistant_page_search_placeholder)) },
                leadingIcon = {
                    Icon(search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        KedgeIconButton(onClick = { searchQuery = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp))

            // 标签过滤器
            AssistantTagsFilterRow(
                settings = settings,
                vm = vm,
                selectedTagIds = selectedTagIds,
                onUpdateSelectedTagIds = { ids ->
                    selectedTagIds = ids
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = lazyListState,
            ) {
                lazyItems(filteredAssistants, key = { assistant -> assistant.id }) { assistant ->
                    ReorderableItem(
                        state = reorderableState,
                        key = assistant.id,
                    ) { isDragging ->
                        val memories by vm.getMemories(assistant).collectAsStateWithLifecycle(
                            initialValue = emptyList(),
                        )
                        AssistantItem(
                            assistant = assistant,
                            settings = settings,
                            memories = memories,
                            onEdit = {
                                navController.navigate(Screen.AssistantDetail(id = assistant.id.toString()))
                            },
                            onShowActions = {
                                actionSheetAssistant = assistant
                            },
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .fillMaxWidth()
                                .animateItem()
                                .then(
                                    if (!isFiltering) {
                                        Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            },
                                            onDragStopped = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            }
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }

                item(key = "new_assistant") {
                    // 双风格：Miuix 走 PreferenceArrow（miuix-preference 原生行），
                    // MD3 保持原 Surface。
                    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
                        PreferenceArrow(
                            title = stringResource(R.string.assistant_page_new),
                            icon = add,
                            onClick = { createState.open(Assistant()) },
                        )
                    } else {
                    KedgeSurface(
                        onClick = { createState.open(Assistant()) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(add, contentDescription = null)
                            Text(
                                text = stringResource(R.string.assistant_page_new),
                                style = KedgeTextStyles.title(),
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    AssistantCreationSheet(createState)

    // 操作菜单 Bottom Sheet
    actionSheetAssistant?.let { assistant ->
        AssistantActionSheet(
            assistant = assistant,
            onDismiss = { actionSheetAssistant = null },
            onCopy = {
                vm.copyAssistant(assistant)
                actionSheetAssistant = null
            },
            onDelete = {
                vm.removeAssistant(assistant)
                actionSheetAssistant = null
            }
        )
    }
}

@Composable
private fun AssistantTagsFilterRow(
    settings: Settings,
    vm: AssistantViewModel,
    selectedTagIds: Set<Uuid>,
    onUpdateSelectedTagIds: (Set<Uuid>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    if (settings.assistantTags.isNotEmpty()) {
        val tagsListState = rememberLazyListState()
        val tagsReorderableState = rememberReorderableLazyListState(tagsListState) { from, to ->
            val newTags = settings.assistantTags.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            vm.updateSettings(settings.copy(assistantTags = newTags))
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
            state = tagsListState
        ) {
            lazyItems(items = settings.assistantTags, key = { tag -> tag.id }) { tag ->
                ReorderableItem(
                    state = tagsReorderableState, key = tag.id
                ) { isDragging ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        KedgeFilterChip(
                            onClick = {
                                onUpdateSelectedTagIds(
                                    if (tag.id in selectedTagIds) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                )
                            },
                            label = {
                                Text(tag.name)
                            },
                            selected = tag.id in selectedTagIds,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantCreationSheet(
    state: EditState<Assistant>,
) {
    state.EditStateContent { assistant, update ->
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.assistant_page_add),
            imageVector = neurology,
            confirmText = stringResource(R.string.assistant_page_save),
            onConfirm = {
                state.confirm()
            },
            onDismiss = {
                state.dismiss()
            },
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    KedgeFormRow(
                        label = {
                            Text(stringResource(R.string.assistant_page_name))
                        },
                    ) {
                        KedgeOutlinedTextField(
                            value = assistant.name, onValueChange = {
                                update(
                                    assistant.copy(
                                        name = it
                                    )
                                )
                            }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    AssistantImporter(
                        onUpdate = {
                            update(it)
                            state.confirm()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantItem(
    assistant: Assistant,
    settings: Settings,
    modifier: Modifier = Modifier,
    memories: List<AssistantMemory>,
    onEdit: () -> Unit,
    onShowActions: () -> Unit,
) {
    KedgeCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onEdit,
        shape = if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
            // Miuix 列表卡统一 20dp 圆角（对齐 KSU SettingsMiuix 的卡片）
            RoundedCornerShape(20.dp)
        } else {
            CardDefaults.shape
        },
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIAvatar(
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                value = assistant.avatar,
                modifier = Modifier
                    .size(48.dp)
                    .heroAnimation("assistant_${assistant.id}")
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {

                Text(
                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    style = KedgeTextStyles.title(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (assistant.enableMemory) {
                        Tag(type = TagType.SUCCESS) {
                            Text(stringResource(R.string.assistant_page_memory_count, memories.size))
                        }
                    }

                    if (assistant.tags.isNotEmpty()) {
                        assistant.tags.take(2).fastForEach { tagId ->
                            val tag = settings.assistantTags.find { it.id == tagId }
                                ?: return@fastForEach
                            KedgeSurface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    text = tag.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = KedgeTextStyles.footnoteSmall(),
                                )
                            }
                        }
                        if (assistant.tags.size > 2) {
                            Text(
                                text = "+${assistant.tags.size - 2}",
                                style = KedgeTextStyles.footnoteSmall(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            KedgeIconButton(
                onClick = onShowActions,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = moreVert,
                    contentDescription = stringResource(R.string.assistant_page_actions)
                )
            }
        }
    }
}

@Composable
private fun AssistantActionSheet(
    assistant: Assistant,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.assistant_page_actions),
        imageVector = moreVert,
        onDismiss = onDismiss,
    ) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // 助手信息头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UIAvatar(
                    name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    value = assistant.avatar,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    style = KedgeTextStyles.title()
                )
            }

            KedgeHorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 克隆选项
            KedgeListItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_clone)) },
                leadingContent = {
                    Icon(
                        imageVector = contentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.onClick {
                    onCopy()
                    dismiss()
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // 删除选项（仅非默认助手显示）
            if (assistant.id !in DEFAULT_ASSISTANTS_IDS) {
                KedgeListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.assistant_page_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.onClick { showDeleteDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AppAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.assistant_page_delete)) },
            text = { Text(stringResource(R.string.assistant_page_delete_dialog_text)) },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                         shapes = ButtonDefaults.shapes(),) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                KedgeTextButton(onClick = { showDeleteDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
