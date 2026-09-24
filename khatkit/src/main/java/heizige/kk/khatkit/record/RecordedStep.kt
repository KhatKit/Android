package heizige.kk.khatkit.record

/**
 * 录制到的一步人工操作（纯数据，不依赖 Android，便于 JVM 单测）。
 *
 * 选择器优先级：viewId > text > contentDescription > 坐标（bounds 中心）。
 */
data class RecordedStep(
    val type: Type,
    /** 事件时间（uptimeMillis，来自 AccessibilityEvent.eventTime）。 */
    val atMillis: Long,
    /** 事件所属应用包名。 */
    val packageName: String = "",
    /** 控件 id（viewIdResourceName），最稳的选择器。 */
    val viewId: String = "",
    /** 控件文字（text）。 */
    val text: String = "",
    /** 控件描述（contentDescription）。 */
    val desc: String = "",
    /** 屏幕坐标（bounds 中心）：点击 / 输入失效时的兜底。 */
    val x: Int = 0,
    val y: Int = 0,
    /** 滑动终点坐标（swipe 兜底）。 */
    val x2: Int = 0,
    val y2: Int = 0,
    /** 滑动方向：up/down/left/right。 */
    val direction: String = "",
    /** 输入框最终文本（TYPE_VIEW_TEXT_CHANGED 去抖后的值）。 */
    val inputText: String = "",
) {
    enum class Type { CLICK, SET_TEXT, SWIPE, OPEN_APP, WAIT }

    val hasCenter: Boolean get() = x > 0 || y > 0

    val hasSwipePath: Boolean get() = (x > 0 || y > 0) && (x2 > 0 || y2 > 0)

    /** 是否指向同一控件（用于输入去抖）。 */
    fun sameTarget(other: RecordedStep): Boolean =
        packageName == other.packageName &&
            viewId == other.viewId &&
            text == other.text &&
            desc == other.desc

    /** 内容完全一致的连续重复步骤（用于去重）。 */
    fun isDuplicateOf(other: RecordedStep): Boolean =
        type == other.type &&
            sameTarget(other) &&
            direction == other.direction &&
            inputText == other.inputText &&
            x == other.x && y == other.y && x2 == other.x2 && y2 == other.y2

    /** 目标的中文描述，用于 Lua 注释与 UI 展示。 */
    fun targetLabel(): String = when {
        text.isNotBlank() -> text
        desc.isNotBlank() -> desc
        viewId.isNotBlank() -> viewId.substringAfterLast('/')
        hasCenter -> "($x, $y)"
        else -> "未知控件"
    }

    /** 一步的中文说明。 */
    fun comment(): String = when (type) {
        Type.CLICK -> "点击「${targetLabel()}」"
        Type.SET_TEXT -> "输入「$inputText」"
        Type.SWIPE -> "向${directionLabel()}滑动"
        Type.OPEN_APP -> "打开应用「$packageName」"
        Type.WAIT -> "等待界面稳定"
    }

    fun directionLabel(): String = when (direction) {
        "up" -> "上"
        "down" -> "下"
        "left" -> "左"
        "right" -> "右"
        else -> direction
    }
}
