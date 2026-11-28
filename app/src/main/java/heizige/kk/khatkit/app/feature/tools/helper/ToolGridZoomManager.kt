package heizige.kk.khatkit.app.feature.tools.helper

/** Tracks relative finger movement for one pinch, independently of the grid's current cell width. */
internal class ToolGridZoomManager {
    private var scale = 1f

    fun update(factor: Float): Int {
        if (!factor.isFinite() || factor <= 0f) return 0
        scale *= factor
        val step = when {
            scale >= STEP_SCALE -> -1
            scale <= 1f / STEP_SCALE -> 1
            else -> 0
        }

        if (step != 0) reset()
        return step
    }

    fun reset() {
        scale = 1f
    }

    private companion object {
        const val STEP_SCALE = 1.10f
    }
}
