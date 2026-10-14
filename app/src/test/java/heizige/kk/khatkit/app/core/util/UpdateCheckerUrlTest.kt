package heizige.kk.khatkit.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `buildUpdateUrl` 的 JVM 单测：既钉死「与旧实现逐字等价」的老行为，
 * 也钉死新增的 `abi` / `applicationId` 上报规则（ABI 为空时不追加空参数）。
 */
class UpdateCheckerUrlTest {

    private val base = "https://heizige.top"

    /** 改动前的拼接方式，作为等价性参照。 */
    private fun legacyUrl(versionCode: String, channel: String): String =
        base.trim().trimEnd('/') +
            "/api/khatkit/app/update?currentVersionCode=" + versionCode +
            "&currentVersion=" + versionCode +
            "&channel=" + channel

    @Test
    fun `legacy request without abi and applicationId matches the old builder`() {
        val url = buildUpdateUrl(
            hubBaseUrl = base,
            versionCode = "190",
            channel = "stable",
            abi = "",
            applicationId = "",
        )
        assertEquals(legacyUrl("190", "stable"), url)
        assertFalse(url.contains("abi="))
        assertFalse(url.contains("applicationId="))
    }

    @Test
    fun `request reports abi and applicationId`() {
        val url = buildUpdateUrl(
            hubBaseUrl = base,
            versionCode = "190",
            channel = "beta",
            abi = "arm64-v8a",
            applicationId = "heizige.kk.khatkit.debug",
        )
        assertEquals(
            "https://heizige.top/api/khatkit/app/update" +
                "?currentVersionCode=190&currentVersion=190&channel=beta" +
                "&abi=arm64-v8a&applicationId=heizige.kk.khatkit.debug",
            url,
        )
    }

    @Test
    fun `hub base url trailing slashes are normalised the same way as before`() {
        val expected = legacyUrl("190", "stable")
        listOf(
            "https://heizige.top/",
            "https://heizige.top//",
            "  https://heizige.top  ",
            "https://heizige.top/   ",
        ).forEach { raw ->
            assertEquals(raw, expected, buildUpdateUrl(raw, "190", "stable", "", ""))
        }
    }

    @Test
    fun `blank abi does not append an empty abi parameter`() {
        listOf("", "   ").forEach { blank ->
            val url = buildUpdateUrl(base, "190", "stable", blank, "heizige.kk.khatkit.debug")
            assertFalse(url, url.contains("abi="))
            assertTrue(url, url.contains("&applicationId=heizige.kk.khatkit.debug"))
        }
    }

    @Test
    fun `blank applicationId does not append an empty applicationId parameter`() {
        listOf("", "   ").forEach { blank ->
            val url = buildUpdateUrl(base, "190", "stable", "arm64-v8a", blank)
            assertFalse(url, url.contains("applicationId="))
            assertTrue(url, url.contains("&abi=arm64-v8a"))
        }
    }

    @Test
    fun `version code stays in both version parameters and channel is preserved`() {
        val url = buildUpdateUrl(base, "205", "beta", "x86_64", "heizige.kk.khatkit")
        assertTrue(url.contains("currentVersionCode=205"))
        assertTrue(url.contains("currentVersion=205"))
        assertTrue(url.contains("channel=beta"))
        assertTrue(url.startsWith("https://heizige.top/api/khatkit/app/update?"))
    }

    @Test
    fun `abi and applicationId values are trimmed`() {
        val url = buildUpdateUrl(base, "190", "stable", " arm64-v8a ", " heizige.kk.khatkit.debug ")
        assertTrue(url, url.contains("&abi=arm64-v8a&"))
        assertTrue(url, url.endsWith("applicationId=heizige.kk.khatkit.debug"))
    }
}
