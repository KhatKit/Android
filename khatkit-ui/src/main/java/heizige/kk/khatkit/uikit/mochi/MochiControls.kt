package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.Text
import ui.feice.mochi.theme.MochiTheme

/**
 * P4b：Kedge 按钮/标签/表单族的 Mochi 版。
 *
 * 与 P4a 同策略：参数名沿用旧 Kedge 组件，调用点只改名字；Miuix/MD3 专有参数
 * （colors/shapes/variant 等）只收不用 —— 配色与形状全部来自 MochiTheme。
 *
 * 语义重定（合同 §5，Mochi 无独立 checkbox/radio）：
 *  - [MochiSwitch]：开关，保持「即时生效」语义；
 *  - [MochiCheckbox] / [MochiRadioButton]：列表行内的**选中标记**（勾/圆点），
 *    不做成开关 —— 点一下立即生效的设置项应改用开关，单选列表应改用
 *    MochiListPickerRow（P6 提供）；这里保留标记形态是为了不改变现有交互语义。
 */

enum class MochiButtonVariant { Primary, Secondary }
enum class MochiIconButtonVariant { Standard, Tonal, Filled }
enum class MochiStatusLevel { Info, Success, Warning, Error }

object MochiButtonDefaults {
    val ContentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
}

@Composable
fun MochiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: MochiButtonVariant = MochiButtonVariant.Primary,
    /** 旧 Miuix 配色：只收不用。 */
    miuixColors: Any? = null,
    contentPadding: PaddingValues = MochiButtonDefaults.ContentPadding,
    interactionSource: Any? = null,
    shapes: Any? = null,
    colors: Any? = null,
    miuixCornerRadius: Dp = 24.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MochiTheme.colorScheme
    Row(
        modifier = modifier
            .background(
                if (variant == MochiButtonVariant.Primary) scheme.primary else scheme.secondaryContainer,
                MochiTheme.shapes.full,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                if (variant == MochiButtonVariant.Primary) scheme.onPrimary else scheme.onSecondaryContainer,
        ) { content() }
    }
}

@Composable
fun MochiTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    interactionSource: Any? = null,
    shapes: Any? = null,
    colors: Any? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MochiTheme.colorScheme.primary,
        ) { content() }
    }
}

@Composable
fun MochiIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    variant: MochiIconButtonVariant = MochiIconButtonVariant.Standard,
    shapes: Any? = null,
    colors: Any? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MochiTheme.colorScheme
    val background = when (variant) {
        MochiIconButtonVariant.Standard -> Color.Transparent
        MochiIconButtonVariant.Tonal -> scheme.secondaryContainer
        MochiIconButtonVariant.Filled -> scheme.primary
    }
    val tint = when (variant) {
        MochiIconButtonVariant.Standard -> scheme.onSurface
        MochiIconButtonVariant.Tonal -> scheme.onSecondaryContainer
        MochiIconButtonVariant.Filled -> scheme.onPrimary
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .background(background, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) scheme.primary else tint,
        ) { content() }
    }
}

@Composable
private fun MochiFieldFrame(
    shape: Shape,
    enabled: Boolean,
    isError: Boolean,
    label: String?,
    supportingText: String?,
    content: @Composable () -> Unit,
) {
    val scheme = MochiTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        label?.let {
            Text(text = it, color = scheme.onSurfaceVariant, style = MochiTheme.textStyles.footnote)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainer, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) { content() }
        supportingText?.let {
            Text(
                text = it,
                color = if (isError) scheme.error else scheme.onSurfaceVariant,
                style = MochiTheme.textStyles.footnote,
            )
        }
    }
}

@Composable
private fun MochiBasicField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    readOnly: Boolean,
    singleLine: Boolean,
    maxLines: Int,
    placeholder: String?,
    isError: Boolean,
    label: String?,
    supportingText: String?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    textStyle: TextStyle?,
    shape: Shape,
) {
    val scheme = MochiTheme.colorScheme
    MochiFieldFrame(shape = shape, enabled = enabled, isError = isError, label = label, supportingText = supportingText) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leadingIcon?.invoke()
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                maxLines = maxLines,
                cursorBrush = SolidColor(scheme.primary),
                textStyle = (textStyle ?: MochiTheme.textStyles.body).copy(color = scheme.onSurface),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, color = scheme.onSurfaceVariant, style = MochiTheme.textStyles.body)
                    }
                    inner()
                },
            )
            trailingIcon?.invoke()
        }
    }
}

