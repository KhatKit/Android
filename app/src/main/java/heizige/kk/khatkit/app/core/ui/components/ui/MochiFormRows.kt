package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import heizige.kk.khatkit.uikit.mochi.KkSwitch

/** Mochi form row for a setting that is represented by a switch. */
@Composable
fun MochiSwitchFormRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit = {},
    description: (@Composable (() -> Unit))? = null,
) {
    FormItem(
        modifier = modifier,
        label = label,
        description = description,
        tail = {
            KkSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}
