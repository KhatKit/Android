package heizige.kk.khatkit.app.core.ui.components.khatkit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.uikit.FormPickerHost
import heizige.kk.khatkit.uikit.KhatKitForm
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * 宿主侧 schema 驱动表单（设计文档 §8 P0-6）。
 *
 * 参数表单的权威来源是卡片的 `input_schema`（`manifest.parameters`）与 `ui_schema`
 * （Hub 索引 `CardIndexEntry.uiSchema`）加卡片本地的 `manifest.ui` 控件提示；
 * 脚本自弹的 `ui.form` 只保留为展示与补参渠道，不再另起一套参数收集逻辑。
 *
 * 本文件只负责「按 schema 算出要问什么」这一纯函数部分（[KhatKitSchemaFormSpec]），
 * 以及把结果交给 [:khatkit-ui] 已有的表单渲染器 [KhatKitForm]；
 * 弹表单 / 等回传 / 回灌参数的编排在 `KhatKitToolProvider.runCardWithStatus`。
 */
object KhatKitSchemaFormSpec {

    /** 宿主 schema 表单标记：只有本文件生成的条目带它，脚本自弹的旧表单永远没有。 */
    const val MARKER = "__host_schema"

    /** 已由调用方（AI / 流）填好、只在表单里展示不再询问的条目。 */
    const val PROVIDED = "__provided"

    /** 宿主能渲染的组件（`UiWidgetVocabulary.LEGACY` 的子集，checkbox 归一成 switch）。 */
    private val WIDGETS = setOf(
        "input", "number", "switch", "slider", "select", "radio",
        "file_picker", "dir_picker", "text", "markdown", "divider",
    )

    private const val VALUE_PREVIEW_LIMIT = 120

    /** 一个需要用户补充的字段。 */
    data class SchemaField(
        val id: String,
        val label: String,
        val widget: String,
        val required: Boolean,
        val options: List<String> = emptyList(),
        val defaultValue: Any? = null,
        val placeholder: String? = null,
        val multiline: Boolean = false,
        val minimum: Double? = null,
        val maximum: Double? = null,
        val step: Double? = null,
    ) {
        /** 转成 `:khatkit-ui` 的 FormWidget 条目；额外键 `__host_schema`/`required` 供宿主识别。 */
        fun toItem(): Map<String, Any?> = buildMap {
            put("id", id)
            put("type", widget)
            put("label", label)
            put("required", required)
            put(MARKER, true)
            defaultValue?.let { put("default", it) }
            placeholder?.takeIf { it.isNotBlank() }?.let { put("placeholder", it) }
            if (multiline) put("multiline", true)
            if (options.isNotEmpty()) put("options", options)
            if (widget == "slider") {
                put("min", minimum ?: 0.0)
                put("max", maximum ?: 100.0)
                step?.let { put("step", it) }
            }
        }
    }

    /** 调用方已经填好的字段：表单只展示，不重复询问。 */
    data class ProvidedField(val id: String, val label: String, val preview: String) {
        fun toItem(): Map<String, Any?> = mapOf(
            "id" to id,
            "type" to "text",
            "label" to label,
            "value" to preview,
            PROVIDED to true,
            MARKER to true,
        )
    }

    /** 一张 schema 表单的计划：哪些要问、哪些只展示。 */
    data class Plan(
        val name: String,
        val fields: List<SchemaField>,
        val provided: List<ProvidedField>,
        val items: List<Map<String, Any?>>,
    ) {
        /** 只有确实缺参数才弹表单；AI 全参数调用时恒为 false。 */
        val shouldAsk: Boolean get() = fields.isNotEmpty()

        val missingRequired: List<String> get() = fields.filter { it.required }.map { it.id }
    }

