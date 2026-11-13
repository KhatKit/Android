package heizige.kk.khatkit.app.feature.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.book4
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.folder
import heizige.kk.khatkit.app.core.ui.icons.terminal
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.feature.ui.shell.MochiSettingsPageScaffold
import heizige.kk.khatkit.ssh.api.SshPluginInfo
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import ui.feice.mochi.list.MochiListSection

@Composable
fun ExtensionsPage(vm: ExtensionsViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val sshPlugin by vm.sshPlugin.collectAsStateWithLifecycle()
    val terminalPlugin by vm.terminalPlugin.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    // 从插件页返回时重读安装状态：插件页里可以卸载，回到本页要立刻变回「安装」。
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    MochiSettingsPageScaffold(
        title = stringResource(R.string.extensions_page_title),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                MochiListSection(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    header = stringResource(R.string.extensions_page_section_extensions),
                ) {
                    MochiOptionItem(
                        onClick = { navController.navigate(Screen.QuickMessages) },
                        leadingContent = { Icon(bolt, null) },
                        titleContent = { Text(stringResource(R.string.assistant_page_quick_messages)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_quick_messages_desc)) },
                        chevron = true,
                    )
                    MochiOptionItem(
                        onClick = { navController.navigate(Screen.Prompts) },
                        leadingContent = { Icon(book4, null) },
                        titleContent = { Text(stringResource(R.string.extensions_page_prompts)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_prompts_desc)) },
                        chevron = true,
                    )
                    MochiOptionItem(
                        onClick = { navController.navigate(Screen.Skills) },
                        leadingContent = { Icon(extension, null) },
                        titleContent = { Text(stringResource(R.string.extensions_page_agent_skills)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_agent_skills_desc)) },
                        chevron = true,
                    )
                    MochiOptionItem(
                        onClick = { navController.navigate(Screen.Workspaces) },
                        leadingContent = { Icon(folder, null) },
                        titleContent = { Text(stringResource(R.string.extensions_page_workspace)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_workspace_desc)) },
                        chevron = true,
                    )
                }
            }
            item {
                // 插件和卡片是两套东西：插件从 Hub 下载原生包（kind=dependency），装好才有入口。
                MochiListSection(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    header = "插件",
                ) {
                    MochiOptionItem(
                        onClick = {
                            if (terminalPlugin != null) navController.navigate(Screen.Plugin(TerminalPluginInfo.ID))
                            else if (!busy) vm.installTerminal()
                        },
                        chevron = terminalPlugin != null,
                        leadingContent = { Icon(terminal, null) },
                        titleContent = { Text("终端") },
                        supportingContent = {
                            Text(if (terminalPlugin != null) "已安装 ${terminalPlugin?.version} · ${TerminalPluginInfo.DESCRIPTION}" else "从服务器下载安装 · ${TerminalPluginInfo.DESCRIPTION}")
                        },
                    )
                    val installedVersion = sshPlugin?.version
                    MochiOptionItem(
                        onClick = {
                            if (installedVersion != null) {
                                navController.navigate(Screen.Plugin(SshPluginInfo.ID))
                            } else if (!busy) {
                                vm.install()
                            }
                        },
                        chevron = installedVersion != null,
                        leadingContent = { Icon(extension, null) },
                        titleContent = { Text(SshPluginInfo.DISPLAY_NAME) },
                        supportingContent = {
                            Text(
                                if (installedVersion != null) {
                                    "已安装 " + installedVersion + " · " + SshPluginInfo.DESCRIPTION
                                } else {
                                    "从服务器下载安装 · " + SshPluginInfo.DESCRIPTION
                                },
                            )
                        },
                    )
                }
            }
            message?.let { text ->
                item {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = ui.feice.mochi.theme.MochiTheme.textStyles.footnote,
                        color = ui.feice.mochi.theme.MochiTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
