package com.furyform.knata.functions

import com.furyform.knata.JSONataException
import com.furyform.knata.evaluator.*

/**
 * JSONata string standard library functions.
 */
object StringFunctions {
    fun register(env: Environment) {
        env.fn("string")         { args, _, _ -> fnString(args) }
        env.fn("length")         { args, _, _ -> fnLength(args) }
        env.fn("substring")      { args, _, _ -> fnSubstring(args) }
        env.fn("substringBefore") { args, _, _ -> fnSubstringBefore(args) }
        env.fn("substringAfter")  { args, _, _ -> fnSubstringAfter(args) }
        env.fn("uppercase")      { args, _, _ -> fnUppercase(args) }
        env.fn("lowercase")      { args, _, _ -> fnLowercase(args) }
        env.fn("trim")           { args, _, _ -> fnTrim(args) }
        env.fn("pad")            { args, _, _ -> fnPad(args) }
        env.fn("contains")       { args, _, _ -> fnContains(args) }
        env.fn("split")          { args, _, _ -> fnSplit(args) }
        env.fn("join")           { args, _, _ -> fnJoin(args) }
        env.fn("match")          { args, _, _ -> fnMatch(args) }
        env.fn("replace")        { args, _, e -> fnReplace(args, e) }
    }

    // $string(arg [, prettify])
    private fun fnString(args: List<Any?>): Any? {
        if (args.isEmpty()) return null
        val v = args[0]
        if (v == null) return null
        return valueToJsonataString(v)
    }

    // $length(str)
    private fun fnLength(args: List<Any?>): Any? {
        val s = args.getOrNull(0) ?: return null
        if (s !is String) throw JSONataException.T0410("length", 1)
        // Unicode-aware: count codepoints
        return s.codePointCount().toDouble()
    }

