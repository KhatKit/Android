package heizige.kk.khatkit.uikit

import androidx.compose.material3.MaterialTheme
import ui.feice.mochi.basic.MochiButton as MochiRealButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiButtonStyle as MochiRealButtonStyle
import ui.feice.mochi.theme.LocalContentColor as MochiLocalContentColor
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButtonVariant
import heizige.kk.khatkit.uikit.mochi.KkSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import heizige.kk.khatkit.uikit.mochi.MochiHorizontalDivider
import ui.feice.mochi.list.MochiListDefaults
import heizige.kk.khatkit.uikit.mochi.KkGroupedColumn
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicatorType
import ui.feice.mochi.basic.MochiStepper
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiItem
import kotlinx.collections.immutable.toPersistentList
import heizige.kk.khatkit.uikit.mochi.KkChevron
import heizige.kk.khatkit.uikit.mochi.rememberKkGroupedRow
import ui.feice.mochi.effect.content.MochiCounterText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import heizige.kk.khatkit.ui.UiNode
import heizige.kk.khatkit.ui.UiNodeParser
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.ui.UiScreenPage

@Composable
fun NodeTreePage(
    title: String,
    root: UiNode,
    options: UiSheetOptions,
    form: Boolean = false,
    readOnly: Boolean = false,
    pages: Map<String, UiScreenPage> = emptyMap(),
    pickerHost: FormPickerHost? = null,
    onAction: (String, Map<String, Any?>) -> Unit,
    onDismiss: () -> Unit,
    markdown: @Composable (String, Modifier) -> Unit,
    image: @Composable (String, String?, Modifier) -> Unit,
) {
    if (pages.isNotEmpty() && !form && !readOnly) {
        PagedNodeTreePage(title, pages, options, pickerHost, onAction, onDismiss, markdown, image)
        return
    }
    val values = remember(root) { mutableStateMapOf<String, Any?>().apply { putAll(root.initialValues()) } }
    val errors = remember(root) { mutableStateMapOf<String, String>() }
    val submit: (String) -> Unit = { action ->
        errors.clear()
        errors.putAll(root.errors(values))
        if (errors.isEmpty() && !readOnly && (!form || action == "submit")) onAction(action, root.collect(values))
    }
    DisposableEffect(root, pickerHost) {
        pickerHost?.attach { id, value -> values[id] = value }
        onDispose { pickerHost?.detach() }
    }
    val hasSubmit = root.walk().any { it.type == "Button" && it.props["action"] == "submit" }
    // A screen's top-level blocks are independent lazy items; fields still live in
    // values above, so recycling an off-screen block never loses form input.
    val lazy = root.lazyScreenRoot()
    val pageKey = options.navigation?.selected ?: title
    val lazyState = key(pageKey) { rememberLazyListState() }
    val counterHost = rememberCounterButtonHost()
    // 顶栏小标题折叠：卡片页顶部的 page_title 还在视野里时栏里不显示标题，
    // 内容往上滚过一段后标题才渐现（和 codex_resets 等分页卡片一致）。
    // 非懒加载路径由 KhatKitPage 内部的 ScrollState 驱动，懒加载路径用列表状态。
    val density = LocalDensity.current
    val collapsePx = with(density) { 72.dp.toPx() }
    val titleProgress: (() -> Float)? = if (!options.usesLargeSheet || !lazy) {
        null
    } else {
        {
            val raw = if (lazyState.firstVisibleItemIndex > 0) {
                collapsePx
            } else {
                lazyState.firstVisibleItemScrollOffset.toFloat()
            }
            (raw / collapsePx).coerceIn(0f, 1f)
        }
    }
    KhatKitPage(
        title = title,
        imageVector = Icons.Default.Edit,
        options = options,
        dismissText = if (readOnly) "关闭" else "取消",
        confirmText = if (form && !hasSubmit) "提交" else null,
        onConfirm = if (form && !hasSubmit) ({ submit("submit") }) else null,
        onDismiss = onDismiss,
        // 导航条不是提交：不跑字段校验，直接把条目 event 交回脚本。
        onNavigate = { event -> onAction(event, root.collect(values)) },
        // 换屏后同一张页面还在组合里：用「当前导航项」当 key，换页归零、同页刷新/求重置保留滚动位置。
        scrollKey = options.navigation?.selected ?: root,
        // 内容更新后复位触底加载守卫。
        contentKey = root,
        lazyListState = if (lazy) lazyState else null,
        titleProgress = titleProgress,
    ) {
        NodeTreeContent(root, values, errors, pickerHost, readOnly, submit, markdown, image,
            if (lazy) lazyState else null, form && !hasSubmit, onDismiss, counterHost)
    }
}

