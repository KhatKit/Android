package heizige.kk.khatkit.app.feature.tools

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import heizige.kk.khatkit.app.feature.tools.helper.toolCategory
import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.uikit.rememberCardIconVector
import ui.feice.mochi.icons.MochiIonicon

@Composable
internal fun ToolEntryIcon(entry: CardIndexEntry, header: String?, size: Dp, tint: Color) {
    val vector = header?.let { rememberCardIconVector(it) }
    if (vector != null) Icon(vector, null, Modifier.size(size), tint = tint)
    else MochiIonicon(entry.toolCategory().icon, size = size, tint = tint)
}
