package heizige.kk.khatkit.app.feature.extensions

import androidx.compose.ui.platform.LocalResources
import heizige.kk.khatkit.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.data.ssh.SshPluginInstaller
import heizige.kk.khatkit.app.core.data.ssh.SshPluginLoader
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.ui.shell.MochiSettingsPageScaffold
import heizige.kk.khatkit.ssh.api.SshEngineProvider
import heizige.kk.khatkit.ssh.api.SshPluginInfo
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.ssh.api.SshRequest
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.list.MochiListSection

/**
 * 远程 SSH 插件的宿主 UI。
 *
 * 页面本身不含 sshj：引擎由 [SshEngineProvider] 从已安装的插件包（DexClassLoader）里解析，
 * 页面只负责收集参数、展示输出。未安装时 [PluginHostPage] 已经拦掉，这里只兜加载失败。
 */
@Composable
fun SshPluginPage(
    pluginId: String = SshPluginInfo.ID,
    onUninstalled: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var engineReady by remember { mutableStateOf(false) }
    var engineAvailable by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("uname -a") }
    var output by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SshPluginLoader.prepare(context)
        engineAvailable = SshEngineProvider.engine() != null
        engineReady = true
    }

    // 用设置页骨架（顶栏 + 受约束的正文槽）：MochiPageScaffold 的大标题分支会把正文放进可滚容器，
    // 正文里的 LazyColumn 在无限高约束下会崩（IllegalStateException: infinity max height）。
    MochiSettingsPageScaffold(
        title = SshPluginInfo.displayName(context),
        navigationIcon = { BackButton() },
        actions = {
            MochiTextButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { SshPluginInstaller.uninstall(context) }
                        SshPluginLoader.updateRecord(null)
                        onUninstalled()
                    }
                },
            ) { Text(stringResource(R.string.explore_market_uninstall)) }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (engineReady && !engineAvailable) {
                item {
                    MochiListSection(
                        modifier = Modifier.fillMaxWidth(),
                        header = stringResource(R.string.extensions_ssh_plugin_status),
                    ) {
                        Text(
                            text = stringResource(R.string.extensions_ssh_engine_load_failed),
                            modifier = Modifier.padding(16.dp),
                            style = MochiTheme.textStyles.body,
                        )
                    }
                }
            }
            item {
                MochiListSection(
                    modifier = Modifier.fillMaxWidth(),
                    header = stringResource(R.string.extensions_ssh_connect_host),
                    footer = stringResource(R.string.extensions_ssh_connect_footer),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MochiTextField(
                            value = host,
                            onValueChange = { host = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.extensions_ssh_host),
                            singleLine = true,
                        )
                        MochiTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.extensions_ssh_port),
                            singleLine = true,
                        )
                        MochiTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.extensions_ssh_username),
                            singleLine = true,
                        )
                        MochiTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.extensions_ssh_password),
                            secure = true,
                            singleLine = true,
                        )
                        MochiTextField(
                            value = command,
                            onValueChange = { command = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.extensions_ssh_command),
                            singleLine = true,
                        )
                        MochiButton(
                            onClick = {
                                val parsedPort = port.toIntOrNull()
                                if (parsedPort == null) {
                                    output = resources.getString(R.string.extensions_ssh_port_invalid)
                                    return@MochiButton
                                }
                                val engine = SshEngineProvider.engine()
                                if (engine == null) {
                                    output = resources.getString(R.string.extensions_ssh_plugin_not_ready)
                                    return@MochiButton
                                }
                                running = true
                                output = null
                                scope.launch {
                                    output = withContext(Dispatchers.IO) {
                                        runCatching {
                                            engine.execute(
                                                context = context,
                                                request = SshRequest(
                                                    host = host.trim(),
                                                    port = parsedPort,
                                                    username = username.trim(),
                                                    password = password,
                                                    command = command.trim(),
                                                ),
                                            ).let { result ->
                                                resources.getString(R.string.extensions_ssh_exit_code, result.exitCode.toString(), result.output)
                                            }
                                        }.getOrElse { error ->
                                            error.message ?: error.javaClass.simpleName
                                        }
                                    }
                                    running = false
                                }
                            },
                            enabled = !running && engineAvailable,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (running) stringResource(R.string.extensions_ssh_connecting) else stringResource(R.string.extensions_ssh_connect_run))
                        }
                    }
                }
            }
            output?.let { text ->
                item {
                    MochiListSection(
                        modifier = Modifier.fillMaxWidth(),
                        header = stringResource(R.string.extensions_output),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = text.ifBlank { stringResource(R.string.extensions_no_output) },
                                modifier = Modifier.padding(top = 8.dp),
                                style = MochiTheme.textStyles.body,
                            )
                        }
                    }
                }
            }
        }
    }
}
