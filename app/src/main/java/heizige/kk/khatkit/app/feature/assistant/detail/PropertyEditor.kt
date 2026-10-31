package heizige.kk.khatkit.app.feature.assistant.detail

import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import heizige.kk.khatkit.ai.provider.CustomBody
import heizige.kk.khatkit.ai.provider.CustomHeader
import heizige.kk.khatkit.highlight.LocalCodeHighlighter
import heizige.kk.khatkit.app.R
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiIconDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme
import heizige.kk.khatkit.app.core.ui.components.richtext.HighlightCodeVisualTransformation
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import heizige.kk.khatkit.app.core.ui.theme.LocalDarkMode
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.keyboardArrowDown

private val COMMON_HEADER_NAMES = listOf(
    "User-Agent", "HTTP-Referer", "X-Title", "Referer", "Origin",
    "Accept-Language", "Cookie", "anthropic-beta", "OpenAI-Organization", "OpenAI-Project",
)

private fun commonHeaderNames(input: String): List<String> {
    val keyword = input.trim()
    return COMMON_HEADER_NAMES.filter {
        it.contains(keyword, ignoreCase = true) && !it.equals(keyword, ignoreCase = true)
    }.ifEmpty { COMMON_HEADER_NAMES }
}

private val jsonLenient = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

/**
 * 「自定义请求头 / 自定义请求体」共用的列表编辑器。
 *
 * 整个列表是**一张** Mochi 分组卡片（[MochiListSection]，小标题 = [title]）：每一项是卡片里的
 * 一段自定义内容（[PropertyRow]，字段按 [MochiListDefaults.RowPadding] 内缩），末尾一行是新增
 * 操作行（[MochiListButton]）。项与项之间由卡片画分隔线，不再一项一张卡片。
 *
 * @param newItem 新增按钮追加的空白项。
 * @param rowContent 单行的字段内容。`deleteButton` 是这一项的删除按钮，由调用点放在第一行字段
 *   的行尾；`replace` 用改后的整项替换列表里的这一项，并**立即**回调 [onUpdate]（时机与旧实现
 *   一致：输入即落库，不等失焦）。
 */
@Composable
private fun <T> PropertyListSection(
    title: String,
    items: List<T>,
    onUpdate: (List<T>) -> Unit,
    addLabel: String,
    deleteLabel: String,
    newItem: () -> T,
    rowContent: @Composable ColumnScope.(
        item: T,
        deleteButton: @Composable () -> Unit,
        replace: (T) -> Unit,
    ) -> Unit,
) {
    MochiListSection(header = title) {
        items.forEachIndexed { index, item ->
            PropertyRow {
                rowContent(
                    this,
                    item,
                    {
                        PropertyDeleteButton(
                            label = deleteLabel,
                            onClick = {
                                onUpdate(items.toMutableList().also { it.removeAt(index) })
                            },
                        )
                    },
                ) { next ->
                    onUpdate(items.toMutableList().also { it[index] = next })
                }
            }
        }
        MochiListButton(
            text = addLabel,
            onClick = { onUpdate(items + newItem()) },
        )
    }
}

/** 卡片里的一项：自定义内容，左右按列表行的边距内缩，字段之间 8dp。 */
@Composable
private fun PropertyRow(fields: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MochiListDefaults.RowPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = fields,
    )
}

