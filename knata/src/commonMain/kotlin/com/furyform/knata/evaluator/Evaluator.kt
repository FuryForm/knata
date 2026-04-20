package com.furyform.knata.evaluator

import com.furyform.knata.JSONataException
import com.furyform.knata.parser.Node

/**
 * Core JSONata evaluator.
 *
 * [eval] dispatches over the AST node hierarchy.  Specialised modules
 * (binary ops, path navigation, function calls, etc.) are implemented
 * as extension functions in the same package and called from here.
 */
fun eval(node: Node, focus: Any?, env: Environment): Any? {
    env.callDepth++
    if (env.callDepth > Environment.MAX_CALL_DEPTH) {
        throw JSONataException.T1001("Stack overflow: call depth exceeded ${Environment.MAX_CALL_DEPTH}")
    }
    try {
        return evalNode(node, focus, env)
    } finally {
        env.callDepth--
    }
}

private fun evalNode(node: Node, focus: Any?, env: Environment): Any? = when (node) {
    // ── Literals ─────────────────────────────────────────────────────────────
    is Node.NumberLit  -> node.value
    is Node.StringLit  -> node.value
    is Node.BoolLit    -> node.value
    is Node.NullLit    -> JsonNull
    is Node.RegexLit   -> compileRegex(node.pattern, node.flags)
    is Node.Wildcard   -> evalWildcard(focus)
    is Node.Descendants -> evalDescendants(focus)

    // ── References ───────────────────────────────────────────────────────────
    is Node.Name       -> evalName(node.name, focus)
    is Node.Variable   -> when (node.name) {
        "" -> focus   // $ = current context/focus
        else -> evalVariable(node.name, env)
    }

    // ── Binary ───────────────────────────────────────────────────────────────
    is Node.Binary     -> evalBinary(node, focus, env)
    is Node.Unary      -> evalUnary(node, focus, env)

    // ── Path ─────────────────────────────────────────────────────────────────
    is Node.Path       -> evalPath(node, focus, env)
    is Node.Predicate  -> evalPredicate(node, focus, env)

    // ── Constructors ─────────────────────────────────────────────────────────
    is Node.ArrayConstructor  -> evalArrayConstructor(node, focus, env)
    is Node.ObjectConstructor -> evalObjectConstructor(node, focus, env)

    // ── Control flow ─────────────────────────────────────────────────────────
    is Node.Conditional -> evalConditional(node, focus, env)
    is Node.Block       -> evalBlock(node, focus, env)
    is Node.Assign      -> evalAssign(node, focus, env)

    // ── Functions ────────────────────────────────────────────────────────────
    is Node.Lambda             -> evalLambda(node, env)
    is Node.FunctionCall       -> evalFunctionCall(node, focus, env)
    is Node.PartialApplication -> evalPartialApplication(node, focus, env)
    is Node.Placeholder        -> null  // handled by partial application
    is Node.FunctionCompose    -> evalFunctionCompose(node, focus, env)
    is Node.Apply              -> evalApply(node, focus, env)

    // ── Transform ────────────────────────────────────────────────────────────
    is Node.Transform  -> evalTransform(node, focus, env)

    // ── Sort ─────────────────────────────────────────────────────────────────
    is Node.Sort       -> evalSort(node, focus, env)

    // ── Group ────────────────────────────────────────────────────────────────
    is Node.Group      -> evalGroup(node, focus, env)

    // ── Focus / Index binding ─────────────────────────────────────────────────
    is Node.FocusBind  -> evalFocusBind(node, focus, env)
    is Node.IndexBind  -> evalIndexBind(node, focus, env)

    // ── Coalescing ───────────────────────────────────────────────────────────
    is Node.Coalesce   -> evalCoalesce(node, focus, env)
    is Node.Default    -> evalDefault(node, focus, env)

    is Node.Parent     -> env.lookup("%.") // parent operator (set by path evaluator)
}

// ── Variable / name resolution ───────────────────────────────────────────────

internal fun evalVariable(name: String, env: Environment): Any? {
    // "$" alone = current focus (bound as "$" in the env by the caller)
    // "$$" = root document
    return env.lookup(name)
}

internal fun evalName(name: String, focus: Any?): Any? {
    val f = unwrapSingle(focus)
    return when (f) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            (f as Map<String, Any?>)[name]
        }
        is List<*>   -> {
            // Auto-map through arrays
            val seq = Sequence()
            for (item in f) {
                val v = evalName(name, item)
                if (v != null) seq.add(v)
            }
            collapseSequence(seq)
        }
        else -> null
    }
}

