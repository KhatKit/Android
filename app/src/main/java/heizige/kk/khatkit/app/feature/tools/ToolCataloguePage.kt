package heizige.kk.khatkit.app.feature.tools

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.LocalKkNavController
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.feature.tools.helper.ToolCategory
import heizige.kk.khatkit.app.feature.tools.helper.ToolGridZoom
import heizige.kk.khatkit.app.feature.tools.helper.ToolShortcutHelper
import heizige.kk.khatkit.app.feature.tools.helper.matchesToolQuery
import heizige.kk.khatkit.app.feature.tools.helper.toolCategory
import heizige.kk.khatkit.app.feature.tools.helper.toolDescription
import heizige.kk.khatkit.app.feature.tools.helper.toolTitle
import heizige.kk.khatkit.app.feature.ui.shell.LocalKkBarBackdrop
import heizige.kk.khatkit.app.feature.ui.shell.MochiPageScaffold
import heizige.kk.khatkit.hub.CardIndexEntry
import ui.feice.mochi.basic.MochiActivityIndicator
import heizige.kk.khatkit.uikit.mochi.kkBarButtonColors
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiIconDropdown
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiRefreshIndicator
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.MochiSearchBar
import ui.feice.mochi.basic.mochiRefreshHold
import ui.feice.mochi.basic.rememberMochiRefreshState
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.MochiCounterText
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.effect.overscroll.mochiContainedScroll
import ui.feice.mochi.effect.overscroll.rememberMochiOverscrollEffect
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListStatusChip
import ui.feice.mochi.list.mochiListSection
import ui.feice.mochi.list.rows
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.collections.immutable.persistentListOf

private enum class ToolScope(val title: String, val icon: String) {
    ALL("全部", "apps"),
    RECENT("近期", "time"),
    FAVORITES("收藏", "star"),
}

private data class ToolView(val grid: Boolean)

private data class ToolGroup(
    val category: ToolCategory,
    val cards: List<CardIndexEntry>,
)

internal const val TOOL_CATALOGUE_SOURCE = "welcome:all-tools"
private fun toolSource(name: String) = "tool-catalogue:$name"

