package heizige.kk.khatkit.app.feature.chat.data

internal data class ChatContextUsage(val tokens: Long, val limit: Int?) {
    val fraction: Float? get() = limit?.let { (tokens.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
}
