package heizige.kk.khatkit.uikit

import heizige.kk.khatkit.hub.CardIndexEntry
import heizige.kk.khatkit.hub.HubAccountStatus
import heizige.kk.khatkit.hub.HubActionResult
import heizige.kk.khatkit.hub.HubCardMarketInfo
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

    // ===== KhatKitHub 套餐 / 市场（后端未部署时全部优雅降级）=====

    /**
     * 当前套餐账户状态。[refresh] 为 true 时顺带请求 GET /api/me 拉取最新额度；
     * 未激活或网络失败时返回带中文 [HubAccountStatus.message] 的状态，不抛异常。
     */
    suspend fun hubAccountStatus(refresh: Boolean = false): HubAccountStatus =
        HubAccountStatus(message = "当前版本未接入 Hub 套餐")

    /** 用激活令牌激活套餐：POST /api/activate，成功后安全保存令牌并返回最新状态。 */
    suspend fun activateHub(token: String, hubBaseUrl: String): HubAccountStatus =
        HubAccountStatus(message = "当前版本不支持套餐激活")

    /** 清除本机保存的激活信息（不影响服务端账户）。 */
    suspend fun clearHubActivation(): HubAccountStatus = HubAccountStatus()

    /** 本机保存的网关令牌（激活令牌），用于创建「KhatKit 套餐网关」；未激活返回 null。 */
    fun hubToken(): String? = null

    /** 市场卡片详情（GET /api/cards：价格 / 协议 / 优选 / 票数），失败返回空表。 */
    suspend fun hubMarketCards(): List<HubCardMarketInfo> = emptyList()

    /** 发布已安装卡片到 Hub（需确认开源协议与每次调用价格）。 */
    suspend fun publishCard(
        name: String,
        license: String,
        price: Double,
        changelog: String = "",
    ): HubActionResult = HubActionResult(false, "当前版本不支持发布卡片")

    /** 提交改进：把本机卡片作为 fork 上传，带父版本、新版本号与更新说明。 */
    suspend fun forkCard(
        name: String,
        parentVersion: String,
        changelog: String,
        newVersion: String = "",
    ): HubActionResult = HubActionResult(false, "当前版本不支持提交改进")

    /** 给市场卡片投票（upvote）。 */
    suspend fun voteCard(name: String): HubActionResult = HubActionResult(false, "当前版本不支持投票")

    fun applySettings()
}

/** 用户手动运行卡片的结果。 */
data class CardRunResult(
    val ok: Boolean,
    val message: String = "",
)
