package heizige.kk.khatkit.app.ui.components.permission

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
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.service.KhatKitAccessibilityService
import heizige.kk.khatkit.app.ui.icons.bolt
import heizige.kk.khatkit.app.ui.theme.listCardStyle
import heizige.kk.khatkit.app.utils.hasUsageStatsPermission
import heizige.kk.khatkit.bridge.impl.AllFilesAccess
import heizige.kk.khatkit.bridge.impl.RootBridgeImpl
import heizige.kk.khatkit.bridge.impl.ShizukuPermission
import heizige.kk.khromia.components.AnimatedRadioItem
import heizige.kk.khromia.helper.fadingEdge
import heizige.kk.khromia.text.OptionsText
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeStatusLevel
import heizige.kk.kedge.components.KedgeWarningCard
import heizige.kk.kedge.theme.KedgeColors
import org.koin.compose.koinInject

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
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val cards = listCardStyle()
    val provider: KhatKitToolProvider = koinInject()

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
        add(
            PermissionUiItem(
                titleRes = R.string.greeting_permission_accessibility_title,
                descRes = R.string.greeting_permission_accessibility_desc,
                granted = accessibilityEnabled,
                actionLabelRes = R.string.greeting_permission_action_settings,
                onAction = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.fillMaxSize())
            .then(
                if (compact) {
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                } else {
                    Modifier
                        .background(KedgeColors.background)
                        .fadingEdge(top = 32.dp, bottom = 56.dp, strength = 1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                }
            ),
    ) {
        OptionsText(stringResource(R.string.greeting_permissions_normal))
        Spacer(modifier = Modifier.height(8.dp))
        if (missingRuntime.isNotEmpty()) {
            KedgeButton(
                onClick = { permissionLauncher.launch(missingRuntime.toTypedArray()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.greeting_permission_grant_all, missingRuntime.size))
            }
        } else {
            KedgeWarningCard(
                message = stringResource(R.string.greeting_permission_all_granted),
                level = KedgeStatusLevel.Success,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(cards.gap)) {
            runtimeItems.forEachIndexed { index, item ->
                PermissionActionItem(
                    item = item,
                    shape = cards.indexedShape(index, runtimeItems.size),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OptionsText(stringResource(R.string.greeting_permissions_special))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.greeting_permissions_hint),
            style = MaterialTheme.typography.bodySmall,
            color = KedgeColors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(cards.gap)) {
            specialItems.forEachIndexed { index, item ->
                PermissionActionItem(
                    item = item,
                    shape = cards.indexedShape(index, specialItems.size),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OptionsText(stringResource(R.string.greeting_permissions_dangerous))
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(cards.gap)) {
            PermissionRadioItem(
                title = stringResource(R.string.greeting_permission_root_title),
                subtitle = if (rootAvailable) {
                    stringResource(R.string.greeting_permission_root_desc)
                } else {
                    stringResource(R.string.greeting_permission_root_desc) + " · 未检测到 root"
                },
                selected = rootAvailable && rootEnabled,
                shape = cards.indexedShape(0, 2),
                dangerous = true,
                enabled = rootAvailable,
                onClick = {
                    val next = !rootEnabled
                    provider.enableRoot = next
                    provider.applySettings()
                    rootEnabled = next
                },
            )
            PermissionRadioItem(
                title = stringResource(R.string.greeting_permission_shizuku_title),
                subtitle = if (shizukuAvailable) {
                    stringResource(R.string.greeting_permission_shizuku_desc)
                } else {
                    stringResource(R.string.greeting_permission_shizuku_desc) + " · 未运行"
                },
                selected = shizukuGranted,
                shape = cards.indexedShape(1, 2),
                dangerous = true,
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

@Composable
private fun PermissionActionItem(
    item: PermissionUiItem,
    shape: Shape,
) {
    val statusLabel = if (item.granted) {
        stringResource(R.string.greeting_permission_status_granted)
    } else {
        stringResource(R.string.greeting_permission_status_not_granted) +
            " · " + stringResource(item.actionLabelRes)
    }
    AnimatedRadioItem(
        text = stringResource(item.titleRes),
        subtitle = stringResource(item.descRes) + " · " + statusLabel,
        isSelected = item.granted,
        onClick = { if (!item.granted) item.onAction?.invoke() },
        shape = shape,
    )
}

@Composable
private fun PermissionRadioItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    enabled: Boolean = true,
    dangerous: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    AnimatedRadioItem(
        text = title,
        subtitle = subtitle,
        isSelected = selected,
        onClick = { if (enabled) onClick() },
        selectedBackground = if (dangerous) colors.errorContainer.copy(alpha = 0.54f)
        else colors.primaryContainer.copy(alpha = 0.54f),
        unselectedBackground = colors.surfaceVariant.copy(alpha = if (enabled) 0.26f else 0.12f),
        checkIconTint = if (dangerous) colors.error else colors.primary,
        textColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
        shape = shape,
        modifier = Modifier.alpha(if (enabled) 1f else 0.6f),
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
