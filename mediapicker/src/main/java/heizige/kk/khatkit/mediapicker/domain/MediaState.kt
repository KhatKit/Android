package heizige.kk.khatkit.mediapicker.domain

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class MediaState(
    val media: List<Media> = emptyList(),
    val mappedMedia: List<MediaItem> = emptyList(),
    val error: String = "",
    val isLoading: Boolean = true,
)

data class AlbumState(
    val albums: List<Album> = emptyList(),
    val error: String = "",
)

/** 分组维度：按时间（修改/拍摄）或按类型（mime/扩展名）。 */
enum class MediaGrouping {
    DateModified, DateTaken, MimeType, Extension
}

/** 时间分组的粒度。`None` 表示不分组，网格平铺。 */
enum class MediaDateGroup {
    Year, Month, Day, None
}

data class MediaDisplaySettings(
    val grouping: MediaGrouping = MediaGrouping.DateModified,
    val dateGroup: MediaDateGroup = MediaDateGroup.Day,
    val groupOrder: OrderType = OrderType.Descending,
    val mediaOrder: MediaOrder = MediaOrder.Date(OrderType.Descending),
)

/**
 * 把扁平媒体列表切成 [MediaItem] 序列（头 + 项）。
 *
 * @param dateLabel 由 UI 层提供，模块内不依赖 `java.icu`/locale 资源，
 *   这样宿主换语言/换字阶不用改数据层。
 */
fun List<Media>.groupMedia(
    settings: MediaDisplaySettings,
    zoneId: ZoneId = ZoneId.systemDefault(),
    dateLabel: (Long, MediaDateGroup) -> String,
): List<MediaItem> {
    fun List<Media>.items() = map { MediaItem.MediaViewItem("media_${it.id}_${it.label}", it) }

    if (settings.dateGroup == MediaDateGroup.None) return items()

    val groups = groupBy { media ->
        when (settings.grouping) {
            MediaGrouping.DateModified, MediaGrouping.DateTaken -> {
                val timestamp =
                    if (settings.grouping == MediaGrouping.DateTaken) media.dateTakenSeconds
                    else media.timestamp
                val date = Instant.ofEpochSecond(timestamp).atZone(zoneId).toLocalDate()
                when (settings.dateGroup) {
                    MediaDateGroup.Year -> date.withDayOfYear(1)
                    MediaDateGroup.Month -> date.withDayOfMonth(1)
                    else -> date
                }.toString()
            }

            // 土耳其语下 "I".lowercase() 是点没上点的 ı，会把 JPEG 和 JXL 拆成两个分组
            MediaGrouping.MimeType -> media.mimeType.lowercase(Locale.ROOT)
            MediaGrouping.Extension -> media.fileExtension.lowercase(Locale.ROOT)
        }
    }.toSortedMap()

    val entries = if (settings.groupOrder == OrderType.Descending) {
        groups.entries.reversed()
    } else {
        groups.entries.toList()
    }

    return entries.flatMap { (key, media) ->
        val first = media.first()
        val title = when (settings.grouping) {
            MediaGrouping.DateModified -> dateLabel(first.timestamp, settings.dateGroup)
            MediaGrouping.DateTaken -> dateLabel(first.dateTakenSeconds, settings.dateGroup)
            else -> key
        }
        listOf(MediaItem.Header("header_${settings.grouping}_$key", title, media)) + media.items()
    }
}
