package com.furyform.knata.evaluator

import com.furyform.knata.JSONataException
import com.furyform.knata.parser.Node

/**
 * Function call and partial application evaluation.
 */
internal fun evalFunctionCall(node: Node.FunctionCall, focus: Any?, env: Environment): Any? {
    val fn = eval(node.fn, focus, env)
    val args = node.args.map { eval(it, focus, env) }
    return applyFunction(fn, args, focus, env)
}

internal fun evalPartialApplication(node: Node.PartialApplication, focus: Any?, env: Environment): Any? {
    val fn = eval(node.fn, focus, env)
    val filledArgs = node.args.map { arg ->
        if (arg == null) null else eval(arg, focus, env)
    }
    return PartiallyApplied(fn, filledArgs)
}

/**
 * Core function dispatch. Handles built-ins, lambdas, and partial applications.
 */
fun applyFunction(fn: Any?, args: List<Any?>, focus: Any?, env: Environment): Any? {
    return when (fn) {
        is BuiltinFunction -> {
            try {
                fn.fn(args, focus, env)
            } catch (e: JSONataException) {
                throw e
            }
        }

        is LambdaFunction -> applyLambda(fn, args, env)

        is PartiallyApplied -> {
            // Fill in placeholder slots with caller-supplied args
            val suppliedIter = args.iterator()
            val fullArgs = fn.filledArgs.map { slot ->
                if (slot == null && suppliedIter.hasNext()) suppliedIter.next()
                else slot
            }.toMutableList()
            // Append any leftover supplied args
            while (suppliedIter.hasNext()) fullArgs.add(suppliedIter.next())
            applyFunction(fn.fn, fullArgs, focus, env)
        }

        null -> throw JSONataException.T0006(0)

        else -> throw JSONataException.T0006(0)
    }
}

private fun applyLambda(fn: LambdaFunction, args: List<Any?>, callEnv: Environment): Any? {
    val frame = fn.closure.child()
    frame.callDepth = callEnv.callDepth + 1
    if (frame.callDepth > Environment.MAX_CALL_DEPTH) {
        throw JSONataException.T1001("Stack overflow")
    }

    for ((i, param) in fn.params.withIndex()) {
        frame.bind(param, if (i < args.size) args[i] else null)
    }

    return eval(fn.body, args.firstOrNull(), frame)
}
