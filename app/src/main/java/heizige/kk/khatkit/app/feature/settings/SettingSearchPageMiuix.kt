package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeSearchBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixPageMetrics
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.search.SearchOptions
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Icon
import heizige.kk.khatkit.app.feature.settings.data.addSearchRepository
import heizige.kk.khatkit.app.feature.settings.data.moveSearchRepository
import heizige.kk.khatkit.app.feature.settings.data.removeSearchRepository

/**
 * 搜索服务列表页的 Miuix 风格版。
 *
 * 服务行 [SearchProviderCard] 与拖拽排序（sh.calvin.reorderable）本身与风格无关 ——
 * 行走 `KedgeOptionItem`（内部按 `LocalKedgeStyle` 分流），拖拽库独立于 UI 框架 ——
 * 这里只换外壳与顶栏操作按钮。
 *
 * 整列服务是**一个分组**：圆角走 `listCardStyle().indexedShape(index, count)`
 * （首项只圆上边、末项只圆下边、中间项直角），项间距走 `listCardStyle().gap`
 * （Miuix 0dp → 贴成一整块；MD3 那一支在 `SettingSearchPage` 的 LazyColumn 里给 2dp 缝）。
 * 以前每张卡自己带 `miuixItemSpacing()`，项与项之间是 12dp 独立卡片，不是分组观感。
 *
 * 顶部与「提供商」设置页同款：列表第 0 项是 KSU 式药丸搜索框，展开成整屏结果层。
 */
@Composable
fun SettingSearchPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    val haptic = LocalHapticFeedback.current
    var showAddSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }

    // 两个列表（页面里的折叠态列表 / 搜索框的全屏结果槽）各自持有一份 LazyListState：
    // 复用同一个 state 会让两个 LazyColumn 共享滚动下标，而页面列表里第 0 项是搜索框、
    // 结果槽里第 0 项是第一个服务，下标含义不同。重排本身按**条目 key**（服务 id）换算，
    // 所以两种下标空间都能落到同一条服务上（见 [moveSearchRepository]）。
    val pageListState = rememberLazyListState()
    val pageReorderableState = rememberReorderableLazyListState(pageListState) { from, to ->
        moveSearchRepository(vm = vm, settings = settings, fromServiceId = from.key, toServiceId = to.key)
    }
    val resultListState = rememberLazyListState()
    val resultReorderableState = rememberReorderableLazyListState(resultListState) { from, to ->
        moveSearchRepository(vm = vm, settings = settings, fromServiceId = from.key, toServiceId = to.key)
    }

    // 服务列表自成一个分组：圆角按 index/count 逐项算，项间距取 listCardStyle().gap
    // （Miuix 0dp → 整组贴成一整块）。不能换成 miuixGroup —— 那会把整组收进一个
    // Column，ReorderableItem 需要的「每项一个 lazy item」就没了，长按拖拽会失效。
    val cards = listCardStyle()

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

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_search_title),
        navigationIcon = { BackButton() },
        actions = {
            KedgeIconButton(onClick = { showAddSheet = true }) {
                Icon(
                    imageVector = add,
                    contentDescription = stringResource(R.string.setting_page_search_add_provider),
                )
            }
        },
        bottomInnerPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(cards.gap),
        lazyListState = pageListState,
    ) {
        item(key = "search") {
            // 与提供商页同一份口径：折叠药丸自带 0 留白，间距在这里补（KSU 每个分组 12dp）。
            KedgeSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.setting_page_search_search_engine),
                active = searchExpanded,
                onActiveChange = { searchExpanded = it },
                cancelLabel = stringResource(R.string.cancel),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MiuixPageMetrics.GroupSpacing),
                // 不传 showResultsWhenEmpty（默认 false）：与提供商页一致，点开搜索先空白，
                // 输入才出列表 —— 服务列表本身就在页面正文里，不需要展开就摊开一份。
                expandedContent = {
                    // 结果层挂在窗口级浮层里（KedgeSearchOverlayLayer），不是本页子树，
                    // 所以不能带 sharedElement，否则 lookahead 配对跨 ViewRoot 会崩。
                    NoHeroTransition {
                        SearchServiceListMiuix(
                            services = filteredServices,
                            onEdit = openDetail,
                            onDelete = removeService,
                            canDelete = settings.searchServices.size > 1,
                            listState = resultListState,
                            reorderableState = resultReorderableState,
                            dragEnabled = false,
                            // 结果槽不在 MiuixSettingsPage 的 LazyColumn 里，12dp 水平边距要自己补。
                            contentPadding = PaddingValues(
                                start = MiuixPageMetrics.HorizontalPadding,
                                end = MiuixPageMetrics.HorizontalPadding,
                                bottom = 16.dp,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
            )
        }

        // 展开期间页面正文整个抽掉（KSU 的 SearchBox 同款）：列表只在结果槽里出现一次。
        if (!searchExpanded) {
            itemsIndexed(filteredServices, key = { _, service -> service.id }) { index, service ->
                ReorderableItem(
                    state = pageReorderableState,
                    key = service.id,
                ) { isDragging ->
                    SearchServiceRow(
                        service = service,
                        shape = cards.indexedShape(index, filteredServices.size),
                        canDelete = settings.searchServices.size > 1,
                        onEdit = openDetail,
                        onDelete = removeService,
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f)
                            .animateItem()
                            // 与助手/提供商列表一致：长按**整行**就能拖，不用先按住拖柄。
                            // 过滤态（搜索中）不开拖拽 —— 那时列表是子集，拖到看不见的条目上
                            // 会让顺序变得难以预期。
                            .dragToReorder(this, haptic, searchQuery.isBlank()),
                    )
                }
            }

            item {
                CommonOptions(
                    settings = settings,
                    onUpdate = { options ->
                        vm.updateSettings(settings.copy(searchCommonOptions = options))
                    },
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
                // 第 0 项是搜索框，新增的服务落在第 1 项。
                scope.launch { pageListState.animateScrollToItem(1) }
            },
        )
    }
}

