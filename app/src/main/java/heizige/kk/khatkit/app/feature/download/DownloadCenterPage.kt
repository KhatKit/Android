package heizige.kk.khatkit.app.feature.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageTopBar
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.download
import heizige.kk.khatkit.app.core.ui.icons.pause
import heizige.kk.khatkit.app.core.ui.icons.playArrow
import heizige.kk.khatkit.app.core.ui.icons.stopCircle
import heizige.kk.khatkit.bridge.DownloadTaskInfo
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** 全局下载中心：KhatKit bridge 下载（含脚本创建）与更新包下载都汇总到这里。 */
@Composable
fun DownloadCenterPage(vm: DownloadCenterViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            KedgePageTopBar(
                title = "下载中心",
                navigationIcon = { BackButton() },
            )
        }
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ordered, key = { it.id }) { task ->
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

@Composable
private fun DownloadTaskRow(
    task: DownloadTaskInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(stateText(task.state))
                            val total = task.total
                            if (total > 0) {
                                append(" · ")
                                append(formatBytes(task.bytes))
                                append(" / ")
                                append(formatBytes(total))
                            } else if (task.bytes > 0) {
                                append(" · ")
                                append(formatBytes(task.bytes))
                            }
                            if (task.isActive && task.speedBps > 0) {
                                append(" · ")
                                append(formatBytes(task.speedBps))
                                append("/s")
                            }
                            task.error?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.state == "failed") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                when {
                    task.isActive -> {
                        IconButton(onClick = onPause, shapes = IconButtonDefaults.shapes()) {
                            Icon(pause, contentDescription = "暂停", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
                            Icon(stopCircle, contentDescription = "取消", modifier = Modifier.size(20.dp))
                        }
                    }

                    task.state == "paused" || task.state == "failed" -> {
                        IconButton(onClick = onResume, shapes = IconButtonDefaults.shapes()) {
                            Icon(playArrow, contentDescription = "继续", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                            Icon(delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
                        }
                    }

                    else -> {
                        IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                            Icon(delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (task.isActive || task.state == "paused") {
                if (task.total > 0) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            task.file?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
