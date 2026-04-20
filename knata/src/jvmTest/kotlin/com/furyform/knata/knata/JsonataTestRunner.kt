package com.furyform.knata

import com.furyform.knata.evaluator.JsonNull as KnataNull
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Dynamic test runner that loads JSONata test cases from JSON files.
 *
 * Test files are resolved from the `testdata/groups/` directory relative to
 * the project root (same layout as the gnata test suite).
 *
 * Each file is an array (or object with "tests" key) of test case objects:
 * ```json
 * [
 *   {
 *     "description": "simple field reference",
 *     "expression": "foo",
 *     "data": { "foo": "bar" },
 *     "result": "bar"
 *   }
 * ]
 * ```
 *
 * A test case may also specify `"error": { "code": "..." }` instead of `"result"`
 * to assert that evaluation throws a JSONataException with that code.
 */
class JsonataTestRunner {

    @TestFactory
    fun jsonataTestSuite(): List<DynamicTest> {
        val root = findTestDataDir() ?: return emptyList()
        val jsonFiles = root.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .sorted()
            .toList()

        return jsonFiles.flatMap { file ->
            loadTestCases(file).mapIndexed { idx, tc ->
                val name = "${file.parentFile.name}/${file.nameWithoutExtension}[$idx]: ${tc.description}"
                DynamicTest.dynamicTest(name) {
                    runTestCase(tc)
                }
            }
        }
    }

    private fun runTestCase(tc: TestCase) {
        if (tc.skip) return

        if (tc.errorCode != null) {
            // Expect an exception
            var caught: JSONataException? = null
            try {
                Knata.evaluate(tc.expression, tc.data)
            } catch (e: JSONataException) {
                caught = e
            }
            assertNotNull(caught, "Expected JSONataException(${tc.errorCode}) but no exception thrown for: ${tc.expression}")
            assertEquals(tc.errorCode, caught!!.code,
                "Expected error code ${tc.errorCode} but got ${caught.code} for: ${tc.expression}")
        } else {
            val actual = try {
                Knata.evaluate(tc.expression, tc.data)
            } catch (e: JSONataException) {
                throw AssertionError("Unexpected JSONataException(${e.code}): ${e.message} for: ${tc.expression}", e)
            }
            assertDeepEquals(tc.result, actual, tc.expression)
        }
    }

    private fun assertDeepEquals(expected: Any?, actual: Any?, expr: String) {
        // Normalize: both KnataNull (our sentinel) and Kotlin null are treated as
        // equivalent in test assertions — JSON test files cannot distinguish
        // JSONata `null` from JSONata `undefined`.
        val e = if (expected === KnataNull) null else expected
        val a = if (actual === KnataNull) null else actual
        when {
            e == null && a == null -> return
            e == null -> assertEquals<Any?>(null, a, "Expression: $expr")
            a == null -> assertEquals<Any?>(e, null, "Expression: $expr")
            e is Double && a is Double -> {
                // Allow small floating-point tolerance
                assertTrue(
                    Math.abs(e - a) < 1e-9 * Math.max(1.0, Math.abs(e)),
                    "Expected $e but got $a for: $expr"
                )
            }
            e is List<*> && a is List<*> -> {
                assertEquals(e.size, a.size, "Array length mismatch for: $expr  expected=$e  actual=$a")
                for (i in e.indices) assertDeepEquals(e[i], a[i], "$expr[$i]")
            }
            e is Map<*, *> && a is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val em = e as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val am = a as Map<String, Any?>
                assertEquals(em.keys, am.keys, "Object keys mismatch for: $expr")
                for (k in em.keys) assertDeepEquals(em[k], am[k], "$expr.$k")
            }
            else -> assertEquals<Any?>(e, a, "Expression: $expr")
        }
    }
}

// ── Test case loading ─────────────────────────────────────────────────────────

data class TestCase(
    val description: String,
    val expression: String,
    val data: Any?,
    val result: Any?,
    val errorCode: String?,
    val skip: Boolean,
)

private val JSON = Json { ignoreUnknownKeys = true }

private fun loadTestCases(file: File): List<TestCase> {
    val text = file.readText()
    val json = JSON.parseToJsonElement(text)
    val array: JsonArray = when (json) {
        is JsonArray  -> json
        is JsonObject -> json["tests"] as? JsonArray ?: json["cases"] as? JsonArray ?: return emptyList()
        else          -> return emptyList()
    }
    return array.filterIsInstance<JsonObject>().map { parseTestCase(it) }
}

private fun parseTestCase(obj: JsonObject): TestCase {
    val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: obj["expr"]?.jsonPrimitive?.contentOrNull ?: ""
    val expression  = obj["expression"]?.jsonPrimitive?.contentOrNull
        ?: obj["expr"]?.jsonPrimitive?.contentOrNull
        ?: ""
    val data        = obj["data"]?.let { jsonToKotlin(it) }
    val result      = obj["result"]?.let { jsonToKotlin(it) }
    val errorCode   = (obj["error"] as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull
        ?: obj["error"]?.jsonPrimitive?.contentOrNull  // sometimes just a string
    val skip        = obj["skip"]?.jsonPrimitive?.booleanOrNull == true
        || obj["todo"]?.jsonPrimitive?.booleanOrNull == true

    return TestCase(description, expression, data, result, errorCode, skip)
}

/** Recursively convert a [JsonElement] to a Kotlin value compatible with the knata evaluator. */
internal fun jsonToKotlin(element: JsonElement): Any? = when (element) {
    is kotlinx.serialization.json.JsonNull -> KnataNull  // JSON null → evaluator null sentinel
    is JsonPrimitive -> when {
        element.isString   -> element.content
        element.booleanOrNull != null -> element.boolean
        else               -> element.doubleOrNull
    }
    is JsonArray     -> element.map { jsonToKotlin(it) }
    is JsonObject    -> {
        val map = com.furyform.knata.evaluator.OrderedMap()
        for ((k, v) in element) map[k] = jsonToKotlin(v)
        map
    }
}

private fun findTestDataDir(): File? {
    // Try several candidate locations relative to the working directory
    val candidates = listOf(
        "testdata/groups",
        "../testdata/groups",
        "knata/testdata/groups",
    )
    for (path in candidates) {
        val dir = File(path)
        if (dir.isDirectory) return dir
    }
    return null
}
