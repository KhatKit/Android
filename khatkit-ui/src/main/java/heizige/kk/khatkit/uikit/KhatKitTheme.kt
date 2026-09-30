package heizige.kk.khatkit.uikit

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import heizige.kk.kedge.theme.KedgeTheme

/**
 * KhatKit UI 的统一主题入口。
 *
 * 所有 KhatKit 自带界面必须包在这一层里，内部只使用 Kedge 组件，
 * 保证换成 [KhatKitUiStyle.MIUIX] 时控件（而不是仅有配色）跟着变。
 *
 * ## darkTheme 必须由调用方传进来
 *
 * Miuix 与 MD3 是两套独立主题：MD3 侧的颜色由内层 `core.ui.theme.KhatKitTheme`
 * 根据用户「颜色模式」设置给出，而 Miuix 侧只认 [heizige.kk.kedge.theme.KedgeTheme]
 * 的 `darkTheme` 参数。这个参数默认是 `isSystemInDarkTheme()`，也就是**系统**深浅色，
 * 跟应用内的浅色/深色设置完全无关。
 *
 * 后果：Miuix 下把颜色模式切到「浅色」没有任何反应——因为 Miuix 主题仍然按系统
 * 深色渲染，而业务里那些读 `MaterialTheme.colorScheme` 的地方又走内层 MD3 主题，
 * 于是出现两套颜色对不上的现象。
 *
 * 调用方（app 模块，能读到用户设置）应显式传入解析后的深浅色。
 */
@Composable
fun KhatKitTheme(
    style: KhatKitUiStyle = KhatKitUiStyle.MATERIAL,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    KedgeTheme(
        style = style.toKedgeStyle(),
        darkTheme = darkTheme,
        content = content,
    )
}
