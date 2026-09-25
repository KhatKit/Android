package heizige.kk.khatkit.dependency

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * 依赖包签名校验（纯 JVM，无 Android 依赖，便于单测）。
 *
 * 信任链：构建时固定 [PinnedDependencyKey]（公钥 + 指纹）→ 服务端用对应私钥对产物签名
 * → 客户端先校验 sha256，再用固定公钥验 `SHA256withECDSA` 签名。二者任一失败都拒绝加载。
 *
 * 说明：ECDSA 签名是分离签名（DER 编码，base64 传输），覆盖 jar 原始字节。
 */
object DependencySignature {

    /** 用固定公钥（或指定公钥）校验产物签名；任何解析/校验异常都视为失败。 */
    fun verify(
        artifact: ByteArray,
        signatureBase64: String,
        publicKeyDer: ByteArray = pinnedPublicKeyDer(),
    ): Boolean = runCatching {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyDer))
        val signature = Signature.getInstance(PinnedDependencyKey.ALGORITHM)
        signature.initVerify(publicKey)
        signature.update(artifact)
        signature.verify(Base64.getDecoder().decode(signatureBase64))
    }.getOrDefault(false)

    /** 固定公钥 DER；常量损坏时抛出（属于构建配置错误）。 */
    fun pinnedPublicKeyDer(): ByteArray =
        decodePublicKeyBase64(PinnedDependencyKey.PUBLIC_KEY_BASE64)

    /** 固定公钥指纹（hex 小写）。 */
    fun pinnedFingerprint(): String = fingerprintSha256(pinnedPublicKeyDer())

    fun decodePublicKeyBase64(publicKeyBase64: String): ByteArray =
        Base64.getDecoder().decode(publicKeyBase64.replace("\\s+".toRegex(), ""))

    fun fingerprintSha256(publicKeyDer: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(publicKeyDer).joinToString("") { "%02x".format(it) }

    fun fingerprintSha256(publicKeyBase64: String): String? =
        runCatching { fingerprintSha256(decodePublicKeyBase64(publicKeyBase64)) }.getOrNull()

    /** 服务端返回的公钥是否与固定值一致（用于在线核对，本地校验失败即拒绝加载）。 */
    fun matchesPinnedFingerprint(
        remoteFingerprintSha256: String,
        pinnedFingerprintSha256: String = PinnedDependencyKey.FINGERPRINT_SHA256,
    ): Boolean = remoteFingerprintSha256.trim()
        .equals(pinnedFingerprintSha256.trim(), ignoreCase = true)

    /** 签名是否是可解析的 base64（格式初筛，真正的验签由 [verify] 做）。 */
    fun isWellFormedBase64(signatureBase64: String): Boolean = runCatching {
        Base64.getDecoder().decode(signatureBase64.replace("\\s+".toRegex(), ""))
        true
    }.getOrDefault(false)
}