/** 服务列表。折叠态挂在页面正文里，展开态搬进搜索框的结果槽，两处共用一份实现。 */
@Composable
private fun SearchServiceListMiuix(
    services: List<SearchOptions>,
    onEdit: (SearchOptions) -> Unit,
    onDelete: (SearchOptions) -> Unit,
    canDelete: Boolean,
    listState: LazyListState,
    reorderableState: ReorderableLazyListState,
    dragEnabled: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val cards = listCardStyle()
    val haptic = LocalHapticFeedback.current
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(cards.gap),
        state = listState,
    ) {
        itemsIndexed(services, key = { _, service -> service.id }) { index, service ->
            ReorderableItem(
                state = reorderableState,
                key = service.id,
            ) { isDragging ->
                SearchServiceRow(
                    service = service,
                    shape = cards.indexedShape(index, services.size),
                    canDelete = canDelete,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    modifier = Modifier
                        .scale(if (isDragging) 0.95f else 1f)
                        .dragToReorder(this, haptic, dragEnabled),
                )
            }
        }
    }
}

/**
 * 一行搜索服务（Miuix 侧）。页面列表与结果槽共用，只由调用点决定 [shape] 与是否可拖。
 */
@Composable
private fun SearchServiceRow(
    service: SearchOptions,
    shape: Shape,
    canDelete: Boolean,
    onEdit: (SearchOptions) -> Unit,
    onDelete: (SearchOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchProviderCard(
        service = service,
        onEdit = { onEdit(service) },
        onDelete = { onDelete(service) },
        canDelete = canDelete,
        shape = shape,
        modifier = modifier,
    )
}

/**
 * 长按整行拖动。`longPressDraggableHandle` 是 `ReorderableItem` 内容 lambda 的
 * `ReorderableCollectionItemScope` 成员扩展，所以必须在那个作用域里取 —— 本函数由调用点
 * 传 `this`（即 scope）进来，行组件本身就不用知道拖拽库的存在。
 *
 * [enabled] 为 false（过滤态 / 结果槽）时原样返回，长按不再拖动。
 */
private fun Modifier.dragToReorder(
    scope: ReorderableCollectionItemScope,
    haptic: HapticFeedback,
    enabled: Boolean,
): Modifier {
    if (!enabled) return this
    return with(scope) {
        this@dragToReorder.longPressDraggableHandle(
            onDragStarted = {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            },
            onDragStopped = {
                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
            },
        )
    }
}
