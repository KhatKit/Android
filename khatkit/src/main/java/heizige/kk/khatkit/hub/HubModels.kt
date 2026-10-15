@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package heizige.kk.khatkit.hub

import heizige.kk.khatkit.card.CardManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

@Serializable
data class CardTags(
    val domain: String,
    val action: String,
    val scene: String? = null,
)

/**
 * 卡片索引条目（旧 `/api/khatkit/search` 与卡片索引）。
 *
 * §8 P0-3：补上统一市场预留的展示 / 配置字段。**全部带默认值**，
 * 老服务端不返回这些字段时整条照旧反序列化（旧响应零回归）。
 */
@Serializable
data class CardIndexEntry(
    val name: String,
    val version: String,
    /**
     * 卡片简介。服务端 `String | LocaleMap`（§3.3）两种形态都收：
     * 纯字符串按原样读，语言映射按四级顺序解析一次（解析期取设备当前语言）。
     * 需要运行时切语言请用统一市场的 [HubItemSummary.descriptionText]。
     */
    @Serializable(with = LocaleTextSerializer::class)
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
    /**
     * 下载量：服务端索引里的计数（下载接口每次 +1）。0 = 未知或确实没人下载，客户端不展示。
     * 旧 Hub 不给这个字段时保持 0，不会因为缺字段让整条索引反序列化失败。
     */
    val downloads: Long = 0,
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
    // ===== §8 P0-3：统一市场（/api/items）预留字段，全部可缺省 =====
    /** 语言映射标题（`display_name`，§3.3）。旧索引没有则空映射。 */
    @JsonNames("display_name")
    val displayName: LocaleMap = LocaleMap.Empty,
    /** 跨源去重键 `(kind, slug)`（§7.1）；旧卡片索引无该字段时保持空串。 */
    val slug: String = "",
    /** 图标（§4）：下载地址 / sha256 / MIME / 无障碍替代文案（`icon_alt` 同样是语言映射）。 */
    @JsonNames("icon_url")
    val iconUrl: String = "",
    @JsonNames("icon_sha256")
    val iconSha256: String = "",
    @JsonNames("icon_mime")
    val iconMime: String = "",
    @JsonNames("icon_alt")
    val iconAlt: LocaleMap = LocaleMap.Empty,
    /** 条目级配置表单 schema（§5.1 / §7.1）。 */
    @JsonNames("config_schema")
    val configSchema: JsonObject = JsonObject(emptyMap()),
    /** 宿主渲染用展示 schema（§5.2 / §7.1）。 */
    @JsonNames("ui_schema")
    val uiSchema: JsonObject = JsonObject(emptyMap()),
    /** 函数式条目声明：input / ui / approval / output schema（§5.1）。 */
    val function: JsonObject = JsonObject(emptyMap()),
    /** 来源绑定（GitHub 等，§7.2/§7.3）；没有来源时为 null。 */
    val source: HubItemSource? = null,
    /** 兼容性声明：min/max app 版本、abi、权限差异（§7.1）。 */
    val compatibility: JsonObject = JsonObject(emptyMap()),
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

// ===== 统一市场条目（§7.1，`GET /api/items`）=====

/**
 * 市场条目：服务端 `KhatKitHubItemSummary` / `KhatKitHubItemDetail`（§7.1）。
 *
 * 字段全部带默认值 —— 老服务端不返回新字段时整条仍能解析；两个接口共用本模型
 * （详情多出来的 `*_json` 字段也能读）。`description` 兼容 `String | LocaleMap`（§3.3）。
 *
 * 去重键用 [dedupeKey]：同一 `(kind, slug)` 同时来自旧资源索引与统一市场时保留统一市场版本。
 */
@Serializable
data class HubItemSummary(
    val id: String = "",
    val name: String = "",
    /** card / skill / mcp / prompt / workflow / toolpkg */
    val kind: String = "",
    /** 跨源去重键（§7.1）；旧服务端缺省为空串，[dedupeKey] 会退回 name。 */
    val slug: String = "",
    val version: String = "",
    /** 语言映射标题（§3.3），用 [title] 取值。 */
    @JsonNames("display_name")
    val displayName: LocaleMap = LocaleMap.Empty,
    /** 语言映射简介（§3.3），用 [descriptionText] 取值。 */
    val description: LocaleMap = LocaleMap.Empty,
    val author: String = "",
    val license: String = "",
    val changelog: String = "",
    val sha256: String = "",
    // ===== 图标（§4）=====
    @JsonNames("icon_url")
    val iconUrl: String = "",
    @JsonNames("icon_sha256")
    val iconSha256: String = "",
    @JsonNames("icon_mime")
    val iconMime: String = "",
    @JsonNames("icon_alt")
    val iconAlt: LocaleMap = LocaleMap.Empty,
    /** `List<String> | LocaleMap`（§7.1）。 */
    val tags: HubTags = HubTags.Empty,
    // ===== schema（§5.1 / §5.2 / §7.1）=====
    @JsonNames("config_schema")
    val configSchema: JsonObject = JsonObject(emptyMap()),
    @JsonNames("ui_schema")
    val uiSchema: JsonObject = JsonObject(emptyMap()),
    val function: JsonObject = JsonObject(emptyMap()),
    /** 来源绑定（GitHub 等，§7.2/§7.3）；无来源为 null。 */
    val source: HubItemSource? = null,
    /** 兼容性声明：min/max app 版本、abi、权限差异（§7.1）。 */
    val compatibility: JsonObject = JsonObject(emptyMap()),
    @JsonNames("min_app_version")
    val minAppVersion: String? = null,
    @JsonNames("api_version")
    val apiVersion: String? = null,
    /** approved / pending / revoked（§7.1、P0-2）。 */
    val status: String = "",
    @JsonNames("created_at")
    val createdAt: String = "",
    @JsonNames("updated_at")
    val updatedAt: String = "",
    val upvotes: Int = 0,
    val score: Long = 0,
    val preferred: Boolean = false,
    @JsonNames("audit_report")
    val auditReport: JsonObject = JsonObject(emptyMap()),
    @JsonNames("audit_score")
    val auditScore: Int? = null,
    val pricing: JsonObject = JsonObject(emptyMap()),
    val requires: JsonArray = JsonArray(emptyList()),
    val files: JsonArray = JsonArray(emptyList()),
    // 详情接口（`/api/items/{id}`）独有的不透明 JSON 字符串
    @JsonNames("manifest_json")
    val manifestJson: String = "",
    @JsonNames("files_json")
    val filesJson: String = "",
    @JsonNames("requires_json")
    val requiresJson: String = "",
    @JsonNames("pricing_json")
    val pricingJson: String = "",
) {
    /** 标题：语言映射为空时退回 [name]。 */
    fun title(localeTags: List<String> = LocaleMapResolver.currentLocaleTags()): String =
        LocaleMapResolver.resolve(displayName, localeTags).ifBlank { name }

    /** 简介：按 §3.3 选当前语言。 */
    fun descriptionText(localeTags: List<String> = LocaleMapResolver.currentLocaleTags()): String =
        LocaleMapResolver.resolve(description, localeTags)

    /** 图标无障碍描述：按 §3.3 选当前语言。 */
    fun iconAltText(localeTags: List<String> = LocaleMapResolver.currentLocaleTags()): String =
        LocaleMapResolver.resolve(iconAlt, localeTags)

    /** 跨源去重键 `(kind, slug)`（§7.1）；slug 缺失时退回 name。 */
    val dedupeKey: String get() = "$kind:${slug.ifBlank { name }}"
}

/** `GET /api/items` 的响应体：`{"items": [...]}`。 */
@Serializable
data class HubItemList(
    val items: List<HubItemSummary> = emptyList(),
)

/**
 * 条目来源绑定（§7.2/§7.3）。字段名对齐服务端列名 `source_*`，
 * 同时收短名（`type` / `url` / `ref` / …），方便来源接口还没落定前先跑通解析。
 */
@Serializable
data class HubItemSource(
    @JsonNames("source_type", "kind")
    val type: String = "",
    @JsonNames("source_url", "html_url")
    val url: String = "",
    @JsonNames("source_owner", "owner")
    val owner: String = "",
    @JsonNames("source_repo", "repo")
    val repo: String = "",
    @JsonNames("source_ref", "ref")
    val ref: String = "",
    @JsonNames("source_path", "path")
    val path: String = "",
    @JsonNames("source_commit", "commit")
    val commit: String = "",
    @JsonNames("sync_mode")
    val syncMode: String = "",
    @JsonNames("auto_publish")
    val autoPublish: Boolean = false,
    @JsonNames("sync_status")
    val syncStatus: String = "",
    @JsonNames("last_sync_at")
    val lastSyncAt: String = "",
    @JsonNames("last_sync_error")
    val lastSyncError: String = "",
)
