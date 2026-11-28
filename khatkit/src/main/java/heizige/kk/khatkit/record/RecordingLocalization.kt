package heizige.kk.khatkit.record

import android.content.Context
import heizige.kk.khatkit.R

/** Presentation only: persisted steps and generated script comments remain unchanged. */
fun UiAction.localizedTargetLabel(context: Context): String =
    if (text.isBlank() && desc.isBlank() && viewId.isBlank() && bbox == null && !hasCenter)
        context.getString(R.string.khatkit_record_unknown_target) else targetLabel()

fun RecordedStep.localizedTargetLabel(context: Context): String =
    if (text.isBlank() && desc.isBlank() && viewId.isBlank() && !hasCenter)
        context.getString(R.string.khatkit_record_unknown_target) else targetLabel()

private fun localizedDirection(context: Context, direction: String): String = when (direction) {
    "up" -> context.getString(R.string.khatkit_record_direction_up)
    "down" -> context.getString(R.string.khatkit_record_direction_down)
    "left" -> context.getString(R.string.khatkit_record_direction_left)
    "right" -> context.getString(R.string.khatkit_record_direction_right)
    else -> direction
}

fun UiAction.localizedComment(context: Context): String = when (type) {
    UiAction.Type.CLICK -> context.getString(R.string.khatkit_record_click, localizedTargetLabel(context))
    UiAction.Type.SET_TEXT -> context.getString(R.string.khatkit_record_type, inputText)
    UiAction.Type.SWIPE -> context.getString(R.string.khatkit_record_swipe, localizedDirection(context, direction))
    UiAction.Type.PRESS -> context.getString(R.string.khatkit_record_press, localizedTargetLabel(context))
    UiAction.Type.OPEN_APP -> context.getString(R.string.khatkit_record_open_app, packageName)
    UiAction.Type.WAIT -> context.getString(R.string.khatkit_record_wait)
    UiAction.Type.GLOBAL -> context.getString(R.string.khatkit_record_global, globalAction)
}

fun RecordedStep.localizedComment(context: Context): String = when (type) {
    RecordedStep.Type.CLICK -> context.getString(R.string.khatkit_record_click, localizedTargetLabel(context))
    RecordedStep.Type.SET_TEXT -> context.getString(R.string.khatkit_record_type, inputText)
    RecordedStep.Type.SWIPE -> context.getString(R.string.khatkit_record_swipe, localizedDirection(context, direction))
    RecordedStep.Type.OPEN_APP -> context.getString(R.string.khatkit_record_open_app, packageName)
    RecordedStep.Type.WAIT -> context.getString(R.string.khatkit_record_wait)
}
