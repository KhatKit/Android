package heizige.kk.khatkit.uikit

import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.ui.UiRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * KhatKit UI 与宿主数据层之间的唯一接口。
 *
 * :khatkit-ui 不依赖 app；由 app 里的 KhatKitToolProvider 实现本接口，
 * 市场页 / 表单宿主 / 设置页只面向该接口，方便单独预览与复用。
 */
interface KhatKitController {

    val uiRequest: StateFlow<UiRequest?>
    val uiProgress: StateFlow<Pair<Float, String>?>

    fun submitForm(values: Map<String, Any?>?)
    fun answerConfirm(confirmed: Boolean)
    fun dismissUi()

    suspend fun searchCards(query: String, limit: Int = 20): List<CardIndexEntry>
    suspend fun installCard(entry: CardIndexEntry, force: Boolean = false): Boolean
    suspend fun uninstallCard(name: String): Boolean
    suspend fun installedCardVersions(): Map<String, String>

    fun listCardSecrets(cardName: String): List<String>
    fun removeCardSecret(cardName: String, key: String)

    var hubBaseUrl: String
    var enableRoot: Boolean
    var downloadConcurrency: Int
    var uiStyle: KhatKitUiStyle

    /** 是否接收 beta 更新通道（KodeHeadServer 的 KhatKit 独立更新通道）。 */
    var receiveBeta: Boolean

    fun applySettings()
}
