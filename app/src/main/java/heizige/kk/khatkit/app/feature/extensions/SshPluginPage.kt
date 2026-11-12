package heizige.kk.khatkit.app.feature.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.data.ssh.SshPluginInstaller
import heizige.kk.khatkit.app.core.data.ssh.SshPluginLoader
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.feature.ui.shell.MochiSettingsPageScaffold
import heizige.kk.khatkit.ssh.api.SshEngineProvider
import heizige.kk.khatkit.ssh.api.SshPluginInfo
import heizige.kk.khatkit.ssh.api.SshRequest
import heizige.kk.khatkit.uikit.mochi.KkCard
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        title = SshPluginInfo.DISPLAY_NAME,
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
            ) { Text("卸载") }
        },
        edgeFade = false,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (engineReady && !engineAvailable) {
                item {
                    KkCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "插件引擎加载失败：插件包可能损坏或不支持当前 ABI，去扩展页重新安装。",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                KkCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "连接远程主机",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "首次连接会记住主机密钥；密钥发生变化时将拒绝连接。密码只走内存，不落盘。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("主机地址") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("端口") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("用户名") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = command,
                            onValueChange = { command = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("执行命令") },
                            singleLine = true,
                        )
                        MochiButton(
                            onClick = {
                                val parsedPort = port.toIntOrNull()
                                if (parsedPort == null) {
                                    output = "端口必须是数字"
                                    return@MochiButton
                                }
                                val engine = SshEngineProvider.engine()
                                if (engine == null) {
                                    output = "插件未就绪，先在扩展页安装远程 SSH 插件"
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
                                                "退出码：" + result.exitCode + "\n" + result.output
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
                            Text(if (running) "连接中…" else "连接并执行")
                        }
                    }
                }
            }
            output?.let { text ->
                item {
                    KkCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("输出", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = text.ifBlank { "(没有输出)" },
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
