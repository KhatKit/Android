package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.glass.MochiBackdrop
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/**
 * P4a：Kedge 顶栏族的 Mochi 版（不再按 旧风格枚举 分叉，也不再落到 material3 顶栏）。
 *
 * 参数名沿用旧 Kedge 组件，调用点只改名字；Miuix/Kedge 专有参数
 * （scrollBehavior / backdrop / colors）只收不用 —— 折叠与毛玻璃由 P6 的
 * Mochi 自建件接手，这里先保证「可见的顶栏」是 Mochi 皮肤。
 */

private val BarHeight = 56.dp

@Composable
private fun BarTitle(title: String, subtitle: String?, titleAlpha: Float, centered: Boolean, titleContent: (@Composable () -> Unit)?) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (titleContent != null) {
            titleContent()
        } else {
            Text(
                text = title,
                color = MochiTheme.colorScheme.onSurface.copy(alpha = titleAlpha.coerceIn(0f, 1f)),
                style = MochiTheme.textStyles.navTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = titleAlpha.coerceIn(0f, 1f)),
                style = MochiTheme.textStyles.footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MochiBarFrame(
    modifier: Modifier,
    container: Color,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    titleSlot: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .then(Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(modifier = Modifier, contentAlignment = Alignment.Center) { navigationIcon() }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { titleSlot() }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** 小标题栏（单行标题 + 导航/操作槽）。 */
@Composable
fun MochiSmallTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleAlpha: Float = 1f,
    barColorAlpha: Float? = null,
    scrollBehavior: Any? = null,
    /** 栏按钮玻璃的取样来源；null = 按材质 fallback 画纯色（旧调用点不传，行为不变）。 */
    backdrop: MochiBackdrop? = null,
) {
    // TOP1：顶栏换成 Mochi 真组件 MochiTitleBar（自带滚动边缘渐隐/玻璃取样/共享返回按钮），
    // 不再自建 Row+Text。barColorAlpha / scrollBehavior 为旧调用点参数，收而不用。
    MochiTitleBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        background = MochiDefaults.groupedBackground(),
        titleProgress = { titleAlpha.coerceIn(0f, 1f) },
        backdrop = backdrop,
    )
}

/** 大标题栏（标题 + 副标题 + 导航/操作槽）。折叠动画留给 P6。 */
@Composable
fun MochiLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    titleAlpha: Float = 1f,
    scrollBehavior: Any? = null,
    translucentTopBar: Boolean = true,
    /** 栏按钮玻璃的取样来源（卡片弹层页面自己录）；null = 纯色。 */
    backdrop: MochiBackdrop? = null,
) {
    // TOP1：大标题栏也走 Mochi 真组件；折叠交给 MochiTitleBar 的 titleProgress（调用点可继续传 titleAlpha）。
    // 旧参数 scrollBehavior / translucentTopBar 收而不用；titleContent 自定义标题槽保留原实现。
    if (titleContent != null) {
        MochiBarFrame(
            modifier = modifier,
            container = MochiTheme.colorScheme.background,
            navigationIcon = navigationIcon,
            actions = actions,
        ) {
            BarTitle(title = title, subtitle = subtitle, titleAlpha = titleAlpha, centered = false, titleContent = titleContent)
        }
    } else {
        MochiTitleBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            navigationIcon = navigationIcon,
            actions = actions,
            background = MochiDefaults.groupedBackground(),
            titleProgress = { titleAlpha.coerceIn(0f, 1f) },
            backdrop = backdrop,
        )
    }
}

/** 中/小号顶栏（带居中可选项）。 */
@Composable
fun MochiTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    centered: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    scrollBehavior: Any? = null,
    colors: Any? = null,
    titleAlpha: Float = 1f,
) {
    // TOP1：中/小号顶栏同样换成 Mochi 真组件；centered 是旧 Kedge 参数（Mochi 顶栏固定左对齐），收而不用。
    if (titleContent != null) {
        MochiBarFrame(
            modifier = modifier,
            container = MochiTheme.colorScheme.background,
            navigationIcon = navigationIcon,
            actions = actions,
        ) {
            BarTitle(title = title, subtitle = subtitle, titleAlpha = titleAlpha, centered = centered, titleContent = titleContent)
        }
    } else {
        MochiTitleBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            navigationIcon = navigationIcon,
            actions = actions,
            background = MochiDefaults.groupedBackground(),
            titleProgress = { titleAlpha.coerceIn(0f, 1f) },
        )
    }
}
