package heizige.kk.khatkit.app.feature.ui.shell

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TabPlacementTest {
    @Test fun tabBarRailAndSidebarFollowAvailablePaneWidth() {
        for ((width, expected) in listOf(360 to TabPlacement.Bottom, 599 to TabPlacement.Bottom,
            600 to TabPlacement.Rail, 839 to TabPlacement.Rail, 840 to TabPlacement.Sidebar,
            1280 to TabPlacement.Sidebar)) {
            assertEquals("width=$width", expected, tabPlacement(width.dp))
        }
    }
}
