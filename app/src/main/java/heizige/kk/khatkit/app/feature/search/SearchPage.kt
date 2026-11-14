package heizige.kk.khatkit.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchResult
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchSort
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.sort
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import heizige.kk.khatkit.app.core.util.toLocalDateTime
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.MochiPullToRefreshBox
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiSearchBar
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.LocalMochiListItemSpacing
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.mochiListSection
import ui.feice.mochi.list.rows
import ui.feice.mochi.theme.MochiTheme
import kotlin.uuid.Uuid

@Composable
fun SearchPage(vm: SearchViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val onOpenResult: (MessageSearchResult) -> Unit = { result ->
        navigateToChatPage(
            navController,
            chatId = Uuid.parse(result.conversationId),
            nodeId = Uuid.parse(result.nodeId),
        )
    }
    var showRebuildDialog by remember { mutableStateOf(false) }

    if (showRebuildDialog) {
        AppDialog(
            onDismissRequest = { showRebuildDialog = false },
            title = { Text(stringResource(R.string.search_page_rebuild_index)) },
            text = { Text(stringResource(R.string.search_page_rebuild_index_desc)) },
            confirmButton = {
                MochiTextButton(
                    onClick = {
                        showRebuildDialog = false
                        vm.rebuildIndex()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                MochiTextButton(
                    onClick = { showRebuildDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    MochiSettingsPageScaffold(
        // 正文顶上是固定的搜索栏 + 分段选择器，骨架级的交界渐隐会糊到它们
        edgeFade = false,
        title = stringResource(R.string.search_page_title),
        // 正文没有大标题，栏标题常显。
        titleProgress = { 1f },
        actions = {
            // 顶栏只留一颗「排序方式」按钮：刷新改成列表下拉刷新，
            // 重建索引这类低频动作收进排序菜单末尾，不再单独占一颗图标。
            SortMenuButton(
                current = vm.sortOrder,
                onSortChange = { vm.onSortChange(it) },
                onRebuildIndex = { showRebuildDialog = true },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            MochiSearchBar(
                query = vm.searchQuery,
                onQueryChange = { vm.onQueryChange(it) },
                placeholder = stringResource(R.string.search_page_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onSearch = { vm.search() },
            )
            SearchResultSection(vm = vm, onOpen = onOpenResult)
        }
    }
}

/** 顶栏的排序方式按钮：按钮上直接显示当前排序，菜单里再放三个排序项和「重建索引」。 */
@Composable
private fun SortMenuButton(
    current: MessageSearchSort,
    onSortChange: (MessageSearchSort) -> Unit,
    onRebuildIndex: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sortLabels = MessageSearchSort.entries.map { sort ->
        stringResource(
            when (sort) {
                MessageSearchSort.RELEVANCE -> R.string.search_page_sort_relevance
                MessageSearchSort.NEWEST_FIRST -> R.string.search_page_sort_newest
                MessageSearchSort.OLDEST_FIRST -> R.string.search_page_sort_oldest
            }
        )
    }
    // 选中态由 MochiDropdown 自己画（selectedIndex），不要再塞单选图标进每一项。
    val sortItems = sortLabels.map { MochiItem(label = it) }
    val items: ImmutableList<MochiItem> = persistentListOf(
        *(sortItems + MochiItem(label = stringResource(R.string.search_page_rebuild_index))).toTypedArray(),
    )

    MochiDropdown(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        items = items,
        onItemClick = { index ->
            expanded = false
            if (index < MessageSearchSort.entries.size) {
                MessageSearchSort.entries.getOrNull(index)?.let(onSortChange)
            } else {
                onRebuildIndex()
            }
        },
        selectedIndex = MessageSearchSort.entries.indexOf(current),
        // 触发器用栏里的透明玻璃 iconbutton（和页面其它顶栏按钮同一枚），
        // 下拉自身的胶囊底/发光全部透明，只借它的面板与选中态。
        colors = MochiDropdownDefaults.dropdownColors(
            buttonColor = Color.Transparent,
            glowColor = Color.Transparent,
        ),
        buttonPadding = PaddingValues(0.dp),
        onClick = { expanded = !expanded },
        buttonContent = {
            MochiIconButton(
                onClick = { expanded = !expanded },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(sort, contentDescription = stringResource(R.string.search_page_sort))
            }
        },
    )
}

/** 范围切换 + 结果列表。 */
@Composable
private fun SearchResultSection(
    vm: SearchViewModel,
    onOpen: (MessageSearchResult) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 范围切换用 Mochi 分段控件，跟设置页的其它分段选择一致。
        MochiSegmentedControl(
            selectedIndex = MessageSearchScope.entries.indexOf(vm.searchScope),
            onSelect = { index ->
                MessageSearchScope.entries.getOrNull(index)?.let(vm::onScopeChange)
            },
            items = persistentListOf(
                *MessageSearchScope.entries.map { scope ->
                    stringResource(
                        when (scope) {
                            MessageSearchScope.CURRENT_ASSISTANT -> R.string.search_page_scope_current_assistant
                            MessageSearchScope.ALL_ASSISTANTS -> R.string.search_page_scope_all_assistants
                        }
                    )
                }.toTypedArray(),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 8.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            when {
                vm.isRebuilding -> {
                    MochiEmptyState(
                        title = stringResource(R.string.search_page_rebuilding_simple),
                        message = if (vm.rebuildProgress.second > 0) {
                            stringResource(
                                R.string.search_page_rebuilding,
                                vm.rebuildProgress.first,
                                vm.rebuildProgress.second,
                            )
                        } else {
                            null
                        },
                        icon = { MochiIonicon("refresh", size = 36.dp) },
                    )
                }
                vm.searchQuery.isBlank() -> {
                    MochiEmptyState(
                        title = stringResource(R.string.search_page_hint),
                        icon = { MochiIonicon("search", size = 36.dp) },
                    )
                }
                vm.results.isEmpty() && !vm.isLoading -> {
                    MochiEmptyState(
                        title = stringResource(R.string.search_page_no_results),
                        icon = { MochiIonicon("search", size = 36.dp) },
                    )
                }
                else -> {
                    // 下拉刷新替代顶栏刷新图标：重新跑一次当前查询。
                    MochiPullToRefreshBox(
                        isRefreshing = vm.isLoading,
                        onRefresh = { vm.search() },
                    ) {
                        CompositionLocalProvider(
                            LocalMochiListItemSpacing provides MochiListDefaults.SectionSpacing,
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 16.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
                            ) {
                                mochiListSection(key = "results") {
                                    rows(vm.results) { result ->
                                        SearchResultRow(
                                            result = result,
                                            onClick = { onOpen(result) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一条搜索结果：标题 + 命中片段 + 时间，跟历史记录同一套 MochiListRow。 */
@Composable
private fun SearchResultRow(
    result: MessageSearchResult,
    onClick: () -> Unit,
) {
    val untitled = stringResource(R.string.search_page_untitled)
    val snippet = remember(result.snippet) {
        result.snippet.replace("[", "").replace("]", "")
    }
    val formattedTime = remember(result.updateAt) { result.updateAt.toLocalDateTime() }
    MochiListRow(
        title = result.title.ifBlank { untitled },
        subtitle = snippet,
        value = formattedTime,
        leading = {
            MochiIonicon(
                "chatbubble",
                size = MochiListDefaults.LeadingSize,
                tint = MochiTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}
