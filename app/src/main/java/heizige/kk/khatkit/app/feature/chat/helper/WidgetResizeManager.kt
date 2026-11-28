package heizige.kk.khatkit.app.feature.chat.helper

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ui.feice.mochi.anim.MochiSprings
import heizige.kk.khatkit.app.feature.chat.data.ResizeEdge



/**
 * 一张小组件的拖动尺寸状态。
 *
 * 手指拖动期间 [frame] 是像素级的「跟手」尺寸：越过 min/max 时走 Mochi 的橡皮筋；
 * 格数（[WidgetCells]）按四舍五入吸附，越过一格回调一次。松手后立即收回到吸附后的
 * 格子尺寸，然后清空，布局重新只认格子，避免确认阶段持续触发自定义测量。
 */
@Stable
internal class WidgetResizeState(private val scope: CoroutineScope) {
    /** 正在拖的那条边；null 表示没在拖。 */
    var edge by mutableStateOf<ResizeEdge?>(null)
        private set

    /** 跟手的像素级预览尺寸；null = 认格子尺寸（见 [widgetResizePreview]）。 */
    var preview by mutableStateOf<Size?>(null)
        private set

    private var settleJob: Job? = null

    fun start(edge: ResizeEdge, from: Size) {
        settleJob?.cancel()
        this.edge = edge
        preview = null
    }

    fun follow(size: Size) {
        settleJob?.cancel()
        preview = size
    }

    /**
     * 松手：把预览从当前像素尺寸用 spring 收回吸附后的格子尺寸，然后清空。
     * 弹簧参数与页面其它过渡同一套（MochiSprings），收回的过程就是「变大变小」的收尾动画。
     */
    fun settle(to: Size, reduceMotion: Boolean) {
        edge = null
        settleJob?.cancel()
        val from = preview
        if (from == null || reduceMotion) {
            preview = null
            return
        }
        settleJob = scope.launch {
            Animatable(0f).animateTo(1f, MochiSprings.snappy(320)) {
                preview = Size(
                    from.width + (to.width - from.width) * value,
                    from.height + (to.height - from.height) * value,
                )
            }
            preview = null
        }
    }
}
