package heizige.kk.khatkit.app.core.data.browser

/**
 * Converts a small HTML fragment into a bounded text snapshot suitable for an agent.
 * This is intentionally conservative: scripts, styles, comments and hidden metadata are removed.
 */
fun buildBrowserDomSnapshot(html: String, maxChars: Int = 20_000): String {
    require(maxChars > 0) { "maxChars must be positive" }
    val cleaned = html
        .replace(Regex("(?is)<!--.*?-->|<script\\b[^>]*>.*?</script>|<style\\b[^>]*>.*?</style>"), " ")
        .replace(Regex("(?is)<(br|p|div|li|h[1-6]|tr|section|article|button|label|input|textarea|select)\\b[^>]*>"), "\\n")
        .replace(Regex("(?is)<[^>]+>"), " ")
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex("\\n{3,}"), "\\n\\n")
        .lines()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("\\n")
        .trim()
    return if (cleaned.length <= maxChars) cleaned else cleaned.take(maxChars) + "\\n[Snapshot truncated]"
}