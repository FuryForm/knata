package com.furyform.knata.sample.ui

import kotlinx.serialization.json.*

fun jsonElementToKotlin(element: JsonElement): Any? = when (element) {
    is JsonObject -> element.mapValues { jsonElementToKotlin(it.value) }
    is JsonArray -> element.map { jsonElementToKotlin(it) }
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.booleanOrNull != null -> element.boolean
        element.longOrNull != null -> element.long
        element.doubleOrNull != null -> element.double
        else -> element.content
    }
    JsonNull -> null
}

fun resultToString(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"$value\""
    is Number, is Boolean -> value.toString()
    is Map<*, *> -> buildString {
        append("{")
        append(value.entries.joinToString(", ") { (key, v) -> "\"$key\": ${resultToString(v)}" })
        append("}")
    }
    is List<*> -> buildString {
        append("[")
        append(value.joinToString(", ") { resultToString(it) })
        append("]")
    }
    else -> value.toString()
}
