package heizige.kk.khatkit.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import heizige.kk.khatkit.app.Screen
import ui.feice.mochi.navigation.MochiNavigator
import ui.feice.mochi.navigation.rememberMochiNavigator

/**
 * [Screen] 世界与 Mochi 返回栈（[MochiNavigator]，路由是 String）之间的适配器。
 *
 * U7a-3 起 RouteActivity 的唯一导航栈就是本类包着的 Mochi 栈（Nav3 的 NavDisplay 与
 * NavViewModel 已删除）；页面里的 navController.navigate(Screen.X) 仍走 LocalNavController，
 * 但背后由 RouteActivity 的 MochiBackedNavStack 转发到本类，所以 94 处调用点不用改。
 *
 * 路由字符串由 [ScreenRoutes] 决定（纯函数、可单测）；本类只做栈操作。
 *
 * @param navigator 由 [rememberKkNavController] 建的 Mochi 栈。构造器留 public 是为了后续测试/预览能包假栈，
 *   真实使用请走 remember 版本（栈要存进 rememberSaveable 才能扛旋转和进程重建）。
 */
@Stable
class KkNavController(private val navigator: MochiNavigator) {

    /** 当前栈（自底向上），只用认识的路由；不认识的 route 会被跳过。 */
    val backStack: List<Screen>
        get() = navigator.backStack.mapNotNull { ScreenRoutes.screenOf(it) }

    /** 栈顶那一页；route 不认识时为 null。 */
    val current: Screen?
        get() = ScreenRoutes.screenOf(navigator.current)

    /** 还能不能返回（栈里除根之外还有页）。 */
    val canPop: Boolean
        get() = navigator.canPop

    /**
     * 打开一页。
     *
     * @param source 来源元素的 key（Mochi 的 `mochiNavSource`）。给了才是「卡片长成整页」的 morph，
     *   不给是 iOS 侧推转场。本步只把参数留出来，暂不接任何 UI。
     */
    fun navigate(screen: Screen, source: String? = null) {
        navigator.push(ScreenRoutes.routeOf(screen), from = source)
    }

    /**
     * 返回上一页，带转场；只剩根页时返回 false（不弹空栈）。
     *
     * 同一帧连按两次是安全的：MochiNavigator.pop() 自己按「非 closing 的页数 > 1」判断，
     * 第二次到根页直接返回 false（对应上游 4af510624 在 NavDisplay.onBack 里加的 size > 1 兜底）。
     */
    fun popBackStack(): Boolean = navigator.pop()

    /** 与 [popBackStack] 同义（顶栏返回、系统返回都走这里）。 */
    fun navigateUp(): Boolean = navigator.pop()

    /** 返回到栈里第 [index] 页（0 = 根）；越界或已在那一页返回 false。 */
    fun popTo(index: Int): Boolean = navigator.popTo(index)
}

/**
 * 创建（或从 rememberSaveable 恢复）一个 Mochi 栈，并包成 [KkNavController]。
 *
 * @param root 根路由对应的页面（不能返回的那一页）。恢复已有栈时 root 只用于首次创建。
 */
@Composable
fun rememberKkNavController(root: Screen = Screen.Greeting): KkNavController {
    val navigator = rememberMochiNavigator(ScreenRoutes.routeOf(root))
    return remember(navigator) { KkNavController(navigator) }
}
