package heizige.kk.khatkit.dependency

import heizige.kk.khatkit.card.CardManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回滚编排与 HTTP 错误分类：同名多版本分组、410 撤销 / 404 下架可回退。
 */
class DependencyFallbackTest {

    private fun dep(version: String, sha: String = "a".repeat(64), signature: String = "c2ln") =
        CardManifest.DependencyReq(name = "imageToolbox", version = version, sha256 = sha, signature = signature)

    @Test
    fun groupsKeepManifestOrderAndAllowMultipleVersions() {
        val groups = DependencyFallback.groups(
            listOf(
                dep("1.2.0"),
                CardManifest.DependencyReq(name = "otherDep", version = "1.0.0", sha256 = "b".repeat(64), signature = "c2ln"),
                dep("1.1.0"),
            ),
        )
        assertEquals(listOf("imageToolbox", "otherDep"), groups.keys.toList())
        assertEquals(listOf("1.2.0", "1.1.0"), groups.getValue("imageToolbox").map { it.version })
        assertEquals(listOf("1.0.0"), groups.getValue("otherDep").map { it.version })
    }

    @Test
    fun http410RevokedIsRetryable() {
        val spec = DependencyFallback.classifyHttp(410)
        assertEquals("DEPENDENCY_REVOKED", spec.code)
        assertTrue("撤销后应尝试同名备用版本", spec.retryable)
    }

    @Test
    fun http404UnavailableIsRetryable() {
        val spec = DependencyFallback.classifyHttp(404)
        assertEquals("DEPENDENCY_VERSION_UNAVAILABLE", spec.code)
        assertTrue(spec.retryable)
    }

    @Test
    fun http5xxDownloadFailureIsRetryable() {
        val spec = DependencyFallback.classifyHttp(503)
        assertEquals("DEPENDENCY_DOWNLOAD_FAILED", spec.code)
        assertTrue(spec.retryable)
    }

    @Test
    fun signatureAndHashFailuresAreNeverRetryable() {
        // 安全红线：签名/哈希失败由 DependencyManager 直接返回 retryable=false
        val err = DependencyEnsureResult.Err("DEPENDENCY_SIGNATURE_INVALID", "签名校验失败")
        assertFalse(err.retryable)
        val hashErr = DependencyEnsureResult.Err("DEPENDENCY_HASH_MISMATCH", "sha256 不匹配")
        assertFalse(hashErr.retryable)
    }
}
