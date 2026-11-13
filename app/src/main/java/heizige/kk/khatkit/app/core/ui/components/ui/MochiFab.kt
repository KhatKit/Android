package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.navigation.LocalMochiBottomBarInset

/**
 * Mochi 没有 FAB 组件，这里用它的按钮拼一个：主按钮是 56dp 的 Filled 圆钮，
 * 次要动作（导入、从接口拉取）是叠在上面的 48dp Tonal 圆钮。
 *
 * [MochiFabColumn] 放在页面的 Box 里，自己贴右下角，并浮在底部浮动栏（[LocalMochiBottomBarInset]）上方；
 * 列表底部要多留 [MochiFabDefaults.stackHeight] 才不会被按钮挡住最后一项。
 */
@Composable
fun BoxScope.MochiFabColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(
                end = 16.dp,
                bottom = maxOf(
                    LocalMochiBottomBarInset.current,
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ) + 16.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MochiFabDefaults.Spacing),
        content = content,
    )
}

/** 主 FAB：56dp Filled 圆钮。 */
@Composable
fun MochiFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MochiIconButton(
        onClick = onClick,
        modifier = modifier,
        style = MochiButtonStyle.Filled,
        size = MochiFabDefaults.Size,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = ui.feice.mochi.theme.LocalContentColor.current,
        )
    }
}

/** 次要 FAB：48dp Tonal 圆钮，叠在主 FAB 上面。 */
@Composable
fun MochiSmallFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MochiIconButton(
        onClick = onClick,
        modifier = modifier,
        style = MochiButtonStyle.Tonal,
        size = MochiFabDefaults.SmallSize,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = ui.feice.mochi.theme.LocalContentColor.current,
        )
    }
}

object MochiFabDefaults {
    val Size: Dp = 56.dp
    val SmallSize: Dp = 48.dp
    val Spacing: Dp = 12.dp

    /** 列表底部要额外留出的高度：[smallCount] 个次要 FAB + 主 FAB + 和列表之间的 16dp。 */
    fun stackHeight(smallCount: Int = 0): Dp = Size + (SmallSize + Spacing) * smallCount + 16.dp
}
