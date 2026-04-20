package com.furyform.knata.evaluator

import com.furyform.knata.parser.Node

/**
 * Sort expression: `expr^(<term1, >term2, ...)`
 */
internal fun evalSort(node: Node.Sort, focus: Any?, env: Environment): Any? {
    val input = eval(node.expr, focus, env) ?: return null

    val items: List<Any?> = when (input) {
        is List<*> -> input
        else -> return input  // single value: nothing to sort
    }

    if (items.isEmpty()) return null

    val sorted = items.sortedWith { a, b ->
        for (term in node.terms) {
            val child = env.child()
            child.bind("$", a)
            val av = eval(term.expr, a, child)
            val child2 = env.child()
            child2.bind("$", b)
            val bv = eval(term.expr, b, child2)

            val cmp = compareForSort(av, bv)
            if (cmp != 0) return@sortedWith if (term.descending) -cmp else cmp
        }
        0
    }

    return sorted
}

private fun compareForSort(a: Any?, b: Any?): Int {
    if (a == null && b == null) return 0
    if (a == null) return -1
    if (b == null) return 1
    if (isNull(a) && isNull(b)) return 0
    if (isNull(a)) return -1
    if (isNull(b)) return 1
    if (a is Double && b is Double) return a.compareTo(b)
    if (a is String && b is String) return a.compareTo(b)
    val an = toNumber(a)
    val bn = toNumber(b)
    if (an != null && bn != null) return an.compareTo(bn)
    return a.toString().compareTo(b.toString())
}
