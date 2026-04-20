package com.furyform.knata

import com.furyform.knata.evaluator.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Smoke tests that do not depend on external test data files.
 * These verify the end-to-end pipeline: lexer → parser → evaluator.
 */
class KnataSmokeTest {

    private fun eval(expr: String, data: Any? = null) = Knata.evaluate(expr, data)

    // ── Literals ──────────────────────────────────────────────────────────────

    @Test fun numberLiteral()  = assertEquals(42.0, eval("42"))
    @Test fun stringLiteral()  = assertEquals("hello", eval("\"hello\""))
    @Test fun trueLiteral()    = assertEquals(true, eval("true"))
    @Test fun falseLiteral()   = assertEquals(false, eval("false"))
    @Test fun nullLiteral()    = assertEquals(JsonNull, eval("null"))
    @Test fun undefinedField() = assertNull(eval("missing", mapOf("foo" to "bar")))

    // ── Arithmetic ────────────────────────────────────────────────────────────

    @Test fun addition()        = assertEquals(3.0,  eval("1 + 2"))
    @Test fun subtraction()     = assertEquals(1.0,  eval("3 - 2"))
    @Test fun multiplication()  = assertEquals(6.0,  eval("2 * 3"))
    @Test fun division()        = assertEquals(2.5,  eval("5 / 2"))
    @Test fun modulo()          = assertEquals(1.0,  eval("7 % 3"))
    @Test fun nestedArith()     = assertEquals(14.0, eval("2 + 3 * 4"))

    // ── String concatenation ──────────────────────────────────────────────────

    @Test fun stringConcat() = assertEquals("hello world", eval("\"hello\" & \" \" & \"world\""))

    // ── Comparison ───────────────────────────────────────────────────────────

    @Test fun eq()  = assertEquals(true,  eval("1 = 1"))
    @Test fun neq() = assertEquals(true,  eval("1 != 2"))
    @Test fun lt()  = assertEquals(true,  eval("1 < 2"))
    @Test fun gt()  = assertEquals(false, eval("1 > 2"))
    @Test fun lte() = assertEquals(true,  eval("2 <= 2"))
    @Test fun gte() = assertEquals(true,  eval("3 >= 2"))

    // ── Boolean logic ─────────────────────────────────────────────────────────

    @Test fun andTrue()  = assertEquals(true,  eval("true and true"))
    @Test fun andFalse() = assertEquals(false, eval("true and false"))
    @Test fun orTrue()   = assertEquals(true,  eval("false or true"))
    @Test fun orFalse()  = assertEquals(false, eval("false or false"))

    // ── Field access ─────────────────────────────────────────────────────────

    @Test fun simpleField() = assertEquals("bar", eval("foo", mapOf("foo" to "bar")))

    @Test fun nestedField() = assertEquals("world", eval("a.b", mapOf("a" to mapOf("b" to "world"))))

    @Test fun arrayElement() {
        val data = mapOf("items" to listOf(1.0, 2.0, 3.0))
        assertEquals(listOf(1.0, 2.0, 3.0), eval("items", data))
    }

    // ── Array constructor ─────────────────────────────────────────────────────

    @Test fun arrayConstructor() = assertEquals(listOf(1.0, 2.0, 3.0), eval("[1, 2, 3]"))

    // ── Object constructor ────────────────────────────────────────────────────

    @Test fun objectConstructor() {
        val result = eval("{\"a\": 1, \"b\": 2}") as? Map<*, *>
        assertEquals(1.0, result?.get("a"))
        assertEquals(2.0, result?.get("b"))
    }

    // ── Conditional ──────────────────────────────────────────────────────────

    @Test fun conditionalTrue()  = assertEquals("yes", eval("true ? \"yes\" : \"no\""))
    @Test fun conditionalFalse() = assertEquals("no",  eval("false ? \"yes\" : \"no\""))

    // ── Variables ────────────────────────────────────────────────────────────

    @Test fun variableBinding() = assertEquals(42.0, eval("\$x := 42; \$x"))
    @Test fun variableArith()   = assertEquals(10.0, eval("\$a := 3; \$b := 7; \$a + \$b"))

    // ── Lambda ───────────────────────────────────────────────────────────────

    @Test fun lambdaCall() = assertEquals(5.0, eval("(\$add := function(\$a, \$b) { \$a + \$b }; \$add(2, 3))"))

    // ── Wildcard ─────────────────────────────────────────────────────────────

    @Test fun wildcardValues() {
        val data = mapOf("a" to 1.0, "b" to 2.0, "c" to 3.0)
        val result = eval("*", data) as? List<*>
        assertEquals(3, result?.size)
    }

    // ── Range ────────────────────────────────────────────────────────────────

