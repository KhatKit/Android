package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import heizige.kk.khatkit.ai.provider.CustomBody
import heizige.kk.khatkit.ai.provider.CustomHeader
import heizige.kk.khatkit.highlight.LocalCodeHighlighter
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.richtext.HighlightCodeVisualTransformation
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import heizige.kk.khatkit.app.core.ui.theme.LocalDarkMode
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.components.ui.SelectTextField

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
 * 两者原来是一份 150 行级的孪生实现（只差字段类型、文案与 JSON 校验），改一处漏一处；
 * 现在只剩这一份：分组标题与新增按钮由这里画（[PropertyAddButton]），每一行的卡片和
 * 删除按钮在 [PropertyRow] 里画，单行的字段由调用点通过 [rowContent] 传进来。
 *
 * @param newItem 新增按钮追加的空白项。
 * @param rowContent 单行的字段内容。`replace` 用改后的整项替换列表里的这一项，并
 *   **立即**回调 [onUpdate]（时机与旧实现一致：输入即落库，不等失焦）。
 */
@Composable
private fun <T> PropertyListSection(
    title: String,
    items: List<T>,
    onUpdate: (List<T>) -> Unit,
    addLabel: String,
    deleteLabel: String,
    newItem: () -> T,
    rowContent: @Composable (item: T, replace: (T) -> Unit) -> Unit,
) {
    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title)
        Spacer(Modifier.height(8.dp))

        items.forEachIndexed { index, item ->
            PropertyRow(
                deleteLabel = deleteLabel,
                onDelete = {
                    onUpdate(items.toMutableList().also { it.removeAt(index) })
                },
            ) {
                rowContent(item) { next ->
                    onUpdate(items.toMutableList().also { it[index] = next })
                }
            }
        }

        PropertyAddButton(
            label = addLabel,
            onClick = { onUpdate(items + newItem()) },
        )
    }
}

/** 单行的卡片：字段在 supporting 槽、删除按钮在 trailing 槽。 */
@Composable
private fun PropertyRow(
    deleteLabel: String,
    onDelete: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    CardGroup {
        item(
            supportingContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fields()
                }
            },
            trailingContent = {
                KedgeIconButton(
                    onClick = onDelete,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(delete, contentDescription = deleteLabel)
                }
            },
            // `headlineContent` 是 CardGroup.item 的必填参数；字段自己的 label 已经
            // 画了名称，这里留空，避免标题画两次。
            headlineContent = {},
        )
    }
}

/** 列表末尾的新增按钮。 */
@Composable
private fun PropertyAddButton(label: String, onClick: () -> Unit) {
    KedgeButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
    ) {
        Icon(add, contentDescription = label)
        Spacer(Modifier.width(4.dp))
        Text(label)
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
    ) { header, replace ->
        var headerName by remember(header.name) { mutableStateOf(header.name) }
        var headerValue by remember(header.value) { mutableStateOf(header.value) }

        SelectTextField(
            value = headerName,
            options = commonHeaderNames(headerName),
            onValueChange = { name ->
                headerName = name
                replace(header.copy(name = name.trim()))
            },
            onOptionSelected = { name ->
                headerName = name
                replace(header.copy(name = name.trim()))
            },
            label = { Text(stringResource(R.string.assistant_page_header_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        KedgeOutlinedTextFieldWithSlots(
            value = headerValue,
            onValueChange = {
                headerValue = it
                replace(header.copy(value = it.trim()))
            },
            label = { Text(stringResource(R.string.assistant_page_header_value)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
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
    ) { body, replace ->
        var bodyKey by remember(body.key) { mutableStateOf(body.key) }
        var bodyValueString by remember(body.value) {
            mutableStateOf(jsonLenient.encodeToString(JsonElement.serializer(), body.value))
        }
        var jsonParseError by remember { mutableStateOf<String?>(null) }

        KedgeOutlinedTextFieldWithSlots(
            value = bodyKey,
            onValueChange = {
                bodyKey = it
                replace(body.copy(key = it.trim()))
            },
            label = { Text(stringResource(R.string.assistant_page_body_key)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        KedgeOutlinedTextFieldWithSlots(
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
            label = { Text(stringResource(R.string.assistant_page_body_value)) },
            modifier = Modifier.fillMaxWidth(),
            isError = jsonParseError != null,
            supportingText = {
                if (jsonParseError != null) {
                    Text(jsonParseError!!)
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
