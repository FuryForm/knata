package com.furyform.knata.functions

import com.furyform.knata.JSONataException
import com.furyform.knata.evaluator.*

/**
 * JSONata object standard library functions.
 */
object ObjectFunctions {
    fun register(env: Environment) {
        env.fn("keys")   { args, _, _ -> fnKeys(args) }
        env.fn("values") { args, _, _ -> fnValues(args) }
        env.fn("lookup") { args, _, _ -> fnLookup(args) }
        env.fn("spread") { args, _, _ -> fnSpread(args) }
        env.fn("merge")  { args, _, _ -> fnMerge(args) }
        env.fn("each")   { args, _, e -> fnEach(args, e) }
        env.fn("sift")   { args, _, e -> fnSift(args, e) }
        env.fn("type")   { args, _, _ -> fnType(args) }
        env.fn("error")  { args, _, _ -> fnError(args) }
        env.fn("assert") { args, _, _ -> fnAssert(args) }
    }

    // $keys(obj)
    private fun fnKeys(args: List<Any?>): Any? {
        val obj = args.getOrNull(0) ?: return null
        if (isNull(obj)) return null
        return when (obj) {
            is Map<*, *> -> {
                val keys = obj.keys.filterIsInstance<String>()
                if (keys.isEmpty()) null else if (keys.size == 1) keys[0] else keys
            }
            is List<*>   -> {
                val allKeys = mutableListOf<String>()
                for (item in obj) {
                    if (item is Map<*, *>) {
                        for (k in item.keys) {
                            if (k is String && k !in allKeys) allKeys.add(k)
                        }
                    }
                }
                if (allKeys.isEmpty()) null else if (allKeys.size == 1) allKeys[0] else allKeys
            }
            else -> null
        }
    }

    // $values(obj)
    private fun fnValues(args: List<Any?>): Any? {
        val obj = args.getOrNull(0) ?: return null
        if (isNull(obj)) return null
        return when (obj) {
            is Map<*, *> -> {
                val vals = obj.values.toList()
                if (vals.isEmpty()) null else if (vals.size == 1) vals[0] else vals
            }
            else -> null
        }
    }

    // $lookup(obj, key)
    private fun fnLookup(args: List<Any?>): Any? {
        val obj = args.getOrNull(0) ?: return null
        if (isNull(obj)) return null
        val key = (args.getOrNull(1) as? String) ?: return null
        return when (obj) {
            is Map<*, *> -> obj[key]
            is List<*>   -> {
                val results = mutableListOf<Any?>()
                for (item in obj) {
                    if (item is Map<*, *>) {
                        val v = item[key]
                        if (v != null) results.add(v)
                    }
                }
                collapseToValue(results)
            }
            else -> null
        }
    }

    // $spread(obj)
    private fun fnSpread(args: List<Any?>): Any? {
        val obj = args.getOrNull(0) ?: return null
        if (isNull(obj)) return null
        return when (obj) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val m = obj as Map<String, Any?>
                m.entries.map { (k, v) -> OrderedMap.of(k to v) }
            }
            is List<*>   -> {
                val result = mutableListOf<Any?>()
                for (item in obj) {
                    if (item is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val m = item as Map<String, Any?>
                        m.entries.forEach { (k, v) -> result.add(OrderedMap.of(k to v)) }
                    }
                }
                if (result.isEmpty()) null else result
            }
            else -> null
        }
    }

    // $merge(array)
    private fun fnMerge(args: List<Any?>): Any? {
        val arr = args.getOrNull(0) ?: return null
        if (isNull(arr)) return null
        val result = OrderedMap()
        val list = when (arr) {
            is List<*> -> arr
            is Map<*,*> -> return deepClone(arr)
            else -> return null
        }
        for (item in list) {
            if (item is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val m = item as Map<String, Any?>
                result.putAll(m)
            }
        }
        return if (result.isEmpty()) null else result
    }

    // $each(obj, function)
    private fun fnEach(args: List<Any?>, env: Environment): Any? {
        val obj = args.getOrNull(0) ?: return null
        if (isNull(obj)) return null
        val fn = args.getOrNull(1) ?: return null
        if (obj !is Map<*, *>) return null
        @Suppress("UNCHECKED_CAST")
        val m = obj as Map<String, Any?>
        val results = mutableListOf<Any?>()
        for ((k, v) in m) {
            val result = applyFunction(fn, listOf(v, k), null, env)
            if (result != null) results.add(result)
        }
        return collapseToValue(results)
    }

    // $sift(obj, function)
    private fun fnSift(args: List<Any?>, env: Environment): Any? {
        val obj = args.getOrNull(0) ?: return null
        if (isNull(obj)) return null
        val fn = args.getOrNull(1) ?: return null
        if (obj !is Map<*, *>) return null
        @Suppress("UNCHECKED_CAST")
        val m = obj as Map<String, Any?>
        val result = OrderedMap()
        for ((k, v) in m) {
            val keep = applyFunction(fn, listOf(v, k), null, env)
            if (isTruthy(keep)) result[k] = v
        }
        return if (result.isEmpty()) null else result
    }

    // $type(value)
    private fun fnType(args: List<Any?>): Any? {
        val v = args.getOrNull(0) ?: return null
        return getType(v)
    }

    // $error(message)
    private fun fnError(args: List<Any?>): Nothing {
        val msg = (args.getOrNull(0) as? String) ?: "Unknown error"
        throw JSONataException.D3001(msg)
    }

    // $assert(condition, message)
    private fun fnAssert(args: List<Any?>): Any? {
        val cond = args.getOrNull(0)
        val msg  = (args.getOrNull(1) as? String) ?: "Assertion failed"
        if (!isTruthy(cond)) throw JSONataException.D3141(msg)
        return null
    }
}

/** Get the JSONata type name of a value. */
internal fun getType(v: Any?): String = when {
    v == null       -> "undefined"
    isNull(v)       -> "null"
    v is Double     -> "number"
    v is Boolean    -> "boolean"
    v is String     -> "string"
    v is List<*>    -> "array"
    v is Map<*, *>  -> "object"
    v is BuiltinFunction || v is LambdaFunction || v is PartiallyApplied -> "function"
    else            -> "undefined"
}

private fun collapseToValue(list: List<Any?>): Any? = when (list.size) {
    0    -> null
    1    -> list[0]
    else -> list
}
