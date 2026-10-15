package heizige.kk.khatkit.app.feature.explore

import heizige.kk.khatkit.app.core.data.ai.mcp.McpCommonOptions
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.mcp.McpStatus
import heizige.kk.khatkit.app.core.data.ai.mcp.serverUrl
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.hub.HubResourceMcpServer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * P0-5：市场 MCP 条目的必填识别、安装落盘决策与状态三态。
 *
 * 落盘决策直接调用 [ExploreMarketViewModel.applyMcpInstallDecision]（VM 的安装/保存
 * 唯一入口，纯函数）。`ExploreMarketViewModel` 的构造依赖 Context/DataStore 这类
 * Android 对象，app 的单测只有 JUnit、没有 mock 框架，所以测的是 VM 自己的决策函数，
 * 而不是 new 一个 VM 实例 —— 与安装路径调用的是同一份代码。
 */
class McpInstallViewModelTest {

    private val assistantId = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
    private val otherServerId = Uuid.parse("11111111-2222-3333-4444-555555555555")

    private fun serverWith(headers: List<List<String>>, transport: String = "streamable_http") =
        HubResourceMcpServer(
            name = "docs-mcp",
            transport = transport,
            url = "https://mcp.example.com/mcp?token=sk-live-abcdef123456",
            headers = headers,
        )

    private fun placeholderServer() = serverWith(
        headers = listOf(
            listOf("Authorization", "\${API_KEY}"),
            listOf("X-Trace", "trace-on"),
        ),
    )

    private fun settingsWith(mcpServers: Set<Uuid>) = Settings(
        assistantId = assistantId,
        assistants = listOf(Assistant(id = assistantId, name = "主助手", mcpServers = mcpServers)),
    )

    // ===== 1. 必填识别 =====

    @Test
    fun `占位头的 MCP 服务被判为缺必填且 enable=false`() {
        val plan = planMcpInstall(placeholderServer())

        assertTrue(plan.needsConfig)
        assertEquals(listOf("Authorization"), plan.missingRequired)
        assertFalse(plan.config.commonOptions.enable)
        // 预填名称 / 传输 / URL / 头：值原样带进表单，用户只需替换占位
        assertEquals("docs-mcp", plan.config.commonOptions.name)
        assertEquals(
            listOf("Authorization" to "\${API_KEY}", "X-Trace" to "trace-on"),
            plan.config.commonOptions.headers,
        )
        assertEquals("https://mcp.example.com/mcp?token=sk-live-abcdef123456", plan.config.serverUrl)
    }

    @Test
    fun `必填齐全的 MCP 服务 enable=true 且头全部保留`() {
        val plan = planMcpInstall(
            serverWith(headers = listOf(listOf("Authorization", "Bearer sk-live-abcdef123456"))),
        )

        assertFalse(plan.needsConfig)
        assertTrue(plan.config.commonOptions.enable)
    }

