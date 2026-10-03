package heizige.kk.khatkit.app.feature.search

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.overlays.KedgeDropdownItemSlot
import heizige.kk.kedge.overlays.KedgeDropdownMenuSlots
import heizige.kk.kedge.components.KedgeSearchBar
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType
import heizige.kk.kedge.components.KedgeRadioButton
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
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeSingleChoiceSegmentedRow
import heizige.kk.khromia.components.SegmentedItem

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
        AppAlertDialog(
            onDismissRequest = { showRebuildDialog = false },
            title = { Text(stringResource(R.string.search_page_rebuild_index)) },
            text = { Text(stringResource(R.string.search_page_rebuild_index_desc)) },
            confirmButton = {
                KedgeTextButton(
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
                KedgeTextButton(onClick = { showRebuildDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.search_page_title),
        scrollBehavior = scrollBehavior,
        actions = {
            SortMenuButton(
                current = vm.sortOrder,
                onSortChange = { vm.onSortChange(it) },
            )
            KedgeIconButton(
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
            KedgeSearchBar(
                value = vm.searchQuery,
                onValueChange = { vm.onQueryChange(it) },
                placeholder = stringResource(R.string.search_page_placeholder),
                active = searchExpanded,
                onActiveChange = { searchExpanded = it },
                cancelLabel = stringResource(R.string.cancel),
                modifier = Modifier.fillMaxWidth(),
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

        KedgeSingleChoiceSegmentedRow(
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
                KedgeProgressIndicator(type = KedgeProgressIndicatorType.Linear, modifier = Modifier.fillMaxWidth())
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
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            style = KedgeTextStyles.body(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
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
    Box {
        KedgeIconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                sort,
                contentDescription = stringResource(R.string.search_page_sort)
            )
        }
        KedgeDropdownMenuSlots(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MessageSearchSort.entries.forEach { sort ->
                KedgeDropdownItemSlot(
                    text = {
                        Text(
                            stringResource(
                                when (sort) {
                                    MessageSearchSort.RELEVANCE -> R.string.search_page_sort_relevance
                                    MessageSearchSort.NEWEST_FIRST -> R.string.search_page_sort_newest
                                    MessageSearchSort.OLDEST_FIRST -> R.string.search_page_sort_oldest
                                }
                            )
                        )
                    },
                    leadingIcon = {
                        KedgeRadioButton(
                            selected = sort == current,
                            onClick = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortChange(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: MessageSearchResult,
    onClick: () -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
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

    KedgeSurface(
        onClick = onClick,
        color = CustomColors.listItemColors.containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = result.title.ifBlank { untitled },
                style = KedgeTextStyles.title(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = snippetText,
                style = KedgeTextStyles.body(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formattedTime,
                style = KedgeTextStyles.footnoteSmall(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
