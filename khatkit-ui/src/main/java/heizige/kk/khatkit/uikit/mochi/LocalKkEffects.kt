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

/**
 * 栏按钮（顶栏圆形按钮、返回键、图标按钮）的样式：true = 液态玻璃，false = 实心胶囊。
 *
 * 由 `KhatKitTheme` 按设置 `DisplaySetting.glassButtons` provide。总开关 effectsEnabled 关掉时
 * 主题材质里没有 control 玻璃，Mochi 默认栏按钮配色本身就会退回实心，所以这里直接给
 * glassButtons 的值即可，页面不用再判断。
 *
 * 背景：`kkSolidBarColors()` 曾把全 app 栏按钮写死成实心（绕开玻璃取样时圆底随内容闪色）。
 * 现在统一走 `kkBarButtonColors()`：读本 Local —— 开 = Mochi 默认栏按钮配色（材质有玻璃就是玻璃），
 * 关 = 原来那套实心配色。
 */
val LocalKkGlassButtons = staticCompositionLocalOf { true }
