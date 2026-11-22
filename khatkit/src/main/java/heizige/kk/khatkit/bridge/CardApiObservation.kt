package heizige.kk.khatkit.bridge

/** Safe summary of a request actually attempted by a card. No path, headers or payload. */
data class CardApiObservation(
    val card: String,
    val host: String,
    val method: String,
    val status: Int?,
    val timeMillis: Long = System.currentTimeMillis(),
)
