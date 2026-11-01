package heizige.kk.khatkit.app.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.LocalKkNavController
import heizige.kk.khatkit.app.Screen
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/** 内置工具入口常驻，不依赖是否安装过带小组件的卡片。 */
@Composable
internal fun ToolCatalogueWidget(modifier: Modifier = Modifier) {
    val nav = LocalKkNavController.current
    MochiListCard(
        modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .mochiNavSource(TOOL_CATALOGUE_SOURCE, MochiListDefaults.SectionCorner, MochiDefaults.groupedContainer())
            .mochiPressDeform()
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                nav?.navigate(Screen.ToolCatalogue, source = TOOL_CATALOGUE_SOURCE)
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MochiIonicon("apps", size = 30.dp, tint = MochiTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("查看所有工具", style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold))
                Text("按功能查找，直接打开使用", style = MochiTheme.textStyles.footnote, color = MochiTheme.colorScheme.onSurfaceVariant)
            }
            MochiIonicon("chevron-forward", size = 18.dp, tint = MochiTheme.colorScheme.onSurfaceVariant)
        }
    }
}
