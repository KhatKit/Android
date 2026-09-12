package heizige.kk.khatkit.uikit

import androidx.compose.runtime.Composable
import heizige.kk.kedge.theme.KedgeTheme

/**
 * KhatKit UI 的统一主题入口。
 *
 * 所有 KhatKit 自带界面必须包在这一层里，内部只使用 Kedge 组件，
 * 保证换成 [KhatKitUiStyle.MIUIX] 时控件（而不是仅有配色）跟着变。
 */
@Composable
fun KhatKitTheme(
    style: KhatKitUiStyle = KhatKitUiStyle.MATERIAL,
    content: @Composable () -> Unit,
) {
    KedgeTheme(
        style = style.toKedgeStyle(),
        content = content,
    )
}
