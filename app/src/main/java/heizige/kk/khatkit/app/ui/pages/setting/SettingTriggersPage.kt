package heizige.kk.khatkit.app.ui.pages.setting

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khromia.components.OptionSwitch
import heizige.kk.khatkit.app.service.KhatKitNotificationListenerService
import heizige.kk.khatkit.app.service.TriggerController
import heizige.kk.khatkit.app.service.TriggerService
import heizige.kk.khatkit.app.ui.components.nav.BackButton
import heizige.kk.khatkit.app.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.ui.components.ui.KedgePageLargeTopBar
import heizige.kk.khatkit.app.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.ui.components.ui.permission.PermissionNotification
import heizige.kk.khatkit.app.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.ui.theme.CustomColors
import heizige.kk.khatkit.app.utils.plus
import heizige.kk.khatkit.card.CardManifest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** 自动化触发器设置：总开关、事件卡片列表、通知监听/电池优化入口。 */
@Composable
fun SettingTriggersPage() {
    val controller: TriggerController = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val triggerState by controller.settings.state.collectAsStateWithLifecycle()
    val cards by controller.cards.collectAsStateWithLifecycle()

    val permissionState = rememberPermissionState(
        permissions = buildSet {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(PermissionNotification)
        },
    )
    PermissionManager(permissionState = permissionState)
    var pendingStart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        controller.refreshCardsAsync()
    }
    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (pendingStart && permissionState.allPermissionsGranted) {
            pendingStart = false
            TriggerService.start(context)
        }
    }

    fun applyMaster(enabled: Boolean) {
        scope.launch {
            controller.settings.setMasterEnabled(enabled)
            controller.syncEngine()
        }
        if (enabled) {
            controller.refreshCardsAsync()
            if (permissionState.allPermissionsGranted) {
                TriggerService.start(context)
            } else {
                pendingStart = true
                permissionState.requestPermissions()
            }
        } else {
            TriggerService.stop(context)
        }
    }

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = "自动化触发器",
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
            item("master") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text("启用自动化触发器") },
                        supportingContent = {
                            Text("卡片在定时、通知、应用启动、充电时自动运行，可逐张关闭")
                        },
                        trailingContent = {
                            OptionSwitch(
                                checked = triggerState.masterEnabled,
                                onCheckedChange = { applyMaster(it) },
                            )
                        },
                    )
                }
            }

            item("cards") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("事件卡片") },
                ) {
                    if (cards.isEmpty()) {
                        item(
                            headlineContent = { Text("暂无事件卡片") },
                            supportingContent = {
                                Text("已安装卡片声明 events 后会显示在这里，可在卡片市场更新卡片")
                            },
                        )
                    }
                    cards.forEach { card ->
                        val cardEnabled = triggerState.masterEnabled &&
                            triggerState.isCardEnabled(card.name)
                        item(
                            headlineContent = { Text(card.name) },
                            supportingContent = {
                                Text(card.events.joinToString("\n") { "· ${eventSummary(it)}" })
                            },
                            trailingContent = {
                                OptionSwitch(
                                    checked = cardEnabled,
                                    enabled = triggerState.masterEnabled,
                                    onCheckedChange = { checked ->
                                        controller.settings.setCardEnabled(card.name, checked)
                                        controller.syncEngine()
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item("permissions") {
                val listenerEnabled by produceState(
                    initialValue = KhatKitNotificationListenerService.isEnabled(context),
                ) {
                    value = KhatKitNotificationListenerService.isEnabled(context)
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("权限与保活") },
                ) {
                    item(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        leadingContent = { Icon(heizige.kk.khatkit.app.ui.icons.notifications, null) },
                        headlineContent = { Text("通知监听权限") },
                        supportingContent = {
                            Text(
                                if (listenerEnabled) "已授予，通知触发器可工作"
                                else "未授予，通知触发器无法工作，点此前往开启"
                            )
                        },
                    )
                    item(
                        onClick = {
                            val packageUri = Uri.parse("package:${context.packageName}")
                            val request = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                packageUri,
                            )
                            runCatching { context.startActivity(request) }.onFailure {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    )
                                }
                            }
                        },
                        leadingContent = { Icon(heizige.kk.khatkit.app.ui.icons.bolt, null) },
                        headlineContent = { Text("忽略电池优化") },
                        supportingContent = { Text("防止系统在后台限制触发器服务") },
                    )
                    item(
                        headlineContent = {
                            Text(
                                text = "触发器服务是常驻前台服务，会显示一条低优先级通知；关闭总开关即可停止。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun eventSummary(event: CardManifest.Event): String = when (event.type) {
    CardManifest.EVENT_SCHEDULE -> buildString {
        append("定时 ")
        when {
            event.times.isNotEmpty() -> append(event.times.joinToString("、"))
            event.intervalMinutes > 0 -> append("每 ${event.intervalMinutes} 分钟")
        }
        if (event.days.isNotEmpty()) {
            append(" ")
            append(event.days.joinToString("") { weekdayName(it) })
        } else {
            append(" 每天")
        }
    }

    CardManifest.EVENT_NOTIFICATION -> buildString {
        append("通知 ")
        append(event.packageName.ifBlank { "任意应用" })
        val conds = buildList {
            if (event.titleContains.isNotBlank()) add("标题含「${event.titleContains}」")
            if (event.textContains.isNotBlank()) add("正文含「${event.textContains}」")
        }
        if (conds.isNotEmpty()) append(" · ${conds.joinToString("，")}")
    }

    CardManifest.EVENT_APP_LAUNCH ->
        "应用启动 ${event.packageName.ifBlank { "任意应用" }}"

    CardManifest.EVENT_CHARGING ->
        if (event.state == CardManifest.CHARGING_DISCONNECTED) "充电 · 断开电源" else "充电 · 接入电源"

    else -> event.type
}

private fun weekdayName(day: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(day - 1) { "?" }
