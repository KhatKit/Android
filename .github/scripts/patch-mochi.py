#!/usr/bin/env python3
"""Apply KhatKit's UI compatibility patches to pinned Mochi."""
import sys
from pathlib import Path

root = Path(sys.argv[1]) / "mochi-compose/src/main/java/ui/feice/mochi/basic"


def patch(path, old, new):
    file = root / path
    source = file.read_text(encoding="utf-8")
    if source.count(old) != 1:
        raise SystemExit(f"anchor mismatch in {path}: {old}")
    file.write_text(source.replace(old, new), encoding="utf-8")


patch(
    "../effect/overscroll/cresto/OffsetOverscrollEffect.kt",
    """            val nextViewport = Size(placeable.width.toFloat(), placeable.height.toFloat())
            if (viewport != nextViewport) viewport = nextViewport""",
    """            // A lookahead pass can measure a different height while the toolbar animates.
            // Only publish the actual viewport. Reading it here would subscribe measure to its own writes.
            if (!isLookingAhead) {
                viewport = Size(placeable.width.toFloat(), placeable.height.toFloat())
            }""",
)

patch(
    "MochiChip.kt",
    """    leadingIcon: (@Composable () -> Unit)? = null,
    colors: MochiChipColors""",
    """    leadingIcon: (@Composable () -> Unit)? = null,
    labelContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: MochiChipColors""",
)
patch(
    "MochiChip.kt",
    """            ChipLabelText(label, chipTextStyle(), if (enabled) contentColorState else null)""",
    """            if (labelContent != null) {
                labelContent()
            } else {
                ChipLabelText(label, chipTextStyle(), if (enabled) contentColorState else null)
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(6.dp))
                trailingContent()
            }""",
)
patch(
    "MochiDropdownDefaults.kt",
    """    public val panel: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {""",
    """    public val panel: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    public val enabled: Boolean = true,
) {""",
)
patch(
    "MochiDropdownDefaults.kt",
    "internal var itemEnabled: Boolean = true",
    "internal var itemEnabled: Boolean = enabled",
)

for path in ("menu/DropdownOverlay.kt", "MochiPopover.kt"):
    if "import androidx.compose.foundation.layout.ime\n" not in (root / path).read_text(encoding="utf-8"):
        patch(
            path,
            "import androidx.compose.foundation.layout.navigationBars\n",
            "import androidx.compose.foundation.layout.ime\n"
            "import androidx.compose.foundation.layout.navigationBars\n",
        )
    patch(
        path,
        "val safeBottom = WindowInsets.navigationBars.getBottom(density).toFloat()",
        """val safeBottom = maxOf(
            WindowInsets.navigationBars.getBottom(density),
            WindowInsets.ime.getBottom(density),
        ).toFloat()""",
    )

patch(
    "MochiSheet.kt",
    """        // 正文的固定测量高度：按最高一档算，换档、拖动都不变（半屏时下面一截被面板裁掉）""",
    """        // KhatKit uses the visible viewport for lists and nested navigation.
        val surfaceHeight: () -> Int = remember(state, inset, highestTop) {
            {
                val frame = sheetFrame(state.width, state.height.roundToInt(), state.top.value, state.largeness, inset)
                val fixedHeight = (state.height - highestTop()).coerceAtLeast(0f).roundToInt()
                sheetSurfaceHeight(frame, fixedHeight, state.fillsBottom)
            }
        }""",
)
patch(
    "MochiSheet.kt",
    """            fixedContentHeight,
            keyboardPadding,
            highestTop,""",
    """            surfaceHeight,
            keyboardPadding,""",
)
patch(
    "MochiSheet.kt",
    """                height = {
                    val frame = sheetFrame(state.width, state.height.roundToInt(), state.top.value, state.largeness, inset)
                    val fixedHeight = (state.height - highestTop()).coerceAtLeast(0f).roundToInt()
                    sheetSurfaceHeight(frame, fixedHeight, state.fillsBottom)
                },
                contentHeight = {
                    // 和面板正文同一截：导航栏安全区 + 键盘超出安全区的部分
                    (fixedContentHeight() - navigationBottom - keyboardPadding()).coerceAtLeast(0)
                },""",
    """                height = surfaceHeight,
                contentHeight = {
                    (surfaceHeight() - navigationBottom - keyboardPadding()).coerceAtLeast(0)
                },""",
)
patch(
    "MochiSheet.kt",
    """                                .layout { m, c ->
                                    // 内容沿用原来的固定测量高度；底部补齐仅由表面在停稳后处理。
                                    val p = m.measure(Constraints.fixed(c.maxWidth, fixedContentHeight()))
                                    layout(c.maxWidth, c.maxHeight) { p.place(0, 0) }
                                }""",
    """                                .fillMaxSize()""",
)

