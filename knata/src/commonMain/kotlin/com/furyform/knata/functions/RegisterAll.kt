package com.furyform.knata.functions

import com.furyform.knata.evaluator.BuiltinFn
import com.furyform.knata.evaluator.BuiltinFunction
import com.furyform.knata.evaluator.Environment
import com.furyform.knata.evaluator.eval
import com.furyform.knata.parser.Parser

/**
 * Registers all JSONata standard library functions into [env].
 */
fun registerAll(env: Environment) {
    StringFunctions.register(env)
    NumericFunctions.register(env)
    ArrayFunctions.register(env)
    ObjectFunctions.register(env)
    BooleanFunctions.register(env)
    HofFunctions.register(env)
    DateTimeFunctions.register(env)
    EncodingFunctions.register(env)
    registerEval(env)
}

/** Register $eval — runs a JSONata expression string against an optional context. */
private fun registerEval(env: Environment) {
    env.fn("eval") { args, _, callEnv ->
        val expr = args.getOrNull(0) as? String ?: return@fn null
        val context = args.getOrNull(1)
        val ast = Parser(expr).parse()
        eval(ast, context, callEnv)
    }
}

/** Helper to bind a named builtin into an environment. */
internal fun Environment.fn(name: String, block: BuiltinFn) {
    bind(name, BuiltinFunction(name, block))
}
