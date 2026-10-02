package heizige.kk.khatkit.mediapicker.domain

sealed class OrderType {
    data object Ascending : OrderType()
    data object Descending : OrderType()
}
