package heizige.kk.khatkit.record

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToLong

/**
 * 把录制的人工操作步骤生成 Lua 回放卡片（card.json + main.lua）。
 *
 * 纯函数：不触碰文件系统与 Android API，由宿主写进本地卡片缓存
 * （与卡片市场/触发器同一份目录），运行后按顺序回放点击/输入/滑动/开应用。
 */
object RecordingCardFactory {

    const val VERSION = "1.0.0"

    /** 单张卡片最多录制的步骤数。 */
    const val MAX_STEPS = 200

    /** 回放时的步骤间隔下限 / 上限（毫秒）。 */
    const val MIN_GAP_MS = 300L
    const val MAX_GAP_MS = 5_000L

    /** 等待控件出现 / 等待应用前台的超时。 */
    const val WAIT_NODE_TIMEOUT_MS = 3_000L
    const val WAIT_PACKAGE_TIMEOUT_MS = 5_000L

    /** 生成结果：card.json 与 main.lua 的正文。 */
    data class GeneratedRecordCard(
        val name: String,
        val version: String = VERSION,
        val manifestJson: String,
        val scriptText: String,
    )

    fun create(
        title: String,
        steps: List<RecordedStep>,
        nowMillis: Long = System.currentTimeMillis(),
    ): GeneratedRecordCard {
        val safeTitle = title.ifBlank { "录制操作" }
        val name = buildName(safeTitle, nowMillis)
        return GeneratedRecordCard(
            name = name,
            manifestJson = buildManifestJson(name, safeTitle, steps.size).toString(),
            scriptText = buildLua(safeTitle, steps),
        )
    }

    /** 中文标题 → 合法小写下划线卡片名（与 name 正则 `^[a-z][a-z0-9_]{1,63}$` 兼容）。 */
    fun buildName(title: String, nowMillis: Long): String {
        val slug = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { "ops" }
        return "record_${slug}_${nowMillis.toString().takeLast(6)}"
    }

    /**
     * 步骤间隔 → `tool.sleep(秒)`。tool bridge 的 sleep 只接受整数秒（0–120），
     * 所以 1 秒以内的间隔不生成 sleep（由操作本身与 waitForNode 兜底）。
     */
    fun sleepLine(gapMs: Long): String? {
        val clamped = gapMs.coerceIn(MIN_GAP_MS, MAX_GAP_MS)
        if (clamped < 1_000L) return null
        val seconds = (clamped / 1000.0).roundToLong().coerceIn(1L, 5L)
        return "tool.sleep($seconds)"
    }

