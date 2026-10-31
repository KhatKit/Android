package heizige.kk.khatkit.app.core.ui.components.ai

import android.content.ClipData
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.AndroidClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import heizige.kk.khatkit.uikit.mochi.LocalKkEffectsEnabled
import heizige.kk.khatkit.uikit.mochi.rememberKkReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.Text
import ui.feice.mochi.anim.MochiMotions
import ui.feice.mochi.theme.MochiTheme

/**
 * 移植自 KodeHeap `RichTextEditorWithSmoothCursor`（ui/richtext/Editor.kt）：同一个 richeditor 1.0.0，
 * 系统光标关掉（透明 brush），自己画一根跟着弹簧走的光标。在原版基础上 Mochi 化：
 *
 * - **头 / 尾两根弹簧**：头用 0.16s 响应、尾用 0.32s 响应，两者之间画一段从透明渐到主题色的拖尾，
 *   光标跳得越远拖尾越长，停下后尾巴追上头、拖尾自然收没；
 * - **呼吸闪烁**：停手 520ms 后光标在 1 ↔ 0.22 之间柔和呼吸，一有输入/移动立刻亮回 1；
 * - **打字脉冲**：字数变多时光标瞬间加粗 + 外圈一圈淡光晕，随弹簧回落；
 * - 聚焦 / 失焦时光标沿高度方向长出 / 收回。
 *
 * 系统「减弱动效」或 FX 总开关关掉时：弹簧改成 snap、不闪、不拖尾、不脉冲，只剩一根静态光标。
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@Composable
internal fun MochiRichTextEditor(
    state: RichTextState,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    val cs = MochiTheme.colorScheme
    val density = LocalDensity.current
    val originalClipboard = LocalClipboard.current
    val motion = !rememberKkReduceMotion() && LocalKkEffectsEnabled.current

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var isFocused by remember { mutableStateOf(false) }

    val cursorRect = remember(textLayoutResult, state.selection) {
        val layout = textLayoutResult
        if (layout != null && state.selection.collapsed) {
            val offset = state.selection.start.coerceIn(0, layout.layoutInput.text.length)
            layout.getCursorRect(offset)
        } else {
            Rect.Zero
        }
    }
    val fallbackHeightPx = with(density) { textStyle.lineHeight.takeIf { it.isSp }?.toPx() ?: 22.dp.toPx() }
    val targetHeight = if (cursorRect.height > 0f) cursorRect.height else fallbackHeightPx
    val target = Offset(cursorRect.left, cursorRect.top)

    val head = remember { Animatable(target, Offset.VectorConverter) }
    val tail = remember { Animatable(target, Offset.VectorConverter) }
    val height = remember { Animatable(targetHeight) }
    // 第一次拿到真实位置时直接落位：不要从 (0,0) 一路飞过来
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(target, targetHeight, motion) {
        if (cursorRect == Rect.Zero) return@LaunchedEffect
        if (!placed || !motion) {
            head.snapTo(target)
            tail.snapTo(target)
            height.snapTo(targetHeight)
            placed = true
            return@LaunchedEffect
        }
        launch { head.animateTo(target, spring(dampingRatio = 1f, stiffness = MochiMotions.stiffnessFor(0.16f))) }
        launch { tail.animateTo(target, spring(dampingRatio = 0.9f, stiffness = MochiMotions.stiffnessFor(0.32f))) }
        launch { height.animateTo(targetHeight, spring(dampingRatio = 1f, stiffness = MochiMotions.stiffnessFor(0.24f))) }
    }

    // 呼吸闪烁：任何移动 / 输入都会重启这个 effect，先亮回 1，停 520ms 后开始呼吸
    val blink = remember { Animatable(1f) }
    val textLength = state.annotatedString.text.length
    LaunchedEffect(target, textLength, isFocused, motion) {
        blink.animateTo(1f, tween(90))
        if (!isFocused || !motion) return@LaunchedEffect
        delay(520)
        while (true) {
            blink.animateTo(0.22f, tween(560, easing = FastOutSlowInEasing))
            blink.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        }
    }

    // 打字脉冲：只在字数增加时触发（删字、移动光标不触发）
    val pulse = remember { Animatable(0f) }
    var lastLength by remember { mutableStateOf(textLength) }
    LaunchedEffect(textLength) {
        val grew = textLength > lastLength
        lastLength = textLength
        if (!grew || !motion) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = MochiMotions.stiffnessFor(0.38f)))
    }

    val focusGrow by animateFloatAsState(
        targetValue = if (isFocused && state.selection.collapsed) 1f else 0f,
        animationSpec = if (motion) spring(dampingRatio = 0.8f, stiffness = MochiMotions.stiffnessFor(0.3f)) else snap(),
        label = "mochi_cursor_focus",
    )

    val cursorColor = cs.primary
    val baseWidthPx = with(density) { 2.dp.toPx() }
    val pulseWidthPx = with(density) { 1.5.dp.toPx() }
    val glowPx = with(density) { 5.dp.toPx() }

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    val safeClipboard = remember(state, originalClipboard) {
        if (originalClipboard is AndroidClipboard) {
            SafeRichTextClipboard(state, originalClipboard)
        } else {
            originalClipboard
        }
    }

    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (cursorRect == Rect.Zero || focusGrow <= 0.001f) return@drawWithContent
            val h = height.value * focusGrow
            val p = pulse.value
            val w = baseWidthPx + pulseWidthPx * p
            val alpha = blink.value * focusGrow
            val headPos = head.value
            // 高度方向从中线长出来
            val top = headPos.y + (height.value - h) / 2f
            val tailPos = tail.value
            val tailTop = tailPos.y + (height.value - h) / 2f
            // 拖尾：尾矩形的后沿 → 头矩形的前沿围成的四边形，从透明渐到主题色
            if ((tailPos - headPos).getDistance() > 1f) {
                val forward = headPos.x >= tailPos.x
                val tailX = if (forward) tailPos.x else tailPos.x + w
                val headX = if (forward) headPos.x + w else headPos.x
                val path = Path().apply {
                    moveTo(tailX, tailTop)
                    lineTo(headX, top)
                    lineTo(headX, top + h)
                    lineTo(tailX, tailTop + h)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(cursorColor.copy(alpha = 0f), cursorColor.copy(alpha = 0.45f * focusGrow)),
                        start = Offset(tailPos.x, tailTop + h / 2f),
                        end = Offset(headPos.x, top + h / 2f),
                    ),
                )
            }
            if (p > 0.01f) {
                drawRoundRect(
                    color = cursorColor.copy(alpha = 0.18f * p * focusGrow),
                    topLeft = Offset(headPos.x - glowPx * p, top - glowPx * p * 0.4f),
                    size = Size(w + glowPx * 2f * p, h + glowPx * 0.8f * p),
                    cornerRadius = CornerRadius(glowPx + w),
                )
            }
            drawRoundRect(
                color = cursorColor.copy(alpha = alpha),
                topLeft = Offset(headPos.x, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f),
            )
        },
    ) {
        CompositionLocalProvider(LocalClipboard provides safeClipboard) {
            BasicTextField(
                value = state.textFieldValue,
                onValueChange = { state.onTextFieldValueChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        isFocused = it.isFocused
                        state.isFocused = it.isFocused
                    }
                    .onPreviewKeyEvent { state.onPreviewKeyEvent(it) }
                    .onPlaced { coords -> state.textFieldWindowPosition = coords.positionInWindow() },
                onTextLayout = {
                    state.onTextLayout(it, density)
                    textLayoutResult = it
                },
                textStyle = textStyle,
                cursorBrush = SolidColor(Color.Transparent),
                visualTransformation = state.visualTransformation,
                decorationBox = { innerTextField ->
                    Box {
                        if (placeholder != null && state.annotatedString.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

/**
 * 同 KodeHeap 的 SafeRichTextClipboardManager：复制时把选区导出成 html + 纯文本，
 * 粘贴时把剪贴板里的 html 交给 RichTextState 解析，富文本格式在复制粘贴之间不丢。
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private class SafeRichTextClipboard(
    private val richTextState: RichTextState,
    private val clipboard: Clipboard,
) : Clipboard, AndroidClipboard {

    override suspend fun getClipEntry(): ClipEntry? {
        val entry = clipboard.getClipEntry() ?: return null
        val clipData = entry.clipData
        if (clipData.itemCount > 0) {
            clipData.getItemAt(0).htmlText?.let { richTextState.pendingClipboardHtml = it }
        }
        return entry
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        if (clipEntry == null) {
            clipboard.setClipEntry(null)
            return
        }
        val copySelection = richTextState.copySelection
        if (copySelection == null || copySelection.collapsed) {
            clipboard.setClipEntry(null)
            return
        }
        val html = richTextState.toHtml(copySelection)
        val text = richTextState.toText(copySelection)
        clipboard.setClipEntry(ClipEntry(ClipData.newHtmlText("rich text", text, html)))
    }

    override val clipboardManager: android.content.ClipboardManager
        get() = (clipboard as AndroidClipboard).clipboardManager
}
