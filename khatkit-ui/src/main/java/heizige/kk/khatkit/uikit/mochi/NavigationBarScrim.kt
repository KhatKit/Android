package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.feice.mochi.theme.MochiGlass
import ui.feice.mochi.theme.MochiTheme

/** The handle sits on the page color, with a short transition into the scrolling content. */
@Composable
fun NavigationBarScrim(background: Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(36.dp).background(
            Brush.verticalGradient(listOf(background.copy(alpha = 0f), background)),
        ))
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(background))
    }
}

/** Glass has its own outer shadow, separate from the plain tab bar's shadow color. */
@Composable
fun rememberNavigationBarMaterial(): MochiGlass? {
    val material = MochiTheme.materials.bar ?: return null
    return remember(material) {
        material.copy(
            light = material.light.copy(shadowOpacity = 0f),
            dark = material.dark.copy(shadowOpacity = 0f),
            byLuma = material.byLuma?.let { recipe ->
                { luma, light -> recipe(luma, light).copy(shadowOpacity = 0f) }
            },
        )
    }
}
