package heizige.kk.khatkit.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局列表卡片圆角与间距（照搬 KodeHead）。
 *
 * 组内规则：首项上圆角、末项下圆角用 [largeCorner]（默认 20dp），
 * 其余角用 [smallCorner]（默认 4dp），项间距 [gap]（默认 2dp）。
 * 数值可在 设置 → 界面 → 列表卡片样式 中自定义。
 */
data class ListCardStyle(
    val largeCorner: Dp = 20.dp,
    val smallCorner: Dp = 4.dp,
    val gap: Dp = 2.dp,
    /**
     * Miuix 模式下每项都是独立圆角卡片（不分组）。
     * 见 `Theme.kt`：Miuix 用 largeCorner=20dp / gap=12dp / 每项同圆角。
     */
    val independentItems: Boolean = false,
) {
    fun groupItemShape(isFirst: Boolean, isLast: Boolean): RoundedCornerShape = when {
        independentItems -> RoundedCornerShape(largeCorner)
        else -> when {
        isFirst && isLast -> RoundedCornerShape(largeCorner)
        isFirst -> RoundedCornerShape(
            topStart = largeCorner,
            topEnd = largeCorner,
            bottomStart = smallCorner,
            bottomEnd = smallCorner,
        )

        isLast -> RoundedCornerShape(
            topStart = smallCorner,
            topEnd = smallCorner,
            bottomStart = largeCorner,
            bottomEnd = largeCorner,
        )

        else -> RoundedCornerShape(smallCorner)
        }
    }

    fun indexedShape(index: Int, count: Int): RoundedCornerShape =
        groupItemShape(isFirst = index == 0, isLast = index == count - 1)
}

/**
 * Miuix 观感的列表卡片样式：**同一组内的选项是合在一起的**——项之间不留间距，
 * 首项只圆上边、末项只圆下边，中间项四角为直角，整组看是一张卡片。
 *
 * 与 MD3 分组的区别只在配色/圆角大小（走 Miuix 的 surfaceContainer 与 20dp），
 * 分组形态本身一致；所以不要开 [ListCardStyle.independentItems]，也不要有 [ListCardStyle.gap]。
 *
 * 取值统一走 [listCardStyle]：它会按当前风格返回，Miuix 下必定是这个「合在一起」的
 * 形态，不依赖谁去下发 [LocalListCardStyle]（Miuix 走 `:khatkit-ui` 的 KhatKitTheme，
 * 经过的应用主题层数与 MD3Exp 不同，靠 provider 传容易漏）。
 */
val MiuixListCardStyle = ListCardStyle(
    largeCorner = 20.dp,
    smallCorner = 0.dp,
    gap = 0.dp,
    independentItems = false,
)

val LocalListCardStyle = staticCompositionLocalOf { ListCardStyle() }

@Composable
@ReadOnlyComposable
fun listCardStyle(): ListCardStyle =
    if (heizige.kk.kedge.theme.LocalKedgeStyle.current == heizige.kk.kedge.theme.KedgeStyle.Miuix) {
        MiuixListCardStyle
    } else {
        LocalListCardStyle.current
    }
