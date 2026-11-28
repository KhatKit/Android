package heizige.kk.khatkit.app.feature.lanshare

import heizige.kk.khatkit.bridge.LanShareBridge

/** 把 App 内的 [LanShareManager] 单例暴露成卡片的 lanshare bridge（WASM 卡用 dep:lanshare 调用）。 */
internal class LanShareCardBridge(private val manager: LanShareManager) : LanShareBridge {

    override fun status(): Map<String, Any?> = snapshot()

    override fun start(): Map<String, Any?> {
        manager.start()
        return snapshot()
    }

    override fun stop(): Map<String, Any?> {
        manager.stop()
        return snapshot()
    }

    private fun snapshot(): Map<String, Any?> {
        val state = manager.state.value
        return mapOf(
            "active" to (state.url != null),
            "url" to (state.url ?: ""),
            "directory" to (state.directory?.name ?: ""),
            "busy" to state.isBusy,
            "error" to (state.error ?: ""),
        )
    }
}

/** 卡片运行时通过 Hilt EntryPoint 取同一个 LanShareManager 单例。 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal interface LanShareCardEntryPoint {
    fun lanShareManager(): LanShareManager
}
