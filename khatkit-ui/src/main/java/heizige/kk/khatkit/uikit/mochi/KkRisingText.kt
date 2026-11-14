package heizige.kk.khatkit.uikit.mochi

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.feice.mochi.anim.MochiSprings
import ui.feice.mochi.effect.content.mochiBlur

/**
 * 计数器式的文字进场：每个字符占一个槽位，从基线下方 [rise] 处升入（MochiCounterText 的
 * 数字滚轮同款，只是槽位里放的是任意字符而不是 0–9），从左到右逐字错开 [staggerMillis]，
 * 落定时走 Mochi 的 snappy 弹簧，带一点回弹。
 *
 * 只用于短文本（占位提示这类一行就够的内容）：逐字排版会丢掉字距对（kerning），
 * 超过 [maxChars] 个字符、或系统开了「减弱动效」、或 LocalKkEffectsEnabled 关掉时直接回落成普通 Text。
 *
 * 读屏读整串文字（clearAndSetSemantics），不会一个字符一个字符念。
 */
@Composable
fun KkRisingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    staggerMillis: Int = 24,
    rise: Dp = 14.dp,
    blur: Dp = 2.5.dp,
    maxChars: Int = 48,
) {
    val reduceMotion = rememberKkReduceMotion()
    val effectsEnabled = LocalKkEffectsEnabled.current
    if (text.isEmpty() || text.length > maxChars || !effectsEnabled || reduceMotion) {
        Text(text = text, style = style, color = color, maxLines = 1)
        return
    }
    // 按码点切，代理对（emoji 这类）不会被劈成两半
    val glyphs = remember(text) {
        buildList {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                add(String(Character.toChars(cp)))
                i += Character.charCount(cp)
            }
        }
    }
    val progresses = remember(text) { List(glyphs.size) { Animatable(0f) } }
    LaunchedEffect(text) {
        coroutineScope {
            progresses.forEachIndexed { index, anim ->
                launch {
                    delay(index.toLong() * staggerMillis)
                    anim.animateTo(1f, MochiSprings.snappy(320))
                }
            }
        }
    }
    Row(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        glyphs.forEachIndexed { index, glyph ->
            Text(
                text = glyph,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .alignByBaseline()
                    .graphicsLayer {
                        val p = progresses[index].value
                        translationY = (1f - p) * rise.toPx()
                        // 透明度比位移先到位，字不会在底部半透明地拖一截
                        alpha = (p * 1.8f).coerceIn(0f, 1f)
                    }
                    // 和计数器一样：在飞的时候是糊的，落定就清晰（半径 0 不建离屏层）
                    .mochiBlur { blur * (1f - progresses[index].value.coerceIn(0f, 1f)) },
            )
        }
    }
}
