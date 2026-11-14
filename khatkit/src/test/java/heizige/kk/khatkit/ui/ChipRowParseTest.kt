package heizige.kk.khatkit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ChipRow / Dropdown 两个新节点的解析正例与反例（见 docs/card-dsl-chip-and-dropdown.md）。 */
class ChipRowParseTest {
    private fun node(kind: String, props: Map<String, Any?>, children: List<Map<String, Any?>> = emptyList()): Map<String, Any?> =
        mapOf("__ui" to kind, "props" to props, "children" to children)

    @Test fun chipRowSingleAndMultiParse() {
        val single = node("ChipRow", mapOf(
            "id" to "metrics",
            "options" to listOf("汉字", "单词", "行数"),
            "icons" to listOf("text_fields", "subject", "notes"),
            "value" to "汉字",
            "action" to "metrics_changed",
        ))
        assertEquals("ChipRow", UiNodeParser.parse(single).type)

        val multi = node("ChipRow", mapOf(
            "id" to "metrics",
            "options" to listOf("汉字", "单词", "行数"),
            "value" to listOf("汉字", "行数"),
            "multiple" to true,
        ))
        assertTrue(UiNodeParser.parse(multi).props["multiple"] == true)
    }

    @Test fun dropdownParses() {
        val dropdown = node("Dropdown", mapOf(
            "id" to "sample",
            "label" to "示例文本",
            "options" to listOf("不用示例", "中文示例"),
            "value" to "不用示例",
            "action" to "sample_changed",
        ))
        assertEquals("Dropdown", UiNodeParser.parse(dropdown).type)
    }

    @Test fun chipRowRejectsOutOfRangeValue() {
        val bad = node("ChipRow", mapOf(
            "id" to "m",
            "options" to listOf("a", "b"),
            "value" to "c",
        ))
        assertTrue("value 不在 options 里应当报错", runCatching { UiNodeParser.parse(bad) }.isFailure)
    }
}
