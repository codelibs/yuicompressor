package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

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
    }

    @Test
    void yieldStarDelegatesRatherThanYieldingTheGeneratorOnce() throws Exception {
        String result = compressNoMunge("function* g(){ yield* other(); }");
        assertEquals("function* g(){yield* other();}", result,
                "'yield* x()' delegates to another generator; dropping the '*' makes it "
                        + "'yield x()', which yields the generator object once instead: " + result);
    }

    @Test
    void plainYieldStillHasNoStar() throws Exception {
        String result = compressNoMunge("function* g(){ yield other(); }");
        assertEquals("function* g(){yield other();}", result,
                "a plain (non-delegating) yield must not gain a '*': " + result);
    }

    @Test
    void labeledStatementDoesNotCrashTheCompressor() throws Exception {
        String result = compressNoMunge("outer: for (var i=0;i<3;i++) { break outer; }");
        assertEquals("outer:for(var i=0;i<3;i++){{break outer;}}", result,
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
        assertEquals("outer:for(var i=0;i<3;i++){{f();}}", result,
                "a for-loop never needs a trailing ';', with or without a label: " + result);
    }

    @Test
    void labeledBlockDoesNotGainANeedlessSemicolon() throws Exception {
        String result = compressNoMunge("outer: { g(); }");
        assertEquals("outer:{g();}", result,
                "a block never needs a trailing ';', with or without a label: " + result);
    }

    @Test
    void labeledExpressionStatementKeepsItsSemicolon() throws Exception {
        // Not a blanket exclusion: a labeled expression statement genuinely
        // needs its ';', and must keep it.
        String result = compressNoMunge("outer: x();");
        assertEquals("outer:x();", result, result);
    }
}
