package heizige.kk.khatkit.uikit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeButtonVariant
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType
import heizige.kk.khatkit.ui.UiNode
import heizige.kk.khatkit.ui.UiNodeParser
import heizige.kk.khatkit.ui.UiSheetOptions

@Composable
fun NodeTreeSheet(
    title: String,
    root: UiNode,
    options: UiSheetOptions,
    form: Boolean = false,
    readOnly: Boolean = false,
    pickerHost: FormPickerHost? = null,
    onAction: (String, Map<String, Any?>) -> Unit,
    onDismiss: () -> Unit,
    markdown: @Composable (String, Modifier) -> Unit,
    image: @Composable (String, String?, Modifier) -> Unit,
) {
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
    KhatKitSheet(
        title = title,
        imageVector = Icons.Default.Edit,
        options = options,
        dismissText = if (readOnly) "关闭" else "取消",
        confirmText = if (form && !hasSubmit) "提交" else null,
        onConfirm = if (form && !hasSubmit) ({ submit("submit") }) else null,
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            root.Render(values, errors, pickerHost, readOnly, submit, markdown, image)
            if (form && !hasSubmit) {
                KedgeTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("取消", style = StyleResolvers.current().textStyle("label"))
                }
            }
        }
    }
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
                Column(Modifier.fillMaxWidth().padding(if (type == "Card") 12.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (text("title").isNotEmpty()) Text(text("title"), style = style.textStyle("subtitle"), color = style.color("default"))
                    children.forEach { it.Render(values, errors, pickerHost, readOnly, onAction, markdown, image) }
                }
            }
            if (type == "Card") KedgeSurface(modifier = m, content = content) else Box(m) { content() }
        }
        "Spacer" -> Spacer(m.width(number("width").dp).height(number("height").dp))
        "Divider" -> HorizontalDivider(modifier = m, color = style.color("muted"))
        "Text" -> Text(text("text"), modifier = m, color = style.color(text("tone", "default")),
            style = style.textStyle(text("style", "body")), fontWeight = if (props["bold"] == true) FontWeight.Bold else null)
        "Markdown" -> markdown(text("text"), m)
        "Image" -> image(text("src"), props["contentDescription"] as? String, m)
        "Badge" -> KedgeSurface(modifier = m) {
            Text(text("text"), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = style.textStyle("label"), color = style.color(text("tone", "default")))
        }
        "ProgressBar" -> Column(m) {
            if (text("label").isNotEmpty()) Text(text("label"), style = style.textStyle("label"))
            KedgeProgressIndicator(progress = number("ratio"), type = KedgeProgressIndicatorType.Linear, modifier = Modifier.fillMaxWidth())
        }
        "Button" -> KedgeButton(
            onClick = { onAction(text("action")) },
            enabled = !readOnly && props["disabled"] != true,
            modifier = m,
            variant = if (text("tone") == "primary") KedgeButtonVariant.Primary else KedgeButtonVariant.Secondary,
        ) {
            Text(text("label"), style = style.textStyle("label"),
                color = if (text("tone") == "danger") style.color("error") else androidx.compose.ui.graphics.Color.Unspecified)
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
