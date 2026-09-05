/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.*;
import java.util.*;

/**
 * Generates minified JavaScript code with munged variable names.
 * Supports ES6+ syntax including arrow functions, template literals, and classes.
 */
public class MungedCodeGenerator {

    /**
     * System property that turns the unhandled-node fallback into a hard
     * failure. Set it (to any value) to make {@link #generate} throw
     * {@link UnsupportedSyntaxException} instead of falling back to
     * {@code toSource()}, which emits un-munged identifiers and drops "?.".
     * Off by default so existing callers keep today's behaviour; the test
     * suite runs the whole fixture corpus plus a modern-syntax table with it
     * on, so a newly-unhandled node type breaks the build instead of
     * silently corrupting output.
     *
     * <p><b>Known strict-mode limitations.</b> Six node types Rhino 1.8.0 still
     * parses have no handler and therefore throw under strict mode. All six are
     * Rhino/E4X legacy that no browser supports, and all six are harmless in
     * the default lenient path, so they are recorded rather than fixed:
     *
     * <table>
     * <caption>Node types with no handler</caption>
     * <tr><td>{@code ARRAYCOMP} (171)</td><td>{@code [i*a for (i in a)]}</td></tr>
     * <tr><td>{@code GENEXPR} (176)</td><td>{@code (i*a for (i in a))}</td></tr>
     * <tr><td>{@code XML} (159)</td><td>{@code <a b="1">{a}</a>}</td></tr>
     * <tr><td>{@code REF_NAME} (87)</td><td>{@code a::b}</td></tr>
     * <tr><td>{@code DOTDOT} (157)</td><td>{@code a..b}</td></tr>
     * <tr><td>{@code DOT} (121) as {@code XmlMemberGet}</td><td>{@code a.@b}</td></tr>
     * </table>
     *
     * <p>The comprehension forms would need real emission support because they
     * contain identifiers that must be munged; the four E4X forms are a dead
     * language extension. This list is enumerated, not sampled - it is what a
     * sweep of the constructs Rhino accepts actually produced - so a seventh
     * appearing means a genuine change, not a gap in the probe.
     *
     * <p>{@code BIGINT} (89) used to be on this list and is now handled: it is
     * standard ES2020 rather than legacy, and strict mode could not compress
     * any file containing a BigInt literal.
     */
    public static final String STRICT_PROPERTY = "yuicompressor.strict";