@Composable
internal fun ToolCataloguePage() {
    val vm = hiltViewModel<ToolCatalogueViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val headerIcons by vm.headerIcons.collectAsStateWithLifecycle()
    val nav = LocalKkNavController.current
    val configuration = LocalConfiguration.current
    val locales = remember(configuration) {
        (0 until configuration.locales.size()).map { configuration.locales[it].toLanguageTag() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var scopeName by rememberSaveable { mutableStateOf(ToolScope.ALL.name) }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var categoryRequest by remember { mutableStateOf(0) }
    var grid by rememberSaveable { mutableStateOf(true) }
    var gridColumnOffset by rememberSaveable { mutableIntStateOf(0) }
    var chipsCollapsed by rememberSaveable { mutableStateOf(false) }
    val scope = ToolScope.valueOf(scopeName)
    val searched = remember(state.cards, query, locales) {
        state.cards.filter { it.matchesToolQuery(query, locales) }
    }
    val scoped = remember(searched, scope, state.favorites, state.recent) {
        searched.filter { entry ->
            when (scope) {
                ToolScope.ALL -> true
                ToolScope.RECENT -> entry.name in state.recent
                ToolScope.FAVORITES -> entry.name in state.favorites
            }
        }.let { result ->
            if (scope == ToolScope.RECENT) {
                result.sortedBy { state.recent.indexOf(it.name).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            } else {
                result
            }
        }
    }
    val categories = remember(scoped) {
        ToolCategory.entries.filter { category ->
            scoped.any { it.toolCategory() == category }
        }
    }
    LaunchedEffect(categories) {
        if (selectedCategory !in categories.map { it.name }) {
            selectedCategory = categories.firstOrNull()?.name.orEmpty()
        }
    }
    val open: (CardIndexEntry) -> Unit = { entry ->
        vm.open(entry) {
            // 下载期间用户可能已经返回，完成时不能把运行页推到别的页面上。
            if (nav?.current == Screen.ToolCatalogue) {
                nav.navigate(Screen.CardWidget(entry.name, entry.toolTitle(locales)), source = toolSource(entry.name))
            }
        }
    }
    MochiPageScaffold(
        containerColor = MochiDefaults.groupedBackground(),
        edgeFade = false,
        topBar = {
            MochiTitleBar(
                title = "工具台",
                navigationIcon = { BackButton() },
                backdrop = LocalKkBarBackdrop.current,
                actions = {
                    ToolCatalogueMenu(onNavigate = { nav?.navigate(it) })
                    MochiBarIconButton(
                        onClick = {
                            grid = !grid
                        },
                        navId = "tools.layout",
                        contentKey = if (grid) "list" else "grid",
                        colors = kkBarButtonColors(),
                    ) {
                        MochiIonicon(if (grid) "list" else "grid", contentDescription = if (grid) "切换为列表" else "切换为网格")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MochiSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "搜索工具名称或功能",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AnimatedVisibility(
                visible = !chipsCollapsed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                MochiChipGroup(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    ToolScope.entries.forEach { option ->
                        MochiChip(
                            label = option.title,
                            selected = scope == option,
                            onClick = {
                                scopeName = option.name
                                selectedCategory = ""
                                categoryRequest++
                                chipsCollapsed = false
                            },
                            leadingIcon = { MochiIonicon(option.icon, size = 16.dp) },
                        )
                    }
                }
            }
            if (state.error != null) {
                MochiListSection(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    MochiListRow(
                        title = "目录未能更新",
                        subtitle = state.error,
                        onClick = vm::refresh,
                        leading = { MochiIonicon("cloud-offline") },
                        leadingStyle = MochiListLeadingStyle.Symbol,
                        trailing = { Text("重试", style = MochiTheme.textStyles.footnote, color = MochiTheme.colorScheme.primary) },
                    )
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val rail = this@BoxWithConstraints.maxWidth >= 600.dp
                Column(Modifier.fillMaxSize()) {
                    if (!rail) {
                        MochiChipGroup(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            categories.forEach { item ->
                                val selected = selectedCategory == item.name
                                CategoryChip(item.title, item.icon, selected, scoped.count { it.toolCategory() == item }) {
                                    selectedCategory = item.name
                                    categoryRequest++
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxSize()) {
                        if (rail) {
                            LazyColumn(
                                modifier = Modifier.width(156.dp),
                                contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                flingBehavior = MochiScrollDefaults.flingBehavior(),
                            ) {
                                items(categories, key = { it.name }) { item ->
                                    val selected = selectedCategory == item.name
                                    RailCategory(item.title, item.icon, selected, scoped.count { it.toolCategory() == item }) {
                                        selectedCategory = item.name
                                        categoryRequest++
                                    }
                                }
                            }
                        }
                        MochiBlurCrossfade(
                            targetState = ToolView(grid),
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        ) { view ->
                            ToolResults(
                                cards = scoped, state = state, headerIcons = headerIcons,
                                locales = locales, query = query, scope = scope,
                                view = view, selectedCategory = selectedCategory,
                                gridColumnOffset = gridColumnOffset,
                                onGridColumnOffsetChange = { gridColumnOffset = it },
                                categoryRequest = categoryRequest,
                                onCategorySelected = { selectedCategory = it },
                                onScrolled = { chipsCollapsed = it },
                                onOpen = open, onFavorite = vm::toggleFavorite,
                                onClear = {
                                    query = ""
                                    scopeName = ToolScope.ALL.name
                                    selectedCategory = ""
                                    chipsCollapsed = false
                                },
                                onRefresh = vm::refresh,
                                onRefreshAwait = vm::refreshAndAwait,
                                onLoadMore = vm::loadMore,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCatalogueMenu(onNavigate: (Screen) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = kkBarButtonColors()
    MochiIconDropdown(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        items = persistentListOf(
            MochiItem("卡片 API 状态", icon = { tint -> MochiIonicon("pulse", size = 20.dp, tint = tint) }),
            MochiItem("局域网共享", icon = { tint -> MochiIonicon("wifi", size = 20.dp, tint = tint) }),
        ),
        onItemClick = { index ->
            expanded = false
            when (index) {
                0 -> onNavigate(Screen.ToolApiHealth)
                1 -> onNavigate(Screen.LanShare)
            }
        },
        colors = MochiDropdownDefaults.iconDropdownColors(
            buttonColor = colors.container,
            buttonContentColor = colors.content,
        ),
        size = 44.dp,
    ) { MochiIonicon("ellipsis-horizontal", contentDescription = "工具台更多操作") }
}

@Composable
private fun CategoryChip(title: String, icon: String, selected: Boolean, count: Int, onClick: () -> Unit) {
    MochiChip(title, selected, onClick, leadingIcon = { MochiIonicon(icon, size = 16.dp) },
        labelContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MochiTheme.textStyles.footnote)
                MochiCounterText(count.toLong(), style = MochiTheme.textStyles.footnote)
            }
        })
}

@Composable
private fun RailCategory(title: String, icon: String, selected: Boolean, count: Int, onClick: () -> Unit) {
    MochiChip(
        label = title, selected = selected, onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).semantics { contentDescription = "$title，$count 个工具" },
        labelContent = {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MochiIonicon(icon, size = 21.dp)
                Text(title, style = MochiTheme.textStyles.footnote, maxLines = 1)
            }
        },
    )
}

@Composable
private fun ToolResults(
    cards: List<CardIndexEntry>,
    state: ToolCatalogueState,
    headerIcons: Map<String, String>,
    locales: List<String>,
    query: String,
    scope: ToolScope,
    view: ToolView,
    gridColumnOffset: Int,
    onGridColumnOffsetChange: (Int) -> Unit,
    selectedCategory: String,
    categoryRequest: Int,
    onCategorySelected: (String) -> Unit,
    onScrolled: (Boolean) -> Unit,
    onOpen: (CardIndexEntry) -> Unit,
    onFavorite: (String) -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshAwait: suspend () -> Unit,
    onLoadMore: () -> Unit,
) {
    val groups = remember(cards) {
        ToolCategory.entries.mapNotNull { category ->
            cards.filter { it.toolCategory() == category }
                .takeIf { it.isNotEmpty() }
                ?.let { ToolGroup(category, it) }
        }
    }
    val overscroll = rememberMochiOverscrollEffect()
    if (cards.isEmpty()) {
        val listState = rememberLazyListState()
        val refresh = rememberMochiRefreshState(overscroll, listState, onRefreshAwait)
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
                .distinctUntilChanged()
                .collect(onScrolled)
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().mochiRefreshHold(refresh).mochiContainedScroll(overscroll),
                state = listState,
                overscrollEffect = overscroll,
                flingBehavior = MochiScrollDefaults.flingBehavior(),
            ) {
                item {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.loading) {
                            MochiActivityIndicator()
                        } else {
                            MochiEmptyState(
                                title = when {
                                    query.isNotBlank() -> "没有匹配的工具"
                                    scope == ToolScope.RECENT -> "还没有近期使用"
                                    scope == ToolScope.FAVORITES -> "还没有收藏"
                                    else -> "暂无工具"
                                },
                                message = when {
                                    query.isNotBlank() -> "换个名称或功能试试。"
                                    scope == ToolScope.RECENT -> "打开过的工具会保留在这里。"
                                    scope == ToolScope.FAVORITES -> "点工具上的星标，方便下次找到。"
                                    else -> "浏览全部分类，选择需要的工具。"
                                },
                                icon = { MochiIonicon(if (query.isNotBlank()) "search" else scope.icon, size = 36.dp) },
                                action = {
                                    MochiButton(onClick = if (state.error != null) onRefresh else onClear, style = MochiButtonStyle.Tonal) {
                                        Text(if (state.error != null) "重新加载" else "查看全部工具")
                                    }
                                },
                            )
                        }
                    }
                }
            }
            MochiRefreshIndicator(refresh, Modifier.align(Alignment.TopCenter))
        }
        return
    }
    if (view.grid) {
        val gridState = rememberLazyGridState()
        val sectionStarts = remember(groups) {
            var next = 0
            groups.map {
                val start = next
                next += it.cards.size + 1
                start
            }
        }
        val refresh = rememberMochiRefreshState(overscroll, onRefresh = onRefreshAwait)
        LaunchedEffect(query, view, scope) { gridState.scrollToItem(0) }
        LaunchedEffect(gridState) {
            snapshotFlow { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
                .distinctUntilChanged()
                .collect(onScrolled)
        }
        LaunchedEffect(categoryRequest) {
            val index = groups.indexOfFirst { it.category.name == selectedCategory }
            if (index >= 0) gridState.animateScrollToItem(sectionStarts[index])
        }
        LaunchedEffect(gridState, groups) {
            snapshotFlow { gridState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { firstVisible ->
                    val index = sectionStarts.indexOfLast { it <= firstVisible }
                    groups.getOrNull(index)?.category?.name?.let(onCategorySelected)
                }
        }
        LaunchedEffect(gridState, cards.size, state.loading, state.complete, state.error) {
            if (state.loading || state.complete || state.error != null) return@LaunchedEffect
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisible ->
                    if (lastVisible >= gridState.layoutInfo.totalItemsCount - 8) onLoadMore()
                }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Match Adaptive's default and keep the available densities relative to the window width.
            val available = maxWidth.value - 28f + 10f // content padding, then recover the last gap
            val defaultColumns = (available / 122f).toInt().coerceAtLeast(1)
            val maxColumns = (available / 102f).toInt().coerceAtLeast(1)
            val columns = (defaultColumns + gridColumnOffset).coerceIn(1, maxColumns)
            val onGridStep by rememberUpdatedState<(Int) -> Unit> { step ->
                onGridColumnOffsetChange((columns + step).coerceIn(1, maxColumns) - defaultColumns)
            }
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val zoom = ToolGridZoom()
                            var claimed = false
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed && it.previousPressed } == 2) {
                                    val step = zoom.update(event.calculateZoom())
                                    if (step != 0) {
                                        claimed = true
                                        onGridStep(step)
                                    }
                                } else {
                                    zoom.reset()
                                }
                                // Leave one-finger scrolling and card taps to the grid until a pinch is recognized.
                                if (claimed) event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .mochiRefreshHold(refresh)
                    .mochiContainedScroll(overscroll),
                columns = GridCells.Fixed(columns),
                state = gridState,
                overscrollEffect = overscroll,
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                flingBehavior = MochiScrollDefaults.flingBehavior(),
            ) {
                groups.forEach { group ->
                    item(key = "category-header:${group.category.name}", span = { GridItemSpan(maxLineSpan) }) {
                        ToolCategoryHeader(group.category, group.cards.size, Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))
                    }
                    items(group.cards, key = { it.name }, contentType = { "tool" }) { entry ->
                        ToolGridCard(entry, state, headerIcons[entry.name], locales, onOpen, onFavorite, Modifier.animateItem())
                    }
                }
                if (!state.complete) {
                    item(key = "load_more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            if (state.loading) MochiActivityIndicator()
                            else if (state.error != null) {
                                MochiButton(onClick = onLoadMore, style = MochiButtonStyle.Tonal) { Text("重试加载") }
                            }
                        }
                    }
                }
            }
            MochiRefreshIndicator(refresh, Modifier.align(Alignment.TopCenter))
        }
    } else {
        val listState = rememberLazyListState()
        val sectionStarts = remember(groups) {
            var next = 0
            groups.map {
                val start = next
                next += it.cards.size
                start
            }
        }
        val refresh = rememberMochiRefreshState(overscroll, listState, onRefreshAwait)
        LaunchedEffect(query, view, scope) { listState.scrollToItem(0) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
                .distinctUntilChanged()
                .collect(onScrolled)
        }
        LaunchedEffect(categoryRequest) {
            val index = groups.indexOfFirst { it.category.name == selectedCategory }
            if (index >= 0) listState.animateScrollToItem(sectionStarts[index])
        }
        LaunchedEffect(listState, groups) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { firstVisible ->
                    val index = sectionStarts.indexOfLast { it <= firstVisible }
                    groups.getOrNull(index)?.category?.name?.let(onCategorySelected)
                }
        }
        LaunchedEffect(listState, cards.size, state.loading, state.complete, state.error) {
            if (state.loading || state.complete || state.error != null) return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisible ->
                    if (lastVisible >= cards.size - 8) onLoadMore()
                }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().mochiRefreshHold(refresh).mochiContainedScroll(overscroll),
                state = listState,
                overscrollEffect = overscroll,
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
                flingBehavior = MochiScrollDefaults.flingBehavior(),
            ) {
                groups.forEach { group ->
                    mochiListSection(key = "tools:${group.category.name}", header = group.category.title) {
                        rows(group.cards, key = { it.name }) { entry ->
                            MochiListRow(
                                title = entry.toolTitle(locales),
                                subtitle = entry.toolDescription(locales).ifBlank { entry.name },
                                onClick = { if (state.opening == null) onOpen(entry) },
                                modifier = Modifier.mochiNavSource(toolSource(entry.name), MochiListDefaults.SectionCorner, MochiDefaults.groupedContainer()),
                                leading = { ToolEntryIcon(entry, headerIcons[entry.name], 24.dp, MochiTheme.colorScheme.primary) },
                                leadingStyle = MochiListLeadingStyle.Symbol,
                                loading = state.opening == entry.name,
                                trailing = { ToolCardActions(entry, state, locales, onFavorite) },
                            )
                        }
                    }
                }
                if (!state.complete) {
                    item(key = "load_more") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            if (state.loading) MochiActivityIndicator()
                            else if (state.error != null) {
                                MochiButton(onClick = onLoadMore, style = MochiButtonStyle.Tonal) { Text("重试加载") }
                            }
                        }
                    }
                }
            }
            MochiRefreshIndicator(refresh, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ToolCategoryHeader(category: ToolCategory, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MochiIonicon(category.icon, size = 20.dp, tint = MochiTheme.colorScheme.primary)
        MochiBlurText(
            category.title,
            style = MochiTheme.textStyles.navTitle,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        MochiCounterText(count.toLong(), color = MochiTheme.colorScheme.onSurfaceVariant, style = MochiTheme.textStyles.footnote)
    }
}

@Composable
private fun ToolGridCard(
    entry: CardIndexEntry,
    state: ToolCatalogueState,
    headerIcon: String?,
    locales: List<String>,
    onOpen: (CardIndexEntry) -> Unit,
    onFavorite: (String) -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val busy = state.opening == entry.name
    MochiListCard(
        modifier
            .mochiNavSource(toolSource(entry.name), MochiListDefaults.SectionCorner, MochiDefaults.groupedContainer())
            .mochiPressDeform()
            .clickable(interaction, indication = null, role = Role.Button, enabled = state.opening == null) { onOpen(entry) },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 172.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(MochiTheme.colorScheme.primaryContainer, MochiTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) { ToolEntryIcon(entry, headerIcon, 23.dp, MochiTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.weight(1f))
                ToolCardActions(entry, state, locales, onFavorite)
            }
            Text(entry.toolTitle(locales), style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val description = entry.toolDescription(locales)
            if (description.isNotBlank()) {
                Text(description, style = MochiTheme.textStyles.footnote, color = MochiTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (busy) MochiActivityIndicator(Modifier.size(14.dp))
                Text(
                    when {
                        busy -> "正在打开"
                        entry.name in state.installed -> "已安装"
                        else -> "下载并打开"
                    },
                    style = MochiTheme.textStyles.footnote,
                    color = if (busy || entry.name !in state.installed) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolCardActions(entry: CardIndexEntry, state: ToolCatalogueState, locales: List<String>, onFavorite: (String) -> Unit) {
    val favorite = entry.name in state.favorites
    if (entry.name !in state.installed) {
        FavoriteButton(entry.name, favorite, onFavorite)
        return
    }
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val title = entry.toolTitle(locales)
    MochiIconDropdown(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        items = persistentListOf(
            MochiItem(if (favorite) "取消收藏" else "收藏工具", icon = { tint ->
                MochiIonicon("star", size = 20.dp, tint = tint)
            }),
            MochiItem("添加到桌面", icon = { tint ->
                MochiIonicon("add-circle", size = 20.dp, tint = tint)
            }),
        ),
        onItemClick = { index ->
            expanded = false
            when (index) {
                0 -> onFavorite(entry.name)
                1 -> if (!ToolShortcutHelper.pin(context, entry.name, title)) {
                    Toast.makeText(context, "无法添加桌面快捷方式", Toast.LENGTH_SHORT).show()
                }
            }
        },
        colors = MochiDropdownDefaults.iconDropdownColors(buttonColor = Color.Transparent),
        menuWidth = 220.dp,
        size = 44.dp,
    ) {
        MochiIonicon("ellipsis-horizontal", size = 19.dp, contentDescription = "${title}的操作")
    }
}

@Composable
private fun FavoriteButton(name: String, favorite: Boolean, onFavorite: (String) -> Unit) {
    MochiIconButton(
        onClick = { onFavorite(name) }, size = 44.dp, style = MochiButtonStyle.Text,
    ) {
        MochiBlurCrossfade(favorite) { selected ->
            MochiIonicon(
                "star",
                size = 19.dp,
                style = if (selected) MochiIconStyle.Filled else MochiIconStyle.Outline,
                tint = if (selected) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.onSurfaceVariant,
                contentDescription = if (selected) "取消收藏" else "收藏工具",
            )
        }
    }
}
