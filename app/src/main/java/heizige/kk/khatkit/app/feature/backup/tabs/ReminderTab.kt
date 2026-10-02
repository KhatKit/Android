package heizige.kk.khatkit.app.feature.backup.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.BackupReminderConfig
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.feature.backup.BackupViewModel
import heizige.kk.khatkit.app.core.util.toLocalDateTime
import java.time.Instant
import heizige.kk.kedge.components.KedgeSingleChoiceSegmentedRow
import heizige.kk.kedge.components.KedgeMultiChoiceSegmentedRow
import heizige.kk.khromia.components.SegmentedItem

@Composable
fun ReminderTab(vm: BackupViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val config = settings.backupReminderConfig

    fun updateConfig(update: BackupReminderConfig) {
        vm.updateSettings(settings.copy(backupReminderConfig = update))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardGroup(
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(
                trailingContent = {
                    KedgeSwitch(
                        checked = config.enabled,
                        onCheckedChange = { updateConfig(config.copy(enabled = it)) },
                    )
                },
                headlineContent = { Text(stringResource(R.string.backup_page_reminder_enable)) },
            )

            if (config.enabled) {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_reminder_interval)) },
                    supportingContent = {
                        val intervals = listOf(1, 3, 7, 14, 30)
                        KedgeSingleChoiceSegmentedRow(
                            modifier = Modifier.fillMaxWidth(),
                            items = intervals.map { days ->
                                SegmentedItem(
                                    label = stringResource(R.string.backup_page_reminder_interval_days, days),
                                    onClick = { updateConfig(config.copy(intervalDays = days)) },
                                    selected = config.intervalDays == days,
                                )
                            },
                        )
                    },
                )

                item(
                    headlineContent = {
                        Text(
                            if (config.lastBackupTime == 0L) {
                                stringResource(R.string.backup_page_reminder_no_record)
                            } else {
                                stringResource(
                                    R.string.backup_page_reminder_last_time,
                                    Instant.ofEpochMilli(config.lastBackupTime).toLocalDateTime()
                                )
                            }
                        )
                    },
                )
            }
        }
    }
}