    /**
     * Thrown when the generator cannot faithfully reproduce a construct -
     * either an unhandled node type under {@link #STRICT_PROPERTY}, or a
     * parameter whose full syntax Rhino does not expose. Failing loudly is
     * deliberate: the alternative is output that parses but means something
     * different.
     */
    public static class UnsupportedSyntaxException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UnsupportedSyntaxException(String message) {
            super(message);
        }
    }

    /**
     * Whether strict mode is on. The property is read as a boolean, not merely
     * tested for presence: {@code -Dyuicompressor.strict=false} has to mean OFF.
     * Presence alone used to mean ON, so every spelling a person reaches for to
     * disable a flag - {@code false}, {@code 0}, {@code off}, {@code no}, and the
     * empty string a shell leaves behind for a bare {@code -Dyuicompressor.strict}
     * - turned it on instead, and the compressor refused files it compresses fine
     * by default. Only {@code true} (in any case) enables it.
     */
    private static boolean isStrict() {
        return Boolean.parseBoolean(System.getProperty(STRICT_PROPERTY));
    }

    private ScopeBuilder scopeBuilder;
    private boolean munge;
    private StringBuilder output;
    // Offsets into "output" where a line break may be safely inserted: right
    // after a top-level statement in a script/block/scope body, or right after
    // a statement inside a switch case, has finished (its trailing ";" already
    // appended when one is needed, or its closing "}" already written). Never
    // recorded inside a string, template literal or regex literal, since those
    // are always emitted as a single atomic append. addLineBreaks() in
    // JavaScriptCompressor breaks only at these offsets, so a line break can
    // never land inside a token.
    private List<Integer> safeBreakOffsets;
    // The original source text. Rhino's QUESTION_DOT type marks every link of an
    // optional chain (e.g. both accesses in "a?.b.c"), not just the one that is
    // actually optional, and FunctionCall.isOptionalCall() over-reports the same
    // way. The only reliable way to know whether one particular link carries "?."
    // is to look at the real source text between that link's target and its
    // property/element/argument list. May be null (e.g. legacy callers/tests that
    // build an AST without an associated source string); optional chaining then
    // conservatively falls back to the plain, non-optional form.
    private String source;

    public MungedCodeGenerator(ScopeBuilder scopeBuilder, boolean munge) {
        this(scopeBuilder, munge, null);
    }

    public MungedCodeGenerator(ScopeBuilder scopeBuilder, boolean munge, String source) {
        this.scopeBuilder = scopeBuilder;
        this.munge = munge;
        this.source = source;
        this.output = new StringBuilder();
        this.safeBreakOffsets = new ArrayList<>();
    }

    public String generate(AstRoot root) {
        output.setLength(0);
        safeBreakOffsets.clear();
        visitNode(root);
        return output.toString();
    }

    /**
     * Offsets into the string returned by {@link #generate}, in ascending
     * order, where a line break may be inserted without splitting a token.
     */
    public List<Integer> getSafeBreakOffsets() {
        return safeBreakOffsets;
    }

    private void markSafeBreak() {
        safeBreakOffsets.add(output.length());
    }

    /**
     * Marks a break only where the text so far ends at a statement separator.
     *
     * <p>A statement that gave up its ";" because a "}" follows is still a
     * statement boundary - a line break there is covered by automatic semicolon
     * insertion - but nothing in the output says so, and a reader checking the
     * break offsets cannot tell it from a break in the middle of an identifier.
     * Not offering the position keeps every recorded break verifiable; the "}"
     * that follows immediately offers one anyway.
     */
    private void markSafeBreakIfAtSeparator() {
        int length = output.length();
        if (length > 0) {
            char last = output.charAt(length - 1);
            if (last == ';' || last == '}') {
                markSafeBreak();
            }
        }
    }

    private void visitNode(AstNode node) {
        if (node == null) {
            return;
        }

        int type = node.getType();

        switch (type) {
            // Core structure
            case Token.SCRIPT:
                visitScript((AstRoot) node);
                break;
            case Token.FUNCTION:
                visitFunction((FunctionNode) node);
                break;
            case Token.BLOCK:
                // Both Block and Scope can have Token.BLOCK type
                if (node instanceof Scope) {
                    visitScope((Scope) node);
                } else {
                    visitBlock((Block) node);
                }
                break;

            // Variables and identifiers
            case Token.NAME:
                visitName((Name) node);
                break;
            case Token.VAR:
            case Token.LET:
            case Token.CONST:
                visitVariableDeclaration((VariableDeclaration) node);
                break;

            // Statements
            case Token.EXPR_RESULT:
            case Token.EXPR_VOID:
                // LabeledStatement.getType() also reports Token.EXPR_VOID (see
                // its class comment) rather than Token.LABEL, so it lands here
                // rather than in a dedicated LABEL case; without this check it
                // would be miscast to ExpressionStatement below.
                if (node instanceof LabeledStatement) {
                    visitLabeledStatement((LabeledStatement) node);
                } else {
                    visitExpressionStatement((ExpressionStatement) node);
                }
                break;
            case Token.RETURN:
                visitReturnStatement((ReturnStatement) node);
                break;
            case Token.IF:
                visitIfStatement((IfStatement) node);
                break;
            case Token.FOR:
                // ForInLoop and ForLoop share the same token type, check ForInLoop first
                if (node instanceof ForInLoop) {
                    visitForInLoop((ForInLoop) node);
                } else {
                    visitForLoop((ForLoop) node);
                }
                break;
            case Token.WHILE:
                visitWhileLoop((WhileLoop) node);
                break;
            case Token.DO:
                visitDoLoop((DoLoop) node);
                break;
            case Token.SWITCH:
                visitSwitchStatement((SwitchStatement) node);
                break;
            case Token.CASE:
                // Reached only when a case is visited outside its switch, where
                // there is no following "}" to end the last statement.
                visitSwitchCase((SwitchCase) node, false);
                break;
            case Token.BREAK:
                visitBreakStatement((BreakStatement) node);
                break;
            case Token.CONTINUE:
                visitContinueStatement((ContinueStatement) node);
                break;
            case Token.THROW:
                visitThrowStatement((ThrowStatement) node);
                break;
            case Token.TRY:
                visitTryStatement((TryStatement) node);
                break;
            case Token.EMPTY:
                // Empty statement, nothing to output
                break;
            case Token.DEBUGGER:
                // Handled here rather than by the fallback: toSource() emits
                // "debugger;\n", whose trailing newline breaks the "minified
                // output is a single line" premise that insertSeparatorIfMerging's
                // Annex B reasoning rests on, and whose ";" then gets a second one
                // from needsSemicolon().
                output.append("debugger");
                break;
            // No "case Token.LABEL:" here: Label (the per-name marker inside a
            // LabeledStatement's label list) is the only AST node that actually
            // reports Token.LABEL, and it is never dispatched through
            // visitNode() - visitLabeledStatement() below reads
            // labeled.getLabels() directly. A LabeledStatement itself reports
            // Token.EXPR_VOID (handled above), never Token.LABEL. A case here
            // casting to LabeledStatement was therefore both unreachable and
            // wrong (a real Token.LABEL node cannot be cast to
            // LabeledStatement); removed rather than left as dead code.
            case Token.WITH:
                visitWithStatement((WithStatement) node);
                break;

            // Literals
            case Token.NUMBER:
                output.append(((NumberLiteral) node).getValue());
                break;
            case Token.BIGINT:
                // Standard ES2020. getValue() is the source text, "n" suffix and
                // hex form included; toSource() would normalise "0xffn" to
                // "255n", which is correct but needlessly rewrites the literal.
                // Harmless in the lenient path (a leaf with no identifiers
                // inside), but without a case here strict mode cannot compress
                // any file containing a BigInt at all.
                output.append(((BigIntLiteral) node).getValue());
                break;
            case Token.STRING:
                visitStringLiteral((StringLiteral) node);
                break;
            case Token.TRUE:
                output.append("true");
                break;
            case Token.FALSE:
                output.append("false");
                break;
            case Token.NULL:
                output.append("null");
                break;
            case Token.THIS:
                output.append("this");
                break;
            case Token.REGEXP:
                visitRegExpLiteral((RegExpLiteral) node);
                break;
            case Token.OBJECTLIT:
                visitObjectLiteral((ObjectLiteral) node);
                break;
            case Token.ARRAYLIT:
                visitArrayLiteral((ArrayLiteral) node);
                break;

            // Assignment operators
            case Token.ASSIGN:
                visitInfixExpression((InfixExpression) node, "=");
                break;
            case Token.ASSIGN_ADD:
                visitInfixExpression((InfixExpression) node, "+=");
                break;
            case Token.ASSIGN_SUB:
                visitInfixExpression((InfixExpression) node, "-=");
                break;
            case Token.ASSIGN_MUL:
                visitInfixExpression((InfixExpression) node, "*=");
                break;
            case Token.ASSIGN_DIV:
                visitInfixExpression((InfixExpression) node, "/=");
                break;
            case Token.ASSIGN_MOD:
                visitInfixExpression((InfixExpression) node, "%=");
                break;
            case Token.ASSIGN_BITAND:
                visitInfixExpression((InfixExpression) node, "&=");
                break;
            case Token.ASSIGN_BITOR:
                visitInfixExpression((InfixExpression) node, "|=");
                break;
            case Token.ASSIGN_BITXOR:
                visitInfixExpression((InfixExpression) node, "^=");
                break;
            case Token.ASSIGN_LSH:
                visitInfixExpression((InfixExpression) node, "<<=");
                break;
            case Token.ASSIGN_RSH:
                visitInfixExpression((InfixExpression) node, ">>=");
                break;
            case Token.ASSIGN_URSH:
                visitInfixExpression((InfixExpression) node, ">>>=");
                break;
            case Token.ASSIGN_EXP:
                visitInfixExpression((InfixExpression) node, "**=");
                break;
            // Logical assignment (ES2021). These MUST be visited rather than
            // left to the toSource() fallback: toSource() re-prints the
            // original source of the whole subtree, so every identifier in it
            // keeps its pre-munge spelling while its declaration is munged,
            // turning locals into globals silently. Rhino models all four as
            // Assignment, which extends InfixExpression.
            case Token.ASSIGN_LOGICAL_OR:
                visitInfixExpression((InfixExpression) node, "||=");
                break;
            case Token.ASSIGN_LOGICAL_AND:
                visitInfixExpression((InfixExpression) node, "&&=");
                break;
            case Token.ASSIGN_NULLISH:
                visitInfixExpression((InfixExpression) node, "??=");
                break;

            // Arithmetic operators
            case Token.ADD:
                visitInfixExpression((InfixExpression) node, "+");
                break;
            case Token.SUB:
                visitInfixExpression((InfixExpression) node, "-");
                break;
            case Token.MUL:
                visitInfixExpression((InfixExpression) node, "*");
                break;
            case Token.DIV:
                visitInfixExpression((InfixExpression) node, "/");
                break;
            case Token.MOD:
                visitInfixExpression((InfixExpression) node, "%");
                break;
            case Token.EXP:
                visitInfixExpression((InfixExpression) node, "**");
                break;

            // Comparison operators
            case Token.EQ:
                visitInfixExpression((InfixExpression) node, "==");
                break;
            case Token.NE:
                visitInfixExpression((InfixExpression) node, "!=");
                break;
            case Token.SHEQ:
                visitInfixExpression((InfixExpression) node, "===");
                break;
            case Token.SHNE:
                visitInfixExpression((InfixExpression) node, "!==");
                break;
            case Token.LT:
                visitInfixExpression((InfixExpression) node, "<");
                break;
            case Token.LE:
                visitInfixExpression((InfixExpression) node, "<=");
                break;
            case Token.GT:
                visitInfixExpression((InfixExpression) node, ">");
                break;
            case Token.GE:
                visitInfixExpression((InfixExpression) node, ">=");
                break;

            // Logical operators
            case Token.AND:
                visitInfixExpression((InfixExpression) node, "&&");
                break;
            case Token.OR:
                visitInfixExpression((InfixExpression) node, "||");
                break;
            case Token.NULLISH_COALESCING:
                // "??" cannot be mixed with "||" / "&&" without parentheses;
                // Rhino records those parentheses as ParenthesizedExpression
                // (Token.LP) nodes, which visitParenthesizedExpression reprints,
                // so routing "??" through the ordinary infix path preserves them.
                visitInfixExpression((InfixExpression) node, "??");
                break;
            case Token.NOT:
                visitUnaryExpression((UnaryExpression) node, "!");
                break;

            // Bitwise operators
            case Token.BITAND:
                visitInfixExpression((InfixExpression) node, "&");
                break;
            case Token.BITOR:
                visitInfixExpression((InfixExpression) node, "|");
                break;
            case Token.BITXOR:
                visitInfixExpression((InfixExpression) node, "^");
                break;
            case Token.BITNOT:
                visitUnaryExpression((UnaryExpression) node, "~");
                break;
            case Token.LSH:
                visitInfixExpression((InfixExpression) node, "<<");
                break;
            case Token.RSH:
                visitInfixExpression((InfixExpression) node, ">>");
                break;
            case Token.URSH:
                visitInfixExpression((InfixExpression) node, ">>>");
                break;

            // Unary operators
            case Token.POS:
                visitUnaryExpression((UnaryExpression) node, "+");
                break;
            case Token.NEG:
                visitUnaryExpression((UnaryExpression) node, "-");
                break;
            case Token.TYPEOF:
                visitKeywordUnary((UnaryExpression) node, "typeof");
                break;
            case Token.VOID:
                visitKeywordUnary((UnaryExpression) node, "void");
                break;
            case Token.DELPROP:
                visitKeywordUnary((UnaryExpression) node, "delete");
                break;

            // Increment/Decrement
            case Token.INC:
            case Token.DEC:
                visitUpdateExpression((UpdateExpression) node);
                break;

            // Other operators
            case Token.COMMA:
                visitInfixExpression((InfixExpression) node, ",");
                break;
            case Token.HOOK:
                visitConditionalExpression((ConditionalExpression) node);
                break;
            case Token.IN:
                visitInfixExpression((InfixExpression) node, " in ");
                break;
            case Token.INSTANCEOF:
                visitInfixExpression((InfixExpression) node, " instanceof ");
                break;

            // Member access
            case Token.CALL:
                visitFunctionCall((FunctionCall) node);
                break;
            case Token.NEW:
                visitNewExpression((NewExpression) node);
                break;
            case Token.GETPROP:
                visitPropertyGet((PropertyGet) node);
                break;
            case Token.GETELEM:
                visitElementGet((ElementGet) node);
                break;
            case Token.QUESTION_DOT:
                // Rhino keeps optional chaining as QUESTION_DOT on an ordinary
                // PropertyGet / ElementGet node, and its own toSource() drops the
                // "?.", so it has to be printed here rather than by the fallback.
                // QUESTION_DOT marks every link of the chain though (see
                // isOptionalGap()), so whether *this* link gets "?." or a plain "."
                // depends on the actual source text, not the node type alone.
                if (node instanceof ElementGet) {
                    ElementGet elementGet = (ElementGet) node;
                    AstNode target = elementGet.getTarget();
                    AstNode element = elementGet.getElement();
                    visitNode(target);
                    output.append(isOptionalGap(target, element.getAbsolutePosition()) ? "?.[" : "[");
                    visitNode(element);
                    output.append("]");
                } else if (node instanceof PropertyGet) {
                    PropertyGet propertyGet = (PropertyGet) node;
                    AstNode target = propertyGet.getTarget();
                    AstNode property = propertyGet.getProperty();
                    visitNode(target);
                    output.append(isOptionalGap(target, property.getAbsolutePosition()) ? "?." : ".");
                    if (property instanceof Name) {
                        output.append(((Name) property).getIdentifier());
                    } else {
                        output.append(property.toSource());
                    }
                } else {
                    output.append(node.toSource());
                }
                break;

            // ES6+ features
            case Token.ARROW:
                visitArrowFunction((FunctionNode) node);
                break;
            case Token.TEMPLATE_LITERAL:
                visitTemplateLiteral((TemplateLiteral) node);
                break;
            case Token.TAGGED_TEMPLATE_LITERAL:
                visitTaggedTemplateLiteral((TaggedTemplateLiteral) node);
                break;

            // Parenthesized expression
            case Token.LP:
                visitParenthesizedExpression((ParenthesizedExpression) node);
                break;

            // Spread and yield
            case Token.YIELD:
            case Token.YIELD_STAR:
                // Yield's constructor sets its own type to Token.YIELD_STAR
                // (not Token.YIELD) for "yield* x" - see visitYield(), which
                // reads the node's actual type back off to decide whether to
                // print the "*".
                visitYield((Yield) node);
                break;

            default:
                appendFallback(node);
                break;
        }
    }

    /**
     * Last resort for a construct this generator cannot reproduce: re-print it
     * with {@code toSource()}.
     *
     * <p>This is not merely "unsupported nodes print oddly". {@code toSource()}
     * re-prints the ORIGINAL source of the whole subtree, so every identifier
     * inside keeps its pre-munge spelling while its declaration was munged -
     * silently turning locals into globals - and it drops "?." entirely. The
     * result parses, so neither a golden comparison nor {@code node --check}
     * notices. Anything that lands here is a latent silent-corruption bug, and
     * upgrading Rhino (Release 2) makes more syntax parse, which routes MORE
     * node types here.
     *
     * <p>{@link #STRICT_PROPERTY} turns that latent bug into a build break. It
     * is off by default so existing callers keep today's behaviour.
     */
    private void appendFallback(AstNode node) {
        int type = node.getType();
        if (isStrict()) {
            throw new UnsupportedSyntaxException(
                "no handler for node type " + type + " (" +
                Token.typeToName(type) + ", " + node.getClass().getName() +
                "); the toSource() fallback would emit un-munged identifiers");
        }
        if (System.getProperty("yuicompressor.debug") != null) {
            System.err.println("Warning: Using toSource() for unsupported node type: " +
                type + " (" + node.getClass().getSimpleName() + ")");
        }
        output.append(node.toSource());
    }

    private void visitScript(AstRoot script) {
        for (Node child : script) {
            if (child instanceof AstNode) {
                visitNode((AstNode) child);
                if (needsSemicolon((AstNode) child)) {
                    output.append(";");
                }
                markSafeBreak();
            }
        }
    }

    private void visitFunction(FunctionNode fn) {
        // Check if this is an arrow function
        if (fn.getFunctionType() == FunctionNode.ARROW_FUNCTION) {
            visitArrowFunction(fn);
            return;
        }

        // Generator function
        if (fn.isGenerator()) {
            output.append("function*");
        } else {
            output.append("function");
        }

        // Function name
        Name fnName = fn.getFunctionName();
        if (fnName != null) {
            output.append(" ");
            output.append(fnName.getIdentifier());
        }

        output.append("(");

        // Parameters
        List<AstNode> params = fn.getParams();
        visitParameterList(params, fn);

        output.append(")");

        // Body
        AstNode body = fn.getBody();
        if (body != null) {
            visitNode(body);
        }
    }

    private void visitArrowFunction(FunctionNode arrow) {
        List<AstNode> params = arrow.getParams();

        // Parameters. The bare form is only available for a single plain
        // identifier with no default: "(a=1)=>a" cannot lose its parentheses,
        // and dropping them here would take the "=1" with them.
        if (params.size() == 1 && params.get(0) instanceof Name && !arrow.hasRestParameter()
                && collectDefaultParams(arrow).isEmpty()) {
            // Single parameter without parentheses (may need them for munging consistency)
            String paramName = ((Name) params.get(0)).getIdentifier();
            output.append(getMungedName(paramName, arrow));
        } else {
            output.append("(");
            visitParameterList(params, arrow);
            output.append(")");
        }

        output.append("=>");

        // Body
        AstNode body = arrow.getBody();
        if (body != null) {
            // Check if body is a block or a single expression
            if (body instanceof Block) {
                visitNode(body);
            } else {
                // Single expression - no braces needed
                visitNode(body);
            }
        }
    }

    /**
     * Emits a function's parameter list.
     *
     * <p>Rhino spreads a parameter's full syntax across three places rather
     * than one. {@code getParams()} carries only the binding target - a rest
     * parameter appears there as a plain {@code Name}, exactly like an
     * ordinary one. {@code hasRestParameter()} says whether the last one is a
     * rest parameter. {@code getDefaultParams()} carries a flat, alternating
     * list of original parameter NAME and default-value expression, and
     * records nothing at all for a destructuring pattern's default.
     *
     * <p>Reading only {@code getParams()}, as this used to, silently dropped
     * both "..." and "= &lt;default&gt;":
     *
     * <pre>
     * function f(a=1){ return a; }               -&gt; function f(a){return a;}
     * function f(...args){ return args.length; } -&gt; function f(args){return args.length;}
     * </pre>
     *
     * which changes {@code f()} from 1 to undefined, and turns an array of the
     * trailing arguments into a single positional parameter (also changing
     * {@code f.length}). Both are silent behaviour changes on syntax Rhino
     * parses happily.
     *
     * <p>A parameter whose full syntax cannot be reconstructed throws rather
     * than emitting a truncated list. The case that reaches this is a
     * destructuring pattern carrying a default - {@code function f({b}={})} -
     * whose "={}" Rhino records nowhere. The original source is consulted to
     * tell that apart from a plain {@code function f({b})}, which is
     * reproduced exactly and must keep working.
     */
    private void visitParameterList(List<AstNode> params, FunctionNode fn) {
        Map<String, AstNode> defaults = collectDefaultParams(fn);
        // A rest parameter is always the last one; Rhino flags it on the
        // function rather than on the parameter node.
        int restIndex = fn.hasRestParameter() ? params.size() - 1 : -1;
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) output.append(",");
            AstNode param = params.get(i);
            if (i == restIndex) {
                output.append("...");
            }
            if (param instanceof Name) {
                String paramName = ((Name) param).getIdentifier();
                output.append(getMungedName(paramName, fn));
                AstNode defaultValue = defaults.get(paramName);
                if (defaultValue != null) {
                    output.append("=");
                    // Visited, not printed: identifiers inside the default
                    // expression must be munged like any other. Rhino keeps
                    // the expression in a side list with no parent link back
                    // to the function, so findScopeForVariable's walk would
                    // run straight off the top and resolve every name against
                    // the GLOBAL scope - "function f(alpha, beta=alpha)" would
                    // emit "function f(b,a=alpha)", making alpha a global.
                    // Restoring the link the node should have had is enough;
                    // it is idempotent and the node is otherwise unreferenced.
                    if (defaultValue.getParent() == null) {
                        defaultValue.setParent(fn);
                    }
                    visitNode(defaultValue);
                }
            } else if (hasDefaultInSource(param)) {
                throw new UnsupportedSyntaxException(
                    "cannot reconstruct the default value of destructuring parameter " + (i + 1)
                        + " of " + describe(fn) + "; Rhino records it nowhere, and emitting the "
                        + "pattern without it would silently change what the function does");
            } else {
                // A destructuring pattern with no default reproduces exactly.
                visitNode(param);
            }
        }
    }

    /**
     * Reads {@code getDefaultParams()}'s flat name/value pairs into a map keyed
     * by the parameter's ORIGINAL (pre-munge) name, which is what Rhino stores.
     * An unexpected shape throws rather than being skipped: silently dropping a
     * default is the defect this method exists to fix.
     */
    private static Map<String, AstNode> collectDefaultParams(FunctionNode fn) {
        List<Object> pairs = fn.getDefaultParams();
        if (pairs == null || pairs.isEmpty()) {
            return Collections.emptyMap();
        }
        if (pairs.size() % 2 != 0) {
            throw new UnsupportedSyntaxException("FunctionNode.getDefaultParams() returned " + pairs.size()
                + " entries for " + describe(fn) + "; expected name/value pairs");
        }
        Map<String, AstNode> defaults = new HashMap<>();
        for (int i = 0; i < pairs.size(); i += 2) {
            Object name = pairs.get(i);
            Object value = pairs.get(i + 1);
            if (!(name instanceof String) || !(value instanceof AstNode)) {
                throw new UnsupportedSyntaxException("unexpected FunctionNode.getDefaultParams() entry at index "
                    + i + " of " + describe(fn) + ": " + className(name) + " / " + className(value));
            }
            defaults.put((String) name, (AstNode) value);
        }
        return defaults;
    }

    /**
     * Whether the original source has a "=" immediately after {@code param},
     * i.e. the parameter carries a default value. Only asked about
     * destructuring patterns, for which Rhino records no default at all, so
     * {@code function f({b})} and {@code function f({b}={})} produce identical
     * parameter nodes and only the source text can tell them apart.
     *
     * <p>Returns true when there is no source to consult: an unseen default is
     * exactly the silent drop this check exists to prevent, so the answer that
     * leads to a thrown error is the safe one.
     */
    private boolean hasDefaultInSource(AstNode param) {
        if (source == null) {
            return true;
        }
        int i = param.getAbsolutePosition() + param.getLength();
        if (i < 0 || i > source.length()) {
            return true;
        }
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < source.length()) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    int end = i + 2;
                    while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '\r') {
                        end++;
                    }
                    i = end;
                    continue;
                }
                if (next == '*') {
                    int end = source.indexOf("*/", i + 2);
                    if (end < 0) {
                        return true; // unterminated comment: don't guess
                    }
                    i = end + 2;
                    continue;
                }
            }
            // Anything else ends the question: "," or ")" means no default.
            return c == '=';
        }
        return false;
    }

    private static String describe(FunctionNode fn) {
        String name = fn.getName();
        return (name == null || name.isEmpty() ? "an anonymous function" : "function " + name)
            + " at line " + fn.getLineno();
    }

    private static String className(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }

    private void visitName(Name name) {
        String identifier = name.getIdentifier();

        // Check if this is a property access
        AstNode parent = name.getParent();
        if (parent instanceof PropertyGet) {
            PropertyGet pg = (PropertyGet) parent;
            if (pg.getProperty() == name) {
                // This is a property name, don't munge it
                output.append(identifier);
                return;
            }
        }

        // Check if this is an object property key
        if (parent instanceof ObjectProperty) {
            ObjectProperty prop = (ObjectProperty) parent;
            if (prop.getLeft() == name && !prop.isShorthand()) {
                // This is a property key, don't munge it
                output.append(identifier);
                return;
            }
        }

        // Munge the variable name
        output.append(getMungedName(identifier, name));
    }

    private void visitVariableDeclaration(VariableDeclaration varDecl) {
        int declType = varDecl.getType();
        if (declType == Token.LET) {
            output.append("let ");
        } else if (declType == Token.CONST) {
            output.append("const ");
        } else {
            output.append("var ");
        }

        List<VariableInitializer> variables = varDecl.getVariables();
        for (int i = 0; i < variables.size(); i++) {
            if (i > 0) output.append(",");

            VariableInitializer vi = variables.get(i);
            AstNode target = vi.getTarget();

            if (target instanceof Name) {
                String varName = ((Name) target).getIdentifier();
                output.append(getMungedName(varName, varDecl));
            } else {
                // Destructuring pattern
                visitNode(target);
            }

            AstNode initializer = vi.getInitializer();
            if (initializer != null) {
                output.append("=");
                visitNode(initializer);
            }
        }
    }

    private void visitExpressionStatement(ExpressionStatement stmt) {
        visitNode(stmt.getExpression());
    }

    private void visitReturnStatement(ReturnStatement ret) {
        output.append("return");
        AstNode value = ret.getReturnValue();
        if (value != null) {
            if (needsSpaceBeforeExpression(value)) {
                output.append(" ");
            }
            visitNode(value);
        }
    }

    private void visitIfStatement(IfStatement ifStmt) {
        output.append("if(");
        visitNode(ifStmt.getCondition());
        output.append(")");

        AstNode thenPart = ifStmt.getThenPart();
        boolean needsBraces = !isBraced(thenPart);
        if (needsBraces) output.append("{");
        visitNode(thenPart);
        if (needsBraces) output.append("}");

        AstNode elsePart = ifStmt.getElsePart();
        if (elsePart != null) {
            output.append("else");
            if (elsePart instanceof IfStatement) {
                output.append(" ");
                visitNode(elsePart);
            } else {
                boolean elseNeedsBraces = !isBraced(elsePart);
                if (elseNeedsBraces) output.append("{");
                visitNode(elsePart);
                if (elseNeedsBraces) output.append("}");
            }
        }
    }

    private void visitForLoop(ForLoop forLoop) {
        output.append("for(");

        AstNode initializer = forLoop.getInitializer();
        if (initializer != null) {
            visitNode(initializer);
        }
        output.append(";");

        AstNode condition = forLoop.getCondition();
        if (condition != null) {
            visitNode(condition);
        }
        output.append(";");

        AstNode increment = forLoop.getIncrement();
        if (increment != null) {
            visitNode(increment);
        }
        output.append(")");

        AstNode body = forLoop.getBody();
        visitLoopBody(body);
    }

    private void visitForInLoop(ForInLoop forIn) {
        // "for each" is a JS 1.6 Mozilla extension and the keyword goes BEFORE
        // the parenthesis: "for each (var b in a)". Emitting it between the
        // iterator and "in" produced "for(var a each in b)", which this
        // compressor's own parser rejects ("missing ; after for-loop
        // initializer") - invalid output, emitted with exit 0. No browser
        // supports the form, but emitting something unparseable is worse than
        // emitting something obsolete.
        output.append(forIn.isForEach() ? "for each(" : "for(");
        visitNode(forIn.getIterator());
        if (forIn.isForOf()) {
            output.append(" of ");
        } else {
            output.append(" in ");
        }
        visitNode(forIn.getIteratedObject());
        output.append(")");

        AstNode body = forIn.getBody();
        visitLoopBody(body);
    }

    private void visitWhileLoop(WhileLoop whileLoop) {
        output.append("while(");
        visitNode(whileLoop.getCondition());
        output.append(")");

        AstNode body = whileLoop.getBody();
        visitLoopBody(body);
    }

    private void visitDoLoop(DoLoop doLoop) {
        output.append("do");

        AstNode body = doLoop.getBody();
        if (isBraced(body)) {
            visitNode(body);
        } else {
            output.append("{");
            visitNode(body);
            output.append("}");
        }

        output.append("while(");
        visitNode(doLoop.getCondition());
        output.append(")");
    }

    private void visitLoopBody(AstNode body) {
        if (isBraced(body)) {
            visitNode(body);
        } else if (body instanceof EmptyStatement) {
            output.append(";");
        } else {
            output.append("{");
            visitNode(body);
            output.append("}");
        }
    }

    /**
     * Whether visiting {@code body} will emit its own braces, so that a caller
     * about to wrap it must not add a second pair.
     *
     * <p>Not {@code instanceof Block}: Rhino wraps a loop, if- or do-body that
     * declares anything in a {@link Scope}, which does NOT extend
     * {@link Block}. The {@code instanceof} check therefore missed every such
     * body and emitted "for(...){{f();}}" - 1,100 redundant brace pairs and
     * 2,201 bytes on jQuery alone. Both classes report {@code Token.BLOCK},
     * and {@code visitNode} routes both to a visitor that writes its own
     * braces, so the node's type is the question that actually matters here.
     * {@code AstRoot} and {@code FunctionNode} are also {@code Scope}
     * subclasses but report SCRIPT/FUNCTION, so they are correctly excluded.
     */
    private static boolean isBraced(AstNode body) {
        return body != null && body.getType() == Token.BLOCK;
    }

    private void visitSwitchStatement(SwitchStatement switchStmt) {
        output.append("switch(");
        visitNode(switchStmt.getExpression());
        output.append("){");

        List<SwitchCase> cases = switchStmt.getCases();
        SwitchCase lastCase = (cases == null || cases.isEmpty()) ? null : cases.get(cases.size() - 1);
        if (cases != null) {
            for (SwitchCase caseNode : cases) {
                visitSwitchCase(caseNode, caseNode == lastCase);
            }
        }

        output.append("}");
        markSafeBreak();
    }

    private void visitSwitchCase(SwitchCase caseNode, boolean isLastCase) {
        AstNode expression = caseNode.getExpression();
        if (expression == null) {
            output.append("default:");
        } else {
            output.append("case");
            if (needsSpaceBeforeExpression(expression)) {
                output.append(" ");
            }
            visitNode(expression);
            output.append(":");
        }

        List<AstNode> statements = caseNode.getStatements();
        if (statements != null) {
            AstNode last = statements.isEmpty() ? null : statements.get(statements.size() - 1);
            for (AstNode stmt : statements) {
                visitNode(stmt);
                // Only the last case's last statement is followed by the switch's
                // "}"; anywhere else the ";" separates it from the next "case".
                if ((!isLastCase || stmt != last) && needsSemicolon(stmt)) {
                    output.append(";");
                }
                markSafeBreakIfAtSeparator();
            }
        }
    }

    private void visitBreakStatement(BreakStatement breakStmt) {
        output.append("break");
        Name label = breakStmt.getBreakLabel();
        if (label != null) {
            output.append(" ");
            output.append(label.getIdentifier());
        }
    }

    private void visitContinueStatement(ContinueStatement contStmt) {
        output.append("continue");
        Name label = contStmt.getLabel();
        if (label != null) {
            output.append(" ");
            output.append(label.getIdentifier());
        }
    }

    private void visitThrowStatement(ThrowStatement throwStmt) {
        output.append("throw");
        AstNode expression = throwStmt.getExpression();
        if (needsSpaceBeforeExpression(expression)) {
            output.append(" ");
        }
        visitNode(expression);
    }

    private void visitTryStatement(TryStatement tryStmt) {
        output.append("try");
        visitNode(tryStmt.getTryBlock());

        for (CatchClause clause : tryStmt.getCatchClauses()) {
            Name varName = clause.getVarName();
            if (varName != null) {
                output.append("catch(");
                output.append(getMungedName(varName.getIdentifier(), clause));
                AstNode guard = clause.getCatchCondition();
                if (guard != null) {
                    // "catch (e if e instanceof TypeError)" is a JS 1.7 Mozilla
                    // extension. Dropping the guard silently WIDENS the catch to
                    // every exception, which changes what the program does;
                    // Rhino exposes it, so emit it.
                    output.append(" if ");
                    visitNode(guard);
                }
                output.append(")");
            } else {
                // Optional catch binding: "catch" with no parentheses. "catch()"
                // is a syntax error.
                output.append("catch");
            }
            visitNode(clause.getBody());
        }

        AstNode finallyBlock = tryStmt.getFinallyBlock();
        if (finallyBlock != null) {
            output.append("finally");
            visitNode(finallyBlock);
        }
    }

    private void visitLabeledStatement(LabeledStatement labeled) {
        for (Label label : labeled.getLabels()) {
            output.append(label.getName());
            output.append(":");
        }
        visitNode(labeled.getStatement());
    }

    private void visitWithStatement(WithStatement withStmt) {
        output.append("with(");
        visitNode(withStmt.getExpression());
        output.append(")");
        visitNode(withStmt.getStatement());
    }

    private void visitBlock(Block block) {
        visitStatementList(block, block.getLastChild());
    }

    private void visitScope(Scope scope) {
        visitStatementList(scope, scope.getLastChild());
    }

    /**
     * Emits "{" statements "}" for a block-like node.
     *
     * <p>The last statement's ";" is left out: "}" ends the statement just as
     * well, and a reader that needs one gets it from automatic semicolon
     * insertion. Upstream YUI does the same ("Remove ';' when followed by a
     * '}'", CHANGELOG 1.1) and it is worth 0.66% of the output over a
     * real-world corpus.
     */
    private void visitStatementList(Iterable<Node> statements, Node last) {
        output.append("{");
        for (Node child : statements) {
            if (child instanceof AstNode) {
                visitNode((AstNode) child);
                if (child != last && needsSemicolon((AstNode) child)) {
                    output.append(";");
                }
                markSafeBreakIfAtSeparator();
            }
        }
        output.append("}");
        markSafeBreak();
    }

    private void visitStringLiteral(StringLiteral str) {
        String value = str.getValue();
        char quoteChar = str.getQuoteCharacter();

        if (quoteChar != '"' && quoteChar != '\'') {
            quoteChar = '"';
        }

        output.append(quoteChar);

        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\':
                        output.append("\\\\");
                        break;
                    case '\n':
                        output.append("\\n");
                        break;
                    case '\r':
                        output.append("\\r");
                        break;
                    case '\t':
                        output.append("\\t");
                        break;
                    case '\b':
                        output.append("\\b");
                        break;
                    case '\f':
                        output.append("\\f");
                        break;
                    case '"':
                        if (quoteChar == '"') {
                            output.append("\\\"");
                        } else {
                            output.append(c);
                        }
                        break;
                    case '\'':
                        if (quoteChar == '\'') {
                            output.append("\\'");
                        } else {
                            output.append(c);
                        }
                        break;
                    default:
                        output.append(c);
                        break;
                }
            }
        }

        output.append(quoteChar);
    }

    private void visitRegExpLiteral(RegExpLiteral regexp) {
        output.append("/");
        output.append(regexp.getValue());
        output.append("/");
        String flags = regexp.getFlags();
        if (flags != null) {
            output.append(flags);
        }
    }

    private void visitTemplateLiteral(TemplateLiteral template) {
        output.append('`');
        List<AstNode> elements = template.getElements();
        for (AstNode element : elements) {
            if (element instanceof TemplateCharacters) {
                // getValue() is the COOKED text - escapes already interpreted.
                // Putting that back between backticks interprets them a second
                // time: "\\d" becomes a literal "d" (breaking String.raw and every
                // tagged template), "\\${" turns into a live substitution, "\\\\"
                // swallows the next character and "\\`" ends the literal early.
                TemplateCharacters chars = (TemplateCharacters) element;
                String raw = chars.getRawValue();
                output.append(raw != null ? raw : chars.getValue());
            } else {
                output.append("${");
                visitNode(element);
                output.append("}");
            }
        }
        output.append('`');
    }

    private void visitTaggedTemplateLiteral(TaggedTemplateLiteral tagged) {
        visitNode(tagged.getTarget());
        visitNode(tagged.getTemplateLiteral());
    }

    private void visitInfixExpression(InfixExpression expr, String operator) {
        AstNode left = expr.getLeft();
        AstNode right = expr.getRight();

        // Add parentheses if needed for precedence
        boolean needsLeftParen = needsParentheses(left, expr, true);
        boolean needsRightParen = needsParentheses(right, expr, false);

        if (needsLeftParen) output.append("(");
        visitNode(left);
        if (needsLeftParen) output.append(")");

        output.append(operator);

        int mark = output.length();
        if (needsRightParen) output.append("(");
        visitNode(right);
        if (needsRightParen) output.append(")");
        insertSeparatorIfMerging(mark, operator);
    }

    private void visitUnaryExpression(UnaryExpression expr, String operator) {
        output.append(operator);
        int mark = output.length();
        AstNode operand = expr.getOperand();
        boolean needsParen = operand instanceof InfixExpression ||
                            operand instanceof ConditionalExpression;
        if (needsParen) output.append("(");
        visitNode(operand);
        if (needsParen) output.append(")");
        insertSeparatorIfMerging(mark, operator);
    }

    /**
     * Inserts a single space at {@code mark} if the character(s) the operand
     * placed there would combine with {@code operator} into a different
     * token than the two are meant to be, e.g.:
     * <ul>
     * <li>"+" immediately before a unary "+" or prefix "++" operand reads as
     * the increment operator: "a+" + "+b" -> "a++b" (a SyntaxError, not
     * "a + (+b)").
     * <li>"-" immediately before a unary "-" or prefix "--" operand reads as
     * the decrement operator the same way.
     * <li>"/" immediately before a "/" (a regex literal's opening delimiter)
     * opens a line comment that silently swallows the rest of the line.
     * <li>"/" immediately before a "*" would open a block comment.
     * <li>bare "&lt;" immediately before "!--" (e.g. "!" applied to a prefix
     * "--x") forms "&lt;!--", the Annex B SingleLineHTMLOpenComment. Since
     * minified output is a single line, that "comment" swallows the rest of
     * the entire FILE, not just the rest of the statement - worse than the
     * "/" case above, and it too leaves output that still parses. "&lt;="
     * and "&lt;&lt;" are unaffected: once either is consumed as its own
     * token, the next token scan starts past where "&lt;!--" could ever be
     * recognized, so this is scoped to the bare, one-character "&lt;"
     * operator specifically (checked via {@code operator}, not just its
     * last character, since a 1-character lookahead can't tell "&lt;" apart
     * from "&lt;=" / "&lt;&lt;").
     * </ul>
     * Checking the actual rendered character(s) (rather than the operand's
     * node type) covers unary "+"/"-", prefix "++"/"--", regex literals and
     * "!--" alike with the same technique, and never fires for a merely
     * adjacent-looking pair the lexer would not actually misread (e.g. "*"
     * immediately before "/", or a left operand ending in "/" or "+"/"-" -
     * see the task report for the merging pairs considered and why only
     * these five are hazards here).
     */
    private void insertSeparatorIfMerging(int mark, String operator) {
        if (output.length() <= mark || operator.isEmpty()) {
            return;
        }
        if (operator.equals("<") && startsWithAnnexBOpenComment(mark)) {
            insertSeparator(mark);
            return;
        }
        char operatorLastChar = operator.charAt(operator.length() - 1);
        char operandFirstChar = output.charAt(mark);
        boolean merges;
        switch (operatorLastChar) {
            case '+':
                merges = operandFirstChar == '+';
                break;
            case '-':
                merges = operandFirstChar == '-';
                break;
            case '/':
                merges = operandFirstChar == '/' || operandFirstChar == '*';
                break;
            default:
                merges = false;
                break;
        }
        if (merges) {
            insertSeparator(mark);
        }
    }

    /**
     * Inserts the separating space at {@code mark} and shifts every already
     * recorded safe-break offset that sits at or after it.
     *
     * <p>The operand was rendered before the separator is inserted, so any
     * statement boundary inside it (a nested function expression's body, for
     * instance) has already been recorded at its pre-insertion offset. Without
     * this shift every such offset lands one character early per separator
     * inserted before it, and {@code addLineBreaks} then cuts inside the
     * preceding token - splitting an identifier, or worse, a string literal,
     * whose closing quote ends up on the next line. Both were reachable at
     * {@code --line-break 20} with two nested insertions, and only the second
     * is a hard SyntaxError; the first still parses.
     */
    private void insertSeparator(int mark) {
        output.insert(mark, ' ');
        for (int i = safeBreakOffsets.size() - 1; i >= 0; i--) {
            int offset = safeBreakOffsets.get(i);
            if (offset < mark) {
                break; // the list is ascending, so nothing earlier can match
            }
            safeBreakOffsets.set(i, offset + 1);
        }
    }

    /**
     * Whether the operand rendered starting at {@code mark} begins with the
     * three characters "!--" - which, immediately after a bare "&lt;", form
     * "&lt;!--" (the Annex B SingleLineHTMLOpenComment).
     */
    private boolean startsWithAnnexBOpenComment(int mark) {
        return mark + 3 <= output.length()
                && output.charAt(mark) == '!'
                && output.charAt(mark + 1) == '-'
                && output.charAt(mark + 2) == '-';
    }

    private void visitKeywordUnary(UnaryExpression expr, String keyword) {
        output.append(keyword);
        AstNode operand = expr.getOperand();
        if (needsSpaceBeforeExpression(operand)) {
            output.append(" ");
        }
        visitNode(operand);
    }

    private void visitUpdateExpression(UpdateExpression expr) {
        String operator = expr.getType() == Token.INC ? "++" : "--";
        AstNode operand = expr.getOperand();

        if (expr.isPrefix()) {
            output.append(operator);
            visitNode(operand);
        } else {
            visitNode(operand);
            output.append(operator);
        }
    }

    private void visitConditionalExpression(ConditionalExpression expr) {
        visitNode(expr.getTestExpression());
        output.append("?");
        visitNode(expr.getTrueExpression());
        output.append(":");
        visitNode(expr.getFalseExpression());
    }

    private void visitFunctionCall(FunctionCall call) {
        AstNode target = call.getTarget();
        visitNode(target);
        // FunctionCall.isOptionalCall() over-reports the same way QUESTION_DOT
        // does: it is true for every call in an optional chain, not just the one
        // immediately after "?." (e.g. in "a?.b().c()" it is also true for
        // ".c()"). So it cannot be trusted on its own; go straight to the source
        // text, exactly like the QUESTION_DOT case above.
        if (isOptionalGap(target, call.getAbsolutePosition() + call.getLp())) {
            output.append("?.");
        }
        output.append("(");

        List<AstNode> args = call.getArguments();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) output.append(",");
            visitNode(args.get(i));
        }

        output.append(")");
    }

    private void visitNewExpression(NewExpression newExpr) {
        output.append("new");
        AstNode target = newExpr.getTarget();
        if (needsSpaceBeforeExpression(target)) {
            output.append(" ");
        }
        visitNode(target);

        List<AstNode> args = newExpr.getArguments();
        if (args != null && !args.isEmpty()) {
            output.append("(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) output.append(",");
                visitNode(args.get(i));
            }
            output.append(")");
        } else if (newExpr.getInitializer() == null) {
            // Include empty parentheses for clarity
            output.append("()");
        }

        AstNode initializer = newExpr.getInitializer();
        if (initializer != null) {
            visitNode(initializer);
        }
    }

    private void visitPropertyGet(PropertyGet pg) {
        visitNode(pg.getTarget());
        appendMemberDot(pg.getTarget());
        AstNode property = pg.getProperty();
        if (property instanceof Name) {
            output.append(((Name) property).getIdentifier());
        } else {
            output.append(property.toSource());
        }
    }

    /**
     * Appends the "." of a member access on {@code target}.
     *
     * <p>A bare decimal integer needs a second one: in "1 .toString()" the space
     * is what stops the "." being read as the start of a fractional part, and
     * dropping it leaves "1.toString()", a syntax error. "1..toString()" is the
     * same length and needs no whitespace.
     *
     * <p>The decision is made from the literal's own text rather than from the
     * characters already in the buffer. Reading the buffer looked equivalent and
     * was not: "8e-5 .toFixed(3)" ends in a digit whose preceding character is
     * "-", so a scan backwards called it a bare integer and produced "8e-5..",
     * which no engine accepts.
     */
    private void appendMemberDot(AstNode target) {
        if (target instanceof NumberLiteral && isBareDecimalInteger(((NumberLiteral) target).getValue())) {
            output.append('.');
        }
        output.append('.');
    }

    /**
     * Whether a numeric literal is written as decimal digits and nothing else -
     * no ".", no exponent, no radix prefix, no BigInt "n". That is the only
     * spelling a following "." is ambiguous after, because it is the only one the
     * lexer can carry on reading as a fractional part. Numeric separators count
     * as digits here: "1_000." is a literal too, so "1_000.toString()" is just as
     * broken as "1.toString()".
     */
    private static boolean isBareDecimalInteger(String literal) {
        if (literal == null || literal.isEmpty()) {
            return false;
        }
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if ((c < '0' || c > '9') && c != '_') {
                return false;
            }
        }
        return true;
    }

    private void visitElementGet(ElementGet eg) {
        visitNode(eg.getTarget());
        output.append("[");
        visitNode(eg.getElement());
        output.append("]");
    }

    private void visitObjectLiteral(ObjectLiteral obj) {
        output.append("{");
        List<ObjectProperty> props = obj.getElements();
        for (int i = 0; i < props.size(); i++) {
            if (i > 0) output.append(",");
            ObjectProperty prop = props.get(i);

            // Check for shorthand property, with or without a default
            Name shorthand = shorthandBinding(prop);
            if (shorthand != null) {
                // "{b}" is shorthand for "{b:b}" and "{b=1}" for "{b:b=1}" -
                // the identifier is BOTH the property key and the binding, so
                // munging it renames the key too. In an object literal that
                // silently renames the property; in a destructuring pattern
                // (including a destructured parameter) it reads a property that
                // does not exist, so "function f({b}){return b;}" called with
                // {b:7} returned undefined. Expanding to "b:a" keeps the key
                // and munges only the binding.
                String original = shorthand.getIdentifier();
                String munged = getMungedName(original, prop);
                output.append(original);
                if (!munged.equals(original)) {
                    output.append(":").append(munged);
                }
                if (!prop.isShorthand()) {
                    // The "{b=1}" form. shorthandBinding() accepts a
                    // non-shorthand property only when its right is the
                    // Assignment carrying this binding's default, so the cast
                    // is safe here and nowhere else.
                    output.append("=");
                    visitNode(((Assignment) prop.getRight()).getRight());
                }
            } else if (prop.isShorthand()) {
                // Shorthand whose left is not a Name. Not known to be
                // reachable, but emitting nothing would be silent truncation.
                visitNode(prop.getLeft());
            } else if (prop.isGetterMethod() || prop.isSetterMethod() || prop.isNormalMethod()) {
                visitObjectMethod(prop);
            } else {
                // Regular property
                AstNode key = prop.getLeft();
                if (key instanceof ComputedPropertyKey) {
                    output.append("[");
                    visitNode(((ComputedPropertyKey) key).getExpression());
                    output.append("]");
                } else {
                    visitNode(key);
                }
                output.append(":");
                visitNode(prop.getRight());
            }
        }
        output.append("}");
    }

    /**
     * The binding {@code Name} of a shorthand property, or null if {@code prop}
     * is not one.
     *
     * <p>There are two shorthand forms and Rhino describes them differently.
     * Plain <code>{b}</code> reports {@code isShorthand() == true}. Shorthand
     * carrying a default, <code>{b = 1}</code>, reports
     * {@code isShorthand() == false}, and is recognisable instead by its right
     * being an {@code Assignment} whose left is the <b>same Name object</b> as
     * {@code prop.getLeft()}.
     *
     * <p>That object identity is the whole discriminator, and it is exact:
     * <code>{k: b = 1}</code> has an identical node shape but two distinct Name
     * objects, because there the key and the binding really are different
     * identifiers and only the binding may be munged. Verified against Rhino
     * 1.8.0 rather than assumed.
     *
     * <p>Keying only on {@code isShorthand()} is what made the first version of
     * this fix stop one character short of <code>{b = 1}</code>. In that path
     * the shared Name object also reaches {@link #visitName}'s property-key
     * guard, which then suppressed munging on the BINDING while every reference
     * to it in the body was munged normally - a declaration and its uses under
     * different names. As an assignment pattern that is silent:
     * <code>({someKey = 5} = o)</code> simply produced the wrong answer.
     */
    private static Name shorthandBinding(ObjectProperty prop) {
        AstNode left = prop.getLeft();
        if (!(left instanceof Name)) {
            return null;
        }
        if (prop.isShorthand()) {
            return (Name) left;
        }
        AstNode right = prop.getRight();
        if (right instanceof Assignment && ((Assignment) right).getLeft() == left) {
            return (Name) left;
        }
        return null;
    }

    /**
     * Emits a getter, setter or ES6 shorthand method of an object literal.
     *
     * <p>Two failure directions are corrected here. The getter and setter
     * branches used to emit "get "/"set " and the key, then emit the parameter
     * list and body only {@code if (right instanceof FunctionNode)} - so a
     * right-hand side that was not a {@code FunctionNode} produced "{get x}",
     * which is not valid JavaScript. And the normal-method branch cast the
     * key blindly, so a generator method ({@code var o = { *gen(){...} };} -
     * which Rhino parses) died with a {@code ClassCastException} deep inside
     * the infix path, because Rhino wraps a generator method's key in a
     * {@code GeneratorMethodDefinition} whose type is {@code Token.MUL}.
     *
     * <p>Anything still not reconstructable goes to {@link #appendFallback},
     * which under strict mode throws naming the construct. A truncated
     * property or a {@code ClassCastException} is not an acceptable outcome
     * either way.
     */
    private void visitObjectMethod(ObjectProperty prop) {
        AstNode right = prop.getRight();
        if (!(right instanceof FunctionNode)) {
            appendFallback(prop);
            return;
        }
        AstNode key = prop.getLeft();
        boolean generator = key instanceof GeneratorMethodDefinition;
        if (generator) {
            key = ((GeneratorMethodDefinition) key).getMethodName();
            if (key == null) {
                appendFallback(prop);
                return;
            }
        }

        if (prop.isGetterMethod()) {
            output.append("get ");
        } else if (prop.isSetterMethod()) {
            output.append("set ");
        } else if (generator) {
            // ES6 shorthand generator method: "*gen(){...}".
            output.append("*");
        }

        if (key instanceof ComputedPropertyKey) {
            output.append("[");
            visitNode(((ComputedPropertyKey) key).getExpression());
            output.append("]");
        } else {
            visitNode(key);
        }

        FunctionNode fn = (FunctionNode) right;
        output.append("(");
        visitParameterList(fn.getParams(), fn);
        output.append(")");
        visitNode(fn.getBody());
    }

    /**
     * Emits an array literal, including its elisions.
     *
     * <p>A trailing elision needs an extra comma of its own. Commas here are
     * separators, and a trailing separator is not an element - "[a,b,]" and
     * "[a,b]" are both length 2 - so a final hole written with only the
     * separator loop vanishes:
     *
     * <pre>
     * [, , alpha, , ]   source: [null,null,7,null]  length 4
     *                   emitted as "[,,b,]"       -> length 3
     *                   must be    "[,,b,,]"      -> length 4
     * </pre>
     *
     * <p>Silent, parses, and changes both the contents and {@code .length} of
     * the array. Rhino's element list already models this correctly - it counts
     * a trailing hole and does not count a trailing separator, matching JS
     * {@code length} in every case checked - so the list size is the length to
     * reproduce.
     */
    private void visitArrayLiteral(ArrayLiteral arr) {
        output.append("[");
        List<AstNode> elements = arr.getElements();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) output.append(",");
            AstNode element = elements.get(i);
            if (element instanceof EmptyExpression) {
                // Elision - just leave empty
            } else {
                visitNode(element);
            }
        }
        if (!elements.isEmpty() && elements.get(elements.size() - 1) instanceof EmptyExpression) {
            output.append(",");
        }
        output.append("]");
    }

    private void visitParenthesizedExpression(ParenthesizedExpression paren) {
        output.append("(");
        visitNode(paren.getExpression());
        output.append(")");
    }

    private void visitYield(Yield yield) {
        output.append(yield.getType() == Token.YIELD_STAR ? "yield*" : "yield");
        AstNode value = yield.getValue();
        if (value != null) {
            if (needsSpaceBeforeExpression(value)) {
                output.append(" ");
            }
            visitNode(value);
        }
    }

    private boolean needsSemicolon(AstNode node) {
        // LabeledStatement.getType() is always Token.EXPR_VOID (see
        // visitNode()'s EXPR_RESULT/EXPR_VOID case), never Token.LABEL, so a
        // "type != Token.LABEL" check below can never exclude one - it would
        // return true unconditionally for every labeled statement, adding a
        // needless ";" after e.g. "outer:for(...){...}". Recurse into the
        // wrapped statement instead, so the decision follows what it
        // actually is: "outer: x();" still needs its ";" (its wrapped
        // statement is an ExpressionStatement), "outer:for(...){...}" does
        // not (its wrapped statement is a FOR loop).
        if (node instanceof LabeledStatement) {
            return needsSemicolon(((LabeledStatement) node).getStatement());
        }
        int type = node.getType();
        return type != Token.FUNCTION &&
               type != Token.BLOCK &&
               type != Token.IF &&
               type != Token.FOR &&
               type != Token.WHILE &&
               type != Token.DO &&
               type != Token.SWITCH &&
               type != Token.TRY &&
               type != Token.WITH;
    }

    /**
     * Whether a word keyword ("return", "throw", "new", "typeof", "void",
     * "delete", "yield", "case") needs a space before the expression that
     * follows it, to keep the two from merging into a single identifier
     * token. Only safe to omit when the expression's node type fixes its
     * first rendered character to something that is never part of an
     * identifier or number, regardless of what the expression contains.
     */
    private boolean needsSpaceBeforeExpression(AstNode expr) {
        if (expr == null) {
            return false;
        }
        switch (expr.getType()) {
            case Token.LP:                  // ParenthesizedExpression: "("
            case Token.OBJECTLIT:           // "{"
            case Token.ARRAYLIT:            // "["
            case Token.STRING:              // a quote character
            case Token.TEMPLATE_LITERAL:    // "`"
            case Token.REGEXP:              // "/"
                return false;
            default:
                return true;
        }
    }

    private boolean needsParentheses(AstNode child, InfixExpression parent, boolean isLeft) {
        // Simple heuristic: if child is also an infix expression with lower precedence
        if (child instanceof ConditionalExpression) {
            // An assignment's right-hand side is an AssignmentExpression, which a
            // conditional already is, so "a=b?c:d" needs no parentheses. Every
            // other operator binds tighter than "?:" and so does need them:
            // "a+(b?c:d)" would otherwise re-parse as "(a+b)?c:d".
            return isLeft || !(parent instanceof Assignment);
        }
        if (child instanceof InfixExpression) {
            int childType = child.getType();
            int parentType = parent.getType();
            // Comma operator always needs parens when nested
            if (childType == Token.COMMA && parentType != Token.COMMA) {
                return true;
            }
        }
        return false;
    }

    // Strips block and line comments so a comment's literal text (e.g.
    // "a /* ?. */ .b") can never be mistaken for an operator.
    private static final java.util.regex.Pattern BLOCK_COMMENT =
            java.util.regex.Pattern.compile("/\\*[\\s\\S]*?\\*/");
    private static final java.util.regex.Pattern LINE_COMMENT =
            java.util.regex.Pattern.compile("//[^\\n\\r]*");

    private String stripComments(String text) {
        // Line comments first. A "//" comment whose own text contains "/*"
        // would otherwise let the block-comment pattern start inside it and
        // run past the line's end, swallowing a genuine "?." that follows and
        // silently widening the optional chain.
        text = LINE_COMMENT.matcher(text).replaceAll("");
        text = BLOCK_COMMENT.matcher(text).replaceAll("");
        return text;
    }

    /**
     * Whether the gap between the end of {@code target} and the start of the next
     * significant token (a property name, an element expression, or an argument
     * list's opening paren, given by {@code tokenStart}) actually contains the
     * "?." operator in the original source.
     *
     * <p>QUESTION_DOT-typed nodes and FunctionCall.isOptionalCall() both mark
     * every link of an optional chain, not just the one link that is actually
     * optional (Rhino's own toSource() cannot tell them apart either, which is
     * why it drops "?." altogether). Reading the real source text between the
     * two positions is the only reliable way to tell whether this particular
     * link carries its own "?." or is a plain "." / "[" / "(" continuation of a
     * chain that started optional earlier.
     */
    private boolean isOptionalGap(AstNode target, int tokenStart) {
        if (source == null) {
            // No source text available (e.g. a legacy caller/test that built the
            // AST directly): fall back to the conservative, non-optional form.
            return false;
        }
        int targetEnd = target.getAbsolutePosition() + target.getLength();
        if (targetEnd < 0 || tokenStart < targetEnd || tokenStart > source.length()) {
            // Positions don't make sense - don't guess, assume plain.
            return false;
        }
        String gap = stripComments(source.substring(targetEnd, tokenStart));
        return gap.contains("?.");
    }

    /**
     * Get the munged name for a variable, or the original name if munging is disabled
     */
    private String getMungedName(String originalName, AstNode context) {
        if (!munge) {
            return originalName;
        }

        ScriptOrFnScope scope = findScopeForVariable(originalName, context);
        if (scope != null) {
            JavaScriptIdentifier id = scope.getIdentifier(originalName);
            if (id != null) {
                String mungedValue = id.getMungedValue();
                if (mungedValue != null) {
                    return mungedValue;
                }
            }
        }

        return originalName;
    }

    /**
     * Find the scope that declares a given variable
     */
    private ScriptOrFnScope findScopeForVariable(String name, AstNode context) {
        AstNode current = context;
        while (current != null) {
            if (current instanceof FunctionNode || current instanceof AstRoot) {
                ScriptOrFnScope scope = scopeBuilder.getScopeForNode(current);
                if (scope != null) {
                    ScriptOrFnScope searchScope = scope;
                    while (searchScope != null) {
                        if (searchScope.getIdentifier(name) != null) {
                            return searchScope;
                        }
                        searchScope = searchScope.getParentScope();
                    }
                }
            }
            current = current.getParent();
        }
        return scopeBuilder.getGlobalScope();
    }
}
