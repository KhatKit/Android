package heizige.kk.khatkit.app.feature.settings

import android.content.Intent
import android.os.Build

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import heizige.kk.khatkit.app.core.ui.components.ui.SwitchSetting
import heizige.kk.khatkit.app.core.ui.components.ui.settingItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeTextField
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.network.WebServerService
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionLocalNetwork
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionNotification
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.network.WebServerManager
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.icons.playArrow
import heizige.kk.khatkit.app.core.ui.icons.stop
import heizige.kk.khatkit.app.core.ui.icons.visibility
import heizige.kk.khatkit.app.core.ui.icons.visibilityOff
import heizige.kk.kedge.components.KedgeExtendedFloatingActionButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun SettingWebPage() {
    val webServerManager: WebServerManager = rememberAppEntryPoint().webServerManager()
    val settingsStore: SettingsRepository = rememberAppEntryPoint().settingsStore()
    val settings = LocalSettings.current
    val serverState by webServerManager.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val toaster = LocalToaster.current
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

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.setting_page_web_server),
        scrollBehavior = scrollBehavior,
        floatingActionButton = {
            KedgeExtendedFloatingActionButton(
                onClick = {
                    if (serverState.isLoading) return@KedgeExtendedFloatingActionButton
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
                },
                icon = {
                    if (serverState.isLoading) {
                        KedgeProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (serverState.isRunning) stop else playArrow,
                            contentDescription = null,
                        )
                    }
                },
                text = {
                    Text(
                        if (serverState.isRunning) {
                            stringResource(R.string.setting_page_web_server_stop)
                        } else {
                            stringResource(R.string.setting_page_web_server_start)
                        }
                    )
                },
                containerColor = if (serverState.isRunning) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                // 本页有 FAB（KedgeExtendedFloatingActionButton），底部按 FAB 的
                // 高度让位，否则滚到底时末项被按钮压住。
                bottom = 8.dp + PageMetrics.BottomContentPaddingWithFab,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_page_web_server_port)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_web_server_port_desc)) },
                        trailingContent = {
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = portText.toIntOrNull()?.let { it !in 1024..65535 } ?: true,
                                modifier = Modifier.width(100.dp),
                                enabled = !serverState.isRunning,
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )
                        },
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_page_web_server_localhost_only,
                            R.string.setting_page_web_server_localhost_only_desc,
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
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_page_web_server_jwt_enable,
                            R.string.setting_page_web_server_jwt_enable_desc,
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
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_page_web_server_password)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_web_server_password_desc)) },
                        trailingContent = {
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = if (passwordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    KedgeIconButton(onClick = { passwordVisible = !passwordVisible }, shapes = IconButtonDefaults.shapes()) {
                                        Icon(
                                            imageVector = if (passwordVisible) visibilityOff else visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                singleLine = true,
                                isError = settings.webServerJwtEnabled && accessPasswordText.isBlank(),
                                modifier = Modifier.width(180.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )
                        },
                    )
                    if (serverState.isRunning) {
                        val port = serverState.port
                        if (!serverState.localhostOnly) {
                            val lanUrl = "http://${serverState.address ?: "localhost"}:$port"
                            item(
                                onClick = { copyUrl(lanUrl) },
                                headlineContent = { Text(stringResource(R.string.setting_page_web_server_lan_address)) },
                                supportingContent = { Text(lanUrl) },
                            )

                            if (serverState.hostname != null) {
                                val mdnsUrl = "http://${serverState.hostname}:$port"
                                item(
                                    onClick = { copyUrl(mdnsUrl) },
                                    headlineContent = { Text(stringResource(R.string.setting_page_web_server_mdns_address)) },
                                    supportingContent = { Text(mdnsUrl) },
                                )
                            }
                        }

                        val localUrl = "http://localhost:$port"
                        item(
                            onClick = { copyUrl(localUrl) },
                            headlineContent = { Text(stringResource(R.string.setting_page_web_server_local_address)) },
                            supportingContent = { Text(localUrl) },
                        )

                        // 卡片作为 MCP tool 的入口，Claude Desktop / Cursor 用这个地址
                        val mcpUrl = if (!serverState.localhostOnly) {
                            "http://${serverState.address ?: "localhost"}:$port/mcp"
                        } else {
                            "http://localhost:$port/mcp"
                        }
                        item(
                            onClick = { copyUrl(mcpUrl) },
                            headlineContent = { Text("MCP 地址（卡片作为 MCP tool）") },
                            supportingContent = { Text(mcpUrl) },
                        )
                    }
                    item(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_page_web_server_address_note),
                                style = KedgeTextStyles.title(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_page_web_server_address_note_desc),
                                style = KedgeTextStyles.body(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    if (serverState.error != null) {
                        item(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.setting_page_web_server_error),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = serverState.error ?: "",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
