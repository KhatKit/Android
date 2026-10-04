package heizige.kk.khatkit.app.core.data.ai.tavern

import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.png.PngChunkType
import com.drew.metadata.png.PngDirectory
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalToolOption
import heizige.kk.khatkit.app.core.data.export.parseCharacterCardLorebook
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.InjectionPosition
import heizige.kk.khatkit.app.core.data.model.LorebookKeyLogic
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TavernCompatTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun `twenty spec cards round trip without field loss`() {
        specCards().forEachIndexed { index, card ->
            val exported = CharacterCardCodec.export(card)
            assertEquals("card $index", json.parseToJsonElement(card), json.parseToJsonElement(exported))
            val again = CharacterCardCodec.export(exported)
            assertEquals("card $index second pass", json.parseToJsonElement(card), json.parseToJsonElement(again))
        }
    }

    @Test
    fun `name override changes only the interpreted name`() {
        val card = specCards().first()
        val exported = CharacterCardCodec.export(card, nameOverride = "Renamed")
        val root = json.parseToJsonElement(exported).jsonObject
        assertEquals("Renamed", root["data"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("keep-me", root["extensions"]!!.jsonObject["vendor"]!!.jsonPrimitive.content)
    }

    @Test
    fun `png chara chunk round trips and is visible to metadata extractor`() {
        val card = specCards().first { it.contains("chara_card_v3") }
        val png = PngCharacterCard.embed(card)
        assertEquals(json.parseToJsonElement(card), json.parseToJsonElement(PngCharacterCard.readJson(png)))
        val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(png))
        val labels = metadata.getDirectoriesOfType(PngDirectory::class.java)
            .filter { it.pngChunkType == PngChunkType.tEXt }
            .map { it.getDescription(PngDirectory.TAG_TEXTUAL_DATA).orEmpty() }
        assertTrue(labels.toString(), labels.any { it.startsWith("chara:") })
        assertTrue(labels.toString(), labels.any { it.startsWith("ccv3:") })
    }

    @Test
    fun `imported card keeps tools and the original json`() {
        val card = specCards()[3]
        val assistant = CharacterCardCodec.importAssistant(card)
            .copy(localTools = listOf(LocalToolOption.TimeInfo, LocalToolOption.Clipboard))
        assertEquals(json.parseToJsonElement(card), json.parseToJsonElement(assistant.tavernCardJson!!))
        assertTrue(assistant.localTools.contains(LocalToolOption.Clipboard))
        assertTrue(assistant.systemPrompt.contains("You are roleplaying as"))
    }

    @Test
    fun `character book maps tavern trigger fields`() {
        val book = parseCharacterCardLorebook(
            """
            {
              "spec": "chara_card_v2",
              "data": {
                "character_book": {
                  "recursive_scanning": true,
                  "entries": {
                    "0": {
                      "keys": ["castle"],
                      "secondary_keys": ["dragon", "knight"],
                      "selective": true,
                      "selectiveLogic": 3,
                      "use_regex": true,
                      "probability": 40,
                      "position": 0,
                      "depth": 2,
                      "role": 2,
                      "exclude_recursion": true,
                      "prevent_recursion": true,
                      "content": "hidden",
                      "comment": "gate"
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val entry = book?.entries?.single()
        assertNotNull(entry)
        assertEquals(true, book?.recursiveScanning)
        assertEquals(LorebookKeyLogic.AND_ALL, entry?.keyLogic)
        assertEquals(true, entry?.selective)
        assertEquals(true, entry?.useRegex)
        assertEquals(40, entry?.probability)
        assertEquals(InjectionPosition.BEFORE_SYSTEM_PROMPT, entry?.position)
        assertEquals(2, entry?.injectDepth)
        assertEquals(MessageRole.ASSISTANT, entry?.role)
        assertEquals(false, entry?.includeInRecursion)
        assertEquals(true, entry?.preventRecursion)
        assertEquals("gate", entry?.name)
    }

    @Test
    fun `chat swipes map to message branches and unknown fields survive`() {
        val raw = """
            [
              {"user_name":"Ada","character_name":"Khat","spec":"st_chat_v1","vendor":"keep"},
              {
                "name":"Khat",
                "is_user":false,
                "mes":"Hello",
                "swipes":["Hello","Hi there"],
                "swipe_id":1,
                "extra":{"mood":"warm"}
              }
            ]
        """.trimIndent()
        val document = TavernChatCodec.import(raw)
        assertEquals("keep", document.header["vendor"]!!.jsonPrimitive.content)
        assertEquals(1, document.messages.single().node.selectIndex)
        assertEquals("Hi there", document.messages.single().node.currentMessage.toText())
        val exported = TavernChatCodec.export(document)
        val again = TavernChatCodec.import(exported)
        assertEquals("warm", again.messages.single().raw["extra"]!!.jsonObject["mood"]!!.jsonPrimitive.content)
        assertEquals(1, again.messages.single().node.selectIndex)
    }

    @Test
    fun `synthesized chat export is an array tavern can open`() {
        val exported = TavernChatCodec.exportNodes(
            nodes = listOf(
                MessageNode(messages = listOf(UIMessage.user("hi")), selectIndex = 0),
                MessageNode(
                    messages = listOf(UIMessage.assistant("one"), UIMessage.assistant("two")),
                    selectIndex = 1,
                ),
            ),
            userName = "Ada",
            characterName = "Khat",
        )
        val document = TavernChatCodec.import(exported)
        assertEquals("st_chat_v1", document.header["spec"]!!.jsonPrimitive.content)
        assertEquals("two", document.messages[1].node.currentMessage.toText())
        assertEquals(MessageRole.USER, document.messages[0].node.currentMessage.role)
    }

    @Test
    fun `instruct wrap order and unknown fields survive`() {
        val raw = """
            {"system_sequence":"[S]","system_suffix":"[/S]","input_sequence":"[U]","input_suffix":"[/U]",
             "output_sequence":"[A]","output_suffix":"[/A]","stop_sequence":"</s>","wrap":true,"names_behavior":"none"}
        """.trimIndent()
        val template = TavernInstruct.parse(raw)
        val rendered = TavernInstruct.apply(
            listOf(UIMessage.system("sys"), UIMessage.user("ask"), UIMessage.assistant("ans")),
            template,
        )
        assertEquals("[S]sys[/S][U]ask[/U][A]ans[/A]", rendered)
        val exported = json.parseToJsonElement(TavernInstruct.export(template)).jsonObject
        assertEquals("none", exported["names_behavior"]!!.jsonPrimitive.content)
        assertEquals("</s>", exported["stop_sequence"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sampler preset applies temperature and keeps unknown fields`() {
        val raw = """{"temperature":0.2,"top_p":0.8,"top_k":40,"repetition_penalty":1.1}"""
        val preset = TavernSamplerPreset.parse(raw)
        val assistant = TavernSamplerPreset.apply(CharacterCardCodec.importAssistant(specCards().first()), preset)
        assertEquals(0.2f, assistant.temperature)
        assertEquals(0.8f, assistant.topP)
        val exported = json.parseToJsonElement(TavernSamplerPreset.export(preset)).jsonObject
        assertEquals(1.1f, exported["repetition_penalty"]!!.jsonPrimitive.content.toFloat())
    }

    @Test
    fun `sillytavern random and roll syntax is accepted beside the local pipe form`() {
        val expanded = expandTavernMacros(
            "{{random:alpha,beta}} {{random::left::right}} {{roll:d6}} {{random:a|b}}",
            userName = "Ada",
            characterName = "Khat",
            randomChoice = { it.last() },
            roll = { _, _ -> 3 },
        )
        assertEquals("beta right 3 b", expanded)
    }

    @Test
    fun `jsonl chat is the sillytavern file form`() {
        val raw = """
            {"user_name":"Ada","character_name":"Khat","create_date":"2026-10-03@12h00m00s"}
            {"name":"Khat","is_user":false,"mes":"Hello","swipes":["Hello","Hi"],"swipe_id":1,"extra":{"mood":"warm"}}
        """.trimIndent()
        val document = TavernChatCodec.import(raw)
        assertEquals("Ada", document.header["user_name"]!!.jsonPrimitive.content)
        assertEquals("Hi", document.messages.single().node.currentMessage.toText())
        val again = TavernChatCodec.import(TavernChatCodec.exportJsonl(document))
        assertEquals("warm", again.messages.single().raw["extra"]!!.jsonObject["mood"]!!.jsonPrimitive.content)
    }

    @Test
    fun `character book reads sillytavern extension fields`() {
        val book = parseCharacterCardLorebook(
            """
            {"character_book":{"entries":[{
              "keys":["castle"],
              "content":"hidden",
              "insertion_order":3,
              "extensions":{"probability":15,"depth":6,"position":4,"exclude_recursion":true,"selectiveLogic":2}
            }]}}
            """.trimIndent(),
        )
        val entry = book?.entries?.single()
        assertEquals(15, entry?.probability)
        assertEquals(6, entry?.injectDepth)
        assertEquals(InjectionPosition.AT_DEPTH, entry?.position)
        assertEquals(false, entry?.includeInRecursion)
        assertEquals(LorebookKeyLogic.NOT_ANY, entry?.keyLogic)
    }

    @Test
    fun `alternate greetings are swipes on the first node`() {
        val assistant = CharacterCardCodec.importAssistant(specCards().first())
        val nodes = tavernSeedNodes(assistant)
        assertEquals(listOf("hello 0", "alt 0"), nodes.single().messages.map { it.toText() })
        assertEquals(0, nodes.single().selectIndex)
    }

    @Test
    fun `png read prefers ccv3 and keeps the original chara chunk`() {
        val card = """{"spec":"chara_card_v2","spec_version":"2.0","data":{"name":"Ada"}}"""
        val png = PngCharacterCard.embed(card)
        val chunks = PngCharacterCard.readText(png)
        assertEquals("chara_card_v2", json.parseToJsonElement(decodeChunk(chunks, "chara"))
            .jsonObject["spec"]!!.jsonPrimitive.content)
        assertEquals("chara_card_v3", json.parseToJsonElement(PngCharacterCard.readJson(png))
            .jsonObject["spec"]!!.jsonPrimitive.content)
    }

    @Test
    fun `macros cover the v1 list and keep unknown text`() {
        val expanded = expandTavernMacros(
            "{{user}} {{char}} {{random:alpha|beta}} {{roll:1d6}} {{lastMessage}} {{time}} {{nope}}",
            userName = "Ada",
            characterName = "Khat",
            lastMessage = "previous",
            time = { "2026-10-03 12:00" },
            randomChoice = { it.first() },
            roll = { _, _ -> 4 },
        )
        assertEquals("Ada Khat alpha 4 previous 2026-10-03 12:00 {{nope}}", expanded)
    }

    // ---------------- C1 群聊导出 / 导入 ----------------

    @Test
    fun `group export round trips three roles two rounds with contract fields intact`() {
        val restored = TavernChatCodec.importGroup(
            TavernChatCodec.exportGroup(
                nodes = groupNodes(),
                config = groupConfig(),
                cards = groupCards(),
                userName = "Ada",
                groupName = "三人组",
            )
        )
        assertNotNull(restored)
        // role_id / round_id / turn_kind / mention_role_ids 四件套逐条相等
        assertEquals(turnsOf(groupNodes()), turnsOfMessages(requireNotNull(restored).messages))
        // 消息文本与 swipe 分支也还在
        assertEquals(groupNodes().map { it.currentMessage.toText() }, requireNotNull(restored).messages.map { it.node.currentMessage.toText() })
        assertEquals(1, requireNotNull(restored).messages[3].node.selectIndex)
        assertEquals(listOf("轮一发言", "重掷的轮一发言"), requireNotNull(restored).messages[3].node.messages.map { it.toText() })
        // 群配置（含 extras 未知字段）与角色卡元数据原样回来
        assertEquals(groupConfig(), requireNotNull(restored).config)
        assertEquals(groupCards(), requireNotNull(restored).cards)
        assertEquals("Ada", requireNotNull(restored).userName)
        assertEquals("三人组", requireNotNull(restored).groupName)
        assertEquals(listOf("Alice", "Bob", "Cara"), requireNotNull(restored).characterNames)
    }

    @Test
    fun `group export keeps the tavern fields and the metadata header tavern strips`() {
        val exported = TavernChatCodec.exportGroup(
            nodes = groupNodes(),
            config = groupConfig(),
            cards = groupCards(),
            userName = "Ada",
            groupName = "三人组",
        )
        val array = json.parseToJsonElement(exported).jsonArray
        val header = array.first().jsonObject
        // SillyTavern group-chats.js:272 只在首行带 chat_metadata 时才把这行当表头丢掉
        assertEquals(true, header["chat_metadata"]!!.jsonObject["is_group"]!!.jsonPrimitive.booleanOrNull)
        assertEquals("st_chat_v1", header["spec"]!!.jsonPrimitive.content)
        assertEquals("Ada", header["user_name"]!!.jsonPrimitive.content)
        val payload = header[TavernChatCodec.GROUP_FIELD]!!.jsonObject
        assertEquals(GroupChat.QR_KIND, payload["kind"]!!.jsonPrimitive.content)
        assertEquals("三人组", payload["name"]!!.jsonPrimitive.content)
        assertEquals(GroupChat.SCHEMA_VERSION, payload["schema_version"]!!.jsonPrimitive.content.toInt())
        // 角色卡最小元数据：RoleCardMeta 那六个字段，逐个落在 payload.cards 上
        val card = payload["cards"]!!.jsonArray[1].jsonObject
        assertEquals("b", card["role_id"]!!.jsonPrimitive.content)
        assertEquals("asst-b", card["assistant_id"]!!.jsonPrimitive.content)
        assertEquals("card-b", card["card_id"]!!.jsonPrimitive.content)
        assertEquals("画师 B", card["persona"]!!.jsonPrimitive.content)
        assertEquals("file://avatar-b", card["avatar_ref"]!!.jsonPrimitive.content)
        // 酒馆认识的消息字段：角色靠 name 显示，归属靠 role_id
        val user = array[1].jsonObject
        assertEquals("Ada", user["name"]!!.jsonPrimitive.content)
        assertEquals(true, user["is_user"]!!.jsonPrimitive.booleanOrNull)
        assertEquals(false, user["is_system"]!!.jsonPrimitive.booleanOrNull)
        assertEquals("群聊导出", user["mes"]!!.jsonPrimitive.content)
        val speaker = array[2].jsonObject
        assertEquals("Alice", speaker["name"]!!.jsonPrimitive.content)
        assertEquals("a", speaker["role_id"]!!.jsonPrimitive.content)
        assertEquals(false, speaker["is_user"]!!.jsonPrimitive.booleanOrNull)
        assertEquals("r1", speaker["round_id"]!!.jsonPrimitive.content)
        assertEquals(GroupChat.TURN_SPEAKER, speaker["turn_kind"]!!.jsonPrimitive.content)
        assertEquals(
            emptyList<String>(),
            speaker[TavernChatCodec.FIELD_MENTION_ROLE_IDS]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("a"),
            array[3].jsonObject[TavernChatCodec.FIELD_MENTION_ROLE_IDS]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(listOf("轮一发言", "重掷的轮一发言"), array[4].jsonObject["swipes"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(1, array[4].jsonObject["swipe_id"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `group export jsonl keeps one object per line and imports back`() {
        val jsonl = TavernChatCodec.exportGroupJsonl(
            nodes = groupNodes(),
            config = groupConfig(),
            cards = groupCards(),
            userName = "Ada",
            groupName = "三人组",
        )
        val lines = jsonl.lines()
        assertEquals(groupNodes().size + 1, lines.size)
        assertEquals(true, json.parseToJsonElement(lines.first()).jsonObject["chat_metadata"] != null)
        val restored = TavernChatCodec.importGroup(jsonl)
        assertNotNull(restored)
        assertEquals(groupConfig(), requireNotNull(restored).config)
        assertEquals(turnsOf(groupNodes()), turnsOfMessages(requireNotNull(restored).messages))
        // 单聊导入路径照旧把首行当表头，群聊文件不会污染它
        val plain = TavernChatCodec.import(jsonl)
        assertEquals(groupNodes().size, plain.messages.size)
        // 不是群聊导出就返回 null，不猜
        assertNull(TavernChatCodec.importGroup("""{"user_name":"Ada"}"""))
        assertNull(TavernChatCodec.importGroup("not a chat"))
    }

    @Test
    fun `group export is byte identical across runs`() {
        val first = TavernChatCodec.exportGroup(
            nodes = groupNodes(),
            config = groupConfig(),
            cards = groupCards(),
            userName = "Ada",
            groupName = "三人组",
        )
        val second = TavernChatCodec.exportGroup(
            nodes = groupNodes(),
            config = groupConfig(),
            cards = groupCards(),
            userName = "Ada",
            groupName = "三人组",
        )
        assertEquals(first, second)
        val jsonl = TavernChatCodec.exportGroupJsonl(
            nodes = groupNodes(),
            config = groupConfig(),
            cards = groupCards(),
            userName = "Ada",
            groupName = "三人组",
        )
        assertEquals(
            jsonl,
            TavernChatCodec.exportGroupJsonl(
                nodes = groupNodes(),
                config = groupConfig(),
                cards = groupCards(),
                userName = "Ada",
                groupName = "三人组",
            ),
        )
    }

    @Test
    fun `group export carries no forbidden key and refuses a smuggled api key`() {
        val clean = TavernChatCodec.exportGroup(
            nodes = groupNodes(),
            config = groupConfig(),
            cards = groupCards(),
            userName = "Ada",
            groupName = "三人组",
        )
        assertEquals(emptySet<String>(), GroupChat.findForbiddenKeys(json.parseToJsonElement(clean)))
        val smuggled = groupConfig().copy(
            extras = buildJsonObject {
                put("vendor_note", "keep")
                put("api_key", "sk-should-never-leave")
            },
        )
        assertThrows(IllegalStateException::class.java) {
            TavernChatCodec.exportGroup(
                nodes = groupNodes(),
                config = smuggled,
                cards = groupCards(),
                userName = "Ada",
                groupName = "三人组",
            )
        }
        // 角色层的 extras 同样过闸门
        assertThrows(IllegalStateException::class.java) {
            TavernChatCodec.exportGroup(
                nodes = groupNodes(),
                config = groupConfig().copy(
                    roles = groupConfig().roles.map { role ->
                        if (role.id != "a") role else role.copy(
                            extras = buildJsonObject { put("memoryContent", "私有记忆") }
                        )
                    },
                ),
                cards = groupCards(),
                userName = "Ada",
                groupName = "三人组",
            )
        }
    }

    @Test
    fun `vote summary and error turns survive the group round trip`() {
        val nodes = listOf(
            MessageNode.of(
                UIMessage.user("投给 a").copy(
                    roundId = "r9",
                    turnKind = GroupChat.TURN_USER,
                )
            ),
            MessageNode.of(
                UIMessage.assistant("本轮投票结果：a").copy(
                    roleId = GroupChat.SUMMARY_ID,
                    roundId = "r9",
                    turnKind = GroupChat.TURN_VOTE_SUMMARY,
                )
            ),
            MessageNode.of(
                UIMessage.assistant("[Alice] 本轮生成失败：超时").copy(
                    roleId = "a",
                    roundId = "r9",
                    turnKind = GroupChat.TURN_ERROR,
                )
            ),
            MessageNode.of(
                UIMessage.assistant("[投票] 本轮未能得出结论：平票").copy(
                    roleId = GroupChat.SUMMARY_ID,
                    roundId = "r9",
                    turnKind = GroupChat.TURN_ERROR,
                )
            ),
        )
        val restored = TavernChatCodec.importGroup(
            TavernChatCodec.exportGroup(
                nodes = nodes,
                config = groupConfig(),
                cards = groupCards(),
                userName = "Ada",
                groupName = "三人组",
            )
        )
        assertEquals(turnsOf(nodes), turnsOfMessages(requireNotNull(restored).messages))
        assertEquals(GroupChat.SUMMARY_ID, requireNotNull(restored).messages[1].roleId)
        assertEquals(GroupChat.TURN_VOTE_SUMMARY, requireNotNull(restored).messages[1].turnKind)
        // 合成节点没有角色名，用与群聊页一致的显示名兜底
        assertEquals("多数决", requireNotNull(restored).messages[1].name)
        assertEquals("Alice", requireNotNull(restored).messages[2].name)
        assertEquals(GroupChat.TURN_ERROR, requireNotNull(restored).messages[3].turnKind)
        // 契约字段也写回了消息本身，调用方拿 node 就能直接用
        assertEquals(GroupChat.TURN_ERROR, requireNotNull(restored).messages[3].node.currentMessage.turnKind)
        assertEquals("r9", requireNotNull(restored).messages[3].node.currentMessage.roundId)
    }

    private fun turnsOf(nodes: List<MessageNode>): List<List<Any?>> = nodes.map { node ->
        val message = node.currentMessage
        listOf(message.roleId, message.roundId, message.turnKind, message.mentionRoleIds)
    }

    private fun turnsOfMessages(messages: List<TavernGroupMessage>): List<List<Any?>> = messages.map { message ->
        val node = message.node.currentMessage
        listOf(node.roleId, node.roundId, node.turnKind, node.mentionRoleIds)
    }

    private fun groupConfig() = GroupConfig(
        roles = listOf(
            GroupRole(
                id = "a",
                name = "Alice",
                assistantId = "asst-a",
                cardId = "card-a",
                extras = buildJsonObject {
                    put("tone", "cold")
                    put("taboos", JsonArray(listOf(JsonPrimitive("emoji"))))
                },
            ),
            GroupRole(id = "b", name = "Bob", assistantId = "asst-b", cardId = "card-b"),
            GroupRole(id = "c", name = "Cara", assistantId = "asst-c", chair = true),
        ),
        mode = GroupChat.MODE_VOTE,
        chairRoleId = "c",
        tokenBudgetPerRound = 400,
        revision = 3,
        voteCandidates = listOf("a", "b"),
        tiePolicy = GroupChat.TIE_CHAIR,
        extras = buildJsonObject {
            put("vendor_note", "未知字段要原样带回")
            put("nested", buildJsonObject { put("depth", 2) })
        },
    )

    private fun groupCards() = listOf(
        RoleCardMeta(roleId = "a", name = "Alice", assistantId = "asst-a", cardId = "card-a", persona = "画师 A"),
        RoleCardMeta(
            roleId = "b",
            name = "Bob",
            assistantId = "asst-b",
            cardId = "card-b",
            persona = "画师 B",
            avatarRef = "file://avatar-b",
        ),
        // cardId / avatarRef 都是 null：导出必须整个键省略，不能变成字符串 "null"
        RoleCardMeta(roleId = "c", name = "Cara", assistantId = "asst-c"),
    )

    /** 三个角色、两轮消息，含 @、议长、vote_summary、error 与一条重掷（多 swipe）分支。 */
    private fun groupNodes(): List<MessageNode> = listOf(
        MessageNode.of(
            UIMessage.user("群聊导出").copy(
                roundId = "r1",
                turnKind = GroupChat.TURN_USER,
                mentionRoleIds = emptyList(),
            )
        ),
        MessageNode.of(
            UIMessage.assistant("Alice 发言").copy(
                roleId = "a",
                roundId = "r1",
                turnKind = GroupChat.TURN_SPEAKER,
            )
        ),
        MessageNode.of(
            UIMessage.assistant("Bob 发言").copy(
                roleId = "b",
                roundId = "r1",
                turnKind = GroupChat.TURN_SPEAKER,
                mentionRoleIds = listOf("a"),
            )
        ),
        MessageNode(
            messages = listOf(
                UIMessage.assistant("轮一发言").copy(
                    roleId = "a",
                    roundId = "r1",
                    turnKind = GroupChat.TURN_SPEAKER,
                ),
                UIMessage.assistant("重掷的轮一发言").copy(
                    roleId = "a",
                    roundId = "r1",
                    turnKind = GroupChat.TURN_SPEAKER,
                ),
            ),
            selectIndex = 1,
        ),
        MessageNode.of(
            UIMessage.assistant("Cara 收尾").copy(
                roleId = "c",
                roundId = "r1",
                turnKind = GroupChat.TURN_CHAIR,
            )
        ),
        MessageNode.of(
            UIMessage.user("再来一轮").copy(
                roundId = "r2",
                turnKind = GroupChat.TURN_USER,
                mentionRoleIds = listOf("b", "c"),
            )
        ),
        MessageNode.of(
            UIMessage.assistant("本轮投票结果：a").copy(
                roleId = GroupChat.SUMMARY_ID,
                roundId = "r2",
                turnKind = GroupChat.TURN_VOTE_SUMMARY,
            )
        ),
    )

    private fun decodeChunk(chunks: List<Pair<String, String>>, keyword: String): String {
        val payload = chunks.first { it.first.equals(keyword, ignoreCase = true) }.second
        return String(java.util.Base64.getDecoder().decode(payload))
    }

    private fun specCards(): List<String> = List(20) { index ->
        val spec = if (index % 2 == 0) "chara_card_v2" else "chara_card_v3"
        val position = listOf("before_char", "after_char", "at_depth")[index % 3]
        """
        {
          "spec": "$spec",
          "spec_version": "${if (spec.endsWith("v3")) "3.0" else "2.0"}",
          "extensions": {"vendor":"keep-me","index":$index,"note":"超集字段 $index"},
          "data": {
            "name": "Card $index 纸片",
            "description": "desc $index",
            "personality": "calm",
            "scenario": "station {{user}}",
            "first_mes": "hello $index",
            "mes_example": "<START>",
            "creator_notes": "notes",
            "system_prompt": "stay in character",
            "post_history_instructions": "remember",
            "alternate_greetings": ["alt $index"],
            "tags": ["demo", "card-$index"],
            "creator": "khatkit",
            "character_version": "1.$index",
            "extensions": {"unused": true, "nested": {"id": $index}},
            "character_book": {
              "name": "book $index",
              "scan_depth": ${4 + index},
              "token_budget": 1024,
              "recursive_scanning": ${index % 2 == 0},
              "extensions": {"book": $index},
              "entries": [{
                "keys": ["key$index"],
                "secondary_keys": ["sec$index"],
                "content": "lore $index",
                "enabled": true,
                "insertion_order": $index,
                "case_sensitive": false,
                "selective": true,
                "selectiveLogic": ${index % 4},
                "constant": false,
                "position": "$position",
                "use_regex": false,
                "probability": ${50 + index},
                "depth": 2,
                "role": ${index % 3},
                "extensions": {"entry": "keep"}
              }]
            }
          }
        }
        """.trimIndent()
    }
}
