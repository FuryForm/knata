package com.furyform.knata.evaluator

import com.furyform.knata.JSONataException
import com.furyform.knata.parser.Node

/**
 * Transform expressions: `| pattern | update [, deletes] |`
 *
 * Clones the matched values, applies update (merge), removes keys in deletes.
 */
internal fun evalTransform(node: Node.Transform, focus: Any?, env: Environment): Any? {
    // The transform is a lambda-like value; when applied to a document it modifies it
    // Build a built-in function that applies the transform
    return BuiltinFunction("transform") { args, _, _ ->
        val target = args.firstOrNull() ?: return@BuiltinFunction null
        applyTransform(node, target, focus, env)
    }
}

private fun applyTransform(node: Node.Transform, target: Any?, focus: Any?, env: Environment): Any? {
    val cloned = deepClone(target) ?: return null

    // Find nodes matching the pattern within the cloned document
    val matched = eval(node.pattern, cloned, env)

    val updates: Any? = eval(node.update, cloned, env)
    val deletes: Any? = node.delete?.let { eval(it, cloned, env) }

    val matchList = when (matched) {
        null -> return cloned
        is List<*> -> matched
        else -> listOf(matched)
    }

    val updateMap: Map<String, Any?> = when (updates) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            updates as Map<String, Any?>
        }
        else -> emptyMap()
    }

    val deleteKeys: Set<String> = when (deletes) {
        is List<*> -> deletes.filterIsInstance<String>().toSet()
        is String  -> setOf(deletes)
        else       -> emptySet()
    }

    for (item in matchList) {
        if (item is OrderedMap) {
            for ((k, v) in updateMap) {
                item[k] = v
            }
            for (k in deleteKeys) {
                item.remove(k)
            }
        }
    }

    return cloned
}
