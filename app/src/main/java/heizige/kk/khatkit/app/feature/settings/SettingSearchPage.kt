package heizige.kk.khatkit.app.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeSearchBar
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.ui.AutoAIIcon
import heizige.kk.khatkit.app.core.ui.components.ui.OutlinedNumberInput
import heizige.kk.khatkit.app.core.ui.components.ui.Tag
import heizige.kk.khatkit.app.core.ui.components.ui.TagType
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.search.SearchCommonOptions
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.search.SearchRepository
import heizige.kk.khatkit.search.SearchOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.reflect.full.primaryConstructor
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormRow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormCard
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khatkit.app.feature.settings.data.addSearchRepository
import heizige.kk.khatkit.app.feature.settings.data.moveSearchRepository
import heizige.kk.khatkit.app.feature.settings.data.removeSearchRepository

@Composable
fun SettingSearchPage(vm: SettingViewModel = hiltViewModel()) {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        SettingSearchPageMiuix(vm)
        return
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    var showAddSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }

    // 重排按**条目 key**（服务 id）换算下标，不做 `from.index` 这类下标算术：
    // 页面列表与搜索框的全屏结果槽里下标空间不同（Miuix 页里第 0 项还是搜索框本身），
    // key 换算对两种情况都成立。
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        moveSearchRepository(vm = vm, settings = settings, fromServiceId = from.key, toServiceId = to.key)
    }

    val filteredServices = remember(settings.searchServices, searchQuery) {
        if (searchQuery.isBlank()) {
            settings.searchServices
        } else {
            settings.searchServices.filter { service ->
                service.displayName.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    val openDetail: (SearchOptions) -> Unit = { service ->
        nav.navigate(Screen.SettingSearchDetail(service.id.toString()))
    }
    val removeService: (SearchOptions) -> Unit = { service ->
        removeSearchRepository(vm = vm, settings = settings, service = service)
    }

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.setting_page_search_title),
        scrollBehavior = scrollBehavior,
        actions = {
            KedgeIconButton(
                onClick = { showAddSheet = true },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = add,
                    contentDescription = stringResource(R.string.setting_page_search_add_provider)
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // 与「提供商」设置页顶部同款：KSU 式药丸搜索框，点一下展开成铺满整屏的结果层。
            // 展开期间页面正文整个抽掉（KSU 的 SearchBox 同款），列表只在展开槽里出现一次。
            KedgeSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.setting_page_search_search_engine),
                active = searchExpanded,
                onActiveChange = { searchExpanded = it },
                cancelLabel = stringResource(R.string.cancel),
                modifier = Modifier.fillMaxWidth(),
                // 不传 showResultsWhenEmpty（默认 false）：与提供商页一致，点开搜索先空白，
                // 输入才出列表 —— 服务列表本身就在页面正文里，不需要展开就摊开一份。
                expandedContent = {
                    // 独立窗口里不能有 sharedElement，否则 lookahead 配对跨 ViewRoot 会崩
                    NoHeroTransition {
                        SearchServiceList(
                            services = filteredServices,
                            settings = settings,
                            onEdit = openDetail,
                            onDelete = removeService,
                            canDelete = settings.searchServices.size > 1,
                            showCommonOptions = false,
                            onUpdateCommonOptions = { options ->
                                vm.updateSettings(settings.copy(searchCommonOptions = options))
                            },
                            listState = lazyListState,
                            reorderableState = reorderableState,
                            dragEnabled = false,
                            bottomPadding = innerPadding.calculateBottomPadding() +
                                PageMetrics.BottomContentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
            )

            if (!searchExpanded) {
                SearchServiceList(
                    services = filteredServices,
                    settings = settings,
                    onEdit = openDetail,
                    onDelete = removeService,
                    canDelete = settings.searchServices.size > 1,
                    showCommonOptions = true,
                    onUpdateCommonOptions = { options ->
                        vm.updateSettings(settings.copy(searchCommonOptions = options))
                    },
                    listState = lazyListState,
                    reorderableState = reorderableState,
                    // 过滤态（搜索中）不开拖拽 —— 那时列表是子集，虽然重排按 key 换算是安全的，
                    // 但拖到看不见的条目上还是会让顺序变得难以预期。
                    dragEnabled = searchQuery.isBlank(),
                    bottomPadding = innerPadding.calculateBottomPadding() +
                        PageMetrics.BottomContentPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .imePadding(),
                )
            }
        }
    }

    if (showAddSheet) {
        AddProviderSheet(
            onDismiss = { showAddSheet = false },
            onConfirm = { options ->
                showAddSheet = false
                addSearchRepository(vm = vm, settings = settings, service = options)
                scope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            }
        )
    }
}

/**
 * 搜索服务列表（MD3 侧）。折叠态挂在页面正文里、展开态搬进搜索框的结果槽，两处共用一份实现，
 * 只有 [modifier]、[dragEnabled] 与 [showCommonOptions] 不同。
 *
 * 服务列表自成一个分组：圆角按 index/count 逐项算（首项只圆上边、末项只圆下边、中间项直角），
 * 项间距取全局列表卡片样式（MD3 2dp、Miuix 0dp）。不能换成 CardGroup —— 那会把所有项收进
 * 一个 Column，ReorderableItem 需要的「每项一个 lazy item」就没了，长按拖拽排序会失效。
 */
@Composable
private fun SearchServiceList(
    services: List<SearchOptions>,
    settings: Settings,
    onEdit: (SearchOptions) -> Unit,
    onDelete: (SearchOptions) -> Unit,
    canDelete: Boolean,
    showCommonOptions: Boolean,
    onUpdateCommonOptions: (SearchCommonOptions) -> Unit,
    listState: LazyListState,
    reorderableState: ReorderableLazyListState,
    dragEnabled: Boolean,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val cards = listCardStyle()
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp) +
            PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(cards.gap),
        state = listState,
    ) {
        itemsIndexed(services, key = { _, service -> service.id }) { index, service ->
            ReorderableItem(
                state = reorderableState,
                key = service.id
            ) { isDragging ->
                SearchProviderCard(
                    service = service,
                    onEdit = { onEdit(service) },
                    onDelete = { onDelete(service) },
                    canDelete = canDelete,
                    shape = cards.indexedShape(index, services.size),
                    modifier = Modifier
                        .scale(if (isDragging) 0.95f else 1f)
                        .animateItem()
                        // 与助手/提供商列表一致：长按**整行**就能拖，不用先按住拖柄。
                        .then(
                            if (dragEnabled) {
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
                        ),
                )
            }
        }

        if (showCommonOptions) {
            item("common_options") {
                // 通用选项是**另一个分组**：列表项间距已经是 cards.gap，组与组之间
                // 另外留 12dp，否则两组卡片会贴成一张（Miuix 那一支的 10dp 由
                // KedgeFormCard 自己加，不需要这里再给）。
                CommonOptions(
                    settings = settings,
                    modifier = Modifier.padding(top = 12.dp),
                    onUpdate = onUpdateCommonOptions,
                )
            }
        }
    }
}

/**
 * 「新增搜索服务」面板：与「新增提供商」用同一套底部弹层（图标 + 标题 + 底部「添加 / 取消」行）。
 *
 * 改造前是 `AppAlertDialog`：标题 + 20 个引擎的列表挤在对话框里，而 MD3 / Miuix 两个风格页
 * 共用同一份对话框，弹层外观与设置页其余弹层不同源。
 *
 * `scrollable = false` + 内容自带 `LazyColumn`：Kedge 版**忽略** `scrollable`、内容必须自己滚
 * （Miuix 分支没有滚动容器），MD3 分支据该参数不再叠第二层滚动
 * （见 `BottomSheetScrollSourceGuardTest`）。高度取 maxHeight 的 68%，20 个引擎在小屏上
 * 也不会把「添加」按钮顶出屏幕外。
 *
 * 顶部是**同款** `KedgeSearchBar`（KSU 药丸搜索框，与提供商页/模型选择器/表情选择器同源）：
 * 20 个引擎要点着找，按名称过滤一下快得多。弹层里展开走 `KedgeSearchSheetHost`
 * （`PrimaryBottomSheet` 已经包好）→ 展开层画在弹层边界内，而不是窗口级全屏层。
 * 与 `EmojiPicker` / 模型选择器一致：展开期间正文整个抽掉，列表只在搜索框的展开槽里出现一次。
 */
@Composable
internal fun AddProviderSheet(
    onDismiss: () -> Unit,
    onConfirm: (SearchOptions) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(SearchOptions.TYPES.keys.first())
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }

    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.setting_page_search_add_provider),
        imageVector = add,
        confirmText = stringResource(R.string.confirm),
        onConfirm = {
            val instance = selectedType.primaryConstructor!!.callBy(mapOf())
            onConfirm(instance)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss,
        scrollable = false,
    ) { _ ->
        // 引擎选择项也是选项行：KedgeOptionItem 插槽版，圆角/间距与列表页同一份口径
        // （选中态只换底色，行结构与未选中完全一致）。
        val cards = listCardStyle()
        // 空关键词保留原顺序（TYPES 的插入顺序即展示顺序），有词就按显示名过滤。
        val types = remember(searchQuery) {
            val all = SearchOptions.TYPES.keys.toList()
            if (searchQuery.isBlank()) {
                all
            } else {
                all.filter {
                    (SearchOptions.TYPES[it] ?: "").contains(searchQuery.trim(), ignoreCase = true)
                }
            }
        }

        // 同一个列表画两处：折叠态在搜索框下面，展开态在搜索框的槽里。两份不能同时在场，
        // 否则 LazyColumn 状态与点击目标会重复（与 EmojiPicker / ModelList 的口径一致）。
        val engineList: @Composable (Modifier) -> Unit = { listModifier ->
            LazyColumn(
                modifier = listModifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(cards.gap),
            ) {
                if (types.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.search_page_no_results),
                            style = KedgeTextStyles.footnote(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                }
                itemsIndexed(types) { index, type ->
                    val name = SearchOptions.TYPES[type] ?: "Unknown"
                    val isSelected = selectedType == type
                    KedgeOptionItem(
                        onClick = { selectedType = type },
                        shape = cards.indexedShape(index, types.size),
                        backgroundColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Unspecified
                        },
                        leadingContent = {
                            AutoAIIcon(
                                name = name,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        titleContent = {
                            Text(
                                text = name,
                                style = KedgeTextStyles.body(),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            KedgeSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.setting_page_search_search_engine),
                active = searchExpanded,
                onActiveChange = { searchExpanded = it },
                cancelLabel = stringResource(R.string.cancel),
                // 引擎表本身就是这块弹层的主 UI：空关键词也摊开，不走「先输入再展示」。
                showResultsWhenEmpty = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                expandedContent = { NoHeroTransition { engineList(Modifier.fillMaxSize()) } },
            )

            // 展开期间列表整个抽掉（KSU 的 SearchBox 同款），只在搜索框的展开槽里出现一次。
            if (!searchExpanded) {
                engineList(Modifier.fillMaxHeight(0.68f))
            }
        }
    }
}

/**
 * 搜索服务的一行：图标 + 名称 + 能力徽标 + ⋮ 菜单，全部走 `KedgeOptionItem` 插槽。
 *
 * 改造前是手搓的 `KedgeCard { Row(...) }`：外壳与行布局自己排，两种风格各写一份，
 * 且**整行不可点**（只能点右侧 ⋮ 才进详情）。现在 [onClick] 整行进编辑页，
 * 尾部控件留在 `trailingContent` 里，⋮ 自己消费点击不会冒泡到整行。
 *
 * [shape] 由调用点按 index/count 算好传入（[heizige.kk.khatkit.app.core.ui.theme.ListCardStyle.indexedShape]）：
 * 本组件不猜自己在组里的位置 —— 列表里它是分组中的一段，弹层里它是单独一行。
 */
@Composable
internal fun SearchProviderCard(
    service: SearchOptions,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    KedgeOptionItem(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        // 底色走组件默认（MD3 `surfaceVariant@0.26` / Miuix `surfaceContainer`）：
        // 以前铺的 `listItemColors` 在 MD3 下等于分组底色（都是 surfaceBright），
        // 行与行之间那 2dp 缝会看不出来，整组糊成一片。
        leadingContent = {
            AutoAIIcon(
                name = service.displayName,
                modifier = Modifier.size(32.dp)
            )
        },
        titleContent = {
            Text(
                text = service.displayName,
                style = KedgeTextStyles.title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            SearchAbilityTagLine(options = service)
        },
        trailingContent = {
            KedgeIconButton(onClick = { showMenu = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = moreVert,
                    contentDescription = null
                )
                KedgeDropdownMenuSlots(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    KedgeDropdownItemSlot(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(edit, contentDescription = null)
                        }
                    )
                    KedgeDropdownItemSlot(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(delete, contentDescription = null)
                        },
                        enabled = canDelete
                    )
                }
            }
        },
    )
}

@Composable
fun SearchAbilityTagLine(
    modifier: Modifier = Modifier,
    options: SearchOptions
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Tag(
            type = TagType.DEFAULT,
        ) {
            Text(stringResource(R.string.search_ability_search))
        }
        if (SearchRepository.get(options).scrapingParameters(options) != null) {
            Tag(
                type = TagType.DEFAULT,
            ) {
                Text(stringResource(R.string.search_ability_scrape))
            }
        }
    }
}

/**
 * 通用选项分组：标题 + 结果数量行，一整组分段卡片。
 *
 * 改造前是 `KedgeCard { Column(padding 16) { 标题; KedgeFormRow } }` —— 外壳自己画卡，
 * 行又自己画一张四角全圆的卡（`FormItem` 在组外按「整组只有一行」算），Miuix 下更明显：
 * 分组底色与圆角都来自 MD3 的 `KedgeCard`，与同页其它分组不同源。
 * 现在外壳交给 `KedgeFormCard`（InlineCardGroup）：底色/圆角由组统一出，组内行只留内侧
 * 小圆角，首项上边、末项下边由整组裁切给出，两种风格一致。
 */
@Composable
internal fun CommonOptions(
    settings: heizige.kk.khatkit.app.core.data.datastore.Settings,
    modifier: Modifier = Modifier,
    onUpdate: (SearchCommonOptions) -> Unit
) {
    var commonOptions by remember(settings.searchCommonOptions) {
        mutableStateOf(settings.searchCommonOptions)
    }
    KedgeFormCard(modifier = modifier) {
        // 标题是组内「不带底色的子项」，卡片底由 InlineCardGroup 那一层透出来；
        // 内边距自己补（组容器不加内边距，行组件自带 16dp 是行的）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_page_search_common_options),
                style = KedgeTextStyles.title()
            )
        }

        KedgeFormRow(
            label = {
                Text(stringResource(R.string.setting_page_search_result_size))
            }
        ) {
            OutlinedNumberInput(
                value = commonOptions.resultSize,
                onValueChange = {
                    commonOptions = commonOptions.copy(resultSize = it)
                    onUpdate(commonOptions)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
