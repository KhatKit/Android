package heizige.kk.khatkit.app.feature.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.network.WebServerManager
import heizige.kk.khatkit.app.core.network.WebServerService
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionLocalNetwork
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionNotification
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.icons.playArrow
import heizige.kk.khatkit.app.core.ui.icons.stop
import heizige.kk.khatkit.app.core.ui.icons.visibility
import heizige.kk.khatkit.app.core.ui.icons.visibilityOff
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeTextField
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.khromia.helper.Toast
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Web 服务器页的 Miuix 风格版（KernelSU 双文件架构）。
 *
 * 与 [SettingWebPage]（MD3）**选项与行为逐条一致**，只换外壳与控件：
 * - 页面骨架：[MiuixSettingsPage] + [miuixGroup]，返回按钮同款 `BackButton`
 * - 启停：MD3 是右下 FAB（`KedgeExtendedFloatingActionButton`），Miuix 换成组内的
 *   [PreferenceArrow] 动作行（与 `SettingPreferencesNetworkPageMiuix` 的「测试连接」同款），
 *   图标仍是 play/stop、加载态在 `endAction` 显示 [KedgeProgressIndicator]
 * - 端口/密码：双风格 [KedgeTextField]（含密码可见性 trailingIcon）
 * - 两个开关：[PreferenceSwitch]
 * - 地址复制行：[PreferenceArrow]（title = 地址类型、summary = 地址、点击复制）
 *
 * 权限申请与「启动后再启」的 pendingStart 逻辑与 MD3 页逐字一致（同一份 state 逻辑）。
 * 无 FAB 故底部留白用 `BottomContentPadding`（MD3 用 `BottomContentPaddingWithFab`）。
 */
