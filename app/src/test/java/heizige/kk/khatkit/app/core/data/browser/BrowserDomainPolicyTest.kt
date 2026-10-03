package heizige.kk.khatkit.app.core.data.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserDomainPolicyTest {
    @Test
    fun `settings normalize domains and reject URL syntax`() {
        assertEquals(setOf("example.com", "xn--fsqu00a.xn--0zwm56d"),
            BrowserDomainPolicy.parse(" EXAMPLE.COM.\n例子.测试，example.com"))
        for (invalid in listOf("https://example.com", "*.example.com", "example.com:443", "a..test", "user@host", "-a.test")) {
            assertThrows(invalid, IllegalArgumentException::class.java) { BrowserDomainPolicy.parse(invalid) }
        }
    }

    @Test
    fun `host matching resists suffix userinfo and scheme confusion`() {
        val policy = BrowserDomainPolicy(setOf("example.com"))
        listOf("https://example.com", "https://sub.example.com:8443/path", "https://EXAMPLE.COM./", "about:blank")
            .forEach(policy::check)
        listOf("https://example.com.evil.test", "https://example.com@evil.test",
            "https://notexample.com", "file:///example.com", "javascript:alert(1)").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { policy.check(it) }
        }
        BrowserDomainPolicy(emptySet()).check("about:blank")
        assertThrows(IllegalArgumentException::class.java) { BrowserDomainPolicy(emptySet()).check("https://example.com") }
    }

}
