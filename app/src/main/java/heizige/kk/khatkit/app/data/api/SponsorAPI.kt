package heizige.kk.khatkit.app.data.api

import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.Request
import heizige.kk.khatkit.app.data.model.Sponsor
import heizige.kk.khatkit.app.utils.JsonInstant

/** Ktor 实现的赞助者接口（替代 Retrofit）。 */
class SponsorAPI(private val httpClient: OkHttpClient) {

    suspend fun getSponsors(): List<Sponsor> {
        val request = Request.Builder()
            .url("https://sponsors.rikka-ai.com/sponsors")
            .get()
            .build()
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) {
            error("getSponsors failed: ${response.code} ${response.message}")
        }
        return JsonInstant.decodeFromString(response.body.string())
    }

    companion object {
        fun create(httpClient: OkHttpClient): SponsorAPI = SponsorAPI(httpClient)
    }
}
