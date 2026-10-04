package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 聊天列表右侧滚动条滑块位置的算术。
 *
 * [resolveScrollbarProgress] 是把 `d2be1c3f`（2026-09-13「滚动条改纯索引比例+5%量化」）
 * 里原本长在组合期的算式原样搬出来的纯函数，**公式一字未改**，只是搬到了可断言的地方：
 * 组合期读 `LazyListState` 属于 `FrequentlyChangingValue`，而滑块画在 `Canvas` 的 draw
 * lambda 里，`Canvas` 与 `LazyListState` 都无法在 JVM 单测里构造。这里能钉住的是算式本身，
 * 钉不住的是「真机滚动时读到的 layoutInfo 对不对」——那一条仍需仪器测试或真机手测。
 *
 * 端点语义是这套算式里最容易改坏的地方，所以逐条钉死：
 * - 总条数为 1（或 0，被夹成 1）时进度恒为 0f；
 * - 已到底（`canScrollForward = false`）强制 1f，已到顶（`canScrollBackward = false`）强制 0f；
 * - 两端同时为 false（列表根本滚不动）时，**后判定的 `canScrollBackward` 覆盖前者**，结果 0f。
 *   这条是逐字保留 `d2be1c3f` 的判定顺序，不是笔误：滚不动的列表滑块停在顶部。
 */
class ChatListScrollbarTest {

    private fun progressOf(
        total: Int,
        firstIndex: Int,
        offsetFraction: Float = 0f,
        canScrollForward: Boolean = true,
        canScrollBackward: Boolean = true,
    ) = resolveScrollbarProgress(
        total = total,
        firstIndex = firstIndex,
        offsetFraction = offsetFraction,
        canScrollForward = canScrollForward,
        canScrollBackward = canScrollBackward,
    )

    // 1. 顶部 / 底部：索引线性映射到 0f / 1f
    @Test
    fun topAndBottom_mapToZeroAndOne() {
        assertEquals(0f, progressOf(total = 100, firstIndex = 0), 1e-6f)
        assertEquals(1f, progressOf(total = 100, firstIndex = 99), 1e-6f)
    }

    // 2. 中间按索引线性插值：(firstIndex) / (total - 1)
    @Test
    fun middle_isLinearInFirstIndex() {
        assertEquals(0.5f, progressOf(total = 101, firstIndex = 50), 1e-6f)
        assertEquals(0.25f, progressOf(total = 5, firstIndex = 1), 1e-6f)
    }

    // 3. 首项内部偏移以分数形式加在分子上：滚到第 3 项的一半 = 2.5 / (total - 1)
    @Test
    fun offsetFraction_addsHalfOfAnItem() {
        assertEquals(2.5f / 9f, progressOf(total = 10, firstIndex = 2, offsetFraction = 0.5f), 1e-6f)
    }

    // 4. 结果恒在 0f..1f：越界索引（导入数据错乱 / 预取项）不会把滑块画出轨道
    @Test
    fun outOfRangeIndex_isClampedIntoZeroOne() {
        assertEquals(1f, progressOf(total = 10, firstIndex = 99), 1e-6f)
        assertEquals(0f, progressOf(total = 10, firstIndex = -5), 1e-6f)
    }

    // 5. 已到底 / 已到顶强制贴边，覆盖索引算出来的中间值
    @Test
    fun ends_arePinnedByScrollFlags() {
        // 索引说自己在中间，但已经不能往下滚 → 贴底
        assertEquals(1f, progressOf(total = 100, firstIndex = 40, canScrollForward = false), 1e-6f)
        // 同理贴顶
        assertEquals(0f, progressOf(total = 100, firstIndex = 40, canScrollBackward = false), 1e-6f)
    }

    // 6. 滚不动的列表（两端都不能滚）：后判定的 canScrollBackward 覆盖前者，结果 0f。
    //    逐字保留 d2be1c3f 的判定顺序，别"顺手修正"成 1f。
    @Test
    fun unscrollableList_pinsToTop() {
        assertEquals(0f, progressOf(total = 3, firstIndex = 1, canScrollForward = false, canScrollBackward = false), 1e-6f)
    }

    // 7. 总条数 <= 1：分母为 0，不做除法，进度为 0f（再由端点标志覆盖）
    @Test
    fun singleItemList_hasZeroProgress() {
        assertEquals(0f, progressOf(total = 1, firstIndex = 0), 1e-6f)
        assertEquals(0f, progressOf(total = 0, firstIndex = 0), 1e-6f)
        // 已到底的单项列表贴底
        assertEquals(1f, progressOf(total = 1, firstIndex = 0, canScrollForward = false), 1e-6f)
    }
}