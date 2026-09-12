package heizige.kk.khatkit.common.http

import kotlinx.coroutines.suspendCancellableCoroutine
import heizige.kk.khatkit.common.http.okhttp.Call
import heizige.kk.khatkit.common.http.okhttp.Callback
import heizige.kk.khatkit.common.http.okhttp.Response
import heizige.kk.khatkit.common.http.okhttp.internal.closeQuietly
import java.io.IOException
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { cause, _, _ ->
                    response.closeQuietly()
                }
            }
        })
    }
}
