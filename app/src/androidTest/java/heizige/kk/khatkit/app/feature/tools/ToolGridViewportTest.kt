package heizige.kk.khatkit.app.feature.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ui.feice.mochi.effect.overscroll.MochiOverscrollEffect

class ToolGridViewportTest {
    @get:Rule val rule = createComposeRule()

    @Test fun gridSettlesWhenCollapsingHeaderChangesLookaheadHeight() {
        val collapsing = mutableStateOf(false)
        val measuredHeights = mutableListOf<Int>()
        rule.setContent {
            LookaheadScope {
                val overscroll = remember { MochiOverscrollEffect() }
                Box(Modifier.fillMaxSize().layout { child, constraints ->
                    // Model a frame of the header animation. The predicted grid is taller
                    // than the grid on screen until the animation catches up.
                    val height = if (collapsing.value && isLookingAhead) 360 else 280
                    val grid = child.measure(Constraints.fixed(constraints.maxWidth, height))
                    layout(grid.width, grid.height) { grid.place(0, 0) }
                }) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        overscrollEffect = overscroll,
                        modifier = Modifier.fillMaxSize().layout { grid, constraints ->
                            measuredHeights.add(constraints.maxHeight)
                            check(measuredHeights.size < 80) { "Grid keeps remeasuring: ${measuredHeights.takeLast(8)}" }
                            val child = grid.measure(constraints)
                            layout(child.width, child.height) { child.place(0, 0) }
                        },
                    ) {
                        items(24) { Box(Modifier.height(60.dp)) }
                    }
                }
            }
        }
        rule.runOnIdle {
            measuredHeights.clear()
            collapsing.value = true
        }
        rule.runOnIdle {
            assertTrue("Grid heights: $measuredHeights", 360 in measuredHeights && 280 in measuredHeights)
        }
    }
}
