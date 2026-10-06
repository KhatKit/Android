package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.ai.tavern.GroupImportReport
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.model.Conversation
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import kotlin.uuid.Uuid

/**
 * 酒馆群聊文件（`.jsonl`）**回导**生产入口的判定层（`resolveTavernGroupImport` +
 * `applyTavernGroupImport`）的契约测试。
 *
 * ## 补的是哪个缺口
 *
 * [TavernChatCodec.exportGroupJsonl] 早有生产入口（[GroupTavernExportCard]，其 UI 文案写着
 * 「可在 SillyTavern 打开并回导」），而 [TavernChatCodec.importGroup] 在 `app/src/main` 里
 * **零调用方**。也就是说导得出、导不回，导出卡片自己承诺的那一步点不到。
 * 本类钉住补上这个入口之后的判定行为。
 *
 * ## 钉住的七件事
 *
 * 1. `Clean` 分支正常落库（[a clean file lands with its config and messages]）。
 * 2. `Normalized(fixes)` 分支**把 fixes 显示出来**（[a normalized file shows every fix]）。
 * 3. `NoConfig` 分支**不当成错误**（[a file without a config block is not an error]）——
 *    这是最容易写错的一条，单独一组断言钉住。
 * 4. `Rejected`（密钥黑名单）分支**拒绝落库**（[a smuggled api key is refused]）。
 * 5. 导入后四个契约字段**逐条相等**（[the four contract fields survive the round trip]）。
 * 6. 非法 UTF-8 / 空文本 / 非 JSONL 输入不崩（[garbled input never throws] 等三条）。
 * 7. 落库前那道 `validate` 闸门真能拦住**落库**（[a foreign memory space key is refused]）——
 *    这一条证明闸门不是装饰：删掉 `resolveTavernGroupImport` 里那行 `GroupChat.validate`，
 *    本类会红（见该测试内的说明）。
 *
 * ## 判定口径：`GroupImportReport`，不是 `config == null`
 *
 * [TavernChatCodec.importGroup] 的 KDoc（`TavernChatCodec.kt:281-291`）写死了这条：
 * `config == null` 有四种原因，报告是唯一能把它们分开的东西，
 * **「调用方不能只看 `config == null` 就当失败，要看 `GroupImportReport`」**。
 * 下面每一组测试都以 `importReport` 为断言对象，不以 `config` 是否为 null 代替。
 *
 * **不证明什么**：真机上的文件选择器交互、SAF 读字节、Toast 与结果视图的真实渲染、
 * 落库后的会话刷新全部**未在设备上验证**。本类只覆盖纯判定层。
 */
class GroupTavernImportTest {

    private val json = Json { encodeDefaults = false }

    // ---------------- 1. Clean 分支：正常落库 ----------------

    @Test
    fun `a clean file lands with its config and messages`() {
        val config = groupConfig()

        val accepted = accepted(resolve(exportGroupJsonl(config), CONVERSATION_ID))

        assertEquals(
            "合法文件必须是 Clean，不是别的分支",
            GroupImportReport.Clean,
            accepted.report,
        )
        assertEquals("群配置必须原样落库", config, accepted.config)
        assertEquals("消息必须一条不少地落库", nodes().size, accepted.nodes.size)
        assertFalse("Clean 不是错误", accepted.isError)
        assertEquals(emptyList<String>(), accepted.detailLines)

        // 落库：群配置换新、消息追加到 messageNodes。
        val conversation = groupConversation().applyTavernGroupImport(accepted)
        assertEquals(config, conversation.groupConfig)
        assertEquals(GroupChat.TYPE_GROUP, conversation.type)
        assertEquals(nodes().size, conversation.messageNodes.size)
        assertEquals(
            cards().map { it.roleId },
            conversation.groupCards?.map { it.roleId },
        )
    }

    @Test
    fun `an imported conversation keeps the messages that were already there`() {
        // 导入是「追加」不是「覆盖」：用户已有的历史不该被一份文件抹掉。
        val existing = MessageNode.of(UIMessage.user("导入之前就有的"))
        val before = groupConversation(
            nodes = listOf(existing),
        )

        val after = before.applyTavernGroupImport(
            accepted(resolve(exportGroupJsonl(groupConfig()), CONVERSATION_ID)),
        )

        assertEquals(1 + nodes().size, after.messageNodes.size)
        assertEquals(
            "原有消息必须还在，且顺序在前",
            existing,
            after.messageNodes.first(),
        )
    }

