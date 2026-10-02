package heizige.kk.khatkit.app.core.ui.effect

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import top.yukonga.miuix.kmp.shader.RuntimeShader
import top.yukonga.miuix.kmp.shader.asBrush

/**
 * 流光背景绘制器，移植自词幕 `app/compose/effect/BgEffectPainter.kt`。
 *
 * 持有 [RuntimeShader] 与 uniform 状态，供 [BgEffectBackground] 每帧更新。
 * 去掉了原项目的 `isOs3` 分支与逐帧光点推送——关于页固定 OS3，流动由着色器
 * 内部按 `uAnimTime` 自行完成。
 *
 * [RuntimeShader] 需要 Android 13（API 33），所以本类标了 [RequiresApi]；
 * 调用方 [BgEffectBackground] 必须先过版本判断，否则在 API 26–32 上会崩。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class BgEffectPainter {

    val runtimeShader by lazy {
        RuntimeShader(OS3_BG_FRAG).also { initStaticUniforms(it) }
    }

    val brush: Brush by lazy { runtimeShader.asBrush() }

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private var animTime = Float.NaN
    private var isDarkCached: Boolean? = null
    private var presetApplied = false

    private fun initStaticUniforms(shader: RuntimeShader) {
        shader.setFloatUniform("uTranslateY", 0f)
        shader.setFloatUniform("uNoiseScale", 1.5f)
        shader.setFloatUniform("uPointRadiusMulti", 1f)
        shader.setFloatUniform("uAlphaMulti", 1f)
        shader.setFloatUniform("uAlphaOffset", 0.1f)
        shader.setFloatUniform("uShadowOffset", 0.01f)
    }

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimTime(time: Float) {
        if (animTime == time) return
        animTime = time
        runtimeShader.setFloatUniform("uAnimTime", animTime)
    }

    fun updateColors(colors: FloatArray) {
        runtimeShader.setFloatUniform("uColors", colors)
    }

    fun updatePresetIfNeeded(logoHeight: Float, height: Float, width: Float, isDark: Boolean) {
        if (presetApplied && isDarkCached == isDark) return
        updateBound(logoHeight, height, width)
        val preset = BgEffectConfig.get(isDark)
        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        runtimeShader.setFloatUniform("uPointOffset", preset.pointOffset)
        runtimeShader.setFloatUniform("uShadowColorMulti", preset.shadowColorMulti)
        runtimeShader.setFloatUniform("uShadowColorOffset", preset.shadowColorOffset)
        runtimeShader.setFloatUniform("uShadowNoiseScale", preset.shadowNoiseScale)
        runtimeShader.setFloatUniform("uBound", bound)
        isDarkCached = isDark
        presetApplied = true
    }

    private fun updateBound(logoHeight: Float, totalHeight: Float, totalWidth: Float) {
        val heightRatio = logoHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bound[0] = 0f
            bound[1] = contentCenterY - aspectRatio / 2f
            bound[2] = 1f
            bound[3] = aspectRatio
        }
    }
}
