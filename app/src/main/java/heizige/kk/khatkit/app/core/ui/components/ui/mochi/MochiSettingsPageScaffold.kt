package heizige.kk.khatkit.app.core.ui.components.ui.mochi

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.feature.ui.shell.LocalKkBarBackdrop
import heizige.kk.khatkit.uikit.mochi.KkBarIconScope
import heizige.kk.khatkit.uikit.mochi.MochiLargeTitleTopBar
import heizige.kk.khatkit.app.feature.ui.shell.MochiPageScaffold
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/**
 * 设置/详情类页面的统一骨架：Mochi 标题栏 + Mochi 分组页底色，正文拿到 PaddingValues。
 *
 * 顶栏是 Mochi 自己的标题栏（状态栏内边距、栏里的按钮、滚动边缘渐隐都由组件给），
 * 页面底色取 MochiDefaults.groupedBackground()，正文左右边距由各页面自己负责 ——
 * 与 Mochi 分组列表 MochiListSection 的 16dp 外边对齐即可。
 *
 * [largeTitle] 由卡片的 `topbar = "large"` 传入时启用。正文仍由卡片自己持有
 * 滚动容器，外层只读取 [titleProgress]，因此大标题收起不会再套一层滚动。
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
    largeTitle: Boolean = false,
    showTopBar: Boolean = true,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior? = null,
    /** 见 MochiPageScaffold.edgeFade：正文顶 / 底有固定不滚的东西时传 false，自己把 kkScrollEdgeFade 加到列表上 */
    edgeFade: Boolean = true,
    /**
     * 顶栏小标题的显隐进度（0 = 藏起来，1 = 完全显示）。
     *
     * 页面正文自带滚动容器、又希望「栏里的标题等正文大标题滚走后再出现」时传它：
     * 由页面读自己的滚动状态算进度（见 CardWidgetPage 的 rememberCardTitleProgress）。
     * 不传 = 常显（{ 1f }），与既有调用点行为一致。
     */
    titleProgress: (() -> Float)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    MochiPageScaffold(
        modifier = modifier,
        topBar = {
            if (showTopBar) {
                if (largeTitle) {
                    MochiLargeTitleTopBar(
                        title = title,
                        navigationIcon = { KkBarIconScope { navigationIcon() } },
                        actions = { KkBarIconScope { actions() } },
                        titleProgress = titleProgress ?: { 0f },
                        backgroundProgress = titleProgress ?: { 0f },
                        backdrop = LocalKkBarBackdrop.current,
                    )
                } else {
                    MochiTitleBar(
                        title = title,
                        navigationIcon = { KkBarIconScope { navigationIcon() } },
                        actions = { KkBarIconScope { actions() } },
                        background = MochiTheme.colorScheme.background,
                        // 骨架（MochiPageScaffold）统一录正文、下发来源；材质没开玻璃时是 null
                        backdrop = LocalKkBarBackdrop.current,
                        titleProgress = titleProgress ?: { 0f },
                    )
                }
            }
        },
        bottomBar = bottomBar,
        rail = rail,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = MochiDefaults.groupedBackground(),
        edgeFade = edgeFade,
    ) { innerPadding ->
        content(innerPadding)
    }
}

/** 与 Mochi 分组卡片一致的内容左右边距（页面正文自己 padding 时用它，别再写死 16dp）。 */
val MochiPageContentPadding = PaddingValues(horizontal = 16.dp)
