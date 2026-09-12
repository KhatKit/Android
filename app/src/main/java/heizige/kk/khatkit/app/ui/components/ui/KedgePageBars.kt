package heizige.kk.khatkit.app.ui.components.ui

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
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
) {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        KedgeLargeTopAppBar(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    } else {
        LargeFlexibleTopAppBar(
            title = titleContent ?: { Text(title) },
            modifier = modifier,
            subtitle = subtitle?.let { { Text(it) } },
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
        heizige.kk.kedge.adaptive.KedgeTopAppBar(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
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
