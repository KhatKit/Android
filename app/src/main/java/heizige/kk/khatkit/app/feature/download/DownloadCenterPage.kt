package heizige.kk.khatkit.app.feature.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiPageContentPadding
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.icons.checkCircle
import heizige.kk.khatkit.app.core.ui.icons.close
import heizige.kk.khatkit.app.core.ui.icons.download
import heizige.kk.khatkit.app.core.ui.icons.downloadForOffline
import heizige.kk.khatkit.app.core.ui.icons.error
import heizige.kk.khatkit.app.core.ui.icons.pause
import heizige.kk.khatkit.app.core.ui.icons.playArrow
import heizige.kk.khatkit.bridge.DownloadTaskInfo
import ui.feice.mochi.basic.MochiProgressRing
import ui.feice.mochi.list.MochiListDefaults
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

    MochiSettingsPageScaffold(title = "下载中心") { innerPadding ->
        val contentPadding = PaddingValues(
            start = MochiPageContentPadding.calculateStartPadding(LayoutDirection.Ltr),
            end = MochiPageContentPadding.calculateEndPadding(LayoutDirection.Ltr),
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 16.dp,
        )

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = download,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }
        } else {
            val activeTasks = tasks.filter { it.isActive }
            val totalSpeedBps = activeTasks.sumOf { it.speedBps }
            val ordered = tasks.sortedWith(
                compareByDescending<DownloadTaskInfo> { it.isActive }
                    .thenByDescending { it.state == "paused" }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                    MochiListSection(header = "下载任务") {
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

@Composable
private fun DownloadSummaryCard(
    activeCount: Int,
    totalSpeedBps: Long,
) {
    MochiListSection {
        MochiListRow(
            title = "活跃下载",
            value = "$activeCount 个",
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
        label = "删除",
        color = MochiTheme.colorScheme.error,
        onClick = onRemove,
        removesRow = true,
        icon = { tint -> Icon(close, contentDescription = null, tint = tint) },
    )

    val leadingAction = when (task.state) {
        "running" -> MochiSwipeAction(
            label = "暂停",
            color = MochiTheme.colorScheme.primary,
            onClick = onPause,
            icon = { tint -> Icon(pause, contentDescription = null, tint = tint) },
        )
        "paused" -> MochiSwipeAction(
            label = "继续",
            color = MochiTheme.colorScheme.primary,
            onClick = onResume,
            icon = { tint -> Icon(playArrow, contentDescription = null, tint = tint) },
        )
        "queued" -> MochiSwipeAction(
            label = "取消",
            color = MochiTheme.colorScheme.primary,
            onClick = onCancel,
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
            subtitle = buildSubtitle(task),
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

private fun buildSubtitle(task: DownloadTaskInfo): String = buildString {
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
        "queued" -> append("等待中")
        "paused" -> {
            if (task.total > 0) {
                append("已暂停 · ${formatBytes(task.bytes)} / ${formatBytes(task.total)}")
            } else {
                append("已暂停 · ${formatBytes(task.bytes)}")
            }
        }
        "done" -> append("已完成 · ${formatBytes(task.bytes)}")
        "failed" -> append("失败 · ${task.error ?: "未知错误"}")
        "cancelled" -> append("已取消")
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
