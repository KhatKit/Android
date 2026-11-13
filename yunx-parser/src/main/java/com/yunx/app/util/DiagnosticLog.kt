@file:Suppress("UNUSED_PARAMETER")
package com.yunx.app.util

/** Upstream diagnostic hooks are intentionally disabled; no credential logging. */
object DiagnosticLog {
    const val NETWORK = "network"
    fun isEnabled() = false
    fun event(module: String, event: String, summary: String? = null) = Unit
    fun warn(module: String, event: String, summary: String? = null) = Unit
    fun error(module: String, event: String, code: Any? = null, summary: String? = null) = Unit
    fun webview(event: String, url: String? = null, code: Any? = null, summary: String? = null) = Unit
}
