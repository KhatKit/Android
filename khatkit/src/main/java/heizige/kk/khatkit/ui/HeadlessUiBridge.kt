package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.UiBridge

/**
 * 无人值守运行（[UiMode.HEADLESS]）使用的 UI 桥：**所有交互立即返回 null / no-op**。
 *
 * 与「宿主没接线」不同，这里是**显式契约**，不是异常：
 *
 * - 脚本调用 `ui.form` / `ui.screen` / `ui.sheet` / `ui.webSheet` / `ui.confirm`
 *   → 立刻拿到 `nil` / `false`，脚本据此走「用户没给值」的分支继续跑完；
 * - `ui.show` / `ui.progress` / `ui.automationStatus` → 丢弃（无人看屏）；
 * - `ui.isCancelled()` → 恒 `false`：无人值守没有看板「停止」按钮，取消由外部的
 *   运行期限（`CardExecutionOptions.deadlineAt`）和调度侧的超时负责。
 *
 * 之所以不抛错：抛错会把「定时任务里顺手调了一次 ui.form」变成整卡执行失败，
 * 而在无人值守语义下这本来就该是「拿不到用户输入」而不是「脚本坏了」。
 */
object HeadlessUiBridge : UiBridge {
    override fun sheet(
        title: String,
        actions: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? = null

    override fun webSheet(
        title: String,
        url: String,
        actions: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? = null

    override fun form(
        title: String,
        items: Any,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? = null

    override fun screen(
        title: String,
        root: Map<String, Any?>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? = null

    override fun confirm(title: String, message: String, danger: Boolean): Boolean = false

    override fun progress(ratio: Float, label: String) = Unit

    override fun show(card: Map<String, Any?>, options: Map<String, Any?>?) = Unit

    override fun automationStatus(label: String, detail: String) = Unit

    override fun isCancelled(): Boolean = false
}
