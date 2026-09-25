package heizige.kk.khatkit.app.feature.backup.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import heizige.kk.khromia.components.PrimaryBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.sync.S3BackupItem
import heizige.kk.khatkit.app.core.data.sync.s3.S3Config
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.feature.backup.BackupViewModel
import heizige.kk.khatkit.app.core.util.UiState
import heizige.kk.khatkit.app.core.util.fileSizeToString
import heizige.kk.khatkit.app.core.util.onError
import heizige.kk.khatkit.app.core.util.onLoading
import heizige.kk.khatkit.app.core.util.onSuccess
import heizige.kk.khatkit.app.core.util.toLocalDateTime
import java.time.Instant
import heizige.kk.khatkit.app.core.ui.icons.settingsBackupRestore
import heizige.kk.khatkit.app.core.ui.icons.upload
import heizige.kk.khatkit.app.core.ui.icons.visibility
import heizige.kk.khatkit.app.core.ui.icons.visibilityOff

@Composable
fun S3Tab(
    vm: BackupViewModel,
    onShowRestartDialog: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s3Config = settings.s3Config
    val backupItemsState by vm.s3BackupItems.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBackupFiles by remember { mutableStateOf(false) }
    var restoringItemId by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }

    fun updateS3Config(newConfig: S3Config) {
        vm.updateSettings(settings.copy(s3Config = newConfig))
    }

    val lastBackupText = if (settings.backupReminderConfig.lastBackupTime == 0L) {
        stringResource(R.string.backup_page_reminder_no_record)
    } else {
        stringResource(
            R.string.backup_page_reminder_last_time,
            Instant.ofEpochMilli(settings.backupReminderConfig.lastBackupTime).toLocalDateTime()
        )
    }
    val backupFileSummary = when (val state = backupItemsState) {
        is UiState.Success -> "${stringResource(R.string.backup_page_files)}: ${state.data.size}"
        UiState.Loading -> "${stringResource(R.string.backup_page_files)}: ..."
        UiState.Idle -> "${stringResource(R.string.backup_page_files)}: -"
        is UiState.Error -> "${stringResource(R.string.backup_page_files)}: -"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackupStatusCard(
                title = stringResource(R.string.backup_page_s3_backup),
                lastBackupText = lastBackupText,
                fileSummaryText = backupFileSummary
            )

            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_endpoint)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.endpoint,
                            onValueChange = { updateS3Config(s3Config.copy(endpoint = it.trim())) },
                            placeholder = { Text("https://s3.amazonaws.com") },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_access_key_id)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.accessKeyId,
                            onValueChange = { updateS3Config(s3Config.copy(accessKeyId = it.trim())) },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_secret_access_key)) },
                    supportingContent = {
                        var passwordVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.secretAccessKey,
                            onValueChange = { updateS3Config(s3Config.copy(secretAccessKey = it.trim())) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) {
                                    visibilityOff
                                } else {
                                    visibility
                                }
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = null)
                                }
                            },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_bucket)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.bucket,
                            onValueChange = { updateS3Config(s3Config.copy(bucket = it.trim())) },
                            placeholder = { Text("my-bucket") },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_path_style)) },
                    supportingContent = { Text(stringResource(R.string.backup_page_s3_path_style_desc)) },
                    trailingContent = {
                        Switch(
                            checked = s3Config.pathStyle,
                            onCheckedChange = { updateS3Config(s3Config.copy(pathStyle = it)) },
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_region)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.region,
                            onValueChange = { updateS3Config(s3Config.copy(region = it.trim())) },
                            placeholder = { Text("auto") },
                            singleLine = true
                        )
                    },
                )
            }

            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_backup_items)) },
                    supportingContent = {
                        MultiChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            S3Config.BackupItem.entries.forEachIndexed { index, item ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = S3Config.BackupItem.entries.size
                                    ),
                                    onCheckedChange = { checked ->
                                        val newItems = if (checked) {
                                            s3Config.items + item
                                        } else {
                                            s3Config.items - item
                                        }
                                        updateS3Config(s3Config.copy(items = newItems))
                                    },
                                    checked = item in s3Config.items
                                ) {
                                    Text(
                                        when (item) {
                                            S3Config.BackupItem.DATABASE -> stringResource(R.string.backup_page_chat_records)
                                            S3Config.BackupItem.FILES -> stringResource(R.string.backup_page_files)
                                        }
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

        HorizontalDivider()
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            vm.testS3()
                            Toast.show(
                                context.getString(R.string.backup_page_connection_success),
                                isError = false
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.show(
                                context.getString(
                                    R.string.backup_page_connection_failed,
                                    e.message ?: ""
                                ),
                                isError = true
                            )
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.backup_page_test_connection))
            }
            OutlinedButton(
                onClick = {
                    vm.loadS3BackupFileItems()
                    showBackupFiles = true
                }
            ) {
                Text(stringResource(R.string.backup_page_restore))
            }

            Button(
                onClick = {
                    scope.launch {
                        isBackingUp = true
                        runCatching {
                            vm.backupToS3()
                            vm.loadS3BackupFileItems()
                            Toast.show(
                                context.getString(R.string.backup_page_backup_success),
                                isError = false
                            )
                        }.onFailure {
                            it.printStackTrace()
                            Toast.show(
                                it.message ?: context.getString(R.string.backup_page_unknown_error),
                                isError = true
                            )
                        }
                        isBackingUp = false
                    }
                },
                enabled = !isBackingUp
            ) {
                if (isBackingUp) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(upload, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isBackingUp) {
                        stringResource(R.string.backup_page_backing_up)
                    } else {
                        stringResource(R.string.backup_page_backup_now)
                    }
                )
            }
        }
    }

    if (showBackupFiles) {
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.backup_page_s3_backup_files),
            imageVector = settingsBackupRestore,
            onDismiss = {
                showBackupFiles = false
            },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                backupItemsState.onSuccess {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(it) { item ->
                            S3BackupItemCard(
                                item = item,
                                isRestoring = restoringItemId == item.displayName,
                                onDelete = {
                                    scope.launch {
                                        runCatching {
                                            vm.deleteS3BackupFile(item)
                                            Toast.show(
                                                context.getString(R.string.backup_page_delete_success),
                                                isError = false
                                            )
                                            vm.loadS3BackupFileItems()
                                        }.onFailure { err ->
                                            err.printStackTrace()
                                            Toast.show(
                                                context.getString(
                                                    R.string.backup_page_delete_failed,
                                                    err.message ?: ""
                                                ),
                                                isError = true
                                            )
                                        }
                                    }
                                },
                                onRestore = { restoreItem ->
                                    scope.launch {
                                        restoringItemId = restoreItem.displayName
                                        runCatching {
                                            vm.restoreFromS3(item = restoreItem)
                                            Toast.show(
                                                context.getString(R.string.backup_page_restore_success),
                                                isError = false
                                            )
                                            showBackupFiles = false
                                            onShowRestartDialog()
                                        }.onFailure { err ->
                                            err.printStackTrace()
                                            Toast.show(
                                                context.getString(
                                                    R.string.backup_page_restore_failed,
                                                    err.message ?: ""
                                                ),
                                                isError = true
                                            )
                                        }
                                        restoringItemId = null
                                    }
                                },
                            )
                        }
                    }
                }.onError {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.backup_page_loading_failed, it.message ?: ""),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }.onLoading {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupStatusCard(
    title: String,
    lastBackupText: String,
    fileSummaryText: String,
) {
    CardGroup {
        item(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = lastBackupText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fileSummaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
        )
    }
}

@Composable
private fun S3BackupItemCard(
    item: S3BackupItem,
    isRestoring: Boolean = false,
    onDelete: (S3BackupItem) -> Unit = {},
    onRestore: (S3BackupItem) -> Unit = {},
) {
    CardGroup {
        item(
            headlineContent = {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.lastModified.toLocalDateTime(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = item.size.fileSizeToString(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onDelete(item)
                            },
                            enabled = !isRestoring
                        ) {
                            Text(stringResource(R.string.backup_page_delete))
                        }
                        Button(
                            onClick = {
                                onRestore(item)
                            },
                            enabled = !isRestoring
                        ) {
                            if (isRestoring) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (isRestoring) {
                                    stringResource(R.string.backup_page_restoring)
                                } else {
                                    stringResource(R.string.backup_page_restore_now)
                                }
                            )
                        }
                    }
                }
            },
        )
    }
}
