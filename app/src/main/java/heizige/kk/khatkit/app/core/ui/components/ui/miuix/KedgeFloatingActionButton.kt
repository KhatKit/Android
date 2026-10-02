package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

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
    /** 底色；`null` 时 Miuix 用自己的默认底色，MD3Exp 用 MD3 默认。 */
    containerColor: androidx.compose.ui.graphics.Color? = null,
    /** 内容色；`null` 时 Miuix 下发 onPrimary（KSU 的 FAB 配色），MD3Exp 用 MD3 默认。 */
    contentColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit,
) {
    when (LocalKedgeStyle.current) {
        // 对齐 KernelSU：Miuix 的 FAB 用 primary 底 + onPrimary 图标、无阴影。
        // 图标颜色靠 LocalContentColor 下发（Icon 默认取它）。
        KedgeStyle.Miuix -> CompositionLocalProvider(
            LocalContentColor provides (contentColor ?: MiuixTheme.colorScheme.onPrimary),
        ) {
            if (containerColor != null) {
                MiuixFloatingActionButton(
                    onClick = onClick,
                    modifier = modifier,
                    containerColor = containerColor,
                    shadowElevation = 0.dp,
                    content = content,
                )
            } else {
                MiuixFloatingActionButton(
                    onClick = onClick,
                    modifier = modifier,
                    shadowElevation = 0.dp,
                    content = content,
                )
            }
        }

        // MD3 的 contentColor 没有 Defaults 常量，不传时它内部会用 LocalContentColor，
        // 所以只在调用点显式给了才透传。
        KedgeStyle.MD3Exp -> if (contentColor != null) {
            FloatingActionButton(
                onClick = onClick,
                modifier = modifier,
                containerColor = containerColor
                    ?: androidx.compose.material3.FloatingActionButtonDefaults.containerColor,
                contentColor = contentColor,
                content = content,
            )
        } else {
            FloatingActionButton(
                onClick = onClick,
                modifier = modifier,
                containerColor = containerColor
                    ?: androidx.compose.material3.FloatingActionButtonDefaults.containerColor,
                content = content,
            )
        }
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
        // Miuix 的 FAB 自带 shape（圆角方）+ 配色默认值，不要用 MD3 的
        // primary 去覆盖——那会得到一个过饱和的圆点，看着就不像 Miuix。
        // 所以 Miuix 分支只透传调用方显式指定的颜色，没给就用 Miuix 自己的。
        KedgeStyle.Miuix -> CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onPrimary,
        ) {
            if (containerColor != null) {
                MiuixFloatingActionButton(
                    onClick = onClick,
                    modifier = modifier,
                    containerColor = containerColor,
                    shadowElevation = 0.dp,
                    content = icon,
                )
            } else {
                MiuixFloatingActionButton(
                    onClick = onClick,
                    modifier = modifier,
                    shadowElevation = 0.dp,
                    content = icon,
                )
            }
        }

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
