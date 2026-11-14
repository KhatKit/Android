package heizige.kk.khatkit.uikit.mochi

import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiSlider
import ui.feice.mochi.basic.MochiTextFieldDefaults
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.utils.MochiHapticProfile
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import ui.feice.mochi.effect.content.mochiAnimateHeight
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import ui.feice.mochi.list.MochiListDefaults
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.Text
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiIcon
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

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

/**
 * 顶栏里的图标按钮是否按「栏按钮」画（有底色 + 玻璃/模糊，和设置页左上返回同一套）。
 * 由顶栏组件（MochiTitleBar 的各处包装）下发，页面不用自己判断。
 */
val LocalKkBarIconButton = androidx.compose.runtime.staticCompositionLocalOf { false }

/** 顶栏按钮槽的包装：里面用 [MochiIconButton] 会按栏按钮（圆底 + 栏玻璃/模糊 + 栏颜色）画。 */
@Composable
fun KkBarIconScope(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalKkBarIconButton provides true, content = content)
}

/**
 * 栏按钮的配色：**故意不是** [MochiButtonDefaults.barColors] 的那个值 — 库里的 barGlass 只在
 * 配色等于默认值时画玻璃。栏按钮画成玻璃时，面色跟着栏背后滚过去的内容变（快速滑动时
 * 返回键的圆底会暗一档再回来，实测圆盘均值 225 → 213，约 0.7s），看起来像「闪一下变色」。
 *
 * 这里把 container 的 alpha 压到 0.995：视觉上和不透明一样，但不再等于默认值，于是按钮走纯色胶囊 ——
 * 和 Mochi 实例（demo 的目录页/聊天页）同一种样子，也不再随背后内容变色。
 */
@Composable
fun kkSolidBarColors(): ui.feice.mochi.basic.MochiButtonColors =
    ui.feice.mochi.basic.MochiButtonDefaults.barColors()
        .let { it.copy(container = it.container.copy(alpha = 0.995f)) }

/**
 * 栏按钮配色：浅色模式使用 Mochi 的实心栏色，深色模式才由设置里的「按钮样式」
 * （[LocalKkGlassButtons]）决定是否使用玻璃。
 *
 * 浅色模式如果继续走玻璃取样，透明栏按钮会把页面背景合成为灰色（常见为 #A0A0），
 * 所以固定走 [kkSolidBarColors]；深色模式保留 Mochi 的玻璃开关。
 *
 * 全 app 的栏按钮（返回键、顶栏图标按钮、页面内直接调的 MochiBarIconButton）都该走这里，
 * 不要再各自传 [kkSolidBarColors] 把玻璃写死。
 */
