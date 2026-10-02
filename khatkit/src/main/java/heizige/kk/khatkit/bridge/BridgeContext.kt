package heizige.kk.khatkit.bridge

/**
 * 一次卡片运行的策略上下文，由 [BridgeRegistry.inject] 按 manifest 构造。
 *
 * 该类型只承载策略数据；具体宿主能力（例如审批 UI）由 app 模块注入。
 */
data class BridgeContext(
    val cardName: String,
    val engine: String,
    val quotaMb: Int,
    /** net 域白名单（小写 host，无端口）。空 = 禁止一切出网。 */
    val networkAllow: Set<String> = emptySet(),
    /** fs 可访问根目录（已 canonicalize）。空 = 只允许应用私有目录。 */
    val fsRoots: Set<String> = emptySet(),
    /** 方法级权限策略：key 形如 "fs.delete" / "net.get" / "ai.chat"。 */
    val permissions: Map<String, String> = emptyMap(),
    /** 同步审批闸门；宿主未提供时按拒绝处理。 */
    val approvalGate: ApprovalGate? = null,
    /** 运行硬超时（epoch ms）；0 = 不限。 */
    val deadlineAt: Long = 0L,
) {
    /**
     * 运行超时的可变句柄：初始值取 [deadlineAt]，脚本调 `host.setTimeout(ms)` 后改写它。
     * 每轮运行一份，所以并发运行互不干扰。
     */
    val deadline: RunDeadline = RunDeadline(deadlineAt)
}

/** 同步审批闸门。实现可以阻塞等待宿主的审批结果。 */
fun interface ApprovalGate {
    /** @return true 放行；false 表示用户拒绝或不可审批。 */
    fun request(title: String, detail: String, category: String): Boolean
}

/**
 * 能按卡片名绑定视图的 bridge（宿主实现提供 `bindCard`）。
 *
 * `BridgeRegistry.inject` 拿到 manifest 后据此给单例实现换一张绑定卡片名的视图，
 * 这样宿主侧实现不必自己知道「现在是哪张卡片」，也不用把实现类暴露给 `khatkit`。
 */
interface CardBindable {
    /** 返回绑定 [cardName] 的同类型实例。 */
    fun bindCard(cardName: String): Any
}
