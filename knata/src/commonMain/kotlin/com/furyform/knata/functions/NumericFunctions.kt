package com.furyform.knata.functions

import com.furyform.knata.JSONataException
import com.furyform.knata.evaluator.*
import kotlin.math.*

/**
 * JSONata numeric standard library functions.
 */
object NumericFunctions {
    fun register(env: Environment) {
        env.fn("number")  { args, _, _ -> fnNumber(args) }
        env.fn("abs")     { args, _, _ -> fnAbs(args) }
        env.fn("floor")   { args, _, _ -> fnFloor(args) }
        env.fn("ceil")    { args, _, _ -> fnCeil(args) }
        env.fn("round")   { args, _, _ -> fnRound(args) }
        env.fn("power")   { args, _, _ -> fnPower(args) }
        env.fn("sqrt")    { args, _, _ -> fnSqrt(args) }
        env.fn("random")  { _, _, _    -> fnRandom() }
        env.fn("sum")     { args, _, _ -> fnSum(args) }
        env.fn("max")     { args, _, _ -> fnMax(args) }
        env.fn("min")     { args, _, _ -> fnMin(args) }
        env.fn("average") { args, _, _ -> fnAverage(args) }
        env.fn("formatNumber")  { args, _, _ -> fnFormatNumber(args) }
        env.fn("formatBase")    { args, _, _ -> fnFormatBase(args) }
        env.fn("formatInteger") { args, _, _ -> fnFormatInteger(args) }
        env.fn("parseInteger")  { args, _, _ -> fnParseInteger(args) }
    }

    // $number(arg)
    private fun fnNumber(args: List<Any?>): Any? {
        val v = args.getOrNull(0) ?: return null
        if (isNull(v)) return null
        return when (v) {
            is Double  -> v
            is String  -> v.trim().toDoubleOrNull()
                ?: throw JSONataException.RuntimeError("Unable to cast value to a number: $v", "D3030")
            is Boolean -> if (v) 1.0 else 0.0
            else -> null
        }
    }

    // $abs(n)
    private fun fnAbs(args: List<Any?>): Any? {
        val n = requireNumber(args, 0, "abs") ?: return null
        return abs(n)
    }

    // $floor(n)
    private fun fnFloor(args: List<Any?>): Any? {
        val n = requireNumber(args, 0, "floor") ?: return null
        return floor(n)
    }

    // $ceil(n)
    private fun fnCeil(args: List<Any?>): Any? {
        val n = requireNumber(args, 0, "ceil") ?: return null
        return ceil(n)
    }

    // $round(n [, precision])
    private fun fnRound(args: List<Any?>): Any? {
        val n = requireNumber(args, 0, "round") ?: return null
        val precision = args.getOrNull(1)?.let { toNumber(it)?.toInt() } ?: 0
        if (precision == 0) {
            // JSONata uses "round half to even" (banker's rounding)
            return roundHalfToEven(n, 0)
        }
        return roundHalfToEven(n, precision)
    }

    /** Round half to even (banker's rounding). */
    private fun roundHalfToEven(n: Double, precision: Int): Double {
        val factor = 10.0.pow(precision)
        val shifted = n * factor
        val floor = floor(shifted)
        val diff = shifted - floor
        return when {
            diff < 0.5 -> floor / factor
            diff > 0.5 -> ceil(shifted) / factor
            else -> {
                // Exactly 0.5: round to even
                if (floor % 2 == 0.0) floor / factor else ceil(shifted) / factor
            }
        }
    }

    // $power(base, exponent)
    private fun fnPower(args: List<Any?>): Any? {
        val base = requireNumber(args, 0, "power") ?: return null
        val exp  = requireNumber(args, 1, "power") ?: return null
        val result = base.pow(exp)
        if (result.isInfinite()) throw JSONataException.RuntimeError("Power function returned Infinity", "D3001")
        if (result.isNaN()) throw JSONataException.RuntimeError("Power function returned NaN", "D3001")
        return result
    }

    // $sqrt(n)
    private fun fnSqrt(args: List<Any?>): Any? {
        val n = requireNumber(args, 0, "sqrt") ?: return null
        if (n < 0) throw JSONataException.RuntimeError("sqrt of negative number", "D3001")
        return sqrt(n)
    }