    fun buildManifestJson(name: String, title: String, stepCount: Int): JsonObject = buildJsonObject {
        put("name", name)
        put("version", VERSION)
        put("description", "录制回放：$title（$stepCount 步）")
        put("author", "user")
        put("license", "MIT")
        put("engine", "lua")
        putJsonObject("entry") { put("lua", "main.lua") }
        put("privilege", "elevated")
        putJsonObject("requires") {
            putJsonArray("bridges") {
                add("tool")
                add("ui")
                add("accessibility")
            }
            putJsonArray("libs") {}
            putJsonArray("bins") {}
            putJsonArray("env") {}
        }
        putJsonObject("network") { putJsonArray("allow") {} }
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {}
        }
        putJsonArray("triggers") { add("user") }
        putJsonObject("tags") {
            put("domain", "device")
            put("action", "control")
            put("scene", "daily")
        }
        putJsonObject("compliance") {
            put("risk", "medium")
            put("note", "回放录制的人工操作会点击/输入真实屏幕控件，请先确认步骤与文本无误")
        }
        putJsonObject("store") {
            put("quota_mb", 1)
            put("secret", false)
        }
    }

    fun buildLua(title: String, steps: List<RecordedStep>): String = buildString {
        appendLine("-- 录制回放卡片：$title")
        appendLine("-- 由 KhatKit「录制人工操作」生成，共 ${steps.size} 步；运行前请确认步骤无误")
        appendLine("local okAll = true")
        appendLine()
        steps.forEachIndexed { index, step ->
            val no = index + 1
            appendLine("-- 第 $no 步：${step.comment()}")
            appendStep(step, no)
            steps.getOrNull(index + 1)?.let { next ->
                sleepLine(next.atMillis - step.atMillis)?.let { appendLine(it) }
            }
            appendLine()
        }
        appendLine("return { ok = okAll, steps = ${steps.size} }")
    }

    private fun StringBuilder.appendStep(step: RecordedStep, no: Int) {
        when (step.type) {
            RecordedStep.Type.CLICK -> appendClick(step, no)
            RecordedStep.Type.SET_TEXT -> appendSetText(step, no)
            RecordedStep.Type.SWIPE -> appendSwipe(step, no)
            RecordedStep.Type.OPEN_APP -> appendOpenApp(step)
            RecordedStep.Type.WAIT -> appendLine("accessibility.waitForIdle($WAIT_NODE_TIMEOUT_MS)")
        }
    }

    private fun StringBuilder.appendClick(step: RecordedStep, no: Int) {
        val selector = step.selectorLiteral()
        when {
            selector != null -> {
                appendLine("accessibility.waitForNode($selector, $WAIT_NODE_TIMEOUT_MS)")
                appendLine("local ok$no = accessibility.click($selector)")
                if (step.hasCenter) {
                    appendLine("if not ok$no then")
                    appendLine("  -- 选择器失效时退回坐标点击")
                    appendLine("  ok$no = accessibility.tap(${step.x}, ${step.y})")
                    appendLine("end")
                }
            }

            step.hasCenter -> appendLine("local ok$no = accessibility.tap(${step.x}, ${step.y})")
            else -> appendLine("local ok$no = false")
        }
        appendLine("if not ok$no then okAll = false end")
    }

    private fun StringBuilder.appendSetText(step: RecordedStep, no: Int) {
        val selector = step.selectorLiteral()
        val text = luaString(step.inputText)
        if (selector != null) {
            appendLine("accessibility.waitForNode($selector, $WAIT_NODE_TIMEOUT_MS)")
            appendLine("local ok$no = accessibility.setText($selector, $text)")
            if (step.hasCenter) {
                appendLine("if not ok$no then")
                appendLine("  -- 控件失效时先点坐标聚焦再输入")
                appendLine("  accessibility.tap(${step.x}, ${step.y})")
                appendLine("  ok$no = accessibility.setText({ editable = true }, $text)")
                appendLine("end")
            }
        } else {
            if (step.hasCenter) appendLine("accessibility.tap(${step.x}, ${step.y})")
            appendLine("local ok$no = accessibility.setText({ editable = true }, $text)")
        }
        appendLine("if not ok$no then okAll = false end")
    }

    private fun StringBuilder.appendSwipe(step: RecordedStep, no: Int) {
        appendLine("local ok$no = accessibility.scroll(${luaString(step.direction)})")
        if (step.hasSwipePath) {
            appendLine("if not ok$no then")
            appendLine("  -- 滚动失败时按坐标滑动")
            appendLine("  ok$no = accessibility.swipe(${step.x}, ${step.y}, ${step.x2}, ${step.y2}, 300)")
            appendLine("end")
        }
        appendLine("if not ok$no then okAll = false end")
    }

    private fun StringBuilder.appendOpenApp(step: RecordedStep) {
        appendLine("accessibility.openApp(${luaString(step.packageName)})")
        appendLine("accessibility.waitForPackage(${luaString(step.packageName)}, $WAIT_PACKAGE_TIMEOUT_MS)")
    }

    /** viewId > text > desc > 坐标：返回 Lua 查询表字面量，全空时返回 null。 */
    private fun RecordedStep.selectorLiteral(): String? = when {
        viewId.isNotBlank() -> "{ viewId = ${luaString(viewId)} }"
        text.isNotBlank() -> "{ text = ${luaString(text)} }"
        desc.isNotBlank() -> "{ desc = ${luaString(desc)} }"
        else -> null
    }

    private fun luaString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
