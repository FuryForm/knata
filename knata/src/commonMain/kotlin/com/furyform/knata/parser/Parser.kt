package com.furyform.knata.parser

import com.furyform.knata.JSONataException
import com.furyform.knata.lexer.Lexer
import com.furyform.knata.lexer.Token
import com.furyform.knata.lexer.TokenType

/**
 * Pratt (top-down operator precedence) parser for JSONata 2.x.
 *
 * Binding powers follow the JSONata precedence table:
 *   60  ~>  (function composition/pipe)
 *   50  ?: ??
 *   40  ? :  (ternary)
 *   35  or
 *   32  and
 *   30  in
 *   20  = != < <= > >=
 *   15  ..  (range)
 *   10  + -
 *    8  &
 *    7   * / %
 *    5  unary -
 */
class Parser(src: String) {
    private val tokens: List<Token> = Lexer(src).tokenize()
    private var cursor: Int = 0

    private fun peek(): Token = tokens[cursor]
    private fun peekType(): TokenType = tokens[cursor].type
    private fun advance(): Token = tokens[cursor++]
    private fun eof(): Boolean = peekType() == TokenType.EOF

    private fun expect(type: TokenType): Token {
        val tok = peek()
        if (tok.type != type) throw JSONataException.S0202(tok.pos, type.name, tok.value)
        return advance()
    }

    private fun check(type: TokenType): Boolean = peekType() == type
    private fun eat(type: TokenType): Boolean {
        if (check(type)) { advance(); return true }
        return false
    }

    fun parse(): Node {
        if (eof()) return Node.Block(emptyList())
        val exprs = mutableListOf(parseExpr(0))
        while (eat(TokenType.SEMI)) {
            if (eof()) break
            exprs += parseExpr(0)
        }
        if (!eof()) {
            val tok = peek()
            throw JSONataException.S0201(tok.pos, tok.value)
        }
        return if (exprs.size == 1) exprs[0] else Node.Block(exprs)
    }

    // ── Pratt expression ─────────────────────────────────────────────────────

    private fun parseExpr(minBp: Int): Node {
        var lhs = parsePrefix()

        while (true) {
            val tok = peek()
            val (lbp, rbp) = infixBp(tok) ?: break
            if (lbp <= minBp) break
            advance()
            lhs = parseInfix(lhs, tok, rbp)
        }

        return lhs
    }

    // ── Prefix / Atom ─────────────────────────────────────────────────────────

    private fun parsePrefix(): Node {
        val tok = advance()
        return when (tok.type) {
            TokenType.NUMBER   -> Node.NumberLit(tok.value.toDouble(), tok.pos)
            TokenType.STRING   -> Node.StringLit(tok.value, tok.pos)
            TokenType.TRUE     -> Node.BoolLit(true, tok.pos)
            TokenType.FALSE    -> Node.BoolLit(false, tok.pos)
            TokenType.NULL     -> Node.NullLit(tok.pos)
            TokenType.VARIABLE -> Node.Variable(tok.value, tok.pos)

            TokenType.NAME -> {
                if (tok.value == "function" || tok.value == "λ") parseLambda(tok.pos)
                else Node.Name(tok.value, tok.pos)
            }

            TokenType.REGEX -> parseRegex(tok)

            TokenType.STAR    -> Node.Wildcard(tok.pos)
            TokenType.STARSTAR -> Node.Descendants(tok.pos)

            // Unary minus (only for non-numeric — numeric handled in lexer)
            TokenType.MINUS   -> Node.Unary("-", parseExpr(50), tok.pos)

            // Unary not - $not(...) is a function call, but the actual `!` is
            // used in transforms so we treat it as a special marker
            TokenType.BANG    -> Node.Unary("!", parseExpr(50), tok.pos)

            TokenType.LPAREN  -> parseParen(tok.pos)
            TokenType.LBRACKET -> parseArrayConstructor(tok.pos)
            TokenType.LBRACE  -> parseObjectConstructor(tok.pos)

            TokenType.PIPE    -> parseTransform(tok.pos)

            else -> throw JSONataException.S0201(tok.pos, tok.value)
        }
    }

    // ── Infix operators ──────────────────────────────────────────────────────

