package heizige.kk.khatkit.app.core.ui.mochispike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiLargeTitleScaffold
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.darkColorScheme
import ui.feice.mochi.theme.lightColorScheme

/**
 * P1 spike 一次性验证屏（kill-switch 证据用）。
 *
 * 只由 adb 显式启动（`am start -n <pkg>/heizige.kk.khatkit.app.core.ui.mochispike.MochiSpikeActivity`），
 * 不进入 RouteActivity 的导航图，不影响正式路由。P7 记账阶段可整体删除。
 *
 * 验证目标：mochi-compose 0.2.0 在 Compose BOM 2026.09.00 / Kotlin 2.4.20 下能否
 * 编译、渲染（MochiTheme + MochiLargeTitleScaffold + MochiListSection）、
 * 交互（行点击自增计数、MochiSheet 弹出）与返回（back 关闭 sheet 后再退出）。
 */
class MochiSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MochiTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                ProvideMochiOverscroll {
                    SpikeScreen()
                }
            }
        }
    }
}

@Composable
private fun SpikeScreen() {
    var taps by remember { mutableIntStateOf(0) }
    var sheetVisible by remember { mutableStateOf(false) }
    val sheetState = rememberMochiSheetState()

    MochiLargeTitleScaffold(
        title = "Mochi Spike",
        subtitle = "mochi-compose 0.2.0",
    ) {
        MochiListSection(header = "兼容性探针") {
            MochiListRow(
                title = "点击次数",
                value = "$taps",
                chevron = true,
                onClick = { taps++ },
            )
            MochiListRow(
                title = "打开 MochiSheet",
                chevron = true,
                onClick = { sheetVisible = true },
            )
        }
        MochiListSection {
            MochiListButton(text = "打开 Sheet（列内按钮）", onClick = { sheetVisible = true })
        }
    }

    if (sheetVisible) {
        MochiSheet(state = sheetState, onDismiss = { sheetVisible = false }) {
            MochiListSection(header = "Sheet") {
                MochiListRow(title = "Sheet 内行", subtitle = "点背景或返回关闭", chevron = false)
            }
            MochiListButton(text = "关闭", onClick = { sheetVisible = false })
        }
    }
}