    // ---------------- 2. Normalized(fixes) 分支：fixes 必须显示 ----------------

    @Test
    fun `a normalized file shows every fix`() {
        // revision = 0 → screenImportedConfig 归一化成 1，并记一条 fix。
        val broken = groupConfig().copy(revision = 0)

        val accepted = accepted(resolve(exportGroupJsonl(broken), CONVERSATION_ID))

        val report = accepted.report as GroupImportReport.Normalized
        assertEquals(1, report.fixes.size)
        assertEquals(GroupChat.DEFAULT_REVISION, accepted.config?.revision)

        // ⚠️ 这条是本任务的核心断言之一：fixes 必须在**展示文案**里出现，
        // 而且改前改后的字面值都在（不是一句「已修正」）。
        val fix = report.fixes.single()
        assertTrue(
            "结果视图必须列出 revision 这一条 fix，实际：${accepted.detailLines}",
            accepted.detailLines.any { it.contains("revision") },
        )
        assertTrue(
            "fix 的描述必须出现在展示文案里，实际：${accepted.detailLines}",
            accepted.detailLines.any { it.contains(fix.message) },
        )
        assertTrue(
            "改前的字面值 0 必须显示出来，实际：${accepted.detailLines}",
            accepted.detailLines.any { it.contains(fix.original) && it.contains("→") },
        )
        assertTrue(
            "改后的字面值必须显示出来，实际：${accepted.detailLines}",
            accepted.detailLines.any { it.contains(fix.corrected) },
        )
        assertFalse("归一化不是错误", accepted.isError)
        assertTrue(
            "成功行要说清归一化了几处",
            accepted.headline.contains("归一化"),
        )
    }

    @Test
    fun `an unknown tie policy is normalized and its fix is shown too`() {
        val broken = groupConfig().copy(tiePolicy = "coin_flip")

        val accepted = accepted(resolve(exportGroupJsonl(broken), CONVERSATION_ID))

        assertTrue(accepted.report is GroupImportReport.Normalized)
        assertEquals(GroupChat.TIE_FAIL, accepted.config?.tiePolicy)
        assertTrue(
            "tie_policy 的 fix 必须显示，实际：${accepted.detailLines}",
            accepted.detailLines.any { it.contains("tie_policy") && it.contains("coin_flip") },
        )
    }

    // ---------------- 3. NoConfig 分支：不是错误（最容易写错的一条） ----------------

    @Test
    fun `a file without a config block is not an error`() {
        // 纯酒馆群聊文件：khatkit_group 在，但里面没有 config 键。
        // 「没有」与「坏掉」必须分开，否则一份完全正常的文件会被报成导入失败。
        val accepted = accepted(resolve(payloadJsonl(config = null), CONVERSATION_ID))

        assertEquals(
            "没有配置块必须是 NoConfig，不能被当成 Rejected",
            GroupImportReport.NoConfig,
            accepted.report,
        )
        assertFalse("⚠️ NoConfig 绝不能被标成错误", accepted.isError)
        assertTrue(
            "成功行要写明「沿用当前群配置」，让用户知道配置没被覆盖",
            accepted.headline.contains("没有群配置块"),
        )
        assertTrue(accepted.headline.contains("沿用当前群配置"))
        // 消息照常拿得到、照常落库。
        assertEquals(1, accepted.nodes.size)
        assertNull("NoConfig 分支的 config 恒为 null", accepted.config)
        // NoConfig 不带任何错误明细：没有可报的字段级错误，也没有要显示的修正项。
        assertEquals(
            "⚠️ NoConfig 不得凭空长出错误明细",
            emptyList<String>(),
            accepted.detailLines,
        )
    }

    @Test
    fun `a NoConfig file keeps the conversation's own group config instead of nulling it`() {
        val mine = groupConfig()
        val before = groupConversation(config = mine)

        val after = before.applyTavernGroupImport(
            accepted(resolve(payloadJsonl(config = null), CONVERSATION_ID)),
        )

        assertEquals(
            "⚠️ 拿 null 覆盖会把一个还能用的群抹成 group_config 为空",
            mine,
            after.groupConfig,
        )
        assertEquals(1, after.messageNodes.size)
    }

