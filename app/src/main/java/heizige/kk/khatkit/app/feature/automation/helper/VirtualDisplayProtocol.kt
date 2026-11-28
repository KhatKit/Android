package heizige.kk.khatkit.app.feature.automation.helper

/** Pure command protocol shared by virtual-display orchestration and JVM tests. */
internal object VirtualDisplayProtocol {
    const val WIDTH = 720
    const val HEIGHT = 1280
    const val DENSITY_DPI = 240
    private val exitCode = Regex("\\[exit\\s+(-?\\d+)\\]", RegexOption.IGNORE_CASE)
    private val checkedExitCode = Regex("(?m)^\\[khatkit-display-exit (-?\\d+)\\]\\s*$")
    private val shellFailure = Regex(
        "(?im)^\\s*(?:\\[(?:error|timeout)\\]|unknown command\\b|error:|exception occurred|securityexception\\b|permission denial\\b|permission denied\\b|cmd:|/system/bin/sh:)",
    )
    private val displayIdPatterns = listOf(
        Regex("(?i)created\\s+virtual\\s+display(?:\\s+with)?\\s+(?:id\\s*[:=]?\\s*)?(\\d+)"),
        Regex("(?i)virtual\\s+display\\s*\\(\\s*(\\d+)\\s*\\)"),
        Regex("(?i)display\\s+id\\s*[:=]\\s*(\\d+)"),
    )

    fun createCommand(name: String): String {
        require(name.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "非法虚拟屏幕名称" }
        return "cmd display create-virtual-display --own-content-only --size \"${WIDTH}x${HEIGHT}\" --density $DENSITY_DPI $name"
    }

    fun destroyCommand(displayId: Int): String {
        require(displayId > 0) { "虚拟屏幕 ID 必须大于 0" }
        return "cmd display destroy-virtual-display $displayId"
    }

    /** RootBridge has no success marker; capture the command's exit code before printf replaces it. */
    fun checkedCommand(command: String): String =
        "$command; _khatkit_display_exit=\$?; printf '\\n[khatkit-display-exit %s]\\n' \"\$_khatkit_display_exit\""

    fun supportsVirtualDisplayCommands(help: String): Boolean =
        isSuccessful(help) &&
            Regex("(?m)^\\s*create-virtual-display(?:\\s|$)").containsMatchIn(help) &&
            Regex("(?m)^\\s*destroy-virtual-display(?:\\s|$)").containsMatchIn(help)

    fun parseCreatedDisplayId(output: String): Int? {
        if (!isSuccessful(output)) return null
        val ids = displayIdPatterns.flatMap { pattern ->
            pattern.findAll(output).map { it.groupValues[1] }.toList()
        }.distinct()
        return ids.singleOrNull()?.toIntOrNull()?.takeIf { it > 0 }
    }

    fun isSuccessful(output: String): Boolean {
        if (shellFailure.containsMatchIn(output)) return false
        val bridgeCodes = exitCode.findAll(output).map { it.groupValues[1].toIntOrNull() }.toList()
        val checkedCodes = checkedExitCode.findAll(output).map { it.groupValues[1].toIntOrNull() }.toList()
        if (bridgeCodes.size > 1 || bridgeCodes.any { it != 0 } || checkedCodes.any { it != 0 }) return false
        return checkedCodes.size == 1 &&
            checkedExitCode.find(output)?.range?.last == output.lastIndex
    }
}
