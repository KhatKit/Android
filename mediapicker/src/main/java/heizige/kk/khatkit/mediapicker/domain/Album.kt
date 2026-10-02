package heizige.kk.khatkit.mediapicker.domain

data class Album(
    val id: Long = 0,
    val label: String,
    val uri: String,
    val pathToThumbnail: String,
    val relativePath: String,
    val timestamp: Long,
    val count: Long = 0,
) {
    companion object {
        /** "全部"伪相册，选择器相册行永远放在第一个。 */
        val All = Album(
            id = ALL_ALBUM_ID,
            label = "All",
            uri = "",
            pathToThumbnail = "",
            relativePath = "",
            timestamp = 0,
            count = 0,
        )
    }
}

const val ALL_ALBUM_ID = -1L
