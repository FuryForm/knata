package com.furyform.knata.evaluator

/**
 * Evaluation environment (scope / frame).
 *
 * Bindings are looked up through the parent chain so nested
 * scopes (lambdas, blocks) see enclosing bindings.
 */
class Environment(val parent: Environment? = null) {
    private val bindings = mutableMapOf<String, Any?>()

    /**
     * Optional cancellation check function. Set by the caller (e.g. FuryJSON)
     * to periodically check if the evaluation should be cancelled. Checked
     * every ~8K iterations in array walk loops to prevent ANR on large docs.
     */
    var cancellationCheck: (() -> Boolean)? = parent?.cancellationCheck

    /** Look up a name, walking the parent chain. */
    fun lookup(name: String): Any? {
        return if (bindings.containsKey(name)) bindings[name]
        else parent?.lookup(name)
    }

    /** Bind a name in this frame. */
    fun bind(name: String, value: Any?) {
        bindings[name] = value
    }

    /** Create a child environment inheriting this one. */
    fun child(): Environment = Environment(this)

    /** Recursion / call depth limit */
    var callDepth: Int = parent?.callDepth ?: 0

    companion object {
        const val MAX_CALL_DEPTH = 1000
    }
}

/**
 * Represents a built-in (native Kotlin) function.
 * The function receives the argument list, the current focus value, and the current environment.
 */
typealias BuiltinFn = (args: List<Any?>, focus: Any?, env: Environment) -> Any?

/** Wraps a built-in function so it can live in the environment. */
data class BuiltinFunction(val name: String, val fn: BuiltinFn)

/**
 * A user-defined (lambda) function with captured environment.
 */
data class LambdaFunction(
    val params: List<String>,
    val body: com.furyform.knata.parser.Node,
    val closure: Environment,
    val signature: String? = null,
    val name: String? = null,
)

/**
 * A partially-applied function: a callable with some arguments pre-filled.
 * Placeholders are represented by null in [filledArgs].
 */
data class PartiallyApplied(
    val fn: Any?,           // BuiltinFunction or LambdaFunction
    val filledArgs: List<Any?>,  // null = placeholder slot
)
