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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeIconButton
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
import heizige.kk.khatkit.app.core.data.datastore.WebDavConfig
import heizige.kk.khatkit.app.core.data.sync.webdav.WebDavBackupItem
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
import heizige.kk.kedge.components.KedgeHorizontalDivider
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSingleChoiceSegmentedRow
import heizige.kk.kedge.components.KedgeMultiChoiceSegmentedRow
import heizige.kk.khromia.components.SegmentedItem

@Composable
fun WebDavTab(
    vm: BackupViewModel,
    onShowRestartDialog: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val webDavConfig = settings.webDavConfig
    val backupItemsState by vm.webDavBackupItems.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBackupFiles by remember { mutableStateOf(false) }
    var restoringItemId by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }

    fun updateWebDavConfig(newConfig: WebDavConfig) {
        vm.updateSettings(settings.copy(webDavConfig = newConfig))
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
                title = stringResource(R.string.backup_page_webdav_backup),
                lastBackupText = lastBackupText,
                fileSummaryText = backupFileSummary
            )

            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_webdav_server_address)) },
                    supportingContent = {
                        KedgeOutlinedTextFieldWithSlots(
                            modifier = Modifier.fillMaxWidth(),
                            value = webDavConfig.url,
                            onValueChange = { updateWebDavConfig(webDavConfig.copy(url = it.trim())) },
                            placeholder = { Text("https://example.com/dav") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_username)) },
                    supportingContent = {
                        KedgeOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = webDavConfig.username,
                            onValueChange = {
                                updateWebDavConfig(
                                    webDavConfig.copy(
                                        username = it.trim()
                                    )
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_password)) },
                    supportingContent = {
                        var passwordVisible by remember { mutableStateOf(false) }
                        KedgeOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = webDavConfig.password,
                            onValueChange = { updateWebDavConfig(webDavConfig.copy(password = it.trim())) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) {
                                    visibilityOff
                                } else {
                                    visibility
                                }
                                KedgeIconButton(onClick = { passwordVisible = !passwordVisible }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(imageVector = image, contentDescription = null)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_path)) },
                    supportingContent = {
                        KedgeOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = webDavConfig.path,
                            onValueChange = { updateWebDavConfig(webDavConfig.copy(path = it.trim())) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    },
                )
            }

            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_backup_items)) },
                    supportingContent = {
                    KedgeMultiChoiceSegmentedRow(
                        modifier = Modifier.fillMaxWidth(),
                        items = WebDavConfig.BackupItem.entries.map { item ->
                            SegmentedItem(
                                label = when (item) {
                                    WebDavConfig.BackupItem.DATABASE -> stringResource(R.string.backup_page_chat_records)
                                    WebDavConfig.BackupItem.FILES -> stringResource(R.string.backup_page_files)
                                },
                                selected = item in webDavConfig.items,
                                onClick = {
                                    val checked = item !in webDavConfig.items
                                    val newItems = if (checked) {
                                        webDavConfig.items + item
                                    } else {
                                        webDavConfig.items - item
                                    }
                                    updateWebDavConfig(webDavConfig.copy(items = newItems))
                                },
                            )
                        },
                    )
                    },
                )
            }
        }

        KedgeHorizontalDivider()
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            KedgeButton(
                onClick = {
                    scope.launch {
                        try {
                            vm.testWebDav()
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
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.backup_page_test_connection))
            }
            KedgeButton(
                onClick = {
                    vm.loadBackupFileItems()
                    showBackupFiles = true
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.backup_page_restore))
            }
            KedgeButton(
                onClick = {
                    scope.launch {
                        isBackingUp = true
                        runCatching {
                            vm.backup()
                            vm.loadBackupFileItems()
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
                enabled = !isBackingUp,
                shapes = ButtonDefaults.shapes(),
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
            title = stringResource(R.string.backup_page_webdav_backup_files),
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
                            WebDavBackupItemCard(
                                item = item,
                                isRestoring = restoringItemId == item.displayName,
                                onDelete = {
                                    scope.launch {
                                        runCatching {
                                            vm.deleteWebDavBackupFile(item)
                                            Toast.show(
                                                context.getString(R.string.backup_page_delete_success),
                                                isError = false
                                            )
                                            vm.loadBackupFileItems()
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
                                            vm.restore(item = restoreItem)
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
                    style = KedgeTextStyles.title()
                )
            },
            supportingContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = lastBackupText,
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fileSummaryText,
                        style = KedgeTextStyles.body(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
        )
    }
}

@Composable
private fun WebDavBackupItemCard(
    item: WebDavBackupItem,
    isRestoring: Boolean = false,
    onDelete: (WebDavBackupItem) -> Unit = {},
    onRestore: (WebDavBackupItem) -> Unit = {},
) {
    CardGroup {
        item(
            headlineContent = {
                Text(
                    text = item.displayName,
                    style = KedgeTextStyles.title()
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
                            style = KedgeTextStyles.body(),
                        )
                        Text(
                            text = item.size.fileSizeToString(),
                            style = KedgeTextStyles.body(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KedgeTextButton(
                            onClick = {
                                onDelete(item)
                            },
                            enabled = !isRestoring,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.backup_page_delete))
                        }
                        KedgeButton(
                            onClick = {
                                onRestore(item)
                            },
                            enabled = !isRestoring,
                            shapes = ButtonDefaults.shapes(),
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
