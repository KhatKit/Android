// SPDX-License-Identifier: AGPL-3.0-or-later
package heizige.kk.khatkit.yunx

import android.content.Context
import com.yunx.app.data.network.*
import com.yunx.app.data.network.model.*
import com.yunx.app.data.network.video.*
import com.yunx.app.data.repository.*
import com.yunx.app.util.AppStrings
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

/**
 * A per-card parser session. No downloader, account database, or upload API is exposed.
 * Share tokens and file tokens remain here; the card sees opaque session/file identifiers.
 */
class YunxParserManager(private val context: Context) {
    private val mutex = Mutex()
    private val sessions = linkedMapOf<String, Session>()
    private data class Session(
        val provider: String,
        val title: String,
        val credential: JSONObject,
        var cookie: String,
        val repo: ShareResolveRepository? = null,
        val share: ShareSession? = null,
        val ilanzou: ILanzouApi? = null,
        val uuid: String = "",
        val media: List<VideoMedia> = emptyList(),
        val notice: String = "",
        val born: Long = System.nanoTime(),
        val directories: MutableMap<String, List<ShareFile>> = mutableMapOf(),
        val files: MutableMap<String, ShareFile> = mutableMapOf(),
    )

    init {
        AppStrings.install(context)
        Pan123DeviceId.install(context)
        XunleiDeviceFingerprint.init(context)
    }

    /** Blocking only on the card worker, never called from Compose/main. */
    fun call(operation: String, input: String): String = runBlocking(Dispatchers.IO) {
        mutex.withLock {
            try {
                withTimeout(90_000) {
                    val p = JSONObject(input)
                    when (operation) {
                        "detect" -> json("provider" to detect(p.getString("text")))
                        "open" -> open(p)
                        "list" -> list(p)
                        "resolve" -> resolve(p)
                        "close" -> { sessions.remove(p.optString("session")); json("ok" to true) }
                        else -> error("未知解析操作")
                    }.toString()
                }
            } catch (e: CancellationException) {
                json("error" to "解析已取消或超时，请重试").toString()
            } catch (e: Exception) {
                // Never propagate upstream HTTP requests, credentials, or signed URLs into UI/logs.
                val message = if (e is VideoParseException) e.userMessage() else e.message.orEmpty()
                json("error" to message.takeIf {
                    it.length in 1..240 && !it.contains("http") &&
                        !Regex("cookie|token|authorization", RegexOption.IGNORE_CASE).containsMatchIn(it)
                }.orEmpty().ifBlank { "解析失败，请检查链接、提取码和账号登录状态后重试" }).toString()
            }
        }
    }

    private fun detect(text: String): String {
        val url = Regex("https?://[^\\s]+").find(text)?.value
            ?.trimEnd('。', '，', ',', '；', ';', ')', ']', '}', '"', '\'')
            ?: error("请粘贴完整的分享链接")
        val uri = URI(url)
        require(uri.userInfo == null && uri.port in listOf(-1, 80, 443)) { "分享地址不合法" }
        val host = uri.host?.lowercase().orEmpty()
        val provider = VideoLinkParser.parse(text)?.platform?.name?.lowercase()
            ?: ShareLinkParser.parse(text)?.platform?.name?.lowercase()
            ?: if (GitHubLinkParser.parse(text) != null) "github" else error("未识别到 YunX 支持的分享链接")
        val domains = when (provider) {
            "quark" -> listOf("quark.cn")
            "uc" -> listOf("uc.cn")
            "baidu" -> listOf("baidu.com")
            "xunlei" -> listOf("xunlei.com")
            "c139" -> listOf("139.com")
            "pan123" -> listOf("123pan.com", "123pan.cn", "123865.com")
            "pan115" -> listOf("115.com", "115cdn.com", "115rc.com")
            "guangya" -> listOf("guangyapan.com")
            "ilanzou" -> listOf("ilanzou.com", "ilanzou.net", "ilanzou.org", "ilanzou.cn")
            "lanzou" -> listOf(host.takeIf {
                Regex("(^|.*\\.)(lanzou[a-z0-9-]*|lan[zs]o[ux]|lanzn)\\.(com|net|org|cn)").matches(it)
            }.orEmpty())
            "douyin" -> listOf("douyin.com", "iesdouyin.com")
            "xhs" -> listOf("xiaohongshu.com", "xhslink.com", "xhslink.cn")
            "kuaishou" -> listOf("kuaishou.com", "gifshow.com")
            "weibo" -> listOf("weibo.com", "weibo.cn")
            "acfun" -> listOf("acfun.cn")
            "bilibili" -> listOf("bilibili.com", "b23.tv")
            "pixiv" -> listOf("pixiv.net")
            "github" -> listOf("github.com", "raw.githubusercontent.com", "codeload.github.com")
            else -> emptyList()
        }
        require(domains.any { it.isNotBlank() && (host == it || host.endsWith(".$it")) }) {
            "分享链接域名与平台不符"
        }
        return provider
    }

