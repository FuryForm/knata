package com.furyform.knata.parser

/**
 * AST node types for JSONata 2.x.
 *
 * All evaluation is done by walking this sealed hierarchy.
 */
sealed class Node(open val pos: Int) {

    // ── Literals ─────────────────────────────────────────────────────────────

    /** Numeric literal - stored as Double for JS compatibility */
    data class NumberLit(val value: Double, override val pos: Int = 0) : Node(pos)

    /** String literal */
    data class StringLit(val value: String, override val pos: Int = 0) : Node(pos)

    /** Boolean literal */
    data class BoolLit(val value: Boolean, override val pos: Int = 0) : Node(pos)

    /** null literal */
    data class NullLit(override val pos: Int = 0) : Node(pos)

    // ── References ───────────────────────────────────────────────────────────

    /** Field name: `foo` */
    data class Name(val name: String, override val pos: Int = 0) : Node(pos)

    /** Variable reference: `$foo`, `$` (=empty string for root), `$$` (=root dollar) */
    data class Variable(val name: String, override val pos: Int = 0) : Node(pos)

    // ── Regex ─────────────────────────────────────────────────────────────────

    /** Regex literal: /pattern/flags */
    data class RegexLit(val pattern: String, val flags: String, override val pos: Int = 0) : Node(pos)

    // ── Path step operators ───────────────────────────────────────────────────

    /** Wildcard: `*` */
    data class Wildcard(override val pos: Int = 0) : Node(pos)

    /** Descendants: `**` */
    data class Descendants(override val pos: Int = 0) : Node(pos)

    /** Parent operator: `%` – not a real token, used internally */
    data class Parent(override val pos: Int = 0) : Node(pos)

    // ── Binary operators ─────────────────────────────────────────────────────

    /** Any binary operation: +  -  *  /  %  &  =  !=  <  <=  >  >=  and  or  in  ..  */
    data class Binary(
        val op: String,
        val lhs: Node,
        val rhs: Node,
        override val pos: Int = 0,
    ) : Node(pos)

    /** Unary minus */
    data class Unary(val op: String, val expr: Node, override val pos: Int = 0) : Node(pos)

    // ── Path navigation ───────────────────────────────────────────────────────

    /** Path step:  a.b  ->  Path([Name("a"), Name("b")]) */
    data class Path(val steps: List<Node>, override val pos: Int = 0) : Node(pos)

    /** Array predicate:  expr[predicate] */
    data class Predicate(val expr: Node, val predicate: Node, override val pos: Int = 0) : Node(pos)

    // ── Array / object constructors ───────────────────────────────────────────

    /** Array constructor: [a, b, c] */
    data class ArrayConstructor(val items: List<Node>, override val pos: Int = 0) : Node(pos)

    /** Object constructor: {key: value, ...} */
    data class ObjectConstructor(val pairs: List<Pair<Node, Node>>, override val pos: Int = 0) : Node(pos)

    // ── Conditional ───────────────────────────────────────────────────────────

    /** Conditional:  cond ? then : else  */
    data class Conditional(
        val condition: Node,
        val then: Node,
        val otherwise: Node?,
        override val pos: Int = 0,
    ) : Node(pos)

    // ── Variable assignment ───────────────────────────────────────────────────

    /** Variable binding:  $x := expr */
    data class Assign(val name: String, val value: Node, override val pos: Int = 0) : Node(pos)

    // ── Block ─────────────────────────────────────────────────────────────────

    /** Parenthesised block:  (expr; expr; ...) */
    data class Block(val exprs: List<Node>, override val pos: Int = 0) : Node(pos)

    // ── Functions ────────────────────────────────────────────────────────────

    /** Lambda:  function($a, $b) { body }  or  λ($a) { body } */
    data class Lambda(
        val params: List<String>,
        val body: Node,
        val signature: String?,  // optional signature string e.g. "<s:n>"
        override val pos: Int = 0,
    ) : Node(pos)

    /** Function application:  expr(args...) */
    data class FunctionCall(
        val fn: Node,
        val args: List<Node>,
        override val pos: Int = 0,
    ) : Node(pos)

    /** Partial application:  fn(arg, ?, ...) */
    data class PartialApplication(
        val fn: Node,
        val args: List<Node?>,  // null = placeholder '?'
        override val pos: Int = 0,
    ) : Node(pos)

    /** Placeholder in partial application */
    data class Placeholder(override val pos: Int = 0) : Node(pos)

    /** Function composition via ~>:  fn1 ~> fn2 */
    data class FunctionCompose(val lhs: Node, val rhs: Node, override val pos: Int = 0) : Node(pos)

    // ── Transform ────────────────────────────────────────────────────────────

    /** Transform:  | expr | update [, delete] | */
    data class Transform(
        val pattern: Node,
        val update: Node,
        val delete: Node?,
        override val pos: Int = 0,
    ) : Node(pos)

    // ── Sort ─────────────────────────────────────────────────────────────────

    /** Sort:  expr^(term1, term2, ...)  */
    data class Sort(val expr: Node, val terms: List<SortTerm>, override val pos: Int = 0) : Node(pos)

    data class SortTerm(val descending: Boolean, val expr: Node)

    // ── Group / aggregation ───────────────────────────────────────────────────

    /** Object grouping:  expr{key:value} */
    data class Group(
        val expr: Node,
        val pairs: List<Pair<Node, Node>>,
        override val pos: Int = 0,
    ) : Node(pos)

    // ── Focus / index binding ─────────────────────────────────────────────────

    /** Focus binding:  expr@$var */
    data class FocusBind(val expr: Node, val varName: String, override val pos: Int = 0) : Node(pos)

    /** Index binding:  expr#$i */
    data class IndexBind(val expr: Node, val varName: String, override val pos: Int = 0) : Node(pos)

    // ── Join operator ─────────────────────────────────────────────────────────

    /** Join:  lhs@$l.rhs@$r[...] – represented as regular path with FocusBind nodes */

    // ── Coalescing / default ──────────────────────────────────────────────────

    /** Null coalescing: a ?? b  (returns b if a is undefined/null) */
    data class Coalesce(val lhs: Node, val rhs: Node, override val pos: Int = 0) : Node(pos)

    /** Elvis / default: a ?: b */
    data class Default(val lhs: Node, val rhs: Node, override val pos: Int = 0) : Node(pos)

    // ── String concatenation ──────────────────────────────────────────────────
    // (handled as Binary with op = "&")

    // ── Pipe  ─────────────────────────────────────────────────────────────────

    /** Pipe chain:  expr ~> fn  */
    // handled as FunctionCompose

    // ── Misc ─────────────────────────────────────────────────────────────────

    /** Apply single-argument pipe:  value ~> $fn */
    data class Apply(val lhs: Node, val rhs: Node, override val pos: Int = 0) : Node(pos)
}
