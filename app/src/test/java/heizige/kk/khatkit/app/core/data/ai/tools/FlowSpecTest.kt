package heizige.kk.khatkit.app.core.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流（Flow）规格解析与占位符解析的纯 JVM 测试。
 * 占位符示例统一用 `${'$'}{...}` 转义，避免测试源码里被 Kotlin 字符串模板吃掉。
 */
class FlowSpecTest {

    private val steps = listOf(
        mapOf(
            "screenshot" to "/tmp/a.png",
            "outputs" to listOf(
                mapOf(
                    "path" to "/tmp/cut.png",
                    "meta" to mapOf("w" to 100),
                )
            ),
        ),
    )

    @Test
    fun `parseFlowSpec parses steps args and flags`() {
        val spec = parseFlowSpec(
            """
            [
              {"card":"device_screen","args":{"include_ocr":false}},
              {"card":"image_ocr","args":{"image":"${'$'}{steps[0].result.screenshot}"},"continue_on_error":true}
            ]
            """.trimIndent(),
            stopOnError = false,
        )

        assertNotNull(spec)
        assertEquals(2, spec!!.steps.size)
        assertEquals("device_screen", spec.steps[0].card)
        assertEquals(false, spec.steps[0].args["include_ocr"])
        assertFalse(spec.steps[0].continueOnError)
        assertEquals("image_ocr", spec.steps[1].card)
        assertEquals("${'$'}{steps[0].result.screenshot}", spec.steps[1].args["image"])
        assertTrue(spec.steps[1].continueOnError)
        assertFalse(spec.stopOnError)
    }

    @Test
    fun `parseFlowSpec applies defaults`() {
        val spec = parseFlowSpec("""[{"card":"a11y_screen_dump"}]""")

        assertNotNull(spec)
        assertTrue(spec!!.stopOnError)
        assertEquals(emptyMap<String, Any?>(), spec.steps.single().args)
        assertFalse(spec.steps.single().continueOnError)
    }

    @Test
    fun `parseFlowSpec rejects invalid json and non array`() {
        assertNull(parseFlowSpec("not json"))
        assertNull(parseFlowSpec("""{"card":"a"}"""))
        assertEquals(emptyList<FlowStepSpec>(), parseFlowSpec("""[{"args":{}},42]""")?.steps)
    }

    @Test
    fun `whole placeholder keeps original type`() {
        assertEquals("/tmp/a.png", resolveFlowValue("${'$'}{steps[0].screenshot}", steps))
        assertEquals("/tmp/cut.png", resolveFlowValue("${'$'}{steps[0].outputs[0].path}", steps))
        assertEquals("/tmp/cut.png", resolveFlowValue("${'$'}{steps[0].outputs.0.path}", steps))
        assertEquals(mapOf("w" to 100), resolveFlowValue("${'$'}{steps[0].outputs[0].meta}", steps))
    }

    @Test
    fun `result prefix is optional alias of step root`() {
        assertEquals("/tmp/a.png", resolveFlowValue("${'$'}{steps[0].result.screenshot}", steps))
        assertEquals("/tmp/cut.png", resolveFlowValue("${'$'}{steps[0].result.outputs[0].path}", steps))
    }

    @Test
    fun `embedded placeholder renders maps as json and missing as empty`() {
        assertEquals("图片：/tmp/cut.png", resolveFlowValue("图片：${'$'}{steps[0].outputs[0].path}", steps))
        assertEquals("""meta={"w":100}""", resolveFlowValue("""meta=${'$'}{steps[0].outputs[0].meta}""", steps))
        assertNull(resolveFlowValue("${'$'}{steps[3].path}", steps))
        assertEquals("前一张：", resolveFlowValue("前一张：${'$'}{steps[3].path}", steps))
    }

    @Test
    fun `resolveFlowArgs walks nested maps and lists`() {
        val resolved = resolveFlowArgs(
            mapOf(
                "image" to "${'$'}{steps[0].outputs[0].path}",
                "texts" to listOf("${'$'}{steps[0].screenshot}", "固定"),
                "options" to mapOf("note" to "前一张 ${'$'}{steps[0].screenshot}"),
            ),
            steps,
        )

        assertEquals("/tmp/cut.png", resolved["image"])
        assertEquals(listOf("/tmp/a.png", "固定"), resolved["texts"])
        assertEquals("前一张 /tmp/a.png", (resolved["options"] as Map<*, *>)["note"])
    }

    @Test
    fun `renderFlowValue serializes non strings for embedding`() {
        assertEquals("""{"w":100}""", renderFlowValue(mapOf("w" to 100)))
        assertEquals("[1,2]", renderFlowValue(listOf(1, 2)))
        assertEquals("abc", renderFlowValue("abc"))
        assertEquals("", renderFlowValue(null))
    }

    @Test
    fun `flow placeholder pattern is android icu compatible`() {
        // Android 的 ICU 正则把未转义的尾部 `}` 当语法错误，JVM 却接受它；
        // 旧写法导致 KhatKitToolsKt 静态初始化抛 PatternSyntaxException，
        // 之后整个文件门面 NoClassDefFoundError，所有消息生成失败。
        val pattern = FLOW_PLACEHOLDER.pattern
        assertTrue(
            "closing brace must be escaped for ICU: $pattern",
            pattern.endsWith("\\}") || pattern.endsWith("[}]"),
        )
    }

    @Test(timeout = 5_000)
    fun `cyclic structures terminate with depth placeholder`() {
        val cyclicMap = mutableMapOf<String, Any?>()
        cyclicMap["self"] = cyclicMap
        val cyclicList = mutableListOf<Any?>()
        cyclicList.add(cyclicList)

        var current: Any? = resolveFlowValue(cyclicMap, emptyList())
        var guard = 0
        while (current is Map<*, *> && guard < 100) {
            current = current["self"]
            guard++
        }
        assertEquals("[嵌套过深]", current)

        var currentList: Any? = resolveFlowValue(cyclicList, emptyList())
        var listGuard = 0
        while (currentList is List<*> && listGuard < 100) {
            currentList = currentList.firstOrNull()
            listGuard++
        }
        assertEquals("[嵌套过深]", currentList)

        assertTrue(renderFlowValue(cyclicMap).contains("嵌套过深"))
        assertTrue(renderFlowValue(cyclicList).contains("嵌套过深"))
    }

    @Test(timeout = 5_000)
    fun `resolved values are not re-resolved`() {
        // 替换结果是「长得像占位符」的文本时也只做单趟解析，不会自引用死循环。
        val selfReferential = listOf(mapOf("path" to "${'$'}{steps[8].path}"))

        assertEquals("${'$'}{steps[8].path}", resolveFlowValue("${'$'}{steps[0].path}", selfReferential))
        assertEquals(
            "前一张：${'$'}{steps[8].path}",
            resolveFlowValue("前一张：${'$'}{steps[0].path}", selfReferential),
        )
    }
}
