package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.network.toProxyOrNull
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.kedge.components.KedgeTextField
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网络偏好页的 Miuix 风格版。
 *
 * UA 输入框用 Kedge 的双风格 [KedgeTextField]，
 * 代理配置沿用原有弹窗（内部是 M3 输入框，Miuix 无等价的多行错误态表单）。
 */
@Composable
fun SettingPreferencesNetworkPageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val proxy = rememberProxyTestState()

    var userAgent by remember(settings.networkSetting.userAgent) {
        mutableStateOf(settings.networkSetting.userAgent)
    }
    var proxyDialogVisible by remember { mutableStateOf(false) }
    val defaultUserAgent = "KhatKit-Android/${BuildConfig.VERSION_NAME}"

    fun updateUserAgent(value: String) {
        userAgent = value
        vm.updateSettings(
            settings.copy(networkSetting = settings.networkSetting.copy(userAgent = value))
        )
    }

    if (proxyDialogVisible) {
        AppAlertDialog(
            onDismissRequest = { proxyDialogVisible = false },
            title = { Text(stringResource(R.string.setting_page_preferences_network_proxy)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KedgeTextField(
                        value = proxy.draftUrl,
                        onValueChange = proxy.setDraftUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.setting_page_preferences_network_proxy),
                        placeholder = "http://127.0.0.1:7890",
                        isError = proxy.draftUrl.isNotBlank() && proxy.draftUrl.toProxyOrNull() == null,
                        singleLine = true,
                    )
                    KedgeTextField(
                        value = proxy.draftUsername,
                        onValueChange = proxy.setDraftUsername,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.backup_page_username),
                        singleLine = true,
                    )
                    KedgeTextField(
                        value = proxy.draftPassword,
                        onValueChange = proxy.setDraftPassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.backup_page_password),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    KedgeTextButton(
                        onClick = proxy::resetDraft,
                        enabled = proxy.draftDirty,
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            },
            confirmButton = {
                KedgeTextButton(
                    onClick = {
                        val (url, user, pass) = proxy.commit()
                        vm.updateSettings(
                            settings.copy(
                                networkSetting = settings.networkSetting.copy(
                                    proxyUrl = url,
                                    proxyUsername = user,
                                    proxyPassword = pass,
                                )
                            )
                        )
                        proxyDialogVisible = false
                    },
                    enabled = proxy.canSave(),
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )
    }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_page_preferences_network),
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceSwitch(
                title = stringResource(R.string.setting_page_preferences_network_auto_retry),
                summary = stringResource(R.string.setting_page_preferences_network_auto_retry_desc),
                checked = settings.networkSetting.enableAutoRetry,
                onCheckedChange = { enabled ->
                    vm.updateSettings(
                        settings.copy(
                            networkSetting = settings.networkSetting.copy(enableAutoRetry = enabled)
                        )
                    )
                },
            )
        }

        miuixGroup {
            KedgeTextField(
                label = stringResource(R.string.setting_page_preferences_network_user_agent),
                value = userAgent,
                onValueChange = ::updateUserAgent,
                placeholder = defaultUserAgent,
                supportingText = stringResource(
                    R.string.setting_page_preferences_network_user_agent_desc,
                    defaultUserAgent,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KedgeTextButton(
                onClick = { updateUserAgent("") },
                enabled = userAgent.isNotEmpty(),
            ) {
                Text(stringResource(R.string.setting_model_page_reset_to_default))
            }
        }

        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_page_config),
                summary = if (proxy.proxyUrl.isBlank()) {
                    stringResource(R.string.setting_page_preferences_network_proxy_desc)
                } else {
                    proxy.proxyUrl
                },
                onClick = {
                    proxy.beginEdit()
                    proxyDialogVisible = true
                },
                startAction = {
                    Icon(
                        imageVector = arrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
            PreferenceArrow(
                title = stringResource(R.string.setting_provider_page_test_connection),
                enabled = !proxy.testing,
                onClick = { proxy.startTest() },
                endAction = {
                    if (proxy.testing) {
                        KedgeProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.setting_provider_page_test))
                    }
                },
            )
        }
    }
}