internal fun UiNode.lazyScreenRoot(): Boolean = type == "Column" &&
    props["scrollable"] != true &&
    ops().none { it[0] == "scrollable" || it[0] == "visible" && it[1] == false }

@Composable
internal fun NodeTreeContent(
    root: UiNode,
    values: MutableMap<String, Any?>,
    errors: Map<String, String>,
    pickerHost: FormPickerHost?,
    readOnly: Boolean,
    submit: (String) -> Unit,
    markdown: @Composable (String, Modifier) -> Unit,
    image: @Composable (String, String?, Modifier) -> Unit,
    lazyState: LazyListState?,
    cancelButton: Boolean = false,
    onDismiss: () -> Unit = {},
    counterHost: CounterButtonHost? = null,
    /** 下拉刷新共用同一根越界弹簧：懒加载列表要显式挂上，才能和刷新状态联动。 */
    overscroll: androidx.compose.foundation.OverscrollEffect? = null,
    /** 刷新中让内容往下让出的高度（[ui.feice.mochi.basic.mochiRefreshHold]）。 */
    refreshHold: Modifier = Modifier,
    /** 列表顶部的不可滚动内容（卡片大标题等），只在懒加载列表路径用。 */
    header: (@Composable () -> Unit)? = null,
    /** 折叠顶栏让出的高度：列表顶部留白（懒加载路径）。 */
    contentTopInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    // 底栏浮在内容上时不占布局：滚动内容末尾留出栏高，最后一项仍能滚到栏上方。
    val bottomInset = LocalKhatKitContentBottomInset.current
    CompositionLocalProvider(LocalCounterButtonHost provides counterHost) {
        if (lazyState != null) {
            val alignment = when (root.props["hAlign"]) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            }
            LazyColumn(
                state = lazyState,
                // fillMaxHeight 优先于节点自带的 modifier 尺寸：卡片把整页写死高度时，
                // LazyColumn 的视口会撑到内容高度，整页就再也滚不动。
                modifier = root.nodeModifier(Modifier.fillMaxWidth()).fillMaxHeight().then(refreshHold),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = contentTopInset + if (header != null) 0.dp else 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + bottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy((root.props["spacing"] as? Number)?.toFloat()?.dp ?: 0.dp),
                horizontalAlignment = alignment,
                overscrollEffect = overscroll,
            ) {
                header?.let { title -> item(key = "__khatkit_header") { title() } }
                itemsIndexed(root.children,
                    key = { index, node -> node.props["key"]?.let { "node:$it" } ?: "index:$index" },
                    contentType = { _, node -> node.type },
                ) { _, node ->
                    // Independent drawing layers keep a static chart's display
                    // list intact while neighbouring records update or animate.
                    node.Render(values, errors, pickerHost, readOnly, submit, markdown, image,
                        Modifier.graphicsLayer())
                }
                if (cancelButton) {
                    item {
                        MochiTextButton(onClick = onDismiss) {
                            Text("取消", style = StyleResolvers.current().textStyle("label"))
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomInset),
            ) {
                root.Render(values, errors, pickerHost, readOnly, submit, markdown, image)
                if (cancelButton) {
                    MochiTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("取消", style = StyleResolvers.current().textStyle("label"))
                    }
                }
            }
        }
    }
}

private fun hex(value: String): Color? {
    if (!value.startsWith("#")) return null
    val digits = value.substring(1)
    return runCatching {
        when (digits.length) {
            6 -> Color(0xFF000000L or digits.toLong(16))
            8 -> Color(digits.toLong(16))
            else -> null
        }
    }.getOrNull()
}

