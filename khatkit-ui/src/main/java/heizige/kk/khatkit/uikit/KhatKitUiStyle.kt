package heizige.kk.khatkit.uikit

import heizige.kk.kedge.theme.KedgeStyle

/**
 * KhatKit 界面风格。宿主可在设置里切换，KhatKit 自带 UI 全部走
 * [KhatKitTheme] → Kedge 双风格组件（MD3Exp / Miuix）。
 */
enum class KhatKitUiStyle {
    MATERIAL,
    MIUIX;

    fun toKedgeStyle(): KedgeStyle = when (this) {
        MATERIAL -> KedgeStyle.MD3Exp
        MIUIX -> KedgeStyle.Miuix
    }
}