    /**
     * 按 schema 计算表单计划。
     *
     * 询问集合 = `input_schema.required` 中已声明的字段 ∪ `manifest.ui` 里 `source="ui"`
     * （卡片显式要求用户填写）的字段；[provided] 里已有的字段一律不问，只作为展示行。
     * 任何一层 schema 缺字段都是正常的（旧卡片只有 `parameters`），不做强校验。
     */
    fun plan(
        name: String,
        parameters: JsonObject?,
        uiSchema: JsonObject? = null,
        ui: Map<String, JsonObject> = emptyMap(),
        provided: Map<String, Any?> = emptyMap(),
    ): Plan {
        val properties = parameters?.get("properties") as? JsonObject ?: JsonObject(emptyMap())
        val requiredIds = (parameters?.get("required") as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter { it.isNotBlank() }
            .toSet()

        val askIds = LinkedHashSet<String>()
        requiredIds.forEach { id -> if (id in properties || id in ui) askIds += id }
        ui.forEach { (id, spec) -> if (hintSource(spec) == "ui") askIds += id }

        val fields = askIds
            .filterNot { isProvided(provided[it]) }
            .map { id ->
                val spec = properties[id] as? JsonObject
                val hint = ui[id] ?: (uiSchema?.get(id) as? JsonObject)
                SchemaField(
                    id = id,
                    label = fieldLabel(id, spec, hint),
                    widget = widgetOf(spec, hint),
                    required = id in requiredIds,
                    options = enumOptions(spec, hint),
                    defaultValue = defaultOf(spec, hint),
                    placeholder = (hint?.get("placeholder")?.plain() as? String)?.takeIf { it.isNotBlank() },
                    multiline = hint?.get("multiline")?.plain() == true,
                    minimum = number(spec?.get("minimum") ?: spec?.get("min")),
                    maximum = number(spec?.get("maximum") ?: spec?.get("max")),
                    step = number(spec?.get("multipleOf") ?: spec?.get("step")),
                )
            }

        // 展示行只列 schema / ui 里声明过的字段，避免把内部参数（如 __khatkit_trigger）摊出来
        val providedRows = (properties.keys + ui.keys)
            .filter { isProvided(provided[it]) }
            .distinct()
            .map { id ->
                val spec = properties[id] as? JsonObject
                val hint = ui[id] ?: (uiSchema?.get(id) as? JsonObject)
                ProvidedField(id = id, label = fieldLabel(id, spec, hint), preview = preview(provided[id]))
            }

        return Plan(
            name = name,
            fields = fields,
            provided = providedRows,
            items = providedRows.map { it.toItem() } + fields.map { it.toItem() },
        )
    }

    /**
     * 把表单回传值并回原参数：只补非空新值，已提供字段不被覆盖成 null。
     * [values] 为 null（用户取消 / 超时 / 宿主没接线）时原样返回。
     */
    fun merge(args: Map<String, Any?>, values: Map<String, Any?>?): Map<String, Any?> {
        if (values.isNullOrEmpty()) return args
        return args + values.filterValues { it != null }
    }

    /** 条目是否来自宿主 schema 表单（[:khatkit-ui] 的脚本表单没有这个标记）。 */
    fun isSchemaDriven(items: List<Map<String, Any?>>): Boolean =
        items.any { it[MARKER] == true }

    /** 该条目是否为「已提供」展示行。 */
    fun isProvidedRow(item: Map<String, Any?>): Boolean = item[PROVIDED] == true

    /** 必填字段数：宿主据此给出一句本地化提示。 */
    fun requiredCount(items: List<Map<String, Any?>>): Int =
        items.count { it[PROVIDED] != true && it["required"] == true }

    // ===== 内部工具：JSON Schema / ui hints → 表单条目 =====

    private fun hintSource(spec: JsonObject?): String? =
        (spec?.get("source")?.plain() as? String)?.trim()?.lowercase()

    /** 值是否算「已提供」：null / 空白串 / 空集合都按缺参处理。 */
    private fun isProvided(value: Any?): Boolean = when (value) {
        null -> false
        is String -> value.isNotBlank()
        is Collection<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        else -> true
    }

    private fun fieldLabel(id: String, spec: JsonObject?, hint: JsonObject?): String {
        val candidates = listOf(
            hint?.get("label"),
            hint?.get("title"),
            spec?.get("title"),
            spec?.get("description"),
        )
        return candidates.firstNotNullOfOrNull { (it?.plain() as? String)?.takeIf { text -> text.isNotBlank() } }
            ?: id
    }

    /** 控件优先级：卡片本地 ui 提示 → 远端 ui_schema → JSON Schema 类型推断。 */
    private fun widgetOf(spec: JsonObject?, hint: JsonObject?): String {
        val explicit = (hint?.get("widget")?.plain() as? String)?.trim()?.lowercase()
        if (explicit == "checkbox") return "switch"
        if (explicit != null && explicit in WIDGETS) return explicit
        val type = (spec?.get("type")?.plain() as? String)?.trim()?.lowercase()
        return when {
            type == "boolean" -> "switch"
            enumOptions(spec, hint).isNotEmpty() -> "select"
            type == "integer" || type == "number" ->
                if (number(spec["minimum"] ?: spec["min"]) != null && number(spec["maximum"] ?: spec["max"]) != null) {
                    "slider"
                } else {
                    "number"
                }

            type == "array" -> "input"
            else -> "input"
        }
    }

    private fun enumOptions(spec: JsonObject?, hint: JsonObject?): List<String> {
        val source = (hint?.get("options") ?: spec?.get("enum")) as? JsonArray ?: return emptyList()
        return source.mapNotNull { element ->
            when (val value = element.plain()) {
                null -> null
                is String -> value.takeIf { it.isNotBlank() }
                else -> value.toString()
            }
        }
    }

    private fun defaultOf(spec: JsonObject?, hint: JsonObject?): Any? =
        (hint?.get("default") ?: spec?.get("default"))?.plain()

    private fun number(element: JsonElement?): Double? = (element as? JsonPrimitive)?.doubleOrNull

    private fun preview(value: Any?): String {
        val text = when (value) {
            null -> ""
            is String -> value
            is Collection<*>, is Map<*, *> -> value.toString()
            else -> value.toString()
        }
        return if (text.length <= VALUE_PREVIEW_LIMIT) text else text.take(VALUE_PREVIEW_LIMIT) + "…"
    }

    /** JSON 元素 → 普通 Kotlin 值；对象 / 数组不作为表单默认值使用。 */
    private fun JsonElement?.plain(): Any? = when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            doubleOrNull != null -> doubleOrNull
            else -> contentOrNull
        }

