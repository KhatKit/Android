package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「大卡片 + 分隔线」表单容器 / 表单项 / 分隔线的双风格版本。
 *
 * MD3 的经典表单长这样：一个容器卡片把所有行装进去，行之间用分隔线断开。
 * Miuix（KernelSU）不是这样：**每一项各自一张圆角卡片，卡片之间靠 12dp 间距分开，
 * 没有任何分隔线**。所以同一份页面代码在两种风格下要换结构，靠这三个包装分流。
 *
 * 用法：把原来的 `KedgeCard(colors = CustomColors.cardColorsOnSurfaceContainer) { … }`
 * 换成 [KedgeFormCard]，`HorizontalDivider()` 换成 [KedgeFormDivider]，
 * `FormItem(…)` 换成 [KedgeFormRow]。
 */
@Composable
fun KedgeFormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalKedgeStyle.current) {
        KedgeStyle.MD3Exp -> KedgeCard(
            modifier = modifier.fillMaxWidth(),
            colors = CustomColors.cardColorsOnSurfaceContainer,
            content = content,
        )

        // Miuix：KernelSU 的分组就是「一个 Card 装一组 preference」。里面的
        // ArrowPreference / SwitchPreference 本身**不画卡片**，全靠这层 Card 提供
        // 背景和圆角；所以这里必须出卡片，否则整组贴在页面底色上完全看不见。
        KedgeStyle.Miuix -> KedgeCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = MiuixFormMetrics.ItemSpacing),
            // 组内每一项自己带内边距（ArrowPreference 有 16dp insideMargin，
            // KedgeFormRow 有 ItemPadding），这里不再叠一层。
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(MiuixFormMetrics.ItemShape),
            color = MiuixTheme.colorScheme.surfaceContainer,
            content = content,
        )
    }
}

/**
 * 表单里的一行。签名与 [heizige.kk.khatkit.app.core.ui.components.ui.FormItem] 一致，
 * 可以直接替换。
 *
 * MD3Exp：左边标题+描述、右边内容（原来的样子）。
 * Miuix：整行一张圆角卡片，标题/描述/内容自上而下，和设置页的选项卡一致。
 */
@Composable
fun KedgeFormRow(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit = {},
    description: (@Composable (() -> Unit))? = null,
    tail: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    when (LocalKedgeStyle.current) {
        KedgeStyle.MD3Exp -> heizige.kk.khatkit.app.core.ui.components.ui.FormItem(
            modifier = modifier,
            label = label,
            description = description,
            tail = tail,
            content = content,
        )

        KedgeStyle.Miuix -> KedgeCard(
            modifier = modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(MiuixFormMetrics.ItemShape),
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = MiuixFormMetrics.ItemPadding,
                vertical = MiuixFormMetrics.ItemPadding,
            ),
        ) {
            // 标题照常独立成行（开关、下拉这类内容没有自己的标题位）。
            // 这里**不**把标题透给输入框：透过去会让输入框在框内再画一次，
            // 和上面这行重复。
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiuixRowLabel { label() }
                if (description != null) {
                    MiuixRowSummary { description() }
                }
                content()
                tail()
            }
        }
    }
}

/** MD3 的分隔线；Miuix 下不渲染（项与项之间只有间距）。 */
@Composable
fun KedgeFormDivider(modifier: Modifier = Modifier) {
    if (LocalKedgeStyle.current == KedgeStyle.Miuix) return
    HorizontalDivider(modifier = modifier)
}

/** Miuix 表单行的度量，与设置页（ArrowPreference）保持一致。 */
@Suppress("unused")
object MiuixFormMetrics {
    /** 行之间的间距，与设置页（miuixGroup 的 GroupSpacing）保持一致。 */
    val ItemSpacing = 10.dp

    /** 行卡片的圆角。 */
    val ItemShape = 20.dp

    /** 行卡片内边距。 */
    val ItemPadding = 10.dp

    /** 组内纵向间距，Miuix 下比 MD3 紧。 */
    val GroupSpacing = 10.dp

    /**
     * 组内块的内边距。
     *
     * MD3 下外层卡片自带 16dp，调用点里再写 `padding(16.dp)` 是为了卡片内部的
     * 留白；Miuix 下 `KedgeFormCard` **不渲染卡片**，那层 padding 就变成了凭空多出
     * 的一圈空白，所以这里要返回 0。
     */
    val InnerPadding = 0.dp
}

/**
 * 行标题：用 Miuix 的 headline1 + Medium，和设置页一致。
 *
 * 只有当行内**没有**输入框（标题需要独立成行）时才渲染；否则标题由
 * [heizige.kk.kedge.components.LocalKedgeRowLabel] 交给输入框显示在框内。
 */
@Composable
internal fun MiuixRowLabel(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        androidx.compose.material3.LocalTextStyle provides
            MiuixTheme.textStyles.headline1.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            ),
        content = content,
    )
}

/** 行描述：用 Miuix 的 body2。 */
@Composable
private fun MiuixRowSummary(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        androidx.compose.material3.LocalTextStyle provides MiuixTheme.textStyles.body2,
        content = content,
    )
}
