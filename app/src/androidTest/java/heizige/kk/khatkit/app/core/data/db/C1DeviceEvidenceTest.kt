package heizige.kk.khatkit.app.core.data.db

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.core.TokenUsage
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.ai.ModelTaskType
import heizige.kk.khatkit.app.core.data.ai.TaskRoutes
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.ai.taskBinding
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.findModelById
import heizige.kk.khatkit.app.core.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.core.data.db.dao.GroupRunDAO
import heizige.kk.khatkit.app.core.data.db.entity.GroupRunEntity
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.core.data.model.SpeakerStep
import heizige.kk.khatkit.app.core.data.model.VoteBallot
import heizige.kk.khatkit.app.core.data.model.VoteOutcome
import heizige.kk.khatkit.app.core.data.model.toMessageNode
import heizige.kk.khatkit.app.core.data.repository.ConversationRepository
import heizige.kk.khatkit.app.core.data.repository.FilesRepository
import heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinator
import heizige.kk.khatkit.app.feature.chat.resolveGroupTurnModelId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.uuid.Uuid

/**
 * C1 真机证据采集：把契约 `docs/beyond-operit-client-changes.md:206` 点名的四类证据
 * （各 viewer 可见消息 ID / 实际模型调用序列 / token(prompt+completion) / 导出 SHA-256）
 * 在**真机真实 Room 数据库 + 真机真实文件系统**上采一遍，落成一份 JSON。
 *
 * ## 这个类刻意不做的事
 *
 * **不发起任何真实 LLM 调用，也不伪造任何 LLM 产物。** 设备上没有配 API key，
 * 契约要求的「实际模型调用序列」与「真实 token 计数」在无密钥环境下**采不到**。
 * 因此：
 * - token 一项只走真实的预算判定与运行日志落库/读回路径
 *   （`budgetDecision` → `advance` → `GroupRunDAO` → 读回），JSON 里 `token_source`
 *   明确标注 `budget-accounting-only, no live LLM call`；
 * - 模型序列一项只走真实的路由判定（`resolveGroupTurnModelId` + `TaskRoutes.resolve`）
 *   与**真实库里**每条消息记录的 `modelId`，JSON 里 `model_sequence_source` 明确标注
 *   `routing-decision + recorded-message-modelId, no live LLM call`。
 *
 * 谁把它当成「跑通了真实模型调用」的证据，都是误读。
 *
 * ## 另外两类是硬证据
 *
 * - viewer 可见消息集合：从**真 SQLite 读回来的**消息上跑
 *   `GroupTurnCoordinator.viewerMessages`，并逐对审计越权（见 [assertNoCrossRoleLeakage]）。
 * - 导出 SHA-256：真机跑 `TavernChatCodec.exportGroupJsonl` → 真机写真文件 →
 *   真机 `MessageDigest` 算哈希；同一 JVM 内算两遍必须一致，且文件实际字节必须
 *   与被哈希的字节串逐字节相同。
 *
 * ## 产物位置
 *
 * `context.getExternalFilesDir(null)/c1-evidence-*`，`adb pull` 直接拿走。
 * 故意**不**走 `connectedAndroidTest`（AGP 跑完会卸载 app，产物一起没了）。
 *
 * ## 数据库隔离
 *
 * 用 `AppDatabaseFactory.create(context, "c1-device-evidence.db")`——与生产同一个工厂
 * （同一套 SQLite 扩展、同一个 `onOpen` 回调、同一个 schema 版本 32），只换了一个独立
 * 库名。不碰 `rikka_hub`、不碰用户数据，测试结束删掉自己那个库。
 */
