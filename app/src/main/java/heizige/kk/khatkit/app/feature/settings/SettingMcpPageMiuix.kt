package heizige.kk.khatkit.app.feature.settings

import androidx.compose.runtime.Composable
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.uploadFile
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixEmptyHint
import heizige.kk.khatkit.app.feature.settings.mcp.McpImportModal
import heizige.kk.khatkit.app.feature.settings.mcp.McpServerConfigModal
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton

/**
 * MCP 服务器页的 Miuix 风格版。
 *
 * 服务器卡片与配置/工具弹窗都已是双风格（卡片底色走 CustomColors，表单走 Kedge 双风格组件），
 * 这里只换外壳。
 */
@Composable
fun SettingMcpPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val mcpConfigs = settings.mcpServers
    val creationState = useEditState<McpServerConfig> {
        vm.updateSettings(settings.copy(mcpServers = mcpConfigs + it))
    }
    val editState = useEditState<McpServerConfig> { newConfig ->
        vm.updateSettings(
            settings.copy(
                mcpServers = mcpConfigs.map {
                    if (it.id == newConfig.id) newConfig else it
                }
            )
        )
    }
    var showImportDialog by remember { mutableStateOf(false) }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_mcp_page_title),
        navigationIcon = { BackButton() },
        actions = {
            KedgeIconButton(onClick = { showImportDialog = true }) {
                Icon(imageVector = uploadFile, contentDescription = null)
            }
            KedgeIconButton(
                onClick = { creationState.open(McpServerConfig.StreamableHTTPServer()) }
            ) {
                Icon(imageVector = add, contentDescription = null)
            }
        },
        bottomInnerPadding = 16.dp,
    ) {
        if (mcpConfigs.isEmpty()) {
            item {
                MiuixEmptyHint(
                    text = stringResource(R.string.setting_mcp_page_no_mcp_servers_found),
                    secondary = stringResource(R.string.setting_mcp_page_add_one_to_get_started),
                )
            }
        }
        items(mcpConfigs.size, key = { mcpConfigs[it].id }) { index ->
            val mcpConfig = mcpConfigs[index]
            McpServerItem(
                item = mcpConfig,
                onEdit = { editState.open(mcpConfig) },
                onDelete = {
                    vm.updateSettings(
                        settings.copy(mcpServers = mcpConfigs.filter { it.id != mcpConfig.id })
                    )
                },
            )
        }
    }

    McpServerConfigModal(creationState)
    McpServerConfigModal(editState)
    if (showImportDialog) {
        McpImportModal(
            onDismiss = { showImportDialog = false },
            onImport = { newConfigs ->
                val existingIds = mcpConfigs.map { it.commonOptions.name }.toSet()
                val toAdd = newConfigs.filter {
                    it.commonOptions.name.isNotBlank() && it.commonOptions.name !in existingIds
                }
                vm.updateSettings(settings.copy(mcpServers = mcpConfigs + toAdd))
                showImportDialog = false
            }
        )
    }
}
