package heizige.kk.khatkit.hub

import heizige.kk.khatkit.card.CardManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

@Serializable
data class CardTags(
    val domain: String,
    val action: String,
    val scene: String? = null,
)

@Serializable
data class CardIndexEntry(
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val tags: CardTags? = null,
    val privilege: String = "none",
    val engine: String = "auto",
    val bridges: List<String> = emptyList(),
    /** 触发方式：ai（AI 工具）/ user（用户手动），未提供时按旧卡片默认两者都允许 */
    val triggers: List<String> = CardManifest.DEFAULT_TRIGGERS,
    /** 卡片包下载地址（相对 CDN 根或绝对 URL） */
    val url: String,
    /** sha256 */
    val hash: String = "",
    val sizeBytes: Long = 0,
    val updatedAt: Long = 0,
    /** AI 摘要（给用户看，也用于语义召回） */
    val summary: String = "",
    /** 标准 JSON Schema：未下载时也能把卡片暴露给 AI（使用时再拉包） */
    val parameters: JsonObject = JsonObject(emptyMap()),
    /** 依赖的共享库 */
    val libs: List<CardLibRef> = emptyList(),
    /**
     * 依赖的原生依赖包（随卡片从 Hub 下载，DexClassLoader 加载）；
     * 与 manifest.requires.dependencies 一致，供能力过滤与预下载决策。
     * 兼容旧 Hub 索引字段 `plugins`（反序列化回退）。
     */
    @JsonNames("plugins")
    val dependencies: List<CardManifest.DependencyReq> = emptyList(),
)

@Serializable
data class CardLibRef(
    val name: String,
    val lang: String = "js",
    val version: String = "*",
)

@Serializable
data class CardIndex(
    val updatedAt: String = "",
    val cards: List<CardIndexEntry> = emptyList(),
)

@Serializable
data class LibIndexEntry(
    val name: String,
    val lang: String = "js",
    val version: String,
    val url: String,
    val hash: String = "",
)

@Serializable
data class LibIndex(
    val updatedAt: String = "",
    val libs: List<LibIndexEntry> = emptyList(),
)

/** 语义召回请求：硬过滤用 capabilities，语义召回在服务端做。 */
@Serializable
data class KhatKitSearchRequest(
    val query: String,
    val capabilities: List<String> = emptyList(),
    val limit: Int = 20,
)
