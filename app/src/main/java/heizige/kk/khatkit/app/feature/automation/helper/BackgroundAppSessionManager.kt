package heizige.kk.khatkit.app.feature.automation.helper

import android.content.Context
import android.os.Build
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeHolder
import heizige.kk.khatkit.bridge.impl.AccessibilityBridgeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Owns one Shizuku/root virtual display used by the existing accessibility card bridge. */
object BackgroundAppSessionManager {
    private class Session(
        val displayId: Int,
        val packageName: String,
        val context: Context,
        val bridge: AccessibilityBridgeImpl,
        val runShell: (String) -> String,
    ) {
        @Volatile var cleanupPending = true
        @Volatile var serviceLost = false

        val isActive: Boolean
            get() = !cleanupPending && !serviceLost
    }

    @Volatile
    private var session: Session? = null

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun start(context: Context, packageName: String, runShell: (String) -> String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return error(context, R.string.automation_background_android_required)
        }
        if (!PACKAGE_NAME.matches(packageName)) return error(context, R.string.automation_background_invalid_package)
        val bridge = AccessibilityBridgeHolder.current()
            ?: return error(context, R.string.automation_background_accessibility_required)
        val installedApp = runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
            ?: return error(context, R.string.automation_background_app_unavailable, packageName)
        if (installedApp.component == null) {
            return error(context, R.string.automation_background_entry_unavailable, packageName)
        }

        val help = executeShell(runShell, "cmd display help")
        if (!VirtualDisplayProtocol.supportsVirtualDisplayCommands(help)) {
            return error(context, R.string.automation_background_unsupported)
        }
        if (session != null) {
            val cleanup = stopInternal()
            if (!VirtualDisplayProtocol.isSuccessful(cleanup)) {
                return error(context, R.string.automation_background_cleanup_failed, cleanup.take(300))
            }
        }
        val displayName = "KhatKit_" + UUID.randomUUID().toString().take(8)
        val created = executeShell(runShell, VirtualDisplayProtocol.createCommand(displayName))
        val displayId = VirtualDisplayProtocol.parseCreatedDisplayId(created)
            ?: return error(context, R.string.automation_background_create_failed, created.take(500))

        // Keep ownership before binding/launching: every later exception must retain a cleanup path.
        val createdSession = Session(displayId, packageName, context.applicationContext, bridge, runShell)
        session = createdSession
        val opened = runCatching {
            bridge.useVirtualDisplay(displayId, packageName) { action ->
                val active = session
                if (action != "back" || active !== createdSession || !active.isActive) {
                    return@useVirtualDisplay false
                }
                VirtualDisplayProtocol.isSuccessful(
                    executeShell(runShell, "input -d $displayId keyevent KEYCODE_BACK"),
                )
            }
            AccessibilityBridgeHolder.current() === bridge &&
                bridge.openApp(packageName) &&
                bridge.waitForPackage(packageName, 3_000L) &&
                AccessibilityBridgeHolder.current() === bridge &&
                !createdSession.serviceLost
        }.getOrDefault(false)
        if (opened) createdSession.cleanupPending = false
        if (!opened || createdSession.serviceLost || AccessibilityBridgeHolder.current() !== bridge) {
            val cleanup = stopInternal()
            return if (VirtualDisplayProtocol.isSuccessful(cleanup)) {
                error(context, R.string.automation_background_launch_unconfirmed)
            } else {
                error(context, R.string.automation_background_launch_cleanup_failed, cleanup.take(300))
            }
        }
        return buildJsonObject {
            put("ok", true)
            put("active", createdSession.isActive)
            put("cleanup_pending", !createdSession.isActive)
            put("display_id", displayId)
            put("package_name", packageName)
            put("message", context.getString(R.string.automation_background_started))
        }.toString()
    }

    @Synchronized
    fun stop(): String {
        val active = session ?: return status()
        val result = stopInternal()
        val succeeded = VirtualDisplayProtocol.isSuccessful(result)
        return buildJsonObject {
            put("ok", succeeded)
            put("active", false)
            put("cleanup_pending", !succeeded)
            put("display_id", active.displayId)
            put("package_name", active.packageName)
            put(
                "message",
                if (succeeded) active.context.getString(R.string.automation_background_stopped)
                else active.context.getString(R.string.automation_background_cleanup_failed, result.take(300)),
            )
        }.toString()
    }

    fun status(): String {
        val active = session
        return buildJsonObject {
            put("ok", true)
            put("active", active?.isActive == true)
            put("cleanup_pending", active != null && !active.isActive)
            if (active != null) {
                put("display_id", active.displayId)
                put("package_name", active.packageName)
            }
        }.toString()
    }

    /** Called when Android disconnects the accessibility service. */
    fun onAccessibilityServiceLost() {
        val active = session ?: return
        active.serviceLost = true
        active.cleanupPending = true
        cleanupScope.launch {
            synchronized(this@BackgroundAppSessionManager) {
                if (session === active) stopInternal()
            }
        }
    }

    /** A reconnected service must not silently route a retained session back to the main display. */
    fun onAccessibilityServiceConnected(bridge: AccessibilityBridgeImpl) {
        val active = session ?: return
        active.serviceLost = true
        active.cleanupPending = true
        runCatching { bridge.useVirtualDisplay(active.displayId, active.packageName) { false } }
        cleanupScope.launch {
            synchronized(this@BackgroundAppSessionManager) {
                if (session === active) {
                    val result = stopInternal()
                    if (VirtualDisplayProtocol.isSuccessful(result)) {
                        // This callback can run before the service publishes the new bridge in Holder.
                        bridge.clearVirtualDisplay(active.displayId)
                    }
                } else if (session?.displayId != active.displayId) {
                    bridge.clearVirtualDisplay(active.displayId)
                }
            }
        }
    }

    private fun stopInternal(): String {
        val active = session ?: return "[khatkit-display-exit 0]\n"
        active.cleanupPending = true
        // A reconnect can happen while create is running, before a session is visible to its callback.
        // Retain the target on the new bridge too if cleanup fails, rather than falling back to display 0.
        AccessibilityBridgeHolder.current()?.takeIf { it !== active.bridge }?.let { bridge ->
            runCatching { bridge.useVirtualDisplay(active.displayId, active.packageName) { false } }
        }
        val result = executeShell(active.runShell, VirtualDisplayProtocol.destroyCommand(active.displayId))
        if (VirtualDisplayProtocol.isSuccessful(result)) {
            active.bridge.clearVirtualDisplay(active.displayId)
            AccessibilityBridgeHolder.current()?.clearVirtualDisplay(active.displayId)
            session = null
        }
        return result
    }

    private fun executeShell(runShell: (String) -> String, command: String): String =
        runCatching { runShell(VirtualDisplayProtocol.checkedCommand(command)) }
            .getOrElse { "[error] ${it.message ?: it.javaClass.simpleName}" }

    private fun error(context: Context, message: Int, vararg args: Any): String = buildJsonObject {
        put("ok", false)
        put("error", context.getString(message, *args))
        val active = session
        put("active", active?.isActive == true)
        put("cleanup_pending", active != null && !active.isActive)
    }.toString()

    private val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
}
