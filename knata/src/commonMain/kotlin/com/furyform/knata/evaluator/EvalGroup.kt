package com.furyform.knata.evaluator

import com.furyform.knata.parser.Node

/**
 * Group-by aggregation: `expr{key: value, ...}`
 *
 * Each item in the input array is evaluated against the key expressions.
 * Items with the same key are grouped together; the value expression receives the group.
 */
internal fun evalGroup(node: Node.Group, focus: Any?, env: Environment): Any? {
    val input = eval(node.expr, focus, env)

    val items: List<Any?> = when (input) {
        null -> return null
        is List<*> -> input
        else -> listOf(input)
    }

    // Keyed groups preserve insertion order
    val groups = linkedMapOf<String, MutableList<Any?>>()

    for (item in items) {
        val child = env.child()
        child.bind("$", item)

        for ((keyNode, _) in node.pairs) {
            val keyVal = eval(keyNode, item, child)
            if (keyVal == null || isNull(keyVal)) continue
            val key = jsonataStringify(keyVal)
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }
    }

    val result = OrderedMap()

    for ((keyNode, valueNode) in node.pairs) {
        for (item in items) {
            val child = env.child()
            child.bind("$", item)

            val keyVal = eval(keyNode, item, child) ?: continue
            if (isNull(keyVal)) continue
            val key = jsonataStringify(keyVal)
            val group = groups[key] ?: continue

            if (!result.containsKey(key)) {
                val groupFocus = if (group.size == 1) group[0] else group
                val child2 = env.child()
                child2.bind("$", groupFocus)
                val value = eval(valueNode, groupFocus, child2)
                result[key] = value
            }
        }
    }

    return if (result.isEmpty()) null else result
}
