package heizige.kk.khatkit.app.core.ui.components.permission

import heizige.kk.khatkit.uikit.mochi.MochiButton
import androidx.compose.runtime.LaunchedEffect
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSheetState
import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import ui.feice.mochi.effect.mask.mochiScrollFadeMask
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import androidx.compose.material3.Icon
import ui.feice.mochi.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.feature.automation.data.ApprovalCategory
import heizige.kk.khatkit.app.feature.automation.data.ApprovalPolicy
import heizige.kk.khatkit.app.feature.automation.data.AutomationBus
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.feature.automation.KhatKitAccessibilityService
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.util.hasUsageStatsPermission
import heizige.kk.khatkit.bridge.impl.AllFilesAccess
import heizige.kk.khatkit.bridge.impl.ClipboardAccess
import heizige.kk.khatkit.bridge.impl.RootBridgeImpl
import heizige.kk.khatkit.bridge.impl.ShizukuPermission
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme

private const val SHIZUKU_REQUEST_CODE = 0x4B4B

/**
 * 权限清单：常规权限 / 系统级权限 / 高级权限三组，实时状态 + 授权按钮。
 *
 * [compact] = false 时为欢迎页整屏样式（背景、渐隐、内部滚动）；
 * [compact] = true 时适合放进 BottomSheet（由外部滚动容器负责滚动）。
 */
