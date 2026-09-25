package heizige.kk.khatkit.app.core.util

import android.os.Bundle

/**
 * 轻量分析抽象：KhatKit fork 已移除 Firebase，保留统一注入点便于以后接自建统计。
 */
interface AppAnalytics {
    fun logEvent(name: String, params: Bundle? = null)
}

object NoOpAnalytics : AppAnalytics {
    override fun logEvent(name: String, params: Bundle?) = Unit
}
