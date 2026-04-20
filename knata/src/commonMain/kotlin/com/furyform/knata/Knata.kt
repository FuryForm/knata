package com.furyform.knata

import com.furyform.knata.evaluator.*
import com.furyform.knata.functions.registerAll
import com.furyform.knata.parser.Parser

/**
 * Main entry point for the knata JSONata 2.x engine.
 *
 * ```kotlin
 * val result = Knata.evaluate("$sum(Account.Order.Product.Price)", data)
 * ```
 *
 * @see Expression for a pre-compiled expression that can be evaluated multiple times.
 */
object Knata {

    /**
     * Parse and evaluate [expression] against [data].
     *
     * @param expression  JSONata expression string
     * @param data        Input document as Kotlin structure (Map, List, String, Double, Boolean, null).
     *                    Pass [JsonNull] for JSON `null`.
     * @param bindings    Optional extra variable bindings (`\$name` → value).
     * @return            Result value or `null` for JSONata *undefined*.
     */
    fun evaluate(
        expression: String,
        data: Any? = null,
        bindings: Map<String, Any?> = emptyMap(),
    ): Any? {
        val env = buildEnv(bindings, data)
        val ast = Parser(expression).parse()
        return eval(ast, data, env)
    }

    /**
     * Pre-compile [expression] into a reusable [Expression] object.
     */
    fun compile(
        expression: String,
        bindings: Map<String, Any?> = emptyMap(),
    ): Expression {
        val ast = Parser(expression).parse()
        return Expression(ast, buildEnv(bindings))
    }

    private fun buildEnv(bindings: Map<String, Any?>, data: Any? = null): Environment {
        val env = Environment()
        registerAll(env)
        // $$ = root document
        env.bind("\$", data)
        for ((name, value) in bindings) {
            val key = if (name.startsWith("\$")) name.drop(1) else name
            env.bind(key, value)
        }
        return env
    }
}

/**
 * A compiled JSONata expression.
 *
 * Instances are created via [Knata.compile] and re-evaluated against different
 * data documents without re-parsing.
 */
class Expression internal constructor(
    private val ast: com.furyform.knata.parser.Node,
    private val env: Environment,
) {
    /**
     * Evaluate this expression against [data].
     */
    fun evaluate(data: Any? = null): Any? = eval(ast, data, env)

    /**
     * Register a custom function (e.g. `$myFn`) into this expression's scope.
     *
     * @param name    Function name without leading `\$` (e.g. `"myFn"`)
     * @param fn      Kotlin lambda: `(args: List<Any?>) -> Any?`
     */
    fun registerFunction(name: String, fn: (List<Any?>) -> Any?) {
        env.bind(name, BuiltinFunction(name) { args, _, _ -> fn(args) })
    }
}
