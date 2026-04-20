package com.furyform.knata.functions

import com.furyform.knata.JSONataException
import com.furyform.knata.evaluator.*

/**
 * JSONata boolean and type-checking standard library functions.
 */
object BooleanFunctions {
    fun register(env: Environment) {
        env.fn("boolean") { args, _, _ -> fnBoolean(args) }
        env.fn("not")     { args, _, _ -> fnNot(args) }
        env.fn("exists")  { args, _, _ -> fnExists(args) }
    }

    // $boolean(arg)
    private fun fnBoolean(args: List<Any?>): Any? {
        val v = args.getOrNull(0) ?: return false
        if (isNull(v)) return false
        return isTruthy(v)
    }

    // $not(arg)
    private fun fnNot(args: List<Any?>): Any? {
        val v = args.getOrNull(0)
        return !isTruthy(v)
    }

    // $exists(arg)
    private fun fnExists(args: List<Any?>): Boolean {
        val v = args.getOrNull(0)
        return v != null  // undefined → false, everything else → true (even JsonNull)
    }
}
