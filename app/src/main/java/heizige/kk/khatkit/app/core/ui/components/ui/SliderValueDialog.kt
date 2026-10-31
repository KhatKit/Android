package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.MochiTextFieldDefaults
import ui.feice.mochi.theme.MochiTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import heizige.kk.khatkit.uikit.mochi.kkAnimateContentSize


/**
 * 滑块行「点数值直接输入」的编辑器。
 *
 * 滑块只能拖，拖到精确值很难（尤其是 0–10000 这种长量程、或者 x1.5 这种一位小数）。
 * 所以滑块行右上角那个数值做成可点的：点开弹窗能直接输入具体数值，还能一键恢复默认。
 *
 * @param value 当前值（滑块内部单位，例如 0.45 表示 45%）
 * @param defaultValue 恢复默认写回的值；null = 这个设置没有默认值，弹窗里不显示「恢复默认」
 * @param displayScale 显示单位换算：百分比 100、dp/秒 1
 * @param prefix / suffix 显示单位的前后缀（秒速的 x、百分比的 %、圆角的 dp）
 * @param decimals 显示与输入允许的小数位（0 = 整数）
 * @param steps 滑块档位数，与 [heizige.kk.khatkit.uikit.mochi.KkSlider] 的 steps 同义：输入值吸附到最近一档
 * @param onValueChangeFinished 确认输入后调用（松手才落盘的设置靠它保存）
 */
@Immutable
data class SliderValueEditor(
    val value: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val defaultValue: Float? = null,
    val onValueChange: (Float) -> Unit,
    val onValueChangeFinished: (() -> Unit)? = null,
    val displayScale: Float = 1f,
    val prefix: String = "",
    val suffix: String = "",
    val decimals: Int = 0,
    val steps: Int = 0,
)

/** 滑块值的显示文本：按显示单位换算、定小数位，再拼前后缀。 */
fun sliderValueText(
    value: Float,
    displayScale: Float = 1f,
    decimals: Int = 0,
    prefix: String = "",
    suffix: String = "",
): String {
    val scaled = value * displayScale
    val number = if (decimals > 0) {
        String.format(Locale.ROOT, "%.${decimals}f", scaled)
    } else {
        scaled.roundToInt().toString()
    }
    return prefix + number + suffix
}

/** 吸附到最近的档位（与 MochiSlider 的 steps 语义一致：steps + 1 等分）。 */
private fun snapToSteps(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    val span = range.endInclusive - range.start
    if (steps <= 0 || span <= 0f) return value.coerceIn(range.start, range.endInclusive)
    val count = steps + 1
    val fraction = ((value - range.start) / span * count).roundToInt() / count.toFloat()
    return (range.start + fraction * span).coerceIn(range.start, range.endInclusive)
}

/**
 * 数值输入弹窗（照 Mochi 示例 app 的「对话框里的输入框」做）。
 *
 * 版面：
 *  1. 输入框直接用库里的 MochiTextField：48dp 胶囊、填充底、不描边，聚焦时右边淡入清除键；
 *     单位（% / dp / x）挂在行尾插槽里；[MochiTextField] 的 selectAllOnFocus 让打开即全选，
 *     直接敲数字就能覆盖旧值，不用先删。
 *  2. 输入框下面一行：左边取值范围、右边「恢复默认」（当前值已经是默认时收起，不留一个点了没反应的按钮）；
 *     输入非法或越界时，MochiTextField 自己抖一下 + 底色转警示色并给出错误说明。
 *  3. 底部两个按钮（取消 / 确定）由 MochiDialog 并排画，带按住融球的形变。
 */
@Composable
fun SliderValueInputDialog(
    visible: Boolean,
    title: String,
    editor: SliderValueEditor,
    onDismiss: () -> Unit,
) {
    val initial = remember(visible) {
        sliderValueText(
            value = editor.value,
            displayScale = editor.displayScale,
            decimals = editor.decimals,
        )
    }
    var text by remember(visible) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) {
            // 等弹窗的入场形变起手再抢焦点，键盘才不会把动画顶掉
            delay(140)
            focus.requestFocus()
        }
    }

    val unitText = editor.suffix.takeIf { it.isNotBlank() } ?: editor.prefix.takeIf { it.isNotBlank() }
    val rangeText = sliderValueText(
        value = editor.valueRange.start,
        displayScale = editor.displayScale,
        decimals = editor.decimals,
        prefix = editor.prefix,
        suffix = editor.suffix,
    ) + " – " + sliderValueText(
        value = editor.valueRange.endInclusive,
        displayScale = editor.displayScale,
        decimals = editor.decimals,
        prefix = editor.prefix,
        suffix = editor.suffix,
    )

    val parsed = text.trim().replace(',', '.').toFloatOrNull()
    val target = parsed?.let { it / editor.displayScale }
    val error = when {
        text.isBlank() -> null // 空着不算错，确认按钮自己置灰
        parsed == null -> "请输入数字"
        target == null || target < editor.valueRange.start || target > editor.valueRange.endInclusive -> "范围 " + rangeText
        else -> null
    }
    val canConfirm = parsed != null && target != null &&
        target >= editor.valueRange.start && target <= editor.valueRange.endInclusive

    fun apply(raw: Float) {
        editor.onValueChange(snapToSteps(raw, editor.valueRange, editor.steps))
        editor.onValueChangeFinished?.invoke()
        onDismiss()
    }

    val atDefault = editor.defaultValue?.let { abs(it - editor.value) < 1e-4f } ?: true

    MochiDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title,
        actions = persistentListOf(
            MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "确定",
                role = MochiDialogActionRole.Primary,
                enabled = canConfirm,
                onClick = { target?.let(::apply) },
            ),
        ),
        dismissOnOutsideTap = true,
    ) {
        Column(
            modifier = Modifier.kkAnimateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MochiTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { it.isDigit() || it == '.' || it == '-' || it == ',' }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                placeholder = "输入数值",
                singleLine = true,
                error = error,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (editor.decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                ),
                textStyle = MochiTheme.textStyles.body,
                colors = MochiTextFieldDefaults.colors(),
                // 打开就全选：直接敲数字覆盖旧值（Safari 地址栏那种行为，库内实现）
                selectAllOnFocus = true,
                trailing = unitText?.let { unit ->
                    {
                        Text(
                            text = unit,
                            style = MochiTheme.textStyles.body,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (error == null) {
                    Text(
                        text = rangeText,
                        style = MochiTheme.textStyles.footnote,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (editor.defaultValue != null && !atDefault) {
                    Text(
                        text = "恢复默认",
                        style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.Medium),
                        color = MochiTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MochiTheme.shapes.full)
                            .clickable { editor.defaultValue?.let(::apply) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
