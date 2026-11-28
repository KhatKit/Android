package heizige.kk.khatkit.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.uikit.KhatKitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ui.feice.mochi.navigation.MochiNavHost

class ReopenChatNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sameHistorySelectionDrawsChatOnEveryVisitWithoutRecreatingActivity() {
        val selected = Screen.Chat("selected")
        val history = ScreenRoutes.routeOf(Screen.History)
        compose.setContent {
            KhatKitTheme {
                ReplaceableNavigationRoot(initialRoot = { Screen.Chat("initial") }) { navigator, replace ->
                    MochiNavHost(navigator = navigator) { route ->
                        val isHistory = route == history
                        val color = when (route) {
                            history -> Color.Blue
                            ScreenRoutes.routeOf(selected) -> Color.Green
                            else -> Color.Red
                        }
                        Box(
                            Modifier.fillMaxSize().background(color).testTag(route).clickable {
                                if (isHistory) replace(selected) else navigator.push(history)
                            }
                        )
                    }
                }
            }
        }

        var chat = ScreenRoutes.routeOf(Screen.Chat("initial"))
        repeat(2) {
            compose.onNodeWithTag(chat).performClick()
            compose.onNodeWithTag(history).performClick()
            chat = ScreenRoutes.routeOf(selected)
            // A cached blank layer can still expose the correct semantics. Check the drawn frame.
            compose.waitForIdle()
            val frame = compose.onRoot().captureToImage().toPixelMap()
            assertEquals(Color.Green, frame[frame.width / 2, frame.height / 2])
        }
    }
}
