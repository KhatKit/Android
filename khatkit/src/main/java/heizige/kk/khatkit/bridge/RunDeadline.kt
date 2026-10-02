package heizige.kk.khatkit.bridge

import java.util.concurrent.atomic.AtomicLong

/**
 * 本次卡片运行的硬超时。
 *
 * 挂在 [BridgeContext] 上（每轮运行一份，并发运行互不干扰）：
 * - 初始值来自 `BridgeRegistry.inject(deadlineAt = …)`，0 表示不限；
 * - 脚本调 `host.setTimeout(ms)` 时改写它；
 * - [expired] 在 `RustBridgeDispatcher.dispatch` 前判定，超时后所有 bridge 调用直接返回
 *   `{"__error":"卡片运行超时，已终止"}`（见设计文档 §2 要点 4）。
 */
class RunDeadline(at: Long = 0L) {

    private val holder = AtomicLong(at)

    /** 绝对截止时间（epoch 毫秒），0 = 不限。 */
    fun at(): Long = holder.get()

    /** 是否已到点。 */
    fun expired(): Boolean {
        val deadline = holder.get()
        return deadline > 0 && System.currentTimeMillis() >= deadline
    }

    /** 剩余毫秒；不限时返回 [Long.MAX_VALUE]。 */
    fun remainingMs(): Long {
        val deadline = holder.get()
        return if (deadline <= 0) Long.MAX_VALUE else deadline - System.currentTimeMillis()
    }

    /** 脚本侧设置：正数为从此刻起算的毫秒数，0 = 取消限制。 */
    fun set(ms: Int) {
        require(ms >= 0) { "超时毫秒数不能为负数" }
        holder.set(if (ms == 0) 0L else System.currentTimeMillis() + ms)
    }

    /** 供测试与诊断直接指定截止时间。 */
    fun setAt(epochMs: Long) {
        holder.set(epochMs)
    }
}