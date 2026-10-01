package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.UiBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 卡片弹层的呈现选项（`ui.form` / `ui.show` / `ui.sheet` / `web.openLogin` 的 options 参数）。
 *
 * - [fullscreen]：占满屏幕宽高（无部分展开态），仍可下滑/返回关闭；
 * - [landscape]：弹层显示期间允许横屏（宿主把 Activity 方向设为 FULL_SENSOR，关闭后还原）；
 * - [height]：弹层高度占屏幕比例 0.1–1.0，null 用组件默认（约 0.9 上限）；
 * - [desktop]：仅网页登录 Sheet 认，按桌面布局渲染（宽视口 + overview + 缩放）；
 * - [userAgent]：仅网页登录 Sheet 认，自定义 User-Agent 字符串。
 *
 * 宿主**不内置任何浏览器 UA**：桌面模式要生效，脚本需自己声明 [userAgent]
 * （如 `web.openLogin(url, title, actions, { desktop = true, user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) …" })`）。
 *
 * 脚本传 `Map`（Lua table / JS object）即可，非法值忽略不报错。
 */
data class UiSheetOptions(
    val fullscreen: Boolean = false,
    val landscape: Boolean = false,
    val height: Float? = null,
    val desktop: Boolean = false,
    val userAgent: String? = null,
) {
    /** 需要自定义大弹层（全屏或显式高度）时为 true。 */
    val usesLargeSheet: Boolean get() = fullscreen || height != null

    /** 脚本是否声明了网页渲染开关；有则宿主才去改 WebView 设置。 */
    val hasWebSettings: Boolean get() = desktop || !userAgent.isNullOrBlank()

    companion object {
        val DEFAULT = UiSheetOptions()

        /** 高度下限：低于 10% 没意义，钳到 0.1。 */
        const val MIN_HEIGHT = 0.1f

        fun from(map: Map<String, Any?>?): UiSheetOptions {
            if (map.isNullOrEmpty()) return DEFAULT
            val rawHeight = (map["height"] as? Number)?.toFloat()
            return UiSheetOptions(
                fullscreen = map["fullscreen"].toBooleanFlag(),
                landscape = map["landscape"].toBooleanFlag(),
                height = rawHeight
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?.coerceIn(MIN_HEIGHT, 1f),
                desktop = map["desktop"].toBooleanFlag(),
                userAgent = (map["user_agent"] ?: map["userAgent"])?.toString()?.trim()?.ifBlank { null },
            )
        }

        private fun Any?.toBooleanFlag(): Boolean = when (this) {
            is Boolean -> this
            is Number -> toDouble() != 0.0
            is String -> equals("true", ignoreCase = true) || this == "1"
            else -> false
        }
    }
}

/**
 * Sheet 动作的摆放位置（action 表单字段 `placement`）。
 *
 * - [TOP]：渲染成弹层右上角的图标按钮（需要 `icon`，无图标时退回 [CONTENT]）；
 * - [OVERFLOW]：收进右上角的溢出菜单；
 * - [CONTENT]：内容区底部的文字按钮（历史行为，未声明 `placement` 时的默认）。
 */
enum class SheetActionPlacement {
    TOP,
    OVERFLOW,
    CONTENT;

    companion object {
        fun from(raw: String?): SheetActionPlacement = when (raw?.trim()?.lowercase()) {
            "top", "header", "icon" -> TOP
            "overflow", "menu" -> OVERFLOW
            else -> CONTENT
        }
    }
}

/** 归一化后的 Sheet 动作：事件名 + 展示标签 + 图标名 + 摆放位置。 */
data class SheetAction(
    val event: String,
    val label: String,
    val icon: String,
    val placement: SheetActionPlacement,
) {
    /** 能否作为顶栏图标按钮：事件与标签齐全且声明了图标名。 */
    val topBarIcon: Boolean get() = event.isNotBlank() && label.isNotBlank() && icon.isNotBlank()

    /** 图标名已知时宿主才能解析出 ImageVector，其余退回默认图标。 */
    val hasIcon: Boolean get() = icon.isNotBlank()

    companion object {
        /** 解析脚本传入的 action 表单；事件缺失时回落到 `id`，都为空则整条忽略。 */
        fun parse(actions: List<Map<String, Any?>>): List<SheetAction> = actions.mapNotNull { action ->
            val event = action["event"]?.toString()?.trim().orEmpty()
                .ifBlank { action["id"]?.toString()?.trim().orEmpty() }
            val label = action["label"]?.toString()?.trim().orEmpty().ifBlank { event }
            if (event.isBlank()) return@mapNotNull null
            val icon = action["icon"]?.toString()?.trim().orEmpty()
            val placement = SheetActionPlacement.from(action["placement"]?.toString())
            SheetAction(
                event = event,
                label = label,
                icon = icon,
                placement = if (placement == SheetActionPlacement.TOP && icon.isBlank()) {
                    SheetActionPlacement.CONTENT
                } else {
                    placement
                },
            )
        }
    }
}

