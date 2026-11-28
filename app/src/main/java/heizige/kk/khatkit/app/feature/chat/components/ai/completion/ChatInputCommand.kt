package heizige.kk.khatkit.app.feature.chat.components.ai.completion

data class ChatInputCommand(
    val name: String,
    val label: String,
    val onRun: () -> Unit,
)

internal fun List<ChatInputCommand>.suggestCommands(context: ChatCompletionContext): List<ChatInputCommand> {
    val text = context.text
    if (context.hasSelection || context.cursor != text.length || !text.startsWith('/')) return emptyList()
    val prefix = text.drop(1)
    if (prefix.any { it !in 'a'..'z' && it !in 'A'..'Z' }) return emptyList()
    return filter { it.name.startsWith(prefix, ignoreCase = true) }
}

internal fun List<ChatInputCommand>.findCommand(text: String): ChatInputCommand? =
    firstOrNull { "/${it.name}".equals(text.trimEnd(' ', '\t'), ignoreCase = true) }
