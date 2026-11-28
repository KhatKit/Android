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
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.feice.mochi.theme.MochiGlass
import ui.feice.mochi.theme.LocalMochiMaterials

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
val LocalKkNavigationBarMaterial = compositionLocalWithComputedDefaultOf<MochiGlass?> {
    LocalMochiMaterials.currentValue.bar
}

@Composable
fun rememberNavigationBarMaterial(): MochiGlass? = LocalKkNavigationBarMaterial.current
