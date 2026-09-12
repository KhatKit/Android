package heizige.kk.khatkit.bridge.impl

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import heizige.kk.khatkit.bridge.AccessibilityBridge

/**
 * 无障碍服务实例的宿主侧注册表。
 *
 * 服务连上时由 app 的 `KhatKitAccessibilityService` 注册，断开时注销；
 * [BridgeFactory] 按注册结果决定是否挂 `accessibility` bridge。
 */
object AccessibilityBridgeHolder {
    @Volatile
    private var bridge: AccessibilityBridgeImpl? = null

    fun attach(impl: AccessibilityBridgeImpl) {
        bridge = impl
    }

    fun detach() {
        bridge = null
    }

    fun current(): AccessibilityBridgeImpl? = bridge
}

/**
 * 基于当前已连接 [AccessibilityService] 的 L1 自动化实现。
 *
 * 所有节点查询都是广度优先 + 上限保护，避免大列表窗口把脚本拖死。
 */
class AccessibilityBridgeImpl(
    private val service: AccessibilityService,
) : AccessibilityBridge {

    /** 前台包名由 app 的 Service 在 TYPE_WINDOW_STATE_CHANGED 时更新。 */
    @Volatile
    var foregroundPackage: String? = null

    override fun isAvailable(): Boolean = true

    override fun currentPackage(): String? =
        foregroundPackage ?: service.rootInActiveWindow?.packageName?.toString()

    override fun dumpWindow(): List<Map<String, Any?>> {
        val root = service.rootInActiveWindow ?: return emptyList()
        return collect(root) { true }
    }

    override fun findNodes(query: Map<String, Any?>): List<Map<String, Any?>> {
        val root = service.rootInActiveWindow ?: return emptyList()
        return collect(root) { node -> matches(node, query) }
    }

    override fun click(query: Map<String, Any?>): Boolean {
        val node = firstMatch(query) ?: return false
        val target = clickableTarget(node) ?: node
        if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        return tap(bounds.exactCenterX(), bounds.exactCenterY())
    }

    override fun longClick(query: Map<String, Any?>): Boolean {
        val node = firstMatch(query) ?: return false
        val target = clickableTarget(node) ?: node
        if (target.isLongClickable && target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            return true
        }
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        return gesture(
            Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) },
            durationMs = 600L,
        )
    }

    override fun setText(query: Map<String, Any?>, text: String): Boolean {
        val node = firstMatch(query) ?: return false
        val target = editableTarget(node) ?: return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    override fun tap(x: Float, y: Float): Boolean =
        gesture(Path().apply { moveTo(x, y) }, durationMs = 60L)

    override fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean =
        gesture(
            Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            },
            durationMs = durationMs.coerceIn(50L, 5_000L),
        )

    override fun scroll(direction: String): Boolean {
        val dir = direction.lowercase()
        val action = when (dir) {
            "forward", "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "backward", "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "left", "right" -> null
            else -> return false
        }
        if (action == null) return gestureScroll(dir)
        val root = service.rootInActiveWindow ?: return false
        val scrollable = firstNode(root) { it.isScrollable } ?: return gestureScroll(dir)
        return scrollable.performAction(action)
    }

    override fun globalAction(action: String): Boolean {
        val id = when (action.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        return service.performGlobalAction(id)
    }

    override fun openApp(packageName: String): Boolean {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            service.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    // ---- 内部：遍历 / 匹配 / 手势 ----

    private fun collect(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (predicate(node)) {
                result.add(nodeToMap(node, depth))
                if (result.size >= MAX_RESULTS) break
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                queue.add(child to depth + 1)
            }
        }
        return result
    }

    private fun firstMatch(query: Map<String, Any?>): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return firstNode(root) { matches(it, query) }
    }

    private fun firstNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        var visited = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (predicate(node)) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return null
    }

    private fun matches(node: AccessibilityNodeInfo, query: Map<String, Any?>): Boolean {
        val exact = query["exact"] == true
        val text = query["text"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (exact) node.text?.toString() == it else node.text?.toString()?.contains(it, ignoreCase = true) == true
        }
        if (text == false) return false
        val desc = query["desc"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            val value = node.contentDescription?.toString().orEmpty()
            if (exact) value == it else value.contains(it, ignoreCase = true)
        }
        if (desc == false) return false
        val viewId = query["viewId"]?.toString()?.takeIf { it.isNotBlank() }?.let { id ->
            val value = node.viewIdResourceName.orEmpty()
            value == id || value.substringAfterLast('/') == id
        }
        if (viewId == false) return false
        val className = query["className"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            node.className?.toString()?.contains(it, ignoreCase = true) == true
        }
        if (className == false) return false
        if (query["clickable"] == true && !node.isClickable && clickableTarget(node) == null) return false
        if (query["editable"] == true && !node.isEditable) return false
        if (query["scrollable"] == true && !node.isScrollable) return false
        return text != null || desc != null || viewId != null || className != null ||
            query["clickable"] == true || query["editable"] == true || query["scrollable"] == true
    }

    private fun clickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < MAX_PARENT_HOPS) {
            if (current.isClickable) return current
            current = current.parent
            hops++
        }
        return null
    }

    private fun editableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < MAX_PARENT_HOPS) {
            if (current.isEditable) return current
            current = current.parent
            hops++
        }
        return null
    }

    private fun nodeToMap(node: AccessibilityNodeInfo, depth: Int): Map<String, Any?> {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return buildMap {
            put("depth", depth)
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { put("text", it.take(MAX_TEXT)) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
                put("desc", it.take(MAX_TEXT))
            }
            node.viewIdResourceName?.let { put("viewId", it) }
            node.className?.toString()?.let { put("className", it) }
            node.packageName?.toString()?.let { put("packageName", it) }
            put("bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
            put("centerX", bounds.exactCenterX().toInt())
            put("centerY", bounds.exactCenterY().toInt())
            put("clickable", node.isClickable)
            put("longClickable", node.isLongClickable)
            put("editable", node.isEditable)
            put("scrollable", node.isScrollable)
            put("enabled", node.isEnabled)
            put("focused", node.isFocused)
            put("selected", node.isSelected)
        }
    }

    private fun gesture(path: Path, durationMs: Long): Boolean {
        return runCatching {
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            service.dispatchGesture(
                GestureDescription.Builder().addStroke(stroke).build(),
                null,
                null,
            )
        }.getOrDefault(false)
    }

    private fun gestureScroll(direction: String): Boolean {
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        return when (direction.lowercase()) {
            "forward", "up" -> swipe(width / 2f, height * 0.7f, width / 2f, height * 0.3f, 300L)
            "backward", "down" -> swipe(width / 2f, height * 0.3f, width / 2f, height * 0.7f, 300L)
            "right" -> swipe(width * 0.3f, height / 2f, width * 0.7f, height / 2f, 300L)
            "left" -> swipe(width * 0.7f, height / 2f, width * 0.3f, height / 2f, 300L)
            else -> false
        }
    }

    private companion object {
        const val MAX_NODES = 600
        const val MAX_RESULTS = 80
        const val MAX_PARENT_HOPS = 12
        const val MAX_TEXT = 512
    }
}
