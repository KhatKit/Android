package heizige.kk.khatkit.app.feature.download

import heizige.kk.khatkit.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiPageContentPadding
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.icons.utility.checkCircle
import heizige.kk.khatkit.app.core.ui.icons.navigation.close
import heizige.kk.khatkit.app.core.ui.icons.utility.delete
import heizige.kk.khatkit.app.core.ui.icons.utility.search
import heizige.kk.khatkit.app.core.ui.icons.files.download
import heizige.kk.khatkit.app.core.ui.icons.files.downloadForOffline
import heizige.kk.khatkit.app.core.ui.icons.utility.error
import heizige.kk.khatkit.app.core.ui.icons.media.pause
import heizige.kk.khatkit.app.core.ui.icons.media.playArrow
import heizige.kk.khatkit.app.core.ui.hooks.rememberSearchExpandState
import heizige.kk.khatkit.bridge.DownloadTaskInfo
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.MochiProgressRing
import ui.feice.mochi.navigation.MochiTitleBarDefaults
import ui.feice.mochi.effect.content.MochiMorphIcon
import ui.feice.mochi.effect.content.mochiBlurReveal
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListDefaults
import androidx.compose.foundation.layout.statusBarsPadding
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiSwipeAction
import ui.feice.mochi.list.MochiSwipeRow
import ui.feice.mochi.theme.MochiTheme

