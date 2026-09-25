package heizige.kk.khatkit.dependency

/**
 * 构建时固定的依赖包签名公钥（信任锚）。
 *
 * 生成方式：服务端首次启动生成 P-256 密钥对（或读取 KHATKIT_SIGNING_KEY），
 * 从 `GET /api/dependencies/pubkey` 取 `publicKey`（X.509 DER base64）与 `fingerprintSha256`。
 *
 * 当前值来自 KServer 开发环境 `uploads/khatkit-keys/`（未配置 KHATKIT_SIGNING_KEY 时自动生成）：
 * 开发/联调可用；**生产发布必须改为生产服务端 `KHATKIT_SIGNING_KEY` 对应公钥与指纹后再发版**，
 * 并保证部署时服务端已配置同一私钥（否则 App 验签失败，依赖会被拒绝加载）。
 *
 * 更换发布密钥必须走 App 发版：把新的 publicKey / fingerprint 更新到这里，
 * 否则旧客户端会拒绝加载新密钥签名的依赖（这是有意为之）。
 */
object PinnedDependencyKey {
    /** ECDSA P-256 / SHA256withECDSA。 */
    const val ALGORITHM = "SHA256withECDSA"

    /** 固定公钥：X.509 SubjectPublicKeyInfo DER 的 base64（当前为 KServer 开发环境密钥）。 */
    const val PUBLIC_KEY_BASE64: String =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESimgL8Ev7vmqF44rnE4JA4pPpmmdpQm7WOtJU9tX8Go2kjjr55XsiX34MlMRz2yMf1HrLb0eplCum0whRwoABg=="

    /** 固定公钥 DER 的 sha256 指纹（hex 小写），必须与服务端 /api/dependencies/pubkey 一致。 */
    const val FINGERPRINT_SHA256: String =
        "d1e65fea02a0155f95cad87b98b92ce858e34a6cf7ccf5f39384d7da7197e75f"
}
