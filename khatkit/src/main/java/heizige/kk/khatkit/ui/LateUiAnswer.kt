package heizige.kk.khatkit.ui

import heizige.kk.khatkit.bridge.UiBridge

/** 用户迟到提交的是哪一类弹层回答；决定续跑时把值回放给脚本的哪个方法。 */
enum class UiAnswerKind {
    FORM,
    SCREEN,
    SHEET,
    CONFIRM,
}

/**
 * 用户超过等待上限之后才提交的回答。
 *
 * [values] 为 `null` 表示用户关掉了弹层（取消），宿主只补一条「交互取消」说明，不重跑卡片。
 */
data class LateUiAnswer(
    val kind: UiAnswerKind,
    val values: Map<String, Any?>?,
) {
    /** 取消类回答（用户关了弹层）。 */
    val cancelled: Boolean get() = values == null

    val confirmed: Boolean get() = values?.get("confirmed") as? Boolean ?: false
}

/**
 * 续跑时注入的「回放 UI」：下一次对应的阻塞 `ui.*` 调用直接拿到用户**之前**提交的值，
 * 而不是再弹一次弹层（也不再等 300 秒）。
 *
 * 为什么需要它：宿主的续跑是**重新执行脚本**，脚本会照原样再调用一次 `ui.form`；
 * 如果没有回放，HEADLESS 会返回 `nil`，用户辛苦填的值就白丢了。回放只对同类调用生效：
 * 用户提交的是表单，`ui.form` 拿到值，`ui.sheet` / `ui.confirm` 仍然返回空/`false`。
 * 其余方法（`show` / `progress` / `automationStatus`）保持 no-op，`isCancelled()` 恒 `false`。
 */
class ReplayUiBridge(private val answer: LateUiAnswer) : UiBridge {
    override fun sheet(
        title: String,
        actions: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? = answer.values.takeIf { answer.kind == UiAnswerKind.SHEET }

    override fun webSheet(
        title: String,
        url: String,
        actions: List<Map<String, Any?>>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? = answer.values.takeIf { answer.kind == UiAnswerKind.SHEET }

    override fun form(
        title: String,
        items: Any,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? = answer.values.takeIf { answer.kind == UiAnswerKind.FORM }

    override fun screen(
        title: String,
        root: Map<String, Any?>,
        options: Map<String, Any?>?,
    ): Map<String, Any?>? = answer.values.takeIf { answer.kind == UiAnswerKind.SCREEN }

    override fun confirm(title: String, message: String, danger: Boolean): Boolean =
        if (answer.kind == UiAnswerKind.CONFIRM) answer.confirmed else false

    override fun progress(ratio: Float, label: String) = Unit

    override fun show(card: Map<String, Any?>, options: Map<String, Any?>?) = Unit

    override fun automationStatus(label: String, detail: String) = Unit

    override fun isCancelled(): Boolean = false
}
