package heizige.kk.khatkit.app.core.data.browser

/**
 * Converts a small HTML fragment into a bounded text snapshot suitable for an agent.
 * This is intentionally conservative: scripts, styles, comments and hidden metadata are removed.
 */
fun buildBrowserDomSnapshot(html: String, maxChars: Int = 20_000): String {
    require(maxChars > 0) { "maxChars must be positive" }
    val cleaned = html
        .replace(Regex("(?is)<!--.*?-->|<script\\b[^>]*>.*?</script>|<style\\b[^>]*>.*?</style>"), " ")
        .replace(Regex("(?is)<(br|p|div|li|h[1-6]|tr|section|article|button|label|input|textarea|select)\\b[^>]*>"), "\n")
        .replace(Regex("(?is)<[^>]+>"), " ")
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .lines()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("\n")
        .trim()
    val suffix = "\n[Snapshot truncated]"
    if (cleaned.toByteArray(Charsets.UTF_8).size <= maxChars) return cleaned
    val marker = suffix.takeWhileWithinUtf8Bytes(maxChars)
    val budget = (maxChars - marker.toByteArray(Charsets.UTF_8).size).coerceAtLeast(0)
    val truncated = cleaned
        .takeWhileWithinUtf8Bytes(budget)
        .trimEnd()
    return truncated + marker
}

private fun String.takeWhileWithinUtf8Bytes(maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    var bytes = 0
    var end = 0
    for (codePoint in codePoints()) {
        val charCount = Character.charCount(codePoint)
        val value = substring(end, end + charCount)
        val valueBytes = value.toByteArray(Charsets.UTF_8).size
        if (bytes + valueBytes > maxBytes) break
        bytes += valueBytes
        end += charCount
    }
    return substring(0, end)
}