@Composable
fun MochiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: Any? = null,
    shape: Shape = MochiTheme.shapes.medium,
    textStyle: TextStyle? = null,
    colors: Any? = null,
) {
    MochiBasicField(
        value, onValueChange, enabled, readOnly, singleLine, maxLines, placeholder, isError, label,
        supportingText, leadingIcon, trailingIcon, textStyle, shape,
    )
}

@Composable
fun MochiOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: Any? = null,
    shape: Shape = MochiTheme.shapes.medium,
    textStyle: TextStyle? = null,
    colors: Any? = null,
) {
    MochiBasicField(
        value, onValueChange, enabled, readOnly, singleLine, maxLines, placeholder, isError, label,
        supportingText, leadingIcon, trailingIcon, textStyle, shape,
    )
}

/** 开关（即时生效）。 */
@Composable
fun MochiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MochiTheme.colorScheme
    Box(
        modifier = modifier
            .width(48.dp)
            .height(28.dp)
            .background(
                if (checked) scheme.primary else scheme.surfaceContainerHighest,
                MochiTheme.shapes.full,
            )
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(22.dp)
                .background(if (checked) scheme.onPrimary else scheme.onSurfaceVariant, CircleShape),
        )
    }
}

/** 列表行内的选中标记（不是开关；语义见文件头注释）。 */
@Composable
fun MochiCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MochiTheme.colorScheme
    Box(
        modifier = modifier
            .size(22.dp)
            .background(if (checked) scheme.primary else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.5.dp, if (checked) scheme.primary else scheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text(text = "✓", color = scheme.onPrimary, style = MochiTheme.textStyles.footnote)
    }
}

/** 单选标记（不是开关；语义见文件头注释）。 */
@Composable
fun MochiRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MochiTheme.colorScheme
    Box(
        modifier = modifier
            .size(22.dp)
            .border(1.5.dp, if (selected) scheme.primary else scheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(modifier = Modifier.size(11.dp).background(scheme.primary, CircleShape))
        }
    }
}

/** 滑杆：Mochi 圆角轨道 + 拖动/点击改值（不引 material3 Slider）。 */
@Composable
fun MochiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val scheme = MochiTheme.colorScheme
    val current = rememberUpdatedState(value)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val fraction = ((current.value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(scheme.surfaceContainerHighest, MochiTheme.shapes.full))
        Box(modifier = Modifier.fillMaxWidth(fraction).height(4.dp).background(scheme.primary, MochiTheme.shapes.full))
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(32.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(modifier = Modifier.size(20.dp).background(scheme.primary, CircleShape))
        }
    }
}

/** 选项行（列表里的可点行 + 前导/标题/支持文本/尾随槽）。 */
@Composable
fun MochiOptionItem(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color.Unspecified,
    leadingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    titleContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (backgroundColor == Color.Unspecified) MochiTheme.colorScheme.surfaceContainer else backgroundColor,
                shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingContent?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            overlineContent?.invoke()
            titleContent()
            supportingContent?.invoke()
        }
        trailingContent?.invoke()
    }
}

/** 可展开选项行：标题行 + 展开内容。 */
@Composable
fun MochiExpandableOptionItem(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    titleContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
    contentVisible: Boolean = expanded,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
    contentBackgroundColor: Color = Color.Unspecified,
    /** 旧 Kedge 参数：展开内容是否与标题行同卡（Mochi 侧内容总在同一列内，语义保留）。 */
    wrapContent: Boolean = false,
    /** 展开内容（旧 Kedge 用 expandedContent 命名）。 */
    expandedContent: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MochiOptionItem(
            onClick = {
                onClick?.invoke()
                onExpandedChange(!expanded)
            },
            modifier = modifier,
            shape = shape,
            backgroundColor = backgroundColor,
            leadingContent = leadingContent,
            overlineContent = overlineContent,
            titleContent = titleContent,
            supportingContent = supportingContent,
            trailingContent = trailingContent,
        )
        if (contentVisible) {
            Box(modifier = Modifier.padding(contentPadding)) {
                expandedContent?.invoke()
                content?.invoke()
            }
        }
    }
}

