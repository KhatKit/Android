package heizige.kk.khatkit.app.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Avatar {
    @Serializable
    @SerialName("heizige.kk.khatkit.app.data.model.Avatar.Dummy")
    data object Dummy : Avatar()

    @Serializable
    @SerialName("heizige.kk.khatkit.app.data.model.Avatar.Emoji")
    data class Emoji(val content: String) : Avatar()

    @Serializable
    @SerialName("heizige.kk.khatkit.app.data.model.Avatar.Image")
    data class Image(val url: String) : Avatar()
}
