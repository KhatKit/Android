package heizige.kk.khatkit.app.feature.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import android.os.SystemClock
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import dagger.hilt.android.EntryPointAccessors
import heizige.kk.khatkit.app.core.di.AppEntryPoint
import heizige.kk.khatkit.app.core.data.automation.ApprovalCategory
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.feature.automation.helper.AutomationManager
import heizige.kk.khatkit.uikit.KhatKitTheme
import ui.feice.mochi.basic.MochiToastDefaults
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import heizige.kk.khatkit.app.core.data.automation.ApprovalRequest
import heizige.kk.khatkit.app.core.data.automation.AutomationStatus
import heizige.kk.khatkit.app.core.data.datastore.Settings



class AutomationToastTest {
    @get:Rule val compose = createComposeRule()

    @After fun reset() {
        AutomationManager.clear()
        AutomationManager.setAppInForeground(true)
    }

    @Test fun darkPillWrapsContentAndUsesMochiColors() = checkPill(true)
    @Test fun lightPillWrapsContentAndUsesMochiColors() = checkPill(false)

    private fun checkPill(dark: Boolean) {
        var label by mutableStateOf("完成")
        var expected = Color.Unspecified
        var dismisses = 0
        compose.setContent {
            KhatKitTheme(darkTheme = dark) {
                expected = MochiToastDefaults.colors().container
                AutomationToast(
                    status = AutomationStatus(label, activeSince = 0),
                    approval = null, onApprove = {}, onDeny = {}, onRemember = {},
                    onDismiss = { dismisses++ }, modifier = Modifier.testTag("pill"),
                )
            }
        }
        val short = compose.onNodeWithTag("pill").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertEquals(32 * density, short.width - text.width, 3f)
        assertTrue(short.height <= 52 * density)
        run {
            val pixels = compose.onNodeWithTag("pill").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, (6 * density).toInt()]
            assertEquals(expected.red, actual.red, 0.02f)
            assertEquals(expected.green, actual.green, 0.02f)
            assertEquals(expected.blue, actual.blue, 0.02f)
        }
        compose.runOnIdle { label = "正在检查自动化步骤是否执行完成" }
        val longer = compose.onNodeWithTag("pill").fetchSemanticsNode().boundsInRoot
        assertTrue(longer.width > short.width)
        compose.onNodeWithTag("pill").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, dismisses) }
    }

    private fun showApproval(systemWindow: Boolean = false): MutableList<String> {
        val decisions = mutableListOf<String>()
        compose.setContent {
            KhatKitTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().testTag("page"), contentAlignment = Alignment.Center) {
                    AutomationToast(status = null,
                        approval = ApprovalRequest("test", "测试授权", "", ApprovalCategory.UI_ACTION),
                        onApprove = { decisions.add("allow:$it") }, onDeny = { decisions.add("deny:$it") },
                        onRemember = { decisions.add("remember:$it") }, onDismiss = { decisions.add("hide") },
                        systemWindow = systemWindow,
                    )
                }
            }
        }
        return decisions
    }

    @Test fun shortTapAllowsOnce() {
        val decisions = showApproval()
        compose.onNodeWithContentDescription("允许").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("allow:test"), decisions) }
    }

    @Test fun accessibleDenyResolvesOnlyOnce() {
        val decisions = showApproval()
        compose.onNodeWithContentDescription("拒绝").performSemanticsAction(SemanticsActions.OnClick) { it(); it() }
        compose.runOnIdle { assertEquals(listOf("deny:test"), decisions) }
    }

    private fun choose(text: String, expected: String, systemWindow: Boolean = false) {
        val decisions = showApproval(systemWindow)
        compose.onNodeWithContentDescription("允许").performTouchInput { longClick() }
        compose.onNodeWithText(text).assertExists()
        compose.runOnIdle { assertTrue(decisions.isEmpty()) }
        compose.onNodeWithText(text).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(expected), decisions) }
    }

    @Test fun longPressThenChooseOnce() = choose("仅本次允许", "allow:test")
    @Test fun longPressThenChooseTenMinutes() = choose("10 分钟内自动允许", "remember:test")
    @Test fun compactMenuRowChoosesTenMinutes() = choose("10 分钟内自动允许", "remember:test", systemWindow = true)

    @Test fun outsideAndBackDoNotResolveApproval() {
        val decisions = showApproval()
        compose.onNodeWithContentDescription("允许").performTouchInput { longClick() }
        compose.onNodeWithText("仅本次允许").assertExists()
        tapOnScreen(40f, instrumentation.targetContext.resources.displayMetrics.heightPixels * 0.22f)
        compose.onNodeWithText("仅本次允许").assertDoesNotExist()
        compose.onNodeWithContentDescription("允许").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithText("仅本次允许").assertExists()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("仅本次允许").assertDoesNotExist()
        compose.onNodeWithContentDescription("允许").assertExists()
        compose.runOnIdle { assertTrue(decisions.isEmpty()) }
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun tapOnScreen(x: Float, y: Float) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "input tap ${x.toInt()} ${y.toInt()}"
        )).use { it.readBytes() }
    }

    // Compose windows can have different origins. Use the actual on-screen accessibility bounds.
    private fun screenBounds(label: String): Rect {
        fun find(node: AccessibilityNodeInfo?): Rect? {
            if (node == null) return null
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) {
                return Rect().also(node::getBoundsInScreen).takeUnless { it.isEmpty }
            }
            return (0 until node.childCount).firstNotNullOfOrNull { find(node.getChild(it)) }
        }
        var result: Rect? = null
        compose.waitUntil(5_000) {
            result = find(instrumentation.uiAutomation.rootInActiveWindow)
            result != null
        }
        return requireNotNull(result) { "Missing on-screen bounds for $label" }
    }

    @Test fun approvalAppearsAboveOpenCardDialogAndIgnoresStaleDecisions() = runBlocking {
        val context = instrumentation.targetContext
        AutomationManager.install(context)
        AutomationManager.setAppInForeground(true)
        compose.setContent {
            KhatKitTheme {
                MochiDialog(visible = true, onDismissRequest = {}, title = "卡片确认",
                    actions = persistentListOf(MochiDialogAction("取消")))
                AutomationInAppHost()
            }
        }
        compose.onNodeWithText("卡片确认").assertExists()
        withTimeout(25_000) {
            val response = async(kotlinx.coroutines.Dispatchers.Default) {
                AutomationManager.requestApproval("测试请求", "", ApprovalCategory.CARD_RUN, grantSession = false)
            }
            val request = withTimeout(5_000) { AutomationManager.pendingApproval.filterNotNull().first() }
            AutomationManager.approve("stale:${request.id}")
            AutomationManager.approveAndRemember("stale:${request.id}")
            compose.onNodeWithContentDescription("拒绝").assertExists()
            assertFalse(response.isCompleted)
            // Wait for the separate decor layer's first layout and its enter animation.
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            val settled = screenBounds("拒绝")
            tapOnScreen(settled.exactCenterX(), settled.exactCenterY())
            compose.waitUntil(5_000) { response.isCompleted }
            assertFalse(response.await())
        }
        compose.onNodeWithText("卡片确认").assertExists()
        Unit
    }

    @Test fun backgroundWindowUsesMochiAndLeavesSurroundingTouchesAlone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Requires an already authorized overlay or accessibility service",
            android.provider.Settings.canDrawOverlays(context) || heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder.current() != null)
        EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .khatKitToolProvider()
        var backgroundTaps = 0
        var expected = Color.Unspecified
        var overlay: View? = null
        try {
            compose.setContent {
                AutomationOverlayTheme {
                    expected = MochiToastDefaults.colors().container
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag("page")
                        .clickable { backgroundTaps++ })
                }
            }
            compose.runOnIdle {
                AutomationManager.begin()
                AutomationManager.update("测试自动化")
                AutomationManager.setAppInForeground(false)
            }
            compose.waitUntil(8_000) {
                instrumentation.runOnMainSync {
                    overlay = WindowInspector.getGlobalWindowViews().firstOrNull {
                        (it.layoutParams as? WindowManager.LayoutParams)?.title == "KhatKit automation"
                    }?.takeIf { it.height > 0 }
                }
                overlay != null
            }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            SystemClock.sleep(200)
            val position = IntArray(2)
            var width = 0
            var height = 0
            instrumentation.runOnMainSync {
                overlay!!.getLocationOnScreen(position)
                width = overlay!!.width
                height = overlay!!.height
            }
            val density = context.resources.displayMetrics.density
            assertTrue("Overlay must wrap its short label: $width x $height",
                width < 220 * density && height <= 52 * density)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            android.util.Log.i("AutomationToastTest",
                "overlay=${position.toList()} ${width}x${height} screenshot=${screenshot.width}x${screenshot.height}")
            java.io.File(context.cacheDir, "automation-overlay-test.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val actual = Color(screenshot.getPixel(position[0] + width / 2, position[1] + (6 * density).toInt()))
            assertEquals(expected.red, actual.red, 0.03f)
            assertEquals(expected.green, actual.green, 0.03f)
            assertEquals(expected.blue, actual.blue, 0.03f)
            screenshot.recycle()
            val time = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                    position[0] + width / 2f, position[1] + height + 20f, 0)
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
            compose.runOnIdle {
                assertEquals("Blank area outside overlay must reach the page", 1, backgroundTaps)
                assertFalse(AutomationManager.isOverlayHidden())
            }
        } finally {
            compose.runOnIdle {
                AutomationManager.clear()
                AutomationManager.setAppInForeground(true)
            }
        }
    }

    @Test fun inAppPositioningSpaceDoesNotConsumeTouches() {
        var taps = 0
        AutomationManager.setAppInForeground(true)
        AutomationManager.begin()
        AutomationManager.update("正在执行自动化")
        compose.setContent {
            KhatKitTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(Color.Black).testTag("underneath")
                    .clickable { taps++ }) { Text("测试页面") }
                AutomationInAppHost()
            }
        }
        compose.onNodeWithText("正在执行自动化").assertExists()
        // Tap the underlying page beside the pill at the same height, not the system navigation area.
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val pill = screenBounds("正在执行自动化")
        val page = screenBounds("测试页面")
        val x = page.left + 24 * compose.density.density
        assertTrue("Tap must be outside the pill: x=$x, $pill", x < pill.left - 16 * compose.density.density)
        tapOnScreen(x, pill.exactCenterY())
        compose.runOnIdle {
            assertEquals(1, taps)
            assertFalse(AutomationManager.isOverlayHidden())
        }
    }
}
