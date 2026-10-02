package heizige.kk.khatkit.app.core.ui.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import heizige.kk.khatkit.app.core.ui.hooks.rememberIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 流光（极光）动态背景，移植自词幕（Kifranei/lyricon）`app/compose/effect/BgEffectBackground.kt`。
 *
 * 在 [content] 下方铺一层 [Canvas]，先用页面底色铺满，再叠加 OS3 着色器绘制的四个漂移光点。
 * 三套配色按 [BgEffectConfig.Config.colorInterpPeriod] 循环用弹簧动画插值，于是背景呈现
 * 缓慢换色 + 光点漂移的观感。
 *
 * 与原项目的差异：
 * - 原版按 HyperOS 大版本在 OS2/OS3 之间切换，并把选择存进 SharedPreferences。本项目
 *   关于页固定用 OS3，不引入这套偏好。
 * - 原版的深浅色取 `MiuixTheme.colorScheme.background` 的亮度；这里改用本项目的
 *   [rememberIsDarkTheme]，以尊重应用内的「颜色模式」设置（仅 Miuix 主题下二者才一致）。
 *
 * @param dynamicBackground 是否持续推进动画。
 * @param bgModifier 贴在 Canvas 上的额外修饰（例如 `Modifier.layerBackdrop(backdrop)`），
 *   让上层卡片能采样到这张流光背景去做毛玻璃。
 * @param alpha 每帧读取的整体透明度，用于跟随滚动淡出。
 */
@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit,
) {
    // Android 13 以下没有 RuntimeShader，直接退化成纯背景，不画流光。
    // 这里必须是「显式版本比较 + 提前 return」：BgEffectPainter 标了 @RequiresApi(33)，
    // lint 的 NewApi 只认同函数内的 SDK_INT 分支。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isRuntimeShaderSupported()) {
        Box(modifier = modifier, content = content)
        return
    }

    Box(modifier = modifier) {
        val surface = MiuixTheme.colorScheme.surface
        val painter = remember { BgEffectPainter() }
        val animTime = rememberFrameTimeSeconds(dynamicBackground)
        val isDark = rememberIsDarkTheme()
        val preset = remember(isDark) { BgEffectConfig.get(isDark) }
        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            var targetStage = 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong())
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(bgModifier)) {
            drawRect(surface)
            // 光点只铺在上方 78%，与 KernelSU/词幕的头图区一致，滚动淡出后露出纯底色。
            val drawHeight = size.height * 0.78f
            val stage = colorStage.value
            val base = stage.toInt()
            val fraction = stage - base
            val getColors = { index: Int ->
                when (index % 4) {
                    0 -> preset.colors2
                    1 -> preset.colors1
                    2 -> preset.colors2
                    3 -> preset.colors3
                    else -> preset.colors2
                }
            }
            val start = getColors(base)
            val end = getColors(base + 1)
            val currentColors = FloatArray(16) { i -> start[i] + (end[i] - start[i]) * fraction }
            painter.updateResolution(size.width, size.height)
            painter.updatePresetIfNeeded(drawHeight, size.height, size.width, isDark)
            painter.updateColors(currentColors)
            painter.updateAnimTime(animTime())
            drawRect(painter.brush, alpha = alpha())
        }
        content()
    }
}
