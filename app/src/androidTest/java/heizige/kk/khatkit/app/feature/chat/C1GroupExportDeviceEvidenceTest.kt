package heizige.kk.khatkit.app.feature.chat

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.ai.mcp.McpCommonOptions
import heizige.kk.khatkit.app.core.data.ai.mcp.McpOAuthState
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.db.AppDatabaseFactory
import heizige.kk.khatkit.app.core.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FilesRepository
import heizige.kk.khatkit.app.core.di.appEntryPoint
import heizige.kk.khatkit.common.android.appTempFolder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.uuid.Uuid

/**
 * C1-09「真机导出」的**设备侧证据**：在真机上从真 Room 库读回群会话，走生产导出入口
 * [TavernChatCodec.exportGroupJsonl]，经**生产 IO 助手** `writeExportTempFile`
 * （`ConversationExport.kt:836`，`GroupExportCard.kt:201` 同一份）写进应用临时目录并
 * 拿到 FileProvider `content://` URI，再把同一份字节复制到
 * `getExternalFilesDir(null)/c1-device-export/` 供 `adb pull`。
 *
 * ## 为什么不是 JVM golden 测试的重复
 *
 * `C1pGroupExportHashTest` 在开发机 JVM 里落盘 `build/`，证明的是编解码确定性。本类证明
 * 的是**设备侧**：真 SQLite 读回 → 真 Android 文件系统写 → FileProvider URI → 设备上
 * `MessageDigest` 算 SHA-256。同一份文件在设备侧与本机侧（pull 后 `sha256sum`）必须同哈希。
 *
 * ## 四类安全检查（全部基于设备上导出的真实文本）
 *
 * 1. provider API key 金丝雀：真机 [Settings] 里种一个 `sk-c1canary-...` 的 apiKey，
 *    断言导出全文不含它，也不含任何 `sk-<长串>`。
 * 2. 隐私记忆金丝雀：真机生产 `MemoryRepository` 里写一条 canary 记忆，断言不入导出。
 * 3. 工具授权 token 金丝雀：真机 Settings 的 MCP `Authorization` 头与 OAuth `accessToken`
 *    各种一份，断言不入导出；另有一条**消息内**的 Tool part，`input` 带 token /
 *    `output` 带记忆正文 / Reasoning part 带记忆正文——导出只取 `toText()`（只看 Text），
 *    这三者都必须整个消失。
 * 4. 生产黑名单扫描：逐行跑 [GroupChat.findForbiddenKeys]（含 `api_key` / `token` /
 *    `memory` / `authorization` 等 30 个键，[GroupChat.FORBIDDEN_EXPORT_KEYS]），必须零命中。
 *
 * 金丝雀「确实存在」在导出后立即从真机 store 读回断言（negation 不是空转）。
 *
 * ## 不证明什么
 *
 * - 没走 `ACTION_SEND` 系统分享面板（`shareFile` 要真实用户点选目标 app）；
 *   经过的是同一函数的 FileProvider URI 生成与真实写盘。
 * - 没证明酒馆/SillyTavern 本体打开该文件（C1-09 另一半，另册）。
 * - 没证明相机扫码（需要真实扫码 UI 交互）。
 */