    @Test
    fun `a file without the khatkit_group block at all is not a config problem`() {
        // 缺整个 khatkit_group 块 → importGroup 返回 null → Unsupported，
        // 话术必须是「不是酒馆群聊文件」，不能报成「群配置校验未通过」。
        val header = buildJsonObject {
            put("spec", "st_chat_v1")
            put("user_name", USER_NAME)
            put("character_name", "酒馆群")
        }
        val raw = listOf(header, messageJson()).joinToString("\n") { encode(it) }

        val outcome = resolve(raw, CONVERSATION_ID)

        val unsupported = outcome as TavernGroupImportOutcome.Unsupported
        assertTrue(unsupported.isError)
        assertTrue(
            "话术要指向「文件不对」而不是「群配置不对」，实际：${unsupported.headline}",
            unsupported.headline.contains("不是酒馆群聊文件"),
        )
        assertEquals(
            "Unsupported 分支绝不能被当成 Accepted（那会拿 null 落库）",
            "Unsupported",
            branchOf(outcome),
        )
    }

    // ---------------- 4. Rejected 分支：绝不落库 ----------------

    @Test
    fun `a smuggled api key is refused`() {
        val leaked = buildJsonObject {
            GroupChat.encodeConfigObject(groupConfig()).forEach { (k, v) -> put(k, v) }
            put("api_key", "sk-should-never-be-read")
        }

        val outcome = resolve(payloadJsonl(config = leaked), CONVERSATION_ID)

        val rejected = outcome as TavernGroupImportOutcome.Rejected
        assertTrue("密钥黑名单命中必须是错误", rejected.isError)
        assertTrue(
            "原因里要点名命中的键，实际：${rejected.reason}",
            rejected.reason.contains("api_key"),
        )
        assertTrue(
            "字段级错误要逐条显示，实际：${rejected.detailLines}",
            rejected.detailLines.any { it.contains("api_key") },
        )
        // Rejected 分支**不带任何可落库的载荷**：applyTavernGroupImport 的入参类型
        // 就是 Accepted，所以拿不到东西去写库（类型层面保证，见 GroupTavernImport.kt 的 ②）。
        assertEquals(
            "Rejected 绝不能是 Accepted",
            "Rejected",
            branchOf(outcome),
        )
    }

    @Test
    fun `a structurally invalid config is refused with field level errors`() {
        val broken = buildJsonObject {
            GroupChat.encodeConfigObject(
                groupConfig().copy(roles = listOf(
                    GroupRole(id = "a", name = "甲", assistantId = ASSISTANT_A),
                    GroupRole(id = "a", name = "另一个甲", assistantId = ASSISTANT_B),
                )),
            ).forEach { (k, v) -> put(k, v) }
        }

        val rejected = resolve(payloadJsonl(config = broken), CONVERSATION_ID)
            as TavernGroupImportOutcome.Rejected

        assertTrue(rejected.reason.contains("未通过") || rejected.reason.contains("校验"))
        assertTrue(
            "重复 role_id 必须逐条显示，实际：${rejected.detailLines}",
            rejected.detailLines.any { it.contains("role_id") },
        )
        assertTrue(rejected.headline.contains("酒馆群聊文件"))
    }

    // ---------------- 落库前那道 validate 闸门 ----------------

