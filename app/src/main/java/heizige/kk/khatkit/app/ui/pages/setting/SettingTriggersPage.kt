package heizige.kk.khatkit.app.ui.pages.setting

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
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
import heizige.kk.khatkit.app.service.CardTriggerOverride
import heizige.kk.khatkit.app.service.KhatKitNotificationListenerService
import heizige.kk.khatkit.app.service.TriggerController
import heizige.kk.khatkit.app.service.TriggerExactAlarmScheduler
import heizige.kk.khatkit.app.service.TriggerService
import heizige.kk.khatkit.app.service.TriggerSettings
import heizige.kk.khatkit.app.service.computeTriggerLogStats
import heizige.kk.khatkit.app.ui.components.nav.BackButton
import heizige.kk.khatkit.app.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.ui.components.ui.KedgePageLargeTopBar
import heizige.kk.khatkit.app.ui.components.ui.permission.PermissionInfo
import heizige.kk.khatkit.app.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.ui.components.ui.permission.PermissionNotification
import heizige.kk.khatkit.app.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.ui.icons.bolt
import heizige.kk.khatkit.app.ui.icons.cleaningServices
import heizige.kk.khatkit.app.ui.icons.editNote
import heizige.kk.khatkit.app.ui.icons.notifications
import heizige.kk.khatkit.app.ui.theme.CustomColors
import heizige.kk.khatkit.app.utils.plus
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.trigger.TriggerCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val PermissionFineLocation = PermissionInfo(
    permission = Manifest.permission.ACCESS_FINE_LOCATION,
    displayName = { Text("位置权限（可选）") },
    usage = { Text("位置触发器需要定位权限；不授权时位置事件会被跳过") },
    required = false,
)

private val PermissionBluetoothConnect = PermissionInfo(
    permission = Manifest.permission.BLUETOOTH_CONNECT,
    displayName = { Text("蓝牙权限（可选）") },
    usage = { Text("蓝牙触发器需要连接权限；不授权时蓝牙事件会被跳过") },
    required = false,
)

