package heizige.kk.khatkit.app.core.data.ai.tavern

import kotlin.random.Random

/** Expands the small, portable macro subset shared by Tavern character cards. */
fun expandTavernMacros(
    template: String,
    userName: String,
    characterName: String,
    lastMessage: String = "",
    time: () -> String = { defaultTavernTime() },
    randomValue: () -> String = { Random.nextInt(0, 101).toString() },
    randomChoice: (List<String>) -> String = { choices -> choices[Random.nextInt(choices.size)] },
    roll: (count: Int, sides: Int) -> Int = { count, sides ->
        (1..count).sumOf { Random.nextInt(1, sides + 1) }
    },
): String {
    val pattern = Regex("\\{\\{\\s*([^{}]+?)\\s*}}")
    return pattern.replace(template) { match ->
        val expression = match.groupValues[1].trim()
        when {
            expression.equals("user", ignoreCase = true) -> userName
            expression.equals("char", ignoreCase = true) || expression.equals("character", ignoreCase = true) -> characterName
            expression.equals("lastMessage", ignoreCase = true) -> lastMessage
            expression.equals("time", ignoreCase = true) -> time()
            expression.equals("random", ignoreCase = true) -> randomValue()
            expression.startsWith("random:", ignoreCase = true) ||
                expression.startsWith("random::", ignoreCase = true) -> {
                val choices = splitRandomList(expression.substringAfter(":"))
                if (choices.isEmpty()) match.value else randomChoice(choices)
            }
            expression.equals("roll", ignoreCase = true) -> roll(1, 100).toString()
            expression.matches(Regex("roll(?:[:：]|\\s+)?\\s*(?:\\d+)?d\\d+", RegexOption.IGNORE_CASE)) -> {
                val dice = Regex("(\\d+)?d(\\d+)", RegexOption.IGNORE_CASE).find(expression)!!.destructured
                val count = dice.component1().toIntOrNull()?.coerceIn(1, 100) ?: 1
                val sides = dice.component2().toIntOrNull()?.coerceIn(2, 1000) ?: return@replace match.value
                roll(count, sides).toString()
            }
            else -> match.value
        }
    }
}

fun defaultTavernTime(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): String =
    now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

/**
 * SillyTavern splits `{{random::a::b}}` on `::` and `{{random:a,b}}` on commas.
 * The local v1 list also accepts `{{random:a|b}}`.
 */
internal fun splitRandomList(body: String): List<String> = when {
    body.contains("::") -> body.split("::")
    body.contains("|") -> body.split("|")
    else -> body.split(",")
}.map { it.trim() }.filter { it.isNotEmpty() }