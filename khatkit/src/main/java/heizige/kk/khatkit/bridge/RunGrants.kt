package heizige.kk.khatkit.bridge

import java.util.concurrent.ConcurrentHashMap

/**
 * 本次运行里被 manifest 显式放行 / 拒绝的方法（key 形如 `fs.delete`）。
 *
 * 为什么需要它：审批分散在各 bridge 实现里做（`fs.delete`、`ai.chat`、`store.embed*`…），
 * 于是「`permissions.methods` 声明了 `allow`」这件事没法统一生效——实现层照样弹审批框。
 * [RustBridgeDispatcher][heizige.kk.khatkit.engine.RustBridgeDispatcher] 在 dispatch 前
 * 读策略并把 `allow` 记在这里，实现层的闸门先看这里再决定要不要问用户。
 *
 * 每轮运行一份（挂在 [BridgeContext] 上），并发运行的卡片互不影响。
 */
class RunGrants {

    private val granted = ConcurrentHashMap.newKeySet<String>()
    private val denied = ConcurrentHashMap.newKeySet<String>()

    /** 策略层判定为 `allow`：实现层可以直接放行，不必再问用户。 */
    fun grant(key: String) {
        granted += key
    }

    /** 策略层判定为 `deny`：dispatch 已经返回错误，实现层不会再被调用。 */
    fun deny(key: String) {
        denied += key
    }

    fun isGranted(key: String): Boolean = key in granted

    fun isDenied(key: String): Boolean = key in denied
}