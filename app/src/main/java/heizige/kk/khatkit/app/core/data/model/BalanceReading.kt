package heizige.kk.khatkit.app.core.data.model



internal sealed interface BalanceReading {
    data object NotQueried : BalanceReading
    data object Loading : BalanceReading
    data object Unavailable : BalanceReading
    data class Value(val raw: String) : BalanceReading
}
