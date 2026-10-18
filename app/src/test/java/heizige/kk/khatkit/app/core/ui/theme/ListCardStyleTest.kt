package heizige.kk.khatkit.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 列表卡片的**软件圆角**口径：一组卡片拼成一张卡 —— 首项大上圆角、末项大下圆角、
 * 中间项全小圆角，组内间距取 `gap`。
 *
 * 聊天页小组件、AI 提示行、设置页选项行都吃这一份，所以这里锁住四种形态；
 * 脚本（Lua 卡片）拿不到这个数，观感只能由宿主决定。
 */
class ListCardStyleTest {

    private val style = ListCardStyle(largeCorner = 20.dp, smallCorner = 4.dp, gap = 2.dp)

    @Test
    fun singleItemIsFullyRounded() {
        assertEquals(RoundedCornerShape(20.dp), style.indexedShape(0, 1))
    }

    @Test
    fun firstItemRoundsTopOnly() {
        assertEquals(
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            style.indexedShape(0, 3),
        )
    }

    @Test
    fun middleItemKeepsSmallCornersOnly() {
        assertEquals(RoundedCornerShape(4.dp), style.indexedShape(1, 3))
    }

    @Test
    fun lastItemRoundsBottomOnly() {
        assertEquals(
            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
            style.indexedShape(2, 3),
        )
    }

    @Test
    fun miuixGroupHasNoGapAndSquareInnerCorners() {
        assertEquals(0.dp, MiuixListCardStyle.gap)
        assertEquals(RoundedCornerShape(0.dp), MiuixListCardStyle.indexedShape(1, 3))
        assertEquals(
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            MiuixListCardStyle.indexedShape(0, 3),
        )
    }

    @Test
    fun independentItemsModeKeepsEveryCardFullyRounded() {
        val independent = style.copy(independentItems = true)
        assertEquals(RoundedCornerShape(20.dp), independent.indexedShape(0, 3))
        assertEquals(RoundedCornerShape(20.dp), independent.indexedShape(1, 3))
        assertEquals(RoundedCornerShape(20.dp), independent.indexedShape(2, 3))
    }
}
