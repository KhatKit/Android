package heizige.kk.khatkit.app.core.util

import android.app.DownloadManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request

/**
 * 应用更新检查：走 KodeHeadServer 的 KhatKit 独立更新通道
 * （`GET /api/khatkit/app/update`），与 KodeHead/Kmd 同一套逻辑：
 * 版本号为 Android versionCode，channel = stable | beta，force_min 强制更新。
 */
class UpdateChecker(
    private val client: OkHttpClient,
    appScope: AppScope,
    private val controller: KhatKitToolProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val updateState: StateFlow<UiState<UpdateCheckResponse>> = checkUpdate().stateIn(
        scope = appScope,
        started = SharingStarted.Lazily,
        initialValue = UiState.Loading,
    )

    private fun checkUpdate(): Flow<UiState<UpdateCheckResponse>> = flow {
        emit(UiState.Loading)
        emit(
            UiState.Success(
                data = fetchUpdateInfo()
            )
        )
    }.catch {
        emit(UiState.Error(it))
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchUpdateInfo(): UpdateCheckResponse {
        val currentVersionCode = BuildConfig.VERSION_CODE.toString()
        val channel = if (controller.receiveBeta) "beta" else "stable"
        val url = buildUpdateUrl(
            hubBaseUrl = controller.hubBaseUrl,
            versionCode = currentVersionCode,
            channel = channel,
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            applicationId = BuildConfig.APPLICATION_ID,
        )
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .get()
                .addHeader(
                    "User-Agent",
                    "KhatKit ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}"
                )
                .build()
        ).await()
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch update info: ${response.code}")
        }
        return json.decodeFromString<UpdateCheckResponse>(response.body.string())
    }

    fun downloadUpdate(context: Context, info: UpdateCheckResponse) {
        runCatching {
            val url = resolveUrl(info.downloadUrl)
            val fileName = "KhatKit-${info.latestVersion}.apk"
            // 统一进 KhatKit 下载中心（可暂停/续传/查看进度）
            controller.enqueueDownloadAsync(url, fileName)
        }.onFailure {
            Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
        }
    }

    /** 服务端可能下发相对路径（/media/...），拼到 hub 地址上。 */
    private fun resolveUrl(raw: String): String = when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        else -> controller.hubBaseUrl.trim().trimEnd('/') + "/" + raw.trimStart('/')
    }
}

/**
 * 拼接更新检查 URL（纯函数，便于 JVM 单测，见 `UpdateCheckerUrlTest`）。
 *
 * 与历史实现逐字兼容的部分：前缀 = `hubBaseUrl.trim().trimEnd('/')`，`currentVersionCode` 与
 * `currentVersion` 都取客户端 versionCode，`channel` = `stable` | `beta`。
 *
 * 新增两个可选查询参数（服务端据此按 ABI 选产物、并拒绝跨包名下发）：
 * - [abi]：本机首选 ABI（`Build.SUPPORTED_ABIS.firstOrNull()`）。**为空字符串时不追加 `abi=` 参数**。
 * - [applicationId]：本机包名（`BuildConfig.APPLICATION_ID`，debug 包带 `.debug` 后缀）。
 *   为空字符串时不追加 `applicationId=` 参数。
 */
internal fun buildUpdateUrl(
    hubBaseUrl: String,
    versionCode: String,
    channel: String,
    abi: String,
    applicationId: String,
): String = buildString {
    append(hubBaseUrl.trim().trimEnd('/'))
    append("/api/khatkit/app/update?currentVersionCode=")
    append(versionCode)
    append("&currentVersion=")
    append(versionCode)
    append("&channel=")
    append(channel)
    if (abi.isNotBlank()) {
        append("&abi=")
        append(abi.trim())
    }
    if (applicationId.isNotBlank()) {
        append("&applicationId=")
        append(applicationId.trim())
    }
}

@Serializable
data class UpdateCheckResponse(
    val hasUpdate: Boolean = false,
    val forceUpdate: Boolean = false,
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val description: String = "",
)

/**
 * 版本号值类，封装版本号字符串并提供比较功能
 *
 * 支持完整的 SemVer 规范：MAJOR.MINOR.PATCH[-prerelease][+build]
 * - 预发布版本优先级低于正式版：1.0.0-alpha < 1.0.0
 * - 预发布标识符按段逐个比较：数字按数值比较，字符串按字典序比较
 * - 预发布标识符优先级：alpha < beta < rc（通过字典序自然满足）
 * - build metadata（+号后面的部分）不影响优先级比较
 */
@JvmInline
value class Version(val value: String) : Comparable<Version> {

    private fun parse(): ParsedVersion {
        // 去掉 build metadata（+号后面的部分）
        val withoutBuild = value.split("+").first()
        // 分离主版本号和预发布标识符
        val hyphenIndex = withoutBuild.indexOf('-')
        val (coreStr, prereleaseStr) = if (hyphenIndex >= 0) {
            withoutBuild.substring(0, hyphenIndex) to withoutBuild.substring(hyphenIndex + 1)
        } else {
            withoutBuild to null
        }
        val core = coreStr.split(".").map { it.toIntOrNull() ?: 0 }
        val prerelease = prereleaseStr?.split(".")
        return ParsedVersion(core, prerelease)
    }

    override fun compareTo(other: Version): Int {
        val a = this.parse()
        val b = other.parse()

        // 先比较主版本号
        val maxLen = maxOf(a.core.size, b.core.size)
        for (i in 0 until maxLen) {
            val ap = if (i < a.core.size) a.core[i] else 0
            val bp = if (i < b.core.size) b.core[i] else 0
            if (ap != bp) return ap.compareTo(bp)
        }

        // 主版本号相同时比较预发布标识符
        // 有预发布标识符的版本优先级低于没有的：1.0.0-alpha < 1.0.0
        return when {
            a.prerelease == null && b.prerelease == null -> 0
            a.prerelease != null && b.prerelease == null -> -1
            a.prerelease == null && b.prerelease != null -> 1
            else -> comparePrerelease(a.prerelease!!, b.prerelease!!)
        }
    }

    companion object {
        fun compare(version1: String, version2: String): Int {
            return Version(version1).compareTo(Version(version2))
        }

        private fun comparePrerelease(a: List<String>, b: List<String>): Int {
            val maxLen = maxOf(a.size, b.size)
            for (i in 0 until maxLen) {
                // 字段少的优先级更低：1.0.0-alpha < 1.0.0-alpha.1
                if (i >= a.size) return -1
                if (i >= b.size) return 1

                val aNum = a[i].toIntOrNull()
                val bNum = b[i].toIntOrNull()

                val cmp = when {
                    // 都是字：按数值比较
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    // 数字优先级低于字符串
                    aNum != null -> -1
                    bNum != null -> 1
                    // 都是字符串：按字典序比较
                    else -> a[i].compareTo(b[i])
                }
                if (cmp != 0) return cmp
            }
            return 0
        }
    }
}

private data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<String>?,
)

// 扩展操作符函数，使比较更直观
operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)
operator fun Version.compareTo(other: String): Int = this.compareTo(Version(other))
