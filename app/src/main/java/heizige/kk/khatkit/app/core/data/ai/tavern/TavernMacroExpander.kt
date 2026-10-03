package heizige.kk.khatkit.app.core.data.ai.tavern

import kotlin.random.Random

/** Expands the small, portable macro subset shared by Tavern character cards. */
fun expandTavernMacros(
    template: String,
    userName: String,
    characterName: String,
    randomValue: () -> String = { Random.nextInt(0, 101).toString() },
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
            expression.equals("random", ignoreCase = true) -> randomValue()
            expression.equals("roll", ignoreCase = true) -> roll(1, 100).toString()
            expression.matches(Regex("roll(?:[:：]|\\s+)?\\s*\\d+d\\d+", RegexOption.IGNORE_CASE)) -> {
                val dice = Regex("(\\d+)d(\\d+)", RegexOption.IGNORE_CASE).find(expression)!!.destructured
                val count = dice.component1().toIntOrNull()?.coerceIn(1, 100) ?: return@replace match.value
                val sides = dice.component2().toIntOrNull()?.coerceIn(2, 1000) ?: return@replace match.value
                roll(count, sides).toString()
            }
            else -> match.value
        }
    }
}