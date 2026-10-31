package heizige.kk.khatkit.uikit.mochi

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * FX1：Kk 层「液态玻璃 / 光效 / 交互动效」总开关，默认 **true**（主人拍板默认打开）。
 *
 * 由 `KhatKitTheme` 按设置（`Settings.displaySetting.effectsEnabled`）provide，app 与 uikit
 * 都能读。开关关掉时：
 *  - 主题侧：`MochiTheme(materials = MochiMaterials())`，栏 / 菜单 / 面板 / 弹窗全部退回纯色，
 *    滚动边缘模糊（scrollEdge）一起关 —— 玻璃与 glow 消失；
 *  - Kk 层：`rememberKkHaptics()` 不再震、`Modifier.kkPressState` 不再缩放（见 KkInteraction.kt）。
 *
 * 边界：**不关** Mochi 真组件内部的 pressScale / 触感 —— 那是基础触感，属于组件自带手感，
 * 不是本仓可调的「光效」。页面里不要写外观分支，一律读这个 Local。
 */
val LocalKkEffectsEnabled = staticCompositionLocalOf { true }