@Composable
fun PermissionChecklist(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** compact 模式下自带的留白；嵌进 Mochi 脚手架（已带 16dp 边距）时传零值。 */
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    header: @Composable ColumnScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val provider: KhatKitToolProvider = rememberAppEntryPoint().khatKitToolProvider()

    var clipboardEnabled by remember { mutableStateOf(ClipboardAccess.isEnabled(context)) }
    var rootEnabled by remember { mutableStateOf(provider.enableRoot) }
    var rootAvailable by remember { mutableStateOf(RootBridgeImpl.isAvailable()) }
    var shizukuAvailable by remember { mutableStateOf(ShizukuPermission.isAvailable()) }
    var shizukuGranted by remember { mutableStateOf(ShizukuPermission.isGranted()) }

    var notificationsGranted by remember { mutableStateOf(checkNotifications(context)) }
    var micGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var cameraGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var calendarGranted by remember {
        mutableStateOf(
            hasPermission(context, Manifest.permission.READ_CALENDAR) &&
                hasPermission(context, Manifest.permission.WRITE_CALENDAR)
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
                hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    var bluetoothGranted by remember { mutableStateOf(checkBluetoothPermission(context)) }
    var usageStatsGranted by remember { mutableStateOf(context.hasUsageStatsPermission()) }
    var exactAlarmGranted by remember { mutableStateOf(checkExactAlarm(context)) }
    var batteryIgnored by remember { mutableStateOf(checkBatteryOptimization(context)) }
    var allFilesGranted by remember { mutableStateOf(AllFilesAccess.isGranted()) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(KhatKitAccessibilityService.isEnabled(context)) }
    var listenerEnabled by remember { mutableStateOf(checkNotificationListener(context)) }
    var handsOff by remember { mutableStateOf(provider.handsOffMode) }
    var showApprovalPolicy by remember { mutableStateOf(false) }

    fun refresh() {
        rootEnabled = provider.enableRoot
        rootAvailable = RootBridgeImpl.isAvailable()
        shizukuAvailable = ShizukuPermission.isAvailable()
        shizukuGranted = ShizukuPermission.isGranted()
        notificationsGranted = checkNotifications(context)
        micGranted = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        cameraGranted = hasPermission(context, Manifest.permission.CAMERA)
        calendarGranted = hasPermission(context, Manifest.permission.READ_CALENDAR) &&
            hasPermission(context, Manifest.permission.WRITE_CALENDAR)
        locationGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        bluetoothGranted = checkBluetoothPermission(context)
        usageStatsGranted = context.hasUsageStatsPermission()
        exactAlarmGranted = checkExactAlarm(context)
        batteryIgnored = checkBatteryOptimization(context)
        allFilesGranted = AllFilesAccess.isGranted()
        overlayGranted = Settings.canDrawOverlays(context)
        accessibilityEnabled = KhatKitAccessibilityService.isEnabled(context)
        listenerEnabled = checkNotificationListener(context)
        handsOff = provider.handsOffMode
    }

    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    fun openSettings(intent: Intent) {
        runCatching { settingsLauncher.launch(intent) }
            .onFailure {
                runCatching {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
    }

    val packageUri = "package:${context.packageName}".toUri()
    val missingRuntime = buildList {
        if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
        if (!locationGranted) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!bluetoothGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (!cameraGranted) add(Manifest.permission.CAMERA)
        if (!calendarGranted) {
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.WRITE_CALENDAR)
        }
    }

    val runtimeItems = buildList {
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_notification_title,
                descRes = R.string.greeting_permission_notification_desc,
                granted = notificationsGranted,
                actionLabelRes = R.string.greeting_permission_grant,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    } else {
                        refresh()
                    }
                },
            )
        )
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_mic_title,
                descRes = R.string.greeting_permission_mic_desc,
                granted = micGranted,
                actionLabelRes = R.string.greeting_permission_grant,
                onAction = { permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
            )
        )
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_location_title,
                descRes = R.string.greeting_permission_location_desc,
                granted = locationGranted,
                actionLabelRes = R.string.greeting_permission_grant,
                onAction = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                PermissionUiItem(
                    titleRes = R.string.greeting_permission_bluetooth_title,
                    descRes = R.string.greeting_permission_bluetooth_desc,
                    granted = bluetoothGranted,
                    actionLabelRes = R.string.greeting_permission_grant,
                    onAction = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                            )
                        )
                    },
                )
            )
        }
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_camera_title,
                descRes = R.string.greeting_permission_camera_desc,
                granted = cameraGranted,
                actionLabelRes = R.string.greeting_permission_grant,
                onAction = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
            )
        )
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_calendar_title,
                descRes = R.string.greeting_permission_calendar_desc,
                granted = calendarGranted,
                actionLabelRes = R.string.greeting_permission_grant,
                onAction = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                        )
                    )
                },
            )
        )
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_background_title,
                descRes = R.string.greeting_permission_background_desc,
                granted = true,
                actionLabelRes = R.string.greeting_permission_grant,
            )
        )
    }

    val specialItems = buildList {
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_usage_stats_title,
                descRes = R.string.greeting_permission_usage_stats_desc,
                granted = usageStatsGranted,
                actionLabelRes = R.string.greeting_permission_action_settings,
                onAction = { openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                PermissionUiItem(
                    titleRes = R.string.greeting_permission_exact_alarm_title,
                    descRes = R.string.greeting_permission_exact_alarm_desc,
                    granted = exactAlarmGranted,
                    actionLabelRes = R.string.greeting_permission_action_settings,
                    onAction = {
                        openSettings(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
                        )
                    },
                )
            )
        }
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_battery_title,
                descRes = R.string.greeting_permission_battery_desc,
                granted = batteryIgnored,
                actionLabelRes = R.string.greeting_permission_action_settings,
                onAction = {
                    val request = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        packageUri,
                    )
                    runCatching { settingsLauncher.launch(request) }.onFailure {
                        openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                },
            )
        )
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_files_title,
                descRes = R.string.greeting_permission_files_desc,
                granted = allFilesGranted,
                actionLabelRes = R.string.greeting_permission_action_settings,
                onAction = {
                    if (activity != null) {
                        AllFilesAccess.request(activity)
                    } else {
                        openSettings(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                },
            )
        )
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_overlay_title,
                descRes = R.string.greeting_permission_overlay_desc,
                granted = overlayGranted,
                actionLabelRes = R.string.greeting_permission_action_settings,
                onAction = {
                    runCatching {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
                        )
                    }.onFailure {
                        openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }
                },
            )
        )
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_listener_title,
                descRes = R.string.greeting_permission_listener_desc,
                granted = listenerEnabled,
                actionLabelRes = R.string.greeting_permission_action_settings,
                onAction = {
                    openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
            )
        )
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.fillMaxSize())
            .then(
                if (compact) {
                    Modifier.padding(contentPadding)
                } else {
                    // 独立整页（引导页）：透出页面底色，滚动边缘渐隐与 Mochi 回弹
                    Modifier
                        .mochiScrollFadeMask(scrollState)
                        .verticalScroll(scrollState, flingBehavior = MochiScrollDefaults.flingBehavior())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                }
            ),
        verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
    ) {
        header()

        // 顶部状态卡：还有缺失就走「一键授权」，全齐就给一行对勾。
        MochiListSection {
            if (missingRuntime.isNotEmpty()) {
                MochiListRow(
                    title = stringResource(R.string.greeting_permission_grant_all, missingRuntime.size),
                    subtitle = stringResource(R.string.greeting_permissions_hint),
                    leading = {
                        Icon(
                            imageVector = bolt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MochiTheme.colorScheme.primary,
                        )
                    },
                    chevron = true,
                    onClick = { permissionLauncher.launch(missingRuntime.toTypedArray()) },
                )
            } else {
                MochiListRow(
                    title = stringResource(R.string.greeting_permission_all_granted),
                    checked = true,
                )
            }
        }

        // 常规权限：运行时权限，点了直接弹系统授权框。
        MochiListSection(header = stringResource(R.string.greeting_permissions_normal)) {
            runtimeItems.forEach { item -> PermissionRow(item) }
            // 剪贴板不是 Android 运行时权限（系统只允许前台读取），放在常规组作为应用级开关
            MochiListRow(
                title = "剪贴板读取",
                subtitle = if (clipboardEnabled) {
                    "允许卡片和工具读取剪贴板 · 系统只允许前台读取"
                } else {
                    "已关闭：卡片读取剪贴板会直接报错"
                },
                checked = clipboardEnabled,
                onClick = {
                    val next = !clipboardEnabled
                    ClipboardAccess.setEnabled(context, next)
                    clipboardEnabled = next
                },
            )
        }

        // 系统级权限：跳系统设置页授权，回来 LifecycleResumeEffect 自动刷新状态。
        MochiListSection(
            header = stringResource(R.string.greeting_permissions_special),
            footer = stringResource(R.string.greeting_permissions_hint),
        ) {
            specialItems.forEach { item -> PermissionRow(item) }
        }

        // 高危权限：无障碍 / root / Shizuku / 放手模式 / 审批策略。
        MochiListSection(header = stringResource(R.string.greeting_permissions_dangerous)) {
            PermissionDangerRow(
                title = stringResource(R.string.greeting_permission_accessibility_title),
                subtitle = stringResource(R.string.greeting_permission_accessibility_desc),
                selected = accessibilityEnabled,
                onClick = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
            PermissionDangerRow(
                title = stringResource(R.string.greeting_permission_root_title),
                subtitle = if (rootAvailable) {
                    stringResource(R.string.greeting_permission_root_desc)
                } else {
                    stringResource(R.string.greeting_permission_root_desc) + " · 未检测到 root"
                },
                selected = rootAvailable && rootEnabled,
                enabled = rootAvailable,
                onClick = {
                    val next = !rootEnabled
                    provider.enableRoot = next
                    provider.applySettings()
                    rootEnabled = next
                },
            )
            PermissionDangerRow(
                title = stringResource(R.string.greeting_permission_shizuku_title),
                subtitle = if (shizukuAvailable) {
                    stringResource(R.string.greeting_permission_shizuku_desc)
                } else {
                    stringResource(R.string.greeting_permission_shizuku_desc) + " · 未运行"
                },
                selected = shizukuGranted,
                enabled = shizukuAvailable,
                onClick = {
                    if (!shizukuGranted && shizukuAvailable && activity != null) {
                        ShizukuPermission.request(activity, SHIZUKU_REQUEST_CODE) { granted ->
                            shizukuGranted = granted
                            shizukuAvailable = ShizukuPermission.isAvailable()
                        }
                    } else {
                        refresh()
                    }
                },
            )
            PermissionDangerRow(
                title = stringResource(R.string.approval_hands_off_title),
                subtitle = stringResource(R.string.approval_hands_off_desc) +
                    " " + stringResource(
                        if (handsOff) R.string.approval_status_on else R.string.approval_status_off
                    ),
                selected = handsOff,
                onClick = {
                    val next = !handsOff
                    provider.handsOffMode = next
                    handsOff = next
                },
            )
            MochiListRow(
                title = stringResource(R.string.approval_sheet_title),
                subtitle = stringResource(R.string.approval_sheet_desc),
                chevron = true,
                onClick = { showApprovalPolicy = true },
            )
        }
    }

    // U7b-6：state 由这里持有，去 if 门控。
    val approvalSheet = rememberMochiSheetState()
    LaunchedEffect(showApprovalPolicy) { if (showApprovalPolicy) approvalSheet.show() else approvalSheet.hide() }
    ApprovalPolicySheet(
        sheetState = approvalSheet,
        onDismiss = { showApprovalPolicy = false },
    )
}

