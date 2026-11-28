package heizige.kk.khatkit.app.core.ui.components.ai

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorComposerMotionTest {
    private val pill = Rect(68f, 740f, 344f, 788f)
    private val fab = Rect(12f, 740f, 60f, 788f)
    private val viewport = Rect(0f, 0f, 360f, 800f)

    @Test
    fun risesWhileMovingTowardFab() {
        val early = editorComposerBounds(pill, fab, viewport, 0.12f, liftPx = 20f)
        assertTrue(early.left in fab.left..pill.left)
        assertTrue(early.left < pill.left)
        assertTrue(early.top < pill.top)
        assertTrue(early.bottom < pill.bottom)
    }

    @Test
    fun joinedGlassReachesFullWidthEarlyAndKeepsItWhileRising() {
        val contact = editorComposerBounds(pill, fab, viewport, EditorFusionContact, liftPx = 20f)
        val wide = editorComposerBounds(pill, fab, viewport, EditorFullWidth, liftPx = 20f)
        val halfway = editorComposerBounds(pill, fab, viewport, 0.5f, liftPx = 20f)

        assertTrue(contact.left < fab.right)
        assertEquals(pill.right, contact.right, 0f)
        assertTrue(wide.top < contact.top)
        assertTrue(halfway.top < wide.top)
        assertEquals(viewport.width, wide.width, 0f)
        assertEquals(viewport.width, halfway.width, 0f)
    }

    @Test
    fun morphKeepsExactEndpoints() {
        assertEquals(pill, editorComposerBounds(pill, fab, viewport, 0f, liftPx = 20f))
        assertEquals(viewport, editorComposerBounds(pill, fab, viewport, 1f, liftPx = 20f))
    }

    @Test
    fun chatBackdropBlurRampsInWithReveal() {
        assertEquals(0f, editorBackdropProgress(0f), 0f)
        assertTrue(editorBackdropProgress(0.5f) in 0.01f..0.99f)
        assertEquals(1f, editorBackdropProgress(1f), 0f)
    }

    @Test
    fun floatingComposerHandsOffToEditorOverTheEarlyDrag() {
        assertEquals(0f, editorSurfaceHandoff(0f), 0f)
        assertEquals(0.5f, editorSurfaceHandoff(0.08f), 0.001f)
        assertEquals(1f, editorSurfaceHandoff(0.16f), 0f)
    }

    @Test
    fun glassRimStartsFadingOnlyNearFullscreen() {
        assertEquals(1f, editorGlassAlpha(0.90f), 0f)
        assertEquals(1f, editorGlassAlpha(0.92f), 0f)
        assertTrue(editorGlassAlpha(0.96f) in 0.01f..0.99f)
        assertEquals(0f, editorGlassAlpha(1f), 0f)
    }

    @Test
    fun predictiveBackBendsOnlyTheTouchedEdgeAndFollowsFingerHeight() {
        val motion = EditorBackMotion(direction = 1f, progress = 0.6f, touchY = 420f, travelX = 40f)
        fun band(y: Float, back: EditorBackMotion) =
            editorWarpBand(y, viewport.width, viewport.height, viewport, 1f, pill.center.x, back)

        val left = band(420f, motion)
        val right = band(420f, motion.copy(direction = -1f))
        assertTrue(left.left.x > 30f)
        assertTrue(band(100f, motion).left.x < left.left.x * 0.02f)
        assertEquals(viewport.width, left.right.x, 0f)
        assertEquals(left.left.x, viewport.width - right.right.x, 0.001f)
        assertEquals(0f, right.left.x, 0f)
        assertEquals(0f, band(420f, motion.copy(progress = 0f)).left.x, 0f)
        assertTrue(band(420f, motion.copy(touchY = 100f)).left.x < left.left.x * 0.02f)
    }

    @Test
    fun openingBendsTowardTheFingerWithFullWidth() {
        val rect = editorComposerBounds(pill, fab, viewport, 0.5f, liftPx = 20f)
        fun band(y: Float, anchorX: Float) = editorWarpBand(
            y, viewport.width, viewport.height, rect, 0.5f, anchorX, EditorBackMotion(),
        )

        val left = band(rect.top, pill.left)
        val right = band(rect.top, pill.right)
        val lower = band(rect.bottom, pill.right)
        assertEquals(viewport.width, right.right.x - right.left.x, 0.001f)
        assertEquals(viewport.width, left.right.x - left.left.x, 0.001f)
        assertTrue(right.left.x > 0f)
        assertTrue(left.left.x < 0f)
        assertTrue(right.right.y < right.left.y)
        assertEquals(0f, lower.left.x, 0f)
    }
}
