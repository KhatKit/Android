package heizige.kk.khatkit.app.core.data.ai.tavern

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.ai.transformers.InputMessageTransformer
import heizige.kk.khatkit.app.core.data.ai.transformers.TransformerContext

/** Expands Tavern macros immediately before a provider request. */
object TavernMacroTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = expandTavernMacrosInMessages(
        messages = messages,
        userName = ctx.settings.displaySetting.userNickname.ifBlank { "User" },
        characterName = ctx.assistant.name.ifBlank { "Assistant" },
    )
}

internal fun expandTavernMacrosInMessages(
    messages: List<UIMessage>,
    userName: String,
    characterName: String,
    randomValue: () -> String = { kotlin.random.Random.nextInt(0, 101).toString() },
    roll: (count: Int, sides: Int) -> Int = { count, sides ->
        (1..count).sumOf { kotlin.random.Random.nextInt(1, sides + 1) }
    },
): List<UIMessage> = messages.map { message ->
    message.copy(
        parts = message.parts.map { part ->
            if (part is UIMessagePart.Text) {
                part.copy(
                    text = expandTavernMacros(
                        template = part.text,
                        userName = userName,
                        characterName = characterName,
                        randomValue = randomValue,
                        roll = roll,
                    )
                )
            } else {
                part
            }
        }
    )
}