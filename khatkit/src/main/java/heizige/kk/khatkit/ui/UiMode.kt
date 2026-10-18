package heizige.kk.khatkit.ui

/**
 * 一次卡片运行对 UI 的策略（`CardExecutionOptions.uiMode`）。
 *
 * 卡片脚本里的 `ui.*` 是**运行期能力**，不是全局开关：同一条脚本在有人看着的时候
 * 可以把参数交给用户填、把结果贴出来；在无人值守的时候（定时任务 / 事件触发 / Flow
 * 子步骤）必须能一句“跳过”就继续跑完，而不是把线程挂死等一个永远不会来的回答。
 *
 * - [INTERACTIVE]：注入宿主的 UI 宿主（`UiBridgeHost` 或运行期的 `CardWidgetBridge`）。
 *   `ui.form` / `ui.screen` / `ui.sheet` / `ui.confirm` 阻塞等用户，结果回脚本。
 * - [HEADLESS]：不注入任何真实 UI；`ui.*` 由 [HeadlessUiBridge] 立即返回 nil / no-op。
 *   脚本不会卡住，也不会在屏幕上留下弹层。
 *
 * 默认值由宿主按触发来源决定（见 `docs/lua-card-development.md` §3.5）：
 * `AI` / `用户` / `聊天小组件` → [INTERACTIVE]；`定时任务` / `事件触发` / `flow` → [HEADLESS]。
 * AI 也可以在一次调用里显式要求 [HEADLESS]（工具参数 `__khatkit_ui = "headless"`），
 * 表示“我只要结果，别让用户操作”。
 */
enum class UiMode(val id: String) {
    INTERACTIVE("interactive"),
    HEADLESS("headless");

    companion object {
        fun fromId(raw: String?): UiMode? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}
