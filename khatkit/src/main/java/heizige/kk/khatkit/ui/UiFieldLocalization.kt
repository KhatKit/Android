package heizige.kk.khatkit.ui

import android.content.Context
import heizige.kk.khatkit.R

/** Resolve current host locale at the UI boundary, without changing protocol diagnostics. */
fun UiNode.localizedErrors(context: Context, values: Map<String, Any?>): Map<String, String> =
    validationIssues(values).mapValues { (_, issue) -> issue.localizedMessage(context) }

fun UiFieldIssue.localizedMessage(context: Context): String {
    val resourceId = when (code) {
    UiFieldIssue.Code.REQUIRED -> R.string.khatkit_error_field_required
    UiFieldIssue.Code.BOOLEAN_REQUIRED -> R.string.khatkit_error_boolean_required
    UiFieldIssue.Code.COUNTER_RANGE -> R.string.khatkit_error_counter_range
    UiFieldIssue.Code.TEXT_REQUIRED -> R.string.khatkit_error_text_required
    UiFieldIssue.Code.PATH_REQUIRED -> R.string.khatkit_error_path_required
    UiFieldIssue.Code.NUMBER_REQUIRED -> R.string.khatkit_error_number_required
    UiFieldIssue.Code.OUT_OF_RANGE -> R.string.khatkit_error_out_of_range
    UiFieldIssue.Code.STEP_MISMATCH -> R.string.khatkit_error_step_mismatch
    UiFieldIssue.Code.OPTION_REQUIRED -> R.string.khatkit_error_option_required
    }
    return if (code == UiFieldIssue.Code.STEP_MISMATCH) context.getString(resourceId, step.toString())
    else context.getString(resourceId)
}
