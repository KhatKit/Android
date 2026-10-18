package heizige.kk.khatkit.uikit

import androidx.compose.foundation.background
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeButtonVariant
import heizige.kk.kedge.components.KedgeHorizontalDivider
import heizige.kk.kedge.components.KedgeIconButton
import heizige.kk.kedge.components.KedgeOptionItem
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType
import heizige.kk.khatkit.ui.UiNode
import heizige.kk.khatkit.ui.UiNodeParser
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.ui.UiScreenPage
import heizige.kk.kedge.theme.KedgeColors

@Composable
fun NodeTreeSheet(
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
        PagedNodeTreeSheet(title, pages, options, pickerHost, onAction, onDismiss, markdown, image)
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
    val lazy = options.usesLargeSheet && root.lazyScreenRoot()
    val pageKey = options.navigation?.selected ?: title
    val lazyState = key(pageKey) { rememberLazyListState() }
    val counterHost = rememberCounterButtonHost()
    KhatKitSheet(
        title = title,
        imageVector = Icons.Default.Edit,
        options = options,
        dismissText = if (readOnly) "关闭" else "取消",
        confirmText = if (form && !hasSubmit) "提交" else null,
        onConfirm = if (form && !hasSubmit) ({ submit("submit") }) else null,
        onDismiss = onDismiss,
        // 导航条不是提交：不跑字段校验，直接把条目 event 交回脚本。
        onNavigate = { event -> onAction(event, root.collect(values)) },
        // 换屏后同一张弹层还在组合里：用「当前导航项」当 key，换页归零、同页刷新/求重置保留滚动位置。
        scrollKey = options.navigation?.selected ?: root,
        // 内容更新后复位触底加载守卫。
        contentKey = root,
        lazyListState = if (lazy) lazyState else null,
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
) {
    CompositionLocalProvider(LocalCounterButtonHost provides counterHost) {
        if (lazyState != null) {
            val alignment = when (root.props["hAlign"]) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            }
            LazyColumn(
                state = lazyState,
                modifier = root.nodeModifier(Modifier.fillMaxWidth()).fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy((root.props["spacing"] as? Number)?.toFloat()?.dp ?: 0.dp),
                horizontalAlignment = alignment,
            ) {
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
                        KedgeTextButton(onClick = onDismiss) {
                            Text("取消", style = StyleResolvers.current().textStyle("label"))
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                root.Render(values, errors, pickerHost, readOnly, submit, markdown, image)
                if (cancelButton) {
                    KedgeTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
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
    "surface" -> KedgeColors.surface
    "on_surface" -> KedgeColors.onSurface
    "on_surface_variant" -> KedgeColors.onSurfaceVariant
    "surface_container" -> KedgeColors.surfaceContainer
    "surface_container_high" -> KedgeColors.surfaceContainerHigh
    "surface_container_highest" -> KedgeColors.surfaceContainerHighest
    "outline_variant" -> KedgeColors.outlineVariant
    "primary" -> KedgeColors.primary
    "on_primary" -> KedgeColors.onPrimary
    "error" -> KedgeColors.error
    "error_container" -> KedgeColors.errorContainer
    "on_error_container" -> KedgeColors.onErrorContainer
    else -> hex(value)
}

private fun weight(value: String): FontWeight? = when (value) {
    "normal" -> FontWeight.Normal
    "medium" -> FontWeight.Medium
    "semibold" -> FontWeight.SemiBold
    "bold" -> FontWeight.Bold
    else -> null
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
                KedgeOptionItem(onClick = null, modifier = m, titleContent = content)
            } else Box(m) { content() }
        }
        "Spacer" -> Spacer(m.width(number("width").dp).height(number("height").dp))
        "Divider" -> KedgeHorizontalDivider(modifier = m, color = style.color("muted"))
        "Text" -> {
            val base = style.textStyle(text("style", "body"))
            Text(
                text("text"), modifier = m,
                color = nodeColor(text("color")) ?: style.color(text("tone", "default")),
                style = base.copy(fontSize = (props["size"] as? Number)?.toFloat()?.sp ?: base.fontSize),
                fontWeight = weight(text("weight"))
                    ?: if (props["bold"] == true) FontWeight.Bold else null,
            )
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
        "Badge" -> KedgeSurface(modifier = m) {
            Text(text("text"), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = style.textStyle("label"),
                color = nodeColor(text("color")) ?: style.color(text("tone", "default")))
        }
        "ProgressBar" -> Column(m) {
            if (text("label").isNotEmpty()) Text(text("label"), style = style.textStyle("label"))
            KedgeProgressIndicator(progress = number("ratio"), type = KedgeProgressIndicatorType.Linear, modifier = Modifier.fillMaxWidth())
        }
        "CounterButton" -> CounterButton(this, values, readOnly, onAction, m)
        "Button" -> KedgeButton(
            onClick = { onAction(text("action")) },
            enabled = !readOnly && props["disabled"] != true,
            modifier = m,
            variant = if (text("tone") == "primary") KedgeButtonVariant.Primary else KedgeButtonVariant.Secondary,
        ) {
            val label = nodeColor(text("color"))
                ?: if (text("tone") == "danger") style.color("error") else LocalContentColor.current
            val icon = UiIconVectors.vector(text("icon"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp),
                        tint = label)
                }
                Text(text("label"), style = style.textStyle("label"), color = label)
            }
        }
        "IconButton" -> KedgeIconButton(
            onClick = { onAction(text("action")) },
            enabled = !readOnly && props["disabled"] != true,
            modifier = m,
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

private fun AlignmentBased(h: Alignment.Horizontal, v: Alignment.Vertical): Alignment =
    object : Alignment {
        override fun align(size: androidx.compose.ui.unit.IntSize, space: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection) =
            androidx.compose.ui.unit.IntOffset(h.align(size.width, space.width, layoutDirection), v.align(size.height, space.height))
    }
