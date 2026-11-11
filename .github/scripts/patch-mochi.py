#!/usr/bin/env python3
"""Patch upstream Mochi v0.2.0 sources with the small API additions that the
KhatKit app was built against (local `0.2.0-khatkit` artifact):
  1. MochiChip: `labelContent` / `trailingContent` slots
  2. MochiItem: `enabled` flag (+ dimmed/disabled rendering in MenuList)

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

print("mochi patched OK")
