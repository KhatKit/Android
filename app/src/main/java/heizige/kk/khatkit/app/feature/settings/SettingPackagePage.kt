package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalUriHandler
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
import heizige.kk.khatkit.app.core.ui.icons.openInNew
import heizige.kk.khatkit.app.Screen
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
 * 套餐 / 激活页：打开 WebView 跳转到购买页面
 */
@Composable
fun SettingPackagePage(
    vm: SettingViewModel = hiltViewModel(),
) {
    val uriHandler = LocalUriHandler.current
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var status by remember { mutableStateOf<HubAccountStatus?>(null) }
    var busy by remember { mutableStateOf(false) }

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
                title = "套餐激活",
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
            item("purchase") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("购买套餐") },
                ) {
                    item(
                        onClick = {
                            uriHandler.openUri("https://heizige.top/khatkit/index.html")
                        },
                        leadingContent = { Icon(openInNew, null) },
                        headlineContent = { Text("打开 KhatKit 套餐商城") },
                        supportingContent = { Text("在浏览器中查看套餐详情、购买并获取激活令牌") },
                    )
                }
            }

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

            item("tip") {
                Text(
                    text = "提示：购买套餐后将获得激活令牌，使用令牌可在网页端激活设备。" +
                        "未激活时所有本地卡片照常运行；只有服务端明确拒绝（令牌无效/工具次数不足）时，" +
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
