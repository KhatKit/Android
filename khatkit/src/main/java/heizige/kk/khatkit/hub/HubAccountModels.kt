package heizige.kk.khatkit.hub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * KhatKitHub 账户 / 套餐 / 卡片市场（v2 API）的数据模型。
 *
 * 对应后端接口：
 *  - POST /api/activate {token, device_id, device_name}
 *  - GET  /api/me（Bearer token）
 *  - POST /api/tools/authorize {card_name, price}
 *  - POST /api/tools/report
 *  - GET  /api/cards
 *  - POST /api/cards/publish
 *  - POST /api/cards/{name}/fork
 *  - POST /api/cards/{name}/vote
 *
 * 后端仍在迭代：响应解析刻意做宽容处理（字段名多别名、缺省即空），
 * 服务不可用时由调用方降级为本地可用，不阻塞免费卡片。
 */

@Serializable
data class HubActivateRequest(
    val token: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class HubToolAuthorizeRequest(
    @SerialName("card_name") val cardName: String,
    /** 与 Hub 一致：单位为分（price_per_call = 元 * 100） */
    @SerialName("price") val priceCents: Long = 0,
)

@Serializable
data class HubToolReportRequest(
    @SerialName("card_name") val cardName: String,
    val price: Double = 0.0,
    val currency: String = "CNY",
    val ok: Boolean = true,
    val trigger: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0,
    val message: String = "",
)

/** 发布请求：字段与 KhatKitHub /api/cards/publish 对齐（价格单位为分）。 */
@Serializable
data class HubCardPublishRequest(
    val name: String,
    val version: String,
    val description: String = "",
    val script: String = "",
    @SerialName("manifest_json") val manifestJson: String = "",
    @SerialName("price_per_call") val pricePerCall: Long = 0,
    val license: String = "",
    val changelog: String = "",
)

/** fork（提交改进）请求：新增 parent_version，其余与发布一致。 */
@Serializable
data class HubCardForkRequest(
    @SerialName("parent_version") val parentVersion: String = "",
    val version: String = "",
    val description: String = "",
    val script: String = "",
    @SerialName("manifest_json") val manifestJson: String = "",
    @SerialName("price_per_call") val pricePerCall: Long = 0,
    val license: String = "",
    val changelog: String = "",
)

/** 激活结果：token 为后续所有请求的 Bearer 令牌，session_key 供网关/服务端会话使用（可选）。 */
data class HubActivation(
    val ok: Boolean,
    val token: String = "",
    val sessionKey: String = "",
    val message: String = "",
    val account: HubAccountInfo = HubAccountInfo(),
)

/** GET /api/me 的账户数据（字段缺失为 null，UI 显示「未知」）。 */
data class HubAccountInfo(
    val active: Boolean = false,
    val plan: String = "",
    val aiTokensRemaining: Long? = null,
    val toolCallsRemaining: Long? = null,
    val expiresAt: Long? = null,
)

/** GET /api/me 的解析结果。 */
data class HubMeResult(
    val ok: Boolean,
    val info: HubAccountInfo = HubAccountInfo(),
    val message: String = "",
)

/** 工具调用授权结果：只有 [Denied] 才会阻止本地执行。 */
sealed class HubAuthorizeOutcome {
    /** 服务器放行（可能附带剩余次数）。 */
    data class Allowed(val remaining: Long? = null) : HubAuthorizeOutcome()

    /** 服务器明确拒绝：配额不足 / 令牌无效等。 */
    data class Denied(val message: String) : HubAuthorizeOutcome()

    /** 网络 / 服务端不可用：调用方按失败开放处理（本地照常运行）。 */
    data class Unavailable(val reason: String) : HubAuthorizeOutcome()
}

/** 发布 / fork / 投票等写操作结果。 */
data class HubActionResult(
    val ok: Boolean,
    val message: String = "",
)

/** 市场卡片详情（GET /api/cards）。价格与后端一致，单位为分。 */
data class HubCardMarketInfo(
    val name: String,
    val version: String = "",
    val author: String = "",
    val summary: String = "",
    val priceCents: Long = 0,
    val currency: String = "CNY",
    val license: String = "",
    val preferred: Boolean = false,
    val votes: Int = 0,
    val score: Long = 0,
)

/** GET /api/cards 的解析结果。 */
data class HubCardListResult(
    val ok: Boolean,
    val cards: List<HubCardMarketInfo> = emptyList(),
    val message: String = "",
)

/** 客户端聚合的套餐账户状态：本机存储 + /api/me 额度 + 设备信息。 */
data class HubAccountStatus(
    val activated: Boolean = false,
    val hubBaseUrl: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val plan: String = "",
    val aiTokensRemaining: Long? = null,
    val toolCallsRemaining: Long? = null,
    val expiresAt: Long? = null,
    val message: String = "",
)
