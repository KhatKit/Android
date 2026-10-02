package heizige.kk.khatkit.mediapicker.data

import heizige.kk.khatkit.mediapicker.domain.DEFAULT_DATE_FORMAT
import heizige.kk.khatkit.mediapicker.domain.EXTENDED_DATE_FORMAT
import heizige.kk.khatkit.mediapicker.domain.WEEKLY_DATE_FORMAT
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 粘性头的日期文案：今天 / 昨天 / 本周内用星期几 / 往年补上年份。
 *
 * @param timestamp 秒（MediaStore 的 `DATE_MODIFIED` 是秒，不是毫秒）。
 */
fun Long.mediaDateLabel(
    format: String = DEFAULT_DATE_FORMAT,
    weeklyFormat: String = WEEKLY_DATE_FORMAT,
    extendedFormat: String = EXTENDED_DATE_FORMAT,
    stringToday: String = "Today",
    stringYesterday: String = "Yesterday",
): String {
    val locale = Locale.getDefault()
    val now = Calendar.getInstance(locale)
    val media = Calendar.getInstance(locale).apply { timeInMillis = this@mediaDateLabel * 1000L }

    val daysDifference = (now.timeInMillis - media.timeInMillis) / (1000L * 60 * 60 * 24)

    return when (daysDifference.toInt()) {
        0 -> if (now.get(Calendar.DATE) != media.get(Calendar.DATE)) stringYesterday else stringToday
        1 -> stringYesterday
        in 2..5 -> SimpleDateFormatCompat(weeklyFormat).format(Date(media.timeInMillis))
        else -> {
            val pattern = if (now.get(Calendar.YEAR) > media.get(Calendar.YEAR)) {
                extendedFormat
            } else {
                format
            }
            SimpleDateFormatCompat(pattern).format(Date(media.timeInMillis))
        }
    }.replaceFirstChar { it.titlecase(locale) }
}

private fun SimpleDateFormatCompat(pattern: String) =
    java.text.SimpleDateFormat(pattern, Locale.getDefault())
