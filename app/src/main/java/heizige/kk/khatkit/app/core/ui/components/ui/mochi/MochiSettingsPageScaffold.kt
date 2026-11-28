package heizige.kk.khatkit.app.core.ui.components.ui.mochi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.LocalPageRoute
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.LocalSettingFocusKey
import heizige.kk.khatkit.app.core.ui.components.ui.rememberSettingFocusKey
import heizige.kk.khatkit.app.feature.ui.shell.LocalKkBarBackdrop
import heizige.kk.khatkit.uikit.mochi.KkBarIconScope
import heizige.kk.khatkit.uikit.mochi.MochiPageTitleBar
import heizige.kk.khatkit.app.feature.ui.shell.MochiPageScaffold
import ui.feice.mochi.theme.MochiTheme

/**
 * 设置/详情类页面的统一骨架：Mochi 标题栏 + Mochi 分组页底色，正文拿到 PaddingValues。
 *
 * 顶栏沿用 Mochi 的安全区、按钮及标题转场，
 * 页面底色取 MochiTheme.colorScheme.background（与聊天页同一角色），正文左右边距由各页面自己负责 ——
 * 与 Mochi 分组列表 MochiListSection 的 16dp 外边对齐即可。
 *
 * 大标题默认随手势折叠。提供 [titleProgress] 的页面可以直接用自己的滚动状态控制标题。
 */
@Composable
fun MochiSettingsPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = { BackButton() },
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    rail: (@Composable () -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: androidx.compose.material3.FabPosition =
        androidx.compose.material3.FabPosition.End,
    /** 默认像 Mochi 示例子页：大标题常显，往上滚收起后顶栏才出现小标题。 */
    largeTitle: Boolean = true,
    showTopBar: Boolean = true,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior? = null,
    /** 是否启用正文底边的渐隐。 */
    edgeFade: Boolean = true,
    /**
     * 顶栏小标题的显隐进度（0 = 藏起来，1 = 完全显示）。
     *
     * 页面正文自带滚动容器、又希望「栏里的标题等正文大标题滚走后再出现」时传它：
     * 由页面读自己的滚动状态算进度（见 CardWidgetPage 的 rememberCardTitleProgress）。
     * 不传时，大标题模式跟随手势，紧凑模式常显。
     */
    titleProgress: (() -> Float)? = null,
    /**
     * 允许正文滚过顶栏下方。列表将顶部留白作为 contentPadding 使用。
     */
    scrollUnderTopBar: Boolean = false,
    /** 栏下渐进模糊的进度；不传时跟随大标题收起进度。 */
    scrollEdgeProgress: (() -> Float)? = null,
    /**
     * 盖在顶栏上的内容槽（例如聊天页那种「点搜索，标题位置换成输入框」）。
     *
     * 在栏的同一个 Box 里画，接收 BoxScope，调用点自己 align 到栏里要盖的位置；
     * 栏里的按钮仍然在上面吃触摸，所以标题区可以放心放输入框。
     */
    overlayTitle: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 设置搜索定位：搜索页在 navigate 之前请求「本页的哪一行」，这里消费掉并下发给组内的行。
    // 锚点由行自己声明（CardGroup 的 anchor / settingItem 的标题资源派生），未命中就是 null，
    // 所有行的行为与改造前一致。
    val focusKey = rememberSettingFocusKey(LocalPageRoute.current)
    MochiPageScaffold(
        modifier = modifier,
        topBar = {
            Box(Modifier.fillMaxWidth()) {
                if (showTopBar) {
                    MochiPageTitleBar(
                        title = title,
                        navigationIcon = { KkBarIconScope { navigationIcon() } },
                        actions = { KkBarIconScope { actions() } },
                        background = MochiTheme.colorScheme.background,
                        backdrop = LocalKkBarBackdrop.current,
                        titleProgress = titleProgress,
                        collapsible = largeTitle,
                    )
                }
                overlayTitle?.invoke(this)
            }
        },
        bottomBar = bottomBar,
        rail = rail,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        // 页面底色跟**聊天页**一致：ChatPage 的 MochiPageScaffold 不传 containerColor，
        // 用的就是骨架默认值 MochiTheme.colorScheme.background；这里原来传 groupedBackground()
        // （深色下 surfaceContainerLowest）比聊天页暗一档，从聊天进设置会有一下明显的底色跳变。
        // 分组卡片仍用更亮一档的 groupedContainer()，层级关系不变。
        containerColor = MochiTheme.colorScheme.background,
        edgeFade = edgeFade,
        scrollUnderTopBar = scrollUnderTopBar && showTopBar,
        scrollEdgeProgress = scrollEdgeProgress,
    ) { innerPadding ->
        // 设置搜索定位：搜索页在 navigate 之前请求「本页的哪一行」，这里消费掉并下发给组内的行。
        // 锚点由行自己声明（CardGroup 的 anchor / settingItem 的标题资源派生），未命中的页
        // 拿到 null，所有行行为与改造前完全一致。
        CompositionLocalProvider(LocalSettingFocusKey provides focusKey) {
            content(innerPadding)
        }
    }
}

/** 与 Mochi 分组卡片一致的内容左右边距（页面正文自己 padding 时用它，别再写死 16dp）。 */
val MochiPageContentPadding = PaddingValues(horizontal = 16.dp)
