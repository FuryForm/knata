package com.furyform.knata.evaluator

/**
 * A sentinel for JSON null values.
 * We use `null` (Kotlin null) to mean "undefined" (no result),
 * and [JsonNull] to mean JSON `null`.
 */
object JsonNull {
    override fun toString() = "null"
}

/** Returns true if [v] is the JSON null sentinel. */
fun isNull(v: Any?): Boolean = v === JsonNull

/** Returns true if [v] represents "undefined" in JSONata (no result). */
fun isUndefined(v: Any?): Boolean = v == null

/**
 * A lazy sequence of values produced during path evaluation.
 * Collapsed to a list (or scalar) at the boundary of each expression.
 */
class Sequence(val values: MutableList<Any?> = mutableListOf()) {
    fun add(v: Any?) {
        if (v is Sequence) values.addAll(v.values) else values.add(v)
    }
}

/**
 * Collapse a [Sequence] following JSONata singleton-unwrap rules:
 * - Empty → null (undefined)
 * - Single element → that element
 * - Multiple → List
 */
fun collapseSequence(seq: Sequence): Any? = when (seq.values.size) {
    0    -> null
    1    -> seq.values[0]
    else -> seq.values.toList()
}

/**
 * An order-preserving map for JSON objects.
 * Wraps LinkedHashMap so key insertion order is maintained,
 * matching the reference implementation's behaviour.
 */
class OrderedMap(private val map: LinkedHashMap<String, Any?> = LinkedHashMap()) :
    MutableMap<String, Any?> by map {

    override fun toString(): String = map.toString()
    override fun equals(other: Any?): Boolean = when (other) {
        is OrderedMap -> map == other.map
        is Map<*, *>  -> map == other
        else          -> false
    }
    override fun hashCode(): Int = map.hashCode()

    fun toMutableMap(): LinkedHashMap<String, Any?> = LinkedHashMap(map)

    companion object {
        fun of(vararg pairs: Pair<String, Any?>): OrderedMap = OrderedMap(linkedMapOf(*pairs))
        fun from(map: Map<String, Any?>): OrderedMap = OrderedMap(LinkedHashMap(map))
    }
}
