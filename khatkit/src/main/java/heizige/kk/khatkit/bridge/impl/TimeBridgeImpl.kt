package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.TimeBridge
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TimeBridgeImpl : TimeBridge {
    override fun now(): Long = System.currentTimeMillis()

    override fun nowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    override fun format(pattern: String, timestampMs: Long, timeZone: String): String {
        require(pattern.isNotBlank()) { "时间格式不能为空" }
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        if (timeZone.isNotBlank()) formatter.timeZone = TimeZone.getTimeZone(timeZone)
        return formatter.format(Date(if (timestampMs == 0L) now() else timestampMs))
    }

    override fun parse(text: String, pattern: String): Long {
        if (pattern.isBlank()) return runCatching { Instant.parse(text).toEpochMilli() }
            .getOrElse { throw IllegalArgumentException("ISO-8601 时间解析失败：$text") }
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        formatter.isLenient = false
        return runCatching { formatter.parse(text)?.time }
            .getOrNull() ?: throw IllegalArgumentException("时间解析失败：$text")
    }

    override fun timeZone(): String = ZoneId.systemDefault().id
    override fun locale(): String = Locale.getDefault().toLanguageTag()
}
