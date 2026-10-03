package heizige.kk.khatkit.app.core.data.ai.tavern

import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.png.PngChunkType
import com.drew.metadata.png.PngDirectory
import heizige.kk.khatkit.ai.core.MessageRole
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalToolOption
import heizige.kk.khatkit.app.core.data.export.parseCharacterCardLorebook
import heizige.kk.khatkit.app.core.data.model.InjectionPosition
import heizige.kk.khatkit.app.core.data.model.LorebookKeyLogic
import heizige.kk.khatkit.app.core.data.model.MessageNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