    @Test
    fun `config_schema 的 required 与 x-secret 都算必填`() {
        val schema: JsonObject = buildJsonObject {
            put("required", JsonArray(listOf(JsonPrimitive("api_key"))))
            put(
                "properties",
                buildJsonObject {
                    put("api_key", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("token", buildJsonObject { put("x-secret", JsonPrimitive(true)) })
                },
            )
        }
        val plan = planMcpInstall(serverWith(headers = emptyList()), schema)

        assertEquals(listOf("api_key", "token"), plan.missingRequired)
        assertFalse(plan.config.commonOptions.enable)
    }

    @Test
    fun `transport=sse 预填成 sse 形态`() {
        val plan = planMcpInstall(serverWith(headers = emptyList(), transport = "sse"))
        assertTrue(plan.config is McpServerConfig.SseTransportServer)
    }

    // ===== 2. 安装落盘决策（VM 入口） =====

    @Test
    fun `缺必填保存后 enable=false 且助手 mcpServers 不变`() {
        val before = settingsWith(mcpServers = setOf(otherServerId))
        val plan = planMcpInstall(placeholderServer())

        val after = ExploreMarketViewModel.applyMcpInstallDecision(before, plan)

        // 候选配置保存下来了，但被强制停用
        val saved = after.mcpServers.single()
        assertFalse(saved.commonOptions.enable)
        assertEquals("https://mcp.example.com/mcp?token=sk-live-abcdef123456", saved.serverUrl)
        assertEquals("docs-mcp", saved.commonOptions.name)
        // 助手侧一个 id 都不能多
        assertEquals(before.assistants.single().mcpServers, after.assistants.single().mcpServers)
        assertEquals(setOf(otherServerId), after.assistants.single().mcpServers)
        assertFalse(after.assistants.single().mcpServers.contains(plan.config.id))
    }

    @Test
    fun `必填齐全时才会挂到当前助手`() {
        val before = settingsWith(mcpServers = setOf(otherServerId))
        val plan = planMcpInstall(serverWith(headers = listOf(listOf("Authorization", "Bearer real"))))

        val after = ExploreMarketViewModel.applyMcpInstallDecision(before, plan)

        assertTrue(after.mcpServers.single().commonOptions.enable)
        assertTrue(after.assistants.single().mcpServers.contains(plan.config.id))
    }

    @Test
    fun `同一 URL 重复安装只覆盖不追加`() {
        val first = planMcpInstall(placeholderServer())
        val second = rePlanMcpInstall(
            first.config.let { cfg ->
                when (cfg) {
                    is McpServerConfig.StreamableHTTPServer -> cfg.copy(
                        commonOptions = cfg.commonOptions.copy(
                            headers = listOf("Authorization" to "Bearer real"),
                        ),
                    )

                    is McpServerConfig.SseTransportServer -> cfg.copy(
                        commonOptions = cfg.commonOptions.copy(
                            headers = listOf("Authorization" to "Bearer real"),
                        ),
                    )
                }
            },
        )
        val once = ExploreMarketViewModel.applyMcpInstallDecision(settingsWith(emptySet()), first)
        val twice = ExploreMarketViewModel.applyMcpInstallDecision(once, second)

        assertEquals(1, twice.mcpServers.size)
        assertEquals(1, twice.assistants.single().mcpServers.size)
        // 同一 URL 覆写复用原 id：留着原 id 才能保证助手侧引用不悬空、也不多出第二个 server
        assertEquals(first.config.id, second.config.id)
        assertTrue(twice.mcpServers.single().commonOptions.enable)
    }

    // ===== 3. 状态三态 =====

    @Test
    fun `缺必填时即使底层报连上也不显示已连接`() {
        val config = planMcpInstall(placeholderServer()).config

        assertEquals(McpCardStatus.NEEDS_CONFIG, mcpCardStatus(config, McpStatus.Connected))
        assertEquals(McpCardStatus.NEEDS_CONFIG, mcpCardStatus(config, McpStatus.Idle))
    }

    @Test
    fun `必填齐全时按连接结果给出已配置或连接失败`() {
        val config = planMcpInstall(
            serverWith(headers = listOf(listOf("Authorization", "Bearer real"))),
        ).config

        assertEquals(McpCardStatus.CONFIGURED, mcpCardStatus(config, McpStatus.Connected))
        assertEquals(McpCardStatus.CONFIGURED, mcpCardStatus(config, null))
        assertEquals(McpCardStatus.CONNECT_FAILED, mcpCardStatus(config, McpStatus.Error("boom")))
        assertEquals(McpCardStatus.CONNECT_FAILED, mcpCardStatus(config, McpStatus.NeedsAuthorization))
    }

    // ===== 4. 脱敏 =====

    @Test
    fun `secret 头做掩码且不泄露原值`() {
        val config = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(
                enable = true,
                name = "docs-mcp",
                headers = listOf(
                    "Authorization" to "Bearer sk-live-abcdef123456",
                    "X-Trace" to "trace-on",
                ),
            ),
            url = "https://mcp.example.com/mcp?api_key=sk-live-abcdef123456&page=2",
        )

        val summary = maskedMcpSummary(config)

        assertFalse(summary.contains("sk-live-abcdef123456"))
        assertFalse(summary.contains("Bearer sk-live"))
        assertTrue(summary.contains("Authorization: ••••••3456"))
        assertTrue(summary.contains("api_key=••••••3456"))
        // 非敏感字段与普通 query 保持原样
        assertTrue(summary.contains("X-Trace: trace-on"))
        assertTrue(summary.contains("page=2"))
    }

    @Test
    fun `短值全遮不残留字符`() {
        assertEquals("••••••", maskSecretValue("sk-123"))
        assertEquals("", maskSecretValue(""))
        assertEquals("https://mcp.example.com/mcp", maskMcpUrl("https://mcp.example.com/mcp"))
    }
}