@Composable
fun kkBarButtonColors(): ui.feice.mochi.basic.MochiButtonColors =
    if (MochiTheme.colorScheme.isDark && LocalKkGlassButtons.current) {
        ui.feice.mochi.basic.MochiButtonDefaults.barColors()
    } else {
        kkSolidBarColors()
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
    // 顶栏上下文：和 MochiBackButton 同一枚栏按钮（圆底 + 栏的配色）
    if (LocalKkBarIconButton.current) {
        MochiBarIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = kkBarButtonColors(),
            content = content,
        )
        return
    }
    // INT2①：图标按钮直连 Mochi 真组件，按压形变与触觉随库走。
    // 带底色的两种走玻璃样式（Tonal→Glass、Filled→ProminentGlass）：主题材质里按钮是玻璃时和顶栏按钮一样画玻璃，
    // 设置里选了实心（材质没有 control 玻璃）时库自动退回 Tonal / Filled，全 app 的圆钮跟着一个开关走。
    MochiRealIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = when (variant) {
            MochiIconButtonVariant.Standard -> MochiRealButtonStyle.Text
            MochiIconButtonVariant.Tonal -> MochiRealButtonStyle.Glass
            MochiIconButtonVariant.Filled -> MochiRealButtonStyle.ProminentGlass
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

/**
 * 带 composable 槽位的 Mochi 输入框（[Md3OutlinedTextFieldWithSlots] / [Md3TextFieldWithSlots] 的底座）。
 *
 * 外观照库里的 MochiTextField：填充底色、不描边，单行是 48dp 胶囊、多行是圆角矩形；
 * label 放在框上方（footnote、次要色），placeholder 用次要色，supporting 在框下方，
 * 错误态只换底色（error 12%），不做 MD3 的整圈描边。
 *
 * 保留「槽位」这层 API：label / placeholder / supportingText / leading / trailing 仍是 composable，
 * 存量 100+ 个调用点不用改签名。
 */
@Composable
private fun MochiSlotField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    label: (@Composable () -> Unit)?,
    placeholder: (@Composable () -> Unit)?,
    enabled: Boolean,
    readOnly: Boolean,
    isError: Boolean,
    supportingText: (@Composable () -> Unit)?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    singleLine: Boolean,
    minLines: Int,
    maxLines: Int,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation,
    shapeOverride: Shape?,
    textStyle: TextStyle?,
) {
    val cs = MochiTheme.colorScheme
    val colors = MochiTextFieldDefaults.colors()
    val fieldShape = shapeOverride ?: if (singleLine) {
        MochiTextFieldDefaults.Shape
    } else {
        MochiRoundedRectangle(18.dp)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 单行/多行切换、说明行出现时高度平滑变化
            .kkAnimateContentSize()
            .graphicsLayer { alpha = if (enabled) 1f else MochiDefaults.DisabledAlpha },
    ) {
        label?.let { slot ->
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides cs.onSurfaceVariant,
            ) {
                ProvideTextStyle(MochiTheme.textStyles.footnote) {
                    Box(Modifier.padding(bottom = 6.dp)) { slot() }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isError) colors.errorContainer else colors.container, fieldShape)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (singleLine) 0.dp else 12.dp,
                    bottom = if (singleLine) 0.dp else 12.dp,
                )
                .heightIn(min = if (singleLine) MochiTextFieldDefaults.Height else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leadingIcon?.invoke()
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                cursorBrush = SolidColor(cs.primary),
                textStyle = (textStyle ?: MochiTheme.textStyles.body).copy(color = cs.onSurface),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            CompositionLocalProvider(
                                androidx.compose.material3.LocalContentColor provides colors.placeholder,
                            ) {
                                ProvideTextStyle(MochiTheme.textStyles.body) { placeholder() }
                            }
                        }
                        inner()
                    }
                },
            )
            trailingIcon?.invoke()
        }
        supportingText?.let { slot ->
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides
                    if (isError) cs.error else cs.onSurfaceVariant,
            ) {
                ProvideTextStyle(MochiTheme.textStyles.footnote) {
                    Box(Modifier.padding(top = 6.dp, start = 4.dp)) { slot() }
                }
            }
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
    MochiSlotField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shapeOverride = null,
        textStyle = textStyle,
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
    MochiSlotField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shapeOverride = null,
        textStyle = textStyle,
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
    // UI-2：Mochi 行件的选中态（行尾对勾），默认关闭；调用点零改。
    selected: Boolean = false,
    // UI-2：行最小高，默认取 Mochi 真行件度量（MochiListDefaults.RowMinHeight = 52dp），需要更高时按页覆盖。
    minHeight: Dp = MochiListDefaults.RowMinHeight,
    // 行尾「›」：点进去是下一级页面的行才开（同 MochiListRow 的 chevron）。
    // 弹窗/选择器/开关/外链行不开。画在 trailingContent 之后，见 [KkChevron]。
    chevron: Boolean = false,
    // 行内边距：默认 Mochi 列表行度量（左右 16 / 上下 8）；嵌在别人的卡片里时可收小。
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    // 交互照 Mochi 列表行（MochiListRow）：不形变、没有水波纹和触觉，按下整行灰底高亮、松手淡出。
    // 分组卡片（KkGroupedColumn）里的行把按下强度交给组，分隔线跟着淡出；底色默认透明，透出组的底色。
    // 独立一行就是一张单行卡片，高亮裁在它自己的外形里。
    val grouped = rememberKkGroupedRow()
    val ownPress = remember { Animatable(0f) }
    val container = when {
        backgroundColor != Color.Unspecified -> backgroundColor
        grouped != null -> Color.Transparent
        else -> MochiListDefaults.colors().container
    }
    // 组内的行不自带圆角：高亮铺满整行，首尾两行的大圆角由组的外形裁出来（同 MochiListCard）
    val rowShape = if (grouped != null) RectangleShape else shape
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 行内容增减（副标题出现 / 消失、行内展开说明）走 Mochi 的尺寸弹簧
            .kkAnimateContentSize()
            .kkGroupedRowPosition(grouped)
            .background(container, rowShape)
            .clip(rowShape)
            .kkRowTap(onClick, enabled = true, press = grouped?.press ?: ownPress, selected = selected)
            // UI-1：行高对齐 Mochi 列表行 56dp。40dp 头像/图标 + 上下各 8dp = 56dp；
            // 纯文字行靠 heightIn 托到 56dp（原来上下各 12dp，带 40dp 头像时是 64dp）。
            .heightIn(min = minHeight)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leadingContent?.invoke()
        Column(
            modifier = Modifier.weight(1f).kkGroupedTextStart(grouped),
            // 标题与 supporting（「记忆: 0」chip / 标签）之间给 2dp：原来是 0，chip 直接贴字基线
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            overlineContent?.invoke()
            titleContent()
            supportingContent?.invoke()
        }
        if (chevron && trailingContent != null) {
            // 行尾值/按钮与 › 之间照 Mochi 行尾的 8dp（行内其余间隔是 12dp）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                trailingContent()
                KkChevron()
            }
        } else {
            trailingContent?.invoke()
            if (chevron) KkChevron()
        }
        // UI-2：选中态用 Mochi 行件同款对勾（行尾、12dp 间隔、主色），不额外加底色。
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MochiTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 行尾「下一级」箭头 ›：所有「点了进二级页」的行共用这一枚，字形/大小/颜色只在这里定。
 *
 * 照搬 Mochi 的 `MochiGlyphs.Chevron(colors.chevron, size = 14.dp)`（Mochi 里是 internal，拿不到）：
 * 先找 `LocalMochiIconProvider` 里的同名图标（`chevron-forward`），没有才手画 2dp 圆头折线，
 * 颜色取 `MochiListDefaults.colors().chevron`（次要色 70%）。只做指示，不接点击（点击归整行）。
 */
