package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import heizige.kk.kedge.adaptive.KedgeLargeTopAppBar
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle

/**
 * 页面大标题栏：Miuix 走 Kedge，MD3Exp 保持原样。
 *
 * Miuix 的 TopAppBar 只接受字符串标题，因此这里以 [title] 为准；
 * 需要图标/多行等富标题时用 [titleContent]，但 Miuix 下仍显示 [title]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KedgePageLargeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    titleContent: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    /** 标题整体透明度。0=标题隐藏（仅留返回按钮），随滚动淡入即 KSU 的 About 页行为。 */
    titleAlpha: Float = 1f,
) {
    val alpha = titleAlpha.coerceIn(0f, 1f)
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        KedgeLargeTopAppBar(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            titleContent = titleContent,
            titleAlpha = alpha,
            // Miuix 下必须用 KedgePageScaffold 下发的那个行为（KedgeLargeTopAppBar
            // 内部会回退到 LocalKedgePageScrollBehavior）。传进来的 MD3 behavior
            // 在 Miuix 下不可用；以前这里自己 remember 一个，等于顶栏观察的是
            // 一个没人喂滚动的实例 -> 列表能滚但大标题不收缩。
            scrollBehavior = null,
        )
    } else {
        LargeFlexibleTopAppBar(
            // 用 graphicsLayer 而不是 colors，才能压住 LargeFlexibleTopAppBar
            // 内部自绘的大标题 + 收起后的小标题（两者同一 slot）。
            title = titleContent ?: {
                Text(
                    text = title,
                    modifier = Modifier.graphicsLayer { this.alpha = alpha },
                )
            },
            modifier = modifier,
            // 调用方传了 titleContent 时，副标题由 titleContent 自行渲染
            // （ChatPage 的 titleContent 内含「助手/模型/提供商」一行）。
            // 此处再传 subtitle 会出现两个副标题。
            subtitle = if (titleContent != null) {
                null
            } else {
                subtitle?.let { { Text(it) } }
            },
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = colors,
        )
    }
}


/** 页面中等标题栏（MD3 MediumTopAppBar，高度用组件默认值）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KedgePageMediumTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.mediumTopAppBarColors(),
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
) {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        // 传 titleContent：ChatPage 的标题要承载「点标题选模型 / 长按改名 / 搜索 morph」，
        // Miuix 原生 bar 的 title 是 String 放不下这些交互。
        // Kedge 侧在 titleContent != null 时改走自绘栏；该栏不设独立底色，
        // 与页面背景同为 surface（对齐 KernelSU 顶栏/背景不分色）。
        heizige.kk.kedge.adaptive.KedgeTopAppBar(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            titleContent = titleContent,
        )
    } else {
        MediumTopAppBar(
            title = titleContent ?: { Text(title) },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = colors,
        )
    }
}

/** 页面小标题栏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KedgePageTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
) {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        heizige.kk.kedge.adaptive.KedgeTopAppBar(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            titleContent = titleContent,
        )
    } else {
        TopAppBar(
            title = titleContent ?: { Text(title) },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = colors,
        )
    }
}
