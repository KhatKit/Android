package heizige.kk.khatkit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.hub.HubAccountStatus
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceInfo
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.miuixGroup
import heizige.kk.khatkit.app.core.ui.icons.bolt
import heizige.kk.khatkit.app.core.ui.icons.cleaningServices
import heizige.kk.khatkit.app.core.ui.icons.favorite
import heizige.kk.khatkit.app.core.ui.icons.openInNew
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 套餐 / 激活页的 Miuix 风格版。
 *
 * 状态展示用 [PreferenceInfo]，可点击项用 [PreferenceArrow]；
 * 账户状态文案全部走 stringResource（原 MD3 版是硬编码中文）。
 */
@Composable
fun SettingPackagePageMiuix(vm: SettingViewModel = hiltViewModel()) {
    val uriHandler = LocalUriHandler.current
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var status by remember { mutableStateOf<HubAccountStatus?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun refresh(refreshQuota: Boolean = false) {
        scope.launch {
            busy = true
            status = provider.hubAccountStatus(refresh = refreshQuota)
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh(refreshQuota = false) }

    val activated = status?.activated == true
    val current = status

    MiuixSettingsPage(
        title = stringResource(R.string.setting_package_page_title),
        bottomInnerPadding = PageMetrics.BottomContentPadding,
        navigationIcon = { BackButton() },
    ) {
        miuixGroup {
            PreferenceArrow(
                title = stringResource(R.string.setting_package_page_mall),
                summary = stringResource(R.string.setting_package_page_mall_desc),
                icon = openInNew,
                onClick = { uriHandler.openUri("https://heizige.top/khatkit/index.html") },
            )
        }

        miuixGroup {
            PreferenceInfo(
                title = when {
                    !activated -> stringResource(R.string.setting_package_page_inactive)
                    !status?.plan.isNullOrBlank() -> stringResource(
                        R.string.setting_package_page_active_with_plan,
                        status?.plan.orEmpty(),
                    )
                    else -> stringResource(R.string.setting_package_page_active)
                },
                summary = buildString {
                    append(
                        stringResource(
                            R.string.setting_package_page_device,
                            current?.deviceName ?: stringResource(R.string.setting_package_page_loading),
                        )
                    )
                    current?.deviceId?.takeIf { it.isNotBlank() }?.let {
                        append('\n').append(stringResource(R.string.setting_package_page_device_id, it))
                    }
                    current?.message?.takeIf { it.isNotBlank() }?.let {
                        append('\n').append(it)
                    }
                },
                icon = favorite,
            )
            PreferenceInfo(
                title = stringResource(R.string.setting_package_page_ai_quota),
                summary = formatQuotaMiuix(current?.aiTokensRemaining),
            )
            PreferenceInfo(
                title = stringResource(R.string.setting_package_page_tool_quota),
                summary = formatQuotaMiuix(current?.toolCallsRemaining),
            )
            PreferenceInfo(
                title = stringResource(R.string.setting_package_page_expires),
                summary = current?.expiresAt?.let { timeFormat.format(Date(it)) }
                    ?: stringResource(R.string.setting_package_page_unknown),
            )
            PreferenceArrow(
                title = if (busy) {
                    stringResource(R.string.setting_package_page_refreshing)
                } else {
                    stringResource(R.string.setting_package_page_refresh)
                },
                summary = stringResource(R.string.setting_package_page_refresh_desc),
                icon = bolt,
                enabled = !busy,
                onClick = { refresh(refreshQuota = true) },
            )
            if (activated) {
                PreferenceArrow(
                    title = stringResource(R.string.setting_package_page_clear),
                    summary = stringResource(R.string.setting_package_page_clear_desc),
                    icon = cleaningServices,
                    onClick = {
                        scope.launch {
                            busy = true
                            status = provider.clearHubActivation()
                            busy = false
                        }
                    },
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.setting_package_page_tip),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

private fun formatQuotaMiuix(value: Long?): String = value?.toString() ?: "—"
