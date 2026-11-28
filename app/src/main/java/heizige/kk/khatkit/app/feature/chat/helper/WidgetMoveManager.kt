package heizige.kk.khatkit.app.feature.chat.helper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import ui.feice.mochi.anim.MochiSprings

/**
 * 编辑态拖动换位。被拖那格跟着手指走（[translation] = 起点 + 位移 − 当前格位），
 * 它的中心压到别的格子上就换到那一格的序号，其它格子直接让位；
 * 松手后从手指位置弹回自己的新格位。
 */
@Stable
internal class WidgetMoveState(private val scope: CoroutineScope) {
    private val settle = Animatable(Offset.Zero, Offset.VectorConverter)
    private var origin by mutableStateOf(Offset.Zero)
    private var total by mutableStateOf(Offset.Zero)

    /** 正被拖的组件名。 */
    var name by mutableStateOf<String?>(null)
        private set

    /** 刚松手、正在弹回格位的组件名。 */
    var settling by mutableStateOf<String?>(null)
        private set

    /** 上一次换位压到的格子；手指离开它之前不再换，免得大小不一的格子来回抖。 */
    var hovered: String? = null

    fun lifted(card: String) = card == name || card == settling

    /** [card] 相对自己格位左上角 [slot] 的平移。 */
    fun translation(card: String, slot: Offset): Offset = when (card) {
        name -> origin + total - slot
        settling -> settle.value
        else -> Offset.Zero
    }

    fun start(card: String, slot: Offset) {
        name = card
        settling = null
        origin = slot
        total = Offset.Zero
        hovered = card
    }

    /** 返回被拖那格此刻的左上角（网格坐标）。 */
    fun drag(delta: Offset): Offset {
        total += delta
        return origin + total
    }

    fun drop(slot: Offset, reduceMotion: Boolean) {
        val card = name ?: return
        val from = origin + total - slot
        name = null
        hovered = null
        if (reduceMotion) return
        settling = card
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                settle.snapTo(from)
                settle.animateTo(Offset.Zero, MochiSprings.snappy(400))
            } finally {
                if (settling == card) settling = null
            }
        }
    }
}
