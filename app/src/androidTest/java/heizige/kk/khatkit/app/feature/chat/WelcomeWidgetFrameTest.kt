package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.R
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.khatkit.bridge.BridgeRegistry
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import heizige.kk.khatkit.engine.EngineResult
import heizige.kk.khatkit.exec.CardExecutionOptions
import heizige.kk.khatkit.exec.CardExecutor
import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.CardWidgetBridge
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class WelcomeWidgetFrameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun miuixHeaderRefreshAndAnimatedHeight() = checkFrame(KhatKitUiStyle.MIUIX)
    @Test fun materialHeaderRefreshAndAnimatedHeight() = checkFrame(KhatKitUiStyle.MATERIAL)

    @Test fun miuixSpinnerDrawsWithinItsSmallIconSlot() {
        var progress by mutableStateOf<Float?>(null)
        compose.setContent {
            KhatKitTheme(KhatKitUiStyle.MIUIX, darkTheme = true) {
                Box(Modifier.size(80.dp).background(Color.Black).testTag("indicator"),
                    contentAlignment = Alignment.Center) {
                    KedgeProgressIndicator(Modifier.size(20.dp), progress = progress,
                        color = Color.White, trackColor = Color.White)
                }
            }
        }
        for (value in listOf(null, 0.5f)) {
            compose.runOnIdle { progress = value }
            val pixels = compose.onNodeWithTag("indicator").captureToImage().toPixelMap()
            var left = pixels.width
            var right = -1
            var top = pixels.height
            var bottom = -1
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (pixels[x, y].red > 0.2f) {
                    left = minOf(left, x); right = maxOf(right, x)
                    top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
            val slot = pixels.width / 4f
            assertTrue("Spinner must be visible", right >= left && bottom >= top)
            assertTrue("Spinner must fit and stay centered in its 20dp slot",
                left >= (pixels.width - slot) / 2 - 2 &&
                    right < (pixels.width + slot) / 2 + 2 &&
                    top >= (pixels.height - slot) / 2 - 2 &&
                    bottom < (pixels.height + slot) / 2 + 2)
        }
    }

    @Test fun scriptRefreshEventsRoundTripInLuaAndJavaScript() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = CardManifest(name = "widget_refresh_test",
            requires = CardManifest.Requires(bridges = listOf("ui")))
        val scripts = mapOf(
            EngineKind.LUA to """
                local event = args.widget or {}
                ui.show(ui.Text { text = event.action == "refresh_status" and "refreshed" or "cached" },
                    { refresh_action = "refresh_status" })
                return { ok = true }
            """.trimIndent(),
            EngineKind.JS to """
                ui.show({__ui:"Text",props:{text:args.widget.action==="refresh_status"?"refreshed":"cached"},children:[]},
                    {refresh_action:"refresh_status"});
                return {ok:true};
            """.trimIndent(),
        )
        for ((kind, script) in scripts) {
            val executor = CardExecutor(BridgeRegistry())
            val card = LoadedCard(manifest, context.cacheDir, script, kind)
            var action = ""
            for (expected in listOf("cached", "refreshed")) {
                val bridge = CardWidgetBridge()
                val result = executor.execute(card, mapOf("widget" to mapOf("action" to action)),
                    CardExecutionOptions(ui = bridge))
                assertTrue(result.toString(), result is EngineResult.Ok)
                assertEquals(expected, bridge.content!!.props["text"])
                assertEquals("refresh_status", bridge.refreshAction)
                action = bridge.refreshAction
            }
        }
    }

    private fun checkFrame(style: KhatKitUiStyle) {
        var expanded by mutableStateOf(false)
        var loading by mutableStateOf(false)
        var refreshes = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val refreshLabel = context.getString(R.string.chat_widget_refresh)
        val hideLabel = context.getString(R.string.chat_widget_hide)
        compose.setContent {
            KhatKitTheme(style, darkTheme = true) {
                WelcomeWidgetFrame(
                    title = "Reset status", description = "Tibo", loading = loading,
                    shape = RoundedCornerShape(20.dp), onRefresh = { refreshes++ },
                    modifier = Modifier.testTag("widget"),
                ) {
                    Column {
                        Text("No reset today")
                        if (expanded) Spacer(Modifier.height(120.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(hideLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(refreshLabel)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, refreshes) }

        val initial = compose.onNodeWithTag("widget").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { expanded = true }
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        val middle = compose.onNodeWithTag("widget").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val end = compose.onNodeWithTag("widget").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Height should interpolate instead of jumping: $initial / $middle / $end",
            middle > initial && middle < end)
        compose.mainClock.autoAdvance = true
        val buttonBefore = compose.onNodeWithContentDescription(refreshLabel).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { loading = true }
        val refresh = compose.onNodeWithContentDescription(refreshLabel)
        val buttonDuring = refresh.fetchSemanticsNode().boundsInRoot
        assertEquals(buttonBefore, buttonDuring)
        assertEquals(end, compose.onNodeWithTag("widget").fetchSemanticsNode().boundsInRoot.height, 0.5f)
        refresh.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals("Loading must not submit another refresh", 1, refreshes) }
    }
}
