package heizige.kk.khatkit.app.feature.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import heizige.kk.khatkit.app.core.ui.icons.edit
import heizige.kk.khatkit.app.core.ui.icons.terminal
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.card.CardStrings
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.feature.ui.shell.MochiSettingsPageScaffold
import heizige.kk.khatkit.ssh.api.SshPluginInfo

@Composable
fun ExtensionsPage(vm: ExtensionsViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val sshPlugin by vm.sshPlugin.collectAsStateWithLifecycle()
    val terminalPlugin by vm.terminalPlugin.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val terminalShortcutEnabled by vm.terminalShortcutEnabled.collectAsStateWithLifecycle()
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
                        trailingContent = {
                            ExtensionEditButton("编辑快捷消息") {
                                navController.navigate(Screen.QuickMessages)
                            }
                        },
                        chevron = true,
                        leadingContent = { Icon(bolt, null) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_quick_messages)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_quick_messages_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Prompts) },
                        trailingContent = {
                            ExtensionEditButton("编辑提示词") { navController.navigate(Screen.Prompts) }
                        },
                        chevron = true,
                        leadingContent = { Icon(book4, null) },
                        headlineContent = { Text(stringResource(R.string.extensions_page_prompts)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_prompts_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Skills) },
                        trailingContent = {
                            ExtensionEditButton("编辑技能") { navController.navigate(Screen.Skills) }
                        },
                        chevron = true,
                        leadingContent = { Icon(extension, null) },
                        headlineContent = { Text(stringResource(R.string.extensions_page_agent_skills)) },
                        supportingContent = { Text(stringResource(R.string.extensions_page_agent_skills_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Workspaces) },
                        trailingContent = {
                            ExtensionEditButton("编辑工作区") { navController.navigate(Screen.Workspaces) }
                        },
                        chevron = true,
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
                    item(
                        onClick = {
                            if (terminalPlugin != null) navController.navigate(Screen.Plugin(TerminalPluginInfo.ID))
                            else if (!busy) vm.installTerminal()
                        },
                        chevron = terminalPlugin != null,
                        trailingContent = terminalPlugin?.let {
                            {
                                ExtensionEditButton("编辑终端插件") {
                                    navController.navigate(Screen.Plugin(TerminalPluginInfo.ID))
                                }
                            }
                        },
                        leadingContent = { Icon(terminal, null) },
                        headlineContent = { Text("终端") },
                        supportingContent = {
                            Text(if (terminalPlugin != null) "已安装 ${terminalPlugin?.version} · ${TerminalPluginInfo.DESCRIPTION}" else "从服务器下载安装 · ${TerminalPluginInfo.DESCRIPTION}")
                        },
                    )
                    if (terminalPlugin != null) {
                        item(
                            onClick = null,
                            leadingContent = { Icon(terminal, null) },
                            headlineContent = { Text("欢迎页快捷方式") },
                            supportingContent = { Text("在聊天欢迎页放置一个打开终端的快捷方式") },
                            trailingContent = {
                                KkSwitch(
                                    checked = terminalShortcutEnabled,
                                    onCheckedChange = vm::setTerminalShortcutEnabled,
                                )
                            },
                        )
                    }
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
                        trailingContent = installedVersion?.let {
                            {
                                ExtensionEditButton("编辑 SSH 插件") {
                                    navController.navigate(Screen.Plugin(SshPluginInfo.ID))
                                }
                            }
                        },
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
                    )
                }
            }
            if (cards.isNotEmpty()) {
                item {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = { Text("卡片功能") },
                    ) {
                        cards.forEach { card ->
                            val strings = CardStrings(card.manifest, listOf("zh-CN", "en"))
                            val title = strings.name
                                .ifBlank { card.manifest.name }
                            item(
                                onClick = { vm.runCard(card.manifest.name) },
                                trailingContent = {
                                    ExtensionEditButton("编辑 $title") {
                                        navController.navigate(Screen.ExploreCardDetail(card.manifest.name))
                                    }
                                },
                                leadingContent = { Icon(extension, null) },
                                headlineContent = { Text(title) },
                                supportingContent = {
                                    Text(
                                        strings.description.ifBlank { "运行已安装卡片" },
                                        maxLines = 1,
                                    )
                                },
                                chevron = true,
                            )
                        }
                    }
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

@Composable
private fun ExtensionEditButton(
    label: String,
    onClick: () -> Unit,
) {
    MochiIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(edit, contentDescription = label)
    }
}
