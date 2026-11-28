package heizige.kk.khatkit.app.feature.chat.data

import heizige.kk.khatkit.hub.LoadedCard
import heizige.kk.khatkit.ui.CardWidgetContent

internal data class WelcomeWidget(
    val card: LoadedCard,
    val enabled: Boolean,
    /** 当前格数：用户拖过就用落盘值，否则 manifest 默认值；始终在 [limits] 内。 */
    val size: WidgetCells,
    val limits: WidgetSizeLimits,
    val loading: Boolean = false,
    /** 脚本最近一次 ui.show：主树 + 各尺寸版本，渲染时按 [size] 现挑一版。 */
    val content: CardWidgetContent? = null,
    val error: String? = null,
) {
    val refreshAction: String get() = content?.refreshAction.orEmpty()
}

internal data class ChatWelcomeState(
    val widgets: List<WelcomeWidget> = emptyList(),
    val toolSize: WidgetCells = WidgetSizeLimits.ALL_TOOLS.default,
    val terminalAvailable: Boolean = false,
    val terminalEnabled: Boolean = false,
    val terminalSize: WidgetCells = WidgetSizeLimits.TERMINAL.default,
)

internal const val ALL_TOOLS_WIDGET = "__khatkit_all_tools"

internal const val TERMINAL_WIDGET = "__khatkit_terminal"

internal val WidgetSizeLimits.Companion.ALL_TOOLS: WidgetSizeLimits
    get() = WidgetSizeLimits(
        min = WidgetCells(1, 1),
        max = WidgetCells(4, 6),
        default = WidgetCells(4, 1),
    )

internal val WidgetSizeLimits.Companion.TERMINAL: WidgetSizeLimits
    get() = WidgetSizeLimits(
        min = WidgetCells(2, 1),
        max = WidgetCells(4, 3),
        default = WidgetCells(4, 1),
    )
