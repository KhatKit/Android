package heizige.kk.khatkit.ui

/** Kept in the runtime module so validation precedes UI dispatch without a module cycle. */
data class UiNode(val type: String, val props: Map<String, Any?>, val children: List<UiNode>) {
    fun walk(): Sequence<UiNode> = sequence {
        yield(this@UiNode)
        children.forEach { yieldAll(it.walk()) }
    }

    fun fields(): List<UiNode> = walk().filter { it.type in UiNodeParser.fields }.toList()

    fun initialValues(): Map<String, Any?> = fields().associate { field ->
        val initial = field.props["value"]
        field.props["id"] as String to (if (field.type == "FilePicker" && initial is List<*>) initial.joinToString("\n") else initial ?: when (field.type) {
            "Switch", "Checkbox" -> false
            "Slider" -> field.props["min"] ?: 0.0
            else -> ""
        })
    }

    fun collect(values: Map<String, Any?>): Map<String, Any?> =
        initialValues().mapValues { (id, default) -> values[id] ?: default }

    fun errors(values: Map<String, Any?>): Map<String, String> = buildMap {
        val collected = collect(values)
        fields().forEach { field ->
            val p = field.props
            val id = p["id"] as String
            val value = collected[id]
            val empty = value == null || value is String && value.isBlank() || value is List<*> && value.isEmpty()
            if (p["required"] == true && empty) put(id, "请填写此项")
            if (!empty && field.type in setOf("Switch", "Checkbox") && value !is Boolean) put(id, "请输入布尔值")
            if (!empty && field.type in setOf("TextField", "DirPicker") && value !is String) put(id, "请输入文本")
            if (!empty && field.type == "FilePicker" && value !is String &&
                !(value is List<*> && value.all { it is String })) put(id, "请输入文件路径")
            if (!empty && field.type in setOf("NumberField", "Slider")) {
                val n = (value as? Number)?.toDouble()
                val min = (p["min"] as? Number)?.toDouble()
                val max = (p["max"] as? Number)?.toDouble()
                val step = (p["step"] as? Number)?.toDouble()
                if (n == null || !n.isFinite()) put(id, "请输入有效数字")
                else if (min != null && n < min || max != null && n > max) put(id, "数值超出范围")
                else if (step != null) {
                    val units = (n - (min ?: 0.0)) / step
                    if (kotlin.math.abs(units - kotlin.math.round(units)) > 1e-6) put(id, "数值不符合步长 $step")
                }
            }
            if (!empty && field.type in setOf("Select", "RadioGroup") && value !in (p["options"] as? List<*>).orEmpty()) {
                put(id, "请选择列表中的值")
            }
        }
    }
}

class UiNodeException(message: String) : IllegalArgumentException(message)

object UiNodeParser {
    val fields = setOf("TextField", "NumberField", "Switch", "Checkbox", "Slider", "Select", "RadioGroup", "FilePicker", "DirPicker")
    private val containers = setOf("Column", "Row", "Box", "Card", "Section")
    private fun schema(vararg entries: Pair<String, String>) = entries.toMap()
    private val schemas = mapOf(
        "Column" to schema("spacing" to "number", "hAlign" to "start|center|end", "scrollable" to "boolean", "modifier" to "modifier"),
        "Row" to schema("spacing" to "number", "vAlign" to "start|center|end|space_between", "modifier" to "modifier"),
        "Box" to schema("hAlign" to "start|center|end", "vAlign" to "start|center|end", "modifier" to "modifier"),
        "Spacer" to schema("height" to "number", "width" to "number"),
        "Divider" to emptyMap(),
        "Card" to schema("title" to "string", "modifier" to "modifier"),
        "Section" to schema("title" to "string", "modifier" to "modifier"),
        "Text" to schema("text" to "string", "style" to "title|subtitle|body|label|code", "tone" to "default|muted|primary|error", "bold" to "boolean", "modifier" to "modifier"),
        "Markdown" to schema("text" to "string", "modifier" to "modifier"),
        "Image" to schema("src" to "string", "contentDescription" to "string", "modifier" to "modifier"),
        "Badge" to schema("text" to "string", "tone" to "default|primary|success|warning|error"),
        "ProgressBar" to schema("ratio" to "number", "label" to "string"),
        "TextField" to schema("placeholder" to "string", "multiline" to "boolean", "lines" to "number"),
        "NumberField" to schema("min" to "number", "max" to "number", "step" to "number"),
        "Slider" to schema("min" to "number", "max" to "number", "step" to "number"),
        "Switch" to emptyMap(), "Checkbox" to emptyMap(),
        "Select" to schema("options" to "strings"), "RadioGroup" to schema("options" to "strings"),
        "FilePicker" to schema("filter" to "image|video|audio|document|any", "multiple" to "boolean"),
        "DirPicker" to emptyMap(),
        "Button" to schema("label" to "string", "action" to "string", "tone" to "primary|default|danger", "disabled" to "boolean"),
    ).mapValues { (name, props) ->
        if (name !in fields) props else props + schema(
            "id" to "string", "label" to "string", "hint" to "string", "required" to "boolean",
            "value" to when (name) {
                "NumberField", "Slider" -> "number"
                "Switch", "Checkbox" -> "boolean"
                "FilePicker" -> "paths"
                else -> "string"
            },
        )
    }
    val components: Set<String> get() = schemas.keys

