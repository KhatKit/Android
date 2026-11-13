package heizige.kk.khatkit.app.feature.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.download
import heizige.kk.khatkit.app.core.ui.icons.pause
import heizige.kk.khatkit.app.core.ui.icons.playArrow
import heizige.kk.khatkit.app.core.ui.icons.stopCircle
import heizige.kk.khatkit.bridge.DownloadTaskInfo
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import ui.feice.mochi.basic.MochiIconButton as NativeMochiIconButton
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection

/** 全局下载中心：KhatKit bridge 下载（含脚本创建）与更新包下载都汇总到这里。 */
@Composable
fun DownloadCenterPage(vm: DownloadCenterViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()

    MochiSettingsPageScaffold(
        title = "下载中心",
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = download,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "暂无下载任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            val ordered = tasks.sortedWith(
                compareByDescending<DownloadTaskInfo> { it.isActive }
                    .thenByDescending { it.state == "paused" }
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                item {
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
private fun DownloadTaskRow(
    task: DownloadTaskInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    MochiListRow(
        title = task.name,
        subtitle = buildString {
            append(stateText(task.state))
            if (task.total > 0) append("  ${(task.progress * 100).toInt()}%")
            val total = task.total
            if (total > 0) {
                append("  ")
                append(formatBytes(task.bytes))
                append(" / ")
                append(formatBytes(total))
            } else if (task.bytes > 0) {
                append("  ")
                append(formatBytes(task.bytes))
            }
            if (task.isActive && task.speedBps > 0) {
                append("  ")
                append(formatBytes(task.speedBps))
                append("/s")
            }
            task.error?.let {
                append("  ")
                append(it)
            }
            task.file?.let {
                append("  ")
                append(it)
            }
        },
        leading = {
            Icon(download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailing = {
            Row {
                when {
                    task.isActive -> {
                        NativeMochiIconButton(onClick = onPause) { Icon(pause, "暂停") }
                        NativeMochiIconButton(onClick = onCancel) { Icon(stopCircle, "取消") }
                    }
                    task.state == "paused" || task.state == "failed" -> {
                        NativeMochiIconButton(onClick = onResume) { Icon(playArrow, "继续") }
                        NativeMochiIconButton(onClick = onRemove) { Icon(delete, "删除") }
                    }
                    else -> NativeMochiIconButton(onClick = onRemove) { Icon(delete, "删除") }
                }
            }
        },
    )
}

private fun stateText(state: String): String = when (state) {
    "queued" -> "排队中"
    "running" -> "下载中"
    "paused" -> "已暂停"
    "done" -> "已完成"
    "failed" -> "失败"
    "cancelled" -> "已取消"
    else -> state
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var index = 0
    while (value >= 1024 && index < units.size - 1) {
        value /= 1024
        index++
    }
    return "%.1f%s".format(value, units[index])
}
