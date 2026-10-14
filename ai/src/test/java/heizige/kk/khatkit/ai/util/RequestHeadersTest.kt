package heizige.kk.khatkit.ai.util

import heizige.kk.khatkit.ai.provider.CustomHeader
import heizige.kk.khatkit.ai.provider.ProviderSetting
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义请求头（提供商「高级设置」）的**合并**与**序列化**语义。
 *
 * 移植自上游 2.5.6 的 `ai/src/test/java/me/rerere/ai/util/RequestHeadersTest.kt`，按本 fork
 * 的包名与 `common/http/okhttp/Headers` 实现适配；末尾补一条 Json 往返用例 —— 自定义请求头
 * 是序列化敏感的（`SettingsRepository` 落盘、`ShareSheet.decodeProviderSetting` 都走 Json），
 * 字段名/结构一变就会静默丢设置，光测合并测不出来。
 */
class RequestHeadersTest {
    @Test
    fun `mergeCustomHeaders should use provider headers when no request headers`() {
        val provider = ProviderSetting.OpenAI(
            customHeaders = listOf(
                CustomHeader("User-Agent", "provider-agent"),
                CustomHeader("X-Title", "provider-title"),
            )
        )

        val headers = provider.mergeCustomHeaders()

        assertEquals(listOf("provider-agent"), headers.values("User-Agent"))
        assertEquals(listOf("provider-title"), headers.values("X-Title"))
    }

    @Test
    fun `mergeCustomHeaders should let request headers override provider headers`() {
        val provider = ProviderSetting.OpenAI(
            customHeaders = listOf(
                CustomHeader("User-Agent", "provider-agent"),
                CustomHeader("X-Title", "provider-title"),
            )
        )

        // 同名判定必须忽略大小写：provider 写 `User-Agent`、请求侧给 `user-agent`
        val headers = provider.mergeCustomHeaders(
            listOf(CustomHeader("user-agent", "model-agent"))
        )

        assertEquals(listOf("model-agent"), headers.values("User-Agent"))
        assertEquals(listOf("provider-title"), headers.values("X-Title"))
    }

    @Test
    fun `mergeCustomHeaders should ignore blank header names`() {
        val provider = ProviderSetting.OpenAI(
            customHeaders = listOf(CustomHeader("", "value"))
        )

        assertEquals(0, provider.mergeCustomHeaders().size())
    }

    @Test
    fun `custom headers should survive json round-trip`() {
        val provider = ProviderSetting.OpenAI(
            customHeaders = listOf(
                CustomHeader("X-Title", "khatkit"),
                CustomHeader("HTTP-Referer", "https://example.invalid"),
            )
        )

        val json = Json.encodeToString(ProviderSetting.serializer(), provider)
        val decoded = Json.decodeFromString(ProviderSetting.serializer(), json)

        assertTrue(decoded is ProviderSetting.OpenAI)
        val decodedHeaders = (decoded as ProviderSetting.OpenAI).customHeaders
        assertEquals(provider.customHeaders, decodedHeaders)
        // 往返之后仍要能正常参与合并
        assertEquals(
            listOf("khatkit"),
            decoded.mergeCustomHeaders().values("x-title"),
        )
    }
}
