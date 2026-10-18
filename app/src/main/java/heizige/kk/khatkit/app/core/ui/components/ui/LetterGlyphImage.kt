package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** 位图边长上限：字母头像最大也就 100~200dp，512px 足够，避免大图时白白吃掉几 MB。 */
private const val MaxGlyphBitmapPx = 512

/**
 * 字母头像里的那个字 —— **先排版进一张 [ImageBitmap]，再用 [Image] 画出来**，不再直接用 `Text`。
 *
 * 为什么不用 `Text`：
 * - `Text` 每次重组都要重新排版、重新字形光栅化。同一个字在列表里出现 N 次（会话头像、
 *   模型图标、角色头像），就是 N 次排版 + N 次 Skia 字形查找；位图只算一次，之后是贴图。
 * - `Text` 的最终像素受 `LocalTextStyle`（letterSpacing / lineHeight / fontFeatureSettings）
 *   影响，同一枚头像在不同容器里会悄悄长得不一样。位图化后**度量只发生一次**，显示端只剩
 *   一张图 + 一个颜色。
 *
 * 着色做法：位图里只画**不透明黑**，显示时 [ColorFilter.tint] 走 `BlendMode.SrcIn` ——
 * 保留字形 alpha、把 RGB 换成目标色。所以**位图与颜色无关**，换主题/换角色色不需要重新渲染，
 * `remember` 的 key 里也就不含颜色。
 *
 * @param glyph 要画的那个字（调用方负责取首字、转大写、兜底 `?`）。
 * @param color 字形最终颜色（tint）。
 * @param style 字体族/字重等排版属性；`fontSize` / `letterSpacing` 由本组件接管，传了也不算。
 * @param fontSize 显式字号（sp）。不给 = 按可用盒子自适应（[fillRatio]）。
 *                   给了就**不参与自适应**，只按「位图 / 盒子」的像素比等比缩 —— 这样从大盒子
 *                   缩到小盒子时，视觉字号与原来一格 `Text(fontSize = …)` 完全一致。
 * @param fillRatio 自适应时，字形排版盒边长占可用盒子边长的比例。
 */
@Composable
internal fun LetterGlyphImage(
    glyph: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    fontSize: TextUnit = TextUnit.Unspecified,
    fillRatio: Float = 0.95f,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        // 只用有限的那一边：无界约束（比如 wrapContent 的 Row 里）拿不到边长，这一帧就不画。
        val sideDp = listOf(maxWidth, maxHeight).filter { it.value.isFinite() }.minOrNull()
        val boxPx = sideDp?.let { with(density) { it.roundToPx() } } ?: 0
        val bitmap = remember(glyph, boxPx, style, fontSize, fillRatio, density.density, density.fontScale) {
            renderGlyphBitmap(
                measurer = measurer,
                glyph = glyph,
                style = style,
                fontSize = fontSize,
                boxPx = boxPx,
                fillRatio = fillRatio,
                density = density,
            )
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(color, BlendMode.SrcIn),
            )
        }
    }
}

/**
 * 把一个字符渲染成正方形位图，字形在方块里居中。
 *
 * 居中按**排版盒**（[androidx.compose.ui.text.TextLayoutResult.size]）对齐，而不是墨迹包围盒 ——
 * 这样跟原来 `Text` 在 `Box(contentAlignment = Center)` 里的位置逐像素相同，换成位图也不会
 * 「字往上/往下跳一格」。
 */
private fun renderGlyphBitmap(
    measurer: TextMeasurer,
    glyph: String,
    style: TextStyle,
    fontSize: TextUnit,
    boxPx: Int,
    fillRatio: Float,
    density: Density,
): ImageBitmap? {
    if (boxPx <= 0 || glyph.isBlank()) return null
    val bitmapPx = min(boxPx, MaxGlyphBitmapPx)
    // 位图可能比盒子小（大尺寸封顶），字形尺寸要按同一比例缩，否则会被二次放大糊掉。
    val scale = bitmapPx.toFloat() / boxPx.toFloat()
    val effective = style.copy(letterSpacing = 0.sp)
    // 先用固定 100sp 量一次当标尺：字体度量对字号是线性的，一次探测就能反推任意目标字号。
    val probe = measurer.measure(
        text = AnnotatedString(glyph),
        style = effective.copy(fontSize = 100.sp),
        maxLines = 1,
    )
    val probeMax = max(probe.size.width, probe.size.height).toFloat()
    if (probeMax <= 0f) return null
    val fontSp = if (fontSize.isSp) {
        (fontSize.value * scale).sp
    } else {
        (bitmapPx * fillRatio * 100f / probeMax).sp
    }
    if (!fontSp.value.isFinite() || fontSp.value <= 0f) return null
    val layout = measurer.measure(
        text = AnnotatedString(glyph),
        style = effective.copy(fontSize = fontSp),
        maxLines = 1,
    )
    val bitmap = ImageBitmap(bitmapPx, bitmapPx)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = Size(bitmapPx.toFloat(), bitmapPx.toFloat()),
    ) {
        drawText(
            textLayoutResult = layout,
            color = Color.Black,
            topLeft = Offset(
                x = (bitmapPx - layout.size.width) / 2f,
                y = (bitmapPx - layout.size.height) / 2f,
            ),
        )
    }
    return bitmap
}
