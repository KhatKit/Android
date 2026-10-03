package heizige.kk.khatkit.app.core.ui.context

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * 可空：`null` 表示「这一段不要参与共享元素转场」。
 *
 * 之前是非空 + `error(...)`，于是任何子 composition 都只能被动继承 Activity 那个
 * scope，没法局部关掉。现在允许置空，配合 [NoHeroTransition] 使用。
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * 让一段内容不参与 shared element 转场。
 *
 * 搜索框的全屏层（[heizige.kk.kedge.components.KedgeSearchBar] 展开后的结果区）是
 * **独立窗口**（MD3 是 Dialog，Miuix 现在也是 Dialog）。而结果列表里的头像带着
 * `.heroAnimation(...)`，那是 `sharedElement`：`SharedElement` 在 lookahead 阶段
 * 会拿匹配到的另一端做 `localLookaheadPositionOf`，两个端点一旦分属不同 ViewRoot，
 * 就直接抛 `IllegalArgumentException: layouts are not part of the same hierarchy`
 * 把整个页面带崩。
 *
 * 搜索结果本来就是「从列表点进详情」，不需要 hero 转场，所以整段置空最省事。
 * 见 `SearchPage` / `AssistantPage` 等处的 `expandedContent`。
 */
@Composable
fun NoHeroTransition(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSharedTransitionScope provides null) {
        content()
    }
}
