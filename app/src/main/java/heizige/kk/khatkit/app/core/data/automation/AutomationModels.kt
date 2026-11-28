package heizige.kk.khatkit.app.core.data.automation

import androidx.annotation.StringRes
import heizige.kk.khatkit.app.R

/**
 * 授权操作类别：审批策略按类别持久化，10 分钟记住同样按类别生效。
 * [SHELL_ROOT] 覆盖 root / Shizuku 命令执行；[FILE_DELETE]、[APP_MANAGE] 和
 * [AI_INVOKE] 供卡片脚本内部的敏感能力使用。
 */
enum class ApprovalCategory(val id: String, @StringRes val labelRes: Int) {
    CARD_RUN("card_run", R.string.approval_category_card_run),
    UI_ACTION("ui_action", R.string.approval_category_ui_action),
    SHELL_ROOT("shell_root", R.string.approval_category_shell_root),
    FILE_DELETE("file_delete", R.string.approval_category_file_delete),
    APP_MANAGE("app_manage", R.string.approval_category_app_manage),
    AI_INVOKE("ai_invoke", R.string.approval_category_ai_invoke);

    companion object {
        fun fromId(id: String?): ApprovalCategory? = entries.firstOrNull { it.id == id }
    }
}

/** 单个类别的审批策略：默认每次询问。 */
enum class ApprovalPolicy(val id: String, @StringRes val labelRes: Int) {
    ALWAYS_ASK("always_ask", R.string.approval_policy_always_ask),
    ALLOW("allow", R.string.approval_policy_allow),
    DENY("deny", R.string.approval_policy_deny);

    companion object {
        fun fromId(id: String?): ApprovalPolicy? = entries.firstOrNull { it.id == id }
    }
}

/** 一次自动化运行的看板状态（[heizige.kk.khatkit.app.feature.automation.helper.AutomationManager] 发布）。 */
data class AutomationStatus(
    val label: String,
    val detail: String = "",
    val recent: List<String> = emptyList(),
    val cancelRequested: Boolean = false,
    /** 一次运行已结束，看板短暂展示完成提示后隐藏；下一次 update 会清除。 */
    val finished: Boolean = false,
    val activeSince: Long,
)

/** 一次待用户确认的自动化授权请求，由悬浮看板展示。 */
data class ApprovalRequest(
    val id: String,
    val title: String,
    val detail: String,
    /** 请求所属操作类别：审批策略与 10 分钟记住都按它生效。 */
    val category: ApprovalCategory,
    /** 请求创建时间，看板倒计时以它为起点，与 APPROVAL_TIMEOUT_MS 对齐。 */
    val requestedAt: Long = System.currentTimeMillis(),
)
