package heizige.kk.khatkit.hub

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * 服务端多语言文案映射（《市场探索页调整方案》§3.3）。
 *
 * 两种形态都收：
 *  - 映射：`{"zh-CN": "深度研究", "en": "Deep Research"}`
 *  - 旧纯字符串：`"Deep Research"`（内部记到 [UNKEYED] 键上 —— 没有语言标签可比对，
 *    只能被第 4 级「首个非空」兜底选中）
 *
 * 选择顺序见 [LocaleMapResolver]：完整标签 → 语言 → `en` → 首个非空。
 * 值一律保留服务端给出的顺序（服务端默认值通常写在第一个）。
 */
@Serializable(with = LocaleMapSerializer::class)
data class LocaleMap(val values: Map<String, String> = emptyMap()) {
    val isEmpty: Boolean get() = values.values.none { it.isNotBlank() }
    val isNotEmpty: Boolean get() = !isEmpty

    override fun toString(): String = LocaleMapResolver.resolve(this)

    companion object {
        /** 纯字符串形态的伪语言键（永远匹配不到语言标签，只能被兜底规则选中）。 */
        const val UNKEYED = ""

        val Empty = LocaleMap()

        fun of(text: String): LocaleMap =
            if (text.isBlank()) Empty else LocaleMap(linkedMapOf(UNKEYED to text))
    }
}

/**
 * `String | LocaleMap` 的纯函数解析器（§3.3 四级顺序）。
 *
 * 无状态、无依赖，可在任何线程调用；不读磁盘、不碰网络。
 */
object LocaleMapResolver {

    /**
     * 四级选择顺序：完整语言标签（`zh-CN`）→ 语言（`zh`，含 `zh-Hans-CN` 这类更长的键）
     * → `en` → 首个非空（服务端默认值 / 旧纯字符串）。
     *
     * @param localeTags 候选标签，按优先级排列；缺省用设备当前语言。
     */
    fun resolve(map: LocaleMap?, localeTags: List<String>): String {
        if (map == null || map.values.isEmpty()) return ""
        val entries = map.values.entries
            .filter { it.value.isNotBlank() }
            .map { normalize(it.key) to it.value }
        if (entries.isEmpty()) return ""

        fun firstWhere(predicate: (String) -> Boolean): String? =
            entries.firstOrNull { predicate(it.first) }?.second

        // 1) 当前 LocaleList 的完整语言标签，例如 zh-CN（允许服务端写 zh_CN）
        for (tag in localeTags) {
            val key = normalize(tag)
            if (key.isEmpty()) continue
            firstWhere { it == key }?.let { return it }
        }
        // 2) 当前语言，例如 zh
        for (tag in localeTags) {
            val language = languageOf(tag)
            if (language.isEmpty()) continue
            firstWhere { it == language || it.startsWith("$language-") }?.let { return it }
        }
        // 3) en（含 en-US / en-GB）
        firstWhere { it == "en" || it.startsWith("en-") }?.let { return it }
        // 4) 服务端默认值 / 第一个非空值
        return entries.first().second
    }

    /** 用设备当前语言解析。 */
    fun resolve(map: LocaleMap?): String = resolve(map, currentLocaleTags())

    /** 宽容解析原始 JSON（`String | LocaleMap`）。 */
    fun resolveJson(element: JsonElement?, localeTags: List<String>): String =
        resolve(fromJson(element), localeTags)

    /** 宽容解析原始 JSON，用设备当前语言。 */
    fun resolveJson(element: JsonElement?): String = resolveJson(element, currentLocaleTags())

    /** 原始 JSON → [LocaleMap]：字符串 / 映射 / 字符串数组都收，其他一律空映射。 */
    fun fromJson(element: JsonElement?): LocaleMap = when (element) {
        is JsonObject -> LocaleMap(
            element.entries.mapNotNull { (key, value) ->
                val text = (value as? JsonPrimitive)
                    ?.takeIf { it !is JsonNull && it.isString }
                    ?.content
                    ?.takeIf { it.isNotBlank() }
                text?.let { key to it }
            }.toMap(),
        )
        is JsonPrimitive -> when {
            element is JsonNull -> LocaleMap.Empty
            element.isString -> LocaleMap.of(element.content)
            else -> LocaleMap.Empty
        }
        is JsonArray -> LocaleMap.of(
            element.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
                .filter { it.isNotBlank() }
                .joinToString(", "),
        )
        null -> LocaleMap.Empty
    }

