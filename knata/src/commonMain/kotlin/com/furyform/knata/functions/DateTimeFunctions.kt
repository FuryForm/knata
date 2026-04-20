package com.furyform.knata.functions

import com.furyform.knata.evaluator.*

/**
 * JSONata date/time standard library functions.
 *
 * Uses System.currentTimeMillis() for `$now()` and `$millis()`.
 * Full XPath picture-string formatting is deliberately minimal but covers the
 * most common patterns used by the official test suite.
 */
object DateTimeFunctions {
    fun register(env: Environment) {
        env.fn("millis")      { _, _, _     -> fnMillis() }
        env.fn("now")         { args, _, _  -> fnNow(args) }
        env.fn("fromMillis")  { args, _, _  -> fnFromMillis(args) }
        env.fn("toMillis")    { args, _, _  -> fnToMillis(args) }
    }

    // $millis() → milliseconds since epoch
    private fun fnMillis(): Double = System.currentTimeMillis().toDouble()

    // $now([picture [, timezone]])
    private fun fnNow(args: List<Any?>): String {
        val millis = System.currentTimeMillis()
        val picture = args.getOrNull(0) as? String
        val tz      = args.getOrNull(1) as? String
        return formatMillis(millis, picture, tz)
    }

    // $fromMillis(millis [, picture [, timezone]])
    private fun fnFromMillis(args: List<Any?>): Any? {
        val millis = toNumber(args.getOrNull(0) ?: return null)
            ?.toLong() ?: return null
        val picture = args.getOrNull(1) as? String
        val tz      = args.getOrNull(2) as? String
        return formatMillis(millis, picture, tz)
    }

    // $toMillis(str [, picture])
    private fun fnToMillis(args: List<Any?>): Any? {
        val str = (args.getOrNull(0) as? String) ?: return null
        // picture ignored for now; parse ISO 8601
        return parseIso8601(str)?.toDouble()
    }
}

// ── Date formatting ───────────────────────────────────────────────────────────

private val UTC_ZONE = java.util.TimeZone.getTimeZone("UTC")

internal fun formatMillis(millis: Long, picture: String?, tzId: String?): String {
    val tz = if (tzId != null) java.util.TimeZone.getTimeZone(tzId) else UTC_ZONE
    val cal = java.util.Calendar.getInstance(tz).also { it.timeInMillis = millis }
    if (picture == null) {
        // Default ISO 8601 date-time
        return buildIso8601(cal, tz)
    }
    return applyPicture(cal, picture)
}

private fun buildIso8601(cal: java.util.Calendar, tz: java.util.TimeZone): String {
    val y  = cal.get(java.util.Calendar.YEAR)
    val mo = cal.get(java.util.Calendar.MONTH) + 1
    val d  = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val h  = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val mi = cal.get(java.util.Calendar.MINUTE)
    val s  = cal.get(java.util.Calendar.SECOND)
    val ms = cal.get(java.util.Calendar.MILLISECOND)
    val offsetMillis = tz.getOffset(cal.timeInMillis)
    val tzStr = if (offsetMillis == 0) "Z" else {
        val sign = if (offsetMillis >= 0) "+" else "-"
        val absOff = Math.abs(offsetMillis / 1000)
        "%s%02d:%02d".format(sign, absOff / 3600, (absOff % 3600) / 60)
    }
    return if (ms > 0)
        "%04d-%02d-%02dT%02d:%02d:%02d.%03d%s".format(y, mo, d, h, mi, s, ms, tzStr)
    else
        "%04d-%02d-%02dT%02d:%02d:%02d%s".format(y, mo, d, h, mi, s, tzStr)
}

/** Very basic XPath 3.1 picture-string for dates and times. */
private fun applyPicture(cal: java.util.Calendar, picture: String): String {
    // We interpret only commonly used markers
    val sb = StringBuilder()
    var i = 0
    while (i < picture.length) {
        if (picture[i] == '[') {
            val end = picture.indexOf(']', i + 1)
            if (end < 0) { sb.append(picture[i]); i++; continue }
            val spec = picture.substring(i + 1, end)
            sb.append(formatSpec(cal, spec))
            i = end + 1
        } else {
            sb.append(picture[i])
            i++
        }
    }
    return sb.toString()
}

private fun formatSpec(cal: java.util.Calendar, spec: String): String {
    val component = spec[0]
    return when (component) {
        'Y' -> "%04d".format(cal.get(java.util.Calendar.YEAR))
        'M' -> "%02d".format(cal.get(java.util.Calendar.MONTH) + 1)
        'D' -> "%02d".format(cal.get(java.util.Calendar.DAY_OF_MONTH))
        'd' -> "%03d".format(cal.get(java.util.Calendar.DAY_OF_YEAR))
        'H' -> "%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY))
        'h' -> "%02d".format(cal.get(java.util.Calendar.HOUR))
        'm' -> "%02d".format(cal.get(java.util.Calendar.MINUTE))
        's' -> "%02d".format(cal.get(java.util.Calendar.SECOND))
        'f' -> "%03d".format(cal.get(java.util.Calendar.MILLISECOND))
        'Z' -> {
            val offset = cal.get(java.util.Calendar.ZONE_OFFSET) + cal.get(java.util.Calendar.DST_OFFSET)
            val sign = if (offset >= 0) "+" else "-"
            val absOff = Math.abs(offset / 1000)
            "%s%02d:%02d".format(sign, absOff / 3600, (absOff % 3600) / 60)
        }
        'z' -> {
            val offset = cal.get(java.util.Calendar.ZONE_OFFSET) + cal.get(java.util.Calendar.DST_OFFSET)
            if (offset == 0) "Z" else {
                val sign = if (offset >= 0) "+" else "-"
                val absOff = Math.abs(offset / 1000)
                "%s%02d:%02d".format(sign, absOff / 3600, (absOff % 3600) / 60)
            }
        }
        else -> "[$spec]"
    }
}

internal fun parseIso8601(s: String): Long? {
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        fmt.timeZone = UTC_ZONE
        try {
            fmt.parse(s)?.time
        } catch (_: Exception) {
            val fmt2 = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            fmt2.timeZone = UTC_ZONE
            try {
                fmt2.parse(s)?.time
            } catch (_: Exception) {
                val fmt3 = java.text.SimpleDateFormat("yyyy-MM-dd")
                fmt3.timeZone = UTC_ZONE
                fmt3.parse(s)?.time
            }
        }
    } catch (_: Exception) {
        null
    }
}