    private fun parseInfix(lhs: Node, tok: Token, rbp: Int): Node {
        return when (tok.type) {
            TokenType.DOT -> {
                val rhs = parseExpr(rbp)
                buildPath(lhs, rhs, tok.pos)
            }
            TokenType.LBRACKET -> {
                if (check(TokenType.RBRACKET)) {
                    advance()
                    // expr[] — flatten singleton
                    Node.Predicate(lhs, Node.ArrayConstructor(emptyList(), tok.pos), tok.pos)
                } else {
                    val pred = parseExpr(0)
                    expect(TokenType.RBRACKET)
                    Node.Predicate(lhs, pred, tok.pos)
                }
            }
            TokenType.LBRACE -> {
                // grouping operator: expr{key:value,...}
                val pairs = parseObjectPairs()
                expect(TokenType.RBRACE)
                Node.Group(lhs, pairs, tok.pos)
            }
            TokenType.LPAREN -> {
                // function call
                parseFunctionCall(lhs, tok.pos)
            }
            TokenType.CARET -> {
                // sort: expr^(terms)
                expect(TokenType.LPAREN)
                val terms = parseSortTerms()
                expect(TokenType.RPAREN)
                Node.Sort(lhs, terms, tok.pos)
            }
            TokenType.AT -> {
                // focus variable binding: expr@$var
                val varTok = expect(TokenType.VARIABLE)
                Node.FocusBind(lhs, varTok.value, tok.pos)
            }
            TokenType.HASH -> {
                // index variable binding: expr#$i
                val varTok = expect(TokenType.VARIABLE)
                Node.IndexBind(lhs, varTok.value, tok.pos)
            }
            TokenType.QUESTION -> {
                // ternary: cond ? then : else
                val then = parseExpr(0)
                expect(TokenType.COLON)
                val otherwise = parseExpr(rbp)
                Node.Conditional(lhs, then, otherwise, tok.pos)
            }
            TokenType.QUESTION_QUESTION -> {
                Node.Coalesce(lhs, parseExpr(rbp), tok.pos)
            }
            TokenType.QUESTION_COLON -> {
                Node.Default(lhs, parseExpr(rbp), tok.pos)
            }
            TokenType.TILDE_GT -> {
                val rhs = parseExpr(rbp)
                Node.Apply(lhs, rhs, tok.pos)
            }
            TokenType.ASSIGN -> {
                // $variable := expr  (right-associative)
                val varNode = lhs as? Node.Variable
                    ?: throw JSONataException.S0201(tok.pos, ":=")
                Node.Assign(varNode.name, parseExpr(rbp), tok.pos)
            }
            // All regular binary operators
            else -> {
                val rhs = parseExpr(rbp)
                Node.Binary(tok.value, lhs, rhs, tok.pos)
            }
        }
    }

    // ── Infix binding powers ──────────────────────────────────────────────────

    /**
     * Returns (leftBp, rightBp) for infix token, or null if it is not infix here.
     * Left-associative: lbp == rbp.  Right-associative: rbp = lbp - 1.
     */
    private fun infixBp(tok: Token): Pair<Int, Int>? = when (tok.type) {
        // Precedence (higher binds tighter):
        //  1  :=   variable assignment    (right-assoc)
        //  2  ~>   pipe/apply             (left-assoc, lowest)
        //  4  ?    ternary                (non-assoc)
        //  6  ?? ?: coalescing            (left-assoc)
        //  8  or                          (left-assoc)
        // 12  and                         (left-assoc)
        // 18  in                          (left-assoc)
        // 20  = != < <= > >=              (left-assoc)
        // 25  ..  range                   (non-assoc)
        // 30  &   string concat           (left-assoc)
        // 35  + -  additive               (left-assoc)
        // 40  * / % multiplicative        (left-assoc)
        // 70+ dot / call / predicate      (left-assoc, postfix)

        TokenType.ASSIGN             -> 1 to 0      // right-assoc, very low
        TokenType.TILDE_GT           -> 2 to 3      // left-assoc
        TokenType.QUESTION           -> 4 to 4
        TokenType.QUESTION_QUESTION  -> 6 to 6
        TokenType.QUESTION_COLON     -> 6 to 6
        TokenType.OR                 -> 8 to 9
        TokenType.AND                -> 12 to 13
        // "in" keyword stored as NAME
        TokenType.NAME               -> if (tok.value == "in") 18 to 19 else null
        TokenType.EQ, TokenType.NEQ, TokenType.LT, TokenType.LTE,
        TokenType.GT, TokenType.GTE -> 20 to 21
        TokenType.DOTDOT             -> 25 to 25
        TokenType.AMP                -> 30 to 31
        TokenType.PLUS, TokenType.MINUS -> 35 to 36
        TokenType.STAR, TokenType.SLASH, TokenType.PERCENT -> 40 to 41
        // Postfix-like operators
        TokenType.DOT                -> 75 to 75
        TokenType.LBRACKET           -> 80 to 0
        TokenType.LBRACE             -> 70 to 0
        TokenType.LPAREN             -> 85 to 0
        TokenType.CARET              -> 70 to 0
        TokenType.AT                 -> 80 to 0
        TokenType.HASH               -> 80 to 0
        else -> null
    }

    // ── Parenthesised expression or block ─────────────────────────────────────

    private fun parseParen(pos: Int): Node {
        // empty parens = empty block
        if (check(TokenType.RPAREN)) {
            advance()
            return Node.Block(emptyList(), pos)
        }
        val exprs = mutableListOf(parseExpr(0))
        while (eat(TokenType.SEMI)) {
            if (check(TokenType.RPAREN)) break
            exprs += parseExpr(0)
        }
        expect(TokenType.RPAREN)
        return if (exprs.size == 1) exprs[0] else Node.Block(exprs, pos)
    }

    // ── Array constructor ─────────────────────────────────────────────────────

