package heizige.kk.khatkit.app.core.data.browser

import android.content.Context
import android.os.Looper
import heizige.kk.khatkit.bridge.BridgeContext
import heizige.kk.khatkit.bridge.BrowserBridge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/** A fresh instance owns only this card run's tabs. The executor releases it in finally. */
class CardBrowserBridge(
    private val runContext: BridgeContext,
    private val controller: BrowserToolController,
    private val assertWorkerThread: () -> Unit = {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Browser scripts must run off the main thread" }
    },
) : BrowserBridge, AutoCloseable {
    private val ids = ConcurrentHashMap.newKeySet<String>()
    private val domains = BrowserDomainPolicy(runContext.networkAllow)

    override fun open(url: String): String {
        domains.check(url)
        return invoke("open") {
            controller.open(url).also {
                ids += Json.parseToJsonElement(it).jsonObject.getValue("id").jsonPrimitive.content
            }
        }
    }

    override fun snapshot(sessionId: String, selector: String?, offset: Int): String {
        require(offset in 0..100_000) { "offset must be between 0 and 100000" }
        return invoke("snapshot") { tab(sessionId).snapshot(selector, offset) }
    }

    override fun act(sessionId: String, action: String, arguments: Map<String, Any?>?): String {
        val args = arguments.orEmpty()
        fun text(key: String) = args[key] as? String ?: error("$key must be a string")
        fun number(key: String): Int {
            val value = args[key] as? Number ?: error("$key must be an integer")
            val asDouble = value.toDouble()
            require(asDouble.isFinite() && asDouble == value.toInt().toDouble()) { "$key must be an integer" }
            return value.toInt()
        }
        return invoke("act") {
            val target = tab(sessionId)
            when (action) {
                "snapshot" -> target.snapshot(
                    args["selector"] as? String,
                    if (args.containsKey("offset")) number("offset") else 0,
                    args["since"] as? String,
                )
                "wait" -> target.waitFor(
                    args["selector"] as? String, args["text"] as? String,
                    if (args.containsKey("timeout_ms")) number("timeout_ms") else 10_000,
                )
                "navigate" -> text("url").let { domains.check(it); target.navigate(it) }
                "click" -> target.click(text("selector"))
                "type" -> target.type(text("selector"), text("text"))
                "scroll" -> target.scroll(number("x"), number("y"))
                "press_key" -> target.pressKey(text("selector"), text("key"))
                "hover" -> target.hover(text("selector"))
                "extract_text" -> target.extractText()
                "read_element" -> target.extractText(text("selector"))
                "extract_links" -> target.extractLinks()
                "screenshot" -> target.screenshot().let { shot ->
                    buildJsonObject {
                        put("image_url", shot.imageUrl)
                        put("capture", Json.parseToJsonElement(shot.metadata))
                    }.toString()
                }
                else -> error("Unknown browser action: $action")
            }
        }
    }

    override fun close(sessionId: String): String = invoke("close") {
        tab(sessionId)
        controller.close(sessionId).also { ids.remove(sessionId) }
    }

    private fun tab(id: String): BrowserToolController {
        require(id in ids) { "Browser session does not belong to this card run" }
        return controller.forSession(id)
    }

    private fun <T> invoke(method: String, block: suspend () -> T): T {
        assertWorkerThread()
        check(!runContext.deadline.expired()) { "Card execution timed out" }
        val key = "browser.$method"
        check(runContext.policy(key) != "deny") { "Card denied $key" }
        if (runContext.policy(key) != "allow") {
            check(runContext.approvalGate?.request(
                "卡片浏览器：${runContext.cardName}", "允许卡片调用 $key？", "browser",
            ) == true) { "Browser action was not approved" }
        }
        check(!runContext.deadline.expired()) { "Card execution timed out" }
        return runBlocking { withTimeout(minOf(30_000L, runContext.deadline.remainingMs())) { block() } }
    }

    /** Cleanup never asks permission, including after denial or a run deadline. */
    override fun close() {
        assertWorkerThread()
        runBlocking {
            var failure: Throwable? = null
            ids.toList().forEach { id ->
                try {
                    controller.close(id)
                } catch (error: Exception) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                } finally {
                    ids.remove(id)
                }
            }
            failure?.let { throw it }
        }
    }

    companion object {
        fun create(context: Context, runContext: BridgeContext): CardBrowserBridge =
            CardBrowserBridge(
                runContext,
                BrowserRuntime(context, checkUrl = BrowserDomainPolicy(runContext.networkAllow)::check),
            )
    }
}