    fun parse(raw: Map<*, *>): UiNode {
        val ids = mutableSetOf<String>()
        var count = 0
        fun visit(value: Any?, depth: Int): UiNode {
            val node = value as? Map<*, *> ?: fail("node", "expected object")
            val name = node["__ui"] as? String ?: fail("__ui", "expected component name")
            val schema = schemas[name] ?: fail(name, "unknown component")
            if (depth > 32) fail(name, "tree depth exceeds 32")
            if (++count > 512) fail(name, "tree node count exceeds 512")
            node.keys.firstOrNull { it !in setOf("__ui", "props", "children") }?.let { fail("$name.$it", "unknown node property") }
            val props = node["props"] as? Map<*, *> ?: fail("$name.props", "expected object")
            val copy = linkedMapOf<String, Any?>()
            props.forEach { (key, v) ->
                val kind = schema[key] ?: fail("$name.$key", "unknown property")
                checkValue(v, kind, "$name.$key")
                copy[key as String] = when (v) {
                    is List<*> -> v.toList()
                    is Map<*, *> -> v.toMap()
                    else -> v
                }
            }
            if (name in fields) {
                val id = copy["id"] as? String
                if (id.isNullOrBlank()) fail("$name.id", "required")
                if (!ids.add(id)) fail("$name.id", "duplicate $id")
            }
            if (name == "Section" && copy["title"] == null) fail("$name.title", "required")
            if (name == "Image") {
                val src = copy["src"] as? String
                if (src.isNullOrEmpty() || !src.startsWith("/") && !src.startsWith("content://")) {
                    fail("$name.src", "expected local absolute path or content URI")
                }
            }
            if (name == "Button" && (copy["action"] as? String).isNullOrEmpty()) fail("$name.action", "required")
            if (name == "ProgressBar" && copy["ratio"] != null && (copy["ratio"] as Number).toDouble() !in 0.0..1.0) fail("$name.ratio", "expected 0..1")
            listOf("spacing", "width", "height", "lines").forEach { key ->
                (copy[key] as? Number)?.toDouble()?.let {
                    if (it < 0 || key == "lines" && (it < 1 || it % 1 != 0.0)) fail("$name.$key", "invalid size")
                }
            }
            val min = (copy["min"] as? Number)?.toDouble()
            val max = (copy["max"] as? Number)?.toDouble()
            if (min != null && max != null && min >= max) fail("$name.max", "must exceed min")
            if ((copy["step"] as? Number)?.toDouble()?.let { it <= 0 } == true) fail("$name.step", "must be positive")
            val children = array(node["children"], "$name.children")
            if (name !in containers && children.isNotEmpty()) fail("$name.children", "leaf cannot contain children")
            return UiNode(name, copy, children.map { visit(it, depth + 1) })
        }
        return visit(raw, 1)
    }

    // The existing Rust encoder serializes an empty Lua table as {}, including empty arrays.
    private fun array(value: Any?, where: String): List<*> = when {
        value is List<*> -> value
        value is Map<*, *> && value.isEmpty() -> emptyList<Any?>()
        else -> fail(where, "expected array")
    }

    private fun checkValue(v: Any?, kind: String, where: String) {
        val valid = when (kind) {
            "string" -> v is String
            "number" -> v is Number && v.toDouble().isFinite()
            "boolean" -> v is Boolean
            "strings" -> array(v, where).all { it is String }
            "paths" -> v is String || array(v, where).all { it is String }
            "modifier" -> { if (v == null) fail(where, "expected Modifier"); modifierOps(v, where); true }
            else -> v is String && v in kind.split('|')
        }
        if (!valid) fail(where, "expected $kind")
    }

    fun modifierOps(value: Any?, where: String = "Modifier"): List<List<Any?>> {
        if (value == null) return emptyList()
        val map = value as? Map<*, *> ?: fail(where, "expected Modifier")
        if (map.keys != setOf("__mod")) fail(where, "invalid Modifier property")
        val ops = array(map["__mod"], where)
        if (ops.size > 64) fail(where, "too many Modifier operations")
        return ops.map { raw ->
            val op = if (raw is String) listOf(raw) else array(raw, where)
            val name = op.firstOrNull() as? String ?: fail(where, "invalid operation")
            val args = op.drop(1)
            val valid = when (name) {
                "fillMaxSize", "fillMaxWidth", "fillMaxHeight", "scrollable" -> args.isEmpty()
                "width", "height", "size", "weight", "padding" -> {
                    val arity = if (name == "padding") args.size in setOf(1, 2, 4) else args.size == 1
                    arity && args.all { it is Number && it.toDouble().isFinite() && it.toDouble() >= 0 && (name != "weight" || it.toDouble() > 0) }
                }
                "visible" -> args.size == 1 && args[0] is Boolean
                "clip" -> args.size in 1..2 && args[0] in setOf("rounded", "circle") &&
                    (args.size == 1 || args[1] is Number && (args[1] as Number).toDouble().let { it.isFinite() && it >= 0 })
                else -> false
            }
            if (!valid) fail("$where.$name", "invalid operation or arguments")
            op.toList()
        }
    }

    private fun fail(where: String, why: String): Nothing = throw UiNodeException("ui.$where: $why")
}
