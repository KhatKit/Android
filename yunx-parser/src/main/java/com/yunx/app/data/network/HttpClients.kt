package com.yunx.app.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Parsing only. Downloads use KhatKit's download bridge. */
object HttpClients {
    private val api = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    fun apiClient(): OkHttpClient = api
}
