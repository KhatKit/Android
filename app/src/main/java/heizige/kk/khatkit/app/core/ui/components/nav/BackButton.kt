package heizige.kk.khatkit.app.core.ui.components.nav

import heizige.kk.khatkit.uikit.mochi.kkBarButtonColors
import ui.feice.mochi.navigation.MochiBackButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import heizige.kk.khatkit.app.core.ui.context.LocalNavController



/**
 * 返回按钮：Mochi 圆形返回（[MochiBackButton]）。全 App 60+ 处页面顶栏都用它。
 */
@Composable
fun BackButton(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val navController = LocalNavController.current
    val back = { onClick?.invoke() ?: navController.popBackStack() }
    // 照 Mochi：顶栏左上角的圆形返回（44dp + 描边「‹」，与 MochiTitleBar 的按钮同高同色）。
    // 配色按设置里的按钮样式：玻璃或实心。
    // 返回行为不变，仍是 navController.popBackStack。
    MochiBackButton(
        modifier = modifier,
        colors = kkBarButtonColors(),
        onClick = { back() },
    )
}
