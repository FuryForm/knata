package com.furyform.knata.functions

import com.furyform.knata.evaluator.*
import kotlinx.datetime.*
import kotlin.math.abs

/**
 * JSONata date/time standard library functions.
 *
 * Uses [Clock.System] for `$now()` and `$millis()`.
 * Full XPath picture-string formatting is deliberately minimal but covers the
 * most common patterns used by the official test suite.
 *
 * Pure Kotlin/KMP implementation — no java.* imports.
 */
object DateTimeFunctions {
    fun register(env: Environment) {
        env.fn("millis")      { _, _, _     -> fnMillis() }
        env.fn("now")         { args, _, _  -> fnNow(args) }
        env.fn("fromMillis")  { args, _, _  -> fnFromMillis(args) }
        env.fn("toMillis")    { args, _, _  -> fnToMillis(args) }
    }

    private fun fnMillis(): Double = Clock.System.now().toEpochMilliseconds().toDouble()

    private fun fnNow(args: List<Any?>): String {
        val millis  = Clock.System.now().toEpochMilliseconds()
        val picture = args.getOrNull(0) as? String
        val tz      = args.getOrNull(1) as? String
        return formatMillis(millis, picture, tz)
    }

    private fun fnFromMillis(args: List<Any?>): Any? {
        val millis  = toNumber(args.getOrNull(0) ?: return null)?.toLong() ?: return null
        val picture = args.getOrNull(1) as? String
        val tz      = args.getOrNull(2) as? String
        return formatMillis(millis, picture, tz)
    }

    private fun fnToMillis(args: List<Any?>): Any? {
        val str = (args.getOrNull(0) as? String) ?: return null
        return parseIso8601(str)?.toDouble()
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun resolveTimeZone(tzId: String?): TimeZone =
    if (tzId == null) TimeZone.UTC
    else try { TimeZone.of(tzId) } catch (_: IllegalTimeZoneException) { TimeZone.UTC }

internal fun formatMillis(millis: Long, picture: String?, tzId: String?): String {
    val tz      = resolveTimeZone(tzId)
    val instant = Instant.fromEpochMilliseconds(millis)
    val dt      = instant.toLocalDateTime(tz)
    return if (picture == null) buildIso8601(instant, tz, dt) else applyPicture(dt, tz, instant, picture)
}

private fun buildIso8601(instant: Instant, tz: TimeZone, dt: LocalDateTime): String {
    val offsetSecs = tz.offsetAt(instant).totalSeconds
    val tzStr = tzOffsetString(offsetSecs, alwaysZ = true)
    val ms = dt.nanosecond / 1_000_000
    return if (ms > 0)
        "%04d-%02d-%02dT%02d:%02d:%02d.%03d%s".format(dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute, dt.second, ms, tzStr)
    else
        "%04d-%02d-%02dT%02d:%02d:%02d%s".format(dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute, dt.second, tzStr)
}

private fun applyPicture(dt: LocalDateTime, tz: TimeZone, instant: Instant, picture: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < picture.length) {
        if (picture[i] == '[') {
            val end = picture.indexOf(']', i + 1)
            if (end < 0) { sb.append(picture[i]); i++; continue }
            sb.append(formatSpec(dt, tz, instant, picture.substring(i + 1, end)))
            i = end + 1
        } else {
            sb.append(picture[i++])
        }
    }
    return sb.toString()
}

private fun formatSpec(dt: LocalDateTime, tz: TimeZone, instant: Instant, spec: String): String {
    val offsetSecs = tz.offsetAt(instant).totalSeconds
    return when (spec[0]) {
        'Y' -> "%04d".format(dt.year)
        'M' -> "%02d".format(dt.monthNumber)
        'D' -> "%02d".format(dt.dayOfMonth)
        'd' -> "%03d".format(dt.date.dayOfYear)
        'H' -> "%02d".format(dt.hour)
        'h' -> "%02d".format(if (dt.hour % 12 == 0) 12 else dt.hour % 12)
        'm' -> "%02d".format(dt.minute)
        's' -> "%02d".format(dt.second)
        'f' -> "%03d".format(dt.nanosecond / 1_000_000)
        'Z' -> tzOffsetString(offsetSecs, alwaysZ = false)
        'z' -> tzOffsetString(offsetSecs, alwaysZ = true)
        else -> "[$spec]"
    }
}

private fun tzOffsetString(offsetSecs: Int, alwaysZ: Boolean): String {
    if (alwaysZ && offsetSecs == 0) return "Z"
    val sign   = if (offsetSecs >= 0) "+" else "-"
    val absOff = abs(offsetSecs)
    return "%s%02d:%02d".format(sign, absOff / 3600, (absOff % 3600) / 60)
}

internal fun parseIso8601(s: String): Long? = try {
    Instant.parse(s).toEpochMilliseconds()
} catch (_: Exception) {
    try {
        LocalDate.parse(s).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    } catch (_: Exception) { null }
}
