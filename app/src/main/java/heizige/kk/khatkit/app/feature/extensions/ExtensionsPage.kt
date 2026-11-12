package heizige.kk.khatkit.app.feature.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.book4
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.folder
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.feature.ui.shell.MochiSettingsPageScaffold
import heizige.kk.khatkit.ssh.api.SshPluginInfo
import heizige.kk.khatkit.uikit.mochi.MochiTextButton

@Composable
fun ExtensionsPage(vm: ExtensionsViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val sshPlugin by vm.sshPlugin.collectAsStateWithLifecycle()
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
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.extensions_page_section_extensions)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.QuickMessages) },
                        leadingContent = { Icon(bolt, null) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_quick_messages)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_quick_messages_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Prompts) },
                        leadingContent = { Icon(book4, null) },
                        headlineContent = { Text(stringResource(R.string.extensions_page_prompts)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_prompts_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Skills) },
                        leadingContent = { Icon(extension, null) },
                        headlineContent = { Text(stringResource(R.string.extensions_page_agent_skills)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_agent_skills_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Workspaces) },
                        leadingContent = { Icon(folder, null) },
                        headlineContent = { Text(stringResource(R.string.extensions_page_workspace)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_workspace_desc)) },
                    )
                }
            }
            item {
                // 插件和卡片是两套东西：插件从 Hub 下载原生包（kind=dependency），装好才有入口。
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("插件") },
                ) {
                    val installedVersion = sshPlugin?.version
                    item(
                        onClick = {
                            if (installedVersion != null) {
                                navController.navigate(Screen.Plugin(SshPluginInfo.ID))
                            } else if (!busy) {
                                vm.install()
                            }
                        },
                        chevron = installedVersion != null,
                        leadingContent = { Icon(extension, null) },
                        headlineContent = { Text(SshPluginInfo.DISPLAY_NAME) },
                        supportingContent = {
                            Text(
                                if (installedVersion != null) {
                                    "已安装 " + installedVersion + " · " + SshPluginInfo.DESCRIPTION
                                } else {
                                    "从服务器下载安装 · " + SshPluginInfo.DESCRIPTION
                                },
                            )
                        },
                        trailingContent = {
                            MochiTextButton(
                                onClick = {
                                    if (installedVersion != null) {
                                        navController.navigate(Screen.Plugin(SshPluginInfo.ID))
                                    } else {
                                        vm.install()
                                    }
                                },
                                enabled = !busy,
                            ) { Text(if (busy) "安装中…" else if (installedVersion != null) "进入" else "安装") }
                        },
                    )
                }
            }
            message?.let { text ->
                item {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
