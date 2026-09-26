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
}
