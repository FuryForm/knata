package com.furyform.knata

/**
 * All JSONata runtime and compile-time errors.
 * Error codes match the JSONata specification.
 */
sealed class JSONataException(message: String, val code: String) : Exception(message) {

    // ── Lexer / Syntax errors (S01xx) ────────────────────────────────────────

    /** Unterminated string literal */
    class S0101(pos: Int) : JSONataException("Unterminated string literal at position $pos", "S0101")

    /** Unterminated comment */
    class S0102(pos: Int) : JSONataException("Unterminated comment at position $pos", "S0102")

    /** Invalid unicode escape */
    class S0103(pos: Int) : JSONataException("Invalid unicode escape at position $pos", "S0103")

    /** Unexpected character */
    class S0204(pos: Int, char: String) : JSONataException("Unexpected character: '$char' at position $pos", "S0204")

    /** Unterminated backtick name */
    class S0105(pos: Int) : JSONataException("Unterminated quoted name at position $pos", "S0105")

    // ── Parser errors (S02xx) ────────────────────────────────────────────────

    /** Unexpected token */
    class S0201(pos: Int, token: String) : JSONataException("Syntax error: '$token' at position $pos", "S0201")

    /** Expected token not found */
    class S0202(pos: Int, expected: String, got: String) : JSONataException(
        "Expected '$expected' but got '$got' at position $pos", "S0202"
    )

    /** Unterminated regex */
    class S0302(pos: Int) : JSONataException("Unterminated regex literal at position $pos", "S0302")

    /** Invalid left-hand side of assignment */
    class S0212(pos: Int) : JSONataException(
        "The left hand side of ':=' must be a variable", "S0212"
    )

    // ── Runtime errors (T01xx) ────────────────────────────────────────────────

    /** Stack overflow / recursion limit */
    class T1001(msg: String) : JSONataException(msg, "T1001")

    // ── Type errors (T02xx) ──────────────────────────────────────────────────

    /** Non-numeric operand */
    class T2001(pos: Int, op: String) : JSONataException(
        "The left side of the '$op' operator must evaluate to a number", "T2001"
    )
    class T2002(pos: Int, op: String) : JSONataException(
        "The right side of the '$op' operator must evaluate to a number", "T2002"
    )

    /** Non-string concatenation operand */
    class T2003(pos: Int) : JSONataException(
        "The left side of the '&' operator must evaluate to a string", "T2003"
    )
    class T2004(pos: Int) : JSONataException(
        "The right side of the '&' operator must evaluate to a string", "T2004"
    )

    // ── Function errors (T04xx / D1xxx) ──────────────────────────────────────

    /** Wrong number of arguments */
    class T0410(fnName: String, argIdx: Int) : JSONataException(
        "Argument $argIdx of function \$$fnName does not match function signature", "T0410"
    )

    /** Function invoked on non-function */
    class T0006(pos: Int) : JSONataException(
        "Attempted to invoke a non-function", "T0006"
    )

    /** $error() called */
    class D3001(msg: String) : JSONataException(msg, "D3001")

    /** $assert() failed */
    class D3141(msg: String) : JSONataException(msg, "D3141")

    // ── Generic ───────────────────────────────────────────────────────────────

    /** Catch-all runtime error */
    class RuntimeError(msg: String, code: String = "E9999") : JSONataException(msg, code)
}
