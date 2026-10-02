package heizige.kk.khatkit.mediapicker.domain

/**
 * 网格里渲染的条目：日期粘性头 or 图片本身。
 *
 * [key] 唯一且稳定 —— LazyVerticalGrid 的 item key 直接用它，
 * MediaStore 刷新后同一个 `media_<id>_<label>` 会复用同一节点，不会整列表重排。
 */
sealed class MediaItem {

    abstract val key: String

    data class Header(
        override val key: String,
        val text: String,
        val data: List<Media>,
    ) : MediaItem()

    data class MediaViewItem(
        override val key: String,
        val media: Media,
    ) : MediaItem()
}

val String.isHeaderKey: Boolean get() = startsWith("header_")
