package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页底栏上滑 morph 的口径：输入胶囊 pill → 编辑器底部工具栏矩形。
 *
 * 钉死四件事：
 * 1. 端点逐位精确（reveal = 0 == 胶囊矩形，reveal = 1 == 最终工具栏矩形）；
 * 2. [0,1] 外 clamp（两端都不外插）；
 * 3. 每个分量单调非降（不会来回抖）；
 * 4. 胶囊 alpha 与工具栏 alpha 严格互补 ⇒ 任一时刻屏幕底部只有一条底栏 >0.5 可见。
 */
class FlbToolbarMorphTest {

    /** 输入胶囊 pill：56dp 高、340px 宽、圆角 = 较短边一半 = 28px。 */
    private val collapsed = Rect(left = 24f, top = 1900f, right = 364f, bottom = 1956f)

    /** 编辑器最终工具栏：满宽、48px 高、圆角 18dp（@3x = 54px）。 */
    private val target = Rect(left = 0f, top = 1880f, right = 1080f, bottom = 1928f)

    private val collapsedRadiusPx = minOf(collapsed.width, collapsed.height) / 2f
    private val targetRadiusPx = 54f

    private fun morph(reveal: Float) = aiFlbToolbarMorph(
        revealProgress = reveal,
        collapsedBounds = collapsed,
        targetBounds = target,
        collapsedCornerRadiusPx = collapsedRadiusPx,
        targetCornerRadiusPx = targetRadiusPx,
    )

    @Test
    fun revealZeroEqualsCollapsedCapsuleExactly() {
        val geometry = morph(0f)
        assertEquals(collapsed.left, geometry.left, 0f)
        assertEquals(collapsed.top, geometry.top, 0f)
        assertEquals(collapsed.width, geometry.width, 0f)
        assertEquals(collapsed.height, geometry.height, 0f)
        assertEquals(collapsedRadiusPx, geometry.cornerRadiusPx, 0f)
        assertEquals(1f, geometry.containerAlpha, 0f)
    }

    @Test
    fun revealOneEqualsFinalToolbarExactly() {
        val geometry = morph(1f)
        assertEquals(target.left, geometry.left, 0f)
        assertEquals(target.top, geometry.top, 0f)
        assertEquals(target.width, geometry.width, 0f)
        assertEquals(target.height, geometry.height, 0f)
        assertEquals(targetRadiusPx, geometry.cornerRadiusPx, 0f)
        assertEquals(0.96f, geometry.containerAlpha, 0f)
    }

    @Test
    fun clampsOutsideUnitRange() {
        val below = morph(-0.75f)
        assertEquals(collapsed.left, below.left, 0f)
        assertEquals(collapsed.top, below.top, 0f)
        assertEquals(collapsed.width, below.width, 0f)
        assertEquals(collapsed.height, below.height, 0f)
        assertEquals(collapsedRadiusPx, below.cornerRadiusPx, 0f)
        assertEquals(1f, below.containerAlpha, 0f)

        val above = morph(1.8f)
        assertEquals(target.left, above.left, 0f)
        assertEquals(target.top, above.top, 0f)
        assertEquals(target.width, above.width, 0f)
        assertEquals(target.height, above.height, 0f)
        assertEquals(targetRadiusPx, above.cornerRadiusPx, 0f)
        assertEquals(0.96f, above.containerAlpha, 0f)
    }

