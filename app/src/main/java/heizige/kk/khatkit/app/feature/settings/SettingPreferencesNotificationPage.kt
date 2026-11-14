package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khatkit.app.core.ui.components.ui.settingAnchor
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import androidx.compose.material3.ButtonDefaults
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.uikit.mochi.KkOptionSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.SwitchSetting
import heizige.kk.khatkit.app.core.ui.components.ui.settingItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionNotification
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.util.toLocalDateTime
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.time.Instant
import heizige.kk.khatkit.uikit.mochi.SegmentedItem
import heizige.kk.khatkit.uikit.mochi.KkSingleChoiceSegmentedRow
import heizige.kk.khatkit.uikit.mochi.KkSingleChoiceSegmentedRow
import heizige.kk.khatkit.uikit.mochi.KkMultiChoiceSegmentedRow

internal val UPDATE_PAUSE_DAY_OPTIONS = listOf(7, 14, 21)
internal const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000L

@Composable
fun SettingPreferencesNotificationPage(vm: SettingViewModel = hiltViewModel()) {
    
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showUpdatePauseDialog by remember { mutableStateOf(false) }
    var selectedUpdatePauseDays by remember { mutableStateOf(UPDATE_PAUSE_DAY_OPTIONS.first()) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val updateChecksEnabled =
        displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()

    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setOf(
            PermissionNotification
        ) else emptySet(),
    )
    PermissionManager(permissionState = permissionState)

    MochiSettingsPageScaffold(
        title = stringResource(R.string.setting_page_preferences_notification),
        scrollBehavior = scrollBehavior,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(
                start = 8.dp,
                top = PageMetrics.TopContentPadding,
                end = 8.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = {
                            selectedUpdatePauseDays = UPDATE_PAUSE_DAY_OPTIONS.first()
                            showUpdatePauseDialog = true
                        },
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_updates_title)) },
                        supportingContent = {
                            Text(
                                if (updateChecksEnabled) {
                                    stringResource(R.string.setting_update_reminder_enabled)
                                } else {
                                    stringResource(
                                        R.string.setting_update_reminder_paused_until,
                                        Instant.ofEpochMilli(displaySetting.updateCheckDisabledUntilEpochMillis)
                                            .toLocalDateTime(),
                                    )
                                }
                            )
                        },
                        // 点了是弹出暂停时长对话框，不是进二级页，所以行尾不放 ›
                        anchor = settingAnchor(R.string.setting_display_page_show_updates_title),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_notification_message_generated,
                            R.string.setting_display_page_notification_message_generated_desc,
                            checked = displaySetting.enableNotificationOnMessageGeneration,
                            onCheckedChange = {
                                if (it && !permissionState.allPermissionsGranted) {
                                    permissionState.requestPermissions()
                                }
                                updateDisplaySetting(displaySetting.copy(enableNotificationOnMessageGeneration = it))
                            },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_notification_message_generated),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_live_update_notification,
                            R.string.setting_display_page_live_update_notification_desc,
                            checked = displaySetting.enableLiveUpdateNotification,
                            onCheckedChange = { updateDisplaySetting(displaySetting.copy(enableLiveUpdateNotification = it)) },
                        ),
                        visible = displaySetting.enableNotificationOnMessageGeneration,
                        anchor = settingAnchor(R.string.setting_display_page_live_update_notification),
                    )
                    settingItem(
                        SwitchSetting(
                            R.string.setting_display_page_floating_chat,
                            R.string.setting_display_page_floating_chat_desc,
                            checked = displaySetting.enableFloatingChatWindow,
                            onCheckedChange = { enabled ->
                                if (enabled) ensureOverlayPermission(context)
                                updateDisplaySetting(displaySetting.copy(enableFloatingChatWindow = enabled))
                            },
                        ),
                        anchor = settingAnchor(R.string.setting_display_page_floating_chat),
                    )
                }
            }
        }
    }

    if (showUpdatePauseDialog) {
        AppDialog(
            onDismissRequest = { showUpdatePauseDialog = false },
            title = { Text(stringResource(R.string.setting_update_reminder_pause_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.setting_update_reminder_pause_description))
                    KkSingleChoiceSegmentedRow(
                        items = UPDATE_PAUSE_DAY_OPTIONS.map { days ->
                            SegmentedItem(
                                label = stringResource(R.string.setting_update_reminder_pause_days, days),
                                selected = selectedUpdatePauseDays == days,
                                onClick = { selectedUpdatePauseDays = days },
                            )
                        },
                    )
                    if (!updateChecksEnabled) {
                        MochiTextButton(
                            onClick = {
                                updateDisplaySetting(
                                    displaySetting.copy(updateCheckDisabledUntilEpochMillis = 0L)
                                )
                                showUpdatePauseDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.setting_update_reminder_resume_now))
                        }
                    }
                }
            },
            confirmButton = {
                MochiTextButton(
                    onClick = {
                        updateDisplaySetting(
                            displaySetting.copy(
                                updateCheckDisabledUntilEpochMillis =
                                    System.currentTimeMillis() + selectedUpdatePauseDays * MILLIS_PER_DAY,
                            )
                        )
                        showUpdatePauseDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                MochiTextButton(onClick = { showUpdatePauseDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** 开悬浮窗时申请覆盖层权限：没有权限就跳到系统的「显示在其他应用上层」页。 */
private fun ensureOverlayPermission(context: Context) {
    if (Settings.canDrawOverlays(context)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}