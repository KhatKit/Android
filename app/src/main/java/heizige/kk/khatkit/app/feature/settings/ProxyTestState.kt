package heizige.kk.khatkit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.network.toProxyOrNull
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khromia.helper.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val PROXY_TEST_URL = "https://www.google.com/generate_204"

/**
 * 网络页的代理状态与连通性测试。
 *
 * MD3 与 Miuix 两份页面共用，避免测试逻辑（超时、错误码判定、Toast 文案）写两份后漂移。
 */
@Composable
internal fun rememberProxyTestState(): ProxyTestState {
    val httpClient = rememberAppEntryPoint().okHttpClient()
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()

    var proxyUrl by remember { mutableStateOf("") }
    var proxyUsername by remember { mutableStateOf("") }
    var proxyPassword by remember { mutableStateOf("") }
    var proxyUrlDraft by remember { mutableStateOf("") }
    var proxyUsernameDraft by remember { mutableStateOf("") }
    var proxyPasswordDraft by remember { mutableStateOf("") }
    var proxyTesting by remember { mutableStateOf(false) }

    fun testProxy() {
        val proxy = proxyUrl.toProxyOrNull() ?: return
        if (proxyTesting) return
        proxyTesting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val testClient = httpClient.newBuilder()
                        .proxy(proxy)
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .callTimeout(15, TimeUnit.SECONDS)
                        .build()
                    testClient.newCall(
                        Request.Builder()
                            .url(PROXY_TEST_URL)
                            .head()
                            .build()
                    ).execute().use { response ->
                        if (response.code != 204) {
                            throw IOException("HTTP ${response.code} ${response.message}")
                        }
                    }
                }
            }
            result.onSuccess {
                Toast.show(
                    resources.getString(R.string.backup_page_connection_success),
                    isError = false,
                )
            }.onFailure { error ->
                Toast.show(
                    resources.getString(
                        R.string.backup_page_connection_failed,
                        error.message.orEmpty(),
                    ),
                    isError = true,
                )
            }
            proxyTesting = false
        }
    }

    return ProxyTestState(
        proxyUrl = proxyUrl,
        setProxyUrl = { proxyUrl = it },
        proxyUsername = proxyUsername,
        setProxyUsername = { proxyUsername = it },
        proxyPassword = proxyPassword,
        setProxyPassword = { proxyPassword = it },
        draftUrl = proxyUrlDraft,
        setDraftUrl = { proxyUrlDraft = it },
        draftUsername = proxyUsernameDraft,
        setDraftUsername = { proxyUsernameDraft = it },
        draftPassword = proxyPasswordDraft,
        setDraftPassword = { proxyPasswordDraft = it },
        testing = proxyTesting,
        startTest = ::testProxy,
    )
}

/** [rememberProxyTestState] 的返回值。 */
internal data class ProxyTestState(
    val proxyUrl: String,
    val setProxyUrl: (String) -> Unit,
    val proxyUsername: String,
    val setProxyUsername: (String) -> Unit,
    val proxyPassword: String,
    val setProxyPassword: (String) -> Unit,
    val draftUrl: String,
    val setDraftUrl: (String) -> Unit,
    val draftUsername: String,
    val setDraftUsername: (String) -> Unit,
    val draftPassword: String,
    val setDraftPassword: (String) -> Unit,
    val testing: Boolean,
    val startTest: () -> Unit,
) {
    /** 草稿区是否有改动（决定「重置」按钮是否可用）。 */
    val draftDirty: Boolean
        get() = draftUrl.isNotEmpty() || draftUsername.isNotEmpty() || draftPassword.isNotEmpty()

    /** 打开编辑弹窗前把已保存值灌进草稿。 */
    fun beginEdit() {
        setDraftUrl(proxyUrl)
        setDraftUsername(proxyUsername)
        setDraftPassword(proxyPassword)
    }

    /** 草稿是否可保存（URL 合法且有改动）。 */
    fun canSave(): Boolean = !draftDirty || draftUrl.isBlank() || draftUrl.toProxyOrNull() != null

    /** 保存草稿并返回要写入设置的值。 */
    fun commit(): Triple<String, String, String> = Triple(draftUrl, draftUsername, draftPassword)

    /** 清空草稿。 */
    fun resetDraft() {
        setDraftUrl("")
        setDraftUsername("")
        setDraftPassword("")
    }
}