        else -> null
    }
}

/**
 * 宿主 schema 表单入口（`KhatKitUiHost` 在 `UiRequest.Form` 分支里按
 * [KhatKitSchemaFormSpec.isSchemaDriven] 选择它或脚本表单）。
 *
 * 这里只做本地化装饰 + 交给 [KhatKitForm] 渲染，不改变字段语义：
 * 提示行与「已提供」标题走 strings.xml，缺参数量由 [KhatKitSchemaFormSpec.requiredCount] 数出来。
 * 条目结构异常时退化成「原样交给渲染器」——渲染失败由调用方按降级处理，不影响卡片执行。
 */
@Composable
fun KhatKitSchemaForm(
    title: String,
    items: List<Map<String, Any?>>,
    onSubmit: (Map<String, Any?>) -> Unit,
    onCancel: () -> Unit,
    options: UiSheetOptions = UiSheetOptions(),
    pickerHost: FormPickerHost? = null,
) {
    // 纯函数只读 map 键，不抛异常；控件类型已在 [KhatKitSchemaFormSpec.WIDGETS] 收敛到渲染器支持的集合，
    // 所以这里不再包 runCatching（@Composable 调用不允许出现在 runCatching 里）。
    val required = KhatKitSchemaFormSpec.requiredCount(items)
    val rows = items.map { item ->
        if (KhatKitSchemaFormSpec.isProvidedRow(item)) {
            val label = item["label"]?.toString().orEmpty()
            val value = item["value"]?.toString().orEmpty()
            item + ("label" to stringResource(R.string.khatkit_schema_form_provided, label, value))
        } else {
            item
        }
    }
    val localized = if (required > 0) {
        listOf(
            mapOf<String, Any?>(
                "type" to "text",
                "label" to stringResource(R.string.khatkit_schema_form_hint, required),
            )
        ) + rows
    } else {
        rows
    }

    KhatKitForm(
        title = stringResource(R.string.khatkit_schema_form_title, title),
        items = localized,
        onSubmit = onSubmit,
        onCancel = onCancel,
        options = options,
        pickerHost = pickerHost,
    )
}

/**
 * 缺参时用宿主表单补参，然后把结果并回同一次调用。
 *
 * - [plan] 没有缺项 → 直接返回 [args]，[ask] 不会被调用（AI 全参数调用不弹表单）；
 * - [ask] 抛异常 / 返回 null（用户取消、宿主没接线、渲染失败）→ 返回 [args]，绝不向上抛，
 *   卡片带着原参数继续执行，只是缺参字段由脚本自己兜底。
 */
internal suspend fun fillSchemaArgs(
    plan: KhatKitSchemaFormSpec.Plan,
    args: Map<String, Any?>,
    ask: suspend (List<Map<String, Any?>>, String) -> Map<String, Any?>?,
): Map<String, Any?> {
    if (!plan.shouldAsk) return args
    val values = runCatching { ask(plan.items, plan.name) }.getOrNull() ?: return args
    return KhatKitSchemaFormSpec.merge(args, values)
}
