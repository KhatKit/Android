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

# Preserve the original content signature; the explicit overload also supports lazy bodies.
# Reuse the pinned library's header geometry without adding an outer scrolling viewport.
patch(
    "MochiSheet.kt",
    """public fun MochiSheetContent(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val corner = LocalMochiSheetCorner.current
    val metrics = corner as? SheetLayoutMetrics
    Column(
        modifier
            .fillMaxWidth()
            // 放不下时能竖着滚：滚到顶再往下拖、往上推还没到最高一档时，拖的是面板（见 MochiSheet）
            .verticalScroll(rememberScrollState())
            .layout { m, c ->
                val horizontal = sheetStableHorizontalInset(metrics, corner)
                val vertical = sheetStableTopInset(metrics, corner)
                val w = (c.maxWidth - horizontal * 2).coerceAtLeast(0)
                val p = m.measure(c.copy(minWidth = w, maxWidth = w))
                layout(c.maxWidth, p.height + vertical * 2) { p.place(horizontal, vertical) }
            },
        verticalArrangement = verticalArrangement,
        content = content,
    )
}""",
    """public fun MochiSheetContent(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    MochiSheetContent(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        scrollable = true,
        content = content,
    )
}

/**
 * The original overload keeps library-owned scrolling. Pass [scrollable] = false for a
 * caller-owned lazy list or scroller. Its finite viewport excludes the concentric insets;
 * the surrounding sheet remains the owner of IME, navigation safety and drag coordination.
 */
@Composable
public fun MochiSheetContent(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    scrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val corner = LocalMochiSheetCorner.current
    val metrics = corner as? SheetLayoutMetrics
    Column(
        modifier
            .fillMaxWidth()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .layout { m, c ->
                val horizontal = sheetStableHorizontalInset(metrics, corner)
                val vertical = sheetStableTopInset(metrics, corner)
                val w = (c.maxWidth - horizontal * 2).coerceAtLeast(0)
                val p = if (scrollable) {
                    // Keep the original scroll viewport, measurement and nested-scroll behavior.
                    m.measure(c.copy(minWidth = w, maxWidth = w))
                } else {
                    check(c.hasBoundedHeight) { "Non-scrolling sheet content requires a bounded height" }
                    m.measure(
                        c.copy(
                            minWidth = w,
                            maxWidth = w,
                            minHeight = (c.minHeight - vertical * 2).coerceAtLeast(0),
                            maxHeight = (c.maxHeight - vertical * 2).coerceAtLeast(0),
                        ),
                    )
                }
                val height = if (scrollable) p.height + vertical * 2 else
                    (p.height + vertical * 2).coerceIn(c.minHeight, c.maxHeight)
                layout(c.maxWidth, height) { p.place(horizontal, vertical) }
            },
        verticalArrangement = verticalArrangement,
        content = content,
    )
}""",
)

# The sheet already lifts/shrinks its content for the keyboard. Consume that bottom
# IME inset for descendants without adding padding or consuming other system sides.
patch(
    "MochiSheet.kt",
    "import androidx.compose.foundation.layout.displayCutout\n",
    "import androidx.compose.foundation.layout.consumeWindowInsets\n"
    "import androidx.compose.foundation.layout.displayCutout\n",
)
patch(
    "MochiSheet.kt",
    """                                    layout(p.width, p.height + k) { p.place(0, 0) }
                                },""",
    """                                    layout(p.width, p.height + k) { p.place(0, 0) }
                                }
                                .consumeWindowInsets(WindowInsets.ime.only(WindowInsetsSides.Bottom)),""",
)

# Keep the upstream picker body; consumers may supply transactional header/footer actions.
patch(
    "../picker/MochiColorPicker.kt",
    """    recents: List<Color> = emptyList(),
    onDismiss: () -> Unit = {},
) {""",
    """    recents: List<Color> = emptyList(),
    onDismiss: () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {""",
)
patch(
    "../picker/MochiColorPicker.kt",
    """        MochiSheetScaffold(
            header = {""",
    """        MochiSheetScaffold(
            header = header ?: {""",
)
patch(
    "../picker/MochiColorPicker.kt",
    """            }
        }
    }
}

/** 网格：第一行灰阶，下面 12 种色相从深到浅 */""",
    """            }
            footer?.invoke()
        }
    }
}

/** 网格：第一行灰阶，下面 12 种色相从深到浅 */""",
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

# Keep the shared toolbar's glass layer populated while its page is carried.
# Suppressing LiquidGlassNode during recording can leave its cached layer empty
# after the navigation host returns the button to normal page drawing.
patch(
    "../navigation/MochiNavBarItem.kt",
    """            GlassDrawSuppression.depth++
            try {
                layer.record { this@draw.drawContent() }
            } finally {
                GlassDrawSuppression.depth--
            }""",
    """            layer.record { this@draw.drawContent() }""",
)