@RunWith(AndroidJUnit4::class)
class C1GroupExportDeviceEvidenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appContext = context.applicationContext

    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository

    private val entry by lazy { appEntryPoint(appContext) }
    private val settingsStore by lazy { entry.settingsStore() }
    private val memoryStore by lazy { entry.memoryRepository() }

    private var originalSettings: Settings? = null
    private var memoryCanaryChunkId: Int? = null

    // ---------------- 固定 id：导出字节必须可复现 ----------------

    private val convId = Uuid.parse("0c1e0901-0000-0000-0000-00000000c109")
    private val assistantId = Uuid.parse("0c1e0901-0000-0000-0000-0000000000a1")
    private val user1Id = Uuid.parse("0c1e0901-0000-0000-0000-0000000000d1")
    private val user2Id = Uuid.parse("0c1e0901-0000-0000-0000-0000000000d2")

    private val providerCanaryId = Uuid.parse("0c1e0901-0000-0000-0000-0000000000f1")
    private val mcpCanaryId = Uuid.parse("0c1e0901-0000-0000-0000-0000000000f2")

    // ---------------- 金丝雀：都是明显假值，只为 grep 命中 ----------------

    private val canaryApiKey = "sk-c1canary-7f3d9a2b5c8e4f01feed"
    private val canaryMemory = "C1-MEMORY-CANARY 记忆金丝雀正文：用户住在火星基地 42 号舱，此句绝不允许离开记忆库。"
    private val canaryToolToken = "c1-canary-tool-authorization-token-9e7c4a2d"
    private val canaryMcpHeader = "Bearer c1-canary-mcp-header-5a2d8b3e"
    private val toolCanaryName = "c1_canary_tool_never_exported"

    private val userName = "阿达"
    private val groupName = "C1 导出金丝雀群 🎭"

    private val cards = listOf(
        RoleCardMeta(
            roleId = "a",
            name = "阿尔法",
            assistantId = "c1e0901-asst-a",
            cardId = "card-a",
            persona = "冷静的记录者。",
        ),
        RoleCardMeta(
            roleId = "b",
            name = "贝塔",
            assistantId = "c1e0901-asst-b",
            cardId = "card-b",
            persona = "挑剔的审稿人。",
            avatarRef = "file://avatar-b",
        ),
        RoleCardMeta(roleId = "c", name = "伽马", assistantId = "c1e0901-asst-c"),
    )

    private val groupConfig = GroupConfig(
        roles = listOf(
            GroupRole(id = "a", name = "阿尔法", assistantId = "c1e0901-asst-a", cardId = "card-a"),
            GroupRole(id = "b", name = "贝塔", assistantId = "c1e0901-asst-b", cardId = "card-b"),
            GroupRole(
                id = "c",
                name = "伽马",
                assistantId = "c1e0901-asst-c",
                chair = true,
                cardId = null,
            ),
        ),
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = "c",
        tokenBudgetPerRound = 4096,
        voteCandidates = emptyList(),
        tiePolicy = GroupChat.TIE_FAIL,
    )

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(EXPORT_DB)
        database = AppDatabaseFactory.create(context, EXPORT_DB)
        repository = ConversationRepository(
            conversationDAO = database.conversationDao(),
            messageNodeDAO = database.messageNodeDao(),
            database = database,
            filesManager = FilesManager(
                context,
                FilesRepository(database.managedFileDao()),
                AppScope(),
            ),
            messageFtsManager = MessageFtsManager(database),
        )

        // 真机生产 settings 里种金丝雀 provider 与 MCP 授权头 / OAuth token。
        originalSettings = settingsStore.settingsFlow.value
        val base = originalSettings ?: Settings(init = false)
        settingsStore.update(
            base.copy(
                providers = base.providers + canaryProvider(),
                mcpServers = base.mcpServers + canaryMcpServer(),
            ),
        )

        // 真机生产记忆库（独立 canary 空间）里种一条金丝雀记忆。
        memoryCanaryChunkId = memoryStore.addMemory(
            assistantId = CANARY_MEMORY_SPACE,
            content = canaryMemory,
        ).id
    }

    @After
    fun tearDown() {
        runBlocking {
            // 金丝雀记忆：硬删分块 + 删空间，不留痕。
            memoryCanaryChunkId?.let { runCatching { memoryStore.deleteMemory(it) } }
            runCatching {
                val production = AppDatabaseFactory.create(appContext)
                production.memoryChunkDao().hardDeleteSpace(CANARY_MEMORY_SPACE)
                production.memorySpaceDao().deleteSpace(CANARY_MEMORY_SPACE)
                production.close()
            }
            originalSettings?.let { runCatching { settingsStore.update(it) } }
            if (::database.isInitialized) database.close()
            context.deleteDatabase(EXPORT_DB)
        }
    }

    @Test
    fun deviceExportedGroupJsonlHashesMatchAndCarryNoSecrets() = runBlocking {
        // ---------- ① 真库落库 → 读回 ----------
        val fixture = buildFixtureMessages()
        repository.insertConversation(
            Conversation(
                id = convId,
                assistantId = assistantId,
                title = "C1 导出金丝雀会话",
                messageNodes = fixture,
                type = GroupChat.TYPE_GROUP,
                groupConfig = groupConfig,
                groupCards = cards,
            ),
        )
        val stored = requireNotNull(repository.getConversationById(convId)) {
            "群会话必须能从真 SQLite 读回"
        }
        assertEquals(GroupChat.TYPE_GROUP, stored.type)
        assertEquals(groupConfig, stored.groupConfig)
        assertEquals(cards, stored.groupCards)
        assertEquals("读回消息条数", fixture.size, stored.currentMessages.size)

        val config = requireNotNull(stored.groupConfig)
        val exported = TavernChatCodec.exportGroupJsonl(
            nodes = stored.messageNodes,
            config = config,
            cards = stored.groupCards.orEmpty(),
            userName = userName,
            groupName = groupName,
            createDate = null,
        )
        val bytes = exported.toByteArray(Charsets.UTF_8)
        assertTrue("导出不应为空", bytes.isNotEmpty())
        assertTrue(
            "首行必须是带 chat_metadata 的表头",
            exported.lineSequence().first().contains("chat_metadata"),
        )

        // ---------- ② 生产 IO 分发：writeExportTempFile 真写盘 + FileProvider URI ----------
        val fileName = "c1-device-export-pipeline.jsonl"
        val productionUri = writeExportTempFile(context, fileName) { it.write(bytes) }
        val productionFile = File(context.appTempFolder, fileName)
        assertEquals("生产临时文件长度必须等于被哈希字节数", bytes.size.toLong(), productionFile.length())
        assertTrue(
            "生产 IO 返回的必须是 content:// FileProvider URI，实际=$productionUri",
            productionUri.toString().startsWith("content://"),
        )
        assertEquals(
            "生产临时文件内容必须逐字节等于导出字节",
            true,
            productionFile.readBytes().contentEquals(bytes),
        )

        // ---------- ③ 复制到 external files dir，供 adb pull ----------
        val pullDir = File(
            requireNotNull(context.getExternalFilesDir(null)) { "external files dir 为 null" },
            "c1-device-export",
        )
        assertTrue("pull 目录建不出来：$pullDir", pullDir.mkdirs() || pullDir.isDirectory)
        val pullFile = File(pullDir, fileName)
        pullFile.writeBytes(bytes)

        val deviceShaOfProductionFile = sha256Hex(productionFile.readBytes())
        val deviceShaOfPullFile = sha256Hex(pullFile.readBytes())
        val deviceShaOfBytes = sha256Hex(bytes)

        // ---------- ④ 金丝雀确实在真机 store 里（让负断言不空转） ----------
        val liveSettings = requireNotNull(settingsStore.settingsFlow.value) { "settings 读不回来" }
        val liveApiKey = liveSettings.providers
            .filterIsInstance<heizige.kk.khatkit.ai.provider.ProviderSetting.OpenAI>()
            .firstOrNull { it.id == providerCanaryId }?.apiKey
        val liveMcpToken = liveSettings.mcpServers
            .firstOrNull { it.id == mcpCanaryId }
            ?.commonOptions?.oauth?.accessToken
        val liveHeader = liveSettings.mcpServers
            .firstOrNull { it.id == mcpCanaryId }
            ?.commonOptions?.headers?.firstOrNull { it.first == "Authorization" }?.second
        val liveMemoryContent = memoryStore.getMemoriesOfAssistant(CANARY_MEMORY_SPACE)
            .map { it.content }
        assertEquals("provider apiKey 金丝雀必须先落进真机 settings", canaryApiKey, liveApiKey)
        assertEquals("MCP OAuth accessToken 金丝雀必须先落进真机 settings", canaryToolToken, liveMcpToken)
        assertEquals("MCP Authorization 头金丝雀必须先落进真机 settings", canaryMcpHeader, liveHeader)
        assertTrue(
            "记忆金丝雀必须先落进真机记忆库",
            liveMemoryContent.any { it.contains(canaryMemory) },
        )

        // ---------- ⑤ 四项安全检查（全部对设备上导出的真实文本） ----------
        val skPattern = Regex("sk-[A-Za-z0-9_-]{6,}")
        val checkApiKey = !exported.contains(canaryApiKey) && !skPattern.containsMatchIn(exported)
        val checkMemory = !exported.contains(canaryMemory)
        val checkToolToken = !exported.contains(canaryToolToken) &&
            !exported.contains(canaryMcpHeader) &&
            !exported.contains(toolCanaryName)
        // 第 4 项：逐行跑生产黑名单扫描。
        val offendingKeys = exported.lines()
            .filter { it.isNotBlank() }
            .flatMap { line -> GroupChat.findForbiddenKeys(Json.parseToJsonElement(line)) }
            .distinct()
        val checkBlacklist = offendingKeys.isEmpty()

        assertTrue("导出不得含 provider API key 金丝雀", checkApiKey)
        assertTrue("导出不得含记忆金丝雀正文", checkMemory)
        assertTrue("导出不得含工具授权 token / 工具名", checkToolToken)
        assertEquals("生产密钥黑名单扫描必须零命中，实际=$offendingKeys", emptyList<String>(), offendingKeys)

        // 工具 part 的 input / output / Reasoning 正文一个都不许在导出文本里。
        assertFalse(
            "Tool part 的 input 里带 token 的 JSON 不得进导出",
            exported.contains("authorization") || exported.contains(canaryToolToken),
        )
        assertFalse(
            "Tool part 的 output 里带记忆正文不得进导出",
            exported.contains("记忆正文"),
        )

        // ---------- ⑥ 哈希三方一致 ----------
        assertEquals("同 JVM 重算必须一致", deviceShaOfBytes, sha256Hex(exported.toByteArray(Charsets.UTF_8)))
        assertEquals("生产 IO 落盘文件哈希必须一致", deviceShaOfBytes, deviceShaOfProductionFile)
        assertEquals("pull 副本哈希必须一致", deviceShaOfBytes, deviceShaOfPullFile)

        // ---------- ⑦ 证据 JSON ----------
        val report = buildJsonObject {
            put("case", "C1-09 device export")
            putJsonObject("device") {
                put("model", Build.MODEL)
                put("sdk", Build.VERSION.SDK_INT)
                put("release", Build.VERSION.RELEASE)
                put("abi", Build.SUPPORTED_ABIS.joinToString(","))
                put("fingerprint", Build.FINGERPRINT)
            }
            put("export_function", "TavernChatCodec.exportGroupJsonl")
            put("io_helper", "writeExportTempFile (ConversationExport.kt:836)")
            put("config_mode", config.mode)
            putJsonObject("conversation") {
                put("id", convId.toString())
                put("message_count", stored.currentMessages.size)
            }
            putJsonObject("production_io_file") {
                put("uri", productionUri.toString())
                put("path", productionFile.absolutePath)
                put("bytes", bytes.size)
                put("sha256_device", deviceShaOfProductionFile)
            }
            putJsonObject("pull_file") {
                put("path", pullFile.absolutePath)
                put("bytes", bytes.size)
                put("sha256_device", deviceShaOfPullFile)
            }
            put("sha256_of_exported_bytes_device", deviceShaOfBytes)
            putJsonObject("canaries_live_in_stores") {
                put("api_key", liveApiKey != null)
                put("mcp_oauth_access_token", liveMcpToken != null)
                put("mcp_authorization_header", liveHeader != null)
                put("memory", liveMemoryContent.any { it.contains(canaryMemory) })
            }
            putJsonObject("security_checks") {
                put("no_provider_api_key_sk", checkApiKey)
                put("no_memory_canary", checkMemory)
                put("no_tool_authorization_token", checkToolToken)
                put("blacklist_scan_hit_count", offendingKeys.size)
                put("blacklist_keys_scanned", GroupChat.FORBIDDEN_EXPORT_KEYS.size)
            }
            putJsonObject("tool_part_not_exported") {
                put("tool_name", toolCanaryName)
                put("input_token", canaryToolToken)
                put("absent", !exported.contains(toolCanaryName) && !exported.contains(canaryToolToken))
            }
            put("export_first_line_has_chat_metadata", exported.lineSequence().first().contains("chat_metadata"))
            put("export_line_count", exported.lines().count { it.isNotBlank() })
            put("export_text_full", exported)
        }
        val reportFile = writeEvidenceFile("c1-device-export-report.json", report)
        assertTrue("证据报告不应为空", reportFile.length() > 0)

        println("C1-EXPORT-DEVICE-BEGIN")
        println("sha256=${deviceShaOfBytes}")
        println("bytes=${bytes.size}")
        println("pull_path=${pullFile.absolutePath}")
        println("checks=api:$checkApiKey memory:$checkMemory token:$checkToolToken blacklist:${offendingKeys.isEmpty()}")
        println("C1-EXPORT-DEVICE-END")
    }

    // ==================================================================
    // fixtures
    // ==================================================================

    private fun canaryProvider() = heizige.kk.khatkit.ai.provider.ProviderSetting.OpenAI(
        id = providerCanaryId,
        enabled = false,
        name = "c1-canary-provider",
        apiKey = canaryApiKey,
        models = emptyList(),
    )

    private fun canaryMcpServer() = McpServerConfig.StreamableHTTPServer(
        id = mcpCanaryId,
        commonOptions = McpCommonOptions(
            name = "c1-canary-mcp",
            headers = listOf("Authorization" to canaryMcpHeader),
            oauth = McpOAuthState(
                enabled = true,
                accessToken = canaryToolToken,
            ),
        ),
        url = "https://example.invalid/mcp",
    )

    /**
     * 夹具：3 角色 / 2 轮 / 五种 turn_kind 全覆盖；其中一条助手消息里塞进带授权的
     * Tool part 与含记忆正文的 Reasoning part（导出只允许出现 Text）。
     */
    private fun buildFixtureMessages(): List<MessageNode> {
        val toolInput = """{"authorization":"Bearer $canaryToolToken","query":"$canaryMemory"}"""
        return listOf(
            MessageNode.of(
                UIMessage.user("@阿尔法 先给结论")
                    .copy(
                        id = user1Id,
                        roundId = "c1e0901-r1",
                        turnKind = GroupChat.TURN_USER,
                        mentionRoleIds = listOf("a"),
                    ),
            ),
            MessageNode(
                messages = listOf(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.Text("阿尔法：结论在附注 ✅"),
                            UIMessagePart.Tool(
                                toolCallId = "call-canary-1",
                                toolName = toolCanaryName,
                                input = toolInput,
                                output = listOf(UIMessagePart.Text(canaryMemory)),
                            ),
                            UIMessagePart.Reasoning(canaryMemory),
                        ),
                    ).copy(
                        id = Uuid.parse("0c1e0901-0000-0000-0000-0000000000a1"),
                        roleId = "a",
                        roundId = "c1e0901-r1",
                        turnKind = GroupChat.TURN_SPEAKER,
                    ),
                ),
            ),
            MessageNode.of(
                UIMessage.assistant("贝塔：逐条挑错")
                    .copy(
                        id = Uuid.parse("0c1e0901-0000-0000-0000-0000000000b1"),
                        roleId = "b",
                        roundId = "c1e0901-r1",
                        turnKind = GroupChat.TURN_SPEAKER,
                    ),
            ),
            MessageNode.of(
                UIMessage.assistant("伽马（议长）汇总第一轮")
                    .copy(
                        id = Uuid.parse("0c1e0901-0000-0000-0000-0000000000c1"),
                        roleId = "c",
                        roundId = "c1e0901-r1",
                        turnKind = GroupChat.TURN_CHAIR,
                    ),
            ),
            MessageNode.of(
                UIMessage.user("继续，展开细节")
                    .copy(
                        id = user2Id,
                        roundId = "c1e0901-r2",
                        turnKind = GroupChat.TURN_USER,
                    ),
            ),
            MessageNode.of(
                UIMessage.assistant("本轮投票结果：a")
                    .copy(
                        id = Uuid.parse("0c1e0901-0000-0000-0000-000000000051"),
                        roleId = GroupChat.SUMMARY_ID,
                        roundId = "c1e0901-r2",
                        turnKind = GroupChat.TURN_VOTE_SUMMARY,
                    ),
            ),
            MessageNode.of(
                UIMessage.assistant("[贝塔] 本轮生成失败：注入夹具")
                    .copy(
                        id = Uuid.parse("0c1e0901-0000-0000-0000-0000000000e1"),
                        roleId = "b",
                        roundId = "c1e0901-r2",
                        turnKind = GroupChat.TURN_ERROR,
                    ),
            ),
        )
    }

    // ==================================================================
    // 落盘与哈希
    // ==================================================================

    private fun writeEvidenceFile(name: String, payload: JsonObject): File {
        val dir = File(
            requireNotNull(context.getExternalFilesDir(null)) { "external files dir 为 null" },
            "c1-device-export",
        )
        assertTrue("证据目录建不出来：$dir", dir.mkdirs() || dir.isDirectory)
        val file = File(dir, name)
        file.writeText(PRETTY.encodeToString(JsonElement.serializer(), payload))
        return file
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val EXPORT_DB = "c1-device-export.db"
        const val CANARY_MEMORY_SPACE = "c1-device-export-canary-space"
        val PRETTY = Json { prettyPrint = true; encodeDefaults = true }
    }
}
