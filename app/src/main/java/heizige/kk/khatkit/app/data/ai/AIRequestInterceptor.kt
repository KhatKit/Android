package heizige.kk.khatkit.app.data.ai

import heizige.kk.khatkit.common.http.okhttp.Interceptor
import heizige.kk.khatkit.common.http.okhttp.Response

class AIRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(request)
    }
}
