package com.furyform.knata.lexer

/** All token types in JSONata 2.x */
enum class TokenType {
    // Literals
    NUMBER,      // numeric literal
    STRING,      // string literal (single or double quoted)
    TRUE,        // true
    FALSE,       // false
    NULL,        // null

    // Identifiers / names
    NAME,        // plain field name or identifier
    VARIABLE,    // $name or $$
    REGEX,       // /pattern/flags

    // Operators - arithmetic
    PLUS,        // +
    MINUS,       // -
    STAR,        // *
    SLASH,       // /
    PERCENT,     // %

    // Operators - comparison
    EQ,          // =
    NEQ,         // !=
    LT,          // <
    LTE,         // <=
    GT,          // >
    GTE,         // >=

    // Operators - logical
    AND,         // and (keyword)
    OR,          // or  (keyword)
    NOT,         // (used in parser, not as separate keyword usually)

    // Operators - other
    AMP,         // &  string concatenation
    DOT,         // .  path step
    DOTDOT,      // .. range
    STARSTAR,    // ** descendants
    PIPE,        // |  transform delimiters / union
    QUESTION,    // ?  conditional
    COLON,       // :  conditional else / object key-value
    ASSIGN,      // := variable assignment
    SEMI,        // ;  block separator
    TILDE_GT,    // ~> pipe / function composition
    AT,          // @  focus binding / join
    HASH,        // #  index binding
    CARET,       // ^  sort

    // Grouping / structuring
    LPAREN,      // (
    RPAREN,      // )
    LBRACKET,    // [
    RBRACKET,    // ]
    LBRACE,      // {
    RBRACE,      // }

    // Special
    BANG,        // ! (transform keep)
    COMMA,       // ,
    QUESTION_QUESTION, // ?? coalescing operator
    QUESTION_COLON,    // ?: default/elvis operator

    // Meta
    EOF,
}

/** A single token produced by the lexer. */
data class Token(
    val type: TokenType,
    val value: String,     // raw source text (or parsed value for strings)
    val pos: Int,          // byte offset in source
)
