package heizige.kk.khatkit.app.ui.theme

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
) {
    fun groupItemShape(isFirst: Boolean, isLast: Boolean): RoundedCornerShape = when {
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

    fun indexedShape(index: Int, count: Int): RoundedCornerShape =
        groupItemShape(isFirst = index == 0, isLast = index == count - 1)
}

val LocalListCardStyle = staticCompositionLocalOf { ListCardStyle() }

@Composable
@ReadOnlyComposable
fun listCardStyle(): ListCardStyle = LocalListCardStyle.current
