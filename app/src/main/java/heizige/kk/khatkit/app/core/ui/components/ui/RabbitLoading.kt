package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicatorType

@Composable
fun RabbitLoadingIndicator(modifier: Modifier = Modifier) {
    val useAppIconStyleLoadingIndicator = LocalSettings.current.displaySetting.useAppIconStyleLoadingIndicator
    val primaryColor = MaterialTheme.colorScheme.primary

    if (useAppIconStyleLoadingIndicator) {
        // 使用新的应用图标动画（纯 Compose 实现）
        AgentLoadingIndicator(
            modifier = modifier,
            color = primaryColor
        )
    } else {
        // 不用 MD3 的 ContainedLoadingIndicator（Miuix 下不画任何东西）。
        // 统一走 Kedge 的双风格加载指示器：MD3 侧是 Expressive 的 LoadingIndicator
        // （图形循环变形），Miuix 侧是 Miuix 自己的环。
        KkProgressIndicator(
            modifier = modifier,
            type = KkProgressIndicatorType.Circular,
        )
    }
}
