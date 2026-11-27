#!/usr/bin/env python3
"""Patch upstream Mochi v0.2.0 sources with the small API additions that the
KhatKit app was built against (see the version catalog for the local artifact):
  1. MochiChip: `labelContent` / `trailingContent` slots
  2. MochiItem: `enabled` flag (+ dimmed/disabled rendering in MenuList)
  3. MochiButton / MochiChip: 新增 `yieldToScroll` —— 滚动容器内的 chip 拖动超过 touchSlop 后
     把这一下手势让给父级（横向 chip 行 / 列表可接管），短按仍触发点击；普通按钮保持整段所有权
  4. Dropdown/popover placement respects the IME as well as the navigation bar.
  5. Sheet lists and nested navigation measure the visible detent, not the tallest one.

Usage: python3 .github/scripts/patch-mochi.py <mochi-checkout-dir>
"""
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()

def patch(rel, old, new, expect=1):
    p = root / rel
    s = p.read_text(encoding="utf-8")
    n = s.count(old)
    if n != expect:
        raise SystemExit(f"anchor mismatch in {rel}: found {n}, expected {expect}\n---\n{old[:200]}")
    p.write_text(s.replace(old, new), encoding="utf-8")
    print(f"patched {rel}")


# ---- 1. MochiChip: rich label / trailing content slots --------------------
chip = "mochi-compose/src/main/java/ui/feice/mochi/basic/MochiChip.kt"
patch(
    chip,
    """    leadingIcon: (@Composable () -> Unit)? = null,
    colors: MochiChipColors = MochiChipDefaults.colors(),
) {
    ChipCore(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        enabled = enabled,
        colors = colors,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showCheck) CheckSlot(selected)
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(6.dp))
            }
            Text(label, maxLines = 1, style = ChipTextStyle())
        }
    }
}""",
    """    leadingIcon: (@Composable () -> Unit)? = null,
    labelContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: MochiChipColors = MochiChipDefaults.colors(),
) {
    ChipCore(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        enabled = enabled,
        colors = colors,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showCheck) CheckSlot(selected)
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(6.dp))
            }
            if (labelContent != null) {
                labelContent()
            } else {
                Text(label, maxLines = 1, style = ChipTextStyle())
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(6.dp))
                trailingContent()
            }
        }
    }
}""",
)

# ---- 2. MochiItem: enabled flag ------------------------------------------
patch(
    "mochi-compose/src/main/java/ui/feice/mochi/basic/MochiDropdownDefaults.kt",
    """public class MochiItem(
    public val label: String,
    public val destructive: Boolean = false,
    public val icon: (@Composable (tint: Color) -> Unit)? = null,
)""",
    """public class MochiItem(
    public val label: String,
    public val destructive: Boolean = false,
    public val icon: (@Composable (tint: Color) -> Unit)? = null,
    public val enabled: Boolean = true,
)""",
)

# ---- 3. MenuList: render disabled items dimmed and non-clickable ---------
menu = "mochi-compose/src/main/java/ui/feice/mochi/basic/menu/MenuList.kt"
patch(
    menu,
    """                    val base = if (item.destructive) style.destructiveColor else style.onPanelColor""",
    """                    val base = when {
                        !item.enabled -> style.onPanelColor.copy(alpha = 0.38f)
                        item.destructive -> style.destructiveColor
                        else -> style.onPanelColor
                    }""",
)
patch(
    menu,
    """                            onClick {
                                onSelect(i)
                                true
                            }""",
    """                            onClick {
                                if (item.enabled) {
                                    onSelect(i)
                                    true
                                } else {
                                    false
                                }
                            }""",
)


# ---- 4. MochiButton: yieldToScroll —— 可滚动容器内的按钮在 touch slop 后让位 ----
button = "mochi-compose/src/main/java/ui/feice/mochi/basic/MochiButton.kt"
patch(
    button,
    """                    val down = awaitFirstDown()
                    // 在 Main 阶段声明点击归属，避免面板的空白点击手势把按钮的按下事件抢走。
                    down.consume()""",
    """                    val down = awaitFirstDown()
                    // 滚动容器里的芯片不预先消费 down，让父级滚动可以观察并接管拖动。
                    if (!behavior.yieldToScroll) down.consume()""",
)
patch(
    button,
    """                    val touchSlop = viewConfiguration.touchSlop
                    var moved = false
                    var click = false""",
    """                    val touchSlop = viewConfiguration.touchSlop
                    var moved = false
                    var yielded = false
                    var click = false""",
)
patch(
    button,
    """                            if (!moved && (c.position - down.position).getDistance() > touchSlop) moved = true
                            if (moved) {""",
    """                            if (!moved && ((c.position - down.position).getDistance() > touchSlop || c.isConsumed)) {
                                moved = true
                                if (behavior.yieldToScroll) {
                                    yielded = true
                                    visual.held = false
                                    glow.markReleased()
                                    scope.launch { glow.release() }
                                    glassGlow?.release()
                                    groupGlow?.release()
                                }
                            }
                            if (moved && !yielded) {""",
)
patch(
    button,
    """                                click = p.x > -slop && p.y > -slop && p.x < size.width + slop && p.y < size.height + slop
                                c.consume()
                                break""",
    """                                click = p.x > -slop && p.y > -slop && p.x < size.width + slop && p.y < size.height + slop
                                if (!yielded) c.consume()
                                break""",
)
patch(
    button,
    """                            // 按在按钮上的这一整下都归按钮：Main 阶段先于外层消费移动，外层的滚动、面板拖动、
                            // 右滑返回都不会接手，按钮一直跟手直到松手（拖出 cancelSlop 外松手不算点击）
                            c.consume()""",
    """                            // 默认按钮保持整段手势所有权；可滚动 chip 在 touch slop 后释放给父级轨道/列表。
                            if (!yielded) c.consume()""",
)
patch(
    button,
    "                    if (click) currentOnClick()",
    "                    if (click && !yielded) currentOnClick()",
)