// ── Wildcard ─────────────────────────────────────────────────────────────────

internal fun evalWildcard(focus: Any?): Any? {
    val f = unwrapSingle(focus)
    return when (f) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val values = (f as Map<String, Any?>).values.filter { it != null }
            when (values.size) {
                0    -> null
                1    -> values[0]
                else -> values
            }
        }
        is List<*>   -> {
            val seq = Sequence()
            for (item in f) {
                val v = evalWildcard(item)
                if (v != null) seq.add(v)
            }
            collapseSequence(seq)
        }
        else -> null
    }
}

// ── Descendants (**) ─────────────────────────────────────────────────────────

internal fun evalDescendants(focus: Any?): Any? {
    val results = mutableListOf<Any?>()
    collectDescendants(focus, results)
    return when (results.size) {
        0    -> null
        1    -> results[0]
        else -> results
    }
}

private fun collectDescendants(value: Any?, out: MutableList<Any?>) {
    when (value) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            for (v in (value as Map<String, Any?>).values) {
                if (v != null) {
                    out.add(v)
                    collectDescendants(v, out)
                }
            }
        }
        is List<*> -> {
            for (item in value) {
                collectDescendants(item, out)
            }
        }
        else -> {}
    }
}

// ── Constructors ─────────────────────────────────────────────────────────────

internal fun evalArrayConstructor(node: Node.ArrayConstructor, focus: Any?, env: Environment): Any? {
    if (node.items.isEmpty()) return emptyList<Any?>()
    val result = mutableListOf<Any?>()
    for (item in node.items) {
        val v = eval(item, focus, env)
        // Ranges (..) evaluate to a List and should be spread, not nested.
        // Path/sequence results that are Lists are also spread per JSONata semantics.
        if (item is Node.Binary && item.op == ".." && v is List<*>) {
            result.addAll(v)
        } else if (v != null) {
            result.add(v)
        }
    }
    return result
}

internal fun evalObjectConstructor(node: Node.ObjectConstructor, focus: Any?, env: Environment): Any? {
    val map = OrderedMap()
    for ((keyNode, valueNode) in node.pairs) {
        val key = eval(keyNode, focus, env)
        val value = eval(valueNode, focus, env)
        if (key != null && !isNull(key)) {
            map[jsonataStringify(key)] = value
        }
    }
    return map
}

// ── Control flow ─────────────────────────────────────────────────────────────

internal fun evalConditional(node: Node.Conditional, focus: Any?, env: Environment): Any? {
    val cond = eval(node.condition, focus, env)
    return if (isTruthy(cond)) {
        eval(node.then, focus, env)
    } else {
        node.otherwise?.let { eval(it, focus, env) }
    }
}

internal fun evalBlock(node: Node.Block, focus: Any?, env: Environment): Any? {
    if (node.exprs.isEmpty()) return null
    val child = env.child()
    var result: Any? = null
    for (expr in node.exprs) {
        result = eval(expr, focus, child)
    }
    return result
}

internal fun evalAssign(node: Node.Assign, focus: Any?, env: Environment): Any? {
    val value = eval(node.value, focus, env)
    env.bind(node.name, value)
    return value
}

// ── Lambda ───────────────────────────────────────────────────────────────────

internal fun evalLambda(node: Node.Lambda, env: Environment): Any? {
    return LambdaFunction(
        params    = node.params,
        body      = node.body,
        closure   = env,
        signature = node.signature,
    )
}

// ── Coalescing / default ──────────────────────────────────────────────────────

internal fun evalCoalesce(node: Node.Coalesce, focus: Any?, env: Environment): Any? {
    val lhs = eval(node.lhs, focus, env)
    return if (lhs == null || isNull(lhs)) eval(node.rhs, focus, env) else lhs
}

internal fun evalDefault(node: Node.Default, focus: Any?, env: Environment): Any? {
    val lhs = eval(node.lhs, focus, env)
    return if (lhs == null || isNull(lhs)) eval(node.rhs, focus, env) else lhs
}

// ── Apply (value ~> fn) ───────────────────────────────────────────────────────

internal fun evalApply(node: Node.Apply, focus: Any?, env: Environment): Any? {
    val value = eval(node.lhs, focus, env)
    // value ~> fn(a, b)  ==  fn(value, a, b)
    // value ~> expr      ==  applyFunction(expr, [value])
    return when (val rhs = node.rhs) {
        is Node.FunctionCall -> {
            val fn = eval(rhs.fn, focus, env)
            val extraArgs = rhs.args.map { a -> eval(a, focus, env) }
            applyFunction(fn, listOf(value) + extraArgs, value, env)
        }
        else -> {
            val fn = eval(rhs, focus, env)
            applyFunction(fn, listOf(value), value, env)
        }
    }
}

