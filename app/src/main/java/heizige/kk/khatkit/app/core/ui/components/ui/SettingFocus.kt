package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

/**
 * 设置搜索的「定位到某一具体设置项」请求。
 *
 * 搜索页搜到的结果可以是**一级页面**（「通知」这种整页入口），也可以是**页面里的某一行**
 * （「显示时间」这种最小选项）。后者跳过去以后还得让用户看见它 —— 页面自己不滚到那里、
 * 也没有任何视觉提示的话，等于跳了个寂寞。所以这里放一个跨页的定位请求：
 *
 * - 搜索页在 navigate(page) **之前**调 [request]，把「目标 route + 行锚点」记下来；
 * - 目标页的骨架（MochiSettingsPageScaffold）读 [LocalSettingFocusKey]，与该 route 匹配的
 *   那一行描一圈主色边、并 bringIntoView；
 * - 高亮是**一次性**的：读完即 [clear]，2.4 秒后自行消失，不会在用户后续操作里阴魂不散。
 *
 * 为什么用进程内状态而不是往路由里塞参数：设置页有 20 多个 Screen 数据对象，每个都要变成
 * 带 focus 参数的 data class，ScreenRoutes 的互逆映射、深链、单测全都要跟着改。定位是一次
 * 点击的瞬时意图，不需要跨进程恢复（进程重建后用户看到的是目标页本身，已是可接受结果），
 * 单例状态是这里更小的改动面。
 */
@Stable
object SettingFocusController {
    /** 当前待消费的定位请求；没有就是 null。 */
    var request by mutableStateOf<SettingFocusRequest?>(null)
        private set

    fun request(route: String, anchor: String) {
        request = SettingFocusRequest(route = route, anchor = anchor)
    }

    /** 目标页消费掉请求后清空，避免下次再进这一页又高亮一次。 */
    fun clear() {
        request = null
    }
}

/** 一次定位请求：目标页 route + 行锚点（锚点是行自己声明的稳定字符串，不是下标）。 */
@Immutable
data class SettingFocusRequest(
    val route: String,
    val anchor: String,
)

/**
 * 当前页面应当高亮的行锚点；由设置页骨架按 [SettingFocusController] 的请求算出后下发。
 * 组外（非设置页骨架）恒为 null，行组件读不到就什么都不做。
 */
val LocalSettingFocusKey = compositionLocalOf<String?> { null }

/**
 * 读取并消费面向 route 这一页的定位请求，返回 2.4 秒内需要高亮的锚点。
 *
 * 只在页面骨架里调用一次（见 MochiSettingsPageScaffold）：每个 CardGroup 各调一次会变成
 * 每页 N 个 collector。
 */
@Composable
fun rememberSettingFocusKey(route: String?): String? {
    var active by remember(route) { mutableStateOf<String?>(null) }
    LaunchedEffect(route) {
        if (route == null) return@LaunchedEffect
        snapshotFlow { SettingFocusController.request }
            .filterNotNull()
            .filter { it.route == route }
            .collect { pending ->
                active = pending.anchor
                // 消费掉：同一次点击只高亮一次，页面重组 / 再次进入都不重放。
                SettingFocusController.clear()
                delay(2400)
                active = null
            }
    }
    return active
}

/**
 * 高亮自己：命中锚点时描一圈主色边，并把这条滚进可视区。
 *
 * 颜色由调用点做动画，本函数只负责挂边和滚动。
 */
fun Modifier.kkSettingFocusHighlight(
    focused: Boolean,
    color: Color,
    shape: Shape,
): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) {
            // 等这一帧布局稳定再滚：刚进页面时行还没测量，立刻 bringIntoView 会滚偏。
            delay(48)
            requester.bringIntoView()
        }
    }
    this
        .bringIntoViewRequester(requester)
        .border(width = 2.dp, color = color, shape = shape)
}
