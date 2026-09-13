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

    /** 已安装卡片支持的触发方式（name -> triggers），市场页据此显示徽标与运行入口。 */
    suspend fun installedCardTriggers(): Map<String, List<String>>

    /** 用户手动运行已安装卡片；ok=false 时 message 为错误原因。 */
    suspend fun runCard(name: String): CardRunResult

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

/** 用户手动运行卡片的结果。 */
data class CardRunResult(
    val ok: Boolean,
    val message: String = "",
)
