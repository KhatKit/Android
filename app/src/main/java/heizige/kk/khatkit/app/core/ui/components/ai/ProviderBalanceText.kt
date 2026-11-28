package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.ui.platform.LocalResources
import heizige.kk.khatkit.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.app.core.util.toDp
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.icons.utility.payments
import heizige.kk.khatkit.app.core.ui.icons.utility.sync
import heizige.kk.khatkit.app.core.data.model.BalanceReading
import heizige.kk.khatkit.app.core.util.isLow
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.Toast
import kotlinx.coroutines.launch
import ui.feice.mochi.theme.MochiTheme

@Composable
fun ProviderBalanceText(
    providerSetting: ProviderSetting,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    showRefresh: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = LocalResources.current
    if (!providerSetting.balanceOption.enabled || providerSetting !is ProviderSetting.OpenAI) return
    val entry = rememberAppEntryPoint()
    val repository = entry.providerBalanceRepository()
    val query = repository.query(providerSetting)
    // The editor auto-saves each keystroke. Never send credentials to a half-typed endpoint.
    val initialQuery = remember(providerSetting.id) { query }
    key(query) {
        val scope = rememberCoroutineScope()
        val reading by query.reading.collectAsStateWithLifecycle()
        suspend fun refresh(force: Boolean) = query.refresh(force) {
            entry.providerManager().getProviderByType(providerSetting).getBalance(providerSetting)
        }
        LaunchedEffect(query) { if (!showRefresh || query === initialQuery) refresh(false) }
        val raw = (reading as? BalanceReading.Value)?.raw
        val low = providerSetting.balanceOption.isLow(raw)
        LaunchedEffect(low) {
            if (repository.episodes.enteredLow(providerSetting.id, low)) {
                Toast.show(resources.getString(R.string.provider_balance_low_toast, providerSetting.name, raw), isError = true)
            }
        }
        val tint = if (low == true) MochiTheme.colorScheme.error else color.takeOrElse { LocalContentColor.current }
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(payments, null, Modifier.size(style.fontSize.toDp()), tint = tint)
            Text(
                text = when {
                    low == true -> stringResource(R.string.provider_balance_low, raw.orEmpty())
                    raw != null -> raw
                    reading == BalanceReading.NotQueried -> stringResource(R.string.provider_balance_pending)
                    reading == BalanceReading.Loading -> "…"
                    else -> stringResource(R.string.provider_balance_unavailable)
                },
                modifier = Modifier.weight(1f, fill = false),
                style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, color = tint,
            )
            if (showRefresh) {
                MochiIconButton(onClick = { scope.launch { refresh(true) } }, enabled = reading != BalanceReading.Loading) {
                    Icon(sync, contentDescription = stringResource(R.string.provider_balance_refresh))
                }
            }
        }
    }
}