    private fun parseArrayConstructor(pos: Int): Node {
        if (check(TokenType.RBRACKET)) {
            advance()
            return Node.ArrayConstructor(emptyList(), pos)
        }
        val items = mutableListOf(parseExpr(0))
        while (eat(TokenType.COMMA)) {
            if (check(TokenType.RBRACKET)) break
            items += parseExpr(0)
        }
        expect(TokenType.RBRACKET)
        return Node.ArrayConstructor(items, pos)
    }

    // ── Object constructor ────────────────────────────────────────────────────

    private fun parseObjectConstructor(pos: Int): Node {
        val pairs = parseObjectPairs()
        expect(TokenType.RBRACE)
        return Node.ObjectConstructor(pairs, pos)
    }

    private fun parseObjectPairs(): List<Pair<Node, Node>> {
        if (check(TokenType.RBRACE)) return emptyList()
        val pairs = mutableListOf<Pair<Node, Node>>()
        do {
            if (check(TokenType.RBRACE)) break
            val key = parseExpr(0)
            expect(TokenType.COLON)
            val value = parseExpr(0)
            pairs += key to value
        } while (eat(TokenType.COMMA))
        return pairs
    }

    // ── Lambda function ───────────────────────────────────────────────────────

    private fun parseLambda(pos: Int): Node {
        // optional signature before params:  <s:n>
        val sig: String? = if (check(TokenType.LT)) {
            val sb = StringBuilder("<")
            advance()
            var depth = 1
            while (!eof() && depth > 0) {
                val t = advance()
                when (t.value) {
                    "<" -> { depth++; sb.append('<') }
                    ">" -> { depth--; if (depth > 0) sb.append('>') else sb.append('>') }
                    else -> sb.append(t.value)
                }
            }
            sb.toString()
        } else null

        expect(TokenType.LPAREN)
        val params = mutableListOf<String>()
        while (!check(TokenType.RPAREN) && !eof()) {
            val p = expect(TokenType.VARIABLE)
            params += p.value
            if (!eat(TokenType.COMMA)) break
        }
        expect(TokenType.RPAREN)
        expect(TokenType.LBRACE)
        val body = if (check(TokenType.RBRACE)) Node.Block(emptyList(), pos) else parseExpr(0)
        expect(TokenType.RBRACE)
        return Node.Lambda(params, body, sig, pos)
    }

    // ── Function call & partial application ──────────────────────────────────

    private fun parseFunctionCall(fn: Node, pos: Int): Node {
        if (check(TokenType.RPAREN)) {
            advance()
            return Node.FunctionCall(fn, emptyList(), pos)
        }
        val args = mutableListOf<Node?>()
        var hasPlaceholder = false
        do {
            if (check(TokenType.RPAREN)) break
            if (check(TokenType.QUESTION)) {
                advance()
                args += null
                hasPlaceholder = true
            } else {
                args += parseExpr(0)
            }
        } while (eat(TokenType.COMMA))
        expect(TokenType.RPAREN)
        return if (hasPlaceholder) {
            Node.PartialApplication(fn, args, pos)
        } else {
            Node.FunctionCall(fn, args.map { it!! }, pos)
        }
    }

    // ── Transform ─────────────────────────────────────────────────────────────

    private fun parseTransform(pos: Int): Node {
        val pattern = parseExpr(0)
        expect(TokenType.PIPE)
        val update = parseExpr(0)
        val delete: Node? = if (eat(TokenType.COMMA)) parseExpr(0) else null
        expect(TokenType.PIPE)
        return Node.Transform(pattern, update, delete, pos)
    }

    // ── Sort terms ────────────────────────────────────────────────────────────

    private fun parseSortTerms(): List<Node.SortTerm> {
        val terms = mutableListOf<Node.SortTerm>()
        do {
            val descending = if (check(TokenType.GT)) { advance(); true }
                             else if (check(TokenType.LT)) { advance(); false }
                             else false
            val expr = parseExpr(0)
            terms += Node.SortTerm(descending, expr)
        } while (eat(TokenType.COMMA))
        return terms
    }

    // ── Regex ─────────────────────────────────────────────────────────────────

    private fun parseRegex(tok: Token): Node {
        val raw = tok.value
        val separatorIdx = raw.lastIndexOf('/')
        return if (separatorIdx > 0) {
            val pattern = raw.substring(0, separatorIdx)
            val flags   = raw.substring(separatorIdx + 1)
            Node.RegexLit(pattern, flags, tok.pos)
        } else {
            Node.RegexLit(raw, "", tok.pos)
        }
    }

    // ── Path building ─────────────────────────────────────────────────────────

    private fun buildPath(lhs: Node, rhs: Node, pos: Int): Node {
        val steps = mutableListOf<Node>()
        when (lhs) {
            is Node.Path -> steps.addAll(lhs.steps)
            else -> steps.add(lhs)
        }
        when (rhs) {
            is Node.Path -> steps.addAll(rhs.steps)
            else -> steps.add(rhs)
        }
        return Node.Path(steps, pos)
    }
}