    private suspend fun open(p: JSONObject): JSONObject {
        val text = p.getString("text")
        val provider = detect(text)
        val raw = p.optString("credential")
        val credential = if (raw.trimStart().startsWith("{")) JSONObject(raw) else json("value" to raw)
        val cookie = credential.optString("value").ifBlank { credential.optString("access_token") }
        val video = VideoLinkParser.parse(text)
        val s = when {
            provider == "github" -> openGitHub(text, cookie, credential)
            video != null -> {
                var resolved: DetectedVideoLink = video
                if (resolved.isShortLink) {
                    for (i in 0 until 5) {
                        val next = VideoHttp.expandRedirect(resolved.url, mapOf("User-Agent" to
                            if (resolved.platform in listOf(VideoPlatform.DOUYIN, VideoPlatform.KUAISHOU))
                                VideoHttp.UA_IPHONE else VideoHttp.UA_CHROME)) ?: break
                        require(URI(next).scheme in listOf("http", "https")) { "短链返回了无效地址" }
                        require(detect(next) == provider) { "短链跳转到了其他平台" }
                        val identified = VideoLinkParser.parse(next)
                        if (identified == null) { resolved = resolved.copy(url = next); break }
                        require(identified.platform == video.platform) { "短链跳转到了其他平台" }
                        detect(next)
                        resolved = identified
                        if (!resolved.isShortLink) break
                    }
                }
                val result = requireNotNull(VideoParsers.of(resolved.platform)).parse(resolved, cookie)
                Session(provider, result.title, credential, cookie,
                    media = result.medias.map { it.copy(url = VideoHttp.secureUrl(it.url), audioUrl = VideoHttp.secureUrl(it.audioUrl)) },
                    notice = result.notice)
            }
            provider == "ilanzou" -> {
                val api = ILanzouApi()
                val uuid = credential.optString("uuid").ifBlank { api.getUuid() }
                val parsed = requireNotNull(ShareLinkParser.parse(text))
                Session(provider, "蓝奏优享分享", credential, cookie,
                    share = ShareSession(parsed.shareId, parsed.shareId, "蓝奏优享分享"), ilanzou = api, uuid = uuid)
            }
            else -> {
                val quark = QuarkApi()
                val uc = UCApi()
                val repo: ShareResolveRepository = when (provider) {
                    "quark" -> QuarkResolveRepository(quark)
                    "uc" -> UCResolveRepository(uc)
                    "baidu" -> BaiduResolveRepository(BaiduApi())
                    "c139" -> C139ResolveRepository(C139Api(deviceInfoProvider = { C139DeviceInfo.from(context) }))
                    "pan123" -> Pan123ResolveRepository(Pan123Api()) { cookie.ifBlank { null } }
                    "pan115" -> Pan115ResolveRepository(Pan115Api())
                    "guangya" -> GuangYaResolveRepository(GuangYaApi()) { cookie.ifBlank { null } }
                    "lanzou" -> LanzouResolveRepository(LanzouApi())
                    "xunlei" -> XunleiResolveRepository(
                        XunleiApi(), { cookie.ifBlank { null } },
                        { credential.optString("device_id").ifBlank { null } },
                        { credential.optString("captcha_token") },
                    )
                    else -> error("不支持的网盘")
                }
                val share = repo.createSession(text, p.optString("password").ifBlank { null }, cookie).getOrThrow()
                Session(provider, share.title, credential, cookie, repo, share).also { session ->
                    quark.cookieSink = { merged, _ -> session.cookie = merged }
                    uc.cookieSink = { merged, _ -> session.cookie = merged }
                }
            }
        }
        sessions.entries.removeAll { System.nanoTime() - it.value.born > 30L * 60 * 1_000_000_000 }
        while (sessions.size >= 8) sessions.remove(sessions.keys.first())
        val id = UUID.randomUUID().toString()
        sessions[id] = s
        return json("session" to id, "provider" to provider, "title" to s.title, "notice" to s.notice,
            "root" to if (provider in listOf("baidu", "guangya")) "" else "0")
    }