@Composable
private fun nodeColor(value: String): Color? = when (value) {
    "surface" -> MaterialTheme.colorScheme.surface
    "on_surface" -> MaterialTheme.colorScheme.onSurface
    "on_surface_variant" -> MaterialTheme.colorScheme.onSurfaceVariant
    "surface_container" -> MaterialTheme.colorScheme.surfaceContainer
    "surface_container_high" -> MaterialTheme.colorScheme.surfaceContainerHigh
    "surface_container_highest" -> MaterialTheme.colorScheme.surfaceContainerHighest
    "outline_variant" -> MaterialTheme.colorScheme.outlineVariant
    "primary" -> MaterialTheme.colorScheme.primary
    "on_primary" -> MaterialTheme.colorScheme.onPrimary
    "primary_container" -> MaterialTheme.colorScheme.primaryContainer
    "on_primary_container" -> MaterialTheme.colorScheme.onPrimaryContainer
    "error" -> MaterialTheme.colorScheme.error
    "error_container" -> MaterialTheme.colorScheme.errorContainer
    "on_error_container" -> MaterialTheme.colorScheme.onErrorContainer
    else -> hex(value)
}

private fun weight(value: String): FontWeight? = when (value) {
    "normal" -> FontWeight.Normal
    "medium" -> FontWeight.Medium
    "semibold" -> FontWeight.SemiBold
    "bold" -> FontWeight.Bold
    else -> null
}

/**
 * 计数器数字的变化方向：按数字部分的绝对值比上次大（+1，往上滚）还是小（−1，往下滚）。
 * 第一次出现、或文字里没有数字时是 0（不滚）。
 */
@Composable
private fun rememberCounterDirection(current: String): Int {
    val last = remember { mutableStateOf(current) }
    val direction = remember(current) {
        val from = last.value.filter { it.isDigit() }.toLongOrNull()
        val to = current.filter { it.isDigit() }.toLongOrNull()
        last.value = current
        when {
            from == null || to == null -> 0
            else -> to.compareTo(from)
        }
    }
    return direction
}

private fun UiNode.ops() = UiNodeParser.modifierOps(props["modifier"])
private fun UiNode.weight(): Float? = ops().lastOrNull { it[0] == "weight" }?.get(1)?.let { (it as Number).toFloat() }

@Composable
private fun UiNode.nodeModifier(base: Modifier): Modifier {
    var result = base
    val operations = ops()
    for (op in operations) {
        fun dp(i: Int = 1) = (op[i] as Number).toFloat().dp
        result = when (op[0]) {
            "fillMaxSize" -> result.fillMaxSize()
            "fillMaxWidth" -> result.fillMaxWidth()
            "fillMaxHeight" -> result.fillMaxHeight()
            "width" -> result.width(dp())
            "height" -> result.height(dp())
            "size" -> result.size(dp())
            "padding" -> when (op.size) {
                2 -> result.padding(dp())
                3 -> result.padding(horizontal = dp(), vertical = dp(2))
                else -> result.padding(start = dp(), top = dp(2), end = dp(3), bottom = dp(4))
            }
            "clip" -> result.clip(if (op[1] == "circle") CircleShape else RoundedCornerShape(if (op.size > 2) dp(2) else 12.dp))
            "background" -> result.background(nodeColor(op[1] as String) ?: Color.Unspecified)
            else -> result
        }
    }
    if (props["scrollable"] == true || operations.any { it[0] == "scrollable" }) {
        // Sheets already scroll; give nested scrolling containers a bounded viewport.
        result = result.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
    }
    return result
}

