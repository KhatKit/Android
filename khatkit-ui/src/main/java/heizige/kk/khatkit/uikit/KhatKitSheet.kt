package heizige.kk.khatkit.uikit

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import heizige.kk.khatkit.uikit.mochi.BasicBottomSheet
import heizige.kk.khatkit.uikit.mochi.LocalBottomSheetDismiss
import heizige.kk.khatkit.uikit.mochi.KkSheetShell
import heizige.kk.khatkit.ui.UiSheetOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.uikit.mochi.MochiButton

/**
 * 为 true 时 [KhatKitSheet] 不弹层，把内容直接铺进当前页面（卡片整页承载用）。
 * 标题、返回由页面自己给；只保留内容与确认按钮。
 */
val LocalKhatKitSheetInline = staticCompositionLocalOf { false }

/**
 * BottomSheet 拖柄顶行右侧的一个动作（`ui.sheet` / `web.openLogin` 的 action）。
 *
 * 脚本只声明 `icon` 名与 `event`，宿主把它解析成 [imageVector] 后传进来；
 * [label] 用作无障碍描述与 tooltip，[event] 是点击后回传卡片的事件名。
 */
data class KhatKitSheetAction(
    val event: String,
    val label: String,
    val imageVector: ImageVector,
)

/**
 * 卡片弹层统一入口（`ui.form` / `ui.show` / `ui.sheet` / `ui.screen` 消费 [UiSheetOptions]）。
 *
 * 三条渲染路径，按「脚本要多少控制权」分流：
 *
 * 1. 什么都没要（默认）：沿用 Khromia [PrimaryBottomSheet]（Miuix 风格走 Kedge 分支），外观与原版完全一致；
 * 2. 只要顶栏动作（`ui.sheet` / `web.openLogin` 的 `placement=top|overflow`）：[TopBarSheet]，
 *    动作落在拖柄所在顶行右侧，底部只留关闭按钮；
 * 3. 要全屏 / 指定高度 / 隐藏顶栏 / 隐藏底栏 / 锁定不可关闭（[UiSheetOptions.usesLargeSheet]）：
 *    换 [KhatKitBottomSheet] —— 独立的、完全受控的弹层，两个 UI 风格行为一致。
 *
 * 方向（`orientation`）不属于渲染路径：由 `KhatKitUiHost` 改 Activity 方向，三条路径都生效。
 */
@Composable
fun KhatKitSheet(
    title: String,
    imageVector: ImageVector,
    options: UiSheetOptions,
    dismissText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    headerActions: List<KhatKitSheetAction> = emptyList(),
    overflowActions: List<KhatKitSheetAction> = emptyList(),
    overflowIcon: ImageVector? = null,
    onAction: (KhatKitSheetAction) -> Unit = {},
    onDismiss: () -> Unit,
    /** 点底部导航条目时回传它的事件名（`options.nav` 只在自绘弹层里渲染）。 */
    onNavigate: (String) -> Unit = {},
    /** 内容变化时换一个值即可把弹层滚动位置归零（卡片换屏用）。 */
    scrollKey: Any? = null,
    /** 每次请求的内容标识（重置触底加载守卫用）。 */
    contentKey: Any? = null,
    /** The content owns scrolling when it is a lazy list. */
    lazyListState: LazyListState? = null,
    pagerState: PagerState? = null,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    if (LocalKhatKitSheetInline.current) {
        InlineSheet(confirmText, onConfirm, onDismiss, scrolls = lazyListState == null && pagerState == null, content)
        return
    }

    val hasTopBar = headerActions.isNotEmpty() ||
        (overflowIcon != null && overflowActions.isNotEmpty())

    if (options.usesLargeSheet) {
        KhatKitBottomSheet(
            title = title,
            imageVector = imageVector,
            options = options,
            dismissText = dismissText,
            confirmText = confirmText,
            onConfirm = onConfirm,
            headerActions = headerActions,
            overflowActions = overflowActions,
            overflowIcon = overflowIcon,
            onAction = onAction,
            onDismiss = onDismiss,
            onNavigate = onNavigate,
            scrollKey = scrollKey,
            contentKey = contentKey,
            lazyListState = lazyListState,
            pagerState = pagerState,
            content = content,
        )
        return
    }

    if (!hasTopBar) {
        KkSheetShell(
            visible = true,
            title = title,
            imageVector = imageVector,
            dismissText = dismissText,
            confirmText = confirmText,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            dismissible = options.dismissable,
            scrollable = true,
        ) { dismiss -> content(dismiss) }
        return
    }

    TopBarSheet(
        title = title,
        headerActions = headerActions,
        overflowActions = overflowActions,
        overflowIcon = overflowIcon,
        dismissText = dismissText,
        onAction = onAction,
        onDismiss = onDismiss,
        content = content,
    )
}

/** 拖柄顶行 + 标题的弹层：动作位于拖柄右侧，底部只留关闭按钮。 */
@Composable
private fun TopBarSheet(
    title: String,
    headerActions: List<KhatKitSheetAction>,
    overflowActions: List<KhatKitSheetAction>,
    overflowIcon: ImageVector?,
    dismissText: String?,
    onAction: (KhatKitSheetAction) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    BasicBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        enablePredictiveBack = true,
        scrollable = true,
        dragHandle = {
            SheetTopBar(
                title = title,
                headerActions = headerActions,
                overflowActions = overflowActions,
                overflowIcon = overflowIcon,
                onAction = onAction,
            )
        },
        bottomBar = { SheetCloseBar(dismissText) },
        content = { content(LocalBottomSheetDismiss.current) },
    )
}

/** 页面里的「弹层」：内容占满剩余高度，自己不滚动的内容由这里滚；确认按钮贴底。 */
@Composable
private fun InlineSheet(
    confirmText: String?,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    scrolls: Boolean,
    content: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            content(onDismiss)
        }
        if (confirmText != null && onConfirm != null) {
            MochiButton(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) { Text(confirmText) }
        }
    }
}
