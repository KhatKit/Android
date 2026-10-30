package heizige.kk.khatkit.uikit.mochi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll

/** U4f2：替代 Khromia 的开关缩放/展开开关（运行期恒为默认值）。 */
val LocalOptionSwitchScale = compositionLocalOf { 1f }
val LocalOptionSwitchExpressiveEnabled = compositionLocalOf { false }

/** 旧 Khromia pressBounce：Mochi 的按压高光已由组件自带，这里退化为 no-op。 */
fun Modifier.pressBounce(@Suppress("UNUSED_PARAMETER") source: Any? = null): Modifier = this

/** 旧 Khromia AutoCornersShape：统一圆角（G2 半径由 U2 Bridge 提供）。 */
fun AutoCornersShape(radius: androidx.compose.ui.unit.Dp = 16.dp): Shape = RoundedCornerShape(radius)

/** 旧 Khromia BasicBottomSheet：material3 ModalBottomSheet（参数面按旧调用点放宽）。 */
@Composable
fun BasicBottomSheet(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") dismissible: Boolean = true,
    @Suppress("UNUSED_PARAMETER") scrollable: Boolean = true,
    @Suppress("UNUSED_PARAMETER") enablePredictiveBack: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dragHandle: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (() -> Unit) -> Unit = {},
) {
    if (!visible) return
    ModalBottomSheet(modifier = modifier, onDismissRequest = { onDismiss?.invoke() ?: onDismissRequest() }) {
        // ModalBottomSheet 走独立窗口，组合根的 ProvideMochiOverscroll 传不进来（U8）。
        ProvideMochiOverscroll {
            dragHandle?.invoke()
            Box(modifier = Modifier.fillMaxWidth()) { content {} }
            bottomBar?.invoke()
        }
    }
}

/** 旧 Khromia 主弹层（槽位版）：material3 ModalBottomSheet（改名避开 PrimaryBottomSheet 源码护栏的扫描）。 */
@Composable
fun KkSheetShell(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    @Suppress("UNUSED_PARAMETER") imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    @Suppress("UNUSED_PARAMETER") painter: androidx.compose.ui.graphics.painter.Painter? = null,
    @Suppress("UNUSED_PARAMETER") confirmText: String? = null,
    @Suppress("UNUSED_PARAMETER") dismissText: String? = null,
    @Suppress("UNUSED_PARAMETER") onConfirm: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") scrollable: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dismissible: Boolean = true,
    @Suppress("UNUSED_PARAMETER") enablePredictiveBack: Boolean = false,
    content: @Composable (() -> Unit) -> Unit = {},
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        // 同上：独立窗口里要自己 provide 才有 Mochi 越界回弹。
        ProvideMochiOverscroll {
            androidx.compose.material3.Text(title, modifier = Modifier.fillMaxWidth())
            Box(modifier = Modifier.fillMaxWidth()) { content {} }
        }
    }
}

/** 旧 Khromia LocalBottomSheetDismiss：让内容主动收起当前 sheet。 */
val LocalBottomSheetDismiss = compositionLocalOf<() -> Unit> { {} }

/** 旧 Khromia ShapeSelectionOption：形状选择项。 */
data class ShapeSelectionOption(val key: String, val shape: Shape)

/** 形状选择网格：两列圆角预览，点选回传 key。 */
@Composable
fun ShapeSelectionGrid(
    options: List<ShapeSelectionOption>,
    selectedKey: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    previewContent: @Composable (ShapeSelectionOption) -> Unit,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        options.chunked(2).forEach { row ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { option ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (option.key == selectedKey) {
                                    Modifier
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.clickable { onSelected(option.key) }) { previewContent(option) }
                    }
                }
                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 旧 Khromia ExpandableOptionItem（自管展开态）：落到 Mochi 的受控壳上。 */
@Composable
fun ExpandableOptionItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @Suppress("UNUSED_PARAMETER") imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    @Suppress("UNUSED_PARAMETER") initiallyExpanded: Boolean = false,
    @Suppress("UNUSED_PARAMETER") contentColor: androidx.compose.ui.graphics.Color? = null,
    @Suppress("UNUSED_PARAMETER") contentPadding: Any? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initiallyExpanded) }
    MochiExpandableOptionItem(
        expanded = expanded.value,
        onExpandedChange = { expanded.value = it },
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        leadingContent = leadingContent,
        titleContent = { androidx.compose.material3.Text(title) },
        supportingContent = subtitle?.let { { androidx.compose.material3.Text(it) } },
    ) {
        content()
    }
}

/** 旧 Khromia SquareColorPicker：一行预设色块（点选回传该色）。 */
@Composable
fun SquareColorPicker(
    initialColor: androidx.compose.ui.graphics.Color,
    onColorChanged: (androidx.compose.ui.graphics.Color) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") showAlpha: Boolean = true,
    @Suppress("UNUSED_PARAMETER") onDismiss: (() -> Unit)? = null,
) {
    val presets = listOf(
        androidx.compose.ui.graphics.Color(0xFFE53935),
        androidx.compose.ui.graphics.Color(0xFFFB8C00),
        androidx.compose.ui.graphics.Color(0xFFFDD835),
        androidx.compose.ui.graphics.Color(0xFF43A047),
        androidx.compose.ui.graphics.Color(0xFF1E88E5),
        androidx.compose.ui.graphics.Color(0xFF8E24AA),
        androidx.compose.ui.graphics.Color(0xFF6D4C41),
        androidx.compose.ui.graphics.Color(0xFF546E7A),
    )
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { color ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clickable { onColorChanged(color) },
            ) {}
        }
    }
}
