package heizige.kk.khatkit.record

/**
 * A4 视觉双通道定位：视觉 bbox → 无障碍节点。
 *
 * 视觉给候选（bbox），无障碍给精确节点。匹配规则：
 * 1. 中心包含优先（视觉 bbox 中心落在节点内 = 最强信号）
 * 2. IoU ≥ [IOU_THRESHOLD] 兜底
 * 3. 都命中时取分数最高者；命中即用节点中心（误差 0），否则退回 bbox 中心（≤8dp）
 *
 * 纯函数，可 JVM 单测。
 */
object VisualGrounding {

    /** 节点匹配接受阈值。 */
    const val IOU_THRESHOLD = 0.25

    /** 有节点命中时误差为 0；无节点命中时用 bbox 中心，声明误差上限 8dp。 */
    const val BBOX_FALLBACK_ERROR_DP = 8

    /** 节点候选（与 UiAutomationNode 结构解耦，宿主负责映射）。 */
    data class NodeCandidate(
        val index: Int,
        val viewId: String = "",
        val text: String = "",
        val desc: String = "",
        val className: String = "",
        val bounds: UiBBox,
        val clickable: Boolean = false,
        val editable: Boolean = false,
    )

    /** 解析结果：最终目标坐标 + 命中方式 + 误差声明。 */
    data class Resolution(
        /** 命中节点（双通道校验通过）；null = 视觉通道兜底。 */
        val node: NodeCandidate? = null,
        val x: Int,
        val y: Int,
        val viewId: String = "",
        val text: String = "",
        val desc: String = "",
        /** node = 用节点中心（误差 0）；bbox = 用视觉 bbox 中心（≤8dp）。 */
        val method: String,
        val errorDp: Int,
        val score: Double = 0.0,
    ) {
        val resolvedByNode: Boolean get() = method == "node"
    }

    /**
     * 把视觉 bbox 解析为可执行目标。
     *
     * @param bbox 视觉模型给出的元素包围盒
     * @param nodes 当前无障碍节点候选（已过滤不可见/零面积）
     */
    fun resolve(bbox: UiBBox, nodes: List<NodeCandidate>): Resolution {
        val candidates = nodes.filter { it.bounds.area > 0 }
        var best: NodeCandidate? = null
        var bestScore = 0.0
        for (node in candidates) {
            val score = scoreMatch(bbox, node.bounds)
            if (score > bestScore) {
                bestScore = score
                best = node
            }
        }
        val hit = best?.takeIf { bestScore >= IOU_THRESHOLD || isCenterInside(bbox, it.bounds) }
        return if (hit != null) {
            Resolution(
                node = hit,
                x = hit.bounds.centerX,
                y = hit.bounds.centerY,
                viewId = hit.viewId,
                text = hit.text,
                desc = hit.desc,
                method = "node",
                errorDp = 0,
                score = bestScore,
            )
        } else {
            Resolution(
                node = null,
                x = bbox.centerX,
                y = bbox.centerY,
                method = "bbox",
                errorDp = BBOX_FALLBACK_ERROR_DP,
                score = bestScore,
            )
        }
    }

    /** 视觉 bbox 中心是否落在节点内（最强匹配信号）。 */
    fun isCenterInside(bbox: UiBBox, node: UiBBox): Boolean =
        node.contains(bbox.centerX, bbox.centerY)

    /** 匹配得分：中心包含给满分，否则按 IoU。 */
    fun scoreMatch(bbox: UiBBox, node: UiBBox): Double {
        if (isCenterInside(bbox, node)) return 1.0
        return bbox.iou(node)
    }

    /**
     * 视觉元素列表 → 各自解析（供 device_screen 一次性输出双通道标注）。
     */
    fun resolveAll(bboxes: List<UiBBox>, nodes: List<NodeCandidate>): List<Resolution> =
        bboxes.map { resolve(it, nodes) }
}