@Composable
fun UiNode.Render(
    values: MutableMap<String, Any?>,
    errors: Map<String, String>,
    pickerHost: FormPickerHost?,
    readOnly: Boolean,
    onAction: (String) -> Unit,
    markdown: @Composable (String, Modifier) -> Unit,
    image: @Composable (String, String?, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ops().lastOrNull { it[0] == "visible" }?.get(1) == false) return
    val m = nodeModifier(modifier)
    val style = StyleResolvers.current()
    fun text(key: String, default: String = "") = props[key] as? String ?: default
    fun number(key: String, default: Float = 0f) = (props[key] as? Number)?.toFloat() ?: default
    val h = when (text("hAlign")) { "center" -> Alignment.CenterHorizontally; "end" -> Alignment.End; else -> Alignment.Start }
    val v = when (text("vAlign")) { "center" -> Alignment.CenterVertically; "end" -> Alignment.Bottom; else -> Alignment.Top }
    when (type) {
        "Column" -> Column(m, verticalArrangement = Arrangement.spacedBy(number("spacing").dp), horizontalAlignment = h) {
            children.forEach { child ->
                child.Render(values, errors, pickerHost, readOnly, onAction, markdown, image,
                    child.weight()?.let { Modifier.weight(it, fill = false) } ?: Modifier)
            }
        }
        "Row" -> Row(m, verticalAlignment = v, horizontalArrangement = if (text("vAlign") == "space_between") Arrangement.SpaceBetween else Arrangement.spacedBy(number("spacing").dp)) {
            children.forEach { child ->
                child.Render(values, errors, pickerHost, readOnly, onAction, markdown, image,
                    child.weight()?.let { Modifier.weight(it) } ?: Modifier)
            }
        }
        "Box" -> Box(m, contentAlignment = AlignmentBased(h, v)) {
            children.forEach { it.Render(values, errors, pickerHost, readOnly, onAction, markdown, image) }
        }
        "Section", "Card" -> {
            val content: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (text("title").isNotEmpty()) Text(text("title"), style = style.textStyle("subtitle"), color = style.color("default"))
                    children.forEach { it.Render(values, errors, pickerHost, readOnly, onAction, markdown, image) }
                }
            }
            if (type == "Card") {
                // Use the same container as settings. Kedge owns both theme branches,
                // shape and padding; informational groups have no row click feedback.
                MochiOptionItem(onClick = null, modifier = m, titleContent = content)
            } else Box(m) { content() }
        }
        // 设置页的一张分组卡片：组内行贴在一起，行间由分隔线分开（Mochi inset grouped）。
        "Group" -> KkGroupedColumn(
            modifier = m,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = MochiListDefaults.colors().container,
        ) {
            children.forEach { it.Render(values, errors, pickerHost, readOnly, onAction, markdown, image) }
        }
        // 设置项行：前导图标 + 标题（/说明）+ 行尾值 + 对勾或箭头。
        "ListRow" -> {
            val disabled = props["disabled"] == true || readOnly
            val tone = text("tone")
            val labelColor = when (tone) {
                "danger" -> style.color("error")
                "warning" -> style.color("warning")
                "primary" -> style.color("primary")
                else -> style.color("default")
            }
            val leading = text("icon")
            MochiOptionItem(
                onClick = if (disabled) null else ({ onAction(text("action")) }),
                modifier = m,
                leadingContent = if (leading.isBlank()) null else ({
                    Icon(
                        imageVector = UiIconVectors.vector(leading) ?: Icons.Filled.Edit,
                        contentDescription = null,
                        // KhatKit 设置页的行：前导图标用正文色（不是弱化色），只有显式 tone 才着色。
                        tint = labelColor,
                        modifier = Modifier.size(22.dp),
                    )
                }),
                titleContent = { Text(text("label"), style = style.textStyle("body"), color = labelColor) },
                supportingContent = text("caption").takeIf { it.isNotEmpty() }?.let {
                    ({ Text(text("caption"), style = style.textStyle("label"), color = style.color("muted")) })
                },
                trailingContent = text("value").takeIf { it.isNotEmpty() }?.let {
                    ({ Text(text("value"), style = style.textStyle("body"), color = style.color("muted")) })
                },
                chevron = props["chevron"] == true,
                selected = props["selected"] == true,
            )
        }
        "Spacer" -> Spacer(m.width(number("width").dp).height(number("height").dp))
        "Divider" -> MochiHorizontalDivider(modifier = m, color = style.color("muted"))
        "Text" -> {
            val base = style.textStyle(text("style", "body"))
            val color = nodeColor(text("color")) ?: style.color(text("tone", "default"))
            val scale = base.copy(fontSize = (props["size"] as? Number)?.toFloat()?.sp ?: base.fontSize)
            if (props["counter"] == true) {
                // Mochi 的计数器数字：每一位是 0–9 滚轮，值变了滚过去（和设置页/角标同一套效果）。
                MochiCounterText(
                    text = text("text"),
                    direction = rememberCounterDirection(text("text")),
                    modifier = m,
                    color = color,
                    style = scale.copy(
                        fontWeight = weight(text("weight"))
                            ?: if (props["bold"] == true) FontWeight.Bold else base.fontWeight,
                    ),
                )
            } else {
                Text(
                    text("text"), modifier = m,
                    color = color,
                    style = scale,
                    fontWeight = weight(text("weight"))
                        ?: if (props["bold"] == true) FontWeight.Bold else null,
                )
            }
        }
        "Icon" -> {
            val vector = UiIconVectors.vector(text("name"))
            if (vector != null) {
                Icon(
                    imageVector = vector,
                    contentDescription = props["contentDescription"] as? String,
                    modifier = m.size(number("size", 20f).dp),
                    tint = nodeColor(text("color")) ?: style.color(text("tone", "default")),
                )
            }
        }
        "Markdown" -> markdown(text("text"), m)
        "Image" -> image(text("src"), props["contentDescription"] as? String, m)
        "Badge" -> KkSurface(modifier = m) {
            Text(text("text"), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = style.textStyle("label"),
                color = nodeColor(text("color")) ?: style.color(text("tone", "default")))
        }
        "ProgressBar" -> Column(m) {
            if (text("label").isNotEmpty()) Text(text("label"), style = style.textStyle("label"))
            KkProgressIndicator(progress = number("ratio"), type = KkProgressIndicatorType.Linear, modifier = Modifier.fillMaxWidth())
        }
        "CounterButton" -> CounterButton(this, values, readOnly, onAction, m)
        "Stepper" -> StepperControl(this, values, readOnly, onAction, m)
        "Selector" -> SelectorControl(this, values, readOnly, onAction, m)
        "ChipRow" -> ChipRowControl(this, values, readOnly, onAction, m)
        "Dropdown" -> DropdownControl(this, values, readOnly, onAction, m)
        "Button" -> {
            val primary = text("tone") == "primary"
            val buttonStyle = if (primary) MochiRealButtonStyle.Filled else MochiRealButtonStyle.Tonal
            val scheme = MochiTheme.colorScheme
            // 次要按钮是中性灰底（和禁用态同一块底，文字换成正文色）：主题的 secondaryContainer 偏色，
            // 铺在列表卡片上和设置页那批行对不上。
            val colors = MochiButtonDefaults.colors(buttonStyle).let {
                if (primary) it else it.copy(container = scheme.onSurface.copy(alpha = 0.12f), content = scheme.onSurface)
            }
            MochiRealButton(
                onClick = { onAction(text("action")) },
                enabled = !readOnly && props["disabled"] != true,
                modifier = m,
                style = buttonStyle,
                colors = colors,
            ) {
                // 前景色读 Mochi 按钮给的 LocalContentColor（实心底上是 onPrimary，禁用时变淡）；
                // Material 那个 LocalContentColor 是按钮外面的颜色，实心底上会糊成一片。
                val label = nodeColor(text("color"))
                    ?: if (text("tone") == "danger") style.color("error") else MochiLocalContentColor.current
                val icon = UiIconVectors.vector(text("icon"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp),
                            tint = label)
                    }
                    Text(text("label"), style = style.textStyle("label"), color = label)
                }
            }
        }
        // 卡片里的圆形图标按钮也接进设置「外观 → 材质 → 按钮样式」（玻璃 / 实心）：
        // Tonal 变体在主题有 control 玻璃时画玻璃，选了实心（材质没玻璃）时退回实心 Tonal 圆钮，
        // 和 app 设置页、栏里的圆钮同一套；液态玻璃总开关关掉时同样变实心。
        "IconButton" -> MochiIconButton(
            onClick = { onAction(text("action")) },
            enabled = !readOnly && props["disabled"] != true,
            modifier = m,
            variant = MochiIconButtonVariant.Tonal,
            shapes = IconButtonDefaults.shapes(),
        ) {
            val vector = UiIconVectors.vector(text("icon"))
            if (vector != null) {
                Icon(
                    imageVector = vector,
                    contentDescription = (props["contentDescription"] as? String) ?: text("action"),
                    tint = (nodeColor(text("color")) ?: style.color(text("tone", "default"))).let {
                        if (readOnly || props["disabled"] == true) it.copy(alpha = it.alpha * 0.38f) else it
                    },
                )
            }
        }
        else -> {
            val legacy = when (type) {
                "TextField" -> "input"; "NumberField" -> "number"; "Switch" -> "switch"
                "Checkbox" -> "checkbox"; "Slider" -> "slider"; "Select" -> "select"
                "RadioGroup" -> "radio"; "FilePicker" -> "file_picker"; "DirPicker" -> "dir_picker"
                else -> error("Unsupported validated node: $type")
            }
            val item = props.toMutableMap().apply {
                put("type", legacy)
                put("default", props["value"])
                if (type == "FilePicker") {
                    val filter = text("filter", "any")
                    put("media", filter)
                    put("filter", if (filter in setOf("image", "video", "audio")) "$filter/*" else "*/*")
                }
            }
            Column(m) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalTextStyle provides style.textStyle("body"),
                    androidx.compose.material3.LocalContentColor provides style.color("default"),
                ) {
                    FormWidget(item, values, pickerHost)
                }
                if (text("hint").isNotEmpty()) Text(text("hint"), style = style.textStyle("label"), color = style.color("muted"))
                errors[text("id")]?.let { Text(it, color = style.color("error"), style = style.textStyle("label")) }
            }
        }
    }
}

