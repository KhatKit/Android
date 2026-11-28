package heizige.kk.khatkit.app.feature.settings.data

internal data class DonationUiState(
    val snapshot: DonationSnapshot? = null,
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val failed: Boolean = false,
    val supporters: List<DonationSupporter> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val wallLoaded: Boolean = false,
    val wallLoading: Boolean = false,
    val wallFailed: Boolean = false,
)