    @Test
    fun everyComponentIsMonotonicAndStaysBetweenEndpoints() {
        // 唯一插值参数：单调非降 + clamp 在 [0, 1]（morph 起点 0.88 之前恒为 0，reveal = 1 恰好为 1）
        var previousFraction = -1f
        var reveal = -0.2f
        while (reveal <= 1.25f) {
            val fraction = morph(reveal).fraction
            assertTrue("fraction monotonic @$reveal", fraction >= previousFraction)
            assertTrue("fraction in [0,1] @$reveal", fraction in 0f..1f)
            previousFraction = fraction
            reveal += 0.005f
        }
        assertEquals(0f, morph(0.5f).fraction, 0f)
        assertEquals(0f, morph(0.88f).fraction, 0f)
        assertEquals(1f, morph(1f).fraction, 0f)

        // 每个分量随 reveal 单调（端点变大则非降、变小则非增），且始终落在两端点闭区间内
        fun assertBetween(name: String, value: Float, start: Float, end: Float, at: Float) {
            val low = minOf(start, end)
            val high = maxOf(start, end)
            assertTrue("$name out of range @$at", value >= low && value <= high)
        }
        val first = morph(-0.2f)
        var previous = first
        reveal = -0.19f
        while (reveal <= 1.21f) {
            val current = morph(reveal)
            assertTrue("left @$reveal", (current.left - previous.left) * (target.left - collapsed.left) >= 0f)
            assertTrue("top @$reveal", (current.top - previous.top) * (target.top - collapsed.top) >= 0f)
            assertTrue("width @$reveal", (current.width - previous.width) * (target.width - collapsed.width) >= 0f)
            assertTrue("height @$reveal", (current.height - previous.height) * (target.height - collapsed.height) >= 0f)
            assertTrue(
                "radius @$reveal",
                (current.cornerRadiusPx - previous.cornerRadiusPx) * (targetRadiusPx - collapsedRadiusPx) >= 0f
            )
            assertTrue(
                "alpha @$reveal",
                (current.containerAlpha - previous.containerAlpha) * (0.96f - 1f) >= 0f
            )
            assertBetween("left", current.left, collapsed.left, target.left, reveal)
            assertBetween("top", current.top, collapsed.top, target.top, reveal)
            assertBetween("width", current.width, collapsed.width, target.width, reveal)
            assertBetween("height", current.height, collapsed.height, target.height, reveal)
            assertBetween("radius", current.cornerRadiusPx, collapsedRadiusPx, targetRadiusPx, reveal)
            assertBetween("alpha", current.containerAlpha, 1f, 0.96f, reveal)
            // 中间段必须是同一条参数化直线：分量 == lerp(端点, fraction)
            val fraction = current.fraction
            if (fraction > 0f && fraction < 1f) {
                assertEquals(collapsed.left + (target.left - collapsed.left) * fraction, current.left, 1e-4f)
                assertEquals(collapsed.top + (target.top - collapsed.top) * fraction, current.top, 1e-4f)
                assertEquals(
                    collapsed.width + (target.width - collapsed.width) * fraction,
                    current.width,
                    1e-4f
                )
                assertEquals(
                    collapsedRadiusPx + (targetRadiusPx - collapsedRadiusPx) * fraction,
                    current.cornerRadiusPx,
                    1e-4f
                )
            }
            previous = current
            reveal += 0.01f
        }
    }

    @Test
    fun capsuleAndToolbarAlphaAreComplementary() {
        // 端点精确
        assertEquals(1f, aiFlbCapsuleAlpha(0f), 0f)
        assertEquals(0f, aiFlbToolbarAlpha(0f), 0f)
        assertEquals(0f, aiFlbCapsuleAlpha(1f), 0f)
        assertEquals(1f, aiFlbToolbarAlpha(1f), 0f)
        // 淡出行程之外恒定
        assertEquals(0f, aiFlbCapsuleAlpha(0.25f), 0f)
        assertEquals(1f, aiFlbCapsuleAlpha(-0.4f), 0f)
        assertEquals(1f, aiFlbToolbarAlpha(0.25f), 0f)
        assertEquals(1f, aiFlbToolbarAlpha(3f), 0f)
        // 互补 + 单调 + 任一时刻不同时 >0.5 可见
        var reveal = -0.2f
        var previousCapsule = aiFlbCapsuleAlpha(reveal)
        while (reveal <= 1.2f) {
            val capsule = aiFlbCapsuleAlpha(reveal)
            val toolbar = aiFlbToolbarAlpha(reveal)
            assertEquals("sum @$reveal", 1f, capsule + toolbar, 1e-6f)
            assertTrue("capsule monotonic @$reveal", capsule <= previousCapsule + 1e-6f)
            assertTrue("only one bottom bar @$reveal", !(capsule > 0.5f && toolbar > 0.5f))
            previousCapsule = capsule
            reveal += 0.005f
        }
        // 交叉点：reveal = 0.125 处两条曲线都恰好 0.5
        assertEquals(0.5f, aiFlbCapsuleAlpha(0.125f), 1e-6f)
        assertEquals(0.5f, aiFlbToolbarAlpha(0.125f), 1e-6f)
        // 与淡出行程常量对得上
        assertEquals(0.25f, AI_FLB_TOOLBAR_FADE_RANGE, 0f)
    }
}
