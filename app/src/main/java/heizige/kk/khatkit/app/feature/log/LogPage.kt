package heizige.kk.khatkit.app.feature.log

import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.KkCard
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import heizige.kk.khatkit.uikit.mochi.Toast as KhromiaToast
import heizige.kk.khatkit.common.android.LogEntry
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.common.android.Logging
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.JsonTree
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import heizige.kk.khatkit.app.core.util.JsonInstantPretty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import heizige.kk.khatkit.app.core.ui.icons.bugReport
import heizige.kk.khatkit.app.core.ui.icons.contentCopy
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormDivider

@Composable
fun LogPage() {
    var logs by remember { mutableStateOf(Logging.getRecentLogs()) }
    var requestLoggingEnabled by remember { mutableStateOf(Logging.isRequestLoggingEnabled()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgeSettingsPageScaffold(
        title = "Logs",
        scrollBehavior = scrollBehavior,
        actions = {
            MochiIconButton(
                onClick = {
                    Logging.clear()
                    logs = Logging.getRecentLogs()
                },
            ) {
                Icon(delete, null)
            }
        },
    ) { contentPadding ->
        UnifiedLogList(
            logs = logs,
            requestLoggingEnabled = requestLoggingEnabled,
            onRequestLoggingChange = {
                requestLoggingEnabled = it
                Logging.setRequestLoggingEnabled(it)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    }
}

@Composable
private fun UnifiedLogList(
    logs: List<LogEntry>,
    requestLoggingEnabled: Boolean,
    onRequestLoggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLog by remember { mutableStateOf<LogEntry.RequestLog?>(null) }
    val sortedLogs = remember(logs) { logs.sortedByDescending { it.timestamp } }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            RequestLoggingSwitchCard(
                enabled = requestLoggingEnabled,
                onEnabledChange = onRequestLoggingChange
            )
        }

        items(sortedLogs, key = { it.id }, contentType = { it.javaClass.simpleName }) { log ->
            when (log) {
                is LogEntry.RequestLog -> RequestLogCard(
                    log = log,
                    onClick = { selectedLog = log }
                )

                is LogEntry.TextLog -> TextLogCard(log = log)
            }
        }
    }

    selectedLog?.let { log ->
        PrimaryBottomSheet(
            visible = true,
            title = "Request Details",
            imageVector = bugReport,
            onDismiss = { selectedLog = null },
            scrollable = false,
        ) { _ ->
            RequestLogDetail(log)
        }
    }
}

@Composable
private fun RequestLoggingSwitchCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    // 照 Mochi（iOS）：开关行本身不可点、不高亮，只有开关能拨。
    // 这是偏好行，不是日志条目，所以随其它选项行一起走 OptionItem；上下两条日志卡
    // 仍是内容卡（带可点详情 / 选择容器），不动。
    MochiOptionItem(
        onClick = null,
        modifier = Modifier.fillMaxWidth(),
        shape = listCardStyle().indexedShape(0, 1),
        titleContent = {
            Text(
                text = stringResource(R.string.log_page_record_requests),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.log_page_record_requests_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            KkSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        },
    )
}

@Composable
private fun RequestLogCard(log: LogEntry.RequestLog, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    KkCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.method,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = log.url,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = JetbrainsMono,
                maxLines = 2
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                log.responseCode?.let { code ->
                    Text(
                        text = "Status: $code",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (code in 200..299) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                log.durationMs?.let { duration ->
                    Text(
                        text = "${duration}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            log.error?.let { error ->
                Text(
                    text = "Error: $error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RequestLogDetail(log: LogEntry.RequestLog) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val clipboard = LocalClipboard.current
    val copyTooLargeMessage = stringResource(R.string.log_page_copy_too_large)
    val scope = rememberCoroutineScope()

    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DetailSection("Time", dateFormat.format(Date(log.timestamp)))
            }

            item {
                DetailSection("URL", log.url)
            }

            item {
                DetailSection("Method", log.method)
            }

            log.responseCode?.let { code ->
                item {
                    DetailSection("Status Code", code.toString())
                }
            }

            log.durationMs?.let { duration ->
                item {
                    DetailSection("Duration", "${duration}ms")
                }
            }

            log.error?.let { error ->
                item {
                    DetailSection("Error", error)
                }
            }

            if (log.requestHeaders.isNotEmpty()) {
                item {
                    KedgeFormDivider()
                    Text(
                        text = "Request Headers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                log.requestHeaders.forEach { (key, value) ->
                    item {
                        HeaderItem(key, value)
                    }
                }
            }

            log.requestBody?.let { body ->
                item {
                    KedgeFormDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Request Body",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        MochiIconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("Request Body", body))
                                        )
                                    } catch (e: RuntimeException) {
                                        // 内容过大时 Binder 会抛出 TransactionTooLargeException (被包装为 RuntimeException)
                                        KhromiaToast.show(
                                            copyTooLargeMessage,
                                            isError = true,
                                        )
                                    }
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = contentCopy,
                                contentDescription = stringResource(R.string.copy)
                            )
                        }
                    }
                    val jsonElement = remember(body) {
                        runCatching { JsonInstantPretty.parseToJsonElement(body) }.getOrNull()
                    }
                    if (jsonElement != null) {
                        JsonTree(
                            json = jsonElement,
                            modifier = Modifier.padding(top = 4.dp),
                            initialExpandLevel = 2
                        )
                    } else {
                        Text(
                            text = body,
                            fontFamily = JetbrainsMono,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (log.responseHeaders.isNotEmpty()) {
                item {
                    KedgeFormDivider()
                    Text(
                        text = "Response Headers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                log.responseHeaders.forEach { (key, value) ->
                    item {
                        HeaderItem(key, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetbrainsMono
        )
    }
}

@Composable
private fun HeaderItem(key: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetbrainsMono
        )
    }
}

@Composable
private fun TextLogCard(log: LogEntry.TextLog) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    KkCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dateFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = JetbrainsMono
                )
            }
        }
    }
}
