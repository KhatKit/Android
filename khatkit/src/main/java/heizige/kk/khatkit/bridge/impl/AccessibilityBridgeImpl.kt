package heizige.kk.khatkit.bridge.impl

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import heizige.kk.khatkit.bridge.AccessibilityBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    override fun waitForNode(query: Map<String, Any?>, timeoutMs: Long): Map<String, Any?>? {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(0L, 120_000L)
        while (true) {
            findNodes(query).firstOrNull()?.let { return it }
            if (System.currentTimeMillis() >= deadline) return null
            Thread.sleep(150)
        }
    }

    override fun press(x: Float, y: Float, durationMs: Long): Boolean =
        gesture(Path().apply { moveTo(x, y) }, durationMs.coerceIn(100L, 10_000L))

    override fun gesture(strokesJson: String): Boolean {
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
        val builder = GestureDescription.Builder()
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

    override fun waitForIdle(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(0L, 120_000L)
        var lastHash = 0
        var stable = 0
        while (true) {
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
    }

    override fun waitForPackage(packageName: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(0L, 120_000L)
        while (true) {
            if (currentPackage() == packageName) return true
            if (System.currentTimeMillis() >= deadline) return false
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    override fun captureScreen(outputPath: String?): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "截图失败：需要 Android 11（API 30）及以上系统"
        }
        val target = outputPath?.takeIf { it.isNotBlank() }
            ?: "/sdcard/Download/KhatKit/screenshot_${System.currentTimeMillis()}.png"
        return try {
            requireSharedStorageAccess(target)
            val outcome = captureBitmap()
            val bitmap = outcome.bitmap ?: return outcome.error ?: "截图失败：未知错误"
            try {
                val file = File(target)
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file.absolutePath
            } catch (e: Exception) {
                "截图保存失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                bitmap.recycle()
            }
        } catch (e: SecurityException) {
            "无共享存储访问权限：$target\n请在 KhatKit 卡片市场 → 设置里授予「所有文件访问」"
        }
    }

    override fun findImage(templatePath: String, threshold: Double): String {
        val file = File(templatePath)
        if (!file.isFile) return errorJson("模板图片不存在：$templatePath")
        val template = try {
            BitmapFactory.decodeFile(templatePath)
        } catch (e: Exception) {
            null
        } ?: return errorJson("模板图片无法解码：$templatePath")
        val outcome = captureBitmap()
        val screen = outcome.bitmap ?: return errorJson(outcome.error ?: "截图失败：未知错误")
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

    override fun tapImage(templatePath: String, threshold: Double, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(0L, MAX_WAIT_MS)
        while (true) {
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
    }

    override fun findColor(colorHex: String, tolerance: Int, region: String): String {
        val target = parseColorHex(colorHex) ?: return errorJson("颜色格式错误：$colorHex（应为 #RRGGBB）")
        val toleranceValue = tolerance.coerceIn(0, 255)
        val outcome = captureBitmap()
        val screen = outcome.bitmap ?: return errorJson(outcome.error ?: "截图失败：未知错误")
        return try {
            val area = parseRegion(region, screen.width, screen.height)
                ?: return errorJson("region 格式错误：$region（应为 x,y,w,h）")
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

    private data class CaptureOutcome(val bitmap: Bitmap?, val error: String?)

    /** 通过无障碍 takeScreenshot 拿到屏幕位图的软件副本（ARGB_8888，可读像素）。 */
    private fun captureBitmap(): CaptureOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return CaptureOutcome(null, "截图失败：需要 Android 11（API 30）及以上系统")
        }
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        var error: String? = null
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val buffer = result.hardwareBuffer
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            buffer.close()
                            if (hardware == null) {
                                error = "截图失败：无法解码屏幕图像"
                            } else {
                                bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
                                hardware.recycle()
                            }
                        } catch (e: Exception) {
                            error = "截图失败：${e.message ?: e.javaClass.simpleName}"
                        } finally {
                            latch.countDown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        error = "截图失败（系统错误码 $errorCode）"
                        latch.countDown()
                    }
                },
            )
        } catch (e: Exception) {
            return CaptureOutcome(null, "截图失败：${e.message ?: e.javaClass.simpleName}")
        }
        if (!latch.await(SCREENSHOT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            return CaptureOutcome(null, "截图失败：等待系统回调超时")
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
        val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val target = editableTarget(focused) ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun windowHash(): Int {
        val root = service.rootInActiveWindow ?: return 0
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

    private fun gesture(path: Path, durationMs: Long): Boolean =
        dispatch(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
                .build()
        )

    private fun dispatch(description: GestureDescription): Boolean =
        runCatching { service.dispatchGesture(description, null, null) }.getOrDefault(false)

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