@Composable
fun KkChevron(modifier: Modifier = Modifier) {
    val tint = MochiListDefaults.colors().chevron
    MochiIcon("chevron-forward", modifier.size(14.dp), tint = tint) { slot ->
        Canvas(slot) {
            val w = 2.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val hh = size.height * 0.36f
            val hw = hh * 0.55f
            val p = Path().apply {
                moveTo(cx - hw, cy - hh)
                lineTo(cx + hw, cy)
                lineTo(cx - hw, cy + hh)
            }
            drawPath(p, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
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
    /** 标题行内边距，见 [MochiOptionItem.contentPadding]。 */
    rowContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    /** 展开内容（旧 Kedge 用 expandedContent 命名）。 */
    expandedContent: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    // 展开/收起照 Mochi 列表卡的做法：容器高度走 mochiAnimateHeight（spring 1.0/380f），
    // 不是「高度+淡入」两段式；连续点击会在过渡途中改目标高度。
    // 整块（标题行 + 展开内容）裁成同一个圆角：展开后内容的角与行按下的涟漪完全对齐；
    // 高度动画只管高度，不改变外形。
    // 展开箭头：收起时朝右（›），展开时转 90° 朝下，弹性跟 Mochi 列表页的展开一致
    @Suppress("UNUSED_VARIABLE")
    val chevronState = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 90f else 0f,
        animationSpec = ui.feice.mochi.anim.MochiMotions.responseSpring(0.3f, 0.6f),
        label = "expand_chevron",
    )
    val chevronRotation = chevronState.value
    Column(modifier = modifier.fillMaxWidth().clip(shape).mochiAnimateHeight()) {
        MochiOptionItem(
            onClick = {
                onClick?.invoke()
                onExpandedChange(!expanded)
            },
            shape = shape,
            backgroundColor = backgroundColor,
            leadingContent = leadingContent,
            overlineContent = overlineContent,
            titleContent = titleContent,
            supportingContent = supportingContent,
            trailingContent = if (showChevron || trailingContent != null) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        trailingContent?.invoke()
                        if (showChevron) {
                            Box(Modifier.graphicsLayer { rotationZ = chevronRotation }) {
                                KkChevron()
                            }
                        }
                    }
                }
            } else {
                null
            },
            contentPadding = rowContentPadding,
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
    // INT：输入框统一走 Mochi 外观（填充胶囊），槽位原样透传、调用点零改。
    MochiSlotField(
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
        shapeOverride = null,
        textStyle = textStyle,
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
    // state 版输入框同样走 Mochi 外观：把 state 当成受控值（text 是 snapshot state，
    // 外部 setText 后这里会跟着重组），避免再维护一条 BasicTextField(state) 的渲染分支。
    val limits = lineLimits ?: androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine
    val singleLine = limits is androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine
    // 多行时把调用方的行数上限接过来：输入框长到上限就在框内滚动，不会把对话框越撑越高
    val multiline = limits as? androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine
    MochiSlotField(
        value = state.text.toString(),
        onValueChange = { state.setTextAndPlaceCursorAtEnd(it) },
        modifier = modifier,
        label = null,
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        readOnly = readOnly,
        isError = false,
        supportingText = null,
        leadingIcon = null,
        trailingIcon = null,
        singleLine = singleLine,
        minLines = multiline?.minHeightInLines ?: 1,
        maxLines = if (singleLine) 1 else (multiline?.maxHeightInLines ?: Int.MAX_VALUE),
        keyboardOptions = KeyboardOptions.Default,
        keyboardActions = KeyboardActions.Default,
        visualTransformation = VisualTransformation.None,
        shapeOverride = shape,
        textStyle = textStyle,
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
    MochiSlotField(
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
        shapeOverride = shape,
        textStyle = textStyle,
    )
}
