package com.furyform.knata.functions

import com.furyform.knata.JSONataException
import com.furyform.knata.evaluator.*

/**
 * JSONata array standard library functions.
 */
object ArrayFunctions {
    fun register(env: Environment) {
        env.fn("count")    { args, _, _ -> fnCount(args) }
        env.fn("append")   { args, _, _ -> fnAppend(args) }
        env.fn("sort")     { args, _, e -> fnSort(args, e) }
        env.fn("reverse")  { args, _, _ -> fnReverse(args) }
        env.fn("shuffle")  { args, _, _ -> fnShuffle(args) }
        env.fn("distinct") { args, _, _ -> fnDistinct(args) }
        env.fn("flatten")  { args, _, _ -> fnFlatten(args) }
        env.fn("zip")      { args, _, _ -> fnZip(args) }
    }

    // $count(array)
    private fun fnCount(args: List<Any?>): Any? {
        val v = args.getOrNull(0) ?: return 0.0
        if (isNull(v)) return 0.0
        return when (v) {
            is List<*> -> v.size.toDouble()
            else       -> 1.0
        }
    }

    // $append(array1, array2)
    private fun fnAppend(args: List<Any?>): Any? {
        val a1 = args.getOrNull(0)
        val a2 = args.getOrNull(1)
        if (a1 == null && a2 == null) return null
        val list1 = when (a1) {
            null       -> emptyList()
            is List<*> -> a1
            else       -> listOf(a1)
        }
        val list2 = when (a2) {
            null       -> emptyList()
            is List<*> -> a2
            else       -> listOf(a2)
        }
        return list1 + list2
    }

    // $sort(array [, function])
    private fun fnSort(args: List<Any?>, env: Environment): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        val list = when (arr) {
            is List<*> -> arr.toMutableList()
            else       -> return arr
        }
        val compareFn = args.getOrNull(1)
        if (compareFn == null) {
            // Natural sort: numbers then booleans then strings
            return list.sortedWith { a, b -> compareValues(a, b) }
        }
        // Custom comparator: fn(a,b) → returns true if a precedes b
        return list.sortedWith { a, b ->
            val res = applyFunction(compareFn, listOf(a, b), null, env)
            if (isTruthy(res)) -1 else 1
        }
    }

    // $reverse(array)
    private fun fnReverse(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        return when (arr) {
            is List<*> -> arr.reversed()
            else       -> arr
        }
    }

    // $shuffle(array)
    private fun fnShuffle(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        return when (arr) {
            is List<*> -> arr.toMutableList().also { it.shuffle() }
            else       -> arr
        }
    }

    // $distinct(array)
    private fun fnDistinct(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        return when (arr) {
            is List<*> -> {
                val seen = mutableListOf<Any?>()
                for (item in arr) {
                    if (seen.none { deepEqual(it, item) }) seen.add(item)
                }
                when (seen.size) {
                    0    -> null
                    1    -> seen[0]
                    else -> seen
                }
            }
            else -> arr
        }
    }

    // $flatten(array [, depth])
    private fun fnFlatten(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        val depth = args.getOrNull(1)?.let { toNumber(it)?.toInt() } ?: Int.MAX_VALUE
        return when (arr) {
            is List<*> -> flattenDeep(arr, depth)
            else       -> arr
        }
    }

    private fun flattenDeep(list: List<*>, depth: Int): List<Any?> {
        if (depth <= 0) return list.toList()
        val result = mutableListOf<Any?>()
        for (item in list) {
            if (item is List<*>) {
                result.addAll(flattenDeep(item, depth - 1))
            } else {
                result.add(item)
            }
        }
        return result
    }

    // $zip(array1, array2, ...)
    private fun fnZip(args: List<Any?>): Any? {
        if (args.isEmpty()) return null
        val arrays = args.map { arg ->
            when (arg) {
                null       -> emptyList<Any?>()
                is List<*> -> arg as List<Any?>
                else       -> listOf(arg)
            }
        }
        val minLen = arrays.minOfOrNull { it.size } ?: 0
        if (minLen == 0) return null
        return (0 until minLen).map { i -> arrays.map { it[i] } }
    }

    /** Natural JSONata sort comparison for untyped values. */
    private fun compareValues(a: Any?, b: Any?): Int {
        return when {
            a == null && b == null -> 0
            a == null -> -1
            b == null -> 1
            a is Double && b is Double -> a.compareTo(b)
            a is String && b is String -> a.compareTo(b)
            a is Boolean && b is Boolean -> a.compareTo(b)
            else -> 0
        }
    }
}