/** 状态标签（Info/Success/Warning/Error）。 */
@Composable
fun MochiStatusTag(
    label: String,
    modifier: Modifier = Modifier,
    level: MochiStatusLevel = MochiStatusLevel.Info,
) {
    val scheme = MochiTheme.colorScheme
    val (bg, fg) = when (level) {
        MochiStatusLevel.Info -> scheme.secondaryContainer to scheme.onSecondaryContainer
        MochiStatusLevel.Success -> scheme.primaryContainer to scheme.onPrimaryContainer
        MochiStatusLevel.Warning -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        MochiStatusLevel.Error -> scheme.errorContainer to scheme.onErrorContainer
    }
    Text(
        text = label,
        color = fg,
        style = MochiTheme.textStyles.footnote,
        modifier = modifier
            .background(bg, MochiTheme.shapes.full)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 徽标底（角标/计数的容器）。 */
@Composable
fun MochiBadge(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MochiTheme.colorScheme
    Row(
        modifier = modifier
            .background(containerColor ?: scheme.error, MochiTheme.shapes.full)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides (contentColor ?: scheme.onError),
        ) { content?.invoke(this) }
    }
}

/** 带角标的内容（右上角叠一个 badge）。 */
@Composable
fun MochiBadgedBox(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        Box(modifier = Modifier.align(Alignment.TopEnd)) { badge() }
    }
}
/** 带 composable 槽位的文本框（label/placeholder/supportingText 都是槽）。 */
@Composable
fun MochiOutlinedTextFieldWithSlots(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: Any? = null,
    shape: Shape? = null,
    textStyle: TextStyle? = null,
    colors: Any? = null,
) {
    val scheme = MochiTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        label?.invoke()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainer, shape ?: MochiTheme.shapes.medium)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                leadingIcon?.invoke()
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    cursorBrush = SolidColor(scheme.primary),
                    textStyle = (textStyle ?: MochiTheme.textStyles.body).copy(color = scheme.onSurface),
                    decorationBox = { inner -> placeholder?.invoke(); inner() },
                )
                trailingIcon?.invoke()
            }
        }
        supportingText?.invoke()
    }
}

/** 由 TextFieldState 驱动的文本框（新的 foundation state API）。 */
@Composable
fun MochiTextFieldWithState(
    state: androidx.compose.foundation.text.input.TextFieldState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    lineLimits: androidx.compose.foundation.text.input.TextFieldLineLimits? = null,
    placeholder: String? = null,
    textStyle: TextStyle? = null,
    shape: Shape? = null,
    colors: Any? = null,
) {
    val scheme = MochiTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, shape ?: MochiTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = state.text.toString(),
            onValueChange = { newText ->
                state.edit {
                    replace(0, length, newText)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = (lineLimits ?: androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine) ==
                androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(scheme.primary),
            textStyle = (textStyle ?: MochiTheme.textStyles.body).copy(color = scheme.onSurface),
            decorationBox = { inner ->
                if (state.text.isEmpty() && placeholder != null) {
                    Text(text = placeholder, color = scheme.onSurfaceVariant, style = MochiTheme.textStyles.body)
                }
                inner()
            },
        )
    }
}

/** 带 composable 槽位的填充式文本框（旧 Kedge 的 TextFieldWithSlots）。 */
@Composable
fun MochiTextFieldWithSlots(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: Any? = null,
    shape: Shape? = null,
    textStyle: TextStyle? = null,
    colors: Any? = null,
) {
    MochiOutlinedTextFieldWithSlots(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        supportingText = supportingText,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        shape = shape,
        textStyle = textStyle,
        colors = colors,
    )
}
