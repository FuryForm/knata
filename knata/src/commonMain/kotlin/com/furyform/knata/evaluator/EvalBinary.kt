package com.furyform.knata.evaluator

import com.furyform.knata.JSONataException
import com.furyform.knata.parser.Node
import kotlin.math.floor

/**
 * Evaluates all binary operator nodes.
 */
internal fun evalBinary(node: Node.Binary, focus: Any?, env: Environment): Any? {
    // Short-circuit logical operators
    if (node.op == "and") {
        val lhsV = eval(node.lhs, focus, env)
        return if (!isTruthy(lhsV)) false else isTruthy(eval(node.rhs, focus, env))
    }
    if (node.op == "or") {
        val lhsV = eval(node.lhs, focus, env)
        return if (isTruthy(lhsV)) true else isTruthy(eval(node.rhs, focus, env))
    }

    val lhs = eval(node.lhs, focus, env)
    val rhs = eval(node.rhs, focus, env)

    return when (node.op) {
        "+"  -> numericOp(lhs, rhs, node.op, node.pos) { a, b -> a + b }
        "-"  -> numericOp(lhs, rhs, node.op, node.pos) { a, b -> a - b }
        "*"  -> numericOp(lhs, rhs, node.op, node.pos) { a, b -> a * b }
        "/"  -> {
            if (lhs == null || rhs == null) return null
            val a = toNumber(lhs) ?: throw JSONataException.T2001(node.pos, node.op)
            val b = toNumber(rhs) ?: throw JSONataException.T2002(node.pos, node.op)
            if (b == 0.0) throw JSONataException.RuntimeError("Division by zero", "D3001")
            a / b
        }
        "%"  -> numericOp(lhs, rhs, node.op, node.pos) { a, b ->
            if (b == 0.0) throw JSONataException.RuntimeError("Division by zero", "D3001")
            a % b
        }
        "&"  -> stringConcat(lhs, rhs, node.pos)
        "="  -> deepEqual(lhs, rhs)
        "!=" -> !deepEqual(lhs, rhs)
        "<"  -> compareValues(lhs, rhs, node.op, node.pos) < 0
        "<=" -> compareValues(lhs, rhs, node.op, node.pos) <= 0
        ">"  -> compareValues(lhs, rhs, node.op, node.pos) > 0
        ">=" -> compareValues(lhs, rhs, node.op, node.pos) >= 0
        "in" -> evalIn(lhs, rhs)
        ".." -> evalRange(lhs, rhs, node.pos)
        else -> throw JSONataException.RuntimeError("Unknown binary operator: ${node.op}")
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun numericOp(lhs: Any?, rhs: Any?, op: String, pos: Int, fn: (Double, Double) -> Double): Double? {
    // JSONata spec: arithmetic on undefined operand → undefined (null), not an error
    if (lhs == null || rhs == null) return null
    val a = toNumber(lhs) ?: throw JSONataException.T2001(pos, op)
    val b = toNumber(rhs) ?: throw JSONataException.T2002(pos, op)
    return fn(a, b)
}

private fun requireNum(v: Any?, side: String, op: String, pos: Int): Double {
    if (v == null) throw JSONataException.RuntimeError("Value is undefined for $side of '$op'", "T2001")
    return toNumber(v) ?: throw if (side == "left") JSONataException.T2001(pos, op) else JSONataException.T2002(pos, op)
}

private fun stringConcat(lhs: Any?, rhs: Any?, pos: Int): String {
    if (lhs == null) return ""
    if (rhs == null) return ""
    val l = when {
        lhs is String  -> lhs
        lhs is Double  -> numberToString(lhs)
        lhs is Boolean -> lhs.toString()
        isNull(lhs)    -> ""
        else -> throw JSONataException.T2003(pos)
    }
    val r = when {
        rhs is String  -> rhs
        rhs is Double  -> numberToString(rhs)
        rhs is Boolean -> rhs.toString()
        isNull(rhs)    -> ""
        else -> throw JSONataException.T2004(pos)
    }
    return l + r
}

/** Compare two values. Returns negative/zero/positive. Throws on incomparable types. */
internal fun compareValues(lhs: Any?, rhs: Any?, op: String, pos: Int): Int {
    if (lhs == null || rhs == null) {
        throw JSONataException.RuntimeError("Cannot compare undefined values with '$op'", "T2001")
    }
    if (lhs is Double && rhs is Double) return lhs.compareTo(rhs)
    if (lhs is String && rhs is String) return lhs.compareTo(rhs)
    val ln = toNumber(lhs)
    val rn = toNumber(rhs)
    if (ln != null && rn != null) return ln.compareTo(rn)
    val ls = lhs as? String ?: lhs.toString()
    val rs = rhs as? String ?: rhs.toString()
    return ls.compareTo(rs)
}

/** JSONata `in` operator: lhs in rhs */
private fun evalIn(lhs: Any?, rhs: Any?): Boolean {
    if (rhs == null) return false
    val list = when (rhs) {
        is List<*> -> rhs
        else -> listOf(rhs)
    }
    return list.any { deepEqual(lhs, it) }
}

/** Range operator: `n..m` → list of integers */
private fun evalRange(lhs: Any?, rhs: Any?, pos: Int): Any? {
    if (lhs == null || rhs == null) return null
    val l = toNumber(lhs)?.let {
        if (it != floor(it)) throw JSONataException.RuntimeError("Range start must be an integer", "T2003")
        it.toInt()
    } ?: throw JSONataException.RuntimeError("Range start must be a number", "T2003")
    val r = toNumber(rhs)?.let {
        if (it != floor(it)) throw JSONataException.RuntimeError("Range end must be an integer", "T2003")
        it.toInt()
    } ?: throw JSONataException.RuntimeError("Range end must be a number", "T2003")
    if (l > r) return null  // JSONata: undefined for empty range
    if (r - l > 10_000_000) throw JSONataException.RuntimeError("Range too large", "D3137")
    return (l..r).map { it.toDouble() }
}

/** Format a Double as JSONata would (integer if whole, else float). */
internal fun numberToString(v: Double): String {
    if (v.isInfinite()) throw JSONataException.RuntimeError("Number is not finite", "D3001")
    if (v.isNaN()) throw JSONataException.RuntimeError("Number is NaN", "D3001")
    return if (v == floor(v) && kotlin.math.abs(v) < 1e21) {
        v.toLong().toString()
    } else {
        v.toString()
    }
}