# ---- 4b. MochiButtonBehavior / MochiChip: 透出并启用 yieldToScroll ---------
defaults = "mochi-compose/src/main/java/ui/feice/mochi/basic/MochiButtonDefaults.kt"
patch(
    defaults,
    """    val fuseGroup: Any? = null,
)""",
    """    val fuseGroup: Any? = null,
    /** 芯片等可滚动容器内的按钮：拖动超过 touch slop 后让位给父级滚动，短按仍可点击。 */
    val yieldToScroll: Boolean = false,
)""",
)
patch(
    defaults,
    """        fuseGroup: Any? = null,
    ): MochiButtonBehavior = MochiButtonBehavior(pressScale, follow, cancelSlop, maxGrow, fuseGroup)""",
    """        fuseGroup: Any? = null,
        yieldToScroll: Boolean = false,
    ): MochiButtonBehavior = MochiButtonBehavior(
        pressScale = pressScale,
        follow = follow,
        cancelSlop = cancelSlop,
        maxGrow = maxGrow,
        yieldToScroll = yieldToScroll,
        fuseGroup = fuseGroup,
    )""",
)
patch(
    chip,
    "        behavior = MochiButtonDefaults.behavior(pressScale = 1.06f, maxGrow = 4.dp),",
    "        behavior = MochiButtonDefaults.behavior(pressScale = 1.06f, maxGrow = 4.dp, yieldToScroll = true),",
)

# The menu lives in an edge-to-edge overlay; its layout size does not shrink
# when the editor is focused. Limit placement to the area above the keyboard.
for rel in ("menu/DropdownOverlay.kt", "MochiPopover.kt"):
    source = f"mochi-compose/src/main/java/ui/feice/mochi/basic/{rel}"
    patch(
        source,
        "import androidx.compose.foundation.layout.navigationBars\n",
        "import androidx.compose.foundation.layout.ime\n"
        "import androidx.compose.foundation.layout.navigationBars\n",
    )
    patch(
        source,
        "val safeBottom = WindowInsets.navigationBars.getBottom(density).toFloat()",
        """val safeBottom = maxOf(
            WindowInsets.navigationBars.getBottom(density),
            WindowInsets.ime.getBottom(density),
        ).toFloat()""",
    )

# Medium and Large share a surface, not a viewport height. Keep Mochi's clipping,
# overscroll and navigation bar treatment; use the same surface bounds for layout.
sheet = "mochi-compose/src/main/java/ui/feice/mochi/basic/MochiSheet.kt"
patch(
    sheet,
    """                height = {
                    val frame = sheetFrame(state.width, state.height.roundToInt(), state.top.value, state.largeness, inset)
                    val highest = state.detents.minOf { state.topOf(it) }
                    val fixedHeight = (state.height - highest).coerceAtLeast(0f).roundToInt()
                    sheetSurfaceHeight(frame, fixedHeight, state.fillsBottom)
                },
                contentHeight = {
                    val highest = state.detents.minOf { state.topOf(it) }
                    val bottomGap = if (MochiSheetDetent.Large in state.detents) 0f else inset
                    ((state.height - highest - bottomGap).coerceAtLeast(0f).roundToInt() - navigationBottom).coerceAtLeast(0)
                },""",
    """                height = surfaceHeight,
                contentHeight = { (surfaceHeight() - navigationBottom).coerceAtLeast(0) },""",
)
patch(
    sheet,
    """            MochiNavSurface(""",
    """            val surfaceHeight = {
                val frame = sheetFrame(state.width, state.height.roundToInt(), state.top.value, state.largeness, inset)
                val highest = state.detents.minOf { state.topOf(it) }
                sheetSurfaceHeight(frame, (state.height - highest).coerceAtLeast(0f).roundToInt(), state.fillsBottom)
            }
            MochiNavSurface(""",
)
patch(
    sheet,
    """                                .layout { m, c ->
                                    // 内容沿用原来的固定测量高度；底部补齐仅由表面在停稳后处理。
                                    val highest = state.detents.minOf { state.topOf(it) }
                                    val bottomGap = if (MochiSheetDetent.Large in state.detents) 0f else inset
                                    val h = (state.height - highest - bottomGap).coerceAtLeast(0f).roundToInt()
                                    val p = m.measure(Constraints.fixed(c.maxWidth, h))
                                    layout(c.maxWidth, c.maxHeight) { p.place(0, 0) }
                                }""",
    """                                .fillMaxSize()""",
)

print("mochi patched OK")