    private fun session(p: JSONObject): Session = sessions[p.getString("session")]?.takeIf {
        System.nanoTime() - it.born <= 30L * 60 * 1_000_000_000
    } ?: error("解析会话已过期，请重新解析分享")

    private suspend fun list(p: JSONObject): JSONObject {
        val s = session(p)
        val directory = p.optString("directory", "0").let {
            if (it == "0" && s.provider in listOf("baidu", "guangya")) "" else it
        }
        val page = p.optInt("page", 1).coerceIn(1, 10000)
        val files = if (s.repo == null && s.ilanzou == null) {
            s.media.mapIndexed { i, m -> json("id" to i.toString(), "name" to m.fileName.ifBlank { "${s.title}-${i + 1}" },
                "size" to m.size, "directory" to false, "quality" to m.quality, "hls" to m.isHls,
                "split_audio" to m.audioUrl.isNotBlank()) }
        } else {
            if (p.optBoolean("refresh")) s.directories.remove(directory)
            val items = s.directories[directory] ?: (
                s.ilanzou?.shareList(s.share!!.shareId,
                    directory.removePrefix("d:").takeUnless { it.isBlank() || it == "0" }, s.uuid)
                    ?: s.repo!!.listFiles(s.share!!, directory, s.cookie).getOrThrow()
                ).also { fetched ->
                    require(fetched.size <= 10000 && s.files.size + fetched.size <= 30000) { "目录过大，请分开解析子目录" }
                    s.directories[directory] = fetched
                    fetched.forEach { s.files[it.fid] = it }
                }
            items.map { json("id" to it.fid, "name" to it.fname, "size" to it.fsize, "directory" to it.isdir) }
        }
        val offset = (page - 1) * 20
        return json("files" to JSONArray(files.drop(offset).take(20)), "has_more" to (offset + 20 < files.size),
            "total" to files.size)
    }

    private suspend fun resolve(p: JSONObject): JSONObject {
        val s = session(p)
        if (s.repo == null && s.ilanzou == null) {
            val m = s.media.getOrNull(p.getString("file_id").toInt()) ?: error("文件不在解析结果中")
            return json("url" to m.url, "name" to m.fileName, "headers" to JSONObject(m.headers),
                "hls" to m.isHls, "audio_url" to m.audioUrl, "audio_headers" to JSONObject(m.audioHeaders),
                "motion_url" to m.motionUrl)
        }
        val file = s.files[p.getString("file_id")] ?: error("请先打开文件所在目录")
        require(!file.isdir) { "请进入文件夹后选择文件" }
        val transfer = s.provider in listOf("baidu", "xunlei", "pan115", "guangya") ||
            (s.provider == "quark" && s.cookie.isNotBlank())
        require(!transfer || p.optBoolean("allow_transfer")) { "此平台可能需要临时转存，请先确认" }
        val link = if (s.ilanzou != null) {
            s.ilanzou.shareDownload(s.share!!.shareId, file.fid.removePrefix("f:"),
                s.credential.optString("user_id"), s.uuid)
        } else if (s.cookie.isBlank() && s.provider in listOf("quark", "uc", "lanzou")) {
            s.repo!!.getGuestShareDownloadLink(s.share!!, file).getOrThrow()
        } else s.repo!!.getShareDownloadLink(s.share!!, file, s.cookie).getOrThrow()
        val headers = downloadHeaders(s.provider, s.cookie, link)
        return json("url" to link.downloadUrl, "name" to file.fname, "headers" to JSONObject(headers),
            "hls" to link.isHls, "notice" to if (link.cleanupDirFid != null)
                "已创建临时转存目录。下载完成后可在网盘中清理 YunX 临时文件。" else "")
    }

