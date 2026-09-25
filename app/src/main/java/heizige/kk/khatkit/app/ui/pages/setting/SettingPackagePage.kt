package heizige.kk.khatkit.app.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.hub.HubAccountStatus
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.khatkit.app.core.ui.icons.autoAwesome
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.cleaningServices
import heizige.kk.khatkit.app.core.ui.icons.favorite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint

/** 「KhatKit 套餐网关」在服务商列表里的固定名称与 ID（重复创建时更新同一条）。 */
private const val GATEWAY_PROVIDER_NAME = "KhatKit 套餐网关"
private val GATEWAY_PROVIDER_ID: Uuid = Uuid.parse("7e2a9c1e-6b5f-4a3d-9c8e-1f2b3c4d5e6f")

/**
 * 套餐 / 激活页：输入激活令牌调用 POST /api/activate，安全保存令牌并展示 GET /api/me 额度；
 * 同时提供「一键创建/更新网关供应商」入口（OpenAI 兼容，baseUrl = <Hub>/v1，未启用）。
 */
@Composable
fun SettingPackagePage(
    vm: SettingVM = hiltViewModel(),
) {
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var hubUrl by remember { mutableStateOf(provider.hubBaseUrl) }
    var tokenInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<HubAccountStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var gatewayMessage by remember { mutableStateOf("") }
    var gatewayMessageIsError by remember { mutableStateOf(false) }

    fun refresh(refreshQuota: Boolean = false) {
        scope.launch {
            busy = true
            status = provider.hubAccountStatus(refresh = refreshQuota)
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh(refreshQuota = false) }

    val current = status
    val activated = current?.activated == true

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = "套餐 / 激活",
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("status") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("账户状态") },
                ) {
                    item(
                        leadingContent = { Icon(favorite, null) },
                        headlineContent = {
                            val plan = status?.plan.orEmpty()
                            Text(
                                when {
                                    !activated -> "未激活"
                                    plan.isNotBlank() -> "已激活：$plan"
                                    else -> "已激活"
                                }
                            )
                        },
                        supportingContent = {
                            Text(
                                buildString {
                                    append("设备：${current?.deviceName ?: "读取中"}")
                                    current?.deviceId?.takeIf { it.isNotBlank() }?.let {
                                        append("\n设备标识：").append(it)
                                    }
                                    current?.message?.takeIf { it.isNotBlank() }?.let {
                                        append("\n").append(it)
                                    }
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("AI 令牌剩余") },
                        supportingContent = { Text(formatQuota(current?.aiTokensRemaining)) },
                    )
                    item(
                        headlineContent = { Text("工具调用剩余") },
                        supportingContent = { Text(formatQuota(current?.toolCallsRemaining)) },
                    )
                    item(
                        headlineContent = { Text("套餐到期时间") },
                        supportingContent = {
                            Text(current?.expiresAt?.let { timeFormat.format(Date(it)) } ?: "未知")
                        },
                    )
                    item(
                        onClick = { if (!busy) refresh(refreshQuota = true) },
                        leadingContent = { Icon(bolt, null) },
                        headlineContent = { Text(if (busy) "正在刷新…" else "刷新额度") },
                        supportingContent = { Text("调用 GET /api/me 拉取最新额度与到期时间") },
                    )
                    if (activated) {
                        item(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    status = provider.clearHubActivation()
                                    busy = false
                                }
                            },
                            leadingContent = { Icon(cleaningServices, null) },
                            headlineContent = { Text("清除本机激活信息") },
                            supportingContent = { Text("仅清除本机保存的令牌，不影响服务端账户") },
                        )
                    }
                }
            }

            item("activate") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("激活套餐") },
                ) {
                    item(
                        headlineContent = { Text("Hub 地址") },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                KedgeOutlinedTextField(
                                    value = hubUrl,
                                    onValueChange = { hubUrl = it },
                                    label = "Hub 地址",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "默认 ${KhatKitToolProvider.DEFAULT_HUB_BASE_URL}；修改后卡片市场也会使用新地址",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    item(
                        headlineContent = { Text("激活令牌") },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                KedgeOutlinedTextField(
                                    value = tokenInput,
                                    onValueChange = { tokenInput = it },
                                    label = "激活令牌",
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "在 KhatKitHub 购买套餐后获得的激活令牌；令牌与设备会话都会加密保存在本机",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            val result = provider.activateHub(tokenInput, hubUrl)
                                            status = result
                                            if (result.activated) tokenInput = ""
                                            busy = false
                                        }
                                    },
                                    enabled = !busy && tokenInput.isNotBlank(),
                                ) {
                                    Text(if (busy) "正在激活…" else "立即激活")
                                }
                            }
                        },
                    )
                }
            }

            item("gateway") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("AI 套餐网关") },
                ) {
                    item(
                        leadingContent = { Icon(autoAwesome, null) },
                        headlineContent = { Text(GATEWAY_PROVIDER_NAME) },
                        supportingContent = {
                            Text(
                                "网关是 OpenAI 兼容接口（<Hub>/v1）。激活后可一键创建供应商，" +
                                    "API Key 使用激活令牌；默认不启用，需要时再到「服务商」中开启并添加模型。"
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            Text(
                                gatewayMessage.ifBlank {
                                    "未激活时创建的网关不可用，请先激活套餐"
                                },
                                color = if (gatewayMessageIsError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        supportingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val outcome = upsertGatewayProvider(settings, vm, provider, hubUrl)
                                        gatewayMessage = outcome
                                        gatewayMessageIsError = !outcome.startsWith("已")
                                    },
                                    enabled = !busy,
                                ) {
                                    Text("创建 / 更新网关供应商")
                                }
                                TextButton(onClick = { refresh(refreshQuota = true) }) {
                                    Text("刷新状态")
                                }
                            }
                        },
                    )
                }
            }

            item("tip") {
                Text(
                    text = "提示：未激活时所有本地卡片照常运行；只有服务端明确拒绝（令牌无效/工具次数不足）时，" +
                        "套餐卡片才会被拦截。网络异常不会阻止卡片运行。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatQuota(value: Long?): String = value?.toString() ?: "未知"

/**
 * 创建或更新「KhatKit 套餐网关」供应商：
 * baseUrl = <hub>/v1，apiKey = 激活令牌，enabled = false（不自动启用、不自动选中）。
 * 返回中文操作结果。
 */
private fun upsertGatewayProvider(
    settings: Settings,
    vm: SettingVM,
    provider: KhatKitToolProvider,
    hubUrl: String,
): String {
    val token = provider.hubToken()
    if (token.isNullOrBlank()) return "请先激活套餐：未检测到激活令牌"
    val base = hubUrl.trim().ifBlank { KhatKitToolProvider.DEFAULT_HUB_BASE_URL }
    val baseUrl = base.trimEnd('/') + "/v1"
    val existing = settings.providers
        .filterIsInstance<ProviderSetting.OpenAI>()
        .firstOrNull { it.name == GATEWAY_PROVIDER_NAME || it.baseUrl == baseUrl }
    val updated: ProviderSetting = existing?.copy(
        name = GATEWAY_PROVIDER_NAME,
        baseUrl = baseUrl,
        apiKey = token,
        enabled = false,
    ) ?: ProviderSetting.OpenAI(
        id = GATEWAY_PROVIDER_ID,
        name = GATEWAY_PROVIDER_NAME,
        baseUrl = baseUrl,
        apiKey = token,
        enabled = false,
    )
    val providers = if (existing == null) {
        listOf(updated) + settings.providers
    } else {
        settings.providers.map { if (it.id == existing.id) updated else it }
    }
    vm.updateSettings(settings.copy(providers = providers))
    return "已创建/更新「$GATEWAY_PROVIDER_NAME」（未启用）；请到「服务商」中添加模型后手动开启"
}
