package heizige.kk.khatkit.app.feature.settings.components

import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ai.provider.BalanceOption
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.common.http.isJsonExprValid
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.uikit.mochi.KkCard
import heizige.kk.khatkit.uikit.mochi.KkChevron
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import ui.feice.mochi.list.MochiListDefaults
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.data.datastore.DEFAULT_PROVIDERS
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import heizige.kk.khatkit.app.core.ui.icons.sync
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle

private val ApiPathRegex = Regex("""^/[^ \t\n\r]*$""")

@Composable
fun SettingProviderBalanceOption(
    provider: ProviderSetting,
    balanceOption: BalanceOption,
    modifier: Modifier = Modifier,
    onEdit: (BalanceOption) -> Unit,
) {
    var expand by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expand) -90f else 90f,
        label = "balanceChevron",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Mochi 列表行：整行点开/收起，开关就是 Mochi 的 Filled 开关（不再用 MD3 Checkbox）
        MochiOptionItem(
            onClick = { expand = !expand },
            shape = listCardStyle().indexedShape(0, 1),
            backgroundColor = MochiListDefaults.colors().container,
            titleContent = {
                Text(stringResource(R.string.setting_provider_page_balance_info))
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KkSwitch(
                        checked = balanceOption.enabled,
                        onCheckedChange = { onEdit(balanceOption.copy(enabled = it)) },
                    )
                    KkChevron(
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            },
        )
        AnimatedVisibility(visible = expand) {
            KkCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Md3OutlinedTextFieldWithSlots(
                        value = balanceOption.apiPath,
                        onValueChange = { onEdit(balanceOption.copy(apiPath = it)) },
                        label = { Text(stringResource(R.string.setting_provider_page_balance_api_path)) },
                        isError = !balanceOption.apiPath.matches(ApiPathRegex),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Md3OutlinedTextFieldWithSlots(
                        value = balanceOption.resultPath,
                        onValueChange = { onEdit(balanceOption.copy(resultPath = it)) },
                        label = { Text(stringResource(R.string.setting_provider_page_balance_json_key)) },
                        isError = !isJsonExprValid(balanceOption.resultPath),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(16.dp)
                    )
                    MochiTextButton(
                        onClick = {
                            val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                            if (defaultProvider != null) {
                                onEdit(defaultProvider.balanceOption.copy())
                            } else {
                                onEdit(BalanceOption())
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(sync, null)
                        Spacer(Modifier.size(6.dp))
                        Text("恢复默认")
                    }
                }
            }
        }
    }
}