/**
 * 节点树里的 Mochi 步进器（iOS UIStepper 样式）。
 *
 * 交互模型：+/- 先本地累加（立即显示），停顿 [STEPPER_SEND_DELAY_MS] 后把「净增量」
 * 作为一批写进 values[id] 并回传 action；卡片按批次提交（和 CounterButton 的
 * 180ms 合并语义一致）。revision 变化（脚本重新出屏）时重置本地增量。
 */
private const val STEPPER_SEND_DELAY_MS = 300L

@Composable
private fun StepperControl(
    node: UiNode,
    values: MutableMap<String, Any?>,
    readOnly: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier,
) {
    val id = node.props["id"] as String
    val action = node.props["action"] as String
    val baseline = (node.props["value"] as? Number)?.toInt() ?: 0
    val min = (node.props["min"] as? Number)?.toInt() ?: 0
    val max = (node.props["max"] as? Number)?.toInt() ?: Int.MAX_VALUE
    val step = ((node.props["step"] as? Number)?.toInt() ?: 1).coerceAtLeast(1)
    val showValue = node.props["showValue"] as? Boolean ?: true
    val revision = (node.props["revision"] as? Number)?.toLong() ?: 0L
    val disabled = readOnly || node.props["disabled"] == true
    var pending by remember(id) { mutableIntStateOf(0) }
    var sent by remember(id) { mutableIntStateOf(0) }
    var sendJob by remember(id) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(revision) {
        sendJob?.cancel()
        sendJob = null
        pending = 0
        sent = 0
    }
    fun scheduleSend() {
        sendJob?.cancel()
        sendJob = scope.launch {
            delay(STEPPER_SEND_DELAY_MS)
            val batch = pending
            if (batch > 0) {
                sent += batch
                pending = 0
                values[id] = batch
                onAction(action)
                values[id] = 0
            } else {
                pending = 0
            }
        }
    }
    val display = baseline + sent + pending
    MochiStepper(
        value = display,
        onValueChange = { next ->
            if (!disabled) {
                pending = next - baseline - sent
                scheduleSend()
            }
        },
        modifier = modifier,
        enabled = !disabled,
        range = min..max,
        step = step,
        showValue = showValue,
        format = { value -> java.text.NumberFormat.getIntegerInstance().format(value) },
    )
}