// ── Function composition (fn1 ~> fn2) ────────────────────────────────────────

internal fun evalFunctionCompose(node: Node.FunctionCompose, focus: Any?, env: Environment): Any? {
    val fn1 = eval(node.lhs, focus, env)
    val fn2 = eval(node.rhs, focus, env)
    // Return a new lambda that applies fn1 then fn2
    val params = listOf("x")
    val composedBody = object {}  // placeholder — we use a BuiltinFunction
    return BuiltinFunction("composed") { args, _, callEnv ->
        val intermediate = applyFunction(fn1, args, args.firstOrNull(), callEnv)
        applyFunction(fn2, listOf(intermediate), intermediate, callEnv)
    }
}

// ── Unary ─────────────────────────────────────────────────────────────────────

internal fun evalUnary(node: Node.Unary, focus: Any?, env: Environment): Any? {
    val v = eval(node.expr, focus, env) ?: return null
    return when (node.op) {
        "-" -> {
            val n = toNumber(v) ?: throw JSONataException.T2001(node.pos, "-")
            -n
        }
        "!" -> !isTruthy(v)
        else -> v
    }
}

// ── Focus & Index binding ─────────────────────────────────────────────────────

internal fun evalFocusBind(node: Node.FocusBind, focus: Any?, env: Environment): Any? {
    val value = eval(node.expr, focus, env)
    val child = env.child()
    child.bind(node.varName, value)
    child.bind("$", value)
    return value
}

internal fun evalIndexBind(node: Node.IndexBind, focus: Any?, env: Environment): Any? {
    // Index binding is handled inside array iteration in the path evaluator
    // Here we just evaluate and return the expression value
    return eval(node.expr, focus, env)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Convert a value to its JSONata string representation (for object keys). */
internal fun jsonataStringify(v: Any?): String = when {
    v == null      -> ""
    isNull(v)      -> "null"
    v is String    -> v
    v is Double    -> if (v == kotlin.math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
    v is Boolean   -> v.toString()
    else           -> v.toString()
}

/** Unwrap a single-element list (JSONata singleton rule). */
internal fun unwrapSingle(v: Any?): Any? = when {
    v is List<*> && v.size == 1 -> v[0]
    else -> v
}

/** JSONata truthiness rules. */
internal fun isTruthy(v: Any?): Boolean = when {
    v == null   -> false
    isNull(v)   -> false
    v is Boolean -> v
    v is Double  -> v != 0.0 && !v.isNaN()
    v is String  -> v.isNotEmpty()
    v is List<*> -> v.isNotEmpty()
    v is Map<*, *> -> v.isNotEmpty()
    else -> true
}

/** Convert a value to Double, or null if not numeric. */
internal fun toNumber(v: Any?): Double? = when {
    v == null   -> null
    isNull(v)   -> null
    v is Double -> v
    v is Int    -> v.toDouble()
    v is Long   -> v.toDouble()
    v is Number -> v.toDouble()
    v is String -> v.trim().toDoubleOrNull()
    v is Boolean -> if (v) 1.0 else 0.0
    else -> null
}

/** Compile a regex pattern with JSONata flags. */
internal fun compileRegex(pattern: String, flags: String): Regex {
    val opts = mutableSetOf<RegexOption>()
    if ('i' in flags) opts += RegexOption.IGNORE_CASE
    if ('m' in flags) opts += RegexOption.MULTILINE
    if ('s' in flags) opts += RegexOption.DOT_MATCHES_ALL
    return Regex(pattern, opts)
}

/** Deep-clone a value (used in transforms). */
internal fun deepClone(v: Any?): Any? = when {
    v == null      -> null
    isNull(v)      -> JsonNull
    v is String    -> v
    v is Double    -> v
    v is Boolean   -> v
    v is List<*>   -> v.map { deepClone(it) }
    v is OrderedMap -> OrderedMap.from(v.mapValues { (_, mv) -> deepClone(mv) })
    v is Map<*, *>  -> {
        @Suppress("UNCHECKED_CAST")
        OrderedMap.from((v as Map<String, Any?>).mapValues { (_, mv) -> deepClone(mv) })
    }
    else -> v
}