    @Test
    fun `a foreign memory space key is refused`() {
        // ⚠️ 非空验证点：删掉 resolveTavernGroupImport 里那行
        //    `GroupChat.validate(config, conversationId)` 的 **if 分支**，
        //    本测试会红（下面 assertEquals(Rejected, ...) 与 assertNull(config) 同时失败）。
        //
        // 为什么这条能穿过 screening：screenImportedConfig 内部是 validate(config)，
        // conversationId 为 null，记忆空间键那条够不着（GroupChat.kt:466 的前置条件）。
        // 也就是说这份带**外来记忆空间键**的配置在 codec 眼里是 Clean，
        // 只有落库前那道带 conversationId 的闸门拦得住。
        val foreign = groupConfig().copy(
            roles = listOf(
                GroupRole(
                    id = "ada",
                    name = "艾达",
                    assistantId = ASSISTANT_A,
                    // 别人会话的键。硬约束：必须是 group:<conversationId>:role:<roleId>，
                    // 失败绝不回退全局 / 助手空间。
                    memorySpaceId = GroupChat.memorySpaceId("另一个会话", "ada"),
                ),
                GroupRole(id = "bob", name = "鲍勃", assistantId = ASSISTANT_B),
            ),
        )

        // 先确认它确实穿过了 codec 的 screening（否则这条测试证明不了闸门在干活）。
        val document = requireNotNull(
            TavernChatCodec.importGroup(exportGroupJsonl(foreign)),
        ) { "fixture 必须能被 importGroup 认出来" }
        assertEquals(
            "前提：codec 侧的 screening 不带 conversationId，记忆空间键它够不着",
            GroupImportReport.Clean,
            document.importReport,
        )

        // 落库前那道闸门必须把它拒掉。
        val outcome = resolve(exportGroupJsonl(foreign), CONVERSATION_ID)

        val rejected = outcome as TavernGroupImportOutcome.Rejected
        assertEquals("群配置校验未通过", rejected.reason)
        assertTrue(
            "必须点名 memory_space_id，实际：${rejected.detailLines}",
            rejected.detailLines.any { it.contains("memory_space_id") },
        )
        assertTrue(
            "错误信息要写明期望的派生键格式",
            rejected.detailLines.any { it.contains(GroupChat.memorySpaceId(CONVERSATION_ID, "ada")) },
        )
    }

    @Test
    fun `a correctly derived memory space key passes the gate`() {
        // 反面：闸门不能把**合法**的那一份也拒了。
        val correct = groupConfig().copy(
            roles = listOf(
                GroupRole(
                    id = "ada",
                    name = "艾达",
                    assistantId = ASSISTANT_A,
                    memorySpaceId = GroupChat.memorySpaceId(CONVERSATION_ID, "ada"),
                ),
                GroupRole(id = "bob", name = "鲍勃", assistantId = ASSISTANT_B),
            ),
        )

        val accepted = accepted(resolve(exportGroupJsonl(correct), CONVERSATION_ID))

        assertEquals(GroupImportReport.Clean, accepted.report)
        assertEquals(
            "合法的记忆空间键必须原样落库",
            GroupChat.memorySpaceId(CONVERSATION_ID, "ada"),
            accepted.config?.roles?.first()?.memorySpaceId,
        )
    }

    // ---------------- 5. 四个契约字段逐条相等 ----------------

    @Test
    fun `the four contract fields survive the round trip`() {
        // 契约（TavernChatCodec.kt:256-268）：exportGroup → importGroup 之后
        // role_id / round_id / turn_kind / mention_role_ids / 群配置 / 角色卡元数据都相等。
        // 本类把「相等」钉在**落库之后**的会话消息上，而不只是 codec 的返回值上。
        val original = nodes()
        val raw = TavernChatCodec.exportGroupJsonl(
            nodes = original,
            config = groupConfig(),
            cards = cards(),
            userName = USER_NAME,
            groupName = GROUP_NAME,
        )

        val imported = groupConversation().applyTavernGroupImport(
            accepted(resolve(raw, CONVERSATION_ID)),
        )

        assertEquals(
            "消息条数必须逐条对应",
            original.size,
            imported.messageNodes.size,
        )
        original.forEachIndexed { index, node ->
            val from = node.currentMessage
            val to = imported.messageNodes[index].currentMessage
            val where = "第 $index 条"
            assertEquals("$where：role_id 必须原样保留", from.roleId, to.roleId)
            assertEquals("$where：round_id 必须原样保留", from.roundId, to.roundId)
            assertEquals("$where：turn_kind 必须原样保留", from.turnKind, to.turnKind)
            assertEquals(
                "$where：mention_role_ids 必须原样保留",
                from.mentionRoleIds,
                to.mentionRoleIds,
            )
            assertEquals("$where：正文不能被导入路径改写", from.toText(), to.toText())
        }
        // 逐条点算：四个字段确实覆盖了 fixture 里的非空取值，不是恰好全为 null 才相等。
        // 第 0 条是用户提问，按契约它没有 role_id（发言者才写 role_id），其余两条各自带值。
        assertEquals(
            "fixture 必须真的带了 role_id（用户轮为 null）",
            listOf(null, "a", "b"),
            original.map { it.currentMessage.roleId },
        )
        assertEquals(listOf("r1", "r1", "r1"), original.map { it.currentMessage.roundId })
        assertEquals(
            listOf(GroupChat.TURN_USER, GroupChat.TURN_SPEAKER, GroupChat.TURN_CHAIR),
            original.map { it.currentMessage.turnKind },
        )
        assertEquals(
            listOf(emptyList(), emptyList(), listOf("a")),
            original.map { it.currentMessage.mentionRoleIds },
        )
    }