/**
 * 节点树里的 Mochi 分段选择器（iOS 26 风格，选中块滑动/拖尾动画由 MochiSegmentedControl 负责）。
 *
 * 点选后把选项文案写进 values[id] 再回传 action，脚本按文案映射业务值；
 * 这样选择器与其他字段一样走「收集 values → 一次回传」的现有链路。
 */
@Composable
private fun SelectorControl(
    node: UiNode,
    values: MutableMap<String, Any?>,
    readOnly: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier,
) {
    val id = node.props["id"] as String
    val action = node.props["action"] as String
    val options = (node.props["options"] as? List<*>)?.map { it.toString() }.orEmpty()
    if (options.isEmpty()) return
    val selectedValue = (node.props["value"] as String) ?: options.first()
    val index = options.indexOf(selectedValue).coerceAtLeast(0)
    val disabled = readOnly || node.props["disabled"] == true
    // 点选当下先本地切段，选中块立即走 Mochi 的滑动/拖尾动画；脚本回传后以 props 为准。
    var shown by remember(node) { mutableIntStateOf(index) }
    // 快速连点只发最后一次：每次 ui.screen 全量重渲染很耗卡片 fuel，攒 320ms 再提交。
    var sendJob by remember(id) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun submitSelected() {
        sendJob?.cancel()
        sendJob = scope.launch {
            kotlinx.coroutines.delay(320)
            onAction(action)
        }
    }
    // 交互完全交给 Mochi：它自己带点选、按住拖选和 haptics，pointerInput 会消费横向拖动。
    // 外层翻页是 pager 的横向滚动（嵌套滚动），所以在这里横拖不会翻页，空白处横拖才切页。
    MochiSegmentedControl(
        selectedIndex = shown,
        onSelect = { picked ->
            if (!disabled && picked != shown) {
                shown = picked
                values[id] = options[picked]
                submitSelected()
            }
        },
        items = persistentListOf(*options.toTypedArray()),
        modifier = modifier.fillMaxWidth(),
        enabled = !disabled,
    )
}

