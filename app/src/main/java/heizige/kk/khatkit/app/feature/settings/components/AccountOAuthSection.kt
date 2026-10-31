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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.oauth.AccountLoginState
import heizige.kk.khatkit.app.core.data.oauth.AccountOAuthSession
import heizige.kk.khatkit.app.core.data.oauth.AccountQuota
import heizige.kk.khatkit.app.core.data.oauth.AccountVendor
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiButtonVariant
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.coroutines.launch
import ui.feice.mochi.theme.MochiTheme

/**
 * UI-6：订阅账号区（ChatGPT / Grok）。
 *
 * - 登录 / 退出：ChatGPT 走 PKCE + 浏览器回环回调，Grok 走设备码（[AccountOAuthSession]）；
 * - 已登录显示 email + 计划名（ChatGPT 用 [heizige.kk.khatkit.app.core.data.oauth.ChatGPTAuth.planDisplayName]）；
 * - 等待授权时显示设备码与设备码页面（Grok）；
 * - 额度条：[quota] 非 null 时画进度条 —— 本仓 ai 侧还没有 ProviderQuota/QuotaQueryRunner（见报告），
 *   所以当前调用点传 null，等 AI-1 把用量接进来后只需把数据填进来，UI 不用改。
 */
@Composable
fun AccountOAuthSection(
    modifier: Modifier = Modifier,
    quota: AccountQuota? = null,
    session: AccountOAuthSession? = null,
) {
    val context = LocalContext.current
    val entryPoint = rememberAppEntryPoint()
    val httpClient = remember(entryPoint) { entryPoint.okHttpClient() }
    // 注意：remember 不能在 `?:` 里（条件调用会破坏 slot 表），先 remember 再回退到外部传入的实例。
    val rememberedSession = remember(httpClient, context) { AccountOAuthSession(context, httpClient) }
    val accountSession = session ?: rememberedSession
    val states by accountSession.states.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    CardGroup(
        modifier = modifier,
        title = { Text(stringResource(R.string.setting_account_section_title)) },
    ) {
        AccountVendor.entries.forEach { vendor ->
            val state = states[vendor] ?: AccountLoginState.Idle
            val loggedIn = state is AccountLoginState.LoggedIn
            item(
                headlineContent = {
                    Text(
                        text = vendor.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = supportingText(vendor, state, accountSession.planLabel(vendor)),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    MochiButton(
                        onClick = {
                            if (loggedIn) {
                                accountSession.logout(vendor)
                            } else {
                                scope.launch {
                                    val result = when (vendor) {
                                        AccountVendor.ChatGPT -> accountSession.loginChatGpt()
                                        AccountVendor.Grok -> accountSession.loginGrok()
                                    }
                                    result.onFailure { error ->
                                        Toast.show(error.message ?: "登录失败", isError = true)
                                    }
                                }
                            }
                        },
                        variant = MochiButtonVariant.Secondary,
                        enabled = state !is AccountLoginState.Waiting,
                    ) {
                        Text(
                            stringResource(
                                if (loggedIn) R.string.setting_account_logout else R.string.setting_account_login
                            )
                        )
                    }
                },
            )
        }
    }

    if (quota != null) {
        AccountQuotaBar(
            quota = quota,
            modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

/** 账号行的副标题：已登录给 email / 计划，等待授权给设备码，失败给错误。 */
@Composable
private fun supportingText(
    vendor: AccountVendor,
    state: AccountLoginState,
    planLabel: String?,
): String = when (state) {
    is AccountLoginState.LoggedIn -> {
        val email = state.credential.email ?: state.credential.accountId
        listOfNotNull(email, planLabel).joinToString(" · ").ifBlank {
            stringResource(R.string.setting_account_logged_in)
        }
    }

    is AccountLoginState.Waiting -> when {
        state.userCode != null && state.verificationUri != null ->
            stringResource(R.string.setting_account_device_code, state.userCode, state.verificationUri)

        state.verificationUri != null ->
            stringResource(R.string.setting_account_waiting_browser)

        else -> stringResource(R.string.setting_account_waiting)
    }

    is AccountLoginState.Failed -> state.message
    AccountLoginState.Idle -> stringResource(R.string.setting_account_not_logged_in)
}

/**
 * 订阅用量条。[quota] 的 fraction 决定填充宽度；数字用「已用 / 上限 + 窗口说明」。
 * 颜色取主题 token，不在页面里写死色值。
 */
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
                text = "${quota.used} / ${quota.limit}",
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