    /**
     * 设备当前语言的候选标签，按优先级：`zh-CN` → `zh`（带国家时补 `zh-CN` 形式）。
     * 只依赖 `java.util.Locale`，不依赖 Android 框架，便于纯 JVM 单测。
     */
    fun currentLocaleTags(): List<String> {
        val locale = Locale.getDefault()
        val tags = linkedSetOf<String>()
        locale.toLanguageTag()
            .takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
            ?.let { tags += it }
        if (locale.language.isNotBlank()) {
            tags += locale.language
            if (locale.country.isNotBlank()) tags += "${locale.language}-${locale.country}"
        }
        return tags.toList()
    }

    private fun normalize(tag: String): String = tag.trim().lowercase().replace('_', '-')

    private fun languageOf(tag: String): String = normalize(tag).substringBefore('-')
}

/**
 * [LocaleMap] 的宽容序列化器：反序列化收 `String | Object | Array` 三种形态，
 * 序列化时纯字符串形态写回字符串，其余写对象。
 */
object LocaleMapSerializer : KSerializer<LocaleMap> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("heizige.kk.khatkit.hub.LocaleMap")

    override fun serialize(encoder: Encoder, value: LocaleMap) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalArgumentException("LocaleMap 只能编码为 JSON")
        jsonEncoder.encodeJsonElement(toJson(value))
    }

    override fun deserialize(decoder: Decoder): LocaleMap {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalArgumentException("LocaleMap 只能从 JSON 解码")
        return LocaleMapResolver.fromJson(jsonDecoder.decodeJsonElement())
    }

    fun toJson(value: LocaleMap): JsonElement {
        val entries = value.values.filterValues { it.isNotBlank() }
        if (entries.isEmpty()) return JsonObject(emptyMap())
        if (entries.size == 1 && entries.keys.first() == LocaleMap.UNKEYED) {
            return JsonPrimitive(entries.values.first())
        }
        return JsonObject(entries.mapValues { JsonPrimitive(it.value) })
    }
}

/**
 * `String | LocaleMap` 字段（如 `CardIndexEntry.description`）的宽容解码：
 * 服务端把字段从纯字符串改成语言映射后，老模型也不会整条反序列化失败。
 * 解析期按 §3.3 顺序取一次文本；需要运行时切语言时请用 [HubItemSummary.descriptionText]。
 */
object LocaleTextSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("heizige.kk.khatkit.hub.LocaleText", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return LocaleMapResolver.resolveJson(jsonDecoder.decodeJsonElement())
    }
}

/**
 * 标签（§7.1 的 `tags` `List<String> | LocaleMap`）。
 *
 * 旧服务端给字符串数组，新服务端可能给语言映射；两种都收。
 */
@Serializable(with = HubTagsSerializer::class)
data class HubTags(
    val values: List<String> = emptyList(),
    val byLocale: LocaleMap = LocaleMap.Empty,
) {
    val isEmpty: Boolean get() = values.isEmpty() && byLocale.isEmpty
    val isNotEmpty: Boolean get() = !isEmpty

    /** 按 §3.3 选中语言后拆成标签列表（多语言标签用逗号/分号分隔）。 */
    fun resolve(localeTags: List<String> = LocaleMapResolver.currentLocaleTags()): List<String> {
        if (byLocale.isNotEmpty) {
            val text = LocaleMapResolver.resolve(byLocale, localeTags)
            if (text.isNotBlank()) {
                return text.split(',', '，', ';', '；')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
        }
        return values
    }

    companion object {
        val Empty = HubTags()

        fun of(values: List<String>) = HubTags(values = values)
    }
}

/** [HubTags] 的宽容序列化器：数组 → 列表，对象 → 语言映射，字符串 → 逗号分隔列表。 */
object HubTagsSerializer : KSerializer<HubTags> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("heizige.kk.khatkit.hub.HubTags")

    override fun serialize(encoder: Encoder, value: HubTags) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalArgumentException("HubTags 只能编码为 JSON")
        val element = if (value.byLocale.isNotEmpty) {
            LocaleMapSerializer.toJson(value.byLocale)
        } else {
            JsonArray(value.values.map { JsonPrimitive(it) })
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): HubTags {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalArgumentException("HubTags 只能从 JSON 解码")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> HubTags(
                values = element
                    .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
                    .filter { it.isNotBlank() },
            )
            is JsonObject -> HubTags(byLocale = LocaleMapResolver.fromJson(element))
            is JsonPrimitive -> HubTags(
                values = if (element is JsonNull) {
                    emptyList()
                } else {
                    element.content.split(',', '，', ';', '；').map { it.trim() }.filter { it.isNotEmpty() }
                },
            )
        }
    }
}