    @Test
    fun `a missing mention list comes back as an empty list not null`() {
        // mention_role_ids 在导出时恒被写成数组（可能是空数组），
        // 所以导入回来必然是空列表而不是 null —— 这条钉住「不会漏字段」。
        val one = listOf(
            MessageNode.of(UIMessage.assistant("没 @ 任何人").copy(roleId = "a")),
        )
        val raw = TavernChatCodec.exportGroupJsonl(
            nodes = one,
            config = groupConfig(),
            cards = cards(),
            userName = USER_NAME,
            groupName = GROUP_NAME,
        )

        val accepted = accepted(resolve(raw, CONVERSATION_ID))

        assertEquals(emptyList<String>(), accepted.nodes.single().currentMessage.mentionRoleIds)
    }

    @Test
    fun `every imported message is kept without filtering`() {
        // 硬约束：过滤只发生在发给模型的副本上，库存消息保持完整。
        // 造一条「视角上不该被某个角色看见」的消息（别人 role_id 的发言），
        // 导入后它必须仍在 messageNodes 里。
        val mixed = listOf(
            MessageNode.of(UIMessage.user("提问").copy(roundId = "r1")),
            MessageNode.of(
                UIMessage.assistant("甲的发言").copy(
                    roleId = "a",
                    roundId = "r1",
                    turnKind = GroupChat.TURN_SPEAKER,
                ),
            ),
            MessageNode.of(
                UIMessage.assistant("乙的发言").copy(
                    roleId = "b",
                    roundId = "r1",
                    turnKind = GroupChat.TURN_SPEAKER,
                ),
            ),
        )
        val raw = TavernChatCodec.exportGroupJsonl(
            nodes = mixed,
            config = groupConfig(),
            cards = cards(),
            userName = USER_NAME,
            groupName = GROUP_NAME,
        )

        val imported = groupConversation().applyTavernGroupImport(
            accepted(resolve(raw, CONVERSATION_ID)),
        )

        assertEquals(
            "导入路径不得做任何过滤：三条消息必须全在",
            listOf("a", "b"),
            imported.messageNodes.drop(1).map { it.currentMessage.roleId },
        )
    }

    // ---------------- 6. 坏输入不崩 ----------------

    @Test
    fun `garbled input never throws`() {
        // 非法 UTF-8 字节：孤立的 0xC3（截断的多字节序列）+ 一个开括号。
        // decodeTavernImportBytes 必须替换而不是抛 MalformedInputException，
        // 之后由 resolveTavernGroupImport 判成 Unsupported。
        val broken = byteArrayOf('['.code.toByte(), 0xC3.toByte(), ']'.code.toByte())

        val text = decodeTavernImportBytes(broken)

        assertNotNull("坏字节必须解码成替换字符而不是抛异常", text)
        val outcome = resolve(text!!, CONVERSATION_ID)
        assertTrue(
            "坏输入必须落到 Unsupported，实际：$outcome",
            outcome is TavernGroupImportOutcome.Unsupported,
        )
        assertTrue(outcome.isError)
    }

    @Test
    fun `blank text is refused without a crash`() {
        listOf("", "   ", "\n\n\t").forEach { raw ->
            val outcome = resolve(raw, CONVERSATION_ID)
            assertTrue(
                "空文本必须落到 Unsupported，实际：'$raw' → $outcome",
                outcome is TavernGroupImportOutcome.Unsupported,
            )
            assertTrue(outcome.headline.contains("空"))
        }
    }

    @Test
    fun `non jsonl input is refused without a crash`() {
        listOf(
            "这是一段普通中文，不是 JSON",
            "{ 只有一个左括号",
            "[{\"mes\": \"孤立的数组\"}, 这行不是 JSON]",
            "12345",
            "null",
        ).forEach { raw ->
            val outcome = resolve(raw, CONVERSATION_ID)
            assertTrue(
                "非 JSONL 输入必须落到 Unsupported（而不是 Accepted/Rejected），" +
                    "实际：'$raw' → $outcome",
                outcome is TavernGroupImportOutcome.Unsupported,
            )
        }
    }