/**
 * 节点树里的 Mochi 芯片行（[ChipRow]）：单选或多选，选中时 chip 自己一笔画出对勾。
 *
 * 交互照 [SelectorControl]：点选当下先本地切，动画立即走 Mochi 的形变；
 * 320ms 去抖后再把 action 回传卡片，避免快速连点把卡片的 fuel 花光。
 */
@Composable
private fun ChipRowControl(
    node: UiNode,
    values: MutableMap<String, Any?>,
    readOnly: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier,
) {
    val id = node.props["id"] as String
    val action = (node.props["action"] as? String).orEmpty()
    val options = (node.props["options"] as? List<*>)?.map { it.toString() }.orEmpty()
    if (options.isEmpty()) return
    val icons = (node.props["icons"] as? List<*>)?.map { it.toString() }.orEmpty()
    val multiple = node.props["multiple"] == true
    val wrap = node.props["wrap"] != false
    val disabled = readOnly || node.props["disabled"] == true
    val initial: Set<String> = when (val raw = node.props["value"]) {
        is List<*> -> raw.mapNotNull { it as? String }.toSet()
        is String -> setOf(raw)
        else -> emptySet()
    }
    var shown by remember(node) { mutableStateOf(initial) }
    var sendJob by remember(id) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun submit() {
        if (action.isEmpty()) return
        sendJob?.cancel()
        sendJob = scope.launch {
            kotlinx.coroutines.delay(320)
            onAction(action)
        }
    }
    if (!multiple) {
        // 单选：界面上只保留一个选中项。
        MochiChipGroup(wrap = wrap, contentPadding = PaddingValues(horizontal = 16.dp), modifier = modifier) {
            options.forEachIndexed { index, label ->
                MochiChip(
                    label = label,
                    selected = label in shown,
                    showCheck = true,
                    enabled = !disabled,
                    onClick = {
                        if (disabled || label in shown) return@MochiChip
                        shown = setOf(label)
                        values[id] = label
                        submit()
                    },
                    leadingIcon = chipIcon(icons.getOrNull(index)),
                )
            }
        }
    } else {
        MochiChipGroup(wrap = wrap, contentPadding = PaddingValues(horizontal = 16.dp), modifier = modifier) {
            options.forEachIndexed { index, label ->
                MochiChip(
                    label = label,
                    selected = label in shown,
                    showCheck = true,
                    enabled = !disabled,
                    onClick = {
                        if (disabled) return@MochiChip
                        shown = if (label in shown) shown - label else shown + label
                        values[id] = shown.toList()
                        submit()
                    },
                    leadingIcon = chipIcon(icons.getOrNull(index)),
                )
            }
        }
    }
}

