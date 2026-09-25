package heizige.kk.khatkit.dependency

import heizige.kk.khatkit.card.CardManifest

/**
 * 依赖回滚与错误分类（纯 JVM，便于单测）。
 *
 * 卡片清单 `requires.dependencies` 允许同一依赖名声明多个版本（回滚清单）：
 * 首选版本被 Hub 撤销（410）或下架（404）时，宿主按清单顺序尝试备用版本。
 * 每个候选版本的 sha256 + ECDSA 签名仍由 [DependencyManager] 强制校验，安全边界不变。
 */
object DependencyFallback {

    /** 同名依赖分组，保持清单声明顺序（第一项为首选版本）。 */
    fun groups(
        requirements: List<CardManifest.DependencyReq>,
    ): LinkedHashMap<String, List<CardManifest.DependencyReq>> {
        val groups = LinkedHashMap<String, MutableList<CardManifest.DependencyReq>>()
        requirements.forEach { req -> groups.getOrPut(req.name) { mutableListOf() }.add(req) }
        return LinkedHashMap(groups)
    }

    /** HTTP 状态 → 依赖错误码 + 是否可回退到备用版本。 */
    fun classifyHttp(status: Int): Spec = when (status) {
        410 -> Spec("DEPENDENCY_REVOKED", retryable = true)
        404 -> Spec("DEPENDENCY_VERSION_UNAVAILABLE", retryable = true)
        else -> Spec("DEPENDENCY_DOWNLOAD_FAILED", retryable = true)
    }

    data class Spec(val code: String, val retryable: Boolean)
}
