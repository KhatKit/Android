package heizige.kk.khatkit.app

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import heizige.kk.khatkit.ui.UiNodeParser
import heizige.kk.khatkit.ui.UiScreenPage
import heizige.kk.khatkit.ui.UiSheetNavItem
import heizige.kk.khatkit.ui.UiSheetNavigation
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import heizige.kk.khatkit.uikit.NodeTreeSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NodeTreePagerInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialControlsKeepTheirEnabledAppearanceDuringNavigation() =
        checkNavigation(KhatKitUiStyle.MATERIAL)

    @Test fun miuixControlsKeepTheirEnabledAppearanceDuringNavigation() =
        checkNavigation(KhatKitUiStyle.MIUIX)

    private fun checkNavigation(style: KhatKitUiStyle) {
        fun node(type: String, props: Map<String, Any?> = emptyMap(), vararg children: Map<String, Any?>) =
            mapOf("__ui" to type, "props" to props, "children" to children.toList())
        fun page(id: String) = UiScreenPage(UiNodeParser.parse(node("Column", emptyMap(),
            node("Button", mapOf("label" to "Action $id", "action" to "action_$id")),
            node("IconButton", mapOf("icon" to "refresh", "action" to "refresh_$id",
                "contentDescription" to "Refresh $id")),
            node("Button", mapOf("label" to "Disabled $id", "action" to "disabled_$id", "disabled" to true)),
        )))
        val pages = mapOf("a" to page("a"), "b" to page("b"))
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        compose.setContent {
            KhatKitTheme(style, darkTheme = true) {
                NodeTreeSheet("Pager fixture", pages.getValue("a").root,
                    UiSheetOptions(fullscreen = true, navigation = UiSheetNavigation(
                        listOf(UiSheetNavItem("a", "Tab A"), UiSheetNavItem("b", "Tab B")), "a")),
                    pages = pages, onAction = { event, values -> events += event to values },
                    onDismiss = {}, markdown = { text, modifier -> Text(text, modifier) },
                    image = { _, _, modifier -> Box(modifier) })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Action a").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, events.size)
            events.clear()
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Tab B").performClick()
        compose.mainClock.advanceTimeBy(64)

        // Inspect the outgoing page while the 220 ms slide is still running.
        // A temporary readOnly state would gray out both controls here.
        compose.onNodeWithText("Action a").assertIsEnabled()
        compose.onNodeWithContentDescription("Refresh a").assertIsEnabled()
        compose.onNodeWithText("Disabled a").assertIsNotEnabled()
        // Semantics clicks bypass touch cancellation: the submission guard must
        // still reject an outgoing page's action during the slide.
        compose.onNodeWithText("Action a").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertTrue(events.isEmpty()) }

        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Action b").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(1, events.size)
            assertEquals("action_b", events.single().first)
            assertEquals("b", events.single().second[UiScreenPage.PAGE_VALUE_KEY])
        }

        compose.onNodeWithText("Tab A").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // A real drag starting on a button must cancel its click, while every
        // other button/icon retains its normal colors throughout the gesture.
        compose.onNodeWithText("Action a").performTouchInput {
            down(center)
            moveBy(Offset(-64f, 0f))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Action a").assertIsEnabled()
        compose.onNodeWithContentDescription("Refresh a").assertIsEnabled()
        compose.onNodeWithText("Disabled a").assertIsNotEnabled()
        compose.onNodeWithText("Action a").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, events.size) }
    }
}
