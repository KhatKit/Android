package heizige.kk.khatkit.hub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/api/items`（§7.1）与旧卡片索引的解析：新字段带默认值可缺省，
 * 缺字段 / 旧响应 / 未知字段都不抛异常。
 */
class HubItemsParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun items(text: String) = json.decodeFromString(HubItemList.serializer(), text).items

    private fun cards(text: String) = json.decodeFromString(CardIndex.serializer(), text).cards

    private val fullItem = """
        {
          "items": [
            {
              "id": "it_1",
              "name": "deep-research",
              "kind": "skill",
              "slug": "deep-research",
              "version": "1.2.0",
              "display_name": {"zh-CN": "深度研究", "en": "Deep Research"},
              "description": {"zh-CN": "联网检索和引用整理", "en": "Research and citation workflow"},
              "author": "khatkit",
              "license": "MIT",
              "changelog": "init",
              "sha256": "abc",
              "icon_url": "https://cdn.example.com/i/deep-research.png",
              "icon_sha256": "def",
              "icon_mime": "image/png",
              "icon_alt": {"zh-CN": "深度研究图标", "en": "Deep research icon"},
              "tags": ["research", "web"],
              "config_schema": {"type": "object", "properties": {"key": {"type": "string"}}},
              "ui_schema": {"order": ["query"], "widget": "form"},
              "function": {"input": {"query": {"type": "string"}}},
              "source": {
                "source_type": "github",
                "source_url": "https://github.com/khatkit/cards",
                "source_owner": "khatkit",
                "source_repo": "cards",
                "source_ref": "main",
                "source_path": "skills/deep-research",
                "source_commit": "0f1e2d3",
                "auto_publish": true,
                "sync_status": "ok"
              },
              "compatibility": {"min_app_version": "2.0.0", "abi": ["arm64-v8a"]},
              "min_app_version": "2.0.0",
              "api_version": "1",
              "status": "approved",
              "created_at": "2026-01-01T00:00:00Z",
              "updated_at": "2026-01-02T00:00:00Z",
              "upvotes": 3,
              "score": 7,
              "preferred": true,
              "pricing": {"price": 0},
              "requires": [{"name": "net"}],
              "files": ["SKILL.md"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `full item parses every new field`() {
        val item = items(fullItem).single()
        assertEquals("deep-research", item.name)
        assertEquals("skill", item.kind)
        assertEquals("deep-research", item.slug)
        assertEquals("1.2.0", item.version)
        assertEquals("深度研究", item.title(listOf("zh-CN", "zh")))
        assertEquals("Deep Research", item.title(listOf("de")))
        assertEquals("联网检索和引用整理", item.descriptionText(listOf("zh-CN", "zh")))
        assertEquals("Research and citation workflow", item.descriptionText(listOf("de")))
        assertEquals("https://cdn.example.com/i/deep-research.png", item.iconUrl)
        assertEquals("def", item.iconSha256)
        assertEquals("image/png", item.iconMime)
        assertEquals("深度研究图标", item.iconAltText(listOf("zh-CN")))
        assertEquals(listOf("research", "web"), item.tags.resolve(listOf("zh-CN")))
        assertEquals("object", item.configSchema["type"]?.jsonPrimitive?.content)
        assertEquals("query", item.uiSchema["order"]?.jsonArray?.first()?.jsonPrimitive?.content)
        assertEquals(
            "string",
            item.function["input"]?.jsonObject?.get("query")?.jsonObject?.get("type")?.jsonPrimitive?.content,
        )
        assertEquals("github", item.source?.type)
        assertEquals("cards", item.source?.repo)
        assertEquals("khatkit", item.source?.owner)
        assertEquals("main", item.source?.ref)
        assertEquals("0f1e2d3", item.source?.commit)
        assertTrue(item.source?.autoPublish == true)
        assertEquals("2.0.0", item.compatibility["min_app_version"]?.jsonPrimitive?.content)
        assertEquals("2.0.0", item.minAppVersion)
        assertEquals("approved", item.status)
        assertEquals(3, item.upvotes)
        assertTrue(item.preferred)
        assertEquals("skill:deep-research", item.dedupeKey)
    }

    /** 旧服务端（无新字段、`description` 仍是纯字符串）：老响应零回归。 */
    @Test
    fun `legacy item without new fields still parses`() {
        val legacy = """
            {
              "items": [
                {
                  "id": "c1",
                  "name": "legacy-card",
                  "kind": "card",
                  "version": "1.0.0",
                  "display_name": "Legacy Card",
                  "description": "旧版卡片简介",
                  "author": "someone",
                  "status": "approved",
                  "install_state": "installed"
                }
              ]
            }
        """.trimIndent()
        val item = items(legacy).single()
        assertEquals("Legacy Card", item.title())
        assertEquals("旧版卡片简介", item.descriptionText())
        assertEquals("", item.slug)
        assertEquals("", item.iconUrl)
        assertEquals("", item.iconSha256)
        assertEquals("", item.iconMime)
        assertEquals("", item.iconAltText())
        assertTrue(item.configSchema.isEmpty())
        assertTrue(item.uiSchema.isEmpty())
        assertTrue(item.function.isEmpty())
        assertTrue(item.compatibility.isEmpty())
        assertNull(item.source)
        assertTrue(item.tags.isEmpty)
        // 没有 slug 时去重键退回 name
        assertEquals("card:legacy-card", item.dedupeKey)
    }

    /** 全缺字段（`{}`）：不抛异常，全部走默认值。 */
    @Test
    fun `item with no fields at all parses to defaults`() {
        val item = items("""{"items":[{}]}""").single()
        assertEquals("", item.name)
        assertEquals("", item.title())
        assertTrue(item.displayName.isEmpty)
        assertTrue(item.description.isEmpty)
        assertTrue(item.tags.isEmpty)
        assertEquals(":", item.dedupeKey)
        assertTrue(items("{}").isEmpty())
    }

    /** 多语言 `tags`（`List<String> | LocaleMap`）两种形态都收。 */
    @Test
    fun `tags accept array and locale map shapes`() {
        val mapped = items("""{"items":[{"name":"x","tags":{"zh-CN":"研究, 联网","en":"research, web"}}]}""")
            .single()
        assertEquals(listOf("研究", "联网"), mapped.tags.resolve(listOf("zh-CN")))
        assertEquals(listOf("research", "web"), mapped.tags.resolve(listOf("de")))

        val array = items("""{"items":[{"name":"x","tags":["a","b"]}]}""").single()
        assertEquals(listOf("a", "b"), array.tags.resolve(listOf("zh-CN")))

        val scalar = items("""{"items":[{"name":"x","tags":"a; b"}]}""").single()
        assertEquals(listOf("a", "b"), scalar.tags.resolve(listOf("zh-CN")))
    }

    /** 旧卡片索引：`tags` 仍是 `{domain, action, scene}`，新字段缺省为空。 */
    @Test
    fun `legacy card index entry keeps old shape and defaults`() {
        val index = """
            {
              "updatedAt": "2026-01-01",
              "cards": [
                {
                  "name": "card-a",
                  "version": "1.0.0",
                  "description": "旧描述",
                  "author": "x",
                  "tags": {"domain": "text", "action": "read"},
                  "url": "card-a.zip",
                  "hash": "h",
                  "sizeBytes": 1024,
                  "downloads": 5
                }
              ]
            }
        """.trimIndent()
        val entry = cards(index).single()
        assertEquals("card-a", entry.name)
        assertEquals("旧描述", entry.description)
        assertEquals("text", entry.tags?.domain)
        assertEquals("read", entry.tags?.action)
        assertEquals(1024L, entry.sizeBytes)
        assertEquals("", entry.slug)
        assertTrue(entry.displayName.isEmpty)
        assertTrue(entry.iconAlt.isEmpty)
        assertTrue(entry.configSchema.isEmpty())
        assertTrue(entry.uiSchema.isEmpty())
        assertTrue(entry.function.isEmpty())
        assertTrue(entry.compatibility.isEmpty())
        assertNull(entry.source)
    }

    /** 服务端把 `description` 改成语言映射后，旧卡片索引模型也不会整条解析失败。 */
    @Test
    fun `card index entry tolerates locale map description`() {
        val entry = cards(
            """{"cards":[{"name":"card-b","version":"2.0.0","description":{"zh-CN":"新描述"},"url":"b.zip"}]}""",
        ).single()
        assertEquals("新描述", entry.description)
        assertFalse(entry.description.isBlank())
    }
}
