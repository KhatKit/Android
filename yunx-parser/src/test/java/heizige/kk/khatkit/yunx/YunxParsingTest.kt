package heizige.kk.khatkit.yunx

import com.yunx.app.data.network.*
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.video.VideoLinkParser
import org.junit.Assert.*
import org.junit.Test

class YunxParsingTest {
    @Test fun identifiesEveryCloudProviderFromRealLinkShapes() {
        val links = mapOf(
            "https://pan.quark.cn/s/abc123?pwd=AB12" to SharePlatform.QUARK,
            "https://drive.uc.cn/s/abc123" to SharePlatform.UC,
            "https://pan.xunlei.com/s/VOabc123" to SharePlatform.XUNLEI,
            "https://pan.baidu.com/s/1abc123" to SharePlatform.BAIDU,
            "https://yun.139.com/shareweb/#/w/i/abc123" to SharePlatform.C139,
            "https://www.123pan.com/s/abc123-ABC123" to SharePlatform.PAN123,
            "https://115.com/s/swabcdef1234?password=1234" to SharePlatform.PAN115,
            "https://www.guangyapan.com/s/abc123" to SharePlatform.GUANGYA,
            "https://wwx.lanzoui.com/iAbCd123" to SharePlatform.LANZOU,
            "https://www.ilanzou.com/s/abc123" to SharePlatform.ILANZOU,
        )
        links.forEach { (url, provider) -> assertEquals(url, provider, ShareLinkParser.parse(url)?.platform) }
    }

    @Test fun identifiesEveryVideoProviderAndGithub() {
        val links = mapOf(
            "https://v.douyin.com/abc123/" to "DOUYIN",
            "https://www.xiaohongshu.com/explore/6721aabbccddeeff00112233" to "XHS",
            "https://v.kuaishou.com/abc123" to "KUAISHOU",
            "https://weibo.com/1234567890/AbCdEfGhi" to "WEIBO",
            "https://www.acfun.cn/v/ac12345678" to "ACFUN",
            "https://www.bilibili.com/video/BV1xx411c7mD" to "BILIBILI",
            "https://www.pixiv.net/artworks/12345678" to "PIXIV",
        )
        links.forEach { (url, provider) -> assertEquals(url, provider, VideoLinkParser.parse(url)?.platform?.name) }
        assertNotNull(GitHubLinkParser.parse("https://github.com/CYQawa/YunX"))
    }

    @Test fun signedCdnHeadersNeverContainAccountTokens() {
        val link = DownloadLink("id", "file", "https://cdn.example.org/file", 10)
        for (id in listOf("xunlei", "c139", "pan123", "guangya", "ilanzou")) {
            val headers = YunxParserManager.downloadHeaders(id, "ACCOUNT_SECRET", link)
            assertFalse(id, headers.values.any { it.contains("ACCOUNT_SECRET") })
            assertNotNull(headers["User-Agent"])
        }
    }

    @Test fun guestCookieAnd115CdnCookieArePreserved() {
        val link = DownloadLink("id", "file", "https://cdn.example.org/file", 10, guestCookie = "__pugs=GUEST")
        for (id in listOf("quark", "uc")) {
            val headers = YunxParserManager.downloadHeaders(id, "account=PRIVATE", link)
            assertEquals("__pugs=GUEST", headers["Cookie"])
            assertNotNull(headers["Referer"])
        }
        val headers = YunxParserManager.downloadHeaders("pan115", "UID=ACCOUNT", link.copy(guestCookie = "acw_tc=CDN"))
        assertTrue(headers["Cookie"]!!.contains("UID=ACCOUNT"))
        assertTrue(headers["Cookie"]!!.contains("acw_tc=CDN"))
    }
}
