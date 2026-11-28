package heizige.kk.khatkit.app.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import heizige.kk.khatkit.ai.provider.BalanceOption
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.common.http.isJsonExprValid
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_PROVIDERS
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.feature.balance.helper.isUsageOnly
import heizige.kk.khatkit.app.feature.balance.helper.thresholdAmount
import ui.feice.mochi.basic.MochiListTextFieldRow
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection

private val ApiPathRegex = Regex("""^/[^ \t\n\r]*$""")

/**
 * 余额查询：一张分组卡片。开关打开后同一张卡片里展开接口路径 / 结果键 / 当前余额 / 恢复默认，
 * 关着时只有开关一行。
 *
 * @param balance 当前余额的显示（行尾的值）；null 不显示这一行
 */
@Composable
fun SettingProviderBalanceOption(
    provider: ProviderSetting,
    balanceOption: BalanceOption,
    modifier: Modifier = Modifier,
    balance: (@Composable () -> Unit)? = null,
    onEdit: (BalanceOption) -> Unit,
) {
    val pathInvalid = !balanceOption.apiPath.matches(ApiPathRegex)
    val keyInvalid = !isJsonExprValid(balanceOption.resultPath)
    val alerts = rememberAppEntryPoint().providerBalanceRepository().episodes
    fun update(option: BalanceOption) {
        if (!option.enabled || !option.lowBalanceAlert || option.isUsageOnly) alerts.enteredLow(provider.id, false)
        onEdit(option)
    }
    MochiListSection(
        modifier = modifier,
        header = stringResource(R.string.setting_provider_page_balance_title),
        footer = when {
            !balanceOption.enabled -> stringResource(R.string.setting_provider_page_balance_disabled_desc)
            pathInvalid -> stringResource(R.string.setting_provider_page_balance_api_path_invalid)
            keyInvalid -> stringResource(R.string.setting_provider_page_balance_json_key_invalid)
            balanceOption.isUsageOnly -> stringResource(R.string.setting_provider_page_balance_usage_only_desc)
            balanceOption.lowBalanceAlert && balanceOption.thresholdAmount() == null -> stringResource(R.string.setting_provider_page_balance_threshold_invalid)
            else -> null
        },
    ) {
        ProviderSwitchRow(
            title = stringResource(R.string.setting_provider_page_balance_info),
            checked = balanceOption.enabled,
            onCheckedChange = { update(balanceOption.copy(enabled = it)) },
        )
        if (balanceOption.enabled) {
            MochiListTextFieldRow(
                value = balanceOption.apiPath,
                onValueChange = { update(balanceOption.copy(apiPath = it)) },
                label = stringResource(R.string.setting_provider_page_balance_api_path),
                placeholder = "/credits",
            )
            MochiListTextFieldRow(
                value = balanceOption.resultPath,
                onValueChange = { update(balanceOption.copy(resultPath = it)) },
                label = stringResource(R.string.setting_provider_page_balance_json_key),
                placeholder = "data.total",
            )
            if (balance != null) {
                MochiListRow(title = stringResource(R.string.setting_provider_page_balance_current), trailing = balance)
            }
            if (balanceOption.isUsageOnly || keyInvalid) {
                MochiListRow(title = stringResource(R.string.setting_provider_page_low_balance_alert), subtitle = stringResource(R.string.setting_provider_page_low_balance_requires_key), enabled = false)
            } else {
                ProviderSwitchRow(
                    title = stringResource(R.string.setting_provider_page_low_balance_alert),
                    checked = balanceOption.lowBalanceAlert,
                    onCheckedChange = { update(balanceOption.copy(lowBalanceAlert = it)) },
                )
                if (balanceOption.lowBalanceAlert) {
                    MochiListTextFieldRow(
                        value = balanceOption.lowBalanceThreshold,
                        onValueChange = { update(balanceOption.copy(lowBalanceThreshold = it)) },
                        label = stringResource(R.string.setting_provider_page_low_balance_threshold),
                        placeholder = "5",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            MochiListButton(
                text = stringResource(R.string.setting_provider_page_restore_defaults),
                onClick = {
                    val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                    update(defaultProvider?.balanceOption?.copy() ?: BalanceOption())
                },
            )
        }
    }
}
