package heizige.kk.khatkit.card

/**
 * Static permission audit for cards and ToolPkgs.
 * Risk score is 0–100; higher means more dangerous.
 * Rules match the S3 v1 list. This is a local scanner, not a copy of an upstream auditor.
 */
object AuditReport {
    const val RULES_VERSION = "1.0.0"

    private val hostPattern = Regex("""https?://([A-Za-z0-9.-]+)""")
    private val outsidePath = Regex("""(?i)(?:/sdcard|/storage|/data/|file://|\\.\\./)""")
    private val reflection = Regex("""Class\.forName|DexClassLoader|java\.lang\.reflect|loadClass\(""")
    private val deviceControl = Regex("""(?i)accessibility|device_act|a11y\.|shizuku|root\.""")
    private val sensitiveRead = Regex("""(?i)clipboard|logcat|Runtime\.getRuntime\(\)\.exec\(\s*"logcat""")

    fun scan(
        script: String,
        networkAllow: List<String> = emptyList(),
        permissions: List<String> = emptyList(),
        hooks: List<String> = emptyList(),
    ): Report {
        val allowed = networkAllow.map { it.lowercase() }.toSet()
        val findings = mutableListOf<Finding>()
        hostPattern.findAll(script).map { it.groupValues[1].lowercase() }.distinct().forEach { host ->
            if (allowed.none { host == it || host.endsWith(".$it") }) {
                findings += Finding("NET_UNDECLARED", Severity.HIGH, "host $host is outside network.allow")
            }
        }
        if (outsidePath.containsMatchIn(script)) {
            findings += Finding("FS_OUTSIDE_WORKSPACE", Severity.HIGH, "path escapes the workspace")
        }
        if (reflection.containsMatchIn(script)) {
            findings += Finding("REFLECTION", Severity.HIGH, "dynamic class loading")
        }
        if (deviceControl.containsMatchIn(script)) {
            findings += Finding("DEVICE_CONTROL", Severity.MEDIUM, "accessibility or device-control bridge")
        }
        if (sensitiveRead.containsMatchIn(script)) {
            findings += Finding("SENSITIVE_READ", Severity.MEDIUM, "clipboard or log read")
        }
        val score = findings.sumOf { it.severity.weight }.coerceIn(0, 100)
        return Report(
            auditRulesVersion = RULES_VERSION,
            score = score,
            level = level(score),
            findings = findings,
            permissions = permissions,
            networkDomains = networkAllow,
            hooks = hooks,
        )
    }

    fun level(score: Int): Level = when {
        score >= 70 -> Level.HIGH
        score >= 30 -> Level.MEDIUM
        else -> Level.LOW
    }

    data class Report(
        val auditRulesVersion: String,
        val score: Int,
        val level: Level,
        val findings: List<Finding>,
        val permissions: List<String>,
        val networkDomains: List<String>,
        val hooks: List<String>,
    )

    data class Finding(val ruleId: String, val severity: Severity, val detail: String)

    enum class Severity(val weight: Int) { LOW(10), MEDIUM(20), HIGH(40) }

    enum class Level { LOW, MEDIUM, HIGH }
}
