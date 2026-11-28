package heizige.kk.khatkit.app.feature.settings.data

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
internal data class DonationProfile(
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val projectUrl: String,
    val websiteUrl: String,
    val contactEmail: String? = null,
)

@Serializable
internal data class DonationSnapshot(
    val profile: DonationProfile,
    val channels: List<RemoteDonationChannel>,
    val acknowledgements: List<DonationSupporter>,
    val acknowledgementTotal: Int,
) {
    fun validated(): DonationSnapshot = apply {
        require(profile.name.isNotBlank() && profile.name.length <= 200)
        require(isDonationUrl(profile.projectUrl) && isDonationUrl(profile.websiteUrl))
        require(profile.avatarUrl == null || isDonationUrl(profile.avatarUrl))
        require(profile.contactEmail == null || isDonationEmail(profile.contactEmail))
        require(channels.size <= 100 && channels.map { it.id }.distinct().size == channels.size)
        channels.forEach {
            require(it.id.isNotBlank() && it.label.isNotBlank() && it.label.length <= 200)
            require(isDonationUrl(it.url, payment = true))
        }
        require(acknowledgementTotal >= acknowledgements.size)
        require(acknowledgements.size <= 3)
        validateSupporters(acknowledgements)
    }
}

@Serializable
internal data class RemoteDonationChannel(val id: String, val label: String, val url: String, val sortOrder: Int = 0)

@Serializable
internal data class DonationSupporter(val id: String, val name: String, val amount: String? = null, val message: String? = null)

@Serializable
internal data class DonationSupporterPage(val items: List<DonationSupporter>, val total: Int, val limit: Int, val offset: Int)

internal fun validateSupporters(items: List<DonationSupporter>) {
    require(items.map { it.id }.distinct().size == items.size)
    items.forEach { require(it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 200) }
}

internal fun isDonationUrl(value: String, payment: Boolean = false): Boolean = runCatching {
    val uri = URI(value)
    !value.any { it.isWhitespace() || it.isISOControl() } && uri.rawUserInfo == null && when (uri.scheme?.lowercase()) {
        "http", "https" -> !uri.host.isNullOrBlank()
        "wxp", "alipays" -> payment && !uri.rawSchemeSpecificPart.isNullOrBlank()
        else -> false
    }
}.getOrDefault(false)

internal fun isDonationEmail(value: String): Boolean = value.length <= 254 &&
    Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+[.][A-Za-z0-9-]+").matches(value)
