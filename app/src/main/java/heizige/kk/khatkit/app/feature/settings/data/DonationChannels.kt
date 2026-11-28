package heizige.kk.khatkit.app.feature.settings.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import heizige.kk.khatkit.app.R

internal enum class DonationChannel(@StringRes val label: Int, @DrawableRes val qr: Int) {
    Wechat(R.string.donate_page_wechat, R.drawable.donate_wechat_qr),
    Alipay(R.string.donate_page_alipay, R.drawable.donate_alipay_qr),
}

internal data class DonationPortal(
    val id: String,
    @StringRes val label: Int,
    val icon: String,
    val url: String,
) {
    fun destination(snapshot: DonationSnapshot?): String? =
        if (snapshot == null) url else snapshot.channels.firstOrNull { it.id == id }?.url
}

/** Only add payment destinations and public acknowledgements supplied by the maintainer. */
internal object DonationChannels {
    const val projectUrl = "https://github.com/KhatKit/Android"
    const val websiteUrl = "https://heizige.top"
    val portals = listOf(
        DonationPortal("afdian", R.string.sponsor_channel_afdian, "flash-outline", "https://afdian.com/a/heizige"),
        DonationPortal("kofi", R.string.sponsor_channel_kofi, "cafe-outline", "https://ko-fi.com/heizige"),
    )
}