/**
 * 自动化审批策略面板：按 [ApprovalCategory] 列出，每项用 Mochi 分段控件三选一
 * （每次询问 / 允许 / 拒绝），选择即持久化并立即生效；放手模式仍覆盖一切为允许。
 */
@Composable
private fun ApprovalPolicySheet(
    sheetState: MochiSheetState,
    onDismiss: () -> Unit,
) {
    // U7b-6：直连 MochiSheet（state 由调用方持有）。
    MochiSheet(state = sheetState, onDismiss = onDismiss) {
        MochiSheetHeader(title = stringResource(R.string.approval_sheet_title))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
            MochiListSection(footer = stringResource(R.string.approval_sheet_desc)) {
                ApprovalCategory.entries.forEach { category ->
                    var policy by remember(category) {
                        mutableStateOf(AutomationBus.policyOf(category))
                    }
                    val options = ApprovalPolicy.entries
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MochiListDefaults.RowPadding, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(category.labelRes),
                            style = MochiTheme.textStyles.subhead,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                        MochiSegmentedControl(
                            selectedIndex = options.indexOf(policy).coerceAtLeast(0),
                            onSelect = { index ->
                                val next = options.getOrNull(index) ?: return@MochiSegmentedControl
                                policy = next
                                AutomationBus.setPolicy(category, next)
                            },
                            items = options.map { stringResource(it.labelRes) }.toImmutableList(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private data class PermissionUiItem(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val granted: Boolean,
    @StringRes val actionLabelRes: Int,
    val onAction: (() -> Unit)? = null,
)

/** 权限行：已授权给对勾，未授权给「›」并点进授权流程。 */
@Composable
private fun PermissionRow(item: PermissionUiItem) {
    MochiListRow(
        title = stringResource(item.titleRes),
        subtitle = stringResource(item.descRes),
        checked = item.granted,
        chevron = !item.granted,
        onClick = item.onAction,
    )
}

/** 高危权限行：标题用警示色，授权状态给对勾。 */
@Composable
private fun PermissionDangerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    MochiListRow(
        title = title,
        subtitle = subtitle,
        checked = selected,
        enabled = enabled,
        destructive = true,
        onClick = onClick,
    )
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun checkBatteryOptimization(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun checkBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (
            hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT) &&
                hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            )

private fun checkExactAlarm(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return alarmManager.canScheduleExactAlarms()
}

private fun checkNotificationListener(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun checkNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