    // $substring(str, start [, length])
    private fun fnSubstring(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) ?: return null) as? String
            ?: throw JSONataException.T0410("substring", 1)
        val codepoints = s.toCodePoints()
        val len = codepoints.size
        var start = (args.getOrNull(1) ?: return null).let {
            toNumber(it)?.toInt() ?: throw JSONataException.T0410("substring", 2)
        }
        if (start < 0) start = maxOf(0, len + start)
        if (start >= len) return ""
        val lengthArg = args.getOrNull(2)
        val end = if (lengthArg != null) {
            val l = toNumber(lengthArg)?.toInt() ?: throw JSONataException.T0410("substring", 3)
            if (l < 0) return ""
            minOf(start + l, len)
        } else len
        return codepoints.subList(start, end).toJavaString()
    }

    // $substringBefore(str, chars)
    private fun fnSubstringBefore(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) ?: return null) as? String ?: throw JSONataException.T0410("substringBefore", 1)
        val c = (args.getOrNull(1) ?: return null) as? String ?: throw JSONataException.T0410("substringBefore", 2)
        val idx = s.indexOf(c)
        return if (idx < 0) s else s.substring(0, idx)
    }

    // $substringAfter(str, chars)
    private fun fnSubstringAfter(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) ?: return null) as? String ?: throw JSONataException.T0410("substringAfter", 1)
        val c = (args.getOrNull(1) ?: return null) as? String ?: throw JSONataException.T0410("substringAfter", 2)
        val idx = s.indexOf(c)
        return if (idx < 0) "" else s.substring(idx + c.length)
    }

    // $uppercase(str)
    private fun fnUppercase(args: List<Any?>): Any? {
        val s = args.getOrNull(0) ?: return null
        if (s !is String) throw JSONataException.T0410("uppercase", 1)
        return s.uppercase()
    }

    // $lowercase(str)
    private fun fnLowercase(args: List<Any?>): Any? {
        val s = args.getOrNull(0) ?: return null
        if (s !is String) throw JSONataException.T0410("lowercase", 1)
        return s.lowercase()
    }

    // $trim(str)
    private fun fnTrim(args: List<Any?>): Any? {
        val s = args.getOrNull(0) ?: return null
        if (s !is String) throw JSONataException.T0410("trim", 1)
        return s.trim()
    }

    // $pad(str, width [, char])
    private fun fnPad(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) ?: return null) as? String ?: throw JSONataException.T0410("pad", 1)
        val width = toNumber(args.getOrNull(1) ?: return null)?.toInt() ?: throw JSONataException.T0410("pad", 2)
        val padChar = (args.getOrNull(2) as? String)?.firstOrNull() ?: ' '
        val sLen = s.codePointCount()
        return when {
            width == 0 -> s
            width > 0 -> {
                val needed = width - sLen
                if (needed <= 0) s else s + padChar.toString().repeat(needed)
            }
            else -> {
                val needed = -width - sLen
                if (needed <= 0) s else padChar.toString().repeat(needed) + s
            }
        }
    }

    // $contains(str, pattern)
    private fun fnContains(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) ?: return null) as? String ?: throw JSONataException.T0410("contains", 1)
        val pattern = args.getOrNull(1) ?: throw JSONataException.T0410("contains", 2)
        return when (pattern) {
            is String -> s.contains(pattern)
            is Regex  -> pattern.containsMatchIn(s)
            else -> throw JSONataException.T0410("contains", 2)
        }
    }

    // $split(str, separator [, limit])
    private fun fnSplit(args: List<Any?>): Any? {
        val s = args.getOrNull(0) ?: return null
        if (s !is String) return null
        val separator = args.getOrNull(1) ?: return null
        val limit = args.getOrNull(2)?.let { toNumber(it)?.toInt() }

        val results: List<String> = when (separator) {
            is String -> {
                if (separator.isEmpty()) {
                    s.toCodePoints().map { intArrayOf(it).toJavaString() }
                } else {
                    s.split(separator)
                }
            }
            is Regex -> {
                separator.split(s)
            }
            else -> throw JSONataException.T0410("split", 2)
        }

        val limited = if (limit != null && limit < results.size) results.take(limit) else results
        return limited
    }

    // $join(array [, separator])
    private fun fnJoin(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        val sep = (args.getOrNull(1) as? String) ?: ""
        val list = when (arr) {
            is List<*> -> arr.map { it as? String ?: valueToJsonataString(it) }
            is String  -> return arr
            else -> return null
        }
        return list.joinToString(sep)
    }

    // $match(str, pattern [, limit])
    private fun fnMatch(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) ?: return null) as? String ?: throw JSONataException.T0410("match", 1)
        val pattern = args.getOrNull(1) ?: throw JSONataException.T0410("match", 2)
        val limit = args.getOrNull(2)?.let { toNumber(it)?.toInt() }
        val regex = when (pattern) {
            is Regex  -> pattern
            is String -> Regex(Regex.escape(pattern))
            else -> throw JSONataException.T0410("match", 2)
        }

        val results = mutableListOf<Any?>()
        var count = 0
        for (mr in regex.findAll(s)) {
            if (limit != null && count >= limit) break
            val groups = mr.groupValues.drop(1)  // skip full match
            val matchObj = OrderedMap()
            matchObj["match"] = mr.value
            matchObj["index"] = mr.range.first.toDouble()
            matchObj["groups"] = if (groups.isNotEmpty()) groups else emptyList<String>()
            results.add(matchObj)
            count++
        }
        return when (results.size) {
            0    -> null
            1    -> results[0]
            else -> results
        }
    }

    // $replace(str, pattern, replacement [, limit])
    private fun fnReplace(args: List<Any?>, env: Environment): Any? {
        val s = (args.getOrNull(0) ?: return null) as? String ?: throw JSONataException.T0410("replace", 1)
        val pattern = args.getOrNull(1) ?: throw JSONataException.T0410("replace", 2)
        val replacement = args.getOrNull(2) ?: throw JSONataException.T0410("replace", 3)
        val limit = args.getOrNull(3)?.let { toNumber(it)?.toInt() }

        val regex = when (pattern) {
            is Regex  -> pattern
            is String -> Regex(Regex.escape(pattern))
            else -> throw JSONataException.T0410("replace", 2)
        }

        return when (replacement) {
            is String -> {
                if (limit == null) {
                    regex.replace(s, replacement.toKotlinReplacement())
                } else {
                    replaceN(s, regex, replacement.toKotlinReplacement(), limit)
                }
            }
            is BuiltinFunction, is LambdaFunction, is PartiallyApplied -> {
                replaceWithFn(s, regex, replacement, limit, env)
            }
            else -> throw JSONataException.T0410("replace", 3)
        }
    }

    private fun replaceN(s: String, regex: Regex, replacement: String, limit: Int): String {
        if (limit <= 0) return s
        val sb = StringBuilder()
        var count = 0
        var lastEnd = 0
        for (mr in regex.findAll(s)) {
            if (count >= limit) break
            sb.append(s, lastEnd, mr.range.first)
            sb.append(mr.value.replace(regex, replacement))
            lastEnd = mr.range.last + 1
            count++
        }
        sb.append(s, lastEnd, s.length)
        return sb.toString()
    }

    private fun replaceWithFn(s: String, regex: Regex, fn: Any?, limit: Int?, env: Environment): String {
        val sb = StringBuilder()
        var count = 0
        var lastEnd = 0
        for (mr in regex.findAll(s)) {
            if (limit != null && count >= limit) break
            sb.append(s, lastEnd, mr.range.first)
            val groups = mr.groupValues.drop(1)
            val matchObj = OrderedMap()
            matchObj["match"] = mr.value
            matchObj["index"] = mr.range.first.toDouble()
            matchObj["groups"] = groups
            val result = applyFunction(fn, listOf(matchObj), matchObj, env)
            sb.append(result?.toString() ?: "")
            lastEnd = mr.range.last + 1
            count++
        }
        sb.append(s, lastEnd, s.length)
        return sb.toString()
    }
}

