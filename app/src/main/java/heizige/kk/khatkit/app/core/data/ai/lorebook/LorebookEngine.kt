package heizige.kk.khatkit.app.core.data.ai.lorebook

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.LorebookKeyLogic
import heizige.kk.khatkit.app.core.data.model.PromptInjection
import heizige.kk.khatkit.app.core.data.model.extractContextForMatching
import kotlin.random.Random

/**
 * SillyTavern world-info activation for the v1 subset.
 *
 * Primary keys are OR. Secondary keys apply only when [PromptInjection.RegexInjection.selective]
 * is set, using [LorebookKeyLogic] (ST `selectiveLogic` 0–3). Probability is a 0–99 roll
 * strictly less than the percent, so 0 never fires and 100 always fires. Recursive scanning
 * appends activated content to the buffer for later entries; it does not re-read the chat.
 */
fun activateLorebookEntries(
    entries: List<PromptInjection.RegexInjection>,
    messages: List<UIMessage>,
    recursiveScanning: Boolean,
    roll: () -> Int = { Random.nextInt(100) },
): List<PromptInjection.RegexInjection> {
    if (entries.isEmpty()) return emptyList()
    val ordered = entries.withIndex().sortedWith(
        compareByDescending<IndexedValue<PromptInjection.RegexInjection>> { it.value.priority }
            .thenBy { it.index }
    )
    val activated = LinkedHashMap<String, PromptInjection.RegexInjection>()
    val recursionBuffer = StringBuilder()
    var guard = 0
    while (guard++ <= entries.size) {
        var found = false
        for ((_, entry) in ordered) {
            val id = entry.id.toString()
            if (id in activated || !entry.enabled) continue
            if (entry.preventRecursion && recursionBuffer.isNotEmpty()) continue
            val chat = extractContextForMatching(messages, entry.scanDepth)
            val buffer = if (recursiveScanning && recursionBuffer.isNotEmpty() && !entry.preventRecursion) {
                chat + "\n" + recursionBuffer
            } else {
                chat
            }
            if (!entryMatches(entry, buffer)) continue
            if (!passesProbability(entry, roll)) continue
            activated[id] = entry
            found = true
            if (recursiveScanning && entry.includeInRecursion && entry.content.isNotEmpty()) {
                if (recursionBuffer.isNotEmpty()) recursionBuffer.append('\n')
                recursionBuffer.append(entry.content)
            }
        }
        if (!found || !recursiveScanning) break
    }
    return activated.values.sortedByDescending { it.priority }
}

internal fun entryMatches(entry: PromptInjection.RegexInjection, buffer: String): Boolean {
    if (!entry.enabled) return false
    if (entry.constantActive) return true
    if (entry.keywords.none { it.isNotBlank() }) return false
    if (entry.keywords.none { keyMatches(it, buffer, entry) }) return false
    if (!entry.selective) return true
    val hits = entry.secondaryKeywords.filter { it.isNotBlank() }.map { keyMatches(it, buffer, entry) }
    return when (entry.keyLogic) {
        LorebookKeyLogic.OR,
        LorebookKeyLogic.AND_ANY -> hits.any { it }
        LorebookKeyLogic.AND_ALL -> hits.isNotEmpty() && hits.all { it }
        LorebookKeyLogic.NOT_ANY -> hits.none { it }
        LorebookKeyLogic.NOT_ALL -> hits.isNotEmpty() && hits.any { !it }
    }
}

internal fun passesProbability(entry: PromptInjection.RegexInjection, roll: () -> Int): Boolean {
    val probability = entry.probability.coerceIn(0, 100)
    if (probability >= 100) return true
    if (probability <= 0) return false
    return roll().coerceIn(0, 99) < probability
}

private fun keyMatches(keyword: String, buffer: String, entry: PromptInjection.RegexInjection): Boolean {
    if (keyword.isBlank()) return false
    if (entry.useRegex) {
        val options = if (entry.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return runCatching { Regex(keyword, options).containsMatchIn(buffer) }.getOrDefault(false)
    }
    return if (entry.caseSensitive) buffer.contains(keyword) else buffer.contains(keyword, ignoreCase = true)
}
