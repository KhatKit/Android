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
    backdrop: Any? = null,
) {
    MochiBarFrame(
        modifier = modifier,
        container = MochiTheme.colorScheme.background,
        navigationIcon = navigationIcon,
        actions = actions,
    ) {
        BarTitle(title = title, subtitle = null, titleAlpha = titleAlpha, centered = false, titleContent = null)
    }
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
    backdrop: Any? = null,
) {
    MochiBarFrame(
        modifier = modifier,
        container = MochiTheme.colorScheme.background,
        navigationIcon = navigationIcon,
        actions = actions,
    ) {
        BarTitle(title = title, subtitle = subtitle, titleAlpha = titleAlpha, centered = false, titleContent = titleContent)
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
    MochiBarFrame(
        modifier = modifier,
        container = MochiTheme.colorScheme.background,
        navigationIcon = navigationIcon,
        actions = actions,
    ) {
        BarTitle(title = title, subtitle = subtitle, titleAlpha = titleAlpha, centered = centered, titleContent = titleContent)
    }
}
