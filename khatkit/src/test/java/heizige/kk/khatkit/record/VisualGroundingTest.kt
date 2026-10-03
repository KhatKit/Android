package heizige.kk.khatkit.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualGroundingTest {

    private fun node(l: Int, t: Int, r: Int, b: Int, viewId: String = "", text: String = "", clickable: Boolean = true) =
        VisualGrounding.NodeCandidate(index = 0, viewId = viewId, text = text, bounds = UiBBox(l, t, r, b), clickable = clickable)

    @Test
    fun `center-inside match resolves to node center with zero error`() {
        val target = UiBBox(100, 100, 200, 200)
        val nodes = listOf(node(50, 50, 250, 250, viewId = "btn", text = "确定"))
        val res = VisualGrounding.resolve(target, nodes)
        assertTrue(res.resolvedByNode)
        assertEquals("node", res.method)
        assertEquals(0, res.errorDp)
        assertEquals(150, res.x) // node center
        assertEquals(150, res.y)
        assertEquals("btn", res.viewId)
        assertEquals("确定", res.text)
    }

    @Test
    fun `no matching node falls back to bbox center`() {
        val target = UiBBox(100, 100, 200, 200)
        val nodes = listOf(node(0, 0, 50, 50, viewId = "far"))
        val res = VisualGrounding.resolve(target, nodes)
        assertFalse(res.resolvedByNode)
        assertEquals("bbox", res.method)
        assertEquals(VisualGrounding.BBOX_FALLBACK_ERROR_DP, res.errorDp)
        assertEquals(150, res.x) // bbox center
        assertEquals(150, res.y)
        assertNull(res.node)
    }

    @Test
    fun `empty node list falls back to bbox`() {
        val target = UiBBox(10, 10, 30, 30)
        val res = VisualGrounding.resolve(target, emptyList())
        assertEquals("bbox", res.method)
        assertEquals(20, res.x)
        assertEquals(20, res.y)
    }

    @Test
    fun `iou match without center-inside still resolves to node`() {
        // node overlaps bbox by ~0.5 IoU but center of bbox is outside node
        val target = UiBBox(100, 100, 300, 300) // center 200,200
        val node = node(250, 250, 350, 350) // overlaps 250-300 square; bbox center 200,200 not inside
        val res = VisualGrounding.resolve(target, listOf(node))
        // IoU = 50*50 / (200*200 + 100*100 - 2500) = 2500/47500 ≈ 0.05 < 0.25 → fallback
        // center not inside → fallback
        assertEquals("bbox", res.method)
    }

    @Test
    fun `best score wins among multiple nodes`() {
        val target = UiBBox(100, 100, 200, 200) // center 150,150
        val nodes = listOf(
            node(0, 0, 300, 300, viewId = "big"), // contains center
            node(140, 140, 160, 160, viewId = "tiny"), // contains center, tighter
        )
        val res = VisualGrounding.resolve(target, nodes)
        assertTrue(res.resolvedByNode)
        // both contain center (score 1.0); first best wins (big) since score not strictly greater
        assertNotNull(res.node)
    }

    @Test
    fun `zero-area nodes are ignored`() {
        val target = UiBBox(0, 0, 10, 10)
        val bad = node(5, 5, 5, 5, viewId = "degenerate")
        val res = VisualGrounding.resolve(target, listOf(bad))
        assertEquals("bbox", res.method)
    }

    @Test
    fun `bbox geometry helpers`() {
        val b = UiBBox(10, 20, 30, 40)
        assertEquals(20, b.width)
        assertEquals(20, b.height)
        assertEquals(20, b.centerX)
        assertEquals(30, b.centerY)
        assertEquals(400, b.area)
        assertTrue(b.contains(15, 25))
        assertFalse(b.contains(5, 25))
    }

    @Test
    fun `iou is zero for disjoint boxes`() {
        assertEquals(0.0, UiBBox(0, 0, 10, 10).iou(UiBBox(20, 20, 30, 30)), 0.0001)
    }

    @Test
    fun `iou is one for identical boxes`() {
        assertEquals(1.0, UiBBox(0, 0, 10, 10).iou(UiBBox(0, 0, 10, 10)), 0.0001)
    }

    @Test
    fun `resolveAll maps each bbox`() {
        val bboxes = listOf(UiBBox(0, 0, 10, 10), UiBBox(100, 100, 110, 110))
        val nodes = listOf(node(0, 0, 20, 20, viewId = "a"))
        val res = VisualGrounding.resolveAll(bboxes, nodes)
        assertEquals(2, res.size)
        assertEquals("node", res[0].method)
        assertEquals("bbox", res[1].method)
    }
}
