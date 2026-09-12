package heizige.kk.khatkit.common.http.okhttp.internal

import heizige.kk.khatkit.common.http.okhttp.Call
import heizige.kk.khatkit.common.http.okhttp.Response

/** 兼容 OkHttp 的 `okhttp3.internal.closeQuietly`。 */
fun Response.closeQuietly() {
    runCatching { close() }
}

fun Call.closeQuietly() {
    runCatching { cancel() }
}
