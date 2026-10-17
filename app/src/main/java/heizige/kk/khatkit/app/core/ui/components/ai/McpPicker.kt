package heizige.kk.khatkit.app.core.ui.components.ai

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeBadge
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import heizige.kk.khatkit.app.core.ui.components.ui.Tag
import heizige.kk.khatkit.app.core.ui.components.ui.TagType
import heizige.kk.khatkit.app.core.ui.components.ui.ToggleSurface
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.icons.dns
import heizige.kk.khatkit.app.core.ui.icons.power
import heizige.kk.khatkit.app.core.ui.icons.warning
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType
import heizige.kk.kedge.components.KedgeBadgedBox

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
                    KedgeProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    KedgeBadgedBox(
                        badge = {
                            if (enabledServers.isNotEmpty()) {
                                KedgeBadge(
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
    if (showMcpPicker) {
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(id = R.string.mcp_picker_title),
            imageVector = dns,
            onDismiss = { showMcpPicker = false },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier.Companion
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
                        KedgeProgressIndicator(
                    type = KedgeProgressIndicatorType.Linear,)
                        Text(
                            text = stringResource(id = R.string.mcp_picker_syncing),
                            style = KedgeTextStyles.body()
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
}

@Composable
fun McpPickerListItem(
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

    // 入口行也是 option item：原来自己按 MaterialTheme 的大圆角 clip + clickable，
    // 与同文件服务器行的圆角/底色/按压反馈两套写法；现在形状与间距统一取
    // listCardStyle()，单行自成一组（index 0 / count 1）⇒ 四角同大圆角。
    val cards = listCardStyle()
    KedgeOptionItem(
        // 整行可点（点开 MCP 选择弹层），与改动前 clickable 语义一致。
        onClick = { showMcpPicker = true },
        modifier = modifier.fillMaxWidth(),
        shape = cards.indexedShape(index = 0, count = 1),
        leadingContent = {
            if (loading) {
                KedgeProgressIndicator(modifier = Modifier.size(24.dp))
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
                    style = KedgeTextStyles.footnoteSmall(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )

    if (showMcpPicker) {
        McpPickerSheet(
            assistant = assistant,
            servers = servers,
            loading = loading,
            onUpdateAssistant = onUpdateAssistant,
            onDismiss = { showMcpPicker = false },
        )
    }
}

@Composable
private fun McpPickerSheet(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    loading: Boolean,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismiss: () -> Unit,
) {
    PrimaryBottomSheet(
        visible = true,
        title = stringResource(id = R.string.mcp_picker_title),
        imageVector = dns,
        onDismiss = onDismiss,
        scrollable = false,
    ) { _ ->
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
                    KedgeProgressIndicator(
                    type = KedgeProgressIndicatorType.Linear,)
                    Text(
                        text = stringResource(id = R.string.mcp_picker_syncing),
                        style = KedgeTextStyles.body()
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
    // 每条服务器 = 一张 option item：圆角/行间距取全局列表卡片样式（首项只圆上边、
    // 末项只圆下边），不再用裸 KedgeCard 自己排 Row。
    val cards = listCardStyle()
    val visibleServers = servers.fastFilter { it.commonOptions.enable }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(cards.gap),
    ) {
        itemsIndexed(visibleServers) { index, server ->
            val status by mcpManager.getStatus(server).collectAsStateWithLifecycle(McpStatus.Idle)
            KedgeOptionItem(
                // 行本身不可点，只有行尾开关可点（与改动前一致）。
                onClick = null,
                shape = cards.indexedShape(index, visibleServers.size),
                // 状态图标/进度圈占 leading 槽。
                leadingContent = {
                    when (status) {
                        McpStatus.Idle -> Icon(power, null)
                        McpStatus.Connecting -> KedgeProgressIndicator(
                            modifier = Modifier.size(
                                24.dp
                            )
                        )

                        McpStatus.Connected -> Icon(dns, null)
                        is McpStatus.Reconnecting -> KedgeProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        is McpStatus.Error -> Icon(warning, null)
                        McpStatus.NeedsAuthorization -> Icon(warning, null)
                        McpStatus.Authorizing -> KedgeProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                titleContent = {
                    Text(
                        text = server.commonOptions.name,
                        style = KedgeTextStyles.displayTitle(),
                    )
                },
                supportingContent = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when (val s = status) {
                                is McpStatus.Idle -> "Idle"
                                is McpStatus.Connecting -> "Connecting"
                                is McpStatus.Connected -> "Connected"
                                is McpStatus.Reconnecting -> "Reconnecting (${s.attempt}/${s.maxAttempts})"
                                is McpStatus.Error -> "Error: ${s.message}"
                                is McpStatus.NeedsAuthorization -> "Needs authorization"
                                is McpStatus.Authorizing -> "Authorizing"
                            },
                            style = KedgeTextStyles.footnoteSmall(),
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                            maxLines = 5
                        )
                        if (status == McpStatus.Connected) {
                            val tools = server.commonOptions.tools
                            val enabledTools = tools.fastFilter { it.enable }
                            Tag(
                                type = TagType.INFO
                            ) {
                                Text("${enabledTools.size}/${tools.size} tools")
                            }
                        }
                    }
                },
                trailingContent = {
                    KedgeSwitch(
                        checked = server.id in assistant.mcpServers,
                        onCheckedChange = {
                            if (it) {
                                val newServers = assistant.mcpServers.toMutableSet()
                                newServers.add(server.id)
                                newServers.removeIf { servers.none { s -> s.id == server.id } } // remove invalid servers
                                onUpdateAssistant(
                                    assistant.copy(
                                        mcpServers = newServers.toSet()
                                    )
                                )
                            } else {
                                val newServers = assistant.mcpServers.toMutableSet()
                                newServers.remove(server.id)
                                newServers.removeIf { servers.none { s -> s.id == server.id } } //  remove invalid servers
                                onUpdateAssistant(
                                    assistant.copy(
                                        mcpServers = newServers.toSet()
                                    )
                                )
                            }
                        }
                    )
                },
            )
        }
    }
}
