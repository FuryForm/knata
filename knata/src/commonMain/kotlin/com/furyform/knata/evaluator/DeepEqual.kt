package com.furyform.knata.evaluator

/**
 * Deep equality following JSONata semantics.
 *
 * - null == null
 * - Sequences/lists are compared element-wise
 * - Objects are compared by key-value pairs (order-independent)
 */
fun deepEqual(a: Any?, b: Any?): Boolean {
    if (a === b) return true
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    if (isNull(a) && isNull(b)) return true
    if (isNull(a) || isNull(b)) return false

    // Numeric: compare as Double
    val an = toNumber(a)
    val bn = toNumber(b)
    if (an != null && bn != null && a !is String && b !is String) {
        return an == bn
    }

    // Both strings
    if (a is String && b is String) return a == b

    // Both booleans
    if (a is Boolean && b is Boolean) return a == b

    // Both lists
    if (a is List<*> && b is List<*>) {
        if (a.size != b.size) return false
        return a.zip(b).all { (x, y) -> deepEqual(x, y) }
    }

    // Both maps
    if (a is Map<*, *> && b is Map<*, *>) {
        if (a.size != b.size) return false
        for ((k, v) in a) {
            if (!b.containsKey(k)) return false
            if (!deepEqual(v, b[k])) return false
        }
        return true
    }

    return false
}
