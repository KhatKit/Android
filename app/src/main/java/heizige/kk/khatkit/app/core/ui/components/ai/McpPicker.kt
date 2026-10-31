package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.runtime.LaunchedEffect
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.basic.rememberMochiSheetState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import androidx.compose.material3.Text
import heizige.kk.khatkit.uikit.mochi.MochiBadge
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.mcp.McpStatus
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.ui.ToggleSurface
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.power
import heizige.kk.khatkit.app.core.ui.icons.warning
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicatorType
import heizige.kk.khatkit.uikit.mochi.MochiBadgedBox
import ui.feice.mochi.basic.MochiActivityIndicator
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.list.LocalMochiListItemSpacing
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.mochiListSection
import ui.feice.mochi.list.rows
import ui.feice.mochi.theme.MochiTheme

@Composable
fun McpPickerButton(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    modifier: Modifier = Modifier,
    onUpdateAssistant: (Assistant) -> Unit
) {
    var showMcpPicker by remember { mutableStateOf(false) }
    val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = status.values.any { it == McpStatus.Connecting }
    val enabledServers = servers.fastFilter {
        it.commonOptions.enable && assistant.mcpServers.contains(it.id)
    }
    ToggleSurface(
        modifier = modifier,
        checked = assistant.mcpServers.isNotEmpty(),
        onClick = {
            showMcpPicker = true
        }
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    KkProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    MochiBadgedBox(
                        badge = {
                            if (enabledServers.isNotEmpty()) {
                                MochiBadge(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(text = enabledServers.size.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = dns,
                            contentDescription = stringResource(R.string.mcp_picker_title),
                        )
                    }

                }
            }
        }
    }
        PrimaryBottomSheet(
            visible = showMcpPicker,
            title = stringResource(id = R.string.mcp_picker_title),
            imageVector = dns,
            onDismiss = { showMcpPicker = false },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(loading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        KkProgressIndicator(
                    type = KkProgressIndicatorType.Linear,)
                        Text(
                            text = stringResource(id = R.string.mcp_picker_syncing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                McpPicker(
                    assistant = assistant,
                    servers = servers,
                    onUpdateAssistant = {
                        onUpdateAssistant(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
}

@Composable
fun McpPickerListItem(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    modifier: Modifier = Modifier,
    /** 嵌在别的分组卡片里时由外面给首末行圆角；不给就单行自成一组。 */
    shape: Shape? = null,
    onUpdateAssistant: (Assistant) -> Unit
) {
    var showMcpPicker by remember { mutableStateOf(false) }
    val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = status.values.any { it == McpStatus.Connecting }
    val enabledServers = servers.fastFilter {
        it.commonOptions.enable && assistant.mcpServers.contains(it.id)
    }

    // 入口行也是 option item：原来自己按 MaterialTheme 的大圆角 clip + clickable，
    // 与同文件服务器行的圆角/底色/按压反馈两套写法；现在形状与间距统一取
    // listCardStyle()，单行自成一组（index 0 / count 1）⇒ 四角同大圆角。
    val cards = listCardStyle()
    MochiOptionItem(
        // 整行可点（点开 MCP 选择弹层），与改动前 clickable 语义一致。
        onClick = { showMcpPicker = true },
        modifier = modifier.fillMaxWidth(),
        shape = shape ?: cards.indexedShape(index = 0, count = 1),
        leadingContent = {
            if (loading) {
                KkProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = dns,
                    contentDescription = stringResource(R.string.mcp_picker_title),
                )
            }
        },
        titleContent = {
            Text(stringResource(R.string.mcp_picker_title))
        },
        trailingContent = {
            if (enabledServers.isNotEmpty()) {
                Text(
                    text = enabledServers.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )

    // U7b-6：state 由这里持有，去 if 门控。
    val mcpSheet = rememberMochiSheetState()
    LaunchedEffect(showMcpPicker) { if (showMcpPicker) mcpSheet.show() else mcpSheet.hide() }
    McpPickerSheet(
            sheetState = mcpSheet,
            assistant = assistant,
            servers = servers,
            loading = loading,
            onUpdateAssistant = onUpdateAssistant,
            onDismiss = { showMcpPicker = false },
        )
}

@Composable
private fun McpPickerSheet(
    sheetState: MochiSheetState,
    assistant: Assistant,
    servers: List<McpServerConfig>,
    loading: Boolean,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismiss: () -> Unit,
) {
    // U7b-6：直连 MochiSheet（state 由调用方持有）。
    MochiSheet(state = sheetState, onDismiss = onDismiss) {
        MochiSheetHeader(title = stringResource(id = R.string.mcp_picker_title))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(loading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    KkProgressIndicator(
                    type = KkProgressIndicatorType.Linear,)
                    Text(
                        text = stringResource(id = R.string.mcp_picker_syncing),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            McpPicker(
                assistant = assistant,
                servers = servers,
                onUpdateAssistant = {
                    onUpdateAssistant(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
fun McpPicker(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onUpdateAssistant: (Assistant) -> Unit
) {
    val mcpManager = rememberAppEntryPoint().mcpManager()
    val visibleServers = servers.fastFilter { it.commonOptions.enable }
    val setServerEnabled: (McpServerConfig, Boolean) -> Unit = { server, enabled ->
        val newServers = assistant.mcpServers.toMutableSet()
        if (enabled) newServers.add(server.id) else newServers.remove(server.id)
        newServers.removeIf { servers.none { s -> s.id == server.id } } // remove invalid servers
        onUpdateAssistant(
            assistant.copy(
                mcpServers = newServers.toSet()
            )
        )
    }
    // 全部服务器 = 一张 Mochi 分组卡片（懒加载：每行是列表的一项，仍连成一张卡片）。
    // 整行可点切换，行尾开关同样可拨。列表无项间距，所以显式提供 0，避免继承外层的值。
    CompositionLocalProvider(LocalMochiListItemSpacing provides 0.dp) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            mochiListSection(key = "mcp_servers") {
                rows(visibleServers, key = { it.id.toString() }) { server ->
                    val status by mcpManager.getStatus(server).collectAsStateWithLifecycle(McpStatus.Idle)
                    val checked = server.id in assistant.mcpServers
                    val tools = server.commonOptions.tools
                    MochiListRow(
                        title = server.commonOptions.name,
                        subtitle = when (val s = status) {
                            is McpStatus.Idle -> "Idle"
                            is McpStatus.Connecting -> "Connecting"
                            is McpStatus.Connected -> "Connected"
                            is McpStatus.Reconnecting -> "Reconnecting (${s.attempt}/${s.maxAttempts})"
                            is McpStatus.Error -> "Error: ${s.message}"
                            is McpStatus.NeedsAuthorization -> "Needs authorization"
                            is McpStatus.Authorizing -> "Authorizing"
                        },
                        // 工具数量放行尾的值（原来是标题旁的 Tag），只在已连接时显示。
                        value = if (status == McpStatus.Connected) {
                            "${tools.fastFilter { it.enable }.size}/${tools.size} tools"
                        } else {
                            null
                        },
                        leading = { McpStatusIcon(status) },
                        onClick = { setServerEnabled(server, !checked) },
                        trailing = {
                            MochiSwitch(
                                checked = checked,
                                onCheckedChange = { setServerEnabled(server, it) },
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 行首的连接状态：连接中 / 重连 / 授权中转圈，其余用图标。 */
@Composable
private fun McpStatusIcon(status: McpStatus) {
    val cs = MochiTheme.colorScheme
    when (status) {
        McpStatus.Idle -> McpRowIcon(power, cs.primary)
        McpStatus.Connected -> McpRowIcon(dns, cs.primary)
        is McpStatus.Error -> McpRowIcon(warning, cs.error)
        McpStatus.NeedsAuthorization -> McpRowIcon(warning, cs.error)
        McpStatus.Connecting,
        is McpStatus.Reconnecting,
        McpStatus.Authorizing -> MochiActivityIndicator(size = 20.dp)
    }
}

@Composable
private fun McpRowIcon(icon: ImageVector, tint: Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = tint,
    )
}