@Composable
fun SettingWebPageMiuix() {
    val webServerManager: WebServerManager = rememberAppEntryPoint().webServerManager()
    val settingsStore: SettingsRepository = rememberAppEntryPoint().settingsStore()
    val settings = LocalSettings.current
    val serverState by webServerManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val copiedText = stringResource(R.string.copied)
    var portText by remember(settings.webServerPort) {
        mutableStateOf(settings.webServerPort.toString())
    }
    var accessPasswordText by remember(settings.webServerAccessPassword) {
        mutableStateOf(settings.webServerAccessPassword)
    }
    var passwordVisible by remember {
        mutableStateOf(false)
    }

    val permissionState = rememberPermissionState(
        permissions = buildSet {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionNotification)
            }
            if (Build.VERSION.SDK_INT >= 37 && !settings.webServerLocalhostOnly) {
                add(PermissionLocalNetwork)
            }
        },
    )
    PermissionManager(permissionState = permissionState)

    var pendingStart by remember { mutableStateOf(false) }

    fun startWebServer() {
        val intent = Intent(context, WebServerService::class.java).apply {
            action = WebServerService.ACTION_START
            putExtra(WebServerService.EXTRA_PORT, settings.webServerPort)
            putExtra(WebServerService.EXTRA_LOCALHOST_ONLY, settings.webServerLocalhostOnly)
        }
        context.startForegroundService(intent)
        scope.launch {
            settingsStore.update { it.copy(webServerEnabled = true) }
        }
    }

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (pendingStart && permissionState.allPermissionsGranted) {
            pendingStart = false
            startWebServer()
        }
    }

    fun copyUrl(url: String) {
        clipboardManager.setText(AnnotatedString(url))
        Toast.show(copiedText)
    }

    fun toggleWebServer() {
        if (serverState.isLoading) return
        if (!serverState.isRunning) {
            if (permissionState.allPermissionsGranted) {
                startWebServer()
            } else {
                pendingStart = true
                permissionState.requestPermissions()
            }
        } else {
            val intent = Intent(context, WebServerService::class.java).apply {
                action = WebServerService.ACTION_STOP
            }
            context.startService(intent)
            scope.launch {
                settingsStore.update { it.copy(webServerEnabled = false) }
            }
        }
    }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_web_server),
        navigationIcon = { BackButton() },
        bottomInnerPadding = PageMetrics.BottomContentPadding,
    ) {
        miuixGroup {
            PreferenceArrow(
                title = stringResource(
                    if (serverState.isRunning) R.string.setting_page_web_server_stop
                    else R.string.setting_page_web_server_start
                ),
                summary = if (serverState.isRunning) "http://localhost:${serverState.port}" else null,
                icon = if (serverState.isRunning) stop else playArrow,
                enabled = !serverState.isLoading,
                onClick = { toggleWebServer() },
                endAction = {
                    if (serverState.isLoading) {
                        KedgeProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
            )
        }

        miuixGroup {
            KedgeTextField(
                value = portText,
                onValueChange = { value ->
                    portText = value.filter { it.isDigit() }
                    val port = portText.toIntOrNull()
                    if (port != null && port in 1024..65535) {
                        scope.launch {
                            settingsStore.update { it.copy(webServerPort = port) }
                        }
                    }
                },
                label = stringResource(R.string.setting_page_web_server_port),
                supportingText = stringResource(R.string.setting_page_web_server_port_desc),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = portText.toIntOrNull()?.let { it !in 1024..65535 } ?: true,
                enabled = !serverState.isRunning,
                modifier = Modifier.fillMaxWidth(),
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_page_web_server_localhost_only),
                summary = stringResource(R.string.setting_page_web_server_localhost_only_desc),
                checked = settings.webServerLocalhostOnly,
                onCheckedChange = { checked ->
                    scope.launch {
                        settingsStore.update {
                            it.copy(webServerLocalhostOnly = checked)
                        }
                    }
                },
                enabled = !serverState.isRunning,
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_page_web_server_jwt_enable),
                checked = settings.webServerJwtEnabled,
                onCheckedChange = { checked ->
                    scope.launch {
                        settingsStore.update {
                            it.copy(webServerJwtEnabled = checked)
                        }
                    }
                },
                enabled = settings.webServerJwtEnabled || accessPasswordText.isNotBlank(),
            )
            KedgeTextField(
                value = accessPasswordText,
                onValueChange = { value ->
                    accessPasswordText = value
                    scope.launch {
                        settingsStore.update {
                            it.copy(
                                webServerAccessPassword = value,
                                webServerJwtEnabled = it.webServerJwtEnabled && value.isNotBlank()
                            )
                        }
                    }
                },
                label = stringResource(R.string.setting_page_web_server_password),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    KedgeIconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) visibilityOff else visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                isError = settings.webServerJwtEnabled && accessPasswordText.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (serverState.isRunning) {
            val port = serverState.port
            val hostname = serverState.hostname
            miuixGroup {
                if (!serverState.localhostOnly) {
                    val lanUrl = "http://${serverState.address ?: "localhost"}:$port"
                    PreferenceArrow(
                        title = stringResource(R.string.setting_page_web_server_lan_address),
                        summary = lanUrl,
                        onClick = { copyUrl(lanUrl) },
                    )

                    if (hostname != null) {
                        val mdnsUrl = "http://$hostname:$port"
                        PreferenceArrow(
                            title = stringResource(R.string.setting_page_web_server_mdns_address),
                            summary = mdnsUrl,
                            onClick = { copyUrl(mdnsUrl) },
                        )
                    }
                }

                val localUrl = "http://localhost:$port"
                PreferenceArrow(
                    title = stringResource(R.string.setting_page_web_server_local_address),
                    summary = localUrl,
                    onClick = { copyUrl(localUrl) },
                )

                // 卡片作为 MCP tool 的入口，Claude Desktop / Cursor 用这个地址
                val mcpUrl = if (!serverState.localhostOnly) {
                    "http://${serverState.address ?: "localhost"}:$port/mcp"
                } else {
                    "http://localhost:$port/mcp"
                }
                PreferenceArrow(
                    title = "MCP 地址（卡片作为 MCP tool）",
                    summary = mcpUrl,
                    onClick = { copyUrl(mcpUrl) },
                )
            }
        }

        miuixGroup {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.setting_page_web_server_address_note),
                    style = KedgeTextStyles.title(),
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.setting_page_web_server_address_note_desc),
                    style = KedgeTextStyles.body(),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }

        val serverError = serverState.error
        if (serverError != null) {
            miuixGroup {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_page_web_server_error),
                        style = KedgeTextStyles.title(),
                        color = MiuixTheme.colorScheme.error,
                    )
                    Text(
                        text = serverError,
                        style = KedgeTextStyles.body(),
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