// ── String serialization helpers ─────────────────────────────────────────────

/** Convert any JSONata value to its string representation ($string semantics). */
internal fun valueToJsonataString(v: Any?): String = when {
    v == null      -> ""
    isNull(v)      -> "null"
    v is String    -> v
    v is Double    -> numberToString(v)
    v is Boolean   -> v.toString()
    v is List<*>   -> "[${v.joinToString(",") { valueToJsonataString(it) }}]"
    v is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        val m = v as Map<String, Any?>
        "{${m.entries.joinToString(",") { (k, mv) -> "\"${escapeJson(k)}\":${valueToJsonataString(mv)}" }}}"
    }
    v is Regex     -> v.pattern
    else -> v.toString()
}

private fun escapeJson(s: String): String = s
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

/** Convert a JSONata replacement string pattern to Kotlin replacement string. */
private fun String.toKotlinReplacement(): String =
    replace("$0", "\\$0").replace("$", "\\$")  // basic passthrough

// ── Unicode-aware string helpers ──────────────────────────────────────────────

/** Count Unicode codepoints (not UTF-16 chars). */
internal fun String.codePointCount(): Int {
    var count = 0
    var i = 0
    while (i < length) {
        val cp = this.codePointAt(i)
        count++
        i += Character.charCount(cp)
    }
    return count
}

/** Convert string to list of Unicode codepoints. */
internal fun String.toCodePoints(): List<Int> {
    val result = mutableListOf<Int>()
    var i = 0
    while (i < length) {
        val cp = this.codePointAt(i)
        result.add(cp)
        i += Character.charCount(cp)
    }
    return result
}

/** Convert list of codepoints back to String. */
internal fun List<Int>.toJavaString(): String {
    val sb = StringBuilder()
    for (cp in this) sb.appendCodePoint(cp)
    return sb.toString()
}

internal fun IntArray.toJavaString(): String {
    val sb = StringBuilder()
    for (cp in this) sb.appendCodePoint(cp)
    return sb.toString()
}
