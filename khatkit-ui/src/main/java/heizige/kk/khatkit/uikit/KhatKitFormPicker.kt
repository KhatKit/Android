package heizige.kk.khatkit.uikit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 表单里的选择器类型：`file_picker` 选文件、`dir_picker` 选目录。 */
enum class FormPickKind {
    FILE,
    DIR,
}

/**
 * 表单 `file_picker` / `dir_picker` 点「浏览」后交给宿主的请求。
 *
 * `:khatkit-ui` 不依赖 app，系统选择器与媒体选择器都由宿主启动；宿主挑完调
 * [FormPickerHost.submit] 回填，表单自动重组。
 */
data class FormPickRequest(
    val id: String,
    val kind: FormPickKind,
    /** 脚本声明的 `media`（image / video / any），媒体选择器据此收窄可见类型。 */
    val media: String,
    /** 脚本声明的 `filter`（mime 或扩展名）；非媒体类型走系统文件选择器。 */
    val filter: String,
    /** `multiple` 为真时宿主允许多选，回填值是换行分隔的路径列表。 */
    val multiple: Boolean,
) {
    /** 走应用内媒体选择器（KhatKitMediaPicker），否则走系统文档选择器。 */
    val preferMediaPicker: Boolean
        get() = kind == FormPickKind.FILE && isMediaFilter(filter)

    companion object {
        /** 媒体选择器能直接呈现的类型；其余（pdf / 文档等）必须走系统选择器。 */
        private val MEDIA_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp",
            "mp4", "mov", "mkv", "webm", "3gp",
            "mp3", "m4a", "wav", "ogg", "flac", "aac",
        )

        /**
         * filter 是否描述「媒体」。空 filter 视为媒体（默认选图片）；
         * `*` 通配与 pdf / 文档等类型交给系统文件选择器。
         */
        fun isMediaFilter(filter: String): Boolean {
            val raw = filter.trim().lowercase()
            if (raw.isEmpty()) return true
            if (raw.startsWith("*")) return false
            if (raw.contains('/')) {
                return raw.startsWith("image/") || raw.startsWith("video/") || raw.startsWith("audio/")
            }
            return raw.removePrefix(".") in MEDIA_EXTENSIONS
        }

        /** 从表单 item 声明里读出选择请求；`id` 缺失时返回 null（该行不参与返回值）。 */
        fun from(item: Map<String, Any?>, kind: FormPickKind): FormPickRequest? {
            val id = item["id"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
            return FormPickRequest(
                id = id,
                kind = kind,
                media = item["media"]?.toString()?.trim()?.lowercase().orEmpty(),
                filter = item["filter"]?.toString()?.trim().orEmpty(),
                multiple = when (val raw = item["multiple"]) {
                    is Boolean -> raw
                    is Number -> raw.toDouble() != 0.0
                    is String -> raw.equals("true", ignoreCase = true) || raw == "1"
                    else -> false
                },
            )
        }
    }
}

/**
 * 表单选择器的宿主桥。
 *
 * 表单渲染时把自己登记成 [attach] 的回填口；用户点「浏览」时把请求放进 [request]，
 * 宿主启动系统 / 媒体选择器，挑完调 [submit]（取消调 [cancel]）。
 * 宿主未提供本对象时，`file_picker` / `dir_picker` 保持纯文本框（历史行为）。
 */
class FormPickerHost {

    private val _request = MutableStateFlow<FormPickRequest?>(null)
    val request: StateFlow<FormPickRequest?> = _request.asStateFlow()

    private var fill: ((String, Any?) -> Unit)? = null

    /** 表单挂载时登记回填口，卸载时清空（由 KhatKitForm 的 DisposableEffect 调用）。 */
    internal fun attach(fill: (String, Any?) -> Unit) {
        this.fill = fill
    }

    internal fun detach() {
        fill = null
        _request.value = null
    }

    /** 表单里点了「浏览」。 */
    fun request(request: FormPickRequest) {
        _request.value = request
    }

    /** 宿主挑完回填；空串按未选择处理（清掉该行）。 */
    fun submit(id: String, value: String) {
        fill?.invoke(id, value.takeIf { it.isNotBlank() })
        _request.value = null
    }

    /** 用户取消、宿主无法呈现选择器，或导入完成前收起选择器；不动已填的值。 */
    fun clearRequest() {
        _request.value = null
    }
}