/** chip 的前导图标槽：图标名为空或未知时返回 null（chip 不画图标）。 */
private fun chipIcon(name: String?): (@Composable () -> Unit)? {
    val key = name?.takeIf { it.isNotBlank() } ?: return null
    val vector = UiIconVectors.vector(key) ?: return null
    return { Icon(vector, contentDescription = null, modifier = Modifier.size(17.dp)) }
}

/**
 * 节点树里的 Mochi 下拉选择（[Dropdown]）：按钮槽显示 label + 当前值 + 箭头，
 * 点开是 Mochi 菜单，选中后**原地**回填 values[id] 并回传 action（不换屏）。
 *
 * 外观与 [ListRow] 对齐（16dp 横内边距、行高 ≥52dp），放在 Group 里不会像异类控件。
 */
@Composable
private fun DropdownControl(
    node: UiNode,
    values: MutableMap<String, Any?>,
    readOnly: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier,
) {
    val id = node.props["id"] as String
    val action = (node.props["action"] as? String).orEmpty()
    val label = (node.props["label"] as? String).orEmpty()
    val icon = (node.props["icon"] as? String).orEmpty()
    val options = (node.props["options"] as? List<*>)?.map { it.toString() }.orEmpty()
    if (options.isEmpty()) return
    val disabled = readOnly || node.props["disabled"] == true
    val current = (node.props["value"] as? String) ?: options.first()
    var expanded by remember(node) { mutableStateOf(false) }
    val style = StyleResolvers.current()
    // 按钮槽的底：在 Group（inset grouped 卡片）里就和其它行一样透明、透出组底色；
    // 独立一行时用列表行容器色。默认的 secondaryContainer 会在一片中性灰里冒出一块青色。
    val grouped = rememberKkGroupedRow() != null
    val dropdownColors = MochiDropdownDefaults.dropdownColors(
        buttonColor = if (grouped) Color.Transparent else MochiListDefaults.colors().container,
        buttonContentColor = style.color("default"),
    )
    MochiDropdown(
        expanded = expanded,
        onExpandedChange = { if (!disabled) expanded = it },
        items = options.map { MochiItem(it) }.toPersistentList(),
        colors = dropdownColors,
        buttonPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onItemClick = { index ->
            expanded = false
            val picked = options.getOrNull(index)
            if (picked != null) {
                values[id] = picked
                if (action.isNotEmpty()) onAction(action)
            }
        },
        selectedIndex = options.indexOf(current),
        enabled = !disabled,
        modifier = modifier,
        onClick = { if (!disabled) expanded = true },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val vector = UiIconVectors.vector(icon)?.takeIf { icon.isNotBlank() }
            if (vector != null) {
                Icon(vector, contentDescription = null, tint = style.color("primary"), modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (label.isNotEmpty()) {
                    Text(label, style = style.textStyle("label"), color = style.color("muted"))
                }
                Text(current, style = style.textStyle("body"), color = style.color("default"))
            }
            KkChevron()
        }
    }
}

private fun AlignmentBased(h: Alignment.Horizontal, v: Alignment.Vertical): Alignment =
    object : Alignment {
        override fun align(size: androidx.compose.ui.unit.IntSize, space: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection) =
            androidx.compose.ui.unit.IntOffset(h.align(size.width, space.width, layoutDirection), v.align(size.height, space.height))
    }
