package heizige.kk.khatkit.app.core.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import heizige.kk.khatkit.app.core.ui.context.LocalSharedTransitionScope

/**
 * 列表项 → 详情页的共享元素转场。
 *
 * `LocalSharedTransitionScope` 允许为 `null`：搜索框全屏层是独立窗口，而它里面
 * 也会有带 `heroAnimation` 的列表项，共享元素跨 ViewRoot 配对会崩。用
 * [heizige.kk.khatkit.app.core.ui.context.NoHeroTransition] 把那一段置空即可，
 * 这里就直接跳过。
 */
@Composable
fun Modifier.heroAnimation(
    key: Any,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedTransitionScope) {
        this@heroAnimation.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}
