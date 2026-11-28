package heizige.kk.khatkit.app.feature.lanshare.data

import heizige.kk.khatkit.app.feature.lanshare.data.LanShareDocument


internal data class LanShareState(
    val directory: LanShareDocument? = null,
    val url: String? = null,
    val isBusy: Boolean = false,
    val outgoing: String = "",
    val incoming: String = "",
    val error: String? = null,
)
