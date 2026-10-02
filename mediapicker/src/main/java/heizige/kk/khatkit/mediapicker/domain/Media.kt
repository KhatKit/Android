package heizige.kk.khatkit.mediapicker.domain

import heizige.kk.khatkit.mediapicker.data.humanFileSize

/**
 * 一条媒体记录，对应 MediaStore 里的一行。
 *
 * 字段与上游 ImageToolbox `Media` 保持一致，方便对照排查。
 */
data class Media(
    val id: Long = 0,
    val label: String,
    val uri: String,
    val path: String,
    val relativePath: String,
    val albumID: Long,
    val albumLabel: String,
    /** 修改时间（秒）。 */
    val timestamp: Long,
    /** 拍摄时间（毫秒），部分 provider 不返回。 */
    val takenTimestamp: Long? = null,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
) {
    /** MediaStore 拿不到 size 时是 0，调用方用 `> 0` 判断要不要显示体积角标。 */
    val fileSize: Long get() = size ?: 0L

    val humanFileSize: String get() = humanFileSize(fileSize)

    val fileExtension: String = label.substringAfterLast(".").removePrefix(".")

    val volume: String = path.substringBeforeLast("/").removeSuffix(relativePath.removeSuffix("/"))
}

/** 拍摄时间（秒），缺失时回落到修改时间。 */
val Media.dateTakenSeconds: Long
    get() = takenTimestamp?.takeIf { it > 0 }?.div(1000) ?: timestamp

const val WEEKLY_DATE_FORMAT = "EEEE"
const val DEFAULT_DATE_FORMAT = "EEE, d MMMM"
const val EXTENDED_DATE_FORMAT = "EEE, d MMMM yyyy"
