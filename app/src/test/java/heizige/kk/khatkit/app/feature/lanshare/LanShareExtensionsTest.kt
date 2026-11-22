package heizige.kk.khatkit.app.feature.lanshare

import heizige.kk.khatkit.app.feature.lanshare.helper.isLanShareAddress
import heizige.kk.khatkit.app.feature.lanshare.helper.lanAttachmentHeader
import heizige.kk.khatkit.app.feature.lanshare.helper.lanHtml
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareExtensionsTest {
    @Test fun sharedTextAndNamesAreEscapedInBrowser() {
        assertEquals("&lt;script&gt;&#39;&amp;&quot;&lt;/script&gt;", "<script>'&\"</script>".lanHtml())
    }

    @Test fun attachmentNamesCannotAddHeaders() {
        assertEquals("attachment; filename=\"download\"; filename*=UTF-8''hello%20%0d%0a%22.txt",
            "hello \r\n\".txt".lanAttachmentHeader())
    }

    @Test fun sharingBindsOnlyToPrivateLocalIpv4Addresses() {
        assertTrue(InetAddress.getByName("192.168.1.4").isLanShareAddress())
        assertTrue(InetAddress.getByName("172.16.10.4").isLanShareAddress())
        assertFalse(InetAddress.getByName("0.0.0.0").isLanShareAddress())
        assertFalse(InetAddress.getByName("127.0.0.1").isLanShareAddress())
        assertFalse(InetAddress.getByName("8.8.8.8").isLanShareAddress())
    }
}