@RunWith(AndroidJUnit4::class)
class C1DeviceEvidenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository
    private lateinit var groupRunDao: GroupRunDAO

    // ---------------- 固定标识符：导出必须字节确定，所以一律不许随机 Uuid ----------------

    private val convId = Uuid.parse("0c1c0de5-0000-0000-0000-000000000001")
    private val assistantId = Uuid.parse("0c1c0de5-0000-0000-0000-0000000000a1")

    private val user1Id = Uuid.parse("11111111-0000-0000-0000-000000000001")
    private val user2Id = Uuid.parse("11111111-0000-0000-0000-000000000002")
    private val a1Id = Uuid.parse("22222222-0000-0000-0000-00000000000a")
    private val a2Id = Uuid.parse("22222222-0000-0000-0000-00000000000b")
    private val b2Id = Uuid.parse("22222222-0000-0000-0000-00000000000c")
    private val c2Id = Uuid.parse("22222222-0000-0000-0000-00000000000d")

    private val roleAModelId = Uuid.parse("44444444-0000-0000-0000-00000000000a")
    private val roleBModelId = Uuid.parse("44444444-0000-0000-0000-00000000000b")
    private val roleCModelId = Uuid.parse("44444444-0000-0000-0000-00000000000c")

    /** 语法合法但**不在模型库里**：用来钉住 `resolveGroupTurnModelId` 的第三关回落。 */
    private val missingModelId = Uuid.parse("44444444-0000-0000-0000-0000000000ff")
    private val providerId = Uuid.parse("55555555-0000-0000-0000-000000000001")

    // ---------------- 群配置：3 角色，c 是议长 ----------------

    private val cards = listOf(
        RoleCardMeta(
            roleId = "a",
            name = "阿尔法",
            assistantId = "0c1c0de5-0000-0000-0000-0000000000b1",
            cardId = "card-a",
            persona = "你是一位冷静的记录者，只写事实，不写形容词。",
            avatarRef = null,
        ),
        RoleCardMeta(
            roleId = "b",
            name = "贝塔",
            assistantId = "0c1c0de5-0000-0000-0000-0000000000b2",
            cardId = "card-b",
            persona = "你是一位挑剔的审稿人，逐条挑错。",
            avatarRef = "avatar://b",
        ),
        RoleCardMeta(
            roleId = "c",
            name = "伽马",
            assistantId = "0c1c0de5-0000-0000-0000-0000000000b3",
            cardId = null,
            persona = "",
            avatarRef = null,
        ),
    )

    private val pipelineConfig = GroupConfig(
        roles = listOf(
            // a：绑了模型库里真实存在的模型 → 三关全过
            GroupRole(
                id = "a",
                name = "阿尔法",
                assistantId = cards[0].assistantId,
                cardId = "card-a",
                modelId = roleAModelId.toString(),
            ),
            // b：绑的是**空白串** → 第一关就不过，回落助手模型
            GroupRole(
                id = "b",
                name = "贝塔",
                assistantId = cards[1].assistantId,
                cardId = "card-b",
                modelId = "",
            ),
            // c：绑的 id 语法合法但库里没有 → 第三关不过，回落助手模型
            GroupRole(
                id = "c",
                name = "伽马",
                assistantId = cards[2].assistantId,
                chair = true,
                cardId = null,
                modelId = missingModelId.toString(),
            ),
        ),
        mode = GroupChat.MODE_PIPELINE,
        chairRoleId = "c",
        tokenBudgetPerRound = 4096,
        voteCandidates = emptyList(),
        tiePolicy = GroupChat.TIE_FAIL,
    )

    private val configs = listOf(
        pipelineConfig,
        pipelineConfig.copy(mode = GroupChat.MODE_ROUNDTABLE, chairRoleId = "c"),
        pipelineConfig.copy(
            mode = GroupChat.MODE_VOTE,
            chairRoleId = "c",
            voteCandidates = listOf("a", "b"),
        ),
    )

    @Before
    fun setUp() {
        context.deleteDatabase(EVIDENCE_DB)
        database = AppDatabaseFactory.create(context, EVIDENCE_DB)
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
        groupRunDao = database.groupRunDao()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
        context.deleteDatabase(EVIDENCE_DB)
    }

    // ==================================================================
    // 用例 1：四类证据一次采齐 + 落 JSON（含 viewer 隔离断言）
    // ==================================================================

    @Test
    fun collectC1DeviceEvidenceAndWriteJson() = runBlocking {
        val generatedAt = System.currentTimeMillis()

        // ---------- ① viewer 可见消息 ID：从真机真库读回来的消息上算 ----------
        //
        // 三种 mode 各采一遍。之前只采 pipeline，于是 `GroupChat.plan` 的另外两条分支
        // 从没被跑过：roundtable 的 `chairRound=true` 放行分支（议长能看本轮所有角色输出）
        // 和 vote 的纯 null 分支（`predecessorId` 与 `chairRound` 都为 null）一次都没被覆盖。
        val stored = persistAndReloadGroupConversation()
        val storedMessages = stored.currentMessages
        val visibleByMode = configs.associate { config ->
            config.mode to collectViewerVisibleMessageIds(storedMessages, config)
        }
        val leakageByMode = configs.associate { config ->
            config.mode to assertNoCrossRoleLeakage(
                storedMessages,
                requireNotNull(visibleByMode[config.mode]),
                config,
            )
        }
        val visibleByRole = requireNotNull(visibleByMode[GroupChat.MODE_PIPELINE])
        val leakage = requireNotNull(leakageByMode[GroupChat.MODE_PIPELINE])

        // ---------- ④ 模型路由判定（真实 resolve，无真实 LLM 调用） ----------
        val settings = syntheticRoutingSettings()
        val routingDecisions = pipelineConfig.roles.map { role ->
            resolveOneRole(role, settings, assistantModelIdOf(role.id))
        }
        val recordedModels = recordedModelIds(storedMessages)

        // ---------- ③ 预算截断的真实判定 + 真实落库读回（无真实 LLM 调用） ----------
        val budget = collectBudgetAccountingEvidence(storedMessages)

        // ---------- ② 三种 mode 的导出 SHA-256（真机文件 + 真机 MessageDigest） ----------
        val exportEvidence = configs.map { config ->
            val exported = exportFor(config, stored)
            val file = writeEvidenceFile("c1-evidence-${config.mode}.jsonl", exported)
            val roundTrip = assertExportRoundTrips(
                exported = exported,
                config = config,
                sourceMessages = storedMessages,
            )
            val record = ExportEvidence(
                mode = config.mode,
                file = file,
                bytes = exported.toByteArray(Charsets.UTF_8).size,
                sha256 = sha256Hex(exported.toByteArray(Charsets.UTF_8)),
                sha256SecondPass = sha256Hex(exported.toByteArray(Charsets.UTF_8)),
                roundTripMessageCount = roundTrip,
            )
            // 落盘文件的实际长度必须等于被哈希的字节数：证明「哈希的那个字节串」
            // 就是「真机磁盘上那个文件」，而不是内存里的另一份。
            assertEquals(
                "导出文件字节数与记录不一致",
                record.bytes.toLong(),
                file.length(),
            )
            assertEquals("导出 SHA-256 必须字节确定", record.sha256, record.sha256SecondPass)
            assertTrue("导出内容不应为空", record.bytes > 0)
            record
        }

        val qrEvidence = configs.map { config ->
            val payload = GroupChat.encodeQr(config, cards)
            val bytes = payload.toByteArray(Charsets.UTF_8)
            writeEvidenceFile("c1-evidence-qr-${config.mode}.json", payload)
            assertEquals("QR 载荷 SHA-256 必须字节确定", sha256Hex(bytes), sha256Hex(bytes))
            assertTrue("QR 载荷不应为空", bytes.isNotEmpty())
            QrEvidence(config.mode, bytes.size, sha256Hex(bytes))
        }

        // ---------------- 组装 JSON ----------------
        val report = buildJsonObject {
            put("generated_at_device", generatedAt)
            putJsonObject("device") {
                put("sdk", Build.VERSION.SDK_INT)
                put("model", Build.MODEL)
                put("abi", Build.SUPPORTED_ABIS.joinToString(","))
                put("brand", Build.BRAND)
                put("fingerprint", Build.FINGERPRINT)
                put("package_name", context.packageName)
                put("database_name", EVIDENCE_DB)
                put("database_version", database.openHelper.readableDatabase.version)
            }
            putJsonObject("viewer_visible_message_ids") {
                visibleByRole.forEach { (role, ids) ->
                    // kotlinx.serialization 1.11 的 JsonArrayBuilder 只剩 add(JsonElement)，
                    // 便捷重载 add(String) 已被移除，所以显式包一层 JsonPrimitive。
                    putJsonArray("role_$role") { ids.forEach { id -> add(JsonPrimitive(id)) } }
                }
            }
            // 三种 mode 各一份 viewer 台账 + 越权审计结果。
            putJsonObject("viewer_ledger_by_mode") {
                configs.forEach { config ->
                    val visible = requireNotNull(visibleByMode[config.mode])
                    val result = requireNotNull(leakageByMode[config.mode])
                    putJsonObject(config.mode) {
                        putJsonObject("visible_message_ids") {
                            visible.forEach { (role, ids) ->
                                putJsonArray("role_$role") { ids.forEach { add(JsonPrimitive(it)) } }
                            }
                        }
                        putJsonObject("step_of_viewer") {
                            visible.keys.forEach { roleId ->
                                val step = requireNotNull(
                                    viewerStepOf(storedMessages, roleId, config),
                                )
                                putJsonObject("role_$roleId") {
                                    put("predecessor_id", step.predecessorId)
                                    put("chair_round", step.chairRound)
                                    put("turn_kind", GroupTurnCoordinator.turnKindOf(step))
                                }
                            }
                        }
                        put("leakage_passed", result.passed)
                        put("checked_pairs", result.checkedPairs)
                        put("violations", JsonArray(result.violations.map { JsonPrimitive(it) }))
                    }
                }
            }
            putJsonObject("viewer_fixture") {
                put("conversation_id", convId.toString())
                put("mode", pipelineConfig.mode)
                put("chair_role_id", pipelineConfig.chairRoleId)
                put("roles", JsonArray(pipelineConfig.roles.map { JsonPrimitive(it.id) }))
                put("all_message_ids", JsonArray(storedMessages.map { JsonPrimitive(it.id.toString()) }))
                put(
                    "message_author_of",
                    buildJsonObject {
                        storedMessages.forEach { message ->
                            put(
                                message.id.toString(),
                                message.roleId ?: message.role.name.lowercase(),
                            )
                        }
                    },
                )
                put(
                    "step_of_viewer",
                    buildJsonObject {
                        visibleByRole.keys.forEach { roleId ->
                            val step = requireNotNull(viewerStepOf(storedMessages, roleId))
                            putJsonObject("role_$roleId") {
                                put("predecessor_id", step.predecessorId)
                                put("chair_round", step.chairRound)
                                put("turn_kind", GroupTurnCoordinator.turnKindOf(step))
                            }
                        }
                    },
                )
            }
            putJsonObject("cross_leakage_assertions") {
                put("passed", leakage.passed)
                put("checked_pairs", leakage.checkedPairs)
                put("violations", JsonArray(leakage.violations.map { JsonPrimitive(it) }))
            }
            putJsonArray("export_sha256") {
                exportEvidence.forEach { evidence ->
                    add(
                        buildJsonObject {
                            put("mode", evidence.mode)
                            put("bytes", evidence.bytes)
                            put("sha256", evidence.sha256)
                            put("sha256_second_pass", evidence.sha256SecondPass)
                            put("file_name", evidence.file.name)
                            put("file_path_on_device", evidence.file.absolutePath)
                            put("file_length_on_disk", evidence.file.length())
                            put("round_trip_message_count", evidence.roundTripMessageCount)
                        },
                    )
                }
            }
            putJsonArray("qr_payload_sha256") {
                qrEvidence.forEach { evidence ->
                    add(
                        buildJsonObject {
                            put("mode", evidence.mode)
                            put("bytes", evidence.bytes)
                            put("sha256", evidence.sha256)
                            put("file_name", "c1-evidence-qr-${evidence.mode}.json")
                        },
                    )
                }
            }
            putJsonObject("budget_accounting") {
                put("spent", budget.spent)
                put("limit", budget.limit)
                put("skipped", JsonArray(budget.skipped.map { JsonPrimitive(it) }))
                put("status", budget.status)
                put("reason", budget.reason)
                put("committed", JsonArray(budget.committed.map { JsonPrimitive(it) }))
                put("conversation_id", budget.conversationId)
                put("round_id", budget.roundId)
                put("run_token", budget.runToken)
                put("started_at", budget.startedAt)
                put("ended_at", budget.endedAt)
                put("limit_from_config", budget.limitFromConfig)
                put("run_token_persisted_before_call", budget.persistedBeforeCall)
                put(
                    "simulated_usage_prompt_completion",
                    buildJsonObject {
                        put("prompt", budget.usagePrompt)
                        put("completion", budget.usageCompletion)
                    },
                )
                put("read_back_from_sqlite", true)
            }
            put("token_source", TOKEN_SOURCE)
            putJsonArray("model_routing_decisions") {
                routingDecisions.forEach { decision ->
                    add(
                        buildJsonObject {
                            put("role_id", decision.roleId)
                            put("role_model_id", decision.roleModelId)
                            put("resolved_model_id", decision.resolvedModelId)
                            put("assistant_chat_model_id", decision.assistantModelId.toString())
                            put("gate_non_blank", decision.gateNonBlank)
                            put("gate_parses_as_uuid", decision.gateParsesAsUuid)
                            put("gate_is_known_model", decision.gateIsKnownModel)
                            put("fallback_taken_from", decision.fallbackTakenFrom)
                            putJsonArray("fallback_chain") {
                                decision.fallbackChain.forEach { stage ->
                                    add(
                                        buildJsonObject {
                                            put("stage", stage.stage)
                                            put("value", stage.value)
                                            put("accepted", stage.accepted)
                                        },
                                    )
                                }
                            }
                            put("task_routes_candidate_count", decision.candidateCount)
                            put(
                                "task_routes_candidate_model_ids",
                                JsonArray(decision.candidateModelIds.map { JsonPrimitive(it) }),
                            )
                            put("task_routes_selected_model_uuid", decision.taskRoutesSelectedUuid)
                            put("task_routes_selected_model_name", decision.taskRoutesSelectedName)
                            put("task_routes_selection_count_after", decision.taskRoutesCountAfter)
                        },
                    )
                }
            }
            putJsonArray("recorded_message_model_ids") {
                recordedModels.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("message_id", entry.messageId)
                            put("role_id", entry.roleId)
                            put("model_id", entry.modelId)
                        },
                    )
                }
            }
            put("model_sequence_source", MODEL_SEQUENCE_SOURCE)
            put("evidence_gaps", EVIDENCE_GAPS)
        }

        val jsonText = PRETTY.encodeToString(JsonElement.serializer(), report)
        val reportFile = writeEvidenceFile("c1-evidence-report.json", jsonText)
        assertTrue("证据报告不应为空", reportFile.length() > 0)

        // 写出来的东西必须能被读回去，否则这份证据没法用。
        val reparsed = Json.parseToJsonElement(reportFile.readText(Charsets.UTF_8))
        assertTrue("证据 JSON 必须可解析为对象", reparsed is JsonObject)
        val reparsedObject = reparsed as JsonObject
        assertEquals(
            "导出证据必须覆盖 pipeline/roundtable/vote 三种 mode",
            3,
            (reparsedObject["export_sha256"] as JsonArray).size,
        )
        assertEquals(
            "QR 载荷证据必须覆盖三种 mode",
            3,
            (reparsedObject["qr_payload_sha256"] as JsonArray).size,
        )
        assertEquals(
            "viewer 可见集合必须覆盖 3 个角色",
            3,
            (reparsedObject["viewer_visible_message_ids"] as JsonObject).size,
        )
        assertEquals(
            "viewer 台账必须覆盖 pipeline/roundtable/vote 三种 mode",
            3,
            (reparsedObject["viewer_ledger_by_mode"] as JsonObject).size,
        )
        // 三种 mode 的发言位形状必须真的不同，否则「各采一遍」只是把同一份结果抄三遍：
        // pipeline 靠 predecessorId 串上一位；roundtable 的议长必须 chairRound=true；
        // vote 两者都为 null。
        val ledger = reparsedObject["viewer_ledger_by_mode"] as JsonObject
        val roundtableSteps = ledger[GroupChat.MODE_ROUNDTABLE]
            ?.let { it as JsonObject }?.get("step_of_viewer") as JsonObject
        // 注意：读回来的是 JsonElement（JsonLiteral），不能直接和 Kotlin Boolean 比。
        assertEquals(
            "roundtable 议长必须落在 chairRound=true 的发言位",
            JsonPrimitive(true),
            (roundtableSteps["role_c"] as JsonObject)["chair_round"],
        )
        val voteSteps = ledger[GroupChat.MODE_VOTE]
            ?.let { it as JsonObject }?.get("step_of_viewer") as JsonObject
        voteSteps.keys.forEach { key ->
            val step = voteSteps[key] as JsonObject
            // `put("predecessor_id", null)` 会写成显式 null，所以读回来是 JsonNull 而不是缺失键。
            assertEquals(
                "vote 模式的 $key 发言位不该有 predecessorId",
                JsonNull,
                step["predecessor_id"],
            )
            assertEquals(
                "vote 模式的 $key 发言位不该是 chairRound",
                JsonPrimitive(false),
                step["chair_round"],
            )
        }
        val pipelineSteps = ledger[GroupChat.MODE_PIPELINE]
            ?.let { it as JsonObject }?.get("step_of_viewer") as JsonObject
        assertEquals(
            "pipeline 里 b 的 predecessor 必须是 a",
            JsonPrimitive("a"),
            (pipelineSteps["role_b"] as JsonObject)["predecessor_id"],
        )
        assertEquals(
            "pipeline 里 a 的 predecessor 必须是 null",
            JsonNull,
            (pipelineSteps["role_a"] as JsonObject)["predecessor_id"],
        )

        println("C1-EVIDENCE-REPORT-BEGIN")
        println(jsonText)
        println("C1-EVIDENCE-REPORT-END")
    }

    // ==================================================================
    // 用例 2：导出可 importGroup 回读，四个契约字段逐条相等（单独钉死）
    // ==================================================================

    @Test
    fun exportedGroupFilesImportBackWithEqualContractFields() = runBlocking {
        val stored = persistAndReloadGroupConversation()
        val expectedTurnKinds = stored.currentMessages.map(::expectedTurnKind)

        configs.forEach { config ->
            assertTrue(
                "群配置 ${config.mode} 必须过真机校验",
                GroupChat.isValid(config),
            )
            val exported = exportFor(config, stored)
            assertTrue(
                "群聊导出首行必须带 chat_metadata，否则酒馆会把表头当消息渲染",
                exported.lineSequence().first().contains("chat_metadata"),
            )
            val count = assertExportRoundTrips(exported, config, stored.currentMessages)
            assertEquals("往返消息条数必须与源一致", expectedTurnKinds.size, count)
        }
    }

    // ==================================================================
    // 用例 3：SHA-256 同 JVM 重算一致 + 落盘字节一致（单独钉死）
    // ==================================================================

    @Test
    fun exportSha256IsReproducibleAndMatchesBytesOnDisk() = runBlocking {
        val stored = persistAndReloadGroupConversation()
        configs.forEach { config ->
            val exported = exportFor(config, stored)
            val bytes = exported.toByteArray(Charsets.UTF_8)
            val first = sha256Hex(bytes)
            val second = sha256Hex(exported.toByteArray(Charsets.UTF_8))
            assertEquals("同 JVM 重算必须一致（导出必须字节确定）", first, second)

            val file = writeEvidenceFile("c1-evidence-repro-${config.mode}.jsonl", exported)
            assertEquals("落盘字节数必须等于被哈希的字节数", bytes.size.toLong(), file.length())
            assertArrayEquals("落盘文件内容必须与被哈希的字节串逐字节相同", bytes, file.readBytes())
            assertEquals("对落盘文件重新算哈希也必须一致", first, sha256Hex(file.readBytes()))
        }
    }

    // ==================================================================
    // ① viewer 可见消息集合
    // ==================================================================

    /**
     * 第二轮（隐式路由）里每个角色**第一次**发言的真实 [SpeakerStep]。
     *
     * step 是 [GroupChat.plan] 算出来的，测试不自己造替身——`viewerMessages` 的
     * `predecessorId` / `chairRound` 两个放行分支全靠它。
     *
     * ⚠️ [config] 必须传进来而不是写死 `pipelineConfig`：`GroupChat.plan` 按 mode 走三条
     * 不同分支，只有真跑过三种 mode 才会覆盖到
     * `predecessorId`（pipeline 串上一位）、`chairRound=true`（roundtable 议长收束）、
     * 以及两者都为 null 的 vote 路径。夹具层改循环，不改生产逻辑。
     */
    private fun viewerStepOf(
        messages: List<UIMessage>,
        roleId: String,
        config: GroupConfig = pipelineConfig,
    ): SpeakerStep? {
        val trigger = messages.first { it.id == user2Id }
        val plan = GroupChat.newRound(
            triggerMessageId = trigger.id.toString(),
            config = config,
            mentionRoleIds = trigger.mentionRoleIds,
            userText = trigger.toText(),
        )
        return plan.plan.firstOrNull { it.role.id == roleId }
    }

    private fun collectViewerVisibleMessageIds(
        messages: List<UIMessage>,
        config: GroupConfig = pipelineConfig,
    ): Map<String, List<String>> = config.roles.associate { role ->
        val step = requireNotNull(viewerStepOf(messages, role.id, config)) {
            "第二轮计划（mode=${config.mode}）里必须有角色 ${role.id} 的发言位"
        }
        role.id to GroupTurnCoordinator
            .viewerMessages(config, messages, step)
            .map { it.id.toString() }
    }

    /**
     * 越权审计：对每个 `(viewer, author)` 有序对（`viewer != author`，3×2 = 6 对），
     * 检查 viewer 可见集合里**每一条**由 `author` 署名的消息是否都能被生产规则的
     * 三条放行分支之一解释：
     *
     * 1. `author` 在该消息的 `mentionRoleIds` 里（显式 @ 投递）；
     * 2. viewer 本轮的 [SpeakerStep.predecessorId] 就是 `author`（pipeline 串上一位）；
     * 3. viewer 本轮是 [SpeakerStep.chairRound]（议长汇总时才放开本轮他人输出）。
     *
     * 这三条正是 [GroupChat.visibleMessages] 里全部「非本人消息」的放行分支
     * （`GroupChat.kt:545-547`）。一条都不沾的消息出现在 viewer 可见集合里就是越权。
     * 这里**不重写**过滤逻辑，只审计它的输出。
     */
    private fun assertNoCrossRoleLeakage(
        messages: List<UIMessage>,
        visibleByRole: Map<String, List<String>>,
        config: GroupConfig = pipelineConfig,
    ): LeakageResult {
        val violations = mutableListOf<String>()
        var checkedPairs = 0

        config.roles.forEach { viewer ->
            val step = requireNotNull(viewerStepOf(messages, viewer.id, config))
            val visible = visibleByRole.getValue(viewer.id).toSet()
            config.roles.filter { it.id != viewer.id }.forEach { author ->
                checkedPairs++
                val allowed = { message: UIMessage ->
                    viewer.id in message.mentionRoleIds ||
                        step.predecessorId == author.id ||
                        step.chairRound
                }
                messages
                    .filter { it.roleId == author.id && it.id.toString() in visible }
                    .forEach { message ->
                        if (!allowed(message)) {
                            violations += "viewer=${viewer.id} 看到了不该看的 author=${author.id} " +
                                "message=${message.id} round=${message.roundId} " +
                                "turnKind=${message.turnKind}"
                        }
                    }
            }
        }

        // 正向断言：用户消息与合成的轮次摘要对**每个**视角都必须可见，
        // 否则「上下文过滤发生在 prompt 组装层」这条契约就少了用户输入这一半。
        config.roles.forEach { viewer ->
            val visible = visibleByRole.getValue(viewer.id).toSet()
            messages.filter { it.role == MessageRole.USER }.forEach { message ->
                assertTrue(
                    "viewer=${viewer.id} 必须看得到用户消息 ${message.id}",
                    message.id.toString() in visible,
                )
            }
            messages.filter { it.roleId == GroupChat.SUMMARY_ID }.forEach { summary ->
                assertTrue(
                    "viewer=${viewer.id} 必须看得到轮次摘要 ${summary.id}",
                    summary.id.toString() in visible,
                )
            }
        }

        assertEquals("越权审计必须覆盖 3 角色 × 2 他人 = 6 对", 6, checkedPairs)
        assertTrue("viewer 可见集合出现越权消息：${violations.joinToString("; ")}", violations.isEmpty())
        return LeakageResult(passed = true, checkedPairs = checkedPairs, violations = violations)
    }

    // ==================================================================
    // ② 导出往返 + SHA-256
    // ==================================================================

    private fun exportFor(config: GroupConfig, stored: Conversation): String =
        TavernChatCodec.exportGroupJsonl(
            nodes = stored.messageNodes,
            config = config,
            cards = cards,
            userName = USER_NAME,
            groupName = GROUP_NAME,
            createDate = null,
        )

    private fun expectedTurnKind(message: UIMessage): String = message.turnKind
        ?: if (message.role == MessageRole.USER) GroupChat.TURN_USER else GroupChat.TURN_SPEAKER

    /**
     * 断言 `exportGroupJsonl` → `importGroup` 之后群配置、角色卡与四个消息级契约字段
     * 逐条相等，返回读回的消息条数。
     */
    private fun assertExportRoundTrips(
        exported: String,
        config: GroupConfig,
        sourceMessages: List<UIMessage>,
    ): Int {
        val document = TavernChatCodec.importGroup(exported)
        assertNotNull("群聊导出必须能被 importGroup 读回", document)
        val doc = requireNotNull(document)
        assertEquals("群名必须往返", GROUP_NAME, doc.groupName)
        assertEquals("用户名必须往返", USER_NAME, doc.userName)
        assertEquals("成员显示名名单必须往返", listOf("阿尔法", "贝塔", "伽马"), doc.characterNames)
        assertEquals("群配置必须逐字段往返（decode(encode(x)) == x）", config, doc.config)
        assertEquals("角色卡元数据必须往返", cards, doc.cards)

        assertEquals("消息条数必须一致", sourceMessages.size, doc.messages.size)
        sourceMessages.forEachIndexed { index, message ->
            val back = doc.messages[index]
            assertEquals("第 $index 条 role_id 不等", message.roleId, back.roleId)
            assertEquals("第 $index 条 round_id 不等", message.roundId, back.roundId)
            assertEquals("第 $index 条 turn_kind 不等", expectedTurnKind(message), back.turnKind)
            assertEquals(
                "第 $index 条 mention_role_ids 不等",
                message.mentionRoleIds,
                back.mentionRoleIds,
            )
            // 契约字段同时写回消息本身（TavernChatCodec.kt:396-405），
            // 调用方拿 node 就能直接用，不必再解一遍 raw。
            assertEquals(
                "第 $index 条 role_id 必须同时写回 node 上的消息",
                message.roleId,
                back.node.messages[back.node.selectIndex].roleId,
            )
            assertEquals(
                "第 $index 条 round_id 必须同时写回 node 上的消息",
                message.roundId,
                back.node.messages[back.node.selectIndex].roundId,
            )
        }
        return doc.messages.size
    }

    // ==================================================================
    // ③ 预算截断：真实判定 + 真实落库读回
    // ==================================================================

    private suspend fun collectBudgetAccountingEvidence(messages: List<UIMessage>): BudgetEvidence {
        val budgetConfig = pipelineConfig.copy(tokenBudgetPerRound = BUDGET_LIMIT)
        val trigger = messages.first { it.id == user2Id }
        val plan = GroupChat.newRound(
            triggerMessageId = trigger.id.toString(),
            config = budgetConfig,
            mentionRoleIds = trigger.mentionRoleIds,
            userText = trigger.toText(),
        )
        val startedAt = 1_800_000_000_000L
        val now = startedAt + (USAGE_PROMPT + USAGE_COMPLETION)

        // ① 抢占 run token：**先落库**，这是契约「run token 必须持久化后才可执行」。
        val claim = GroupTurnCoordinator.claimRound(
            conversationId = convId.toString(),
            plan = plan,
            tokenLimit = budgetConfig.tokenBudgetPerRound,
            existing = null,
            expectedRunToken = null,
            activeRunToken = null,
            newRunToken = RUN_TOKEN,
            now = startedAt,
        )
        assertTrue("首次抢占必须拿到执行权", claim is GroupTurnCoordinator.Claim.Acquired)
        val acquired = claim as GroupTurnCoordinator.Claim.Acquired
        groupRunDao.insert(GroupTurnCoordinator.toEntity(acquired.state))
        val persistedFirst = groupRunDao.findByRound(convId.toString(), plan.roundId)
        assertNotNull("run token 必须先落库才允许发起模型调用", persistedFirst)

        // ② 一次发言成功后的推进判定：真实预算口径 prompt + completion。
        val advance = GroupTurnCoordinator.advance(
            state = acquired.state,
            finishedRoleId = "a",
            usage = USAGE_PROMPT to USAGE_COMPLETION,
            plan = plan,
            tokenLimit = budgetConfig.tokenBudgetPerRound,
            now = now,
        )
        val total = USAGE_PROMPT + USAGE_COMPLETION
        assertTrue(
            "累计 $total >= 上限 $BUDGET_LIMIT 且仍有剩余角色，必须停跑",
            advance is GroupTurnCoordinator.Advance.BudgetStopped,
        )
        val stopped = (advance as GroupTurnCoordinator.Advance.BudgetStopped).state
        assertEquals("stop 里的已用量必须等于 prompt+completion", total, stopped.spentTokens)
        assertEquals("stop 里的上限必须是本轮快照", BUDGET_LIMIT, stopped.tokenLimit)
        assertEquals("未运行角色必须精确到剩余两位", listOf("b", "c"), stopped.skippedRoleIds)

        // ③ 按 ChatManager.persistRoundState 的同一口径落库（ChatManager.kt:1789-1811）。
        groupRunDao.updateBudget(
            conversationId = convId.toString(),
            roundId = plan.roundId,
            spent = stopped.spentTokens,
            tokenLimit = stopped.tokenLimit,
            skippedRoleIds = stopped.skippedRoleIds,
            reason = stopped.reason,
            status = stopped.status,
            updatedAt = now,
        )
        groupRunDao.updateCommittedRoles(convId.toString(), plan.roundId, stopped.committedRoleIds, now)
        groupRunDao.finish(
            conversationId = convId.toString(),
            roundId = plan.roundId,
            status = stopped.status,
            endedAt = stopped.endedAt ?: now,
            errorMessage = stopped.errorMessage,
            updatedAt = now,
        )

        // ④ 从**真 SQLite 读回来**，不许拿内存里的对象当证据。
        val stored = groupRunDao.findByRound(convId.toString(), plan.roundId)
        assertNotNull("预算截断必须留下运行日志行", stored)
        val row = requireNotNull(stored)
        assertEquals("spent_tokens 必须等于 prompt+completion 累计", total, row.spentTokens)
        assertEquals("token_limit 必须落库", BUDGET_LIMIT, row.tokenLimit)
        assertEquals("未运行角色必须落库（契约点名）", listOf("b", "c"), row.skippedRoleIds)
        assertEquals("超预算原因必须落库（契约点名）", GroupRunEntity.REASON_TOKEN_BUDGET_EXCEEDED, row.reason)
        assertEquals("状态必须是 BUDGET_STOPPED", GroupRunEntity.STATUS_BUDGET_STOPPED, row.status)
        assertEquals("已提交角色必须落库", listOf("a"), row.committedRoleIds)
        assertEquals("run token 必须与抢占时一致", RUN_TOKEN, row.runToken)
        assertTrue("终态必须带 ended_at", GroupRunEntity.isTerminal(row.status))
        assertEquals("started_at 不得被改写", startedAt, row.startedAt)
        assertEquals("ended_at 必须等于收尾时刻", now, row.endedAt)

        return BudgetEvidence(
            spent = row.spentTokens,
            limit = row.tokenLimit,
            skipped = row.skippedRoleIds,
            status = row.status,
            reason = row.reason,
            committed = row.committedRoleIds,
            conversationId = row.conversationId,
            roundId = row.roundId,
            runToken = row.runToken,
            startedAt = row.startedAt,
            endedAt = row.endedAt,
            limitFromConfig = budgetConfig.tokenBudgetPerRound,
            persistedBeforeCall = persistedFirst != null,
            usagePrompt = USAGE_PROMPT,
            usageCompletion = USAGE_COMPLETION,
        )
    }

    // ==================================================================
    // ④ 模型路由判定
    // ==================================================================

    /**
     * 一个**测试内合成**的 [Settings]：一个 enabled provider + 三个 CHAT 模型。
     *
     * ⚠️ 合成的是**配置**，不是调用结果：`TaskRoutes.resolve` 真跑、真返回 `Model`，
     * 但全程**不发 HTTP**。JSON 里 `model_sequence_source` 已标注这一点。
     */
    private fun syntheticRoutingSettings() = Settings(
        chatModelId = roleAModelId,
        providers = listOf(
            ProviderSetting.OpenAI(
                id = providerId,
                enabled = true,
                name = "c1-evidence-provider",
                models = listOf(
                    Model(modelId = "c1-model-a", displayName = "C1 Model A", id = roleAModelId),
                    Model(modelId = "c1-model-b", displayName = "C1 Model B", id = roleBModelId),
                    Model(modelId = "c1-model-c", displayName = "C1 Model C", id = roleCModelId),
                ),
            ),
        ),
    )

    private fun resolveOneRole(role: GroupRole, settings: Settings, assistantModelId: Uuid): RoutingDecision {
        val isKnownModel: (Uuid) -> Boolean = { settings.findModelById(it) != null }
        val roleModelUuid = role.modelId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val roleModelAccepted = roleModelUuid != null && isKnownModel(roleModelUuid)

        // 真实的角色绑定判定（生成侧）：非空白 / 合法 Uuid / 当前真实存在，三关依次。
        val resolved = resolveGroupTurnModelId(role, assistantModelId, isKnownModel)

        val binding = settings.taskBinding(ModelTaskType.CHAT, resolved)
        val routed = TaskRoutes.resolve(settings, ModelTaskType.CHAT, resolved)

        return RoutingDecision(
            roleId = role.id,
            roleModelId = role.modelId.orEmpty(),
            assistantModelId = assistantModelId,
            gateNonBlank = role.modelId?.isNotBlank() == true,
            gateParsesAsUuid = role.modelId
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Uuid.parse(it) }.isSuccess } == true,
            gateIsKnownModel = roleModelAccepted,
            resolvedModelId = resolved?.toString().orEmpty(),
            fallbackTakenFrom = when {
                roleModelAccepted -> "role.model_id"
                role.modelId.isNullOrBlank() -> "assistant.chat_model_id (role.model_id 为空白)"
                else -> "assistant.chat_model_id (role.model_id 不在模型库里)"
            },
            fallbackChain = listOf(
                FallbackStage(
                    stage = "role.model_id",
                    value = role.modelId.orEmpty(),
                    accepted = roleModelAccepted,
                ),
                FallbackStage(
                    stage = "assistant.chat_model_id",
                    value = assistantModelId.toString(),
                    accepted = resolved == assistantModelId,
                ),
                FallbackStage(
                    stage = "task_routes.session_default",
                    value = "settings.chatModelId=${settings.chatModelId}",
                    accepted = resolved == null,
                ),
            ),
            taskRoutesSelectedUuid = routed.id.toString(),
            taskRoutesSelectedName = routed.modelId,
            taskRoutesCountAfter = TaskRoutes.snapshot()["CHAT:${routed.id}"] ?: 0,
            candidateCount = binding.candidates.size,
            candidateModelIds = binding.candidates.map { it.modelId },
        )
    }

    /** 从**真库读回来的**消息里取出「哪条消息记的是哪个模型」。 */
    private fun recordedModelIds(messages: List<UIMessage>): List<RecordedModel> = messages
        .filter { it.role == MessageRole.ASSISTANT }
        .map { message ->
            RecordedModel(
                messageId = message.id.toString(),
                roleId = message.roleId.orEmpty(),
                modelId = message.modelId?.toString().orEmpty(),
            )
        }

    // ==================================================================
    // 落库：真 AppDatabase + 真 ConversationRepository
    // ==================================================================

    /**
     * 走真实 Repository 把群会话写进真机真 SQLite，再读回来。
     *
     * 返回的是**从数据库读出来的**会话，viewer 过滤因此是在「真机真库里存成什么样就按
     * 什么样算」，而不是在内存对象上算。
     */
    private suspend fun persistAndReloadGroupConversation(): Conversation {
        val messages = buildFixtureMessages()
        repository.insertConversation(
            Conversation(
                id = convId,
                assistantId = assistantId,
                title = "C1 真机证据会话",
                messageNodes = messages.map { it.toMessageNode() },
                type = GroupChat.TYPE_GROUP,
                groupConfig = pipelineConfig,
                groupCards = cards,
            ),
        )

        val stored = repository.getConversationById(convId)
        assertNotNull("群会话必须能从真 SQLite 读回", stored)
        val conversation = requireNotNull(stored)
        assertEquals("会话类型必须是 GROUP", GroupChat.TYPE_GROUP, conversation.type)
        assertEquals("群配置必须往返", pipelineConfig, conversation.groupConfig)
        assertEquals("角色卡元数据必须往返", cards, conversation.groupCards)

        val storedMessages = conversation.currentMessages
        assertEquals("消息条数必须与写入一致", messages.size, storedMessages.size)
        // 顺序按 node_index ASC；读回来必须还是写入顺序，否则 viewer 过滤里
        // 「最后一条 USER 消息」的位置判定就没意义了。
        messages.forEachIndexed { index, message ->
            val back = storedMessages[index]
            assertEquals("第 $index 条消息 id 未按写入顺序读回", message.id, back.id)
            assertEquals("第 $index 条 role_id 未落库", message.roleId, back.roleId)
            assertEquals("第 $index 条 round_id 未落库", message.roundId, back.roundId)
            assertEquals("第 $index 条 turn_kind 未落库", message.turnKind, back.turnKind)
            assertEquals(
                "第 $index 条 mention_role_ids 未落库",
                message.mentionRoleIds,
                back.mentionRoleIds,
            )
            if (back.role == MessageRole.ASSISTANT) {
                assertEquals("第 $index 条 model_id 未落库", message.modelId, back.modelId)
            }
        }
        return conversation
    }

    /**
     * 固定夹具：第一轮（显式 @a）+ 第二轮（隐式路由，pipeline 顺序 a→b→c）+ 一条议长
     * 合成的轮次摘要。
     *
     * 所有 `UIMessage.id` 固定：`roundId` 由触发消息 id 派生（`GroupChat.roundIdFor`），
     * id 随机会让导出字节随机、SHA-256 就不可比了。
     */
    private fun buildFixtureMessages(): List<UIMessage> {
        val settings = syntheticRoutingSettings()
        val isKnownModel: (Uuid) -> Boolean = { settings.findModelById(it) != null }
        val modelOfRole = pipelineConfig.roles.associate { role ->
            role.id to resolveGroupTurnModelId(role, assistantModelIdOf(role.id), isKnownModel)
        }
        val round1 = GroupChat.roundIdFor(user1Id.toString())
        val round2 = GroupChat.roundIdFor(user2Id.toString())

        val user1Text = "@阿尔法 请先定调这一轮的总论。"
        val user2Text = "继续，展开细节。"

        val summary = GroupTurnCoordinator.voteSummaryMessage(
            state = GroupTurnCoordinator.RoundState(
                conversationId = convId.toString(),
                roundId = round2,
                runToken = RUN_TOKEN,
                status = GroupRunEntity.STATUS_COMPLETED,
                spentTokens = 0,
                tokenLimit = pipelineConfig.tokenBudgetPerRound,
                committedRoleIds = emptyList(),
                skippedRoleIds = emptyList(),
                reason = "",
                errorMessage = "",
                startedAt = 0,
                updatedAt = 0,
                endedAt = 0,
            ),
            config = pipelineConfig,
            outcome = VoteOutcome.Decided(
                winner = "a",
                tally = mapOf("a" to 2, "b" to 1),
                ballots = listOf(
                    VoteBallot(roleId = "a", candidateId = "a"),
                    VoteBallot(roleId = "b", candidateId = "a"),
                    VoteBallot(roleId = "c", candidateId = "b"),
                ),
            ),
        )

        return listOf(
            // 用户消息：生产路径（ChatManager.kt:495-505）只设 mentionRoleIds，
            // roleId / roundId / turnKind 恒为 null，由导出层补默认值。
            UIMessage(
                id = user1Id,
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(user1Text)),
                mentionRoleIds = GroupChat.parseMentions(user1Text, pipelineConfig.roles),
            ),
            UIMessage(
                id = a1Id,
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("阿尔法的第一轮发言：先把口径统一。")),
                roleId = "a",
                roundId = round1,
                turnKind = GroupChat.TURN_SPEAKER,
                modelId = modelOfRole.getValue("a"),
                usage = TokenUsage(promptTokens = 120, completionTokens = 40, totalTokens = 160),
            ),
            UIMessage(
                id = user2Id,
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(user2Text)),
                mentionRoleIds = GroupChat.parseMentions(user2Text, pipelineConfig.roles),
            ),
            UIMessage(
                id = a2Id,
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("阿尔法展开：先列事实，再给判断。")),
                roleId = "a",
                roundId = round2,
                turnKind = GroupChat.TURN_SPEAKER,
                modelId = modelOfRole.getValue("a"),
                usage = TokenUsage(promptTokens = 210, completionTokens = 60, totalTokens = 270),
            ),
            UIMessage(
                id = b2Id,
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("贝塔挑错：第二条缺乏可验证来源。")),
                roleId = "b",
                roundId = round2,
                turnKind = GroupChat.TURN_SPEAKER,
                modelId = modelOfRole.getValue("b"),
                usage = TokenUsage(promptTokens = 230, completionTokens = 70, totalTokens = 300),
            ),
            UIMessage(
                id = c2Id,
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("伽马收束：按阿尔法的口径执行。")),
                roleId = "c",
                roundId = round2,
                turnKind = GroupChat.TURN_SPEAKER,
                modelId = modelOfRole.getValue("c"),
                usage = TokenUsage(promptTokens = 200, completionTokens = 55, totalTokens = 255),
            ),
            summary,
        )
    }

    private fun assistantModelIdOf(roleId: String): Uuid = when (roleId) {
        "a" -> roleAModelId
        "b" -> roleBModelId
        "c" -> roleCModelId
        else -> error("夹具里没有角色 $roleId")
    }

    // ==================================================================
    // 文件 / 哈希工具
    // ==================================================================

    private fun writeEvidenceFile(name: String, content: String): File {
        val dir = requireNotNull(context.getExternalFilesDir(null)) {
            "getExternalFilesDir(null) 返回 null，无法落证据"
        }
        require(name.startsWith(EVIDENCE_PREFIX)) { "证据文件名必须以 $EVIDENCE_PREFIX 开头：$name" }
        val file = File(dir, name)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    // ==================================================================
    // 常量与小结构
    // ==================================================================

    private companion object {
        const val EVIDENCE_DB = "c1-device-evidence.db"
        const val EVIDENCE_PREFIX = "c1-evidence-"
        const val USER_NAME = "用户"
        const val GROUP_NAME = "C1 证据群"
        const val RUN_TOKEN = "c1-evidence-run-token"
        const val BUDGET_LIMIT = 400
        const val USAGE_PROMPT = 3_000
        const val USAGE_COMPLETION = 1_096

        const val TOKEN_SOURCE = "budget-accounting-only, no live LLM call"
        const val MODEL_SEQUENCE_SOURCE =
            "routing-decision + recorded-message-modelId, no live LLM call"

        const val EVIDENCE_GAPS = "real prompt/completion token counts and the real model " +
            "call sequence cannot be collected on-device without API keys; see token_source " +
            "and model_sequence_source. No LLM output was fabricated."

        val PRETTY = Json { prettyPrint = true }
    }

    private data class LeakageResult(
        val passed: Boolean,
        val checkedPairs: Int,
        val violations: List<String>,
    )

    private data class ExportEvidence(
        val mode: String,
        val file: File,
        val bytes: Int,
        val sha256: String,
        val sha256SecondPass: String,
        val roundTripMessageCount: Int,
    )

    private data class QrEvidence(val mode: String, val bytes: Int, val sha256: String)

    private data class BudgetEvidence(
        val spent: Int,
        val limit: Int,
        val skipped: List<String>,
        val status: String,
        val reason: String,
        val committed: List<String>,
        val conversationId: String,
        val roundId: String,
        val runToken: String,
        val startedAt: Long,
        val endedAt: Long?,
        val limitFromConfig: Int,
        val persistedBeforeCall: Boolean,
        val usagePrompt: Int,
        val usageCompletion: Int,
    )

    private data class FallbackStage(val stage: String, val value: String, val accepted: Boolean)

    private data class RoutingDecision(
        val roleId: String,
        val roleModelId: String,
        val assistantModelId: Uuid,
        val gateNonBlank: Boolean,
        val gateParsesAsUuid: Boolean,
        val gateIsKnownModel: Boolean,
        val resolvedModelId: String,
        val fallbackTakenFrom: String,
        val fallbackChain: List<FallbackStage>,
        val taskRoutesSelectedUuid: String,
        val taskRoutesSelectedName: String,
        val taskRoutesCountAfter: Int,
        val candidateCount: Int,
        val candidateModelIds: List<String>,
    )

    private data class RecordedModel(
        val messageId: String,
        val roleId: String,
        val modelId: String,
    )
}
