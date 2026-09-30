package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton

/**
 * 双风格浮动按钮：Miuix 用 Miuix 原生 FAB，MD3Exp 保持 [FloatingActionButton]。
 *
 * 写法和 [heizige.kk.khatkit.app.core.ui.components.nav.BackButton] 一致 ——
 * 页面只调一次，形态跟着当前风格变，不在页面里写 if。
 */
@Composable
fun KedgeFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (LocalKedgeStyle.current) {
        KedgeStyle.Miuix -> MiuixFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            content = content,
        )

        KedgeStyle.MD3Exp -> FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            content = content,
        )
    }
}

/**
 * 双风格「带文字的」浮动按钮。
 *
 * Miuix 0.9.3 没有 Extended FAB，只有圆形 [MiuixFloatingActionButton]，所以
 * Miuix 下退化成普通 FAB（只显示图标），MD3 下保持 ExtendedFloatingActionButton
 * 的带文字形态。语义不丢，只是 Miuix 下少几个字的标签。
 *
 * @param icon 图标槽，两种风格都渲染。
 * @param text 仅 MD3 渲染。
 */
@Composable
fun KedgeExtendedFloatingActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
) {
    when (LocalKedgeStyle.current) {
        KedgeStyle.Miuix -> MiuixFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = containerColor ?: MiuixTheme.colorScheme.primary,
            content = icon,
        )

        KedgeStyle.MD3Exp -> androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = onClick,
            icon = icon,
            text = text,
            modifier = modifier,
            containerColor = containerColor
                ?: androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
        )
    }
}
