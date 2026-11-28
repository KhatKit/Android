package heizige.kk.khatkit.app.feature.imggen.data

import kotlinx.serialization.Serializable


@Serializable
data class GeneratedImage(
    val id: Int,
    val prompt: String,
    val filePath: String,
    val timestamp: Long,
    val model: String
)