/** 自动化触发器设置：总开关、事件卡片编辑、执行日志、通知监听/电池优化入口。 */
@Composable
fun SettingTriggersPage() {
    val controller: TriggerController = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val triggerState by controller.settings.state.collectAsStateWithLifecycle()
    val cards by controller.cards.collectAsStateWithLifecycle()
    val logEntries by controller.logs.entries.collectAsStateWithLifecycle()

    val permissionState = rememberPermissionState(
        permissions = buildSet {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(PermissionNotification)
            add(PermissionFineLocation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(PermissionBluetoothConnect)
        },
    )
    PermissionManager(permissionState = permissionState)
    var pendingStart by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<TriggerCard?>(null) }
    val editingOverride = remember(editingCard) {
        editingCard?.let { controller.settings.overrideFor(it.name) } ?: CardTriggerOverride.NONE
    }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val logStats = remember(logEntries) { computeTriggerLogStats(logEntries) }
    val exactAlarmAllowed = runCatching {
        TriggerExactAlarmScheduler.canScheduleExact(context)
    }.getOrDefault(true)

    LaunchedEffect(Unit) {
        controller.refreshCardsAsync()
    }
    LaunchedEffect(permissionState.allRequiredPermissionsGranted) {
        if (pendingStart && permissionState.allRequiredPermissionsGranted) {
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
            if (permissionState.allRequiredPermissionsGranted) {
                TriggerService.start(context)
            } else {
                pendingStart = true
                permissionState.requestPermissions()
            }
        } else {
            TriggerService.stop(context)
        }
    }

    editingCard?.let { card ->
        TriggerCardEditorSheet(
            card = card,
            override = editingOverride,
            onSave = { override ->
                controller.saveOverride(card.name, override)
                editingCard = null
            },
            onDismiss = { editingCard = null },
        )
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
                            Text("卡片在定时、通知/点击/回复、应用启动/退出/安装/卸载、充电、Wi-Fi、网络、电量、屏幕、剪贴板、蓝牙、位置、快捷方式、磁贴事件时自动运行")
                        },
                        trailingContent = {
                            OptionSwitch(
                                checked = triggerState.masterEnabled,
                                onCheckedChange = { applyMaster(it) },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("运行并发上限") },
                        supportingContent = { Text("触发任务按 FIFO 排队；默认 1（串行），可调至 3") },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (TriggerSettings.MIN_MAX_PARALLEL..TriggerSettings.MAX_MAX_PARALLEL).forEach { value ->
                                    FilterChip(
                                        selected = triggerState.maxParallel == value,
                                        onClick = { controller.setMaxParallel(value) },
                                        label = { Text("$value") },
                                    )
                                }
                            }
                        },
                    )
                }
            }

            item("cards") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("事件卡片（点击编辑事件）") },
                ) {
                    if (cards.isEmpty()) {
                        item(
                            headlineContent = { Text("暂无已安装卡片") },
                            supportingContent = {
                                Text("在卡片市场安装卡片后，可在这里给它们配置事件触发器")
                            },
                        )
                    }
                    cards.forEach { card ->
                        val cardEnabled = triggerState.masterEnabled &&
                            triggerState.isCardEnabled(card.name)
                        item(
                            onClick = { editingCard = card },
                            leadingContent = { Icon(editNote, contentDescription = null) },
                            headlineContent = { Text(card.name) },
                            supportingContent = {
                                Text(
                                    if (card.events.isEmpty()) {
                                        "未配置事件，点击添加"
                                    } else {
                                        card.events.mapIndexed { index, event ->
                                            "· ${eventSummary(event)}" +
                                                if (index in card.disabledIndexes) "（已停用）" else ""
                                        }.joinToString("\n")
                                    }
                                )
                            },
                            trailingContent = {
                                OptionSwitch(
                                    checked = cardEnabled,
                                    enabled = triggerState.masterEnabled,
                                    onCheckedChange = { checked ->
                                        controller.settings.setCardEnabled(card.name, checked)
                                        controller.syncEngine()
                                        controller.syncExternalBindingsAsync()
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item("stats") {
                if (logEntries.isNotEmpty()) {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = { Text("运行统计（最近 ${logEntries.size} 条记录）") },
                    ) {
                        item(
                            headlineContent = {
                                Text("今日运行 ${logStats.todayRuns} 次 · 成功率 ${logStats.successRate}%")
                            },
                            supportingContent = {
                                Text(
                                    buildString {
                                        if (logStats.topCards.isNotEmpty()) {
                                            append("最常触发：")
                                            append(
                                                logStats.topCards.joinToString("、") {
                                                    "${it.first}（${it.second}）"
                                                }
                                            )
                                        }
                                        if (logStats.typeCounts.isNotEmpty()) {
                                            if (isNotEmpty()) append("\n")
                                            append("类型分布：")
                                            append(
                                                logStats.typeCounts.joinToString("、") {
                                                    "${eventTypeLabel(it.first)} ${it.second}"
                                                }
                                            )
                                        }
                                    }
                                )
                            },
                        )
                    }
                }
            }

            item("logs") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("执行日志（最近 ${minOf(logEntries.size, LOG_UI_LIMIT)} / ${logEntries.size} 条）") },
                ) {
                    if (logEntries.isEmpty()) {
                        item(
                            headlineContent = { Text("暂无执行记录") },
                            supportingContent = { Text("卡片被事件触发后会在这里留下运行结果") },
                        )
                    } else {
                        item(
                            onClick = { controller.logs.clear() },
                            leadingContent = { Icon(cleaningServices, contentDescription = null) },
                            headlineContent = { Text("清空日志") },
                            supportingContent = { Text("仅清除本地运行记录，不影响卡片配置") },
                        )
                        logEntries.asReversed().take(LOG_UI_LIMIT).forEach { entry ->
                            item(
                                headlineContent = {
                                    Text(
                                        "${if (entry.ok) "成功" else "失败"} · ${entry.card}",
                                        color = if (entry.ok) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        buildString {
                                            append(timeFormat.format(Date(entry.at)))
                                            append(" · ")
                                            append(eventTypeLabel(entry.type))
                                            if (entry.payload.isNotBlank()) {
                                                append(" · ")
                                                append(entry.payload)
                                            }
                                            if (entry.message.isNotBlank()) {
                                                append("\n")
                                                append(entry.message)
                                            }
                                        }
                                    )
                                },
                            )
                        }
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
                        leadingContent = { Icon(notifications, null) },
                        headlineContent = { Text("通知监听权限") },
                        supportingContent = {
                            Text(
                                if (listenerEnabled) "已授予，通知/点击/直接回复触发器可工作"
                                else "未授予，通知/点击/直接回复触发器无法工作，点此前往开启"
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
                        leadingContent = { Icon(bolt, null) },
                        headlineContent = { Text("忽略电池优化") },
                        supportingContent = { Text("防止系统在后台限制触发器服务") },
                    )
                    item(
                        onClick = { permissionState.requestPermissions() },
                        headlineContent = { Text("请求位置 / 蓝牙等运行时权限") },
                        supportingContent = {
                            Text("位置与蓝牙触发器需要额外权限；未授权时对应事件自动跳过")
                        },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmAllowed) {
                        item(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            leadingContent = { Icon(bolt, null) },
                            headlineContent = { Text("精确闹钟权限（未授予）") },
                            supportingContent = {
                                Text("分钟级定时需要「闹钟和提醒」权限；未授予时退回每 60 秒轮询")
                            },
                        )
                    }
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

private const val LOG_UI_LIMIT = 50

internal fun eventSummary(event: CardManifest.Event): String = when (event.type) {
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
        when (event.calendar) {
            CardManifest.CALENDAR_WORKDAY -> append(" · 仅工作日")
            CardManifest.CALENDAR_WEEKEND -> append(" · 仅休息日")
            CardManifest.CALENDAR_HOLIDAY -> append(" · 仅节假日")
        }
    }

    CardManifest.EVENT_NOTIFICATION,
    CardManifest.EVENT_NOTIFICATION_CLICK,
    CardManifest.EVENT_NOTIFICATION_REPLY -> buildString {
        append(
            when (event.type) {
                CardManifest.EVENT_NOTIFICATION_CLICK -> "通知点击 "
                CardManifest.EVENT_NOTIFICATION_REPLY -> "通知回复 "
                else -> "通知 "
            }
        )
        append(event.packageName.ifBlank { "任意应用" })
        val conds = buildList {
            if (event.titleContains.isNotBlank()) add("标题含「${event.titleContains}」")
            if (event.textContains.isNotBlank()) add("正文含「${event.textContains}」")
        }
        if (conds.isNotEmpty()) append(" · ${conds.joinToString("，")}")
    }

    CardManifest.EVENT_APP_LAUNCH ->
        "应用启动 ${event.packageName.ifBlank { "任意应用" }}"

    CardManifest.EVENT_APP_EXIT ->
        "应用退出 ${event.packageName.ifBlank { "（未填包名）" }}"

    CardManifest.EVENT_APP_INSTALL ->
        "应用安装 ${event.packageName.ifBlank { "任意应用" }}"

    CardManifest.EVENT_APP_UNINSTALL ->
        "应用卸载 ${event.packageName.ifBlank { "任意应用" }}"

    CardManifest.EVENT_SHORTCUT ->
        "桌面快捷方式「${event.name.ifBlank { "未命名" }}」"

    CardManifest.EVENT_TILE ->
        "快捷设置磁贴「${event.name.ifBlank { "未命名" }}」"

    CardManifest.EVENT_CHARGING ->
        if (event.state == CardManifest.CHARGING_DISCONNECTED) "充电 · 断开电源" else "充电 · 接入电源"

    CardManifest.EVENT_WIFI -> buildString {
        append("Wi-Fi ")
        append(event.ssid.ifBlank { "任意热点" })
        when (event.state) {
            CardManifest.STATE_CONNECTED -> append(" · 已连接")
            CardManifest.STATE_DISCONNECTED -> append(" · 已断开")
            else -> append(" · 任意状态")
        }
    }

    CardManifest.EVENT_NETWORK ->
        if (event.state == CardManifest.NETWORK_OFFLINE) "网络 · 离线" else "网络 · 在线"

    CardManifest.EVENT_BATTERY -> buildString {
        append("电量")
        val conds = buildList {
            if (event.levelBelow >= 0) add("低于 ${event.levelBelow}%")
            if (event.levelAbove >= 0) add("高于 ${event.levelAbove}%")
            when (event.state) {
                CardManifest.BATTERY_CHARGING -> add("充电中")
                CardManifest.BATTERY_DISCHARGING -> add("放电中")
            }
        }
        if (conds.isNotEmpty()) append(" · ${conds.joinToString("，")}") else append(" · 任意")
    }

    CardManifest.EVENT_SCREEN -> when (event.state) {
        CardManifest.SCREEN_ON -> "屏幕 · 点亮"
        CardManifest.SCREEN_OFF -> "屏幕 · 熄灭"
        CardManifest.SCREEN_UNLOCKED -> "屏幕 · 解锁"
        CardManifest.SCREEN_LOCKED -> "屏幕 · 锁屏"
        else -> "屏幕"
    }

    CardManifest.EVENT_CLIPBOARD -> "剪贴板含「${event.textContains}」"

    CardManifest.EVENT_BLUETOOTH -> buildString {
        append("蓝牙")
        if (event.device.isNotBlank()) append(" ${event.device}")
        append(
            if (event.state == CardManifest.STATE_DISCONNECTED) " · 已断开" else " · 已连接"
        )
    }

    CardManifest.EVENT_LOCATION -> buildString {
        append("位置 · ${if (event.state == CardManifest.LOCATION_EXIT) "离开" else "进入"}")
        append(" ${event.lat ?: "-"},${event.lon ?: "-"} 半径 ${event.radiusM}m")
    }

    else -> event.type
}

private fun weekdayName(day: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(day - 1) { "?" }
