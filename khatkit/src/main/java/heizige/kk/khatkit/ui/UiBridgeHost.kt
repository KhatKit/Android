package heizige.kk.khatkit.ui

import java.util.logging.Logger

import heizige.kk.khatkit.bridge.UiBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** 弹层导航底栏的一个条目（`options.nav.items[]`）。 */
data class UiSheetNavItem(
    /** 点击后回传给脚本的 event（缺 `event` 时用 `id`）。 */
    val event: String,
    val label: String,
    /** 图标名；`UiIcons` 里没有的名字会被丢掉，渲染侧退回圆点图标。 */
    val icon: String? = null,
    val enabled: Boolean = true,
)

/**
 * 弹层导航底栏（`options.nav`）。
 *
 * 只在宿主自绘大弹层（[UiSheetOptions.usesLargeSheet]）里渲染，位置固定在底部、
 * 不参与内容滚动；它出现时默认的操作底栏自动让位（脚本不用再写 `hide_bottombar`）。
 * 选中态由脚本每次给的 `selected` 决定 —— 点条目回传 event，卡片重跑后重建树。
 */
data class UiSheetNavigation(
    val items: List<UiSheetNavItem>,
    val selected: String? = null,
)

/** 弹层导航底栏最多几条；超过直接忽略（底部放 6 个 icon 已经不是导航了）。 */
private const val MAX_NAV_ITEMS = 5

/**
 * 卡片弹层的呈现选项（`ui.form` / `ui.show` / `ui.sheet` / `web.openLogin` 的 options 参数）。
 *
 * - [fullscreen]：占满屏幕宽高（无部分展开态），仍可下滑/返回关闭；
 * - [height]：弹层高度占屏幕比例 0.1–1.0，null 用组件默认（约 0.9 屏）；
 * - [hideTopBar]：隐藏顶栏（标题行 / 拖柄条）。下滑关闭手势仍在整块弹层上，关不掉的是「只能点按钮」那档；
 * - [hideBottomBar]：隐藏底栏（提交 / 取消按钮行）。**锁定态（[dismissable]=false）下宿主强制保留底栏**，
 *   否则用户点不到东西就出不去了；
 * - [navigation]：底部导航栏（宿主用 Kedge 的 `KedgeNavigationBar` 画），固定在弹层底部、
 *   不随内容滚动；出现时操作底栏自动让位；
 * - [largeTopBar]：顶栏换成 Kedge 的 `KedgeLargeTopAppBar`（折叠大标题），内容滚动时标题收缩；
 * - [topActions]：大顶栏右侧的图标动作（`top_actions`），点击回传 event，和导航条目同一套解析；
 * - [dismissable]：false = 锁定，点击遮罩、返回键、下滑都不能关闭，只能点弹层里的按钮（含宿主补的关闭按钮）；
 * - [orientation]：弹层期间把 Activity 方向锁到 `landscape` / `portrait` / `auto`（全传感器），关闭后还原；
 * - [landscape]：旧的布尔写法，等价 `orientation = "landscape"`（`orientation` 优先）；
 * - [desktop]：仅网页登录 Sheet 认，按桌面布局渲染（宽视口 + overview + 缩放）；
 * - [userAgent]：仅网页登录 Sheet 认，自定义 User-Agent 字符串。
 *
 * 宿主**不内置任何浏览器 UA**：桌面模式要生效，脚本需自己声明 [userAgent]
 * （如 `web.openLogin(url, title, actions, { desktop = true, user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) …" })`）。
 *
 * 脚本传 `Map`（Lua table / JS object）即可，非法值忽略不报错；每个字段都接受
 * 下划线 / 驼峰 / 连字符三种写法（`hide_topbar` / `hideTopBar` / `hide-topbar`）。
 */
