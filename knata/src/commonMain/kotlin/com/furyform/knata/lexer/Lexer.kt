package com.furyform.knata.lexer

import com.furyform.knata.JSONataException

/**
 * Lexer (tokenizer) for JSONata 2.x expressions.
 *
 * Converts a raw JSONata source string into a flat list of [Token]s.
 */
class Lexer(private val src: String) {
    private var pos: Int = 0
    private val tokens = mutableListOf<Token>()

    fun tokenize(): List<Token> {
        while (pos < src.length) {
            skipWhitespaceAndComments()
            if (pos >= src.length) break
            readToken()
        }
        tokens += Token(TokenType.EOF, "", pos)
        return tokens
    }

    // ── Whitespace / comment skipping ────────────────────────────────────────

    private fun skipWhitespaceAndComments() {
        while (pos < src.length) {
            when {
                src[pos].isWhitespace() -> pos++
                // /* block comment */
                pos + 1 < src.length && src[pos] == '/' && src[pos + 1] == '*' -> {
                    pos += 2
                    val end = src.indexOf("*/", pos)
                    if (end == -1) throw JSONataException.S0102(pos)
                    pos = end + 2
                }
                else -> return
            }
        }
    }

    // ── Main dispatch ─────────────────────────────────────────────────────────

    private fun readToken() {
        val start = pos
        val ch = src[pos]

        when {
            ch == '`' -> readBacktickName(start)
            ch == '"' || ch == '\'' -> readStringLiteral(start, ch)
            ch == '/' -> readSlashOrRegex(start)
            ch.isDigit() || (ch == '-' && pos + 1 < src.length && src[pos + 1].isDigit() && tokens.lastOrNull()?.type.let {
                it == null || it == TokenType.LPAREN || it == TokenType.LBRACKET ||
                        it == TokenType.COMMA || it == TokenType.COLON ||
                        it == TokenType.SEMI || it == TokenType.ASSIGN ||
                        it == TokenType.EQ || it == TokenType.NEQ ||
                        it == TokenType.LT || it == TokenType.LTE ||
                        it == TokenType.GT || it == TokenType.GTE ||
                        it == TokenType.PLUS || it == TokenType.MINUS ||
                        it == TokenType.STAR || it == TokenType.PERCENT ||
                        it == TokenType.AMP || it == TokenType.PIPE ||
                        it == TokenType.AND || it == TokenType.OR ||
                        it == TokenType.QUESTION || it == TokenType.TILDE_GT
            }) -> readNumber(start)
            ch == '$' -> readVariable(start)
            ch.isLetter() || ch == '_' -> readName(start)
            else -> readOperator(start)
        }
    }

    // ── Literals ─────────────────────────────────────────────────────────────

