package heizige.kk.khatkit.app.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiAutomationSnapshotTest {
    @Test
    fun boundsAndTextAreNormalized() {
        val raw = mapOf<String, Any?>(
            "depth" to 2,
            "className" to "android.widget.Button",
            "viewId" to "demo:id/submit",
            "text" to "Submit",
            "desc" to "Submit form",
            "bounds" to mapOf("left" to 1, "top" to 2, "right" to 30, "bottom" to 40),
            "clickable" to true,
            "editable" to false,
            "scrollable" to false,
        )

        val node = UiAutomationNode(
            index = 0,
            depth = raw["depth"] as Int,
            className = raw["className"] as String,
            viewId = raw["viewId"] as String,
            text = raw["text"] as String,
            description = raw["desc"] as String,
            bounds = UiAutomationBounds(1, 2, 30, 40),
            clickable = raw["clickable"] as Boolean,
            editable = raw["editable"] as Boolean,
            scrollable = raw["scrollable"] as Boolean,
        )

        assertEquals(UiAutomationBounds(1, 2, 30, 40), node.bounds)
        assertTrue(node.clickable)
    }
}