    @Test fun rangeExpression() = assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), eval("[1..5]"))

    // ── Predicate filter ─────────────────────────────────────────────────────

    @Test fun predicateFilter() {
        val data = mapOf("items" to listOf(mapOf("v" to 1.0), mapOf("v" to 2.0), mapOf("v" to 3.0)))
        val result = eval("items[v > 1]", data) as? List<*>
        assertEquals(2, result?.size)
    }

    // ── Stdlib: String functions ──────────────────────────────────────────────

    @Test fun fnLength()          = assertEquals(5.0, eval("\$length(\"hello\")"))
    @Test fun fnUppercase()       = assertEquals("HELLO", eval("\$uppercase(\"hello\")"))
    @Test fun fnLowercase()       = assertEquals("hello", eval("\$lowercase(\"HELLO\")"))
    @Test fun fnTrim()            = assertEquals("hi", eval("\$trim(\"  hi  \")"))
    @Test fun fnSubstring()       = assertEquals("ello", eval("\$substring(\"hello\", 1)"))
    @Test fun fnSubstringLen()    = assertEquals("el", eval("\$substring(\"hello\", 1, 2)"))
    @Test fun fnSubstringBefore() = assertEquals("hel", eval("\$substringBefore(\"hello\", \"lo\")"))
    @Test fun fnSubstringAfter()  = assertEquals("lo", eval("\$substringAfter(\"hello\", \"hel\")"))
    @Test fun fnContainsTrue()    = assertEquals(true, eval("\$contains(\"hello\", \"ell\")"))
    @Test fun fnContainsFalse()   = assertEquals(false, eval("\$contains(\"hello\", \"xyz\")"))
    @Test fun fnSplit()           = assertEquals(listOf("a", "b", "c"), eval("\$split(\"a,b,c\", \",\")"))
    @Test fun fnJoin()            = assertEquals("a-b-c", eval("\$join([\"a\",\"b\",\"c\"], \"-\")"))
    @Test fun fnReplace()         = assertEquals("h-ll- w-rld", eval("\$replace(\"hello world\", /[eo]/, \"-\")"))
    @Test fun fnString()          = assertEquals("42", eval("\$string(42)"))

    // ── Stdlib: Numeric functions ─────────────────────────────────────────────

    @Test fun fnAbs()    = assertEquals(3.0,  eval("\$abs(-3)"))
    @Test fun fnFloor()  = assertEquals(3.0,  eval("\$floor(3.7)"))
    @Test fun fnCeil()   = assertEquals(4.0,  eval("\$ceil(3.2)"))
    @Test fun fnRound()  = assertEquals(4.0,  eval("\$round(3.5)"))
    @Test fun fnPower()  = assertEquals(8.0,  eval("\$power(2, 3)"))
    @Test fun fnSqrt()   = assertEquals(3.0,  eval("\$sqrt(9)"))
    @Test fun fnSum1()   = assertEquals(6.0,  eval("\$sum([1, 2, 3])"))
    @Test fun fnMax1()   = assertEquals(3.0,  eval("\$max([1, 2, 3])"))
    @Test fun fnMin1()   = assertEquals(1.0,  eval("\$min([1, 2, 3])"))
    @Test fun fnAvg()    = assertEquals(2.0,  eval("\$average([1, 2, 3])"))

    // ── Stdlib: Array functions ───────────────────────────────────────────────

    @Test fun fnCount()    = assertEquals(3.0,  eval("\$count([1, 2, 3])"))
    @Test fun fnAppend()   = assertEquals(listOf(1.0, 2.0, 3.0, 4.0), eval("\$append([1, 2], [3, 4])"))
    @Test fun fnReverse()  = assertEquals(listOf(3.0, 2.0, 1.0), eval("\$reverse([1, 2, 3])"))
    @Test fun fnSort1()    = assertEquals(listOf(1.0, 2.0, 3.0), eval("\$sort([3, 1, 2])"))
    @Test fun fnDistinct() = assertEquals(listOf(1.0, 2.0, 3.0), eval("\$distinct([1, 2, 1, 3, 2])"))
    @Test fun fnFlatten()  = assertEquals(listOf(1.0, 2.0, 3.0), eval("\$flatten([[1, 2], [3]])"))

    // ── Stdlib: Object functions ──────────────────────────────────────────────

    @Test fun fnKeys()   {
        val data = mapOf("a" to 1.0, "b" to 2.0)
        val result = eval("\$keys(\$)", data)
        assertTrue(result is List<*> && result.size == 2)
    }
    @Test fun fnMerge()  = assertEquals(mapOf("a" to 1.0, "b" to 2.0), eval("\$merge([{\"a\":1},{\"b\":2}])").let {
        @Suppress("UNCHECKED_CAST")
        (it as? Map<String, Any?>)?.let { m -> mapOf("a" to m["a"], "b" to m["b"]) }
    })
    @Test fun fnType()   = assertEquals("string", eval("\$type(\"hello\")"))
    @Test fun fnTypeNum() = assertEquals("number", eval("\$type(42)"))

    // ── Stdlib: Boolean / HOF ─────────────────────────────────────────────────

    @Test fun fnBoolean() = assertEquals(true, eval("\$boolean(1)"))
    @Test fun fnNot()     = assertEquals(false, eval("\$not(true)"))
    @Test fun fnExists()  = assertEquals(true, eval("\$exists(\"x\")"))
    @Test fun fnMap1()    = assertEquals(listOf(2.0, 4.0, 6.0), eval("\$map([1,2,3], function(\$v) { \$v * 2 })"))
    @Test fun fnFilter1() = assertEquals(listOf(2.0, 4.0), eval("\$filter([1,2,3,4], function(\$v) { \$v % 2 = 0 })"))
    @Test fun fnReduce1() = assertEquals(6.0, eval("\$reduce([1,2,3], function(\$a,\$b) { \$a + \$b })"))

    // ── Encoding ─────────────────────────────────────────────────────────────

    @Test fun fnBase64Encode() = assertEquals("aGVsbG8=", eval("\$base64encode(\"hello\")"))
    @Test fun fnBase64Decode() = assertEquals("hello", eval("\$base64decode(\"aGVsbG8=\")"))
}
