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
import heizige.kk.khatkit.app.feature.balance.data.isUsageOnly
import heizige.kk.khatkit.app.feature.balance.data.thresholdAmount
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
        header = "余额",
        footer = when {
            !balanceOption.enabled -> "在提供商详情和模型选择器显示余额"
            pathInvalid -> "API 路径要以 / 开头且不能有空格"
            keyInvalid -> "结果 JSON 键的表达式无效"
            balanceOption.isUsageOnly -> "当前结果是累计用量，不能用于低余额告警"
            balanceOption.lowBalanceAlert && balanceOption.thresholdAmount() == null -> "阈值须为大于 0 的数字；当前不会触发告警"
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
                label = "API 路径",
                placeholder = "/credits",
            )
            MochiListTextFieldRow(
                value = balanceOption.resultPath,
                onValueChange = { update(balanceOption.copy(resultPath = it)) },
                label = "结果键",
                placeholder = "data.total",
            )
            if (balance != null) {
                MochiListRow(title = "当前余额", trailing = balance)
            }
            if (balanceOption.isUsageOnly || keyInvalid) {
                MochiListRow(title = "低余额告警", subtitle = "需要有效的剩余额度结果键", enabled = false)
            } else {
                ProviderSwitchRow(
                    title = "低余额告警",
                    checked = balanceOption.lowBalanceAlert,
                    onCheckedChange = { update(balanceOption.copy(lowBalanceAlert = it)) },
                )
                if (balanceOption.lowBalanceAlert) {
                    MochiListTextFieldRow(
                        value = balanceOption.lowBalanceThreshold,
                        onValueChange = { update(balanceOption.copy(lowBalanceThreshold = it)) },
                        label = "低于此值提醒",
                        placeholder = "5",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            MochiListButton(
                text = "恢复默认",
                onClick = {
                    val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                    update(defaultProvider?.balanceOption?.copy() ?: BalanceOption())
                },
            )
        }
    }
}