    private fun readNumber(start: Int) {
        // optional leading minus
        if (src[pos] == '-') pos++
        // integer part
        while (pos < src.length && src[pos].isDigit()) pos++
        // fractional part
        if (pos < src.length && src[pos] == '.') {
            val next = pos + 1
            if (next < src.length && src[next].isDigit()) {
                pos++ // consume '.'
                while (pos < src.length && src[pos].isDigit()) pos++
            }
        }
        // exponent
        if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
            pos++
            if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
            while (pos < src.length && src[pos].isDigit()) pos++
        }
        tokens += Token(TokenType.NUMBER, src.substring(start, pos), start)
    }

    private fun readStringLiteral(start: Int, quote: Char) {
        pos++ // skip opening quote
        val sb = StringBuilder()
        while (pos < src.length) {
            val ch = src[pos]
            if (ch == quote) {
                pos++
                tokens += Token(TokenType.STRING, sb.toString(), start)
                return
            }
            if (ch == '\\') {
                pos++
                if (pos >= src.length) throw JSONataException.S0101(pos)
                when (src[pos]) {
                    '"', '\'', '\\', '/' -> sb.append(src[pos])
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        val hex = src.substring(pos + 1, minOf(pos + 5, src.length))
                        if (hex.length < 4) throw JSONataException.S0103(pos)
                        val code = hex.toIntOrNull(16) ?: throw JSONataException.S0103(pos)
                        sb.append(code.toChar())
                        pos += 4
                    }
                    else -> {
                        sb.append('\\')
                        sb.append(src[pos])
                    }
                }
                pos++
            } else {
                sb.append(ch)
                pos++
            }
        }
        throw JSONataException.S0101(start) // unterminated string
    }

    private fun readBacktickName(start: Int) {
        pos++ // skip '`'
        val end = src.indexOf('`', pos)
        if (end == -1) throw JSONataException.S0105(start)
        val name = src.substring(pos, end)
        pos = end + 1
        tokens += Token(TokenType.NAME, name, start)
    }

    // ── Variable ─────────────────────────────────────────────────────────────

    private fun readVariable(start: Int) {
        pos++ // skip '$'
        // Check for $$ (root document reference) before reading the name
        if (pos < src.length && src[pos] == '$') {
            pos++ // skip second '$'
            tokens += Token(TokenType.VARIABLE, "$", start)
            return
        }
        val nameStart = pos
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
        tokens += Token(TokenType.VARIABLE, src.substring(nameStart, pos), start)
    }

    // ── Identifiers and keywords ──────────────────────────────────────────────

    private fun readName(start: Int) {
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
        val raw = src.substring(start, pos)
        val type = when (raw) {
            "true"  -> TokenType.TRUE
            "false" -> TokenType.FALSE
            "null"  -> TokenType.NULL
            "and"   -> TokenType.AND
            "or"    -> TokenType.OR
            "in"    -> TokenType.NAME   // 'in' is used as part of inclusion operator
            else    -> TokenType.NAME
        }
        tokens += Token(type, raw, start)
    }

    // ── Regex literals ────────────────────────────────────────────────────────

    /**
     * Context-sensitive: a '/' after a value token is division; otherwise a regex.
     * JSONata regex: /pattern/flags  where flags can be empty or contain 'i', 'm', etc.
     */
    private fun readSlashOrRegex(start: Int) {
        val prevType = tokens.lastOrNull()?.type
        val isDivision = prevType != null && prevType in VALUE_ENDING_TOKENS
        if (isDivision) {
            pos++
            tokens += Token(TokenType.SLASH, "/", start)
            return
        }
        // Regex literal
        pos++ // skip '/'
        val sb = StringBuilder()
        var inClass = false
        while (pos < src.length) {
            val ch = src[pos]
            when {
                ch == '\\' && pos + 1 < src.length -> {
                    sb.append(ch)
                    sb.append(src[pos + 1])
                    pos += 2
                }
                ch == '[' -> { inClass = true; sb.append(ch); pos++ }
                ch == ']' -> { inClass = false; sb.append(ch); pos++ }
                ch == '/' && !inClass -> {
                    pos++ // skip closing '/'
                    val flags = buildString {
                        while (pos < src.length && src[pos].isLetter()) {
                            append(src[pos++])
                        }
                    }
                    tokens += Token(TokenType.REGEX, "$sb${if (flags.isNotEmpty()) "/$flags" else ""}", start)
                    return
                }
                else -> { sb.append(ch); pos++ }
            }
        }
        throw JSONataException.S0302(start)
    }

    // ── Operators ────────────────────────────────────────────────────────────

    private fun readOperator(start: Int) {
        val ch = src[pos]
        val next = if (pos + 1 < src.length) src[pos + 1] else '\u0000'
        val next2 = if (pos + 2 < src.length) src[pos + 2] else '\u0000'

        fun emit(type: TokenType, len: Int = 1) {
            tokens += Token(type, src.substring(start, start + len), start)
            pos += len
        }

        when (ch) {
            '+' -> emit(TokenType.PLUS)
            '-' -> emit(TokenType.MINUS)
            '*' -> if (next == '*') emit(TokenType.STARSTAR, 2) else emit(TokenType.STAR)
            '%' -> emit(TokenType.PERCENT)
            '&' -> emit(TokenType.AMP)
            '.' -> if (next == '.') emit(TokenType.DOTDOT, 2) else emit(TokenType.DOT)
            ',' -> emit(TokenType.COMMA)
            ';' -> emit(TokenType.SEMI)
            '(' -> emit(TokenType.LPAREN)
            ')' -> emit(TokenType.RPAREN)
            '[' -> emit(TokenType.LBRACKET)
            ']' -> emit(TokenType.RBRACKET)
            '{' -> emit(TokenType.LBRACE)
            '}' -> emit(TokenType.RBRACE)
            '^' -> emit(TokenType.CARET)
            '@' -> emit(TokenType.AT)
            '#' -> emit(TokenType.HASH)
            '|' -> emit(TokenType.PIPE)
            '!' -> if (next == '=') emit(TokenType.NEQ, 2) else emit(TokenType.BANG)
            '=' -> emit(TokenType.EQ)
            '<' -> if (next == '=') emit(TokenType.LTE, 2) else emit(TokenType.LT)
            '>' -> if (next == '=') emit(TokenType.GTE, 2) else emit(TokenType.GT)
            ':' -> if (next == '=') emit(TokenType.ASSIGN, 2) else emit(TokenType.COLON)
            '~' -> if (next == '>') emit(TokenType.TILDE_GT, 2) else throw JSONataException.S0204(start, ch.toString())
            '?' -> when {
                next == '?' -> emit(TokenType.QUESTION_QUESTION, 2)
                next == ':' -> emit(TokenType.QUESTION_COLON, 2)
                else -> emit(TokenType.QUESTION)
            }
            else -> throw JSONataException.S0204(start, ch.toString())
        }
    }

    companion object {
        /** Token types that end a value expression — i.e., '/' after these is division. */
        private val VALUE_ENDING_TOKENS = setOf(
            TokenType.NUMBER, TokenType.STRING, TokenType.TRUE, TokenType.FALSE, TokenType.NULL,
            TokenType.NAME, TokenType.VARIABLE, TokenType.RPAREN, TokenType.RBRACKET, TokenType.RBRACE,
            TokenType.REGEX,
        )
    }
}
