package heizige.kk.khatkit.app.core.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import heizige.kk.khatkit.uikit.mochi.rememberMochiMaterialTokens

/**
 * U2 正式 Bridge 的**应用侧挂载点**：全 app 只有这一处 MaterialTheme 挂载点。
 *
 * token 全部来自 Mochi（映射在 khatkit-ui/uikit/mochi/MochiMaterialBridge.kt）：
 *  - colorScheme ← MochiColorScheme（37 角色逐域拷贝；AMOLED 整条表面阶梯重锚到纯黑，见 Bridge）
 *  - shapes ← MochiShapes（按 Mochi 圆角半径建 RoundedCornerShape）
 *  - typography ← MochiTextStyles 的 15 个 MD3 角色
 *  - motionScheme ← MochiMotionScheme（当前取 expressive，逐条 spring 映射待后续）
 *
 * 页面直接用 MD3 组件即为 Mochi 外观；旧 extend 调色板与 GoogleSans 只在功能点直接引用，不进主题。
 *
 * 明暗**不从这里传**：唯一真相是 KhatKitTheme 里那个 ThemeController（本仓 ColorMode → mochi
 * ColorSchemeMode），Bridge 拷过来的已经是 isDark 之后的那套角色。这里再收一个 `dark` 参数只会造出
 * 第二个真相 —— 之前那个被 @Suppress 掉的 UNUSED_PARAMETER 就是这么骗人的。
 */
@Composable
fun ProvideMaterial3Theme(
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    val tokens = rememberMochiMaterialTokens(amoled = amoled)
    CompositionLocalProvider(LocalContentColor provides tokens.colorScheme.onBackground) {
        MaterialTheme(
            colorScheme = tokens.colorScheme,
            shapes = tokens.shapes,
            typography = tokens.typography,
            content = content,
        )
    }
}

/** 功能色（extend 调色板）：不参与主题注入，仅供仍需它的调用点直接取用。 */
val LocalExtendColors = compositionLocalOf { lightExtendColors() }

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