/** 全局下载中心：KhatKit bridge 下载（含脚本创建）与更新包下载都汇总到这里。 */
@Composable
fun DownloadCenterPage(vm: DownloadCenterViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    // 栏底渐显：与历史记录页同一套（标题常显、底色跟滚动淡入）。
    val listState = rememberLazyListState()
    // 搜索态不进 rememberSaveable：重新进页面应该回到普通态（ChatPage 的搜索也是临时的）。
    var query by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    // 搜索动效照 ChatPage：rememberSearchExpandState 驱动进度，mochiBlurReveal 淡入上移，
    // 文字用 navTitle 字阶（不是药丸输入框），返回键收起。
    val searchExpand = rememberSearchExpandState(
        expanded = searchActive,
        onCollapse = {
            searchActive = false
            query = ""
        },
    )
    val searchProgress = searchExpand.progress
    val searchVisible by remember { derivedStateOf { searchProgress.value > 0.001f } }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) {
            kotlinx.coroutines.delay(100)
            searchFocus.requestFocus()
        }
    }
    val filtered = if (query.isBlank()) {
        tasks
    } else {
        tasks.filter { it.name.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
    }

    // 清空是不可逆操作：跟历史记录同一套 Mochi 对话框 + 长按确认。
    MochiDialog(
        visible = showClearDialog,
        onDismissRequest = { showClearDialog = false },
        title = stringResource(R.string.download_clear_title),
        message = stringResource(R.string.download_clear_message),
        actions = persistentListOf(
            MochiDialogAction(
                label = stringResource(R.string.explore_market_clear),
                role = MochiDialogActionRole.Destructive,
                holdToConfirm = true,
                onClick = { vm.removeAll() },
            ),
            MochiDialogAction(label = stringResource(R.string.cancel), role = MochiDialogActionRole.Cancel),
        ),
    )

    MochiSettingsPageScaffold(
        title = stringResource(R.string.download_center_title),
        // 搜索时标题让位给输入框；平时标题常显、底色跟滚动淡入。
        titleProgress = { 1f - searchProgress.value },
        actions = {
            // 图标变化走 Mochi 的内容变换（MochiMorphIcon 原位变形），不是直接换一张图。
            MochiIconButton(
                onClick = {
                    if (searchActive) {
                        searchActive = false
                        query = ""
                    } else {
                        searchActive = true
                    }
                },
            ) {
                MochiMorphIcon(
                    icon = if (searchActive) close else search,
                    size = 24.dp,
                    tint = MochiTheme.colorScheme.onSurface,
                )
            }
            // 清空是普通态的动作：搜索时按进度淡出，不做尺寸补间。
            val deleteAlpha = 1f - searchProgress.value
            if (deleteAlpha > 0.001f) {
                MochiIconButton(
                    onClick = { showClearDialog = true },
                    enabled = tasks.isNotEmpty(),
                    modifier = Modifier.graphicsLayer { alpha = deleteAlpha },
                ) {
                    Icon(delete, contentDescription = stringResource(R.string.download_clear_title))
                }
            }
        },
        // 搜索框直接盖在栏标题的位置（ChatPage 同款：navTitle 字阶 + mochiBlurReveal 淡入上移）。
        overlayTitle = {
            if (searchVisible) {
                val navTitle = MochiTheme.textStyles.navTitle
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .height(MochiTitleBarDefaults.Height)
                        .padding(horizontal = MochiButtonDefaults.BarHorizontalPadding + 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .mochiBlurReveal({ searchProgress.value }, rise = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isBlank()) {
                            Text(
                                text = stringResource(R.string.download_search_placeholder),
                                style = navTitle,
                                color = MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = navTitle.copy(color = MochiTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MochiTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            val contentPadding = PaddingValues(
                start = MochiPageContentPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = MochiPageContentPadding.calculateEndPadding(LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            )

            when {
                tasks.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = download,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                }
                filtered.isEmpty() -> {
                    MochiEmptyState(
                        title = stringResource(R.string.download_empty_title),
                        modifier = Modifier.padding(innerPadding),
                        message = stringResource(R.string.download_empty_message),
                        icon = { MochiIonicon("search", size = 36.dp) },
                    )
                }
                else -> {
                    val activeTasks = filtered.filter { it.isActive }
                    val totalSpeedBps = activeTasks.sumOf { it.speedBps }
                    val ordered = filtered.sortedWith(
                        compareByDescending<DownloadTaskInfo> { it.isActive }
                            .thenByDescending { it.state == "paused" }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
                    ) {
                        if (activeTasks.isNotEmpty()) {
                            item(key = "summary") {
                                DownloadSummaryCard(
                                    activeCount = activeTasks.size,
                                    totalSpeedBps = totalSpeedBps,
                                )
                            }
                        }

                        item(key = "tasks") {
                            // 栏标题已经是「下载中心」，正文不再重复分组小标题。
                            MochiListSection {
                                ordered.forEach { task ->
                                    DownloadTaskRow(
                                        task = task,
                                        onPause = { vm.pause(task.id) },
                                        onResume = { vm.resume(task.id) },
                                        onCancel = { vm.cancel(task.id) },
                                        onRemove = { vm.remove(task.id) },
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

@Composable
private fun DownloadSummaryCard(
    activeCount: Int,
    totalSpeedBps: Long,
) {
    MochiListSection {
        MochiListRow(
            title = stringResource(R.string.download_active_title),
            value = androidx.compose.ui.res.pluralStringResource(R.plurals.download_active_count, activeCount, activeCount),
            subtitle = if (totalSpeedBps > 0) formatSpeed(totalSpeedBps) else null,
            leading = {
                Icon(
                    imageVector = downloadForOffline,
                    contentDescription = null,
                    modifier = Modifier.size(MochiListDefaults.LeadingSize),
                    tint = MochiTheme.colorScheme.primary,
                )
            },
            leadingStyle = MochiListLeadingStyle.Tile,
        )
    }
}

@Composable
private fun DownloadTaskRow(
    task: DownloadTaskInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val deleteAction = MochiSwipeAction(
        label = stringResource(R.string.delete),
        color = MochiTheme.colorScheme.error,
        onClick = onRemove,
        removesRow = true,
        icon = { tint -> Icon(close, contentDescription = null, tint = tint) },
    )

    val leadingAction = when (task.state) {
        // 配色与 Mochi 实例一致：primary 底配 onPrimary 字。
        "running" -> MochiSwipeAction(
            label = stringResource(R.string.download_pause),
            color = MochiTheme.colorScheme.primary,
            onClick = onPause,
            contentColor = MochiTheme.colorScheme.onPrimary,
            icon = { tint -> Icon(pause, contentDescription = null, tint = tint) },
        )
        "paused" -> MochiSwipeAction(
            label = stringResource(R.string.download_resume),
            color = MochiTheme.colorScheme.primary,
            onClick = onResume,
            contentColor = MochiTheme.colorScheme.onPrimary,
            icon = { tint -> Icon(playArrow, contentDescription = null, tint = tint) },
        )
        "queued" -> MochiSwipeAction(
            label = stringResource(R.string.cancel),
            color = MochiTheme.colorScheme.primary,
            onClick = onCancel,
            contentColor = MochiTheme.colorScheme.onPrimary,
            icon = { tint -> Icon(close, contentDescription = null, tint = tint) },
        )
        else -> null
    }

    MochiSwipeRow(
        actions = listOf(deleteAction),
        leadingActions = listOfNotNull(leadingAction),
        dividerInset = MochiListDefaults.RowPadding,
    ) {
        MochiListRow(
            title = task.name,
            subtitle = buildSubtitle(androidx.compose.ui.platform.LocalContext.current, task),
            leading = { TaskLeadingIcon(task) },
        )
    }
}

@Composable
private fun TaskLeadingIcon(task: DownloadTaskInfo) {
    when (task.state) {
        "running", "queued" -> {
            val progress = if (task.total > 0) task.progress else null
            MochiProgressRing(
                progress = progress,
                size = MochiListDefaults.LeadingSize,
                strokeWidth = 3.dp,
            )
        }
        "done" -> {
            Icon(
                imageVector = checkCircle,
                contentDescription = null,
                modifier = Modifier.size(MochiListDefaults.LeadingSize),
                tint = MochiTheme.colorScheme.primary,
            )
        }
        "failed" -> {
            Icon(
                imageVector = error,
                contentDescription = null,
                modifier = Modifier.size(MochiListDefaults.LeadingSize),
                tint = MochiTheme.colorScheme.error,
            )
        }
        "cancelled" -> {
            Icon(
                imageVector = close,
                contentDescription = null,
                modifier = Modifier.size(MochiListDefaults.LeadingSize),
                tint = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
        "paused" -> {
            MochiProgressRing(
                progress = if (task.total > 0) task.progress else 0f,
                size = MochiListDefaults.LeadingSize,
                strokeWidth = 3.dp,
            )
        }
        else -> {
            Icon(
                imageVector = download,
                contentDescription = null,
                modifier = Modifier.size(MochiListDefaults.LeadingSize),
                tint = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildSubtitle(context: android.content.Context, task: DownloadTaskInfo): String = buildString {
    when (task.state) {
        "running" -> {
            if (task.total > 0) {
                append("${formatBytes(task.bytes)} / ${formatBytes(task.total)}")
            } else {
                append(formatBytes(task.bytes))
            }
            if (task.speedBps > 0) {
                append(" · ${formatSpeed(task.speedBps)}")
            }
        }
        "queued" -> append(context.getString(R.string.download_status_queued))
        "paused" -> {
            if (task.total > 0) {
                append(context.getString(R.string.download_status_paused_total, formatBytes(task.bytes), formatBytes(task.total)))
            } else {
                append(context.getString(R.string.download_status_paused, formatBytes(task.bytes)))
            }
        }
        "done" -> append(context.getString(R.string.download_status_done, formatBytes(task.bytes)))
        "failed" -> append(context.getString(R.string.download_status_failed, task.error ?: context.getString(R.string.download_error_unknown)))
        "cancelled" -> append(context.getString(R.string.download_status_cancelled))
        else -> append(task.state)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

private fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / 1024f / 1024f)
    bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024f)
    else -> "$bytesPerSecond B/s"
}
