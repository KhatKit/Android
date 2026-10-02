package heizige.kk.khatkit.mediapicker.data

import android.net.Uri
import android.provider.MediaStore
import heizige.kk.khatkit.mediapicker.domain.AllowedMedia

/**
 * MediaStore 不认识的图片格式：它们的 mime 落在 `image/%` 之外，
 * 只能从 `MediaStore.Files` 里按扩展名补捞。
 */
val SUPPORTED_FILES = mapOf(
    "jxl" to "image/jxl",
    "qoi" to "image/qoi",
)

fun getSupportedFileSequence(allowedMedia: AllowedMedia): Sequence<String> =
    when (allowedMedia) {
        AllowedMedia.Both, is AllowedMedia.Photos -> SUPPORTED_FILES.asSequence()
        AllowedMedia.Videos -> emptySequence()
    }.map { (ext, _) -> "%.$ext" }

fun humanFileSize(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val units = arrayOf("kB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1000
    var unitIndex = 0
    while (value >= 1000 && unitIndex < units.lastIndex) {
        value /= 1000
        unitIndex++
    }
    return if (value >= 100) "%.0f %s".format(value, units[unitIndex])
    else "%.1f %s".format(value, units[unitIndex])
}

internal fun mediaStoreImagesUri() = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
internal fun mediaStoreVideosUri() = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
internal fun mediaStoreFilesUri() = MediaStore.Files.getContentUri("external")