data class UiSheetOptions(
    val fullscreen: Boolean = false,
    val landscape: Boolean = false,
    val height: Float? = null,
    val desktop: Boolean = false,
    val userAgent: String? = null,
    val hideTopBar: Boolean = false,
    val hideBottomBar: Boolean = false,
    val dismissable: Boolean = true,
    val orientation: String? = null,
    val navigation: UiSheetNavigation? = null,
    val largeTopBar: Boolean = false,
    val topActions: List<UiSheetNavItem> = emptyList(),
    /** 滚到内容底部时回传的 event（`load_more = "..."` 或 `load_more = true` → `"load_more"`）。 */
    val loadMoreEvent: String? = null,
) {
    /**
     * 需要自定义大弹层（自绘遮罩 + 顶/底栏完全受控）时为 true。
     *
     * 命中的情况：全屏、显式高度、隐藏顶栏、隐藏底栏、锁定不可关闭、带导航底栏。
     * 只改方向（[orientation]）不算 —— 方向是宿主级开关，默认 BottomSheet 也能用。
     */
    val usesLargeSheet: Boolean
        get() = fullscreen || height != null || hideTopBar || hideBottomBar ||
            !dismissable || navigation != null || largeTopBar || topActions.isNotEmpty() ||
            loadMoreEvent != null

    /**
     * 生效的屏幕方向：`landscape` / `portrait` / `auto`，null 表示不动用户当前的旋转设置。
     *
     * 旧写法 `landscape = true` 视作 `orientation = "landscape"`，但显式的 [orientation] 优先。
     */
    val effectiveOrientation: String?
        get() = orientation?.lowercase()?.takeIf { it in ORIENTATIONS }
            ?: if (landscape) ORIENTATION_LANDSCAPE else null

    /** 脚本是否声明了网页渲染开关；有则宿主才去改 WebView 设置。 */
    val hasWebSettings: Boolean get() = desktop || !userAgent.isNullOrBlank()

    companion object {
        val DEFAULT = UiSheetOptions()

        /** 高度下限：低于 10% 没意义，钳到 0.1。 */
        const val MIN_HEIGHT = 0.1f

        const val ORIENTATION_LANDSCAPE = "landscape"
        const val ORIENTATION_PORTRAIT = "portrait"
        const val ORIENTATION_AUTO = "auto"

        /** 认的方向取值；`sensor` / `rotated` 是 `auto` 的别名。 */
        val ORIENTATIONS = setOf(ORIENTATION_LANDSCAPE, ORIENTATION_PORTRAIT, ORIENTATION_AUTO)

        fun from(map: Map<String, Any?>?): UiSheetOptions {
            if (map.isNullOrEmpty()) return DEFAULT
            val rawHeight = (map.flag("height") as? Number)?.toFloat()
            return UiSheetOptions(
                fullscreen = map.flag("fullscreen").toBooleanFlag(),
                landscape = map.flag("landscape").toBooleanFlag(),
                height = rawHeight
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?.coerceIn(MIN_HEIGHT, 1f),
                desktop = map.flag("desktop").toBooleanFlag(),
                userAgent = map.flag("user_agent")?.toString()?.trim()?.ifBlank { null },
                hideTopBar = map.flag("hide_topbar", "hide_top_bar", "hide_header", "no_topbar")
                    .toBooleanFlag(),
                hideBottomBar = map.flag("hide_bottombar", "hide_bottom_bar", "hide_actions", "no_bottombar")
                    .toBooleanFlag(),
                dismissable = map.dismissableFlag(),
                orientation = map.orientationFlag(),
                navigation = map.navigationFlag(),
                largeTopBar = map.largeTopBarFlag(),
                topActions = map.topActionsFlag(),
                loadMoreEvent = map.loadMoreFlag(),
            )
        }

        /**
         * 解析 `nav`：`{ selected = "...", items = { { id/event, label, icon } ... } }`。
         *
         * 也接受直接给数组的简写（`nav = { item1, item2, ... }`，此时没有选中态）。
         * 少于 2 条或多于 5 条一律当没写；条目的 event/label 缺一个就丢该条。
         */
        private fun Map<String, Any?>.navigationFlag(): UiSheetNavigation? {
            val raw = flag("nav", "bottom_nav", "navigation", "tabs") ?: return null
            val map = raw as? Map<*, *>
            val rawItems = when {
                map != null -> map["items"] ?: map["entries"] ?: map["list"] ?: return null
                raw is List<*> -> raw
                else -> return null
            }
            val entries = rawItems as? List<*> ?: return null
            val items = entries.mapNotNull { entry ->
                val item = entry as? Map<*, *> ?: return@mapNotNull null
                val event = (item["event"] ?: item["id"] ?: item["action"])
                    ?.toString()?.trim().orEmpty()
                val label = (item["label"] ?: item["title"] ?: event)?.toString()?.trim().orEmpty()
                if (event.isEmpty() || label.isEmpty()) return@mapNotNull null
                UiSheetNavItem(
                    event = event,
                    label = label,
                    icon = item["icon"]?.toString()?.trim()?.takeIf { UiIcons.isValid(it) },
                    enabled = item["enabled"]?.toBooleanFlag() ?: (item["disabled"]?.toBooleanFlag() != true),
                )
            }
            if (items.size !in 2..MAX_NAV_ITEMS) return null
            val selected = (map?.get("selected") ?: map?.get("value") ?: map?.get("current"))
                ?.toString()?.trim()?.ifBlank { null }
            return UiSheetNavigation(items = items, selected = selected)
        }

        /**
         * `topbar = "large"` / `large_topbar = true` 都切到 Kedge 大标题栏；
         * `topbar = "hidden"` 等价 `hide_topbar = true`（`hideTopBarFlag` 已处理）。
         */
        private fun Map<String, Any?>.largeTopBarFlag(): Boolean {
            flag("large_topbar", "large_top_bar", "large-top-bar")?.let { return it.toBooleanFlag() }
            val value = flag("topbar", "top_bar", "top_bar_style") ?: return false
            return when (value) {
                is Boolean -> value
                else -> value.toString().trim().lowercase() in setOf("large", "big", "大", "大标题")
            }
        }

        /** `load_more = true` / `load_more = "事件名"`：滚到底时回传的 event。 */
        private fun Map<String, Any?>.loadMoreFlag(): String? {
            val raw = flag("load_more", "loadmore", "on_reach_end", "on_bottom") ?: return null
            return when (raw) {
                is Boolean -> if (raw) "load_more" else null
                else -> raw.toString().trim().ifEmpty { null }
            }
        }

        /** 大顶栏右侧动作：`top_actions = { { id, label, icon }, … }`，最多 3 个。 */
        private fun Map<String, Any?>.topActionsFlag(): List<UiSheetNavItem> {
            val raw = flag("top_actions", "top_buttons", "header_actions") as? List<*> ?: return emptyList()
            return raw.mapNotNull { entry ->
                val item = entry as? Map<*, *> ?: return@mapNotNull null
                val event = (item["event"] ?: item["id"] ?: item["action"])
                    ?.toString()?.trim().orEmpty()
                val label = (item["label"] ?: item["title"] ?: event)?.toString()?.trim().orEmpty()
                if (event.isEmpty()) return@mapNotNull null
                UiSheetNavItem(
                    event = event,
                    label = label.ifEmpty { event },
                    icon = item["icon"]?.toString()?.trim()?.takeIf { UiIcons.isValid(it) },
                )
            }.take(3)
        }

        /** 按「驼峰 / 下划线 / 连字符」三种写法依次找同一个键（脚本语言习惯不一）。 */
        private fun Map<String, Any?>.flag(vararg names: String): Any? {
            for (name in names) {
                if (containsKey(name)) return this[name]
            }
            val wanted = names.map { it.replace("_", "").replace("-", "").lowercase() }
            for ((key, value) in this) {
                if (key.replace("_", "").replace("-", "").lowercase() in wanted) return value
            }
            return null
        }

        /**
         * 关不关得掉：`dismissable` / `closable` / `dismissible` 任一为 false 即锁定；
         * 反过来 `locked = true` 也是锁定的简写。
         */
        private fun Map<String, Any?>.dismissableFlag(): Boolean {
            if (flag("locked")?.toBooleanFlag() == true) return false
            val raw = flag("dismissable", "dismissible", "closable") ?: return true
            // 键存在就按它取值：明确写 false / "false" / 0 都算锁定
            return raw.toBooleanFlag() || (raw is String && raw.trim().lowercase() in setOf("1", "yes", "on"))
        }

        /** 方向取值：`landscape` / `portrait` / `auto`（`sensor` 视作 auto）；非法值当未设置。 */
        private fun Map<String, Any?>.orientationFlag(): String? {
            val raw = flag("orientation", "screen_orientation")?.toString()?.trim()?.lowercase()
            return when (raw) {
                ORIENTATION_LANDSCAPE, "横屏" -> ORIENTATION_LANDSCAPE
                ORIENTATION_PORTRAIT, "竖屏" -> ORIENTATION_PORTRAIT
                ORIENTATION_AUTO, "sensor", "full_sensor" -> ORIENTATION_AUTO
                else -> null
            }
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
 * - [TOP]：渲染成 BottomSheet 拖柄所在顶行右侧的图标按钮（需要 `icon`，无图标时退回 [CONTENT]）；
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

        /** 拖柄顶行右侧图标按钮（脚本用 `placement="top"` + `icon` 声明）。 */
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

    data class FormTree(
        val title: String,
        val root: UiNode,
        val options: UiSheetOptions,
        val deferred: CompletableDeferred<Map<String, Any?>?>,
    ) : UiRequest

    data class Screen(
        val title: String,
        val root: UiNode,
        val options: UiSheetOptions,
        val deferred: CompletableDeferred<Map<String, Any?>?>,
        val pages: Map<String, UiScreenPage> = emptyMap(),
    ) : UiRequest

    data class ShowTree(val title: String, val root: UiNode, val options: UiSheetOptions) : UiRequest

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
 *
 * 三条运行期纪律（见 `docs/lua-card-development.md` §3.5）：
 *
 * 1. **宿主不在场就立刻降级**：Compose 层没挂载（后台、桌面、纯工具调用）时不再傻等
 *    [timeoutMillis]，先给 [HOST_ATTACH_GRACE_MS] 的挂载宽限，仍不在场就返回 `null`。
 * 2. **超时不等于用户取消**：等待超时后弹层**保持可见**并标记为过期，脚本按「没拿到值」
 *    继续跑；用户之后提交的值经 [onLateAnswer] 交给宿主续跑，不会丢。
 * 3. **按卡片隔离迟到提交**：请求在 [withinCard] 上下文里携带卡片名，续跑时宿主知道
 *    该拿哪张卡片的参数去重跑。
 */
class UiBridgeHost(
    private val timeoutMillis: Long = 300_000,
    private val onAutomationStatus: (String, String) -> Unit = { _, _ -> },
    private val onCancelled: () -> Boolean = { false },
) : UiBridge {
    companion object {
        /** 宿主挂载宽限：运行开始到 Compose 首次组合之间允许的最大等待。 */
        const val HOST_ATTACH_GRACE_MS = 1_500L

        /** 脚本线程的卡片名上下文，由 [ScopedUiBridge]（`BridgeContextDecorator`）写入。 */
        internal val cardNameContext = ThreadLocal<String?>()

        internal fun <T> withinCard(cardName: String, block: () -> T): T {
            val previous = cardNameContext.get()
            cardNameContext.set(cardName)
            return try {
                block()
            } finally {
                if (previous == null) cardNameContext.remove() else cardNameContext.set(previous)
            }
        }

        private val waitBudget = ThreadLocal<Long>()
        internal fun <T> withinBudget(millis: Long, block: () -> T): T {
            val previous = waitBudget.get()
            waitBudget.set(millis)
            return try { block() } finally {
                if (previous == null) waitBudget.remove() else waitBudget.set(previous)
            }
        }
    }

    private val _request = MutableStateFlow<UiRequest?>(null)
    val request: StateFlow<UiRequest?> = _request.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Float, String>?>(null)
    val progress: StateFlow<Pair<Float, String>?> = _progress.asStateFlow()

    private val _shown = MutableStateFlow<Map<String, Any?>?>(null)
    val shown: StateFlow<Map<String, Any?>?> = _shown.asStateFlow()

    /** 宿主 Compose 层是否在场；[KhatKitUiHost] 在组合期挂载/卸载。 */
    private val _hostAttached = MutableStateFlow(false)
    val hostAttached: StateFlow<Boolean> = _hostAttached.asStateFlow()

    /** 累计等待超时次数（宿主用它判断一次运行里是否出现过「用户还在操作」）。 */
    private val _timeouts = MutableStateFlow(0L)
    val timeouts: StateFlow<Long> = _timeouts.asStateFlow()

    private val requestCards = ConcurrentHashMap<UiRequest, String>()
    private val expired: MutableSet<UiRequest> = Collections.newSetFromMap(ConcurrentHashMap<UiRequest, Boolean>())

    /**
     * 迟到的用户提交：脚本早已超时返回，但弹层还开着，用户之后才点提交。
     *
     * 宿主在这里续跑（重新执行卡片、用 [ReplayUiBridge] 把值回放给脚本）并把结果补发回会话；
     * [LateUiAnswer.cancelled] 为 true 表示用户取消。未接线时（无人值守 / 测试）直接丢弃。
     */
    var onLateAnswer: ((cardName: String, answer: LateUiAnswer) -> Unit)? = null

    fun onHostAttached() {
        _hostAttached.value = true
    }

    fun onHostDetached() {
        _hostAttached.value = false
    }

    /** 宿主宽限等待：不在场时不阻塞脚本，返回 false。 */
    private fun awaitHostAttached(): Boolean {
        if (_hostAttached.value) return true
        return runBlocking {
            withTimeoutOrNull(HOST_ATTACH_GRACE_MS) {
                _hostAttached.first { it }
                true
            } ?: false
        }
    }

    /**
     * 挂起等待一次弹层回传。
     *
     * @return 用户提交的值；`null` 表示取消、宿主不在场或等待超时（后两者由调用侧区分：
     *         超时会把请求标记为过期，用户晚到的提交走 [onLateAnswer]）。
     */
    private fun <T> awaitRequest(
        request: UiRequest,
        deferred: CompletableDeferred<T>,
        budget: Long,
    ): T? {
        requestCards[request] = cardNameContext.get().orEmpty()
        if (!awaitHostAttached()) {
            deferred.cancel()
            requestCards.remove(request)
            return null
        }
        _request.value = request
        Logger.getLogger("KKPerf").fine("post:${request.javaClass.simpleName} @${System.nanoTime() / 1_000_000}")
        val result = runBlocking { withTimeoutOrNull(budget) { deferred.await() } }
        if (result == null && !deferred.isCompleted && _request.value === request) {
            // 超时，但弹层仍在屏幕上：标记过期，等用户之后提交时续跑
            expired += request
            _timeouts.value += 1
        } else {
            // 操作屏（Screen）在提交后继续留在组合里：脚本下一次 ui.screen 直接替换内容，
            // 不再「关掉再重开」。运行结束时由 [endScreen] 收掉。
            if (request !is UiRequest.Screen) {
                _request.compareAndSet(request, null)
                requestCards.remove(request)
            }
            if (!deferred.isCompleted) deferred.cancel()
        }
        return result
    }

    override fun form(
        title: String,
        items: Any,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        val request = if (items is Map<*, *>) {
            UiRequest.FormTree(title, UiNodeParser.parse(items), UiSheetOptions.from(options), deferred)
        } else {
            require(items is List<*>) { "ui.form: expected node tree or legacy items" }
            val legacy = items.map {
                require(it is Map<*, *> && it.keys.all { key -> key is String }) { "ui.form: invalid legacy item" }
                require(it["type"] in heizige.kk.khatkit.card.UiWidgetVocabulary.LEGACY) { "ui.form: unknown legacy type ${it["type"]}" }
                @Suppress("UNCHECKED_CAST")
                (it as Map<String, Any?>)
            }
            java.util.logging.Logger.getLogger("KhatKit.UI").warning("ui.form(items) is deprecated; use a node tree")
            UiRequest.Form(title, legacy, UiSheetOptions.from(options), deferred)
        }
        return awaitRequest(request, deferred, budget())
    }

    override fun screen(title: String, root: Map<String, Any?>, options: Map<String, Any?>?): Map<String, Any?>? {
        Logger.getLogger("KKPerf").fine("screen.entry @${System.nanoTime() / 1_000_000}")
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        val parsed = UiNodeParser.parse(root)
        val sheetOptions = UiSheetOptions.from(options)
        val pages = UiScreenPage.parse(options, sheetOptions.navigation)
        Logger.getLogger("KKPerf").fine("screen.parsed @${System.nanoTime() / 1_000_000}")
        return awaitRequest(
            UiRequest.Screen(title, parsed, sheetOptions, deferred, pages),
            deferred,
            budget(),
        )
    }

    override fun sheet(
        title: String,
        actions: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        return awaitRequest(
            UiRequest.Sheet(title = title, actions = actions, options = UiSheetOptions.from(options), deferred = deferred),
            deferred,
            budget(),
        )
    }

    override fun webSheet(
        title: String,
        url: String,
        actions: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? {
        val deferred = CompletableDeferred<Map<String, Any?>?>()
        return awaitRequest(
            UiRequest.Sheet(
                title = title,
                url = url,
                actions = actions,
                options = UiSheetOptions.from(options),
                deferred = deferred,
                isWeb = true,
            ),
            deferred,
            budget(),
        )
    }

    override fun confirm(title: String, message: String, danger: Boolean): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        return awaitRequest(UiRequest.Confirm(title, message, danger, deferred), deferred, budget()) ?: false
    }

    /** 本次等待预算：运行期限与宿主上限取小。 */
    private fun budget(): Long = minOf(timeoutMillis, waitBudget.get() ?: Long.MAX_VALUE)

    override fun progress(ratio: Float, label: String) {
        _progress.value = ratio.coerceIn(0f, 1f) to label
        // 进度文字同步到自动化看板（宿主未接线时为 no-op）
        if (label.isNotBlank()) onAutomationStatus(label, "")
    }

    override fun show(card: Map<String, Any?>, options: Map<String, Any?>?) {
        if (card.containsKey("__ui")) {
            val root = UiNodeParser.parse(card)
            _shown.value = card
            _request.value = UiRequest.ShowTree(options?.get("title") as? String ?: "", root, UiSheetOptions.from(options))
            return
        }
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
        val current = _request.value ?: return
        val collected = when (current) {
            is UiRequest.Form -> values
            is UiRequest.FormTree -> {
                if (values != null && current.root.errors(values).isNotEmpty()) return
                values?.let(current.root::collect)
            }
            else -> return
        }
        if (expired.remove(current)) {
            // 脚本已按超时继续；这次提交属于「迟到」，交给宿主续跑
            _request.compareAndSet(current, null)
            dispatchLate(current, collected)
            return
        }
        when (current) {
            is UiRequest.Form -> current.deferred.complete(collected)
            is UiRequest.FormTree -> current.deferred.complete(collected)
            else -> return
        }
        _request.compareAndSet(current, null)
        requestCards.remove(current)
    }

    fun selectSheetAction(event: String, values: Map<String, Any?> = emptyMap()) {
        val current = _request.value as? UiRequest.Sheet ?: return
        val result = mapOf("event" to event, "values" to values)
        _request.value = null
        if (expired.remove(current)) {
            dispatchLate(current, result)
            return
        }
        current.deferred.complete(result)
        requestCards.remove(current)
    }

    fun submitScreen(event: String, values: Map<String, Any?>) {
        Logger.getLogger("KKPerf").fine("click:$event @${System.nanoTime() / 1_000_000}")
        val screen = _request.value as? UiRequest.Screen ?: return
        val pageId = values[UiScreenPage.PAGE_VALUE_KEY] as? String
        val page = if (screen.pages.isEmpty()) null else screen.pages[pageId] ?: return
        val activeRoot = page?.root ?: screen.root
        if (activeRoot.errors(values).isNotEmpty()) return
        // 事件必须真的来自这张树：Button / IconButton 的 action，或底部导航条目的 event。
        // 只认 Button 会把 IconButton（刷新 / 外链）和 nav 全吞掉 —— 点下去毫无反应。
        val fromNode = activeRoot.walk().any {
            (it.type == "Button" || it.type == "IconButton" || it.type == "CounterButton") &&
                it.props["action"] == event && it.props["disabled"] != true &&
                (it.type != "CounterButton" || ((values[it.props["id"]] as? Number)?.toInt() ?: 0) in 1..10)
        }
        val fromNav = screen.options.navigation?.items
            ?.any { it.event == event && it.enabled } == true
        val fromTopBar = screen.options.topActions.any { it.event == event }
        val fromLoadMore = (page?.loadMoreEvent ?: if (page == null) screen.options.loadMoreEvent else null) == event
        if (!fromNode && !fromNav && !fromTopBar && !fromLoadMore) return
        val result = mapOf("event" to event, "values" to activeRoot.collect(values)) +
            if (page != null) mapOf("page" to pageId) else emptyMap()
        if (expired.remove(screen)) {
            dispatchLate(screen, result)
            return
        }
        screen.deferred.complete(result)
    }

    /**
     * 一次运行结束时收掉「提交后继续显示」的操作屏。
     *
     * 保留过期屏（用户超时后迟到的提交还要靠它续跑），其余直接清空。
     */
    fun endScreen() {
        val current = _request.value as? UiRequest.Screen ?: return
        if (current in expired) return
        _request.compareAndSet(current, null)
        requestCards.remove(current)
    }

    fun answerConfirm(confirmed: Boolean) {
        val current = _request.value as? UiRequest.Confirm ?: return
        _request.value = null
        if (expired.remove(current)) {
            dispatchLate(current, mapOf("confirmed" to confirmed))
            return
        }
        current.deferred.complete(confirmed)
        requestCards.remove(current)
    }

    fun dismiss() {
        when (val current = _request.value) {
            is UiRequest.Form -> current.deferred.complete(null)
            is UiRequest.FormTree -> current.deferred.complete(null)
            is UiRequest.Screen -> current.deferred.complete(null)
            is UiRequest.Confirm -> current.deferred.complete(false)
            is UiRequest.Sheet -> current.deferred.complete(null)
            else -> Unit
        }
        val current = _request.value
        _request.value = null
        if (current != null && expired.remove(current)) {
            dispatchLate(current, null)
        } else if (current != null) {
            requestCards.remove(current)
        }
    }

    /** 迟到提交交给宿主续跑；`values == null` 表示用户取消。 */
    private fun dispatchLate(request: UiRequest, values: Map<String, Any?>?) {
        val card = requestCards.remove(request).orEmpty()
        val kind = when (request) {
            is UiRequest.Form, is UiRequest.FormTree -> UiAnswerKind.FORM
            is UiRequest.Screen -> UiAnswerKind.SCREEN
            is UiRequest.Sheet -> UiAnswerKind.SHEET
            is UiRequest.Confirm -> UiAnswerKind.CONFIRM
            else -> return
        }
        onLateAnswer?.invoke(card, LateUiAnswer(kind, values))
    }
}
