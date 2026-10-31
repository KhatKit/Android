package heizige.kk.khatkit.app.core.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.edit

private const val TAG = "CrashHandler"
private const val PREFS_NAME = "crash_handler"
private const val KEY_CRASHED = "crashed"
private const val KEY_STACKTRACE = "stacktrace"
private const val KEY_KIND = "kind"
private const val KEY_SUMMARY = "summary"
private const val KEY_THREAD = "thread"
private const val KEY_TIME = "time"
private const val KEY_LAST_EXIT = "last_exit_time"
private const val MAX_STACKTRACE_LENGTH = 16000

/** 上次崩溃的报告：[native] 为 true 时是原生崩溃（信号），否则是 Java 未捕获异常。 */
data class CrashReport(
    val native: Boolean,
    val summary: String,
    val thread: String,
    val time: Long,
    val text: String,
)

object CrashHandler {
    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            markCrashed(appContext, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 上次运行是否崩溃。Java 异常在崩溃当时就记下了；原生崩溃（渲染线程栈溢出、SIGSEGV 等）
     * 进程直接被杀、来不及记，所以这里再问一次系统：主进程最近一次退出是不是原生崩溃。
     */
    fun hasCrashed(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_CRASHED, false)) return true
        return runCatching { captureNativeCrash(context, prefs) }
            .onFailure { Log.w(TAG, "Failed to read process exit reasons", it) }
            .getOrDefault(false)
    }

    fun getReport(context: Context): CrashReport? {
        val prefs = prefs(context)
        val text = prefs.getString(KEY_STACKTRACE, null) ?: return null
        return CrashReport(
            native = prefs.getString(KEY_KIND, null) == "native",
            summary = prefs.getString(KEY_SUMMARY, null).orEmpty(),
            thread = prefs.getString(KEY_THREAD, null).orEmpty(),
            time = prefs.getLong(KEY_TIME, 0L),
            text = text,
        )
    }

    fun getStackTrace(context: Context): String? = prefs(context).getString(KEY_STACKTRACE, null)

    fun clearCrashed(context: Context) {
        prefs(context).edit {
            remove(KEY_CRASHED).remove(KEY_STACKTRACE).remove(KEY_KIND)
                .remove(KEY_SUMMARY).remove(KEY_THREAD).remove(KEY_TIME)
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun markCrashed(context: Context, thread: Thread, throwable: Throwable) {
        val stackTrace = buildString {
            appendLine("Thread: ${thread.name}")
            appendLine(throwable.stackTraceToString())
        }.take(MAX_STACKTRACE_LENGTH)
        val summary = listOfNotNull(
            throwable::class.java.simpleName,
            throwable.message?.lineSequence()?.firstOrNull()?.take(200),
        ).joinToString(": ")
        prefs(context).edit(commit = true) {
            putBoolean(KEY_CRASHED, true)
            putString(KEY_STACKTRACE, stackTrace)
            putString(KEY_KIND, "java")
            putString(KEY_SUMMARY, summary)
            putString(KEY_THREAD, thread.name)
            putLong(KEY_TIME, System.currentTimeMillis())
        } // commit() 同步写入，确保崩溃前写完
    }

    /**
     * 只看主进程最近一次退出：是原生崩溃、且之前没报过，才算。装新版、用户划掉、系统回收都会盖掉旧记录，
     * 所以不会把很久以前的崩溃翻出来。
     */
    private fun captureNativeCrash(context: Context, prefs: SharedPreferences): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val last = am.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            .filter { it.processName == context.packageName }
            .maxByOrNull { it.timestamp } ?: return false
        if (last.timestamp <= prefs.getLong(KEY_LAST_EXIT, 0L)) return false
        prefs.edit { putLong(KEY_LAST_EXIT, last.timestamp) }
        if (last.reason != ApplicationExitInfo.REASON_CRASH_NATIVE) return false

        val parsed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { last.traceInputStream?.use { Tombstone.parse(it.readBytes()) } }.getOrNull()
        } else {
            null
        }
        val text = parsed?.text ?: buildString {
            appendLine("Native crash")
            appendLine("Process: ${last.processName} (pid ${last.pid})")
            last.description?.let { appendLine("Description: $it") }
            appendLine("Signal: ${last.status}")
        }
        val summary = when {
            parsed == null -> last.description ?: "signal ${last.status}"
            parsed.cause.isNotBlank() && parsed.signal.isNotBlank() -> "${parsed.signal} · ${parsed.cause}"
            else -> parsed.signal.ifBlank { parsed.cause }
        }
        prefs.edit(commit = true) {
            putBoolean(KEY_CRASHED, true)
            putString(KEY_STACKTRACE, text.take(MAX_STACKTRACE_LENGTH))
            putString(KEY_KIND, "native")
            putString(KEY_SUMMARY, summary)
            putString(KEY_THREAD, parsed?.threadName.orEmpty())
            putLong(KEY_TIME, last.timestamp)
        }
        return true
    }
}
