package heizige.kk.khatkit.app.core.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import heizige.kk.khatkit.app.core.ui.hooks.rememberAmoledDarkMode
import heizige.kk.khatkit.app.core.ui.hooks.rememberCurrentColorMode
import heizige.kk.khatkit.app.core.ui.hooks.rememberUserSettingsState
import heizige.kk.khatkit.app.core.util.getActivity
import heizige.kk.khromia.components.LocalOptionSwitchExpressiveEnabled
import heizige.kk.khromia.components.LocalOptionSwitchScale

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun KhatKitTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val theme = findThemeById(settings.themeId, settings.customThemes)
                ?: findPresetTheme(settings.themeId)
            theme.getColorScheme(dark = darkTheme)
        }
    }
    val colorSchemeConverted = remember(darkTheme, amoledDarkMode, colorScheme) {
        if (darkTheme && amoledDarkMode) {
            colorScheme.copy(
                background = AMOLED_DARK_BACKGROUND,
                surface = AMOLED_DARK_BACKGROUND,
            )
        } else {
            colorScheme
        }
    }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    // 更新状态栏图标颜色
    val view = LocalView.current
    val activity = view.context.getActivity()
    // 浮窗可能使用 Application Context，没有可更新系统栏的 Activity。
    if (!view.isInEditMode && activity != null) {
        DisposableEffect(view, activity, darkTheme) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            controller.apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose {
                // 嵌套主题（如终端的深色主题）退出时恢复原有系统栏图标颜色。
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    val isMiuix = heizige.kk.kedge.theme.LocalKedgeStyle.current == heizige.kk.kedge.theme.KedgeStyle.Miuix
    val listCardStyle = remember(
        settings.listCardLargeCorner,
        settings.listCardSmallCorner,
        settings.listCardGap,
        isMiuix,
    ) {
        if (isMiuix) {
            MiuixListCardStyle
        } else {
            ListCardStyle(
                largeCorner = settings.listCardLargeCorner.dp,
                smallCorner = settings.listCardSmallCorner.dp,
                gap = settings.listCardGap.dp,
            )
        }
    }

    // 回弹（overscroll）：MD3 要**平台默认**（Android 12+ 拉伸回弹、以下 glow），
    // 只有 Miuix 才继续屏蔽。
    //
    // Miuix 侧的效果不由这里负责：`KedgeMiuixTheme` → `MiuixMaterialThemeBridge`
    // （Kedge/…/theme/MiuixMaterialBridge.kt:137）会在**更内层** provide
    // `MiuixOverscrollFactory`（内层覆盖外层），页面再用 `Modifier.overScrollVertical()`
    // （见 `core/ui/components/ui/miuix/MiuixSettingsPage.kt:152` 并配 `:158 overscrollEffect = null`）
    // 自己画 Miuix 风格的回弹。两条都不要抢，所以 Miuix 分支保持今天的 `null` 不变。
    //
    // 为什么不能再写死 `provides null`（foundation 1.12.0 源码逐条）：
    //  · `Overscroll.kt:343-347` `rememberOverscrollEffect()` 第一行就是
    //    `val overscrollFactory = LocalOverscrollFactory.current ?: return null`
    //    ⇒ 只要本层 provide null，**全 app 的滚动容器都拿不到 overscroll**（用户报的没回弹）。
    //  · `Overscroll.kt:390-393` `LocalOverscrollFactory` 的默认值是计算出来的
    //    `compositionLocalWithComputedDefaultOf { defaultOverscrollFactory() }`。
    //  · `AndroidOverscroll.android.kt:93-101` Android 实现：读 `LocalOverscrollConfiguration`，
    //    非空 ⇒ 返回 `AndroidEdgeEffectOverscrollFactory`（:100，:125-133 造
    //    `AndroidEdgeEffectOverscrollEffect`）。
    //  · `OverscrollConfiguration.android.kt:76-77` `LocalOverscrollConfiguration` 的默认值
    //    就是**非空**的 `OverscrollConfiguration()`（本仓没有任何地方 provide 它，已 grep 证实）。
    //  ⇒ 所以「把当前值原样转发下去」就等于本层不 provide，取的正是平台默认工厂。
    val overscrollFactory = LocalOverscrollFactory.current

    // 内容色必须显式给，不能吃 Compose 的默认值。
    //
    // `MaterialTheme` / `MaterialExpressiveTheme` 只 provide colorScheme / typography /
    // shapes，**不 provide `LocalContentColor`**；它的默认值是纯黑（`Color.Black`）。
    // 只有 `Surface` / `Scaffold` / `Card` 这类容器会在自己内部 provide 一次。
    // 于是「没有包在 Surface 里、又没写 `color =` 的 `Text`」在深色模式下就是黑字配
    // 深底 —— 实测卡片弹窗标题（`ExploreMarketPage` 的 `PopupHead`）1.0:1，肉眼
    // 只能看到一点点笔画。浅色模式下黑字配浅底是对的，所以这个缺陷只在深色出现。
    //
    // 与 Kedge 的 Miuix 分支同口径：`MiuixMaterialThemeBridge`
    //（Kedge/…/theme/MiuixMaterialBridge.kt）也是把 `LocalContentColor` 桥成
    // `onBackground`；这里补的是 MD3 分支漏掉的那一半。内层 `Surface`/`Card`
    // 仍会用 `contentColorFor()` 覆盖本值，不受影响。
    CompositionLocalProvider(
        LocalContentColor provides colorSchemeConverted.onBackground,
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors,
        LocalOverscrollFactory provides if (isMiuix) null else overscrollFactory,
        LocalListCardStyle provides listCardStyle,
        LocalOptionSwitchScale provides 1f,
        LocalOptionSwitchExpressiveEnabled provides false,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeConverted,
            typography = Typography,
            content = content,
            motionScheme = MotionScheme.expressive()
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