    @Test
    fun `a utf8 bom is stripped so the file still parses`() {
        // Windows 编辑器很容易在 JSONL 开头留 BOM；不剥掉的话 Json 直接抛，
        // 一份完全正常的文件会被报成「不是酒馆群聊文件」。
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            exportGroupJsonl(groupConfig()).toByteArray(StandardCharsets.UTF_8)

        val text = decodeTavernImportBytes(withBom)

        assertNotNull(text)
        assertFalse("BOM 必须被剥掉", text!!.startsWith("\uFEFF"))
        assertTrue(resolve(text, CONVERSATION_ID) is TavernGroupImportOutcome.Accepted)
    }

    @Test
    fun `an oversized file is refused instead of read into memory`() {
        val tooBig = ByteArray(TAVERN_IMPORT_MAX_BYTES + 1)

        assertNull("超过上限必须回 null（不当成合法文件）", decodeTavernImportBytes(tooBig))
        // 边界：正好等于上限仍然放行。
        assertNotNull(
            "正好等于上限必须放行",
            decodeTavernImportBytes(ByteArray(TAVERN_IMPORT_MAX_BYTES)),
        )
    }

    @Test
    fun `the import mime type stays the repo's existing json convention`() {
        // 不得引入新 mime，也不得与导出侧分叉：读文件与写文件共用一个常量。
        assertEquals("application/json", TAVERN_IMPORT_MIME_TYPE)
        assertEquals(GROUP_EXPORT_MIME_TYPE, TAVERN_IMPORT_MIME_TYPE)
    }

    // ---------------- 卡片可见性（isGroupConversation 严格口径） ----------------

    @Test
    fun `the import card shows only for strict group conversations`() {
        // 与 GroupTavernExportTest 的同名测试是同一口径的两处复述：
        // 导出与回导两张卡必须对「同一个会话是不是群聊」给出同一个答案。
        val group = groupConversation()
        assertTrue(isGroupConversation(group))
        // 老数据可能残留 group_config 却已被改回单聊，这时绝不能给群聊入口。
        assertFalse(isGroupConversation(group.copy(type = GroupChat.TYPE_DIRECT)))
        assertFalse(isGroupConversation(group.copy(groupConfig = null)))
    }

    // ---------------- 报告与 config 必须一起读 ----------------

    @Test
    fun `NoConfig and Rejected are told apart by the report not by a null config`() {
        // 这是 TavernChatCodec KDoc 那句警告的回归：两种分支 config 都是 null，
        // 只看 config 会把它们混成一种。
        val noConfig = resolve(payloadJsonl(config = null), CONVERSATION_ID)
        val rejected = resolve(
            payloadJsonl(
                config = buildJsonObject {
                    GroupChat.encodeConfigObject(groupConfig()).forEach { (k, v) -> put(k, v) }
                    put("token", "sk-nope")
                },
            ),
            CONVERSATION_ID,
        )

        assertTrue(
            "两者都必须是带可落库/不可落库之别的不同分支",
            noConfig is TavernGroupImportOutcome.Accepted && noConfig.report == GroupImportReport.NoConfig,
        )
        assertTrue(rejected is TavernGroupImportOutcome.Rejected)
        assertFalse(
            "⚠️ NoConfig 不得与 Rejected 混成一种",
            (noConfig as TavernGroupImportOutcome.Accepted).report is GroupImportReport.Rejected,
        )
    }

    // ---------------- fixtures ----------------

    private fun resolve(raw: String, conversationId: String = CONVERSATION_ID) =
        resolveTavernGroupImport(raw, conversationId)

    private fun accepted(outcome: TavernGroupImportOutcome): TavernGroupImportOutcome.Accepted =
        outcome as? TavernGroupImportOutcome.Accepted
            ?: error("期望落库，实际是 $outcome")

    /** 分支名。用字符串而不是 `is` 断言：编译器已经把窄化后的类型推死，`is` 会退化成恒假检查。 */
    private fun branchOf(outcome: TavernGroupImportOutcome): String =
        outcome::class.simpleName ?: "unknown"

