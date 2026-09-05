package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Compressions that used to change what the program does.
 *
 * <p>Every case here parsed cleanly before the fix and then behaved differently,
 * which is the failure mode a minifier must not have. Where node is available the
 * cases are also executed, so the test proves the behaviour rather than the
 * spelling; the string assertions stand on their own when it is not.
 */
class JsSemanticsTest {

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {
        }

        public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
        }

        public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource,
                int lineOffset) {
            return new EvaluatorException(message);
        }
    };

    private String compress(String source) throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT).compress(out, -1, true, false, false, false);
        return out.toString();
    }

    /** Runs both forms and asserts they print the same thing. Skipped without node. */
    private void assertSameBehaviour(String source) throws Exception {
        if (!NodeRuntime.isAvailable()) {
            return;
        }
        String minified = compress(source);
        assertEquals(NodeRuntime.run(source), NodeRuntime.run(minified),
                "compression changed what the program does: " + minified);
    }

    // ------------------------------------------------------------------
    // Template literals must be re-emitted from their raw text.
    // ------------------------------------------------------------------

    @Test
    void backslashEscapesSurviveInsideATemplateLiteral() throws Exception {
        assertEquals("var a=`\\d+\\.\\d+`;", compress("var a = `\\d+\\.\\d+`;"));
    }

    @Test
    void anEscapedDollarBraceDoesNotBecomeASubstitution() throws Exception {
        assertEquals("var a=`price: \\${sum}`;", compress("var a = `price: \\${sum}`;"));
    }

    @Test
    void anEscapedBacktickDoesNotEndTheLiteral() throws Exception {
        assertEquals("var a=`x\\`y`;", compress("var a = `x\\`y`;"));
    }

    @Test
    void anEscapedBackslashKeepsBothCharacters() throws Exception {
        assertEquals("var a=`a\\\\b`;", compress("var a = `a\\\\b`;"));
    }

    @Test
    void substitutionsStillWork() throws Exception {
        assertEquals("var n=1;var s=`v=${n+1} x`;", compress("var n = 1; var s = `v=${n+1} x`;"));
    }

    @Test
    void stringRawKeepsItsPatternAtRuntime() throws Exception {
        assertSameBehaviour("var re=new RegExp(String.raw`\\d+\\.\\d+`);console.log(re.source,re.test(\"3.14\"));");
    }

    // ------------------------------------------------------------------
    // A free reference is emitted verbatim, so no local may be munged onto it.
    // ------------------------------------------------------------------

    @Test
    void aLocalIsNotMungedOntoAFreeGlobal() throws Exception {
        String result = compress("function f(){var container=\"BODY\";return a.init(container);}");
        assertTrue(result.contains("a.init("), "the free global was renamed: " + result);
        assertTrue(!result.contains("var a="), "a local was munged onto the free global: " + result);
    }

    @Test
    void aLocalMungedOntoAFreeGlobalChangesBehaviour() throws Exception {
        assertSameBehaviour("function f(){var container=\"BODY\";return a.init(container);}"
                + "a={init:function(x){return \"init:\"+x;}};console.log(f());");
    }

    /**
     * A "var" hoists over its own uses, so a reference above the declaration is
     * not free and must keep being munged - deciding at visit time would give up
     * every such name.
     */
    @Test
    void aHoistedVarIsStillMunged() throws Exception {
        assertEquals("function f(){use(a);var a=1;return a}",
                compress("function f(){use(longName);var longName=1;return longName;}"));
    }

    @Test
    void aHoistedFunctionDeclarationIsStillMunged() throws Exception {
        assertEquals("function f(){return helper();function helper(){var a=1;return a}}",
                compress("function f(){return helper();function helper(){var longName=1;return longName;}}"));
    }

    // ------------------------------------------------------------------
    // A function expression in a destructuring default gets its own scope.
    // ------------------------------------------------------------------

    @Test
    void aFunctionInAnObjectPatternDefaultGetsItsOwnScope() throws Exception {
        assertSameBehaviour("function f(){var opts={};"
                + "var {handler=function(){var a=100;return a+longName;}}=opts;"
                + "var longName=5;return handler();}console.log(f());");
    }

    @Test
    void aFunctionInAnArrayPatternDefaultGetsItsOwnScope() throws Exception {
        assertSameBehaviour("var arr=[undefined];function g(){"
                + "var [item=function(){var a=7;return a+outerName;}]=arr;"
                + "var outerName=3;return item();}console.log(g());");
    }

    // ------------------------------------------------------------------
    // A bare integer needs a second "." before a member access.
    // ------------------------------------------------------------------

    @Test
    void aBareIntegerKeepsAValidMemberAccess() throws Exception {
        assertEquals("console.log(1..toString());", compress("console.log(1 .toString());"));
    }

    @Test
    void aSeparatedIntegerAlsoNeedsTheSecondDot() throws Exception {
        // "1_000." is a literal too, so "1_000.toString()" is just as broken.
        assertEquals("console.log(1_000..toString());", compress("console.log(1_000 .toString());"));
    }

    @Test
    void anUnambiguousNumericLiteralIsLeftAlone() throws Exception {
        assertEquals("console.log(0.5.toFixed(1));", compress("console.log(0.5.toFixed(1));"));
        assertEquals("console.log(0x10.toString(2));", compress("console.log(0x10 .toString(2));"));
        assertEquals("console.log(1e3.toFixed(0));", compress("console.log(1e3 .toFixed(0));"));
        assertEquals("console.log(1n.toString());", compress("console.log(1n .toString());"));
        assertEquals("console.log(1..toString());", compress("console.log(1..toString());"));
    }

    /**
     * A negative exponent ends in a digit whose preceding character is "-", which
     * a backwards scan over the output buffer read as a bare integer and turned
     * into "8e-5..toFixed(3)" - not valid anywhere. Found in a real bundle.
     */
    @Test
    void aNegativeExponentIsNotMistakenForABareInteger() throws Exception {
        assertEquals("console.log(8e-5.toFixed(3));", compress("console.log(8e-5 .toFixed(3));"));
        assertEquals("console.log(2e5.toFixed(1));", compress("console.log(2e5 .toFixed(1));"));
    }

    @Test
    void anIdentifierEndingInADigitIsNotTreatedAsANumber() throws Exception {
        assertEquals("console.log(a1.b);", compress("console.log(a1.b);"));
        assertEquals("console.log(md5.digest());", compress("console.log(md5.digest());"));
    }
}
