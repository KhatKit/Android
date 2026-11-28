package heizige.kk.khatkit.app.feature.extensions

import heizige.kk.khatkit.app.core.data.terminal.TerminalPluginInfo

import heizige.kk.khatkit.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.data.ssh.SshPluginLoader
import heizige.kk.khatkit.app.core.data.ssh.SshPluginRecord
import heizige.kk.khatkit.app.core.data.ssh.SshPluginRepository
import heizige.kk.khatkit.app.core.data.terminal.TerminalPluginLoader
import heizige.kk.khatkit.app.core.data.terminal.TerminalPluginRepository
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.shell.MochiSettingsPageScaffold
import heizige.kk.khatkit.ssh.api.SshPluginInfo
import ui.feice.mochi.theme.MochiTheme

/**
 * 插件 UI 宿主：按 pluginId 分发到插件自己的页面。
 *
 * 插件不是卡片 —— 插件包从 Hub 下载安装（SshPluginRepository 记录版本），宿主只负责分发；
 * 未安装 / 未知 id 一律显示 Mochi 空态页，不会串到别的插件页面上。
 */
@Composable
fun PluginHostPage(pluginId: String) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    var loaded by remember(pluginId) { mutableStateOf(false) }
    var record by remember(pluginId) { mutableStateOf<SshPluginRecord?>(null) }
    var terminalInstalled by remember(pluginId) { mutableStateOf(false) }

    LaunchedEffect(pluginId) {
        val installed = runCatching { SshPluginRepository(context).read() }.getOrNull()
        SshPluginLoader.updateRecord(installed)
        record = installed
        terminalInstalled = TerminalPluginLoader.isLoadable(context, TerminalPluginRepository(context).read())
        loaded = true
    }

    when {
        !loaded -> MochiSettingsPageScaffold(
            title = if (pluginId == TerminalPluginInfo.ID) {
                stringResource(R.string.extensions_terminal)
            } else {
                stringResource(R.string.extensions_ssh)
            },
        ) { }
        pluginId == TerminalPluginInfo.ID && terminalInstalled -> TerminalPluginPage()
        pluginId == TerminalPluginInfo.ID -> PluginUnavailablePage(
            title = stringResource(R.string.extensions_terminal),
            reason = stringResource(R.string.extensions_plugin_not_installed),
        )
        pluginId != SshPluginInfo.ID -> PluginUnavailablePage(
            title = stringResource(R.string.extensions_plugins),
            reason = stringResource(R.string.extensions_plugin_missing),
        )

        record == null -> PluginUnavailablePage(
            title = stringResource(R.string.extensions_ssh),
            reason = stringResource(R.string.extensions_plugin_not_installed),
        )

        else -> SshPluginPage(
            pluginId = pluginId,
            onUninstalled = {
                record = null
                navController.popBackStack()
            },
        )
    }
}

@Composable
private fun PluginUnavailablePage(title: String, reason: String) {
    MochiSettingsPageScaffold(title = title) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = reason,
                style = MochiTheme.textStyles.body,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