# Expose the exact Depth implementation already used by MochiTabScaffold.
patch(
    "../navigation/MochiTabPageTransition.kt",
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.Immutable\n",
)
patch(
    "../navigation/MochiTabPageTransition.kt",
    "import ui.feice.mochi.utils.smoothstep\n",
    "import ui.feice.mochi.utils.smoothstep\nimport ui.feice.mochi.theme.MochiDefaults\nimport ui.feice.mochi.theme.MochiTheme\n",
)
patch(
    "../navigation/MochiTabPageTransition.kt",
    """    background: Color,
    modifier: Modifier = Modifier,
    link: TabBarLink? = null,""",
    """    background: Color,
    modifier: Modifier = Modifier,
    backdrop: Color = background,
    retainPages: Boolean = true,
    link: TabBarLink? = null,""",
)
patch(
    "../navigation/MochiTabPageTransition.kt",
    """        modifier.then(swipe).drawBehind {
            if (layers.size > 1) drawRect(background)
        },""",
    """        modifier.then(swipe).drawBehind {
            if (layers.size > 1) drawRect(backdrop)
        },""",
)
patch(
    "../navigation/MochiTabPageTransition.kt",
    """        layers.forEach { retained.add(it.key) }
        val shown = retained.toList()""",
    """        if (!retainPages) retained.retainAll(layers.map { it.key }.toSet())
        layers.forEach { retained.add(it.key) }
        val shown = retained.toList()""",
)
patch(
    "../navigation/MochiTabPageTransition.kt",
    """/**
 * 这一页此刻是否在场（在 [MochiTabPageTransition] 里显示或正在过渡）。""",
    """/**
 * The only peer transition currently exposed by this pinned library source.
 * The implementation is MochiTabPageTransition, also used by MochiTabScaffold.
 */
@Immutable
public class MochiPeerTransitionSpec private constructor() {
    public companion object {
        /** In-place scale and fade, with the same spring and gesture as MochiTabScaffold. */
        public val Depth: MochiPeerTransitionSpec = MochiPeerTransitionSpec()
    }
}

/**
 * Public entry point for the same retained, state-saving Depth transition used by MochiTabScaffold.
 * [pages] enables library-owned horizontal dragging; [onPageChange] is called only after a drag commits.
 * Clicks and external selection changes use [targetState] and the same transition path.
 * Only Depth is exposed: there is no Slide style or configurable scale/spring.
 * [pages] is forwarded unchanged; [direction] is unused because Depth has no horizontal translation.
 * Retained content can outlive entries removed from [pages]; callers must handle obsolete keys.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
public fun <T : Any> MochiPeerTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    pages: List<T>? = null,
    onPageChange: (T) -> Unit = {},
    direction: (from: T, to: T) -> Int = { from, to ->
        if (pages != null) pages.indexOf(to).compareTo(pages.indexOf(from)) else 1
    },
    background: Color = MochiDefaults.groupedBackground(),
    backdrop: Color = MochiTheme.colorScheme.surfaceContainerHighest,
    spec: MochiPeerTransitionSpec = MochiPeerTransitionSpec.Depth,
    retainPages: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    check(spec === MochiPeerTransitionSpec.Depth)
    MochiTabPageTransition(
        targetState = targetState,
        pages = pages,
        onPageChange = onPageChange,
        background = background,
        modifier = modifier,
        backdrop = backdrop,
        retainPages = retainPages,
        content = content,
    )
}

/**
 * 这一页此刻是否在场（在 [MochiTabPageTransition] 里显示或正在过渡）。""",
)