    // $random()
    private fun fnRandom(): Double = Math.random()

    // $sum(array)
    private fun fnSum(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        val list = toNumberList(arr, "sum") ?: return null
        if (list.isEmpty()) return null
        return list.sum()
    }

    // $max(array)
    private fun fnMax(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        val list = toNumberList(arr, "max") ?: return null
        if (list.isEmpty()) return null
        return list.max()
    }

    // $min(array)
    private fun fnMin(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        val list = toNumberList(arr, "min") ?: return null
        if (list.isEmpty()) return null
        return list.min()
    }

    // $average(array)
    private fun fnAverage(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        val list = toNumberList(arr, "average") ?: return null
        if (list.isEmpty()) return null
        return list.average()
    }

    // $formatNumber(value, picture [, options])
    private fun fnFormatNumber(args: List<Any?>): Any? {
        val n = requireNumber(args, 0, "formatNumber") ?: return null
        val picture = (args.getOrNull(1) as? String) ?: throw JSONataException.T0410("formatNumber", 2)
        // Basic XSLT 3.0 picture string support
        return formatNumber(n, picture)
    }

    // $formatBase(value, base)
    private fun fnFormatBase(args: List<Any?>): Any? {
        val n = requireNumber(args, 0, "formatBase") ?: return null
        val base = args.getOrNull(1)?.let { toNumber(it)?.toInt() } ?: 10
        if (base < 2 || base > 36) throw JSONataException.RuntimeError("Base must be between 2 and 36", "D3001")
        return n.toLong().toString(base)
    }

    // $formatInteger(value, picture)
    private fun fnFormatInteger(args: List<Any?>): Any? {
        val n = requireNumber(args, 0, "formatInteger") ?: return null
        val picture = (args.getOrNull(1) as? String) ?: throw JSONataException.T0410("formatInteger", 2)
        return formatInteger(n.toLong(), picture)
    }

    // $parseInteger(value, picture)
    private fun fnParseInteger(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        val picture = (args.getOrNull(1) as? String) ?: throw JSONataException.T0410("parseInteger", 2)
        return parseInteger(s, picture)
    }

    private fun requireNumber(args: List<Any?>, idx: Int, fn: String): Double? {
        val v = args.getOrNull(idx) ?: return null
        if (isNull(v)) return null
        return toNumber(v) ?: throw JSONataException.T0410(fn, idx + 1)
    }

    private fun toNumberList(v: Any?, fn: String): List<Double>? {
        return when (v) {
            is List<*> -> v.mapNotNull { toNumber(it) }
            else -> {
                val n = toNumber(v) ?: return null
                listOf(n)
            }
        }
    }
}

// ── Number formatting helpers ─────────────────────────────────────────────────

/** Basic XSLT 3.0 decimal format picture string implementation. */
internal fun formatNumber(n: Double, picture: String): String {
    // Split picture at ; for negative subpicture
    val parts = picture.split(";")
    val posPicture = parts[0]
    val negPicture = if (parts.size > 1) parts[1] else null

    val actualPicture = if (n < 0 && negPicture != null) negPicture else posPicture
    val absValue = if (n < 0 && negPicture == null) -n else if (n < 0) -n else n

    val prefix = actualPicture.takeWhile { it != '#' && it != '0' && it != '.' }
    val suffix = actualPicture.dropWhile { it != '.' && it != '#' && it != '0' }.dropWhile { it == '#' || it == '0' || it == '.' }
        .let { if (it.isEmpty()) "" else it }

    // Get the hash/zero part
    val intPart = actualPicture.dropWhile { it != '#' && it != '0' && it != '.' }
        .takeWhile { it != '.' }.filter { it == '#' || it == '0' || it == ',' }
    val fracPart = if ('.' in actualPicture) {
        actualPicture.substringAfter('.').takeWhile { it == '#' || it == '0' }
    } else ""

    val minIntDigits = intPart.count { it == '0' }
    val grouping = ',' in intPart
    val groupSize = if (grouping) {
        intPart.substringAfterLast(',').count { it == '#' || it == '0' }
    } else 0

    val minFracDigits = fracPart.count { it == '0' }
    val maxFracDigits = fracPart.length

    // Format integer
    val formatted = if (fracPart.isEmpty()) {
        absValue.toLong().toString()
    } else {
        // Format with decimal places
        val factor = Math.pow(10.0, maxFracDigits.toDouble())
        val rounded = Math.round(absValue * factor).toDouble() / factor
        val full = "%.${maxFracDigits}f".format(rounded)
        full
    }

    val dotIdx = formatted.indexOf('.')
    var intStr = if (dotIdx >= 0) formatted.substring(0, dotIdx) else formatted
    val fracStr = if (dotIdx >= 0) formatted.substring(dotIdx + 1) else ""

    // Pad integer part
    while (intStr.length < minIntDigits) intStr = "0$intStr"

    // Insert grouping separators
    if (grouping && groupSize > 0) {
        val sb = StringBuilder()
        for ((i, c) in intStr.reversed().withIndex()) {
            if (i > 0 && i % groupSize == 0) sb.append(',')
            sb.append(c)
        }
        intStr = sb.reverse().toString()
    }

    // Build fraction
    val fracResult = if (maxFracDigits > 0) {
        val fr = fracStr.padEnd(minFracDigits, '0').take(maxFracDigits).trimEnd('0').padEnd(minFracDigits, '0')
        if (fr.isNotEmpty() || minFracDigits > 0) ".$fr" else ""
    } else ""

    val sign = if (n < 0 && negPicture == null) "-" else ""
    return "$sign$prefix$intStr$fracResult$suffix"
}

