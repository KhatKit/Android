package heizige.kk.khatkit.app.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeTextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionNotification
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DisplaySetting
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khromia.components.SegmentedItem
import heizige.kk.khromia.components.SingleChoiceSegmentedRow
import heizige.kk.khatkit.app.core.util.toLocalDateTime
import java.time.Instant

/**
 * 通知偏好页的 Miuix 风格版。
 */
@Composable
fun SettingPreferencesNotificationPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var showUpdatePauseDialog by remember { mutableStateOf(false) }
    var selectedUpdatePauseDays by remember { mutableStateOf(UPDATE_PAUSE_DAY_OPTIONS.first()) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val updateChecksEnabled =
        displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()

    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setOf(
            PermissionNotification
        ) else emptySet(),
    )
    PermissionManager(permissionState = permissionState)

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_notification),
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_display_page_show_updates_title),
                summary = if (updateChecksEnabled) {
                    stringResource(R.string.setting_update_reminder_enabled)
                } else {
                    stringResource(
                        R.string.setting_update_reminder_paused_until,
                        Instant.ofEpochMilli(displaySetting.updateCheckDisabledUntilEpochMillis)
                            .toLocalDateTime(),
                    )
                },
                onClick = {
                    selectedUpdatePauseDays = UPDATE_PAUSE_DAY_OPTIONS.first()
                    showUpdatePauseDialog = true
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.setting_display_page_notification_message_generated),
                summary = stringResource(R.string.setting_display_page_notification_message_generated_desc),
                checked = displaySetting.enableNotificationOnMessageGeneration,
                onCheckedChange = {
                    if (it && !permissionState.allPermissionsGranted) {
                        permissionState.requestPermissions()
                    }
                    updateDisplaySetting(displaySetting.copy(enableNotificationOnMessageGeneration = it))
                },
            )
            if (displaySetting.enableNotificationOnMessageGeneration) {
                PreferenceSwitch(
                    title = stringResource(R.string.setting_display_page_live_update_notification),
                    summary = stringResource(R.string.setting_display_page_live_update_notification_desc),
                    checked = displaySetting.enableLiveUpdateNotification,
                    onCheckedChange = {
                        updateDisplaySetting(displaySetting.copy(enableLiveUpdateNotification = it))
                    },
                )
            }
        }
    }

    if (showUpdatePauseDialog) {
        AppAlertDialog(
            onDismissRequest = { showUpdatePauseDialog = false },
            title = { Text(stringResource(R.string.setting_update_reminder_pause_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.setting_update_reminder_pause_description))
                    SingleChoiceSegmentedRow(
                        items = UPDATE_PAUSE_DAY_OPTIONS.map { days ->
                            SegmentedItem(
                                label = stringResource(R.string.setting_update_reminder_pause_days, days),
                                selected = selectedUpdatePauseDays == days,
                                onClick = { selectedUpdatePauseDays = days },
                            )
                        },
                    )
                    if (!updateChecksEnabled) {
                        KedgeTextButton(
                            onClick = {
                                updateDisplaySetting(
                                    displaySetting.copy(updateCheckDisabledUntilEpochMillis = 0L)
                                )
                                showUpdatePauseDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            },
            confirmButton = {
                KedgeTextButton(onClick = {
                    updateDisplaySetting(
                        displaySetting.copy(
                            updateCheckDisabledUntilEpochMillis =
                                System.currentTimeMillis() + selectedUpdatePauseDays * MILLIS_PER_DAY
                        )
                    )
                    showUpdatePauseDialog = false
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )
    }
}
