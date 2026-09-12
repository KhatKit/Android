package heizige.kk.khatkit.common.http.okhttp.logging

import android.util.Log
import heizige.kk.khatkit.common.http.okhttp.Interceptor
import heizige.kk.khatkit.common.http.okhttp.Response

/** Ktor 兼容层的日志拦截器（替代 okhttp3.logging）。 */
class HttpLoggingInterceptor(
    private val logger: Logger = Logger.DEFAULT,
) : Interceptor {

    enum class Level { NONE, BASIC, HEADERS, BODY }

    var level: Level = Level.NONE
    private val redactedHeaders = mutableSetOf<String>()

    fun redactHeader(name: String) {
        redactedHeaders.add(name.lowercase())
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (level == Level.NONE) return chain.proceed(chain.request)
        val request = chain.request
        logger.log("--> ${request.method} ${request.url}")
        if (level != Level.BASIC) {
            request.headers.names().forEach { name ->
                request.headers.values(name).forEach { value -> logger.log("$name: ${mask(name, value)}") }
            }
        }
        val response = chain.proceed(request)
        logger.log("<-- ${response.code} ${response.message} ${request.url}")
        if (level != Level.BASIC) {
            response.headers.names().forEach { name ->
                response.headers.values(name).forEach { value -> logger.log("$name: ${mask(name, value)}") }
            }
        }
        return response
    }

    private fun mask(name: String, value: String): String =
        if (name.lowercase() in redactedHeaders) "██" else value

    fun interface Logger {
        fun log(message: String)

        companion object {
            val DEFAULT: Logger = Logger { message -> Log.d("KtorHttp", message) }
        }
    }
}
