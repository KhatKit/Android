package heizige.kk.khatkit.localization

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeTextTest {
    @Test fun structuredArgumentsPreserveLegacyDiagnosticFormatting() {
        val text = RuntimeText(RuntimeText.Code.DEPENDENCY_PREPARING, listOf("imageToolbox", "1.0.0"))
        assertEquals(listOf("imageToolbox", "1.0.0"), text.arguments)
        assertEquals("正在准备依赖包：imageToolbox 1.0.0", text.diagnosticMessage())
    }

    @Test fun numberedIntegerArgumentsAreNotInterpolatedByKotlin() {
        assertEquals("服务器错误（503），请稍后重试",
            RuntimeText(RuntimeText.Code.HUB_SERVER_ERROR, listOf(503)).diagnosticMessage())
    }
}
