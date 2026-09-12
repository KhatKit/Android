package heizige.kk.khatkit.uikit

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.bridge.impl.AllFilesAccess
import heizige.kk.khatkit.bridge.impl.ShizukuPermission
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeRadioButton
import heizige.kk.kedge.components.KedgeSlider
import heizige.kk.kedge.components.KedgeSwitch
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.khromia.components.PrimaryBottomSheet

private const val SHIZUKU_REQUEST_CODE = 0x4B4B

/** 卡片密钥查看 / 撤销（设计文档 7.4：可展示、可撤销）。 */
@Composable
fun KhatKitSecretsDialog(
    cardName: String,
    controller: KhatKitController,
    onDismiss: () -> Unit,
    onToast: (String, Boolean) -> Unit = { _, _ -> },
) {
    var secrets by remember(cardName) { mutableStateOf(controller.listCardSecrets(cardName)) }
    val context = LocalContext.current

    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.khatkit_secrets_title, cardName),
        imageVector = Icons.Filled.Lock,
        onDismiss = onDismiss,
        scrollable = false,
    ) { _ ->
        if (secrets.isEmpty()) {
            Text(stringResource(R.string.khatkit_secrets_empty))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                secrets.forEach { key ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(key, style = MaterialTheme.typography.bodyMedium)
                        KedgeTextButton(
                            onClick = {
                                controller.removeCardSecret(cardName, key)
                                secrets = controller.listCardSecrets(cardName)
                                onToast(context.getString(R.string.khatkit_secrets_revoked, key), false)
                            },
                        ) {
                            Text(stringResource(R.string.khatkit_secrets_revoke))
                        }
                    }
                }
            }
        }
    }
}

/** 本机设置：Hub 地址 / root / 下载并发 / 界面风格 / Shizuku / 所有文件访问。 */
@Composable
fun KhatKitSettingsDialog(
    controller: KhatKitController,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
    onToast: (String, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var hubUrl by remember { mutableStateOf(controller.hubBaseUrl) }
    var rootEnabled by remember { mutableStateOf(controller.enableRoot) }
    var concurrency by remember { mutableStateOf(controller.downloadConcurrency.toFloat()) }
    var style by remember { mutableStateOf(controller.uiStyle) }
    var shizukuAvailable by remember { mutableStateOf(ShizukuPermission.isAvailable()) }
    var shizukuGranted by remember { mutableStateOf(ShizukuPermission.isGranted()) }
    var allFilesGranted by remember { mutableStateOf(AllFilesAccess.isGranted()) }

    val noActivityError = stringResource(R.string.khatkit_toast_no_activity)
    val shizukuGrantedText = stringResource(R.string.khatkit_toast_shizuku_granted)
    val shizukuDeniedText = stringResource(R.string.khatkit_toast_shizuku_denied)
    val settingsSavedText = stringResource(R.string.khatkit_settings_saved)

    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.khatkit_settings_title),
        imageVector = Icons.Filled.Settings,
        confirmText = stringResource(R.string.khatkit_action_save),
        onConfirm = {
            controller.hubBaseUrl = hubUrl
            controller.enableRoot = rootEnabled
            controller.downloadConcurrency = concurrency.toInt()
            controller.uiStyle = style
            controller.applySettings()
            onToast(settingsSavedText, false)
            onApplied()
        },
        onDismiss = onDismiss,
    ) { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KedgeOutlinedTextField(
                value = hubUrl,
                onValueChange = { hubUrl = it },
                label = stringResource(R.string.khatkit_settings_hub_url),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SettingRow(stringResource(R.string.khatkit_settings_enable_root)) {
                KedgeSwitch(checked = rootEnabled, onCheckedChange = { rootEnabled = it })
            }

            Column {
                Text(stringResource(R.string.khatkit_settings_download_concurrency, concurrency.toInt()))
                KedgeSlider(
                    value = concurrency,
                    onValueChange = { concurrency = it },
                    valueRange = 1f..5f,
                    steps = 3,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.khatkit_settings_ui_style))
                KhatKitUiStyle.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = style == option,
                                role = Role.RadioButton,
                                onClick = { style = option },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KedgeRadioButton(
                            selected = style == option,
                            onClick = { style = option },
                        )
                        Text(
                            text = when (option) {
                                KhatKitUiStyle.MATERIAL -> stringResource(R.string.khatkit_style_material)
                                KhatKitUiStyle.MIUIX -> stringResource(R.string.khatkit_style_miuix)
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            SettingRow(
                when {
                    !shizukuAvailable -> stringResource(R.string.khatkit_shizuku_unavailable)
                    shizukuGranted -> stringResource(R.string.khatkit_shizuku_granted)
                    else -> stringResource(R.string.khatkit_shizuku_not_granted)
                }
            ) {
                if (shizukuAvailable && !shizukuGranted) {
                    KedgeTextButton(
                        onClick = {
                            val activity = context as? Activity
                            if (activity == null) {
                                onToast(noActivityError, true)
                                return@KedgeTextButton
                            }
                            ShizukuPermission.request(activity, SHIZUKU_REQUEST_CODE) { granted ->
                                shizukuGranted = granted
                                shizukuAvailable = ShizukuPermission.isAvailable()
                                onToast(if (granted) shizukuGrantedText else shizukuDeniedText, !granted)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.khatkit_action_grant))
                    }
                }
            }

            SettingRow(
                if (allFilesGranted) stringResource(R.string.khatkit_all_files_granted)
                else stringResource(R.string.khatkit_all_files_not_granted)
            ) {
                KedgeTextButton(
                    onClick = {
                        val activity = context as? Activity
                        if (activity == null) {
                            onToast(noActivityError, true)
                            return@KedgeTextButton
                        }
                        AllFilesAccess.request(activity)
                        allFilesGranted = AllFilesAccess.isGranted()
                    },
                ) {
                    Text(
                        if (allFilesGranted) stringResource(R.string.khatkit_action_refresh)
                        else stringResource(R.string.khatkit_action_grant)
                    )
                }
            }
        }

    }
}

@Composable
private fun SettingRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        trailing()
    }
}
