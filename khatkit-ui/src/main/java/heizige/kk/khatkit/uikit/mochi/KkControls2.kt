package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** U4f2：分段行的条目类型（替代 Khromia 的 SegmentedItem，字段一一对应）。 */
data class SegmentedItem(
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
    val leadingIcon: ImageVector? = null,
    val enabled: Boolean = true,
)

/** 开关：material3 Switch（自带 thumb 平移动画）。 */
@Composable
fun KkOptionSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}

/** 分组小标题（替代 Khromia OptionsText）。 */
@Composable
fun KkOptionsText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** 普通说明文字（替代 Khromia OptionText）。 */
@Composable
fun KkOptionText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

/** 带值的滑块：material3 Slider 底座。 */
@Composable
fun KkFancySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/** 单选行：RadioButton + 文本/副标题。 */
@Composable
fun KkAnimatedRadioItem(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selected: Boolean = isSelected,
    subtitle: String? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: androidx.compose.ui.graphics.Shape? = null,
    @Suppress("UNUSED_PARAMETER") selectedColor: Color? = null,
    @Suppress("UNUSED_PARAMETER") selectedBackground: Color? = null,
    @Suppress("UNUSED_PARAMETER") unselectedBackground: Color? = null,
    @Suppress("UNUSED_PARAMETER") checkIconTint: Color? = null,
    @Suppress("UNUSED_PARAMETER") textColor: Color? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp2()),
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        androidx.compose.foundation.layout.Column {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun KkAnimatedRadioButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selected: Boolean = isSelected,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: androidx.compose.ui.graphics.Shape? = null,
    @Suppress("UNUSED_PARAMETER") selectedBackground: Color? = null,
    @Suppress("UNUSED_PARAMETER") unselectedBackground: Color? = null,
    @Suppress("UNUSED_PARAMETER") checkIconTint: Color? = null,
) {
    RadioButton(selected = selected, onClick = onClick, modifier = modifier, enabled = enabled)
}

/** 与主色协调：按比例把当前色向 primary 混合（替代 Khromia 的 harmonizeWithPrimary）。 */
@Composable
fun Color.harmonizeWithPrimary(
    primary: Color = MaterialTheme.colorScheme.primary,
    amount: Float = 0.2f,
): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red * (1 - a) + primary.red * a,
        green = green * (1 - a) + primary.green * a,
        blue = blue * (1 - a) + primary.blue * a,
        alpha = alpha,
    )
}

/** 颜色混合（替代 Khromia 的 blend）。 */
fun Color.blend(other: Color, amount: Float): Color = other.copy(
    alpha = 1f,
).let {
    val a = amount.coerceIn(0f, 1f)
    Color(
        red = red * (1 - a) + it.red * a,
        green = green * (1 - a) + it.green * a,
        blue = blue * (1 - a) + it.blue * a,
        alpha = alpha,
    )
}

private fun Int.dp2() = androidx.compose.ui.unit.Dp(this.toFloat())