/** 一个等待宿主 UI 处理的请求。 */
sealed interface UiRequest {
    data class Sheet(
        val title: String,
        val url: String? = null,
        val actions: List<Map<String, Any?>>,
        val options: UiSheetOptions,
        val deferred: CompletableDeferred<Map<String, Any?>?>,
        /** true 表示卡片网页登录（`web.openLogin`），宿主据此启用桌面模式与 Cookie 顶栏动作。 */
        val isWeb: Boolean = false,
    ) : UiRequest {
        private val parsedActions: List<SheetAction> = SheetAction.parse(actions)

        /** 右上角图标按钮（脚本用 `placement="top"` + `icon` 声明）。 */
        val topBarActions: List<SheetAction>
            get() = parsedActions.filter { it.placement == SheetActionPlacement.TOP }

        /** 右上角溢出菜单项（脚本用 `placement="overflow"` 声明）。 */
        val overflowActions: List<SheetAction>
            get() = parsedActions.filter { it.placement == SheetActionPlacement.OVERFLOW }

        /** 内容区文字按钮；顶栏动作不再重复出现在这里。 */
        val contentActions: List<SheetAction>
            get() = parsedActions.filter { it.placement == SheetActionPlacement.CONTENT }

        /** 网页登录 Sheet 才需要 Cookie 顶栏动作（普通表单 Sheet 没有这个概念）。 */
        val showTopBar: Boolean get() = topBarActions.isNotEmpty() || overflowActions.isNotEmpty()
    }

    data class Form(
        val title: String,
        val items: List<Map<String, Any?>>,
        val options: UiSheetOptions,
        val deferred: CompletableDeferred<Map<String, Any?>?>,
    ) : UiRequest

    data class Confirm(
        val title: String,
        val message: String,
        val danger: Boolean,
        val deferred: CompletableDeferred<Boolean>,
    ) : UiRequest

    data class Show(
        val card: Map<String, Any?>,
        val options: UiSheetOptions,
    ) : UiRequest
}

/**
 * ui bridge 的宿主侧实现（设计文档 7.2）。
 *
 * 脚本跑后台线程，UI 主线程；用 CompletableDeferred + withTimeoutOrNull 阻塞等待回传。
 * 宿主 Compose 层观察 [request]，渲染后调用 [submitForm] / [answerConfirm] 回传。
 */
class UiBridgeHost(
    private val timeoutMillis: Long = 300_000,
    private val onAutomationStatus: (String, String) -> Unit = { _, _ -> },
    private val onCancelled: () -> Boolean = { false },
) : UiBridge {

    private val _request = MutableStateFlow<UiRequest?>(null)
    val request: StateFlow<UiRequest?> = _request.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Float, String>?>(null)
    val progress: StateFlow<Pair<Float, String>?> = _progress.asStateFlow()

    private val _shown = MutableStateFlow<Map<String, Any?>?>(null)
    val shown: StateFlow<Map<String, Any?>?> = _shown.asStateFlow()

    override fun form(
        title: String,
        items: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        _request.value = UiRequest.Form(title, items, UiSheetOptions.from(options), deferred)
        return runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } }
    }

    override fun sheet(
        title: String,
        actions: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        _request.value = UiRequest.Sheet(title = title, actions = actions, options = UiSheetOptions.from(options), deferred = deferred)
        return runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } }
    }

    override fun webSheet(title: String, url: String, actions: List<Map<String, Any?>>, options: Map<String, Any?>?): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        _request.value = UiRequest.Sheet(
            title = title,
            url = url,
            actions = actions,
            options = UiSheetOptions.from(options),
            deferred = deferred,
            isWeb = true,
        )
        return runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } }
    }

    override fun confirm(title: String, message: String, danger: Boolean): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _request.value = UiRequest.Confirm(title, message, danger, deferred)
        return runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } } ?: false
    }

    override fun progress(ratio: Float, label: String) {
        _progress.value = ratio.coerceIn(0f, 1f) to label
        // 进度文字同步到自动化看板（宿主未接线时为 no-op）
        if (label.isNotBlank()) onAutomationStatus(label, "")
    }

    override fun show(card: Map<String, Any?>, options: Map<String, Any?>?) {
        _shown.value = card
        _request.value = UiRequest.Show(card, UiSheetOptions.from(options))
    }

    override fun automationStatus(label: String, detail: String) {
        // Keep the same status visible inside KhatKit while the external overlay
        // remains responsible for runs that continue over another app.
        if (label.isNotBlank()) _progress.value = 1f to if (detail.isBlank()) label else "$label：$detail"
        onAutomationStatus(label, detail)
    }

    override fun isCancelled(): Boolean = onCancelled()

    fun submitForm(values: Map<String, Any?>?) {
        val current = _request.value as? UiRequest.Form ?: return
        current.deferred.complete(values)
        _request.value = null
    }

    fun selectSheetAction(event: String, values: Map<String, Any?> = emptyMap()) {
        val current = _request.value as? UiRequest.Sheet ?: return
        current.deferred.complete(mapOf("event" to event, "values" to values))
        _request.value = null
    }

    fun answerConfirm(confirmed: Boolean) {
        val current = _request.value as? UiRequest.Confirm ?: return
        current.deferred.complete(confirmed)
        _request.value = null
    }

    fun dismiss() {
        when (val current = _request.value) {
            is UiRequest.Form -> current.deferred.complete(null)
            is UiRequest.Confirm -> current.deferred.complete(false)
            is UiRequest.Sheet -> current.deferred.complete(null)
            else -> Unit
        }
        _request.value = null
    }
}
