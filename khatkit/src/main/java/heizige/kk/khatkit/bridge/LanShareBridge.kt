package heizige.kk.khatkit.bridge

/**
 * 局域网共享（L0）：卡片查询 / 开启 / 关闭本机的局域网共享会话。
 *
 * 宿主侧接 App 内已有的 LanShareManager（同一个单例），三个方法都返回当前状态快照：
 * active: Boolean, url: String, directory: String, busy: Boolean, error: String。
 * 开启/关闭是异步的：调用后立刻返回的快照里 busy 可能仍是 true，卡片应提示稍后刷新。
 *
 * WASM 卡片用 dep:lanshare 能力调用（manifest.capabilities + card.json requires.bridges 都声明 lanshare）。
 */
interface LanShareBridge {
    fun status(): Map<String, Any?>

    fun start(): Map<String, Any?>

    fun stop(): Map<String, Any?>
}
