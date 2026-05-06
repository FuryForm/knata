package com.furyform.knata.adapter

/**
 * A generic lazy JSON document node that knata can navigate directly,
 * bypassing Map/List wrapper objects.
 *
 * Implementations wrap an integer-indexed document (e.g. FuryJSON's
 * JsonNodeIndex) and provide O(children) key lookup and O(1) child
 * iteration without allocating Map/List/Entry objects for every node.
 */
interface JsonIndexNode {
    /** Child count for objects/arrays; 0 for primitives. */
    val childCount: Int

    /** Look up a child by key (for object nodes only). */
    fun childByKey(key: String): JsonIndexNode?

    /** Get child at position [index] (for array nodes only). */
    fun childAt(index: Int): JsonIndexNode?

    /** Iterate all children (for objects and arrays). */
    fun children(): Sequence<JsonIndexNode>

    /** Get the string keys of all children (for object nodes only). */
    fun childKeys(): Sequence<String>

    // ── Primitive value access (leaf nodes) ──
    val stringValue: String? get() = null
    val numberValue: Double? get() = null
    val boolValue: Boolean? get() = null
    val isNullValue: Boolean get() = false

    // ── Type checks ──
    val isObject: Boolean get() = false
    val isArray: Boolean get() = false
    val isLeaf: Boolean get() = false
}

/**
 * Convert a [JsonIndexNode] to a standard Kotlin value that knata
 * can operate on (String, Double, Boolean, or null for primitives,
 * or the node itself for objects/arrays).
 */
fun JsonIndexNode.toKnataValue(): Any? = when {
    isLeaf -> stringValue ?: numberValue ?: if (isNullValue) null else boolValue
    else -> this
}
