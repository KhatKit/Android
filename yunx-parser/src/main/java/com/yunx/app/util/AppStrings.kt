package com.yunx.app.util

import android.content.Context

/** Adapter: parser errors follow the host's configured locale. */
object AppStrings {
    @Volatile private var context: Context? = null
    fun install(value: Context) { context = value.applicationContext }
    fun get(id: Int, vararg args: Any): String =
        context?.getString(id, *args).orEmpty()
}
