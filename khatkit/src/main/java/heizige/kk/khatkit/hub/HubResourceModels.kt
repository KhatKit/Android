package heizige.kk.khatkit.hub

import kotlinx.serialization.Serializable

/**
 * 资源市场（v1）：与卡片市场并列的第二条分发通道 —— 卡片发的是「能力脚本」，
 * 这里发的是**可直接用的现成内容**：技能合集（一组 SKILL.md）、MCP 服务（一组
 * 远端 MCP 端点）、以及把两者 + 卡片打在一起的合集包。
 *
 * 契约由服务端 `GET /api/khatkit/resources/index.json` 与
 * `GET /api/khatkit/resources/{slug}/download` 提供，字段与
 * KodeHeapServer 的 `KhatKitResourceEntry` / `KhatKitResourcePackage` 一一对应。
 * 全部字段给默认值：服务端加字段不会让老客户端解析失败。
 */
@Serializable
data class HubResourceEntry(
    val slug: String = "",
    /** skill_pack（技能合集）/ mcp（MCP 服务）/ bundle（混合合集） */
    val kind: String = "",
    val name: String = "",
    val description: String = "",
    val author: String = "",
    val version: String = "1.0.0",
    val tags: List<String> = emptyList(),
    /** 语义图标名（如 travel_explore / dns），客户端映射不到就用 kind 的默认图标 */
    val icon: String = "",
    val downloads: Long = 0,
    /** 包内条目数（技能数 / 服务器数 / 两者之和），列表页不下载就能显示「N 项」 */
    val itemCount: Int = 0,
    /** 包内条目名（技能名或 MCP 服务器名），用于「装没装过」的本地比对 */
    val items: List<String> = emptyList(),
    val sizeBytes: Long = 0,
    val updatedAt: Long = 0,
    val featured: Boolean = false,
) {
    val isSkillPack: Boolean get() = kind == KIND_SKILL_PACK
    val isMcp: Boolean get() = kind == KIND_MCP
    val isBundle: Boolean get() = kind == KIND_BUNDLE

    companion object {
        const val KIND_SKILL_PACK = "skill_pack"
        const val KIND_MCP = "mcp"
        const val KIND_BUNDLE = "bundle"
    }
}

@Serializable
data class HubResourceIndex(
    val updatedAt: String = "",
    val resources: List<HubResourceEntry> = emptyList(),
)

/** 一个技能：`files` 是相对路径 -> 文本内容，必含 `SKILL.md`。 */
@Serializable
data class HubResourceSkill(
    val name: String = "",
    val description: String = "",
    val files: Map<String, String> = emptyMap(),
)

/** 一个远端 MCP 服务：transport 目前只有 streamable_http / sse 两种带 URL 的形态。 */
@Serializable
data class HubResourceMcpServer(
    val name: String = "",
    val transport: String = "streamable_http",
    val url: String = "",
    /** [[key, value]]，客户端落成 McpCommonOptions.headers 的 Pair 列表 */
    val headers: List<List<String>> = emptyList(),
    val description: String = "",
)

@Serializable
data class HubResourcePackage(
    val resource: HubResourceEntry,
    val skills: List<HubResourceSkill> = emptyList(),
    val mcpServers: List<HubResourceMcpServer> = emptyList(),
    /** 随合集一起安装的卡片（slug），走卡片通道单独下载 */
    val cards: List<String> = emptyList(),
    val notes: String = "",
)