    private fun encode(element: kotlinx.serialization.json.JsonElement): String =
        json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)

    private fun exportGroupJsonl(config: GroupConfig, nodes: List<MessageNode> = nodes()): String =
        TavernChatCodec.exportGroupJsonl(
            nodes = nodes,
            config = config,
            cards = cards(),
            userName = USER_NAME,
            groupName = GROUP_NAME,
        )

    /** 手拼一份酒馆群聊文件。`config` 传 null = 文件里没有配置块（NoConfig 那条路径）。 */
    private fun payloadJsonl(config: JsonObject?): String {
        val payload = buildJsonObject {
            put("kind", GroupChat.QR_KIND)
            put("schema_version", GroupChat.SCHEMA_VERSION)
            put("name", GROUP_NAME)
            config?.let { put("config", it) }
            put("cards", buildJsonArray { cards().forEach { add(it.toJson()) } })
        }
        val header = buildJsonObject {
            put("spec", "st_chat_v1")
            put("user_name", USER_NAME)
            put("character_name", GROUP_NAME)
            put("chat_metadata", buildJsonObject { put("is_group", true) })
            put(TavernChatCodec.GROUP_FIELD, payload)
        }
        return listOf(header, messageJson()).joinToString("\n") { encode(it) }
    }

    private fun RoleCardMeta.toJson(): JsonObject = buildJsonObject {
        put("role_id", roleId)
        put("name", name)
        put("assistant_id", assistantId)
        cardId?.let { put("card_id", it) }
        put("persona", persona)
    }

    private fun messageJson(): JsonObject = buildJsonObject {
        put("name", "艾达")
        put("is_user", false)
        put("is_system", false)
        put("mes", JsonPrimitive("艾达发言"))
        put(TavernChatCodec.FIELD_ROLE_ID, JsonPrimitive("a"))
        put(TavernChatCodec.FIELD_ROUND_ID, JsonPrimitive("r1"))
        put(TavernChatCodec.FIELD_TURN_KIND, JsonPrimitive(GroupChat.TURN_SPEAKER))
        put(TavernChatCodec.FIELD_MENTION_ROLE_IDS, JsonArray(emptyList()))
    }

    private fun groupConfig() = GroupConfig(
        roles = listOf(
            GroupRole(id = "a", name = "艾达", assistantId = ASSISTANT_A),
            GroupRole(id = "b", name = "鲍勃", assistantId = ASSISTANT_B),
        ),
        mode = GroupChat.MODE_PIPELINE,
        tokenBudgetPerRound = 4096,
        revision = 3,
    )

    private fun cards() = listOf(
        RoleCardMeta(roleId = "a", name = "艾达", assistantId = ASSISTANT_A, persona = "你是艾达。"),
        RoleCardMeta(roleId = "b", name = "鲍勃", assistantId = ASSISTANT_B),
    )

    /** 覆盖四个契约字段的四种组合：用户轮 + 普通发言 + 议长发言（带 @）。 */
    private fun nodes() = listOf(
        MessageNode.of(
            UIMessage.user("开始吧").copy(
                roundId = "r1",
                turnKind = GroupChat.TURN_USER,
            ),
        ),
        MessageNode.of(
            UIMessage.assistant("艾达发言").copy(
                roleId = "a",
                roundId = "r1",
                turnKind = GroupChat.TURN_SPEAKER,
            ),
        ),
        MessageNode.of(
            UIMessage.assistant("鲍勃收尾").copy(
                roleId = "b",
                roundId = "r1",
                turnKind = GroupChat.TURN_CHAIR,
                mentionRoleIds = listOf("a"),
            ),
        ),
    )

    private fun groupConversation(
        config: GroupConfig? = groupConfig(),
        nodes: List<MessageNode> = emptyList(),
        title: String = GROUP_NAME,
    ) = Conversation(
        id = Uuid.parse(CONVERSATION_ID),
        assistantId = Uuid.random(),
        title = title,
        messageNodes = nodes,
        type = GroupChat.TYPE_GROUP,
        groupConfig = config,
    )

    private companion object {
        const val CONVERSATION_ID = "11111111-1111-1111-1111-111111111111"
        const val USER_NAME = "阿泽"
        const val GROUP_NAME = "圆桌"
        const val ASSISTANT_A = "22222222-2222-2222-2222-222222222222"
        const val ASSISTANT_B = "33333333-3333-3333-3333-333333333333"
    }
}
