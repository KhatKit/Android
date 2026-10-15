package heizige.kk.khatkit.app.core.ui.components.khatkit

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §8 P0-6 宿主侧 schema 表单的纯 JVM 测试。
 *
 * 覆盖合同里可单测的四条：缺参才问、已提供字段不重复询问、参数并回同一次调用、
 * 表单失败/取消/全参数三种情形都不抛异常也不改变原参数。
 * 「同一次 runCardWithStatus 调用」由 `KhatKitTools.runCardWithStatus` 的结构保证
 * （`arguments` 只算一次、只有一处 `runManager.run(card, arguments)`），本文件不去复述。
 */
class KhatKitSchemaFormTest {

    private val schema = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put("url", buildJsonObject { put("type", "string"); put("title", "目标地址") })
                put("token", buildJsonObject { put("type", "string"); put("description", "访问令牌") })
                put("count", buildJsonObject { put("type", "integer"); put("minimum", 1); put("maximum", 10) })
                put("dry_run", buildJsonObject { put("type", "boolean") })
                put("mode", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("fast")); add(kotlinx.serialization.json.JsonPrimitive("thorough")) }) })
            }
        )
        put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("url")); add(kotlinx.serialization.json.JsonPrimitive("token")) })
    }

    private val declaredUi = mapOf("token" to buildJsonObject { put("source", "ui"); put("widget", "input") })

    @Test
    fun `缺必填 UI 字段时生成表单且只问缺的字段`() {
        val plan = KhatKitSchemaFormSpec.plan(
            name = "image_fetch",
            parameters = schema,
            ui = declaredUi,
            provided = mapOf("url" to "https://example.com/a.png"),
        )

        assertTrue(plan.shouldAsk)
        assertEquals(listOf("token"), plan.fields.map { it.id })
        assertEquals(listOf("token"), plan.missingRequired)
        // 已提供的 url 只作为展示行，不出现同 id 的输入条目
        assertEquals(listOf("url"), plan.provided.map { it.id })
        assertFalse(plan.items.any { it["id"] == "url" && it[KhatKitSchemaFormSpec.PROVIDED] != true })
        assertTrue(plan.items.all { it[KhatKitSchemaFormSpec.MARKER] == true })
        assertEquals(1, KhatKitSchemaFormSpec.requiredCount(plan.items))
    }

    @Test
    fun `AI 全参数调用不弹表单`() {
        val plan = KhatKitSchemaFormSpec.plan(
            name = "image_fetch",
            parameters = schema,
            ui = declaredUi,
            provided = mapOf("url" to "https://example.com/a.png", "token" to "sk-1"),
        )
        assertFalse(plan.shouldAsk)

        var asked = 0
        val merged = runBlocking {
            fillSchemaArgs(plan, mapOf("url" to "u", "token" to "t")) { _, _ ->
                asked++
                mapOf("token" to "不该发生")
            }
        }
        assertEquals(0, asked) // 一次 UI 请求都没发
        assertEquals(mapOf("url" to "u", "token" to "t"), merged)
    }

    @Test
    fun `缺参时表单值并回同一次调用的参数表`() {
        val plan = KhatKitSchemaFormSpec.plan(
            name = "image_fetch",
            parameters = schema,
            uiSchema = buildJsonObject { put("token", buildJsonObject { put("label", "访问令牌") }) },
            ui = declaredUi,
            provided = mapOf("url" to "https://example.com/a.png"),
        )
        val args = mapOf("url" to "https://example.com/a.png", "__khatkit_trigger" to "AI")

        val merged = runBlocking {
            fillSchemaArgs(plan, args) { items, title ->
                assertEquals("image_fetch", title)
                assertTrue(items.any { it["id"] == "token" })
                mapOf("token" to "sk-live")
            }
        }

        assertEquals("https://example.com/a.png", merged["url"])
        assertEquals("sk-live", merged["token"])
        assertEquals("AI", merged["__khatkit_trigger"]) // 内部参数不被表单结果覆盖/丢失
        // 回传 null（用户取消）时保持原参数
        assertEquals(args, runBlocking { fillSchemaArgs(plan, args) { _, _ -> null } })
    }

    @Test
    fun `表单渲染失败降级为原参数且不抛异常`() {
        val plan = KhatKitSchemaFormSpec.plan(
            name = "image_fetch",
            parameters = schema,
            ui = declaredUi,
            provided = emptyMap(),
        )
        val args = mapOf("other" to 1)

        val merged = runBlocking {
            fillSchemaArgs(plan, args) { _, _ -> throw IllegalStateException("ui.form: unknown legacy type") }
        }

        assertEquals(args, merged)
    }

    @Test
    fun `schema 缺失或畸形时退化为不问`() {
        val args = mapOf("a" to 1)
        val noSchema = KhatKitSchemaFormSpec.plan(name = "c", parameters = JsonObject(emptyMap()), provided = args)
        assertFalse(noSchema.shouldAsk)
        assertEquals(args, runBlocking { fillSchemaArgs(noSchema, args) { _, _ -> error("不应被调用") } })

        // properties 不是对象 / required 不是数组：只按 manifest.ui 的 source=ui 问
        val broken = buildJsonObject { put("properties", "oops"); put("required", "oops") }
        val malformed = KhatKitSchemaFormSpec.plan(
            name = "c",
            parameters = broken,
            ui = mapOf("key" to buildJsonObject { put("source", "ui") }),
            provided = args,
        )
        assertTrue(malformed.shouldAsk)
        assertEquals(listOf("key"), malformed.fields.map { it.id })
        assertEquals("input", malformed.fields.first().widget)
        assertFalse(malformed.fields.first().required)
    }

    @Test
    fun `控件按 schema 与 ui 提示推断且收敛到宿主白名单`() {
        val plan = KhatKitSchemaFormSpec.plan(
            name = "c",
            parameters = schema,
            ui = mapOf(
                "dry_run" to buildJsonObject { put("source", "ui") },
                "count" to buildJsonObject { put("source", "ui"); put("widget", "checkbox") },
                "mode" to buildJsonObject { put("source", "ui") },
                "url" to buildJsonObject { put("source", "ui"); put("widget", "unknown_widget") },
                "token" to buildJsonObject { put("source", "ui") },
            ),
        )
        val byId = plan.fields.associateBy { it.id }

        assertEquals("switch", byId.getValue("dry_run").widget)
        assertEquals("switch", byId.getValue("count").widget) // checkbox 归一成 switch（LEGACY 白名单里没有 checkbox）
        assertEquals("select", byId.getValue("mode").widget)
        assertEquals(listOf("fast", "thorough"), byId.getValue("mode").options)
        assertEquals("input", byId.getValue("url").widget) // 未知 widget → 退回类型推断（string → input）
        assertEquals("input", byId.getValue("token").widget)
        // 必填项 url/token 也被问，且 required 标记正确
        assertTrue(byId.getValue("url").required)
        assertEquals(2, KhatKitSchemaFormSpec.requiredCount(plan.items))
    }

    @Test
    fun `脚本自弹的旧表单不会被识别成宿主 schema 表单`() {
        val legacy = listOf<Map<String, Any?>>(
            mapOf("id" to "url", "type" to "input", "label" to "URL"),
        )
        assertFalse(KhatKitSchemaFormSpec.isSchemaDriven(legacy))
        assertFalse(KhatKitSchemaFormSpec.isProvidedRow(legacy.first()))
        assertEquals(0, KhatKitSchemaFormSpec.requiredCount(legacy))

        val host = KhatKitSchemaFormSpec.plan(
            name = "c",
            parameters = schema,
            ui = declaredUi,
            provided = emptyMap(),
        )
        assertTrue(KhatKitSchemaFormSpec.isSchemaDriven(host.items))
        assertTrue(host.items.any { KhatKitSchemaFormSpec.isProvidedRow(it) || it["id"] != null })
    }

    @Test
    fun `已提供判定覆盖空白串与空集合`() {
        val plan = KhatKitSchemaFormSpec.plan(
            name = "c",
            parameters = schema,
            ui = declaredUi,
            provided = mapOf("url" to "   ", "token" to "sk-2"),
        )
        // 空白串按缺参处理；token 已提供 → 只剩 url 要问，展示行只剩 token
        assertEquals(listOf("url"), plan.fields.map { it.id })
        assertEquals(listOf("token"), plan.provided.map { it.id })
    }
}
