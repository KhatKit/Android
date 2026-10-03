package heizige.kk.khatkit.record

/**
 * A4 统一动作原语：录制（人工）/ 回放 / Agent 生成三者同构。
 *
 * 是 [RecordedStep] 的超集：多出 press / global 动作与视觉 bbox 目标；
 * 通过 [toRecordedStep] / [fromRecordedStep] 与录制格式双向转换，
 * 保证录制的轨迹可被 Agent 复用、Agent 生成的动作可导出为录制卡。
 *
 * 目标选择器优先级（与 RecordedStep 一致）：viewId > text > desc > 坐标/bbox 中心。
 */
data class UiAction(
    val type: Type,
    val atMillis: Long = 0L,
    val packageName: String = "",
    val viewId: String = "",
    val text: String = "",
    val desc: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val x2: Int = 0,
    val y2: Int = 0,
    val direction: String = "",
    val inputText: String = "",
    val durationMs: Long = 0L,
    val globalAction: String = "",
    /** 视觉通道目标（多模态 bbox）；双通道校验后可提升为节点目标。 */
    val bbox: UiBBox? = null,
    /** 目标来源：human（录制）/ agent（模型直出）/ vision（视觉 bbox 解析后）。 */
    val origin: String = ORIGIN_AGENT,
) {
    enum class Type { CLICK, SET_TEXT, SWIPE, PRESS, OPEN_APP, WAIT, GLOBAL }

    companion object {
        const val ORIGIN_HUMAN = "human"
        const val ORIGIN_AGENT = "agent"
        const val ORIGIN_VISION = "vision"

        fun fromRecordedStep(step: RecordedStep): UiAction = UiAction(
            type = when (step.type) {
                RecordedStep.Type.CLICK -> Type.CLICK
                RecordedStep.Type.SET_TEXT -> Type.SET_TEXT
                RecordedStep.Type.SWIPE -> Type.SWIPE
                RecordedStep.Type.OPEN_APP -> Type.OPEN_APP
                RecordedStep.Type.WAIT -> Type.WAIT
            },
            atMillis = step.atMillis,
            packageName = step.packageName,
            viewId = step.viewId,
            text = step.text,
            desc = step.desc,
            x = step.x,
            y = step.y,
            x2 = step.x2,
            y2 = step.y2,
            direction = step.direction,
            inputText = step.inputText,
            origin = ORIGIN_HUMAN,
        )
    }

    /** 导出为录制步骤（press/global 等 Agent 扩展动作降级为 WAIT + 注释说明）。 */
    fun toRecordedStep(): RecordedStep = RecordedStep(
        type = when (type) {
            Type.CLICK -> RecordedStep.Type.CLICK
            Type.SET_TEXT -> RecordedStep.Type.SET_TEXT
            Type.SWIPE -> RecordedStep.Type.SWIPE
            Type.OPEN_APP -> RecordedStep.Type.OPEN_APP
            Type.WAIT, Type.PRESS, Type.GLOBAL -> RecordedStep.Type.WAIT
        },
        atMillis = atMillis,
        packageName = packageName,
        viewId = viewId,
        text = text,
        desc = desc,
        x = x,
        y = y,
        x2 = x2,
        y2 = y2,
        direction = direction,
        inputText = inputText,
    )

    val hasCenter: Boolean get() = x > 0 || y > 0

    val hasBBox: Boolean get() = bbox != null

    /** 目标中文描述（UI 展示与轨迹注释）。 */
    fun targetLabel(): String = when {
        text.isNotBlank() -> text
        desc.isNotBlank() -> desc
        viewId.isNotBlank() -> viewId.substringAfterLast('/')
        bbox != null -> "bbox(${bbox.left},${bbox.top},${bbox.right},${bbox.bottom})"
        hasCenter -> "($x, $y)"
        else -> "未知控件"
    }

    fun comment(): String = when (type) {
        Type.CLICK -> "点击「${targetLabel()}」"
        Type.SET_TEXT -> "输入「$inputText」"
        Type.SWIPE -> "向${directionLabel()}滑动"
        Type.PRESS -> "长按「${targetLabel()}」"
        Type.OPEN_APP -> "打开应用「$packageName」"
        Type.WAIT -> "等待界面稳定"
        Type.GLOBAL -> "全局动作「$globalAction」"
    }

    fun directionLabel(): String = when (direction) {
        "up" -> "上"
        "down" -> "下"
        "left" -> "左"
        "right" -> "右"
        else -> direction
    }
}

/** 视觉通道的元素包围盒（屏幕坐标，含式）。 */
data class UiBBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val area: Int get() = width * height

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom

    /** 与另一 bbox 的交集面积。 */
    fun intersectionArea(other: UiBBox): Int {
        val l = maxOf(left, other.left)
        val t = maxOf(top, other.top)
        val r = minOf(right, other.right)
        val b = minOf(bottom, other.bottom)
        return if (r > l && b > t) (r - l) * (b - t) else 0
    }

    /** IoU（交并比）。 */
    fun iou(other: UiBBox): Double {
        val inter = intersectionArea(other)
        if (inter == 0) return 0.0
        val union = area + other.area - inter
        return if (union <= 0) 0.0 else inter.toDouble() / union
    }
}
