package heizige.kk.khatkit.app.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.oauth.AccountQuota
import heizige.kk.khatkit.app.core.data.oauth.AccountVendor
import heizige.kk.khatkit.app.core.data.oauth.ProviderAccountManager
import heizige.kk.khatkit.app.core.data.oauth.ProviderAccountState
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiButtonVariant
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.coroutines.launch
import ui.feice.mochi.theme.MochiTheme

/**
 * OA-4：账号类提供商（[ProviderSetting.Account]）的账号区，数据全部来自 [ProviderAccountManager]：
 *  - 登录 / 退出 / 续期都走 manager（它是凭据的唯一写入口，写回 Settings.providers 里对应 Account 的 credential）；
 *  - 账号态（email / 套餐 / 设备码 / 错误）读 manager.accounts[vendor]，与请求侧同一份凭据；
 *  - 额度条：[quota] 非 null 才画 —— ai 侧目前还没有 ProviderQuota/QuotaQueryRunner
 *    （ChatGPTProvider/GrokProvider 的 getBalance 仍是 TODO），所以调用点现在不传。
 */
@Composable
fun ProviderAccountSection(
    provider: ProviderSetting.Account,
    manager: ProviderAccountManager,
    modifier: Modifier = Modifier,
    quota: AccountQuota? = null,
) {
    val vendor = provider.vendor()
    val accounts by manager.accounts.collectAsStateWithLifecycle()
    val state = accounts[vendor] ?: ProviderAccountState(loggedIn = provider.credential != null)
    val scope = rememberCoroutineScope()

    CardGroup(modifier = modifier) {
        title { Text(stringResource(R.string.setting_account_section_title)) }
        item(
            headlineContent = {
                Text(
                    text = provider.name.ifBlank { vendor.label },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = accountSupportingText(state),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                MochiButton(
                    onClick = {
                        scope.launch {
                            if (state.loggedIn || provider.credential != null) {
                                manager.logout(vendor)
                            } else {
                                manager.login(vendor)
                                    .onFailure { error ->
                                        Toast.show(error.message ?: "登录失败", isError = true)
                                    }
                            }
                        }
                    },
                    variant = MochiButtonVariant.Secondary,
                    enabled = state.userCode == null,
                ) {
                    Text(
                        stringResource(
                            if (state.loggedIn || provider.credential != null) R.string.setting_account_logout
                            else R.string.setting_account_login
                        )
                    )
                }
            },
        )
    }

    if (quota != null) {
        AccountQuotaBar(
            quota = quota,
            modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

/** 账号行的副标题：设备码 > 已登录(email·plan) > 错误 > 未登录。 */
@Composable
private fun accountSupportingText(state: ProviderAccountState): String = when {
    state.userCode != null && state.verificationUri != null ->
        stringResource(R.string.setting_account_device_code, state.userCode, state.verificationUri)
    state.loggedIn -> listOfNotNull(state.email, state.plan).joinToString(" · ").ifBlank {
        stringResource(R.string.setting_account_logged_in)
    }
    state.error != null -> state.error
    else -> stringResource(R.string.setting_account_not_logged_in)
}

private fun ProviderSetting.Account.vendor(): AccountVendor = when (this) {
    is ProviderSetting.Grok -> AccountVendor.Grok
    else -> AccountVendor.ChatGPT
}

/** 订阅用量条：[quota] 的 fraction 决定填充宽度，颜色取主题 token（页面不写死色值）。 */
@Composable
fun AccountQuotaBar(
    quota: AccountQuota,
    modifier: Modifier = Modifier,
) {
    val colors = MochiTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = quota.windowLabel,
                style = MochiTheme.textStyles.footnote,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = quota.used.toString() + " / " + quota.limit.toString(),
                style = MochiTheme.textStyles.footnote,
                color = colors.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(quota.fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.primary),
            )
        }
    }
}