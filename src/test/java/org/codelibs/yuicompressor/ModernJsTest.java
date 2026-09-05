package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Parser;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/** Regression tests for modern JavaScript syntax. */
class ModernJsTest {

    static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    private String compress(String source) throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT)
                .compress(out, -1, true, false, false, false);
        return out.toString().trim();
    }

    private String compressNoMunge(String source) throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT)
                .compress(out, -1, false, false, false, false);
        return out.toString().trim();
    }

    @Test
    void optionalChainingOnPropertyIsPreserved() throws Exception {
        String result = compress("var v = a?.b?.c;");
        assertTrue(result.contains("?."),
                "dropping '?.' turns a safe undefined into a TypeError: " + result);
    }

    @Test
    void optionalChainingOnElementIsPreserved() throws Exception {
        String result = compress("var w = obj?.[key];");
        assertTrue(result.contains("?."), "optional element access must be kept: " + result);
    }

    @Test
    void optionalCallIsPreserved() throws Exception {
        String result = compress("foo?.(1);");
        assertTrue(result.contains("?."), "an optional call must be kept: " + result);
    }

    @Test
    void aLineCommentContainingBlockCommentTextDoesNotSwallowAGenuineOptionalChain() throws Exception {
        // isOptionalGap strips comments out of the source gap before looking
        // for "?.". Stripping BLOCK comments first let the "/*" inside this
        // line comment open a block comment that ran to the next "*/" - past
        // the end of the line - taking the real "?." with it and silently
        // widening the chain to a plain ".". Line comments must go first.
        String result = compressNoMunge("var v = a //x /*\n/*y*/?.b;");
        assertEquals("var v=a?.b;", result, result);
    }

    @Test
    void catchWithoutBindingStaysValid() throws Exception {
        String result = compress("try { f(); } catch { g(); }");
        assertFalse(result.contains("catch()"),
                "'catch()' is a syntax error; the binding is optional: " + result);
    }

    @Test
    void shorthandMethodStaysShorthand() throws Exception {
        String result = compress("var o = { m(){ return 1; } }; o.m();");
        assertFalse(result.contains("function"),
                "a shorthand method should not expand to a function expression: " + result);
    }

    // Rhino marks every link of an optional chain with the same QUESTION_DOT
    // type (and FunctionCall.isOptionalCall() over-reports the same way), so a
    // naive "type says QUESTION_DOT => emit '?.'" fix widens mixed chains: it
    // adds a "?." to links that were never optional in the source. That is its
    // own silent behaviour change - e.g. "a?.b.c" throws on a null "a" while
    // "a?.b?.c" quietly evaluates to undefined. These assert the exact output
    // so a chain is reproduced link-for-link, not merely "contains '?.'
    // somewhere".

    @Test
    void optionalChainDoesNotWidenToTrailingPlainProperty() throws Exception {
        String result = compressNoMunge("var v = a?.b.c;");
        assertEquals("var v=a?.b.c;", result,
                "only 'a?.b' is optional; 'a?.b?.c' would turn a TypeError into undefined: " + result);
    }

    @Test
    void optionalChainDoesNotWidenToLeadingOrTrailingPlainProperty() throws Exception {
        String result = compressNoMunge("var v = a.b?.c.d;");
        assertEquals("var v=a.b?.c.d;", result,
                "only 'c' is reached optionally; neither 'a.b' nor '.d' may gain '?.': " + result);
    }

    @Test
    void optionalElementChainDoesNotWidenToTrailingPlainProperty() throws Exception {
        String result = compressNoMunge("var v = a?.[0].c;");
        assertEquals("var v=a?.[0].c;", result,
                "the trailing '.c' is not optional and must not gain '?.': " + result);
    }

    @Test
    void optionalCallChainDoesNotWidenToTrailingPlainProperty() throws Exception {
        String result = compressNoMunge("var v = a?.b().c;");
        assertEquals("var v=a?.b().c;", result,
                "the call and the trailing '.c' are not themselves optional: " + result);
    }

    @Test
    void templateLiteralContentIsUntouched() throws Exception {
        String result = compress("var s = `a ${x} b`; f(s);");
        assertTrue(result.contains("${x} b"),
                "whitespace inside a template literal is string data: " + result);
    }

    @Test
    void templateLiteralRunsOfSpacesArePreserved() throws Exception {
        String result = compress("var t = `keep   spaces`; f(t);");
        assertTrue(result.contains("keep   spaces"),
                "runs of spaces inside a template literal must survive: " + result);
    }

    @Test
    void regexLiteralContentIsUntouched() throws Exception {
        String result = compress("var re = /^( a )+$/; f(re);");
        assertTrue(result.contains("( a )"),
                "removing the spaces changes what the regex matches: " + result);
    }

    // A bare contains("beta") does not discriminate here: the generator's
    // minimal (no-munge... well munged, but "beta" isn't renamed since it's
    // a top-level var) output for this input is
    // "var alpha=1;var beta=2;var gamma=alpha+beta;gamma++;", and the
    // project's old fixed-20-char slicer (reinstated: chop every 20 chars,
    // no regard for token boundaries) would cut it at columns 20 and 40:
    // "var alpha=1;var beta" / "=2;var gamma=alpha+b" / "eta;gamma++;". The
    // FIRST "beta" (the declaration) happens to end exactly at column 20,
    // so it survives whole and contains("beta") still finds it - even
    // though the SECOND "beta" (in "alpha+beta") gets split into "b" / "eta"
    // across the line break. Counting whole-token ("\bbeta\b") occurrences
    // catches that: the old slicer leaves only 1, not 2. Confirmed by
    // simulating the old slicer against today's actual generator output.
    @Test
    void lineBreakNeverSplitsAnIdentifier() throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(
                new StringReader("var alpha=1; var beta=2; var gamma=alpha+beta; gamma++;"), SILENT)
                .compress(out, 20, true, false, false, false);
        String result = out.toString();
        Matcher m = Pattern.compile("\\bbeta\\b").matcher(result);
        int count = 0;
        while (m.find()) {
            count++;
        }
        assertEquals(2, count, "an identifier was split across a line break: " + result);
        assertEveryLineBreakIsAtAStatementBoundary(result);
    }

    // The two cases above the fix: the generator records a safe-break offset
    // as soon as a statement inside the nested function body is complete, but
    // insertSeparatorIfMerging then inserted its separating space at an
    // EARLIER offset without adjusting the recorded ones. Every recorded break
    // therefore landed k characters early, k being the number of separators
    // inserted before it. "a + + +function(){...}" needs two of them, so the
    // break lands two characters inside the preceding token.
    //
    // Both are the same defect and only the second is loud: splitting an
    // identifier leaves output that still PARSES (as two statements naming two
    // different variables), while splitting a string literal puts its closing
    // quote on the next line and is a hard SyntaxError.

    @Test
    void lineBreakNeverSplitsAnIdentifierAfterNestedSeparatorInsertions() throws Exception {
        String result = compressAt(20, "var q = a + + +function(){ abcdefghijklmnop; }();");
        assertEquals("var q=a+ + +function(){abcdefghijklmnop}\n();", result, result);
        assertParses(result);
        assertEveryLineBreakIsAtAStatementBoundary(result);
    }

    /**
     * The fixture's "a" is a free global, so the inner "s" may not be munged onto
     * it - it used to be, which is the collision ScopeBuilder now reserves against.
     */
    @Test
    void lineBreakNeverSplitsAStringLiteralAfterNestedSeparatorInsertions() throws Exception {
        String result = compressAt(20, "var q = a + + +function(){ var s = \"hello\"; }();");
        assertEquals("var q=a+ + +function(){var b=\"hello\"}\n();", result, result);
        assertParses(result);
        assertEveryLineBreakIsAtAStatementBoundary(result);
    }

    @Test
    void lineBreakIsCorrectWithASingleSeparatorInsertion() throws Exception {
        // k=1 rather than k=2, so the break is one character early rather than
        // two - the same defect at a different magnitude, and the case that
        // shows the fix is a per-separator shift, not a fixed correction.
        String result = compressAt(20, "var q = a + +function(){ abcdefghijklmnop; }();");
        assertEquals("var q=a+ +function(){abcdefghijklmnop}\n();", result, result);
        assertParses(result);
        assertEveryLineBreakIsAtAStatementBoundary(result);
    }

    private String compressAt(int linebreakpos, String source) throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT)
                .compress(out, linebreakpos, true, false, false, false);
        return out.toString();
    }

    /**
     * Every offset the generator records is taken immediately after a
     * statement has finished - its trailing ";" already appended when one is
     * needed, or its closing "}" already written - so the character preceding
     * an inserted newline can only be ";" or "}". A break landing anywhere
     * else is by definition inside a token.
     */
    private void assertEveryLineBreakIsAtAStatementBoundary(String result) {
        // Assert a break actually happened first. Without this the loop below
        // is vacuous when addLineBreaks does nothing at all: making it a no-op
        // left this check, all three differential line-break cases and
        // CliOptionTest green, and only the exact-output tests caught it. A
        // "no line break landed badly" assertion that passes because there are
        // no line breaks is the shape this release keeps removing.
        assertTrue(result.indexOf('\n') >= 0,
                "no line break was inserted at all, so this assertion would prove nothing: " + result);
        for (int i = 0; i < result.length(); i++) {
            if (result.charAt(i) != '\n') {
                continue;
            }
            char before = i == 0 ? '\0' : result.charAt(i - 1);
            assertTrue(before == ';' || before == '}',
                    "line break at offset " + i + " follows '" + before
                            + "', so it does not fall on a statement boundary: " + result);
        }
    }

    /**
     * Re-parses with Rhino, which is always available here (unlike node).
     * Parsing alone is not sufficient for these cases - a split identifier
     * still parses, as two statements naming two different variables - which
     * is why {@link #assertEveryLineBreakIsAtAStatementBoundary} carries the
     * rest of the weight.
     */
    private void assertParses(String result) {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setRecordingComments(false);
        env.setLanguageVersion(Context.VERSION_ES6);
        env.setErrorReporter(SILENT);
        new Parser(env, SILENT).parse(result, "compressed", 1);
    }

    @Test
    void yieldStarDelegatesRatherThanYieldingTheGeneratorOnce() throws Exception {
        String result = compressNoMunge("function* g(){ yield* other(); }");
        assertEquals("function* g(){yield* other()}", result,
                "'yield* x()' delegates to another generator; dropping the '*' makes it "
                        + "'yield x()', which yields the generator object once instead: " + result);
    }

    @Test
    void plainYieldStillHasNoStar() throws Exception {
        String result = compressNoMunge("function* g(){ yield other(); }");
        assertEquals("function* g(){yield other()}", result,
                "a plain (non-delegating) yield must not gain a '*': " + result);
    }

    @Test
    void labeledStatementDoesNotCrashTheCompressor() throws Exception {
        String result = compressNoMunge("outer: for (var i=0;i<3;i++) { break outer; }");
        assertEquals("outer:for(var i=0;i<3;i++){break outer}", result,
                "the label and the 'break outer' inside it must survive, and a labeled "
                        + "for-loop must not gain a needless trailing ';' (a for-loop never "
                        + "needs one): " + result);
    }

    @Test
    void addOperatorDoesNotMergeWithUnaryPlusOperand() throws Exception {
        String result = compressNoMunge("var t1 = a + +b;");
        assertEquals("var t1=a+ +b;", result,
                "'a++b' is a SyntaxError - a separating space must keep the '+' operator "
                        + "apart from the unary '+' operand: " + result);
    }

    @Test
    void subOperatorDoesNotMergeWithUnaryMinusOperand() throws Exception {
        String result = compressNoMunge("var t2 = a - -b;");
        assertEquals("var t2=a- -b;", result,
                "'a--b' is a SyntaxError - a separating space must keep the '-' operator "
                        + "apart from the unary '-' operand: " + result);
    }

    @Test
    void addOperatorDoesNotMergeWithUnaryPlusBeforeNewExpression() throws Exception {
        String result = compressNoMunge("var t3 = start + +new Date();");
        assertEquals("var t3=start+ +new Date();", result,
                "'start++new Date()' is a SyntaxError - 'start + +new Date()' is a common "
                        + "real-world idiom that must round-trip: " + result);
    }

    @Test
    void divisionOperatorDoesNotMergeWithFollowingRegexIntoALineComment() throws Exception {
        String result = compressNoMunge("var y = x / /re/.test(\"re\") ? 1 : 2;");
        assertEquals("var y=x/ /re/.test(\"re\")?1:2;", result,
                "'x//re/...' turns the rest of the statement into a line comment - this "
                        + "passes 'node --check' (the output still parses) while silently "
                        + "discarding the ternary: " + result);
    }

    // A doubled unary minus/plus is a pure expression with no side effect.
    // Collapsing the space turns it into a pre-decrement/pre-increment,
    // which both changes the value AND writes to the operand - a silent
    // behaviour change worse than a SyntaxError, since nothing signals it.

    @Test
    void doubleUnaryMinusStaysAPureExpressionRatherThanAPreDecrement() throws Exception {
        String result = compressNoMunge("var a = 5; var r = - -a;");
        assertEquals("var a=5;var r=- -a;", result,
                "'--a' pre-decrements and mutates 'a'; '- -a' must not collapse into it: " + result);
    }

    @Test
    void doubleUnaryPlusStaysAPureExpressionRatherThanAPreIncrement() throws Exception {
        String result = compressNoMunge("var c = 5; var r = + +c;");
        assertEquals("var c=5;var r=+ +c;", result,
                "'++c' pre-increments and mutates 'c'; '+ +c' must not collapse into it: " + result);
    }

    @Test
    void addOperatorDoesNotMergeWithPrefixIncrementOperand() throws Exception {
        String result = compressNoMunge("var a = 1, b = 2; var r = a + ++b;");
        assertEquals("var a=1,b=2;var r=a+ ++b;", result,
                "'a+++b' reparses as '(a++) + b', incrementing the wrong variable "
                        + "('a' instead of 'b') and changing the result: " + result);
    }

    @Test
    void subOperatorDoesNotMergeWithPrefixDecrementOperand() throws Exception {
        String result = compressNoMunge("var a = 1, b = 2; var r = a - --b;");
        assertEquals("var a=1,b=2;var r=a- --b;", result,
                "'a---b' reparses as '(a--) - b', decrementing the wrong variable "
                        + "('a' instead of 'b') and changing the result: " + result);
    }

    @Test
    void addAfterPostfixIncrementAndAddBeforePrefixIncrementStayDistinct() throws Exception {
        // Both "a++ + b" and "a + ++b" naively render to the same raw text
        // "a+++b" if no separator is ever inserted - and "a+++b" itself
        // always reparses as "(a++) + b", so an unconditional collapse
        // would silently make one of the two inputs wrong. After the fix
        // they must render differently: the left-boundary case needs no
        // separator (maximal-munch already resolves "a+++b" as intended for
        // it), the right-boundary case does.
        String postfixThenAdd = compressNoMunge("var a = 1, b = 2; var r = a++ + b;");
        String addThenPrefix = compressNoMunge("var a = 1, b = 2; var r = a + ++b;");
        assertEquals("var a=1,b=2;var r=a+++b;", postfixThenAdd, postfixThenAdd);
        assertEquals("var a=1,b=2;var r=a+ ++b;", addThenPrefix, addThenPrefix);
        assertFalse(postfixThenAdd.equals(addThenPrefix),
                "'a++ + b' and 'a + ++b' have different meanings and must not collapse "
                        + "to the same output: " + postfixThenAdd);
    }

    // A bare "<" immediately before "!--" (e.g. "!" applied to a prefix
    // "--x") forms "<!--", the Annex B SingleLineHTMLOpenComment. Minified
    // output is a single line, so that "comment" swallows the rest of the
    // ENTIRE FILE, not just the rest of the statement - worse than defect
    // (D), and like it, 'node --check' reports the corrupted output as
    // valid. "<=" and "<<" are unaffected: once either is consumed as its
    // own token, the next token scan starts past where "<!--" could ever
    // be recognized.

    @Test
    void ltOperatorDoesNotMergeWithFollowingAnnexBOpenComment() throws Exception {
        String result = compressNoMunge("var a = 1, b = 5; var r = a < !--b; console.log(r, b);");
        assertFalse(result.contains("<!--"),
                "'<!--' opens a comment that swallows the rest of the FILE (minified "
                        + "output is one line), not just the rest of the statement: " + result);
        assertEquals("var a=1,b=5;var r=a< !--b;console.log(r,b);", result, result);
    }

    @Test
    void lshOperatorIsNotAffectedByTheAnnexBCheck() throws Exception {
        // "<<" is consumed as its own token before the scan could ever see
        // "<!--" starting - a separator here would only cost bytes.
        String result = compressNoMunge("var a = 1, b = 5; var r = a << !--b;");
        assertEquals("var a=1,b=5;var r=a<<!--b;", result, result);
    }

    @Test
    void leOperatorIsUnaffectedByTheAnnexBCheck() throws Exception {
        String result = compressNoMunge("var r = a <= b;");
        assertEquals("var r=a<=b;", result, result);
    }

    @Test
    void ltOperatorWithAnOrdinaryOperandIsUnaffected() throws Exception {
        String result = compressNoMunge("var r = a < b;");
        assertEquals("var r=a<b;", result, result);
    }

    @Test
    void postfixDecrementThenGtDoesNotGainANeedlessSeparator() throws Exception {
        // "-->" is only the Annex B SingleLineHTMLCloseComment when it
        // begins a line with nothing but whitespace before it. The
        // generator never emits it in that position (line breaks only
        // occur after ';' or '}'), so this must stay unseparated.
        String result = compressNoMunge("var a = 1, b = 5; var r = a-- > b;");
        assertEquals("var a=1,b=5;var r=a-->b;", result, result);
    }

    // needsSemicolon(MungedCodeGenerator.java) excluded Token.LABEL the same
    // way visitNode's switch once had a dead "case Token.LABEL:" - but
    // LabeledStatement.getType() is always Token.EXPR_VOID, so that
    // exclusion never fired and every labeled statement got an unconditional
    // (and sometimes needless) trailing ';'.

    @Test
    void labeledForLoopDoesNotGainANeedlessSemicolon() throws Exception {
        String result = compressNoMunge("outer: for (var i=0;i<3;i++) { f(); }");
        assertEquals("outer:for(var i=0;i<3;i++){f()}", result,
                "a for-loop never needs a trailing ';', with or without a label: " + result);
    }

    @Test
    void labeledBlockDoesNotGainANeedlessSemicolon() throws Exception {
        String result = compressNoMunge("outer: { g(); }");
        assertEquals("outer:{g()}", result,
                "a block never needs a trailing ';', with or without a label: " + result);
    }

    @Test
    void labeledExpressionStatementKeepsItsSemicolon() throws Exception {
        // Not a blanket exclusion: a labeled expression statement genuinely
        // needs its ';', and must keep it.
        String result = compressNoMunge("outer: x();");
        assertEquals("outer:x();", result, result);
    }

    // The README promises munging stays safe "even when using constructs
    // such as 'eval' or 'with'". Direct eval can read any local visible in
    // its enclosing scope chain by name; a with statement can dynamically
    // shadow any of those same locals with a property of its object. Either
    // way, renaming the local would silently change what the program does,
    // so the scopes eval/with can see must be left unmunged.

    @Test
    void evalReadingALocalByNamePreventsThatLocalFromBeingMunged() throws Exception {
        String result = compress("function f(){ var secretName = 42; return eval(\"secretName\"); }");
        assertEquals("function f(){var secretName=42;return eval(\"secretName\")}", result,
                "eval(\"secretName\") only works if 'secretName' keeps its name: " + result);
    }

    @Test
    void evalInsideAFunctionExpressionArgumentPreventsMunging() throws Exception {
        // A regression check: function expressions passed as call arguments
        // only started getting scopes (and munged) once ScopeBuilder learned
        // to reach them; before that this case was accidentally safe.
        String result = compress("run(function(secretName){ return eval(\"secretName\"); });");
        assertEquals("run(function(secretName){return eval(\"secretName\")});", result,
                "the parameter read by eval() must not be renamed: " + result);
    }

    @Test
    void evalInANestedFunctionProtectsEveryEnclosingScope() throws Exception {
        // Direct eval sees the locals of every enclosing scope, not just the
        // one it's written in - so a local two scopes up from the eval call
        // must stay unmunged too.
        String result = compress(
                "function outer(){ var outerLocal = 7; function inner(){ return eval(\"outerLocal\"); } return inner(); }");
        assertEquals(
                "function outer(){var outerLocal=7;function inner(){return eval(\"outerLocal\")}return inner()}",
                result, "eval() in inner() can still read outerLocal by name: " + result);
    }

    @Test
    void aSiblingFunctionWithNoEvalStillMungesItsOwnLocals() throws Exception {
        // Protecting the scopes eval/with can see must not cascade into
        // unrelated functions declared alongside them: g() never calls
        // eval and doesn't need protecting, only f() does.
        String result = compress(
                "function f(){ var a = 1; eval(\"a\"); function g(){ var longLocalName = 2; return longLocalName; } return g(); }");
        assertEquals(
                "function f(){var a=1;eval(\"a\");function g(){var b=2;return b}return g()}",
                result, "g() has no eval of its own, so its local must still be munged: " + result);
    }

    @Test
    void evalAssignedToAVariableWithoutBeingCalledStillPreventsMunging() throws Exception {
        // "var e = eval" is still a bare reference to the identifier eval,
        // even though it's not invoked at this call site.
        String result = compress("function f(){ var secretName = 9; var e = eval; return e(\"secretName\"); }");
        assertEquals("function f(){var secretName=9;var e=eval;return e(\"secretName\")}", result,
                "aliasing eval must still protect the local it might read: " + result);
    }

    @Test
    void aPropertyAccessNamedEvalDoesNotPreventMunging() throws Exception {
        // "window.eval(...)" is an indirect eval: it always runs in global
        // scope and can never see this function's locals, so it is not the
        // hazard a bare "eval" reference is and must not disable munging.
        String result = compress("function f(){ var localOnly = 1; return window.eval(\"1+1\"); }");
        assertEquals("function f(){var a=1;return window.eval(\"1+1\")}", result,
                "window.eval is an indirect eval and cannot see localOnly: " + result);
    }

    @Test
    void withStatementPreventsMungingOfLocalsItCanShadow() throws Exception {
        String result = compress("function f(obj){ var x = 5; with (obj) { return x; } }");
        assertEquals("function f(obj){var x=5;with(obj){return x}}", result,
                "renaming x would change which binding 'with' resolves to: " + result);
    }

    @Test
    void ordinaryCodeWithNoEvalOrWithStillMungesNormally() throws Exception {
        // Guards against the fix becoming a blanket munging disable.
        String result = compress("function f(){ var longVariableName = 1; return longVariableName + 1; }");
        assertEquals("function f(){var a=1;return a+1}", result,
                "code with no eval/with must still munge exactly as before: " + result);
    }

    // "??", "??=", "||=", "&&=" and "**=" used to have no case in
    // MungedCodeGenerator's switch, so they fell through to
    // "default: output.append(node.toSource())". toSource() re-prints the
    // ORIGINAL source of the whole subtree, so both operands kept their
    // pre-munge spelling while their declarations were munged - the
    // parameters became "b"/"a" and the body still read "alpha"/"beta",
    // which are now globals. The output parses cleanly, so nothing in the
    // suite caught it. Each test below asserts the munged names actually
    // reach the operands.

    @Test
    void nullishCoalescingMungesItsOperands() throws Exception {
        String result = compress("function f(alpha, beta) { var gamma = alpha ?? beta; return gamma; }");
        assertEquals("function f(c,b){var a=c??b;return a}", result,
                "toSource() would re-emit 'alpha ?? beta', turning both locals into globals: " + result);
    }

    @Test
    void logicalOrAssignmentMungesItsOperands() throws Exception {
        String result = compress("function f(alpha, beta) { alpha ||= beta; return alpha; }");
        assertEquals("function f(b,a){b||=a;return b}", result, result);
    }

    @Test
    void logicalAndAssignmentMungesItsOperands() throws Exception {
        String result = compress("function f(alpha, beta) { alpha &&= beta; return alpha; }");
        assertEquals("function f(b,a){b&&=a;return b}", result, result);
    }

    @Test
    void nullishAssignmentMungesItsOperands() throws Exception {
        String result = compress("function f(alpha, beta) { alpha ??= beta; return alpha; }");
        assertEquals("function f(b,a){b??=a;return b}", result, result);
    }

    @Test
    void exponentAssignmentMungesItsOperands() throws Exception {
        // Not in the original triage of this defect, but the same shape:
        // Token.ASSIGN_EXP had no case either, so "**=" leaked too.
        String result = compress("function f(alpha, beta) { alpha **= beta; return alpha; }");
        assertEquals("function f(b,a){b**=a;return b}", result, result);
    }

    @Test
    void nullishCoalescingKeepsAnOptionalChainInItsOperand() throws Exception {
        // The end-to-end corruption: source evaluates to undefined, the old
        // output threw a TypeError because toSource() dropped the "?.".
        String result = compress("function f(config) { return config.timeout ?? config.server?.timeout; }");
        assertEquals("function f(a){return a.timeout??a.server?.timeout}", result,
                "dropping '?.' turns a safe undefined into a TypeError: " + result);
    }

    @Test
    void nullishCoalescingKeepsParenthesesAgainstLogicalOperators() throws Exception {
        // "a ?? b || c" is a SyntaxError - mixing "??" with "||"/"&&"
        // requires parentheses, so they must survive in both directions.
        assertEquals("var v=(a??b)||c;", compressNoMunge("var v = (a ?? b) || c;"));
        assertEquals("var v=a??(b||c);", compressNoMunge("var v = a ?? (b || c);"));
    }

    // Rhino wraps a loop, if- or do-body that declares anything in a Scope,
    // which does NOT extend Block, so the "body instanceof Block" check missed
    // it and wrapped an already-braced block in a second brace pair:
    // "for(...){f();}" came out as "for(...){{f();}}". 1,100 of them on jQuery,
    // 2,200 bytes. Checking the node's TYPE catches Block and Scope alike.

    @Test
    void aBracedLoopBodyDoesNotGainASecondBracePair() throws Exception {
        assertEquals("for(var i=0;i<3;i++){f()}", compressNoMunge("for (var i=0;i<3;i++) { f(); }"));
    }

    @Test
    void aBracedWhileBodyDoesNotGainASecondBracePair() throws Exception {
        assertEquals("while(x){f()}", compressNoMunge("while (x) { f(); }"));
    }

    @Test
    void aBracedDoBodyDoesNotGainASecondBracePair() throws Exception {
        assertEquals("do{f()}while(x)", compressNoMunge("do { f(); } while (x);"));
    }

    @Test
    void aBracedIfAndElseBodyDoNotGainASecondBracePair() throws Exception {
        assertEquals("if(x){f()}else{g()}", compressNoMunge("if (x) { f(); } else { g(); }"));
    }

    @Test
    void anUnbracedBodyStillGetsItsBraces() throws Exception {
        // The other direction: braces are what make the emitted body a single
        // statement, so a single-statement body must still get them.
        assertEquals("for(var i=0;i<3;i++){f()}", compressNoMunge("for (var i=0;i<3;i++) f();"));
        assertEquals("while(x){f()}", compressNoMunge("while (x) f();"));
        assertEquals("if(x){f()}else{g()}", compressNoMunge("if (x) f(); else g();"));
    }

    @Test
    void danglingElseBindingIsUnchanged() throws Exception {
        // The inner "if" has no else of its own, so the braces around it are
        // load-bearing: without them the "else" would bind to the inner "if".
        assertEquals("if(a){if(b){f()}}else{g()}", compressNoMunge("if (a) { if (b) f(); } else g();"));
    }

    @Test
    void anEmptyLoopBodyStillCompressesToASemicolon() throws Exception {
        assertEquals("for(var i=0;i<3;i++);", compressNoMunge("for (var i=0;i<3;i++) ;"));
    }

    // A shorthand property is one identifier serving as BOTH the property key
    // and the binding, so munging it renames the key with it. Found by
    // DifferentialExecutionTest running the code rather than checking that it
    // parses - the broken output parses perfectly and simply returns undefined.

    @Test
    void aShorthandObjectLiteralPropertyKeepsItsKeyWhenTheBindingIsMunged() throws Exception {
        String result = compress("function f(){ var longLocalName = 7; return { longLocalName }; }");
        assertEquals("function f(){var a=7;return{longLocalName:a}}", result,
                "munging the shorthand would rename the property itself: " + result);
    }

    @Test
    void aShorthandDestructuringPatternKeepsItsKeyWhenTheBindingIsMunged() throws Exception {
        String result = compress("function f(){ var o = { b: 7 }; var { b } = o; return b; }");
        assertEquals("function f(){var c={b:7};var {b:a}=c;return a}", result,
                "munging the shorthand would read a property that does not exist: " + result);
    }

    // Shorthand WITH a default is the same shape one character further on, and
    // Rhino describes it differently: isShorthand() is false, and the form is
    // instead identified by the right being an Assignment whose left is the
    // SAME Name object as prop.getLeft(). Keying only on isShorthand() left all
    // three positions below broken.

    @Test
    void aShorthandDestructuredParameterWithADefaultKeepsItsKey() throws Exception {
        String result = compress("function f({ someKey = 5 }) { return someKey; }");
        assertEquals("function f({someKey:a=5}){return a}", result,
                "the binding must be munged and the key must not: " + result);
    }

    @Test
    void aShorthandVarDestructuringWithADefaultKeepsItsKey() throws Exception {
        String result = compress("function f(o) { var { someKey = 5 } = o; return someKey; }");
        assertEquals("function f(b){var {someKey:a=5}=b;return a}", result, result);
    }

    @Test
    void aShorthandAssignmentDestructuringWithADefaultKeepsItsKey() throws Exception {
        // The silent one: before this fix it emitted "{someKey:someKey=5}",
        // which parses and returns undefined instead of the default.
        String result = compress("function f(o) { var someKey; ({ someKey = 5 } = o); return someKey; }");
        assertEquals("function f(b){var a;({someKey:a=5}=b);return a}", result, result);
    }

    @Test
    void aNonShorthandPropertyWithADefaultIsUnaffected() throws Exception {
        // "{k: b = 1}" has the same node shape but two DISTINCT Name objects,
        // so the key is not the binding and only the binding is munged. This is
        // the case the object-identity discriminator must keep telling apart.
        assertEquals("function f({k:a=5}){return a}", compress("function f({ k: someKey = 5 }) { return someKey; }"));
    }

    @Test
    void anArrayDestructuringDefaultIsUnaffected() throws Exception {
        assertEquals("function f([a=5]){return a}", compress("function f([ someKey = 5 ]) { return someKey; }"));
    }

    @Test
    void aShorthandWithADefaultThatIsNotMungedStaysShorthand() throws Exception {
        // Nothing to rename, so the original form is kept rather than expanded.
        assertEquals("({g=1}=o);", compressNoMunge("({ g = 1 } = o);"));
    }

    @Test
    void aShorthandPropertyThatIsNotMungedStaysShorthand() throws Exception {
        // The other direction: an unmunged binding must not be expanded to
        // "g:g", which would cost bytes for nothing.
        assertEquals("var g=1;var o={g};", compress("var g = 1; var o = { g };"));
    }

    @Test
    void aGeneratorObjectMethodCompressesRatherThanCrashing() throws Exception {
        // Rhino wraps a generator method's key in a GeneratorMethodDefinition
        // whose type is Token.MUL, so the object-literal path cast it to
        // InfixExpression and died with a ClassCastException.
        assertEquals("var o={*gen(){yield 1}};", compressNoMunge("var o = { *gen(){ yield 1; } };"));
    }

    @Test
    void aComputedGeneratorObjectMethodCompresses() throws Exception {
        assertEquals("var o={*[1+1](){yield 1}};", compressNoMunge("var o = { *[1+1](){ yield 1; } };"));
    }

    // Commas in an array literal are separators, so a trailing one is not an
    // element - "[a,b,]" and "[a,b]" are both length 2. A trailing elision
    // therefore needs an extra comma of its own, which the separator-only loop
    // never emitted: "[,,b,]" is length 3 where the source was length 4.

    @Test
    void aTrailingElisionKeepsItsSlot() throws Exception {
        assertEquals("var a=[,,b,,];", compressNoMunge("var a = [, , b, , ];"));
    }

    @Test
    void aSingleElisionKeepsItsSlot() throws Exception {
        assertEquals("var a=[,];", compressNoMunge("var a = [, ];"));
        assertEquals("var a=[,,];", compressNoMunge("var a = [, , ];"));
    }

    @Test
    void aTrailingCommaAfterARealElementIsStillDropped() throws Exception {
        // The other direction: this comma is a separator, not a slot, so
        // dropping it is correct and must keep happening.
        assertEquals("var a=[1,2];", compressNoMunge("var a = [1, 2, ];"));
        assertEquals("var a=[];", compressNoMunge("var a = [];"));
    }

    @Test
    void anInteriorElisionIsUnchanged() throws Exception {
        assertEquals("var a=[1,,2];", compressNoMunge("var a = [1, , 2];"));
    }

    @Test
    void bigIntLiteralsKeepTheirForm() throws Exception {
        assertEquals("var a=10n+0xffn;", compressNoMunge("var a = 10n + 0xffn;"));
    }

    // Two Mozilla-only legacy forms that were emitted wrongly rather than
    // falling to the fallback, so the strict tripwire could not see them.

    @Test
    void forEachPutsItsKeywordBeforeTheParenthesis() throws Exception {
        // "for(var a each in b)" is not valid syntax anywhere - this
        // compressor's own parser rejects it - so it was invalid output
        // emitted with exit 0.
        String result = compressNoMunge("for each (var b in a) { f(b); }");
        assertEquals("for each(var b in a){f(b)}", result, result);
    }

    @Test
    void aCatchGuardIsNotDropped() throws Exception {
        // Dropping the guard silently widens the catch to every exception.
        String result = compressNoMunge("try { g(); } catch (e if e instanceof TypeError) { h(e); }");
        assertEquals("try{g()}catch(e if e instanceof TypeError){h(e)}", result, result);
    }

    @Test
    void debuggerStatementStaysOnOneLine() throws Exception {
        // Token.DEBUGGER used to reach the toSource() fallback, which emits
        // "debugger;\n" - an embedded newline in output the line-break and
        // comment-injection machinery both assume is a single line - and then
        // needsSemicolon() added a second ';'.
        String result = compressNoMunge("debugger; f();");
        assertEquals("debugger;f();", result, result);
    }
}
