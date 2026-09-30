package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import heizige.kk.kedge.components.KedgeCheckbox
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ListSelectableItem(
    key: Any,
    selectedKeys: List<Any>,
    onSelectChange: (Any) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (enabled) {
            KedgeCheckbox(
                checked = key in selectedKeys,
                onCheckedChange = {
                    onSelectChange(key)
                }
            )
        }
        Box(
            modifier = Modifier.weight(1f)
        ) {
            content()
        }
    }
}
