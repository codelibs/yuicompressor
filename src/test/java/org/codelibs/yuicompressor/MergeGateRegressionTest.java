package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

/**
 * Regressions this release introduces against {@code main}, each pinned by the
 * smallest input that shows it. Every case here was verified by running the same
 * input through a build of {@code main} (070bdd7): {@code main} produces the
 * expected value and this branch does not. They are regressions, not pre-existing
 * defects, and so are separated from the deferred-defect tests in
 * {@link ModernCssTest}.
 *
 * <p>The one exception is
 * {@link #aMungedLocalDoesNotCollideWithANamedFunctionExpression}, which fails on
 * {@code main} too. It is kept here because it is the same root cause as the
 * function-declaration case above it - {@code ScopeBuilder} never declares a
 * function's own name - and fixing that root cause fixes both. It is labelled
 * pre-existing so the regression count stays honest.
 */
class MergeGateRegressionTest {

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {}
        public void error(String m, String s, int l, String ls, int lo) {}
        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    private static String css(String input) throws Exception {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(input)).compress(out, -1);
        return out.toString();
    }

    private static String js(String input) throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(input), SILENT)
                .compress(out, -1, true, false, false, false);
        return out.toString();
    }

    // ---- CSS: empty-rule removal and the escaped "@" ---------------------------

    // The empty-rule pattern grew a third alternative that excludes "@", so when an
    // empty rule's selector contains an escaped "@" the match starts AT the "@" and
    // the text before it is stranded and glued onto the NEXT rule's selector. That
    // next rule is live CSS, and it stops matching anything. Exit code stays 0.
    //
    // Tailwind v4 emits exactly this shape for container-query variants
    // (".\@md\:flex"), and an unused variant is emitted as an empty rule.
    @Test
    void anEmptyRuleWithAnEscapedAtSignDoesNotCorruptTheFollowingSelector() throws Exception {
        assertEquals("p{color:red}", css(".\\@container{}p{color:red}"));
    }

    @Test
    void anEmptyTailwindContainerVariantDoesNotCorruptTheNextRule() throws Exception {
        assertEquals(".\\@lg\\:hidden{display:none}",
                css(".\\@lg\\:block{}.\\@lg\\:hidden{display:none}"));
    }

    @Test
    void anEscapedAtSignInATypeSelectorDoesNotCorruptTheNextRule() throws Exception {
        assertEquals("p{color:red}", css("a\\@b{}p{color:red}"));
    }

    // ---- CSS: @property must not make minification quadratic -------------------

    // preservePropertyAtRuleBlocks replaces each "@property{...}" with a brace-free
    // placeholder. The stylesheet then contains one long run with no "{", "}", "/",
    // ";" or "@", which is the worst case for the backtracking empty-rule regex.
    // Tailwind v4 emits one @property per theme variable, routinely 100-400.
    //
    // Guards the ORDER, not a wall-clock budget: doubling the input must not
    // quadruple the time. Generous factor so ordinary CI noise cannot fail it.
    @Test
    void manyPropertyAtRulesDoNotMakeMinificationQuadratic() throws Exception {
        css(atRules(50)); // warm up the regex engine and JIT

        long small = timeCss(atRules(400));
        long large = timeCss(atRules(800));

        assertTrue(large < small * 3 + 250,
                "doubling the @property count multiplied the time by "
                        + (small == 0 ? "infinity" : String.valueOf((double) large / small))
                        + " (" + small + "ms -> " + large + "ms); the empty-rule regex is "
                        + "backtracking over the brace-free placeholder run");
    }

    private static String atRules(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("@property --p").append(i).append("{inherits:false}\n");
        }
        return sb.toString();
    }

    private static long timeCss(String input) throws Exception {
        long start = System.nanoTime();
        css(input);
        return (System.nanoTime() - start) / 1_000_000L;
    }

    // ---- CSS: a brace or semicolon inside an unquoted url() ---------------------

    // preserveCustomPropertyValues' depth counter decrements on ")" and "]" without
    // a floor, so it goes negative and the "depth == 0" test that should end the
    // custom-property value never fires again. "{" and "}" are legal url-token code
    // points (CSS Syntax L3 4.3.6), so one unquoted url() containing a brace makes
    // the scan swallow the rest of the stylesheet.
    //
    // Not corruption - the output stays valid CSS. It is silent NON-minification:
    // everything from the offending declaration to EOF is emitted verbatim while
    // the tool reports success.
    @Test
    void aBraceInsideAnUnquotedUrlDoesNotDisableMinification() throws Exception {
        assertEquals(":root{--bg:url(a}b.png)}p:hover{color:red;margin:0}",
                css(":root{--bg:url(a}b.png)}p:hover { color : red ; margin : 0px }"));
    }

    @Test
    void anOpeningBraceInsideAnUnquotedUrlDoesNotDisableMinification() throws Exception {
        assertEquals(":root{--bg:url(a{b.png)}p:hover{color:red;margin:0}",
                css(":root{--bg:url(a{b.png)}p:hover { color : red ; margin : 0px }"));
    }

    // ";" is likewise a legal url-token code point, but it is in the at-rule and
    // declaration boundary sets, so a ";" inside an unquoted url() makes the "--"
    // that follows look like the start of a real custom property.
    @Test
    void aSemicolonInsideAnUnquotedUrlDoesNotDisableMinification() throws Exception {
        assertEquals("a{background:url(/x/;--y.png)}b:hover{color:red;margin:0}",
                css("a{background:url(/x/;--y.png)}b:hover { color : red ; margin : 0px }"));
    }

    // Scale matters more than the single declaration: the damage is unbounded, not
    // local. One such value on line 1 leaves the whole file untouched.
    @Test
    void oneBadUrlDoesNotLeaveTheRestOfTheStylesheetUnminified() throws Exception {
        StringBuilder sb = new StringBuilder(":root{--bg:url(a}b.png)}\n");
        for (int i = 0; i < 200; i++) {
            sb.append(".r").append(i).append("{color:#AABBCC;margin:0px}\n");
        }
        String result = css(sb.toString());

        assertTrue(result.indexOf("#AABBCC") < 0,
                "no colour after the offending declaration was minified; "
                        + result.length() + " of " + sb.length() + " bytes came back verbatim");
    }

    // ---- JS: a function's own name is neither munged nor reserved --------------

    // ScopeBuilder never declares a FunctionNode's name in the enclosing scope, so
    // the munger hands that same name out as a free symbol. Before this release the
    // bug was unreachable in an IIFE because nested function scopes were not
    // traversed at all; the scope-traversal fix that produces the -24% jQuery win
    // is what arms it. Output parses, so node --check does not notice.
    @Test
    void aMungedLocalDoesNotCollideWithAFunctionDeclarationName() throws Exception {
        String compressed = js("(function(){\n"
                + "  function f(x){ return x * 2; }\n"
                + "  var v1=1, v2=2, v3=3, v4=4, v5=5, v6=6;\n"
                + "  return f(v1) + v2 + v3 + v4 + v5 + v6;\n"
                + "})();");

        assertTrue(compressed.indexOf("var f=") < 0,
                "a local was munged to \"f\", the name of the function declared beside it, "
                        + "so calling f() now reads the variable: " + compressed);
    }

    // A named function expression binds its own name in a scope that shadows the
    // surrounding variable, so a recursive self-call must keep resolving to the
    // function even after the outer variable is reassigned. The generator rewrites
    // the self-call to the outer binding instead.
    //
    // REGRESSION: main leaves this scope unmunged and is correct. Distinct from
    // the collision tests above - here no two names collide; the reference is
    // simply resolved against the wrong binding.
    @Test
    void aNamedFunctionExpressionSelfCallIsNotRewrittenToTheOuterVariable()
            throws Exception {
        String compressed = js("var result = (function () {\n"
                + "  var stringify = function stringify(n) {\n"
                + "    return n <= 1 ? '1' : n + ',' + stringify(n - 1); };\n"
                + "  var saved = stringify;\n"
                + "  stringify = function () { return 'REPLACED'; };\n"
                + "  return saved(3);\n"
                + "})();");

        // "stringify" must survive twice: once naming the function expression, and
        // once as the recursive self-call inside it. Source returns "3,2,1"; when
        // the self-call is rewritten to the outer binding the result is
        // "3,REPLACED", because that binding is reassigned in between.
        int occurrences = 0;
        for (int at = compressed.indexOf("stringify"); at >= 0;
                at = compressed.indexOf("stringify", at + 1)) {
            occurrences++;
        }
        // At least two: the name on the function expression, and the self-call
        // inside it. Not an exact count - main munges nothing here and so keeps
        // five, which is equally correct.
        assertTrue(occurrences >= 2,
                "the recursive self-call was rewritten to the outer binding, so "
                        + "reassigning that binding changes what the recursion calls: "
                        + compressed);
    }

    // PRE-EXISTING, not a regression: this fails on main too, because a named
    // function expression's own name was never declared in its scope there either.
    // Same root cause as the test above; listed so one fix closes both.
    @Test
    void aMungedLocalDoesNotCollideWithANamedFunctionExpression() throws Exception {
        String compressed = js("function outer(){ var longName=5; "
                + "var g=function a(){return longName;}; return g(); }");

        assertTrue(compressed.indexOf("var a=") < 0,
                "a local was munged to \"a\", the name of the function expression beside it: "
                        + compressed);
    }
}
