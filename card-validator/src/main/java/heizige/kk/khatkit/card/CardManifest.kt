package heizige.kk.khatkit.card

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 卡片 manifest 规范（KhatKit 设计文档第 5 节）。
 *
 * 刻意与 MCP tool schema 对齐：`parameters` 就是标准 JSON Schema，
 * 将来把卡片暴露成 MCP tool 时零成本。
 */
@Serializable
data class CardManifest(
    /** 全局唯一，小写下划线 */
    val name: String,
    val version: String = "0.0.0",
    val description: String = "",
    val author: String = "",
    val license: String = "",

    /** lua | js | auto | command */
    val engine: String = "auto",
    val entry: Entry = Entry(),

    /** none | elevated */
    val privilege: String = "none",

    val requires: Requires = Requires(),
    val network: Network = Network(),

    /** 标准 JSON Schema */
    val parameters: JsonObject = JsonObject(emptyMap()),

    /** 声明哪些参数必须由用户 UI 填写：{ 参数名: { source, widget, ... } } */
    val ui: Map<String, JsonObject> = emptyMap(),

    val tags: Tags? = null,
    val compliance: Compliance? = null,
    val store: Store = Store(),

    /** engine == command 时的命令模板，`{}` 占位符由宿主白名单转义 */
    val command: String? = null,
) {
    @Serializable
    data class Entry(
        val lua: String? = null,
        val js: String? = null,
    )

    @Serializable
    data class Requires(
        val bridges: List<String> = emptyList(),
        val libs: List<LibRequirement> = emptyList(),
        val bins: List<String> = emptyList(),
        val env: List<String> = emptyList(),
    )

    @Serializable
    data class LibRequirement(
        val name: String,
        /** lua | js */
        val lang: String = "js",
        val version: String = "*",
    )

    @Serializable
    data class Network(
        /** 域名白名单，空数组 = 禁止联网 */
        val allow: List<String> = emptyList(),
    )

    /** 两个正交维度 + 一个可选场景，见 TagVocabulary */
    @Serializable
    data class Tags(
        val domain: String,
        val action: String,
        val scene: String? = null,
    )

    @Serializable
    data class Compliance(
        /** low | medium | high */
        val risk: String = "low",
        val note: String = "",
    )

    @Serializable
    data class Store(
        @SerialName("quota_mb") val quotaMb: Int = 50,
        val secret: Boolean = false,
    )

    val isCommand: Boolean get() = engine == "command"

    /** 卡片要求的能力集合，供能力协商过滤 */
    val requiredBridges: Set<String> get() = requires.bridges.toSet()
}