/** Format an integer using an XSLT-style picture (e.g. "001", "i", "I", "w", "W"). */
internal fun formatInteger(n: Long, picture: String): String {
    return when (picture.lowercase()) {
        "i"  -> toRoman(n.toInt()).lowercase()
        "I"  -> toRoman(n.toInt())
        "w"  -> toWords(n).lowercase()
        "W"  -> toWords(n).uppercase()
        "Ww" -> toWords(n).let { it[0].uppercaseChar() + it.substring(1) }
        else -> {
            val zeros = picture.count { it == '0' }
            n.toString().padStart(zeros, '0')
        }
    }
}

/** Parse an integer using a picture string. */
internal fun parseInteger(s: String, picture: String): Double? {
    return when (picture.lowercase()) {
        "i"  -> fromRoman(s.uppercase())?.toDouble()
        else -> s.toLongOrNull()?.toDouble()
    }
}

private fun toRoman(n: Int): String {
    if (n <= 0) return ""
    val vals = listOf(1000,900,500,400,100,90,50,40,10,9,5,4,1)
    val syms = listOf("M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I")
    val sb = StringBuilder()
    var rem = n
    for ((v, s) in vals.zip(syms)) {
        while (rem >= v) { sb.append(s); rem -= v }
    }
    return sb.toString()
}

private fun fromRoman(s: String): Int? {
    val map = mapOf('I' to 1,'V' to 5,'X' to 10,'L' to 50,'C' to 100,'D' to 500,'M' to 1000)
    var total = 0
    var prev = 0
    for (c in s.reversed()) {
        val v = map[c] ?: return null
        if (v < prev) total -= v else total += v
        prev = v
    }
    return total
}

private val ones = listOf("","one","two","three","four","five","six","seven","eight","nine",
    "ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen")
private val tens = listOf("","","twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety")

private fun toWords(n: Long): String {
    if (n == 0L) return "zero"
    if (n < 0) return "minus ${toWords(-n)}"
    return buildWords(n)
}

private fun buildWords(n: Long): String = when {
    n < 20    -> ones[n.toInt()]
    n < 100   -> tens[(n / 10).toInt()] + if (n % 10 != 0L) "-${ones[(n % 10).toInt()]}" else ""
    n < 1000  -> "${ones[(n / 100).toInt()]} hundred" + if (n % 100 != 0L) " and ${buildWords(n % 100)}" else ""
    n < 1_000_000 -> "${buildWords(n / 1000)} thousand" + if (n % 1000 != 0L) " ${buildWords(n % 1000)}" else ""
    n < 1_000_000_000 -> "${buildWords(n / 1_000_000)} million" + if (n % 1_000_000 != 0L) " ${buildWords(n % 1_000_000)}" else ""
    else -> "${buildWords(n / 1_000_000_000)} billion" + if (n % 1_000_000_000 != 0L) " ${buildWords(n % 1_000_000_000)}" else ""
}
