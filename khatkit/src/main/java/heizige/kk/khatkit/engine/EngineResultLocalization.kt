package heizige.kk.khatkit.engine

import android.content.Context
import heizige.kk.khatkit.R

/** Protocol diagnostic messages stay stable; host UI displays the error code safely. */
fun EngineResult.Err.localizedMessage(context: Context): String = localizedEngineFailure(context, code)

fun CardExecutionException.localizedMessage(context: Context): String = localizedEngineFailure(context, code)

private fun localizedEngineFailure(context: Context, code: String): String = when (code) {
    "CARD_CANCELLED" -> context.getString(R.string.khatkit_runtime_cancelled)
    "CARD_TIMEOUT" -> context.getString(R.string.khatkit_runtime_timeout)
    "RUST_ENGINE", "RUST_ENGINE_UNAVAILABLE", "WASM_ENGINE" -> context.getString(R.string.khatkit_runtime_engine_unavailable)
    "BRIDGE_INJECT_FAILED" -> context.getString(R.string.khatkit_runtime_bridge_injection)
    "COMMAND_MISSING" -> context.getString(R.string.khatkit_runtime_command_missing)
    "COMMAND_UNAVAILABLE" -> context.getString(R.string.khatkit_runtime_command_unavailable)
    "COMMAND_FAILED" -> context.getString(R.string.khatkit_runtime_command_failed)
    else -> context.getString(
        if (code.startsWith("DEPENDENCY_")) R.string.khatkit_dependency_error else R.string.khatkit_runtime_execution_failed,
        code,
    )
}
