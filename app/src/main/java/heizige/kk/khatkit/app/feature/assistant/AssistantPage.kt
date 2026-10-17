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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.khatkit.app.core.ui.components.ui.activeNestedScroll
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
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
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeCheckbox
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeSearchBar
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_ASSISTANTS_IDS
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.getCurrentChatModel
import heizige.kk.khatkit.app.core.data.model.RoleTemplates
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.app.core.ui.components.ai.ModelSelector
import heizige.kk.khatkit.app.core.ui.components.ai.ReasoningButton
import heizige.kk.khatkit.app.core.ui.components.ui.Select
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ai.AssistantItem
import heizige.kk.khatkit.app.core.ui.components.ui.Tag
import heizige.kk.khatkit.app.core.ui.components.ui.TagType
import heizige.kk.khatkit.app.core.ui.components.ui.RikkaConfirmDialog
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
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.contentCopy
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.neurology
import heizige.kk.khatkit.app.core.ui.icons.search
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
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
    // 搜索框展开态（KSU SuperSearchBar 的全屏搜索）
    var searchExpanded by remember { mutableStateOf(false) }
    // 标签过滤状态
    var selectedTagIds by remember { mutableStateOf(emptySet<Uuid>()) }
    // 操作菜单状态
    var actionSheetAssistant by remember { mutableStateOf<Assistant?>(null) }
    var cloneTarget by remember { mutableStateOf<Assistant?>(null) }
    var cloneWithMemories by remember { mutableStateOf(false) }

    // 根据搜索关键词和选中的标签过滤助手
    val filteredAssistants = remember(settings.assistants, selectedTagIds, searchQuery) {
        settings.assistants.filter { assistant ->
            val matchesSearch = searchQuery.isBlank() ||
                assistant.name.contains(searchQuery, ignoreCase = true) ||
                assistant.description.contains(searchQuery, ignoreCase = true) ||
                assistant.id.toString().contains(searchQuery, ignoreCase = true)
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
                        MiuixIconButton(onClick = { createState.open(Assistant(chatModelId = settings.getCurrentChatModel()?.id,
                            enableMemory = true, autoExtractMemory = true, enableRecentChatsReference = true)) }) {
                            MiuixIcon(add, contentDescription = stringResource(R.string.assistant_page_add))
                        }
                    } else {
                        KedgeIconButton(
                            onClick = { createState.open(Assistant(chatModelId = settings.getCurrentChatModel()?.id,
                                enableMemory = true, autoExtractMemory = true, enableRecentChatsReference = true)) },
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
        // Miuix 下 KedgePageScaffold 已接 Miuix 折叠行为，再挂 MD3 连接会吃掉全部
        // 滚动增量（整页滑不动），所以按风格取。
        modifier = activeNestedScroll(scrollBehavior),
        containerColor = CustomColors.pageContainerColor,
    
        md3ScrollBehavior = scrollBehavior,
    ) { it ->
        // KedgeSearchBar 的 16dp 外边距**只在 MD3 分支自带**（Miuix 分支为 0，由调用点给，
        // 见组件里 horizontalPadding / topPadding 的注释）。所以这一页的补白必须按风格给：
        // MD3 再补一层就是 32dp —— 比其它页宽一圈，也和本页 16dp 的列表卡片对不齐。
        val isMiuix = LocalKedgeStyle.current == KedgeStyle.Miuix

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(top = if (isMiuix) 16.dp else 0.dp)
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

            // 助手列表：折叠态挂在页面里，展开态搬进搜索框的全屏槽，所以抽成 lambda 复用。
            val assistantList: @Composable (Modifier) -> Unit = { listModifier ->
                val cards = listCardStyle()
                LazyColumn(
                    modifier = listModifier,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    // 项间距取全局列表卡片样式：整列看起来是一张分组卡片，
                    // 圆角按 index/count 逐项算（首项只圆上边、末项只圆下边）。
                    // 这里刻意没用 CardGroup：那一版是一次性把所有项收进 Column，
                    // 会丢掉 ReorderableItem 需要的「每项一个 lazy item」，长按拖拽排序就没了。
                    verticalArrangement = Arrangement.spacedBy(cards.gap),
                    state = lazyListState,
                ) {
                    lazyItemsIndexed(filteredAssistants, key = { _, assistant -> assistant.id }) { index, assistant ->
                        ReorderableItem(
                            state = reorderableState,
                            key = assistant.id,
                        ) { isDragging ->
                            val memoryCount by remember(assistant.id) { vm.getMemoryCount(assistant) }.collectAsStateWithLifecycle(
                                initialValue = 0,
                            )
                            // 支持行只在真有记忆角标/标签时才占位，空 Row 会在标题下留一道死间距。
                            val hasSupporting = assistant.canManageRoles || assistant.enableMemory || assistant.tags.isNotEmpty()
                            AssistantItem(
                                assistant = assistant,
                                defaultAssistantName = stringResource(R.string.assistant_page_default_assistant),
                                shape = cards.indexedShape(index, filteredAssistants.size),
                                // 头像 → 详情页的共享元素转场属于本页，不进共享行。
                                avatarModifier = Modifier.heroAnimation("assistant_${assistant.id}"),
                                supportingContent = if (hasSupporting) {
                                    {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (assistant.canManageRoles) {
                                                Tag(type = TagType.SUCCESS) { Text("主管") }
                                            }
                                            if (assistant.enableMemory) {
                                                Tag(type = TagType.SUCCESS) {
                                                    Text(stringResource(R.string.assistant_page_memory_count, memoryCount))
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
                                } else {
                                    null
                                },
                                trailingContent = {
                                    KedgeIconButton(
                                        onClick = { actionSheetAssistant = assistant },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(
                                            imageVector = moreVert,
                                            contentDescription = stringResource(R.string.assistant_page_actions)
                                        )
                                    }
                                },
                                onClick = {
                                    navController.navigate(Screen.AssistantDetail(id = assistant.id.toString()))
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
                        // 独立成组：与上面助手组之间留 16dp，圆角自己走全局列表卡片样式。
                        // 双风格都交给 KedgeOptionItem 内部分流（MD3Exp / Miuix），
                        // 不再在这里手写 PreferenceArrow 分支。
                        KedgeOptionItem(
                            onClick = { createState.open(Assistant(chatModelId = settings.getCurrentChatModel()?.id)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            shape = cards.indexedShape(0, 1),
                            leadingContent = { Icon(add, contentDescription = null) },
                            titleContent = {
                                Text(
                                    text = stringResource(R.string.assistant_page_new),
                                    style = KedgeTextStyles.title(),
                                )
                            },
                        )
                    }
                }
            }

            // KSU 式药丸搜索框（KernelSU SuperSearchBar 同一套逻辑）：点一下折叠药丸，
            // 展开成铺满全屏的搜索页 + 取消键；返回键收起，关键词同时清空。
            // 展开期间标签过滤行与助手列表整个抽掉（KSU 的 SearchBox 同款）。
            KedgeSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.assistant_page_search_placeholder),
                active = searchExpanded,
                onActiveChange = { searchExpanded = it },
                cancelLabel = stringResource(R.string.cancel),
                // MD3 侧留白由组件自己给（跟提供商/搜索页同款 16dp），Miuix 侧才要调用点补。
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isMiuix) 16.dp else 0.dp),
                expandedContent = { NoHeroTransition { NoHeroTransition { assistantList(Modifier.fillMaxSize()) } } },
                // 不传 showResultsWhenEmpty，用默认的 false：点开搜索后先是一片空白，
                // 输入才把助手列表淡入（"不输入就不展示"）。收起后列表回到页面里，
                // 「新建助手」那一项自然也就回来了。
            )

            if (!searchExpanded) {
                // 标签过滤器
                AssistantTagsFilterRow(
                    settings = settings,
                    vm = vm,
                    selectedTagIds = selectedTagIds,
                    onUpdateSelectedTagIds = { ids ->
                        selectedTagIds = ids
                    }
                )

                assistantList(
                    Modifier
                        .fillMaxSize()
                        .imePadding(),
                )
            }
        }
    }

    AssistantCreationSheet(createState, settings)

    // 操作菜单 Bottom Sheet
    actionSheetAssistant?.let { assistant ->
        val memoryCount by remember(assistant.id) { vm.getMemoryCount(assistant) }.collectAsStateWithLifecycle(initialValue = 0)
        AssistantActionSheet(
            assistant = assistant,
            onDismiss = { actionSheetAssistant = null },
            onCopy = {
                if (memoryCount > 0) {
                    cloneWithMemories = false
                    cloneTarget = assistant
                    actionSheetAssistant = null
                } else {
                    vm.copyAssistant(assistant)
                    actionSheetAssistant = null
                }
            },
            onDelete = {
                vm.removeAssistant(assistant)
                actionSheetAssistant = null
            }
        )
    }

    RikkaConfirmDialog(
        show = cloneTarget != null,
        title = stringResource(R.string.assistant_page_clone),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            cloneTarget?.let { vm.copyAssistant(it, copyMemories = cloneWithMemories) }
            cloneTarget = null
        },
        onDismiss = { cloneTarget = null },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = cloneWithMemories,
                    role = Role.Checkbox,
                    onValueChange = { cloneWithMemories = it },
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KedgeCheckbox(checked = cloneWithMemories, onCheckedChange = null)
            Text(stringResource(R.string.assistant_page_clone_with_memories))
        }
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
    settings: Settings,
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
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val templates = listOf("自定义角色", "Toolbox 主管")
                    val toolbox = RoleTemplates.toolbox(modelId = assistant.chatModelId, id = assistant.id)
                    Select(
                        options = templates,
                        selectedOption = if (assistant.systemPrompt == toolbox.systemPrompt) templates[1] else templates[0],
                        onOptionSelected = { template ->
                            update(if (template == templates[1]) toolbox else Assistant(
                                id = assistant.id,
                                chatModelId = assistant.chatModelId,
                                enableMemory = true,
                                autoExtractMemory = true,
                                enableRecentChatsReference = true,
                            ))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UIAvatar(
                        value = assistant.avatar,
                        name = assistant.name,
                        onUpdate = { update(assistant.copy(avatar = it)) },
                        modifier = Modifier.size(64.dp),
                    )
                    // 表单行收口到 KedgeOptionItem：标题走 titleContent，输入框走
                    // supportingContent（不再经项目内的转接层）。
                    KedgeOptionItem(
                        onClick = null,
                        titleContent = {
                            Text(
                                text = stringResource(R.string.assistant_page_name),
                                style = KedgeTextStyles.title(),
                            )
                        },
                        supportingContent = {
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
                        },
                    )

                    KedgeOutlinedTextField(
                        value = assistant.description,
                        onValueChange = { update(assistant.copy(description = it)) },
                        label = "职责描述",
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )
                    ModelSelector(
                        modelId = assistant.chatModelId,
                        providers = settings.providers,
                        type = ModelType.CHAT,
                        onSelect = { update(assistant.copy(chatModelId = it.id)) },
                    )
                    ReasoningButton(
                        reasoningLevel = assistant.reasoningLevel,
                        onUpdateReasoningLevel = { update(assistant.copy(reasoningLevel = it)) },
                    )
                    AssistantImporter(
                        assistant = assistant,
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
