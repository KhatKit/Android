package heizige.kk.khatkit.app.feature.automation.data

import heizige.kk.khatkit.app.core.data.automation.ApprovalCategory

import heizige.kk.khatkit.app.core.data.automation.ApprovalPolicy

import android.content.Context

/**
 * 按类别的审批策略持久化（SharedPreferences，文件名与 key 保持既有落盘格式）。
 *
 * 唯一读写入口：看板侧经
 * [heizige.kk.khatkit.app.feature.automation.helper.AutomationManager.policyOf] 读取，
 * 设置页经 [heizige.kk.khatkit.app.feature.automation.helper.AutomationManager.setPolicy] 写入。
 */
class ApprovalPolicyRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取某类别的策略；未设置或值损坏时按 [ApprovalPolicy.ALWAYS_ASK]。 */
    fun policyOf(category: ApprovalCategory): ApprovalPolicy =
        ApprovalPolicy.fromId(
            runCatching { prefs.getString(KEY_PREFIX + category.id, null) }.getOrNull()
        ) ?: ApprovalPolicy.ALWAYS_ASK

    /** 写入某类别的策略，立即生效。 */
    fun setPolicy(category: ApprovalCategory, policy: ApprovalPolicy) {
        prefs.edit().putString(KEY_PREFIX + category.id, policy.id).apply()
    }

    companion object {
        /** 审批策略持久化文件名 / key 前缀（khatkit 自动化设置）。 */
        const val PREFS_NAME = "khatkit_automation"
        const val KEY_PREFIX = "approval_policy_"
    }
}
