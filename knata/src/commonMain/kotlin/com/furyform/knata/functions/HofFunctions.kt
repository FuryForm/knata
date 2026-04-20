package com.furyform.knata.functions

import com.furyform.knata.JSONataException
import com.furyform.knata.evaluator.*

/**
 * JSONata higher-order standard library functions: $map, $filter, $reduce, $single, $zip.
 */
object HofFunctions {
    fun register(env: Environment) {
        env.fn("map")    { args, _, e -> fnMap(args, e) }
        env.fn("filter") { args, _, e -> fnFilter(args, e) }
        env.fn("reduce") { args, _, e -> fnReduce(args, e) }
        env.fn("single") { args, _, e -> fnSingle(args, e) }
    }

    // $map(array, function)
    private fun fnMap(args: List<Any?>, env: Environment): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        val fn = args.getOrNull(1) ?: return null
        val list = when (arr) {
            is List<*> -> arr as List<Any?>
            else       -> listOf(arr)
        }
        val results = list.mapIndexed { idx, item ->
            applyFunction(fn, listOf(item, idx.toDouble(), list), null, env)
        }
        return collapseSequenceList(results)
    }

    // $filter(array, function)
    private fun fnFilter(args: List<Any?>, env: Environment): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        val fn = args.getOrNull(1) ?: return null
        val list = when (arr) {
            is List<*> -> arr as List<Any?>
            else       -> listOf(arr)
        }
        val results = list.filterIndexed { idx, item ->
            val res = applyFunction(fn, listOf(item, idx.toDouble(), list), null, env)
            isTruthy(res)
        }
        return collapseSequenceList(results)
    }

    // $reduce(array, function [, init])
    private fun fnReduce(args: List<Any?>, env: Environment): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        val fn = args.getOrNull(1) ?: return null
        val list = when (arr) {
            is List<*> -> arr as List<Any?>
            else       -> listOf(arr)
        }
        if (list.isEmpty()) return args.getOrNull(2)

        var acc: Any?
        var startIdx: Int
        if (args.size >= 3) {
            acc = args[2]
            startIdx = 0
        } else {
            acc = list[0]
            startIdx = 1
        }
        for (i in startIdx until list.size) {
            acc = applyFunction(fn, listOf(acc, list[i]), null, env)
        }
        return acc
    }

    // $single(array, function)
    private fun fnSingle(args: List<Any?>, env: Environment): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        val fn = args.getOrNull(1) ?: return null
        val list = when (arr) {
            is List<*> -> arr as List<Any?>
            else       -> listOf(arr)
        }
        val matches = list.filterIndexed { idx, item ->
            val res = applyFunction(fn, listOf(item, idx.toDouble(), list), null, env)
            isTruthy(res)
        }
        if (matches.size != 1) {
            if (matches.isEmpty()) throw JSONataException.RuntimeError(
                "\$single: no match found", "D3010"
            )
            throw JSONataException.RuntimeError("\$single: multiple matches found", "D3010")
        }
        return matches[0]
    }

    private fun collapseSequenceList(list: List<Any?>): Any? {
        val filtered = list.filterNotNull()
        return when (filtered.size) {
            0    -> null
            1    -> filtered[0]
            else -> filtered
        }
    }
}
