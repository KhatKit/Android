package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.theme.AtomOneDarkPalette
import heizige.kk.khatkit.app.core.ui.theme.AtomOneLightPalette
import heizige.kk.khatkit.app.core.ui.theme.LocalDarkMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.code
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown

@Composable
fun JsonTree(
    json: JsonElement,
    modifier: Modifier = Modifier,
    initialExpandLevel: Int = 1
) {
    var selectedString by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.horizontalScroll(rememberScrollState())) {
        JsonNode(
            element = json,
            key = null,
            depth = 0,
            initialExpandLevel = initialExpandLevel,
            onStringClick = { selectedString = it }
        )
    }

    selectedString?.let { content ->
        PrimaryBottomSheet(
            visible = true,
            title = "JSON",
            imageVector = code,
            onDismiss = { selectedString = null },
            scrollable = false,
        ) { _ ->
            // Sheet 在独立窗口中，需要自己的 SelectionContainer，否则会沿用外层的 SelectionRegistrar 导致崩溃
            SelectionContainer {
                Text(
                    text = content,
                    fontFamily = JetbrainsMono,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun JsonNode(
    element: JsonElement,
    key: String?,
    depth: Int,
    initialExpandLevel: Int,
    onStringClick: (String) -> Unit
) {
    when (element) {
        is JsonObject -> JsonObjectNode(element, key, depth, initialExpandLevel, onStringClick)
        is JsonArray -> JsonArrayNode(element, key, depth, initialExpandLevel, onStringClick)
        is JsonPrimitive -> JsonPrimitiveNode(element, key, depth, onStringClick)
        is JsonNull -> JsonNullNode(key, depth)
    }
}

@Composable
private fun JsonObjectNode(
    obj: JsonObject,
    key: String?,
    depth: Int,
    initialExpandLevel: Int,
    onStringClick: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(depth < initialExpandLevel) }
    val entries = remember(obj) { obj.entries.toList() }

    Column {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) keyboardArrowDown else arrowForward,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = (depth * 16).dp)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (key != null) {
                KeyText(key)
                Text(": ", fontFamily = JetbrainsMono)
            }
            Text(
                text = if (expanded) "{" else "{ ... } (${entries.size})",
                fontFamily = JetbrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                entries.forEach { (childKey, childElement) ->
                    JsonNode(
                        element = childElement,
                        key = childKey,
                        depth = depth + 1,
                        initialExpandLevel = initialExpandLevel,
                        onStringClick = onStringClick
                    )
                }
                Row(modifier = Modifier.padding(start = (depth * 16 + 14).dp)) {
                    Text(
                        text = "}",
                        fontFamily = JetbrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonArrayNode(
    array: JsonArray,
    key: String?,
    depth: Int,
    initialExpandLevel: Int,
    onStringClick: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(depth < initialExpandLevel) }

    Column {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) keyboardArrowDown else arrowForward,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = (depth * 16).dp)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (key != null) {
                KeyText(key)
                Text(": ", fontFamily = JetbrainsMono)
            }
            Text(
                text = if (expanded) "[" else "[ ... ] (${array.size})",
                fontFamily = JetbrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                array.forEachIndexed { index, childElement ->
                    JsonNode(
                        element = childElement,
                        key = index.toString(),
                        depth = depth + 1,
                        initialExpandLevel = initialExpandLevel,
                        onStringClick = onStringClick
                    )
                }
                Row(modifier = Modifier.padding(start = (depth * 16 + 14).dp)) {
                    Text(
                        text = "]",
                        fontFamily = JetbrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonPrimitiveNode(
    primitive: JsonPrimitive,
    key: String?,
    depth: Int,
    onStringClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.padding(start = (depth * 16 + 14).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (key != null) {
            KeyText(key)
            Text(": ", fontFamily = JetbrainsMono)
        }
        ValueText(
            primitive = primitive,
            onClick = if (primitive.isString) {
                { onStringClick(primitive.contentOrNull ?: "") }
            } else null
        )
    }
}

@Composable
private fun JsonNullNode(
    key: String?,
    depth: Int
) {
    Row(
        modifier = Modifier.padding(start = (depth * 16 + 14).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (key != null) {
            KeyText(key)
            Text(": ", fontFamily = JetbrainsMono)
        }
        Text(
            text = "null",
            fontFamily = JetbrainsMono,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun KeyText(key: String) {
    Text(
        text = "\"$key\"",
        fontFamily = JetbrainsMono,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ValueText(primitive: JsonPrimitive, onClick: (() -> Unit)? = null) {
    // JSON 着色与语法高亮共用同一套明暗色板。以前这里写死 Darcula 深色三色
    // （#6A8759 绿 / #CC7832 橙 / #6897BB 蓝），浅色主题下白底上绿和橙几乎糊掉。
    // 现在按 LocalDarkMode 取 Atom One Dark/Light —— 明暗只出现在颜色取值处。
    val palette = if (LocalDarkMode.current) AtomOneDarkPalette else AtomOneLightPalette
    val (text, color) = when {
        primitive.isString -> {
            val content = (primitive.contentOrNull ?: "")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            "\"$content\"" to palette.string
        }

        primitive.booleanOrNull != null -> {
            primitive.content to palette.keyword
        }

        primitive.longOrNull != null || primitive.doubleOrNull != null -> {
            primitive.content to palette.number
        }

        else -> {
            primitive.content to MaterialTheme.colorScheme.onSurface
        }
    }

    Text(
        text = text,
        fontFamily = JetbrainsMono,
        color = color,
        textDecoration = if (onClick != null) TextDecoration.Underline else null,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}
