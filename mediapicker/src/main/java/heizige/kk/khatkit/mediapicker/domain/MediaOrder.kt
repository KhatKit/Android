package heizige.kk.khatkit.mediapicker.domain

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive

/** 媒体排序维度。UI 的排序面板直接把枚举铺成列表。 */
sealed class MediaOrder(val orderType: OrderType) {
    class Label(orderType: OrderType) : MediaOrder(orderType)
    class Date(orderType: OrderType) : MediaOrder(orderType)
    class Path(orderType: OrderType) : MediaOrder(orderType)
    class Size(orderType: OrderType) : MediaOrder(orderType)
    class DateTaken(orderType: OrderType) : MediaOrder(orderType)
    class Random(orderType: OrderType = OrderType.Descending) : MediaOrder(orderType)

    fun copy(orderType: OrderType): MediaOrder = when (this) {
        is Date -> Date(orderType)
        is Label -> Label(orderType)
        is Path -> Path(orderType)
        is Size -> Size(orderType)
        is DateTaken -> DateTaken(orderType)
        is Random -> Random(orderType)
    }

    suspend fun sortMedia(media: List<Media>): List<Media> = coroutineScope {
        fun sortByText(selector: (Media) -> String): List<Media> {
            val withKeys = media.map {
                ensureActive()
                it to selector(it).lowercase()
            }
            val comparator = compareBy<Pair<Media, String>> {
                ensureActive()
                it.second
            }
            return withKeys.sortedWith(
                if (orderType == OrderType.Ascending) comparator else comparator.reversed()
            ).map { it.first }
        }

        fun byDate(): List<Media> = when (orderType) {
            OrderType.Ascending -> media.sortedBy { it.timestamp }
            OrderType.Descending -> media.sortedByDescending { it.timestamp }
        }

        fun byDateTaken(): List<Media> = when (orderType) {
            OrderType.Ascending -> media.sortedBy { it.dateTakenSeconds }
            OrderType.Descending -> media.sortedByDescending { it.dateTakenSeconds }
        }

        fun bySize(): List<Media> {
            media.forEach { ensureActive() }
            return when (orderType) {
                OrderType.Ascending -> media.sortedBy { it.fileSize }
                OrderType.Descending -> media.sortedByDescending { it.fileSize }
            }
        }

        when (this@MediaOrder) {
            is Date -> byDate()
            is DateTaken -> byDateTaken()
            is Size -> bySize()
            is Label -> sortByText(Media::label)
            is Path -> sortByText(Media::path)
            is Random -> media.shuffled()
        }
    }

    fun sortAlbums(albums: List<Album>): List<Album> = when (this) {
        is Date -> when (orderType) {
            OrderType.Ascending -> albums.sortedBy { it.timestamp }
            OrderType.Descending -> albums.sortedByDescending { it.timestamp }
        }

        is Label -> when (orderType) {
            OrderType.Ascending -> albums.sortedBy { it.label.lowercase() }
            OrderType.Descending -> albums.sortedByDescending { it.label.lowercase() }
        }

        else -> albums
    }
}
