package heizige.kk.khatkit.bridge.impl

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import heizige.kk.khatkit.R
import heizige.kk.khatkit.bridge.AccessibilityBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

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

    private data class VirtualDisplayTarget(
        val displayId: Int,
        val packageName: String,
        val globalAction: (String) -> Boolean,
    )

    /** Identity is an operation generation, including transitions back to the main display. */
    private class DisplayRoute(val target: VirtualDisplayTarget?)

    private val displayRoute = AtomicReference(DisplayRoute(null))
    private val operationRoute = ThreadLocal<DisplayRoute>()

    private val currentRoute: DisplayRoute
        get() = operationRoute.get() ?: displayRoute.get()

    val targetDisplayId: Int?
        get() = currentRoute.target?.displayId

    fun useVirtualDisplay(displayId: Int, packageName: String, globalAction: (String) -> Boolean) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        require(displayId > Display.DEFAULT_DISPLAY)
        displayRoute.set(DisplayRoute(VirtualDisplayTarget(displayId, packageName, globalAction)))
    }

    fun clearVirtualDisplay(displayId: Int? = null) {
        while (true) {
            val previous = displayRoute.get()
            if (displayId != null && previous.target?.displayId != displayId) return
            if (displayRoute.compareAndSet(previous, DisplayRoute(null))) {
                foregroundPackage = null
                return
            }
        }
    }

    /** Nested operations retain the original display even if a session stops between steps. */
    private inline fun <T> withDisplayRoute(block: () -> T): T {
        if (operationRoute.get() != null) return block()
        operationRoute.set(displayRoute.get())
        return try {
            block()
        } finally {
            operationRoute.remove()
        }
    }

    private fun routeIsCurrent(): Boolean = currentRoute === displayRoute.get()

    /** 无障碍服务上下文的 WindowManager，addView 后窗口带 `TYPE_ACCESSIBILITY_OVERLAY` token。 */
    private val overlayWindowManager: WindowManager
        get() = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override fun isAvailable(): Boolean = true

    override fun currentPackage(): String? = withDisplayRoute {
        if (!routeIsCurrent()) null
        else if (targetDisplayId != null) activeRoot()?.packageName?.toString()
        else foregroundPackage ?: activeRoot()?.packageName?.toString()
    }

    private fun activeRoot(): AccessibilityNodeInfo? {
        if (!routeIsCurrent()) return null
        val target = currentRoute.target ?: return service.rootInActiveWindow
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val windows = service.getWindowsOnAllDisplays().get(target.displayId).orEmpty()
        val roots = windows.mapNotNull { it.root }
        return roots.firstOrNull { it.packageName?.toString() == target.packageName }
            ?: (windows.firstOrNull { it.isActive } ?: windows.firstOrNull { it.isFocused })?.root
    }

    override fun dumpWindow(): List<Map<String, Any?>> {
        return withDisplayRoute {
            val root = activeRoot() ?: return emptyList()
            return collect(root) { true }
        }
    }

    override fun findNodes(query: Map<String, Any?>): List<Map<String, Any?>> {
        return withDisplayRoute {
            val root = activeRoot() ?: return emptyList()
            return collect(root) { node -> matches(node, query) }
        }
    }

    override fun click(query: Map<String, Any?>): Boolean {
        return withDisplayRoute {
            val node = firstMatch(query) ?: return false
            val target = clickableTarget(node) ?: node
            if (!routeIsCurrent()) return false
            if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            return tap(bounds.exactCenterX(), bounds.exactCenterY())
        }
    }

    override fun longClick(query: Map<String, Any?>): Boolean {
        return withDisplayRoute {
            val node = firstMatch(query) ?: return false
            val target = clickableTarget(node) ?: node
            if (!routeIsCurrent()) return false
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
    }

    override fun setText(query: Map<String, Any?>, text: String): Boolean {
        return withDisplayRoute {
            val node = firstMatch(query) ?: return false
            val target = editableTarget(node) ?: return false
            if (!routeIsCurrent()) return false
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return routeIsCurrent() && target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
    }

    override fun tap(x: Float, y: Float): Boolean = withDisplayRoute {
        gesture(Path().apply { moveTo(x, y) }, durationMs = 60L)
    }

    override fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean = withDisplayRoute {
        gesture(
            Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            },
            durationMs = durationMs.coerceIn(50L, 5_000L),
        )
    }

    override fun scroll(direction: String): Boolean {
        return withDisplayRoute {
            val dir = direction.lowercase()
            val action = when (dir) {
                "forward", "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                "backward", "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                "left", "right" -> null
                else -> return false
            }
            if (action == null) return gestureScroll(dir)
            val root = activeRoot() ?: return false
            val scrollable = firstNode(root) { it.isScrollable } ?: return gestureScroll(dir)
            return routeIsCurrent() && scrollable.performAction(action)
        }
    }

    override fun globalAction(action: String): Boolean {
        return withDisplayRoute {
            if (!routeIsCurrent()) return false
            currentRoute.target?.let { target ->
                return target.globalAction(action.lowercase())
            }
            val id = when (action.lowercase()) {
                "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                else -> return false
            }
            return routeIsCurrent() && service.performGlobalAction(id)
        }
    }

    override fun openApp(packageName: String): Boolean {
        return withDisplayRoute {
            val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!routeIsCurrent()) return false
            return runCatching {
                val displayId = targetDisplayId
                if (displayId == null) {
                    service.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
                    service.startActivity(intent, options)
                }
                true
            }.getOrDefault(false)
        }
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

    override fun waitForNode(query: Map<String, Any?>, timeoutMs: Long): Map<String, Any?>? {
        return withDisplayRoute {
            val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(0L, 120_000L)
            while (routeIsCurrent()) {
                findNodes(query).firstOrNull()?.let { return it }
                if (System.currentTimeMillis() >= deadline) return null
                Thread.sleep(150)
            }
            null
        }
    }

    override fun press(x: Float, y: Float, durationMs: Long): Boolean = withDisplayRoute {
        gesture(Path().apply { moveTo(x, y) }, durationMs.coerceIn(100L, 10_000L))
    }

    override fun gesture(strokesJson: String): Boolean {
        return withDisplayRoute {
            val strokes = try {
                JSONArray(strokesJson)
            } catch (e: Exception) {
                throw IllegalArgumentException("手势 JSON 解析失败：${e.message ?: "格式错误"}")
            }
            if (strokes.length() == 0) return false
            val maxStrokes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                GestureDescription.getMaxStrokeCount()
            } else {
                FALLBACK_MAX_STROKES
            }
            val builder = gestureBuilder()
            var added = 0
            for (i in 0 until strokes.length()) {
                if (added >= maxStrokes) break
                val points = strokes.optJSONArray(i) ?: continue
                val path = Path()
                var startMs = 0L
                var endMs = 0L
                for (j in 0 until points.length()) {
                    val point = points.optJSONObject(j) ?: continue
                    val px = point.optDouble("x", Double.NaN).toFloat()
                    val py = point.optDouble("y", Double.NaN).toFloat()
                    if (px.isNaN() || py.isNaN()) continue
                    val offset = point.optLong("t", endMs).coerceAtLeast(0L)
                    if (path.isEmpty) {
                        path.moveTo(px, py)
                        startMs = offset
                        endMs = offset
                    } else {
                        path.lineTo(px, py)
                        endMs = offset.coerceAtLeast(startMs)
                    }
                }
                if (path.isEmpty || startMs > MAX_GESTURE_MS - MIN_GESTURE_MS) continue
                val duration = (endMs - startMs).coerceIn(MIN_GESTURE_MS, MAX_GESTURE_MS - startMs)
                builder.addStroke(GestureDescription.StrokeDescription(path, startMs, duration))
                added++
            }
            if (added == 0) return false
            return dispatch(builder.build())
        }
    }

    override fun waitForIdle(timeoutMs: Long): Boolean {
        return withDisplayRoute {
            val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(0L, 120_000L)
            var lastHash = 0
            var stable = 0
            while (routeIsCurrent()) {
                val hash = windowHash()
                if (hash != 0 && hash == lastHash) {
                    stable++
                    if (stable >= 2) return true
                } else {
                    stable = 0
                    lastHash = hash
                }
                if (System.currentTimeMillis() >= deadline) return false
                Thread.sleep(POLL_INTERVAL_MS)
            }
            false
        }
    }

    override fun waitForPackage(packageName: String, timeoutMs: Long): Boolean {
        return withDisplayRoute {
            val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(0L, 120_000L)
            while (routeIsCurrent()) {
                if (currentPackage() == packageName) return true
                if (System.currentTimeMillis() >= deadline) return false
                Thread.sleep(POLL_INTERVAL_MS)
            }
            false
        }
    }

    override fun captureScreen(outputPath: String?): String {
        return withDisplayRoute {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return service.getString(R.string.khatkit_accessibility_capture_android_required)
            }
            val target = outputPath?.takeIf { it.isNotBlank() }
                ?: "/sdcard/Download/KhatKit/screenshot_${System.currentTimeMillis()}.png"
            return try {
                requireSharedStorageAccess(target)
                val outcome = captureBitmap()
                val bitmap = outcome.bitmap ?: return outcome.error
                    ?: service.getString(R.string.khatkit_accessibility_capture_unknown)
                try {
                    val file = File(target)
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    file.absolutePath
                } catch (e: Exception) {
                    service.getString(R.string.khatkit_accessibility_capture_save_failed, e.message ?: e.javaClass.simpleName)
                } finally {
                    bitmap.recycle()
                }
            } catch (e: SecurityException) {
                service.getString(R.string.khatkit_accessibility_storage_required, target)
            }
        }
    }

    override fun findImage(templatePath: String, threshold: Double): String {
        return withDisplayRoute {
            val file = File(templatePath)
            if (!file.isFile) return errorJson(service.getString(R.string.khatkit_accessibility_template_missing, templatePath))
            val template = try {
                BitmapFactory.decodeFile(templatePath)
            } catch (e: Exception) {
                null
            } ?: return errorJson(service.getString(R.string.khatkit_accessibility_template_invalid, templatePath))
            val outcome = captureBitmap()
            val screen = outcome.bitmap ?: return errorJson(
                outcome.error ?: service.getString(R.string.khatkit_accessibility_capture_unknown),
            )
            return try {
                val match = TemplateMatcher.find(screen, template)
                val limit = if (threshold > 0.0) threshold.coerceAtMost(1.0) else DEFAULT_IMAGE_THRESHOLD
                if (match == null || match.score < limit) {
                    JSONObject().put("found", false).toString()
                } else {
                    JSONObject()
                        .put("found", true)
                        .put("x", match.centerX.roundToInt())
                        .put("y", match.centerY.roundToInt())
                        .put("score", (match.score * 10_000).roundToInt() / 10_000.0)
                        .toString()
                }
            } finally {
                screen.recycle()
                template.recycle()
            }
        }
    }

    override fun tapImage(templatePath: String, threshold: Double, timeoutMs: Long): Boolean {
        return withDisplayRoute {
            val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(0L, MAX_WAIT_MS)
            while (routeIsCurrent()) {
                val tapped = runCatching {
                    val json = JSONObject(findImage(templatePath, threshold))
                    if (json.optBoolean("found")) {
                        tap(json.optDouble("x").toFloat(), json.optDouble("y").toFloat())
                    } else {
                        false
                    }
                }.getOrDefault(false)
                if (tapped) return true
                if (System.currentTimeMillis() >= deadline) return false
                Thread.sleep(IMAGE_POLL_INTERVAL_MS)
            }
            false
        }
    }

    override fun findColor(colorHex: String, tolerance: Int, region: String): String {
        return withDisplayRoute {
            val target = parseColorHex(colorHex) ?: return errorJson(
                service.getString(R.string.khatkit_accessibility_color_invalid, colorHex),
            )
            val toleranceValue = tolerance.coerceIn(0, 255)
            val outcome = captureBitmap()
            val screen = outcome.bitmap ?: return errorJson(
                outcome.error ?: service.getString(R.string.khatkit_accessibility_capture_unknown),
            )
            return try {
                val area = parseRegion(region, screen.width, screen.height)
                    ?: return errorJson(service.getString(R.string.khatkit_accessibility_region_invalid, region))
                val pixels = IntArray(area.width * area.height)
                screen.getPixels(pixels, 0, area.width, area.x, area.y, area.width, area.height)
                val targetR = (target shr 16) and 0xFF
                val targetG = (target shr 8) and 0xFF
                val targetB = target and 0xFF
                for (index in pixels.indices) {
                    val color = pixels[index]
                    if (abs(((color shr 16) and 0xFF) - targetR) <= toleranceValue &&
                        abs(((color shr 8) and 0xFF) - targetG) <= toleranceValue &&
                        abs((color and 0xFF) - targetB) <= toleranceValue
                    ) {
                        return JSONObject()
                            .put("found", true)
                            .put("x", area.x + index % area.width)
                            .put("y", area.y + index / area.width)
                            .put("color", "#" + Integer.toHexString(color and 0xFFFFFF).uppercase().padStart(6, '0'))
                            .toString()
                    }
                }
                JSONObject().put("found", false).toString()
            } finally {
                screen.recycle()
            }
        }
    }

    private data class CaptureOutcome(val bitmap: Bitmap?, val error: String?)

    /** 通过无障碍 takeScreenshot 拿到屏幕位图的软件副本（ARGB_8888，可读像素）。 */
    private fun captureBitmap(): CaptureOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return CaptureOutcome(null, service.getString(R.string.khatkit_accessibility_capture_android_required))
        }
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        var error: String? = null
        if (!routeIsCurrent()) return CaptureOutcome(null, service.getString(R.string.khatkit_accessibility_display_changed))
        val displayId = targetDisplayId ?: Display.DEFAULT_DISPLAY
        try {
            service.takeScreenshot(
                displayId,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val buffer = result.hardwareBuffer
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            buffer.close()
                            if (hardware == null) {
                                error = service.getString(R.string.khatkit_accessibility_capture_decode_failed)
                            } else {
                                bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
                                hardware.recycle()
                            }
                        } catch (e: Exception) {
                            error = service.getString(R.string.khatkit_accessibility_capture_failed, e.message ?: e.javaClass.simpleName)
                        } finally {
                            latch.countDown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        error = service.getString(R.string.khatkit_accessibility_capture_system_error, errorCode)
                        latch.countDown()
                    }
                },
            )
        } catch (e: Exception) {
            return CaptureOutcome(null, service.getString(R.string.khatkit_accessibility_capture_failed, e.message ?: e.javaClass.simpleName))
        }
        if (!latch.await(SCREENSHOT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            return CaptureOutcome(null, service.getString(R.string.khatkit_accessibility_capture_timeout))
        }
        if (!routeIsCurrent()) {
            bitmap?.recycle()
            return CaptureOutcome(null, service.getString(R.string.khatkit_accessibility_display_changed))
        }
        return CaptureOutcome(bitmap, error)
    }

    private fun errorJson(message: String): String = JSONObject().put("error", message).toString()

    private fun parseColorHex(value: String): Int? {
        val hex = value.trim().removePrefix("#")
        return when (hex.length) {
            6 -> hex.toIntOrNull(16)
            8 -> hex.substring(2).toIntOrNull(16)
            else -> null
        }
    }

    private data class Region(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun parseRegion(region: String, screenWidth: Int, screenHeight: Int): Region? {
        if (region.isBlank()) return Region(0, 0, screenWidth, screenHeight)
        val parts = region.split(',').map { it.trim().toIntOrNull() }
        if (parts.size != 4) return null
        val x = parts[0] ?: return null
        val y = parts[1] ?: return null
        val w = parts[2] ?: return null
        val h = parts[3] ?: return null
        if (w <= 0 || h <= 0) return null
        val left = x.coerceIn(0, screenWidth - 1)
        val top = y.coerceIn(0, screenHeight - 1)
        val width = w.coerceAtMost(screenWidth - left)
        val height = h.coerceAtMost(screenHeight - top)
        if (width <= 0 || height <= 0) return null
        return Region(left, top, width, height)
    }

    override fun paste(): Boolean {
        return withDisplayRoute {
            if (!routeIsCurrent()) return false
            val displayId = targetDisplayId
            val focused = if (displayId == null) {
                service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            } else {
                activeRoot()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            } ?: return false
            if (displayId != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
                if (focused.window?.displayId != displayId) return false
            }
            val target = editableTarget(focused) ?: return false
            return routeIsCurrent() && target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
    }

    override fun addOverlay(view: View, params: WindowManager.LayoutParams): Boolean = withDisplayRoute {
        if (targetDisplayId != null || !routeIsCurrent()) return@withDisplayRoute false
        runCatching {
            overlayWindowManager.addView(view, params)
            true
        }.getOrDefault(false)
    }

    override fun removeOverlay(view: View) {
        runCatching { overlayWindowManager.removeView(view) }
    }

    private fun windowHash(): Int {
        val root = activeRoot() ?: return 0
        var hash = 1
        var visited = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            hash = 31 * hash + node.text?.toString().hashCode()
            hash = 31 * hash + node.contentDescription?.toString().hashCode()
            hash = 31 * hash + node.viewIdResourceName.hashCode()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            hash = 31 * hash + bounds.hashCode()
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return hash
    }

    private fun requireSharedStorageAccess(path: String) {
        if (AllFilesAccess.isGranted()) return
        if (!path.startsWith("/sdcard") && !path.startsWith("/storage") && !path.startsWith("/mnt/sdcard")) return
        throw SecurityException(
            "无共享存储访问权限：$path\n" +
                "请在 KhatKit 卡片市场 → 设置里授予「所有文件访问」"
        )
    }

    private fun firstMatch(query: Map<String, Any?>): AccessibilityNodeInfo? {
        val root = activeRoot() ?: return null
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

    private fun gesture(path: Path, durationMs: Long): Boolean =
        dispatch(
            gestureBuilder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
                .build()
        )

    private fun gestureBuilder(): GestureDescription.Builder = GestureDescription.Builder().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            targetDisplayId?.let { setDisplayId(it) }
        }
    }

    private fun dispatch(description: GestureDescription): Boolean =
        routeIsCurrent() &&
            runCatching { service.dispatchGesture(description, null, null) }.getOrDefault(false)

    private fun gestureScroll(direction: String): Boolean {
        val displayId = targetDisplayId
        val metrics = if (displayId == null) {
            service.resources.displayMetrics
        } else {
            val display = service.getSystemService(DisplayManager::class.java)?.getDisplay(displayId)
                ?: return false
            service.createDisplayContext(display).resources.displayMetrics
        }
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
        const val FALLBACK_MAX_STROKES = 10
        const val MIN_GESTURE_MS = 50L
        const val MAX_GESTURE_MS = 60_000L
        const val POLL_INTERVAL_MS = 200L
        const val SCREENSHOT_TIMEOUT_SEC = 8L
        const val IMAGE_POLL_INTERVAL_MS = 300L
        const val MAX_WAIT_MS = 120_000L
        const val DEFAULT_IMAGE_THRESHOLD = 0.9
    }
}
