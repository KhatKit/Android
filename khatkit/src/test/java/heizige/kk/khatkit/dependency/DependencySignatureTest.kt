package heizige.kk.khatkit.dependency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * 依赖包签名校验（ECDSA P-256 / SHA256withECDSA）：happy path、篡改产物、篡改签名、错误公钥。
 * 纯 JVM，不依赖 Android。
 */
class DependencySignatureTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(bytes: ByteArray): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(bytes)
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    @Test
    fun happyPathVerifies() {
        val artifact = "image-toolbox-dependency-jar".toByteArray(Charsets.UTF_8)
        assertTrue(DependencySignature.verify(artifact, sign(artifact), keyPair.public.encoded))
    }

    @Test
    fun tamperedArtifactFails() {
        val artifact = "artifact".toByteArray(Charsets.UTF_8)
        val signature = sign(artifact)
        val tampered = artifact.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertFalse(DependencySignature.verify(tampered, signature, keyPair.public.encoded))
    }

    @Test
    fun tamperedSignatureFails() {
        val artifact = "artifact".toByteArray(Charsets.UTF_8)
        val der = Base64.getDecoder().decode(sign(artifact))
        der[der.size / 2] = (der[der.size / 2] + 1).toByte()
        val tampered = Base64.getEncoder().encodeToString(der)
        assertFalse(DependencySignature.verify(artifact, tampered, keyPair.public.encoded))
    }

    @Test
    fun wrongKeyFails() {
        val other = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val artifact = "artifact".toByteArray(Charsets.UTF_8)
        assertFalse(DependencySignature.verify(artifact, sign(artifact), other.public.encoded))
    }

    @Test
    fun malformedInputsFailWithoutThrowing() {
        val artifact = "artifact".toByteArray(Charsets.UTF_8)
        assertFalse(DependencySignature.verify(artifact, "not-base64!!", keyPair.public.encoded))
        assertFalse(DependencySignature.verify(artifact, "", keyPair.public.encoded))
        assertFalse(DependencySignature.verify(artifact, sign(artifact), byteArrayOf(1, 2, 3)))
        assertFalse(DependencySignature.isWellFormedBase64("!!!"))
        assertTrue(DependencySignature.isWellFormedBase64(sign(artifact)))
    }

    @Test
    fun fingerprintMismatchFails() {
        assertFalse(
            "服务端返回的指纹与固定值不一致必须拒绝",
            DependencySignature.matchesPinnedFingerprint("f".repeat(64)),
        )
        assertFalse("空指纹必须拒绝", DependencySignature.matchesPinnedFingerprint(""))
        assertTrue(DependencySignature.matchesPinnedFingerprint(PinnedDependencyKey.FINGERPRINT_SHA256))
        assertTrue(DependencySignature.matchesPinnedFingerprint(PinnedDependencyKey.FINGERPRINT_SHA256.uppercase()))
        // 构造注入的自定义固定指纹：必须按传入值比较，而不是永远比内置常量
        val custom = "ab".repeat(32)
        assertTrue(DependencySignature.matchesPinnedFingerprint(custom.uppercase(), custom))
        assertFalse(DependencySignature.matchesPinnedFingerprint(PinnedDependencyKey.FINGERPRINT_SHA256, custom))
    }

    @Test
    fun pinnedKeyIsValidAndFingerprintMatchesServerContract() {
        val pinnedDer = DependencySignature.pinnedPublicKeyDer()
        assertTrue("固定公钥必须是合法 X.509 EC 公钥", pinnedDer.isNotEmpty())
        assertEquals(PinnedDependencyKey.FINGERPRINT_SHA256, DependencySignature.pinnedFingerprint())
        assertEquals(PinnedDependencyKey.FINGERPRINT_SHA256, DependencySignature.fingerprintSha256(pinnedDer))
        assertTrue(DependencySignature.matchesPinnedFingerprint(PinnedDependencyKey.FINGERPRINT_SHA256.uppercase()))
        assertFalse(DependencySignature.matchesPinnedFingerprint("f".repeat(64)))
    }

    @Test
    fun randomKeySignatureIsRejectedByPinnedPublicKey() {
        val artifact = "pinned-key-self-check".toByteArray(Charsets.UTF_8)
        assertFalse(
            "非固定密钥签出的产物必须被固定公钥拒绝",
            DependencySignature.verify(artifact, sign(artifact), DependencySignature.pinnedPublicKeyDer()),
        )
    }
}