    private suspend fun openGitHub(text: String, token: String, credentials: JSONObject): Session {
        val api = GitHubApi(tokenProvider = { token.ifBlank { null } })
        val media = mutableListOf<VideoMedia>()
        val link = GitHubLinkParser.parse(text) ?: error("GitHub 链接无法识别")
        val title = when (link) {
            is GitHubLinkType.DirectFile -> {
                media += VideoMedia(link.url, VideoMediaKind.IMAGE, link.fileName)
                link.fileName
            }
            is GitHubLinkType.Repository -> {
                val repo = api.getRepo(link.owner, link.repo) ?: error("无法读取仓库")
                media += VideoMedia("https://github.com/${repo.fullName}/archive/refs/heads/${repo.defaultBranch}.zip",
                    VideoMediaKind.IMAGE, "${repo.name}-${repo.defaultBranch.replace('/', '-')}.zip", quality = "源码")
                for (page in 1..10) {
                    val releases = api.getReleases(link.owner, link.repo, page) ?: error("无法读取 Releases")
                    releases.forEach { release -> release.assets.forEach {
                        media += VideoMedia(it.downloadUrl, VideoMediaKind.IMAGE, it.name, it.size, quality = release.tagName)
                    } }
                    if (releases.size < 30) break
                }
                repo.fullName
            }
            is GitHubLinkType.Account -> {
                val org = api.getUserType(link.owner) == "Organization"
                for (page in 1..10) {
                    val repos = (if (org) api.getOrgRepos(link.owner, page) else api.getUserRepos(link.owner, page))
                        ?: error("无法读取账号仓库")
                    repos.forEach { repo -> media += VideoMedia(
                        "https://github.com/${repo.fullName}/archive/refs/heads/${repo.defaultBranch}.zip",
                        VideoMediaKind.IMAGE, "${repo.name}.zip", quality = repo.fullName)
                    }
                    if (repos.size < 30) break
                }
                link.owner
            }
        }
        return Session("github", title, credentials, token, media = media,
            notice = "GitHub：下载公开仓库源码包和 Release 附件")
    }

    companion object {
        private fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply {
            pairs.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
        }

        internal fun downloadHeaders(provider: String, cookie: String, link: DownloadLink): Map<String, String> {
            val guest = link.guestCookie
            return when (provider) {
                "xunlei" -> mapOf("User-Agent" to XunleiConstants.APP_UA)
                "baidu" -> mapOf("Cookie" to cookie, "User-Agent" to BaiduConstants.UA_NETDISK)
                "c139" -> mapOf("User-Agent" to C139Constants.PC_UA)
                "pan115" -> mapOf("Cookie" to Pan115Constants.mergeCookies(cookie, guest),
                    "User-Agent" to Pan115Constants.CLIENT_UA, "Referer" to Pan115Constants.DOWNLOAD_REFERER)
                "pan123" -> mapOf("User-Agent" to Pan123Constants.WEB_UA, "Referer" to Pan123Constants.DOWNLOAD_REFERER)
                "guangya" -> mapOf("User-Agent" to GuangYaConstants.WEB_UA, "Referer" to GuangYaConstants.DOWNLOAD_REFERER)
                "ilanzou" -> mapOf("User-Agent" to ILanzouConstants.WEB_UA, "Referer" to ILanzouConstants.DOWNLOAD_REFERER,
                    "Origin" to ILanzouConstants.WEB_SITE)
                "lanzou" -> mapOf("User-Agent" to LanzouConstants.WEB_UA, "Cookie" to LanzouConstants.DOWN_IP_COOKIE,
                    "Referer" to URI(link.downloadUrl).let { "${it.scheme}://${it.host}/" })
                "uc" -> buildMap {
                    put("Cookie", guest.ifBlank { cookie })
                    put("User-Agent", if (guest.isBlank()) UCConstants.USER_AGENT else UCConstants.GUEST_UA)
                    put("Referer", UCConstants.DOWNLOAD_REFERER)
                    put("Origin", UCConstants.WEB_ORIGIN)
                    if (guest.isNotBlank()) put("Sec-Ch-Ua", UCConstants.GUEST_SEC_CH_UA)
                }
                else -> mapOf("Cookie" to guest.ifBlank { cookie }, "User-Agent" to QuarkConstants.API_USER_AGENT,
                    "Referer" to QuarkConstants.DOWNLOAD_REFERER)
            }
        }
    }
}
