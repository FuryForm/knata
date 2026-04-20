package com.furyform.knata.evaluator

import com.furyform.knata.JSONataException
import com.furyform.knata.parser.Node

/**
 * Path navigation: evaluates [Node.Path] and [Node.Predicate].
 *
 * JSONata path semantics:
 *  1. Each step is evaluated against the result of the previous step.
 *  2. When the current value is an array, the step auto-maps across elements.
 *  3. The result is flattened one level.
 */
internal fun evalPath(node: Node.Path, focus: Any?, env: Environment): Any? {
    var current: Any? = focus

    for (step in node.steps) {
        current = evalStep(step, current, env)
        if (current == null) return null
    }

    return current
}

/**
 * Evaluate a single path step against [focus].
 * Auto-maps through arrays and collapses the result.
 */
internal fun evalStep(step: Node, focus: Any?, env: Environment): Any? {
    // If focus is an array, map the step across each element
    val f = focus
    return when {
        f is List<*> && step !is Node.Predicate && step !is Node.ArrayConstructor -> {
            val seq = Sequence()
            for ((idx, item) in f.withIndex()) {
                val child = env.child()
                child.bind("$", item)
                if (step is Node.IndexBind) {
                    child.bind(step.varName, idx.toDouble())
                }
                val v = evalStepSingle(step, item, child)
                if (v != null) seq.add(flatten1(v))
            }
            collapseSequence(seq)
        }
        else -> evalStepSingle(step, f, env)
    }
}

private fun evalStepSingle(step: Node, focus: Any?, env: Environment): Any? {
    return when (step) {
        is Node.FocusBind -> {
            val v = evalStep(step.expr, focus, env)
            val child = env.child()
            child.bind(step.varName, v)
            child.bind("$", v)
            v
        }
        is Node.IndexBind -> {
            // Index binding is resolved in the array loop above
            evalStep(step.expr, focus, env)
        }
        else -> eval(step, focus, env)
    }
}

/**
 * Flatten one level of nesting (JSONata auto-flattens path results).
 */
private fun flatten1(v: Any?): Any? {
    if (v !is List<*>) return v
    if (v.isEmpty()) return null
    val flat = mutableListOf<Any?>()
    for (item in v) {
        if (item is List<*>) flat.addAll(item) else flat.add(item)
    }
    return if (flat.isEmpty()) null
    else if (flat.size == 1) flat[0]
    else flat
}

/**
 * Evaluate a predicate expression: `expr[predicate]`.
 *
 * - Numeric predicate → index access (negative = from end)
 * - Boolean predicate → filter
 * - Undefined predicate on empty [] → keep/flatten enclosing array
 */
internal fun evalPredicate(node: Node.Predicate, focus: Any?, env: Environment): Any? {
    val subject = eval(node.expr, focus, env)

    // Empty array predicate: expr[] just flattens singleton
    if (node.predicate is Node.ArrayConstructor && node.predicate.items.isEmpty()) {
        return when (subject) {
            null -> null
            is List<*> -> subject
            else -> listOf(subject)
        }
    }

    val items: List<Any?> = when (subject) {
        null -> return null
        is List<*> -> subject
        else -> listOf(subject)
    }

    // Check if predicate is a numeric index
    val pred = node.predicate
    val predVal = eval(pred, items, env)
    val numIdx = toNumber(predVal)

    if (numIdx != null && predVal !is Boolean) {
        val idx = numIdx.toInt()
        val realIdx = if (idx < 0) items.size + idx else idx
        return if (realIdx in items.indices) items[realIdx] else null
    }

    // Boolean predicate — filter
    val results = mutableListOf<Any?>()
    for ((idx, item) in items.withIndex()) {
        val child = env.child()
        child.bind("$", item)
        // Make index available as a number for numeric predicates
        val pv = eval(pred, item, child)
        val pNum = if (pv is Double || pv is Int || pv is Long) toNumber(pv) else null
        if (pNum != null) {
            val realIdx2 = if (pNum.toInt() < 0) items.size + pNum.toInt() else pNum.toInt()
            if (realIdx2 == idx) results.add(item)
        } else if (isTruthy(pv)) {
            results.add(item)
        }
    }
    return when (results.size) {
        0    -> null
        1    -> results[0]
        else -> results
    }
}