/** 一项的第一行：字段占满剩余宽度，行尾放操作按钮（下拉建议 / 删除）。 */
@Composable
private fun PropertyFirstLine(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun PropertyDeleteButton(label: String, onClick: () -> Unit) {
    MochiIconButton(onClick = onClick) {
        Icon(delete, contentDescription = label, tint = MochiTheme.colorScheme.error)
    }
}

@Composable
fun CustomHeaders(headers: List<CustomHeader>, onUpdate: (List<CustomHeader>) -> Unit) {
    PropertyListSection(
        title = stringResource(R.string.assistant_page_custom_headers),
        items = headers,
        onUpdate = onUpdate,
        addLabel = stringResource(R.string.assistant_page_add_header),
        deleteLabel = stringResource(R.string.assistant_page_delete_header),
        newItem = { CustomHeader("", "") },
    ) { header, deleteButton, replace ->
        var headerName by remember(header.name) { mutableStateOf(header.name) }
        var headerValue by remember(header.value) { mutableStateOf(header.value) }
        var suggestionsExpanded by remember { mutableStateOf(false) }

        PropertyFirstLine {
            // 名称可自由输入，旁边的下拉按钮给常用请求头（原 SelectTextField 的输入 + 选项）。
            MochiTextField(
                value = headerName,
                onValueChange = { name ->
                    headerName = name
                    replace(header.copy(name = name.trim()))
                },
                placeholder = stringResource(R.string.assistant_page_header_name),
                modifier = Modifier.weight(1f),
            )
            val options = commonHeaderNames(headerName)
            MochiIconDropdown(
                expanded = suggestionsExpanded,
                onExpandedChange = { suggestionsExpanded = it },
                items = options.map { MochiItem(label = it) }.toImmutableList(),
                onItemClick = { index ->
                    options.getOrNull(index)?.let { name ->
                        headerName = name
                        replace(header.copy(name = name.trim()))
                    }
                    suggestionsExpanded = false
                },
                selectedIndex = options.indexOf(headerName),
            ) {
                Icon(
                    keyboardArrowDown,
                    contentDescription = stringResource(R.string.assistant_page_header_name),
                    tint = MochiTheme.colorScheme.onSurface,
                )
            }
            deleteButton()
        }
        MochiTextField(
            value = headerValue,
            onValueChange = {
                headerValue = it
                replace(header.copy(value = it.trim()))
            },
            placeholder = stringResource(R.string.assistant_page_header_value),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun CustomBodies(customBodies: List<CustomBody>, onUpdate: (List<CustomBody>) -> Unit) {
    val assistantPageInvalidJson = stringResource(R.string.assistant_page_invalid_json)

    PropertyListSection(
        title = stringResource(R.string.assistant_page_custom_bodies),
        items = customBodies,
        onUpdate = onUpdate,
        addLabel = stringResource(R.string.assistant_page_add_body),
        deleteLabel = stringResource(R.string.assistant_page_delete_body),
        newItem = { CustomBody("", JsonPrimitive("")) },
    ) { body, deleteButton, replace ->
        var bodyKey by remember(body.key) { mutableStateOf(body.key) }
        var bodyValueString by remember(body.value) {
            mutableStateOf(jsonLenient.encodeToString(JsonElement.serializer(), body.value))
        }
        var jsonParseError by remember { mutableStateOf<String?>(null) }

        PropertyFirstLine {
            MochiTextField(
                value = bodyKey,
                onValueChange = {
                    bodyKey = it
                    replace(body.copy(key = it.trim()))
                },
                placeholder = stringResource(R.string.assistant_page_body_key),
                modifier = Modifier.weight(1f),
            )
            deleteButton()
        }
        // JSON 值保留槽位版输入框：MochiTextField 没有 visualTransformation（代码高亮）和 minLines。
        Md3OutlinedTextFieldWithSlots(
            value = bodyValueString,
            onValueChange = { newString ->
                bodyValueString = newString
                try {
                    val newJsonValue = jsonLenient.parseToJsonElement(newString)
                    replace(body.copy(value = newJsonValue))
                    jsonParseError = null
                } catch (e: Exception) {
                    jsonParseError =
                        String.format(assistantPageInvalidJson, e.message?.take(100) ?: "")
                }
            },
            label = { M3Text(stringResource(R.string.assistant_page_body_value)) },
            modifier = Modifier.fillMaxWidth(),
            isError = jsonParseError != null,
            supportingText = {
                if (jsonParseError != null) {
                    M3Text(jsonParseError!!)
                }
            },
            minLines = 3,
            maxLines = 5,
            visualTransformation = HighlightCodeVisualTransformation(
                language = "json",
                highlighter = LocalCodeHighlighter.current,
                darkMode = LocalDarkMode.current
            ),
            textStyle = LocalTextStyle.current.merge(fontFamily = JetbrainsMono),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
