package heizige.kk.khatkit.app.feature.settings.components

import heizige.kk.khatkit.uikit.mochi.MochiButton
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.KkSwitch
import androidx.compose.animation.AnimatedVisibility
import heizige.kk.khatkit.uikit.mochi.KkAnimatedRow
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.basic.rememberMochiSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.icons.brightnessAuto
import heizige.kk.khatkit.app.core.ui.icons.check
import heizige.kk.khatkit.app.core.ui.icons.darkMode
import heizige.kk.khatkit.app.core.ui.icons.lightMode
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.theme.ColorMode
import heizige.kk.khatkit.app.core.ui.theme.MorphPolygonShape
import heizige.kk.khatkit.app.core.ui.theme.PresetThemes
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import heizige.kk.khatkit.uikit.mochi.MochiExpandableOptionItem
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.core.graphics.toColorInt
import heizige.kk.khatkit.uikit.mochi.KkEditDialog
import heizige.kk.khatkit.uikit.mochi.KkEditField
import heizige.kk.khatkit.uikit.mochi.fadingEdge
import heizige.kk.khatkit.uikit.mochi.KkOptionsText
import ui.feice.mochi.picker.MochiSeedPalette
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.shape.mochiBorder
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

@Composable
fun MorphThemeModeSelector(
    selected: ColorMode,
    onSelect: (ColorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        listOf(ColorMode.SYSTEM, ColorMode.DARK, ColorMode.LIGHT).forEach { mode ->
            val isSelected = selected == mode
            val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Sunny) }
            val progress by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "morphProgress",
            )
            val animatedScale by animateFloatAsState(
                targetValue = if (isSelected) 1.3f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "iconScale",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .aspectRatio(1f)
                    .clip(MorphPolygonShape(morph, progress))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.87f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.87f)
                    )
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (mode) {
                        ColorMode.SYSTEM -> brightnessAuto
                        ColorMode.LIGHT -> lightMode
                        ColorMode.DARK -> darkMode
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .scale(animatedScale),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.87f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f),
                )

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedVisibility(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)),
                        visible = !isSelected,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Text(
                            text = when (mode) {
                                ColorMode.SYSTEM -> stringResource(R.string.greeting_settings_theme_system)
                                ColorMode.LIGHT -> stringResource(R.string.greeting_settings_theme_light)
                                ColorMode.DARK -> stringResource(R.string.greeting_settings_theme_dark)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .alpha(0.87f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 主题配色调色板：直接用 Mochi 的莫奈色板 [MochiSeedPalette]。
 *
 * 每个色块画的是「种子色生成的整套配色」（主色 + 辅助色 + 第三色），不是一个孤零零的圆点；
 * 选中的色块内缩出一圈缝、外面亮主色环，切换时轻震。末尾的自定义色块是彩虹圆环，
 * 点开就是 Mochi 自己的选色面板（网格 / 色谱 / 滑块），不再自己拼 HSV 方块。
 *
 * [selectedSeedColor] 给当前主题的种子色（自定义主题也能回显选中）；为 null 时按 [selectedThemeId]
 * 在预设里查。
 */
@Composable
fun PresetThemeColorDots(
    selectedThemeId: String,
    onSelectTheme: (String) -> Unit,
    onCustomColor: (Color) -> Unit,
    modifier: Modifier = Modifier,
    selectedSeedColor: Color? = null,
) {
    val seeds = remember { PresetThemes.map { it.standardLight.primary }.toImmutableList() }
    val selected = selectedSeedColor
        ?: PresetThemes.firstOrNull { it.id == selectedThemeId }?.standardLight?.primary
    MochiSeedPalette(
        selected = selected,
        onSelect = { color ->
            val preset = PresetThemes.firstOrNull { it.standardLight.primary == color }
            if (preset != null) onSelectTheme(preset.id) else onCustomColor(color)
        },
        modifier = modifier,
        seeds = seeds,
        custom = true,
    )
}

@Composable
fun ThemeCustomColorSheet(
    visible: Boolean,
    /** inScope 页面可传自己的 state（退出动画 + 页根 backdrop）；不传的旧调用点仍按 visible 走兼容层。 */
    sheetState: MochiSheetState? = null,
    initialColor: Color,
    history: List<Long>,
    onDismiss: () -> Unit,
    onColorChanged: (Color) -> Unit = {},
    onClear: (() -> Unit)? = null,
    onConfirm: (Color) -> Unit,
) {
    var customColor by remember(visible) { mutableStateOf(initialColor) }
    var showHexDialog by remember { mutableStateOf(false) }

    // 半屏打开、可拉到全屏：正文自己按高度分配空间（取色方块 weight(1f)），
    // 两种高度都不会在底部留一段空白。
    val internalSheet = rememberMochiSheetState()
    val effectiveSheet = sheetState ?: internalSheet
    if (sheetState == null) {
        LaunchedEffect(visible) { if (visible) effectiveSheet.show() else effectiveSheet.hide() }
    }

    // HSV 是取色器的原生坐标：色相 = 横条，饱和度 / 明度 = 方块的两轴
    val initialHsv = remember(visible) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember(visible) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(visible) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(visible) { mutableFloatStateOf(initialHsv[2]) }
    fun emit(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        brightness = v
        val color = colorFromHsv(h, s, v)
        customColor = color
        onColorChanged(color)
    }

    PrimaryBottomSheet(
        state = effectiveSheet,
        title = stringResource(R.string.greeting_settings_custom_color),
        imageVector = palette,
        dismissText = stringResource(android.R.string.cancel),
        confirmText = stringResource(R.string.greeting_settings_custom_color_confirm),
        onConfirm = { onConfirm(customColor) },
        onDismiss = onDismiss,
        // 正文自己铺满面板高度（取色方块撑开），不能再套一层滚动容器
        scrollable = false,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 当前颜色：色块 + HEX（点 HEX 精确输入）+ 可选「重置」
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MochiRoundedRectangle(16.dp))
                        .background(customColor),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = String.format("#%08X", customColor.toArgb()),
                        style = MochiTheme.textStyles.titleMedium.copy(fontFeatureSettings = "tnum"),
                        color = MochiTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(MochiTheme.shapes.small)
                            .clickable { showHexDialog = true }
                            .padding(vertical = 2.dp),
                    )
                    Text(
                        text = "点按 HEX 精确输入",
                        style = MochiTheme.textStyles.footnote,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onClear != null) {
                    MochiTextButton(onClick = { onClear() }) {
                        Text(stringResource(R.string.avatar_reset))
                    }
                }
            }

            // 饱和度（横）× 明度（纵）：填满剩余高度，全屏时不会在底下空一块
            SaturationValueSquare(
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                onChange = { sat, value -> emit(hue, sat, value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            HueSlider(
                hue = hue,
                onHueChange = { emit(it, saturation, brightness) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (history.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.greeting_settings_history_colors),
                        style = MochiTheme.textStyles.footnote,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fadingEdge(left = 20.dp, right = 20.dp, strength = 1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        history.reversed().forEach { argb ->
                            val color = Color(argb.toInt())
                            val hsv = remember(argb) {
                                FloatArray(3).also { android.graphics.Color.colorToHSV(argb.toInt(), it) }
                            }
                            val selected = customColor.toArgb() == argb.toInt()
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(MochiRoundedRectangle(10.dp))
                                    .background(color)
                                    .then(
                                        if (selected) {
                                            Modifier.mochiBorder(
                                                2.dp,
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                MochiRoundedRectangle(10.dp),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { emit(hsv[0], hsv[1], hsv[2]) },
                            )
                        }
                    }
                }
            }
        }
    }

    // 始终调用并用 visible 控制显隐，不要用 if (showHexDialog) 包一层：
    // 那样退场动画播不出来（组件会在 visible 变 false 的瞬间被卸载）。
    KkEditDialog(
        visible = showHexDialog,
        title = stringResource(R.string.greeting_settings_custom_color),
        fields = listOf(
            KkEditField(
                label = "Hex Code",
                initialValue = String.format("%08X", customColor.toArgb()),
                placeholder = "#AARRGGBB, #RRGGBB, AARRGGBB, RRGGBB",
                maxLength = 10,
                onValidate = { input -> if (parseHexColor(input) == null) "Invalid hex" else null },
            )
        ),
        onDismiss = { showHexDialog = false },
        onConfirm = { results ->
            parseHexColor(results.first())?.let { parsed ->
                val hsv = FloatArray(3).also { arr -> android.graphics.Color.colorToHSV(parsed.toArgb(), arr) }
                hue = hsv[0]
                saturation = hsv[1]
                brightness = hsv[2]
                customColor = parsed
                onColorChanged(parsed)
            }
            showHexDialog = false
        },
    )
}

/** HSV（0..360 / 0..1 / 0..1）→ Compose Color。 */
private fun colorFromHsv(hue: Float, saturation: Float, brightness: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))

/**
 * 饱和度 × 明度方块：横轴饱和度（左白右纯色），纵轴明度（上亮下黑）。
 * 拖动 / 点按都跟手，白色圆环标出当前取值。
 */
@Composable
private fun SaturationValueSquare(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(onChange)
    val shape = remember { MochiRoundedRectangle(18.dp) }
    Canvas(
        modifier = modifier
            .clip(shape)
            .pointerInput(Unit) {
                fun pick(x: Float, y: Float) {
                    current(
                        (x / size.width).coerceIn(0f, 1f),
                        (1f - y / size.height).coerceIn(0f, 1f),
                    )
                }
                detectTapGestures { pick(it.x, it.y) }
            }
            .pointerInput(Unit) {
                fun pick(x: Float, y: Float) {
                    current(
                        (x / size.width).coerceIn(0f, 1f),
                        (1f - y / size.height).coerceIn(0f, 1f),
                    )
                }
                detectDragGestures(
                    onDragStart = { pick(it.x, it.y) },
                ) { change, _ -> pick(change.position.x, change.position.y) }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, colorFromHsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = saturation * size.width
        val cy = (1f - brightness) * size.height
        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
        drawCircle(
            color = Color.Black.copy(alpha = 0.25f),
            radius = 9.5.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(1.dp.toPx()),
        )
    }
}

/** 色相条：红→黄→绿→青→蓝→品红的胶囊渐变 + 白色圆钮，点 / 拖都改色相。 */
@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(onHueChange)
    Canvas(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                fun pick(x: Float) {
                    current((x / size.width).coerceIn(0f, 1f) * 360f)
                }
                detectTapGestures { pick(it.x) }
            }
            .pointerInput(Unit) {
                fun pick(x: Float) {
                    current((x / size.width).coerceIn(0f, 1f) * 360f)
                }
                detectDragGestures(
                    onDragStart = { pick(it.x) },
                ) { change, _ -> pick(change.position.x) }
            },
    ) {
        val trackHeight = 12.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val rainbow = listOf(0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f, 360f)
            .map { colorFromHsv(it, 1f, 1f) }
        drawRoundRect(
            brush = Brush.horizontalGradient(rainbow),
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )
        val cx = (hue / 360f).coerceIn(0f, 1f) * size.width
        drawCircle(Color.White, radius = 12.dp.toPx(), center = Offset(cx, size.height / 2f))
        drawCircle(
            color = Color.Black.copy(alpha = 0.18f),
            radius = 12.dp.toPx(),
            center = Offset(cx, size.height / 2f),
            style = Stroke(1.dp.toPx()),
        )
    }
}

internal fun parseHexColor(input: String): Color? {
    return try {
        val normalized = input.trim()
            .replace(" ", "")
            .replace("0x", "")
            .replace("0X", "")
            .let { if (!it.startsWith("#") && it.isNotEmpty()) "#$it" else it }
        val finalHex = when (normalized.length) {
            7 -> "#FF${normalized.substring(1)}"
            9 -> normalized
            6 -> "#FF$normalized"
            8 -> "#$normalized"
            else -> return null
        }
        Color(finalHex.toColorInt())
    } catch (_: Exception) {
        null
    }
}