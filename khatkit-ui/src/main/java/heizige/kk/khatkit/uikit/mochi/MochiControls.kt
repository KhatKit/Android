package heizige.kk.khatkit.uikit.mochi

import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiSlider
import ui.feice.mochi.basic.MochiHapticProfile
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import ui.feice.mochi.basic.MochiButton as MochiRealButton
import ui.feice.mochi.basic.MochiButtonStyle as MochiRealButtonStyle
import ui.feice.mochi.basic.MochiIconButton as MochiRealIconButton
import ui.feice.mochi.effect.elastic.mochiPressDeform
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.math.roundToInt
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
 *  - [KkSwitch]：开关，保持「即时生效」语义；
 *  - [Md3Checkbox] / [Md3RadioButton]：列表行内的**选中标记**（勾/圆点），
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
    // INT2①：直接走 Mochi 真按钮 —— 按压缩放 1.08、跟手位移、pressedContainer 高光、glow 与触觉都由库内实现，
    // 这里只保留旧调用面（variant/contentPadding）。
    MochiRealButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = if (variant == MochiButtonVariant.Primary) MochiRealButtonStyle.Filled else MochiRealButtonStyle.Tonal,
        contentPadding = contentPadding,
    ) { content() }
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
    // INT2①：文本按钮也是 Mochi 真按钮的 Text 样式，同样有按压高光/形变与触觉。
    MochiRealButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = MochiRealButtonStyle.Text,
        contentPadding = contentPadding,
    ) { content() }
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
    // INT2①：图标按钮直连 Mochi 真组件（Standard→Text/Tonal→Tonal/Filled→Filled），按压形变与触觉随库走。
    MochiRealIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = when (variant) {
            MochiIconButtonVariant.Standard -> MochiRealButtonStyle.Text
            MochiIconButtonVariant.Tonal -> MochiRealButtonStyle.Tonal
            MochiIconButtonVariant.Filled -> MochiRealButtonStyle.Filled
        },
    ) { content() }
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
fun Md3TextField(
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
    // U3：输入类回 material3 TextField（配色/圆角由 U2 Bridge 提供）。
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle ?: MochiTheme.textStyles.bodyLarge,
        label = label?.let { { androidx.compose.material3.Text(it) } },
        placeholder = placeholder?.let { { androidx.compose.material3.Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { androidx.compose.material3.Text(it) } },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
    )
}

@Composable
fun Md3OutlinedTextField(
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
    // U3：输入类回 material3 TextField（配色/圆角由 U2 Bridge 提供）。
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle ?: MochiTheme.textStyles.bodyLarge,
        label = label?.let { { androidx.compose.material3.Text(it) } },
        placeholder = placeholder?.let { { androidx.compose.material3.Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { androidx.compose.material3.Text(it) } },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = shape,
    )
}

/** 开关（即时生效）。 */
@Composable
fun KkSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // SW1：换成 Mochi 的开关 —— z 弹性、gel 形变、glow 与触觉都是组件自带的，
    // 这里不再自己震（否则会和 Mochi 内部触觉双发）。
    val effects = LocalKkEffectsEnabled.current
    MochiSwitch(
        checked = checked,
        onCheckedChange = { newValue -> onCheckedChange?.invoke(newValue) },
        modifier = modifier,
        enabled = enabled,
        gel = if (effects) 0.6f else 0f,
        hapticProfile = if (effects) MochiHapticProfile.IosLike else MochiHapticProfile.Off,
    )
}

/** 列表行内的选中标记（不是开关；语义见文件头注释）。 */
@Composable
fun Md3Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}

/** 单选标记（不是开关；语义见文件头注释）。 */
@Composable
fun Md3RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.RadioButton(selected = selected, onClick = onClick, modifier = modifier, enabled = enabled)
}

/** 滑杆：Mochi 圆角轨道 + 拖动/点击改值（不引 material3 Slider）。 */
@Composable
fun KkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    // SW1：换成 Mochi 滑杆 —— 跟手、每档 tick 与松手 confirm、两端光效都由组件自带，
    // 这里不再手写 tick/confirm（会双发触觉）。
    val effects = LocalKkEffectsEnabled.current
    MochiSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = "slider" },
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        gel = if (effects) 0.6f else 0f,
        hapticProfile = if (effects) MochiHapticProfile.IosLike else MochiHapticProfile.Off,
    )
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
    // INT2①③ + OPT1：选项行走 Mochi 真按压形变（mochiPressDeform：缩 0.96 + 跟手偏移）+ kkPressState 高光
    // + 打开触觉；三者都受 LocalKkEffectsEnabled 控制，关掉时整行退回普通 clickable。
    val effects = LocalKkEffectsEnabled.current
    val haptics = rememberKkHaptics()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (effects) Modifier.mochiPressDeform() else Modifier)
            .kkPressState(interaction, enabled = effects && onClick != null)
            .background(
                if (backgroundColor == Color.Unspecified) MochiTheme.colorScheme.surfaceContainer else backgroundColor,
                shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = {
                            if (effects) haptics.open()
                            onClick()
                        },
                    )
                } else {
                    Modifier
                },
            )
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
fun Md3OutlinedTextFieldWithSlots(
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
    shape: Shape = MochiTheme.shapes.medium,
    textStyle: TextStyle? = null,
    colors: Any? = null,
) {
    // U3：带槽位的输入框同样回 material3 OutlinedTextField（槽位直接透传）。
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle ?: MochiTheme.textStyles.bodyLarge,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = shape,
    )
}

/** 由 TextFieldState 驱动的文本框（新的 foundation state API）。 */
@Composable
fun Md3TextFieldWithState(
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
    // U3：state 版输入框走 material3 的 TextFieldState 重载。
    androidx.compose.material3.OutlinedTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle ?: MochiTheme.textStyles.bodyLarge,
        placeholder = placeholder?.let { { androidx.compose.material3.Text(it) } },
        lineLimits = lineLimits ?: androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
        shape = shape ?: MochiTheme.shapes.medium,
    )
}

/** 带 composable 槽位的填充式文本框（旧 Kedge 的 TextFieldWithSlots）。 */
@Composable
fun Md3TextFieldWithSlots(
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
    // U3：填充式带槽位输入框也回 material3 TextField。
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle ?: MochiTheme.textStyles.bodyLarge,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
    )
}
