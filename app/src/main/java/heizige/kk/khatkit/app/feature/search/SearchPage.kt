package heizige.kk.khatkit.app.feature.search

import heizige.kk.khatkit.app.core.ui.components.ui.kkScrollEdgeFade
import heizige.kk.khatkit.uikit.mochi.Md3SearchBar
import heizige.kk.khatkit.uikit.mochi.Md3RadioButton
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.MochiPageLargeTopBar
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.khatkit.uikit.mochi.MochiMenuRow
import heizige.kk.khatkit.uikit.mochi.MochiMenuPopup
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicatorType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchResult
import heizige.kk.khatkit.app.core.data.db.fts.MessageSearchSort
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.util.toLocalDateTime
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.core.ui.icons.sort
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.uikit.mochi.KkSingleChoiceSegmentedRow
import heizige.kk.khatkit.uikit.mochi.SegmentedItem
import ui.feice.mochi.theme.MochiTheme

@Composable
fun SearchPage(vm: SearchViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    var searchExpanded by remember { mutableStateOf(false) }
    val onOpenResult: (MessageSearchResult) -> Unit = { result ->
        searchExpanded = false
        navigateToChatPage(
            navController,
            chatId = Uuid.parse(result.conversationId),
            nodeId = Uuid.parse(result.nodeId),
        )
    }
    var showRebuildDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 进页面直接展开全屏搜索（原来是给内联输入框 requestFocus，现在药丸点开才是输入框）
    LaunchedEffect(Unit) {
        searchExpanded = true
    }

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
                MochiTextButton(onClick = { showRebuildDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    MochiSettingsPageScaffold(
        // 正文里有固定的搜索栏 / 工具条 / 全屏视图，骨架级的交界渐隐会糊到它们
        edgeFade = false,
        title = stringResource(R.string.search_page_title),
        scrollBehavior = scrollBehavior,
        actions = {
            SortMenuButton(
                current = vm.sortOrder,
                onSortChange = { vm.onSortChange(it) },
            )
            MochiIconButton(
                onClick = { showRebuildDialog = true },
                enabled = !vm.isRebuilding,
            ) {
                Icon(
                    refresh,
                    contentDescription = stringResource(R.string.search_page_rebuild_button)
                )
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // KSU 式药丸搜索框（KernelSU SuperSearchBar 同一套逻辑）：点一下折叠药丸，
            // 展开成铺满全屏的搜索页 + 取消键；返回键收起。展开期间结果区整个搬进
            // 全屏槽，折叠态才平铺在页面里。
            Md3SearchBar(
                value = vm.searchQuery,
                onValueChange = { vm.onQueryChange(it) },
                placeholder = stringResource(R.string.search_page_placeholder),
                active = searchExpanded,
                onActiveChange = { searchExpanded = it },
                cancelLabel = stringResource(R.string.cancel),
                modifier = Modifier.fillMaxWidth(),
                // 这里**必须**保持 true：SearchPage 进页面就 LaunchedEffect 自动展开
                // （SearchPage.kt:85），若空关键词不展示，一进来是一片空白，而且范围切换
                // 段选器（当前助手 / 所有助手）也被结果区一起藏掉、没法切 —— 那是死路。
                showResultsWhenEmpty = true,
                expandedContent = {
                    // 独立窗口里不能有 sharedElement，否则 lookahead 配对跨 ViewRoot 会崩
                    NoHeroTransition {
                        SearchResultSection(vm = vm, onOpen = onOpenResult)
                    }
                },
            )

            if (!searchExpanded) {
                SearchResultSection(vm = vm, onOpen = onOpenResult)
            }
        }
    }
}


/** 范围切换 + 结果列表。折叠态显示在页面里，展开态塞进搜索框的全屏槽。 */
@Composable
private fun SearchResultSection(
    vm: SearchViewModel,
    onOpen: (MessageSearchResult) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {

        KkSingleChoiceSegmentedRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            items = MessageSearchScope.entries.map { scope ->
                SegmentedItem(
                    label = stringResource(
                        when (scope) {
                            MessageSearchScope.CURRENT_ASSISTANT -> R.string.search_page_scope_current_assistant
                            MessageSearchScope.ALL_ASSISTANTS -> R.string.search_page_scope_all_assistants
                        }
                    ),
                    selected = vm.searchScope == scope,
                    onClick = { vm.onScopeChange(scope) },
                )
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            if (vm.isLoading || vm.isRebuilding) {
                KkProgressIndicator(type = KkProgressIndicatorType.Linear, modifier = Modifier.fillMaxWidth())
            }

            when {
                vm.isRebuilding -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val (current, total) = vm.rebuildProgress
                        Text(
                            text = if (total > 0) stringResource(
                                R.string.search_page_rebuilding,
                                current,
                                total
                            ) else stringResource(R.string.search_page_rebuilding_simple),
                            style = MochiTheme.textStyles.body,
                            color = MochiTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                vm.searchQuery.isBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.search_page_hint),
                            style = MochiTheme.textStyles.body,
                            color = MochiTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                vm.results.isEmpty() && !vm.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.search_page_no_results),
                            style = MochiTheme.textStyles.body,
                            color = MochiTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        // 搜索栏 / 筛选条是固定的，交界渐隐加在它下面的列表上（这一页骨架级的关了）
                        modifier = Modifier.fillMaxSize().kkScrollEdgeFade(bottom = 0.dp),
                    ) {
                        items(vm.results) { result ->
                            SearchResultItem(
                                result = result,
                                onClick = { onOpen(result) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenuButton(
    current: MessageSearchSort,
    onSortChange: (MessageSearchSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val items: kotlinx.collections.immutable.ImmutableList<ui.feice.mochi.basic.MochiItem> =
        kotlinx.collections.immutable.persistentListOf(*MessageSearchSort.entries.map { sort ->
            ui.feice.mochi.basic.MochiItem(
                label = stringResource(
                    when (sort) {
                        MessageSearchSort.RELEVANCE -> R.string.search_page_sort_relevance
                        MessageSearchSort.NEWEST_FIRST -> R.string.search_page_sort_newest
                        MessageSearchSort.OLDEST_FIRST -> R.string.search_page_sort_oldest
                    }
                ),
                icon = { color ->
                    Md3RadioButton(selected = sort == current, onClick = null)
                },
            )
        }.toTypedArray())

    ui.feice.mochi.basic.MochiDropdown(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        items = items,
        onItemClick = { index ->
            expanded = false
            MessageSearchSort.entries.getOrNull(index)?.let(onSortChange)
        },
        selectedIndex = MessageSearchSort.entries.indexOf(current),
        onClick = { expanded = !expanded },
        buttonContent = {
            Icon(sort, contentDescription = stringResource(R.string.search_page_sort))
        },
    )
}

@Composable
private fun SearchResultItem(
    result: MessageSearchResult,
    onClick: () -> Unit,
) {
    val highlightColor = MochiTheme.colorScheme.tertiaryContainer
    val untitled = stringResource(R.string.search_page_untitled)
    val snippetText = buildAnnotatedString {
        val snippet = result.snippet
        var index = 0
        while (index < snippet.length) {
            val start = snippet.indexOf('[', index)
            if (start == -1) {
                append(snippet.substring(index))
                break
            }
            if (start > index) {
                append(snippet.substring(index, start))
            }
            val end = snippet.indexOf(']', start + 1)
            if (end == -1) {
                append(snippet.substring(start))
                break
            }
            val matched = snippet.substring(start + 1, end)
            withStyle(SpanStyle(background = highlightColor)) {
                append(matched)
            }
            index = end + 1
        }
    }
    val formattedTime = remember(result.updateAt) {
        result.updateAt.toLocalDateTime()
    }

    KkSurface(
        onClick = onClick,
        color = CustomColors.listItemColors.containerColor,
        shape = MochiTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = result.title.ifBlank { untitled },
                style = MochiTheme.textStyles.titleMedium,
                color = MochiTheme.colorScheme.onSurface,
            )
            Text(
                text = snippetText,
                style = MochiTheme.textStyles.body,
                color = MochiTheme.colorScheme.onSurface,
            )
            Text(
                text = formattedTime,
                style = MochiTheme.textStyles.labelSmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
