package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.navigation.MochiNavigator

/**
 * 项目统一的底部弹层（U7b 起：Mochi 示例的 sheet 形态）。
 *
 * 历史：P4c 之前按旧双风格开关在「Kedge 的 Miuix sheet」与「Khromia PrimaryBottomSheet」之间分流；
 * P4c 切成 MochiModalBottomSheet（Popup + 表面色，无拖拽、无停靠、无进出场动画，且 scrollable 是空参）；
 * U7b 换成 **Mochi 真 sheet**：rememberMochiSheetState 驱动 MochiSheet，
 * 得到示例 Page 里的拖拽跟手、半屏/全屏两档吸附（换档轻震）、松手弹簧、点背景 / 返回键关闭，
 * 以及内部多页时先退子页再关面板（传 [MochiSheetState] 的重载 + 内层 MochiNavHost）。
 *
 * 显示用 state.show()、关闭用 state.hide()：本文件不再用 `if (visible)` 把内容包起来，
 * 收起动画由 MochiSheet 跑完再卸载浮层（浮层挂在窗口层，见 InWindowOverlay）。
 *
 * 兼容：签名与全部调用点一字未改；[painter]/[imageVector] 只为兼容旧调用方保留（Mochi 顶栏用
 * [MochiBarButton] 文字按钮，图标槽留给后续按页族接入）。[scrollable] 现在真的生效：
 * true 由弹层自己竖着滚，false 表示内容自带滚动容器（两个护栏测试的口径不变）。
 */
@Composable
fun PrimaryBottomSheet(
    modifier: Modifier = Modifier,
    visible: Boolean,
    title: String,
    @Suppress("UNUSED_PARAMETER") painter: Painter? = null,
    @Suppress("UNUSED_PARAMETER") imageVector: ImageVector? = null,
    dismissText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    dismissible: Boolean = true,
    /** 顶栏浮在正文上（iOS sheet 形态）：正文不再被顶栏挤下去，滚动只发生在正文区。 */
    floatingHeader: Boolean = false,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    val sheetState = rememberMochiSheetState()
    // 兼容薄层：Boolean 只负责把状态搬进 MochiSheetState；组合树里没有 if (visible)。
    LaunchedEffect(visible) {
        if (visible) sheetState.show() else sheetState.hide()
    }
    PrimarySheetShell(
        state = sheetState,
        modifier = modifier,
        title = title,
        // 外部把 visible 置 false 引起的收起不回调 onDismiss（那是调用方自己的动作），
        // 只有用户拖拽 / 点背景 / 返回键关掉时 visible 仍是 true，才转给调用方。
        visible = visible,
        dismissText = dismissText,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        scrollable = scrollable,
        dismissible = dismissible,
        floatingHeader = floatingHeader,
        navigator = null,
        content = content,
    )
}

/**
 * 状态直传重载：页面自己 rememberMochiSheetState，打开前 state.show()、关闭后 state.hide()；
 * Modifier.mochiSheetBackdrop(state) 只能挂在不透明的整页根上：它在面板展开（含弹出时的过冲）时先铺黑底再缩小页面，
 * 挂在 Scaffold 里透明的列表上会让整页闪一下黑。
 * 多页内容把面板自己的 MochiNavigator 传给 MochiSheet 即可先退子页再关面板。
 */
@Composable
fun PrimaryBottomSheet(
    state: MochiSheetState,
    modifier: Modifier = Modifier,
    title: String,
    // 图标槽沿用旧重载：护栏测试要求每个调用点都显式传 painter=/imageVector=（Mochi 顶栏用文字按钮，这里只收不用）。
    @Suppress("UNUSED_PARAMETER") painter: Painter? = null,
    @Suppress("UNUSED_PARAMETER") imageVector: ImageVector? = null,
    dismissText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    dismissible: Boolean = true,
    /** 顶栏浮在正文上（iOS sheet 形态）：正文不再被顶栏挤下去，滚动只发生在正文区。 */
    floatingHeader: Boolean = false,
    /** 面板内多页内容用的独立导航器（和内容里的 MochiNavHost 同一个）：有子页时先退页，退回根页才关面板。 */
    navigator: MochiNavigator? = null,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    PrimarySheetShell(
        state = state,
        modifier = modifier,
        title = title,
        visible = null,
        navigator = navigator,
        dismissText = dismissText,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        scrollable = scrollable,
        dismissible = dismissible,
        floatingHeader = floatingHeader,
        content = content,
    )
}

/**
 * 两种入口共用的壳：顶栏（[MochiSheetHeader]：左关闭 / 右确认）+ 正文。
 * 顶部标题行取代了旧版「标题 Text + 底部通栏主按钮 + 底部文字按钮」的排布，
 * 按钮仍走同一套 Mochi 按压态与触觉。
 */
@Composable
private fun PrimarySheetShell(
    state: MochiSheetState,
    modifier: Modifier,
    title: String,
    /** null = 状态直传（没有外部 Boolean，用户每次关闭都回调）。 */
    visible: Boolean?,
    dismissText: String?,
    confirmText: String?,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    scrollable: Boolean,
    dismissible: Boolean,
    floatingHeader: Boolean,
    navigator: MochiNavigator?,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentVisible by rememberUpdatedState(visible)
    MochiSheet(
        state = state,
        modifier = modifier,
        navigator = navigator,
        onDismiss = {
            if (dismissible && currentVisible != false) currentOnDismiss()
        },
    ) {
        val hasHeader = title.isNotEmpty() || dismissText != null || (confirmText != null && onConfirm != null)
        val header: (@Composable () -> Unit)? = if (hasHeader) {
            {
                MochiSheetHeader(
                title = title,
                navigationIcon = if (dismissText != null) {
                    {
                        MochiBarButton(
                            text = dismissText,
                            onClick = {
                                if (dismissible) currentOnDismiss() else state.hide()
                            },
                        )
                    }
                } else {
                    null
                },
                actions = if (confirmText != null && onConfirm != null) {
                    {
                        MochiBarButton(
                            text = confirmText,
                            onClick = { onConfirm() },
                        )
                    }
                } else {
                    null
                },
            )
            }
        } else {
            null
        }
        if (floatingHeader && header != null) {
            // 悬浮顶栏：顶栏浮在正文之上，正文从顶栏下面开始；正文自己管滚动（scrollable 应为 false）。
            var headerHeightPx by remember { mutableIntStateOf(0) }
            val headerHeight = with(LocalDensity.current) { headerHeightPx.toDp() }
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(top = headerHeight)) { content(onDismiss) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headerHeightPx = it.height }
                        .background(MochiDefaults.sheetBackground()),
                ) { header() }
            }
            return@MochiSheet
        }
        if (hasHeader) {
            header?.invoke()
        }
        if (scrollable) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                content(onDismiss)
            }
        } else {
            // 内容自带 LazyColumn / verticalScroll：不能再套一层滚动容器。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                content(onDismiss)
            }
        }
    }
}
