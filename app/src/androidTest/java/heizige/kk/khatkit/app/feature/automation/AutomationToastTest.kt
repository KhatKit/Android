package heizige.kk.khatkit.app.feature.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import dagger.hilt.android.EntryPointAccessors
import heizige.kk.khatkit.app.core.di.AppEntryPoint
import heizige.kk.khatkit.app.feature.automation.data.ApprovalCategory
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.khatkit.app.feature.automation.data.AutomationBus
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khatkit.uikit.KhatKitUiStyle
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AutomationToastTest {
    @get:Rule val compose = createComposeRule()

    @After fun reset() {
        AutomationBus.clear()
        AutomationBus.setAppInForeground(true)
    }

    @Test fun miuixPillWrapsContentAndUsesNativeColors() = checkPill(KhatKitUiStyle.MIUIX)
    @Test fun materialPillWrapsContent() = checkPill(KhatKitUiStyle.MATERIAL)

    private fun checkPill(style: KhatKitUiStyle) {
        var label by mutableStateOf("完成")
        var expected = Color.Unspecified
        var dismisses = 0
        compose.setContent {
            KhatKitTheme(style, darkTheme = true) {
                expected = KedgeColors.secondaryContainer
                AutomationToast(
                    status = AutomationBus.AutomationStatus(label, activeSince = 0),
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
        if (style == KhatKitUiStyle.MIUIX) {
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

    @Test fun nativeApprovalButtonsStillRespond() {
        var allowed = 0
        var denied = 0
        compose.setContent {
            KhatKitTheme(KhatKitUiStyle.MIUIX, darkTheme = true) {
                AutomationToast(status = null,
                    approval = AutomationBus.ApprovalRequest("test", "测试授权", "", ApprovalCategory.UI_ACTION),
                    onApprove = { allowed++ }, onDeny = { denied++ }, onRemember = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithContentDescription("拒绝").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("允许").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, allowed)
            assertEquals(1, denied)
        }
    }

    @Test fun backgroundWindowUsesMiuixAndLeavesSurroundingTouchesAlone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val provider = EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .khatKitToolProvider()
        val originalStyle = provider.uiStyle
        var backgroundTaps = 0
        var expected = Color.Unspecified
        var overlay: View? = null
        try {
            compose.runOnIdle { provider.uiStyle = KhatKitUiStyle.MIUIX }
            compose.setContent {
                AutomationOverlayTheme {
                    expected = KedgeColors.secondaryContainer
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag("page")
                        .clickable { backgroundTaps++ })
                }
            }
            compose.runOnIdle {
                AutomationBus.begin()
                AutomationBus.update("测试自动化")
                AutomationBus.setAppInForeground(false)
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
                assertFalse(AutomationBus.isOverlayHidden())
            }
        } finally {
            compose.runOnIdle {
                AutomationBus.clear()
                AutomationBus.setAppInForeground(true)
                provider.uiStyle = originalStyle
            }
        }
    }

    @Test fun inAppPositioningSpaceDoesNotConsumeTouches() {
        var taps = 0
        AutomationBus.setAppInForeground(true)
        AutomationBus.begin()
        AutomationBus.update("正在执行自动化")
        compose.setContent {
            KhatKitTheme(KhatKitUiStyle.MIUIX, darkTheme = true) {
                Box(Modifier.fillMaxSize().background(Color.Black).testTag("underneath")
                    .clickable { taps++ }) { Text("测试页面") }
                AutomationInAppHost()
            }
        }
        compose.onNodeWithText("正在执行自动化").assertExists()
        // 指针落在旧提示底部空白区域，而不是胶囊上。
        compose.onNodeWithTag("underneath").performTouchInput {
            click(Offset(center.x, height - 20f))
        }
        compose.runOnIdle {
            assertEquals(1, taps)
            assertFalse(AutomationBus.isOverlayHidden())
        }
    }
}
