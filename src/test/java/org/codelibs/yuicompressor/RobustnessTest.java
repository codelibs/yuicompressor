package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Whole-compressor properties nothing else asserts: thread safety, symbol-table
 * capacity, line endings, byte order marks, and non-ASCII text.
 */
class RobustnessTest {

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    private static String js(String source) throws IOException {
        return js(source, true);
    }

    private static String js(String source, boolean munge) throws IOException {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT).compress(out, -1, munge, false, false, false);
        return out.toString();
    }

    private static String css(String source) throws IOException {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(source)).compress(out, -1);
        return out.toString();
    }

    // --- concurrency -------------------------------------------------------

    /**
     * Both compressors are used from build tools that minify many files in
     * parallel, and both reach static state - {@code JavaScriptCompressor}'s
     * shared {@code ones}/{@code twos}/{@code threes} symbol lists and
     * {@code CssCompressor}'s static regexes. Nothing tested that a second
     * thread cannot see a torn or shared value.
     *
     * <p>Fixed inputs, compared against a single-threaded result computed
     * first: any interference shows as a mismatch or a thrown exception, both
     * of which are collected rather than swallowed.
     */
    @Test
    void eightThreadsCompressingConcurrentlyAllProduceTheSingleThreadedResult() throws Exception {
        final String jsSource = "function outer(alpha,beta){var gamma=alpha+beta;"
                + "function inner(delta){return delta+gamma;}return inner(1);}";
        final String cssSource = "a{color:#ff0000;margin:0px;width:calc(100%-10px)}"
                + "b{background:url(data:image/png;base64,AAA=)}";
        final String jsExpected = js(jsSource);
        final String cssExpected = css(cssSource);

        final int threads = 8;
        final int iterations = 40;
        final List<String> problems = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        String actualJs = js(jsSource);
                        if (!jsExpected.equals(actualJs)) {
                            problems.add("js differed: " + actualJs);
                        }
                        String actualCss = css(cssSource);
                        if (!cssExpected.equals(actualCss)) {
                            problems.add("css differed: " + actualCss);
                        }
                    }
                } catch (Throwable e) {
                    problems.add("threw " + e);
                } finally {
                    done.countDown();
                }
            }, "compressor-" + t).start();
        }

        start.countDown();
        assertTrue(done.await(120, TimeUnit.SECONDS), "concurrent compression did not finish within 120s");
        assertEquals(List.of(), problems, "concurrent compression is not deterministic");
    }

    // --- symbol table capacity --------------------------------------------

    private static final Pattern DECLARED = Pattern.compile("var ([A-Za-z][A-Za-z0-9]*)=");

    /**
     * {@code ScriptOrFnScope.munge} has three symbol pools - one, two and three
     * characters - and falls through to the next when the current one is
     * exhausted, throwing "ran out of symbols" if even the third runs dry. Only
     * the one-character pool had coverage: the largest scope in the whole
     * fixture corpus never exhausts it, so the fall-through was 18 unexecuted
     * lines guarding a correctness property - names must stay DISTINCT across
     * the boundary, or two locals collide and the program silently changes.
     */
    @Test
    void aScopeLargerThanTheSingleCharacterPoolGetsDistinctLongerNames() throws Exception {
        StringBuilder source = new StringBuilder("function f(){");
        int locals = 80;
        for (int i = 0; i < locals; i++) {
            source.append("var localVariableNumber").append(i).append("=").append(i).append(";");
        }
        source.append("return localVariableNumber0+localVariableNumber").append(locals - 1).append(";}");

        String compressed = js(source.toString());

        Set<String> names = new HashSet<>();
        Matcher m = DECLARED.matcher(compressed);
        int count = 0;
        boolean sawMultiCharacter = false;
        while (m.find()) {
            names.add(m.group(1));
            count++;
            if (m.group(1).length() > 1) {
                sawMultiCharacter = true;
            }
        }
        assertEquals(locals, count, "every local should still be declared: " + compressed);
        assertEquals(locals, names.size(),
                "two locals were given the same munged name, which silently merges them: " + compressed);
        assertTrue(sawMultiCharacter,
                "with " + locals + " locals the one-character pool must be exhausted: " + compressed);
        assertTrue(compressed.indexOf("localVariableNumber") < 0, "nothing should be left un-munged: " + compressed);
    }

    /**
     * The pool is shared down the scope chain, so a nested scope must not
     * reuse a name its enclosing scope is still using - that is what the
     * "remove the symbols already used in the containing scopes" step in
     * {@code munge()} is for, and it is on the same untested fall-through path.
     */
    @Test
    void aNestedScopeDoesNotReuseAnEnclosingScopesName() throws Exception {
        StringBuilder source = new StringBuilder("function outer(){");
        for (int i = 0; i < 60; i++) {
            source.append("var outerLocal").append(i).append("=").append(i).append(";");
        }
        source.append("function inner(){");
        for (int i = 0; i < 60; i++) {
            source.append("var innerLocal").append(i).append("=outerLocal").append(i).append(";");
        }
        source.append("return innerLocal0;}return inner();}");

        String compressed = js(source.toString());
        int innerStart = compressed.indexOf("function ", 1);
        assertTrue(innerStart > 0, compressed);

        Set<String> outerNames = declaredNames(compressed.substring(0, innerStart));
        Set<String> innerNames = declaredNames(compressed.substring(innerStart));
        assertEquals(60, outerNames.size(), compressed);
        assertEquals(60, innerNames.size(), compressed);
        assertEquals(Set.of(), intersection(outerNames, innerNames),
                "the inner scope reused a name the outer scope still holds, so its reads resolve to the wrong "
                        + "variable: " + compressed);
    }

    private static Set<String> declaredNames(String code) {
        Set<String> names = new HashSet<>();
        Matcher m = DECLARED.matcher(code);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> both = new HashSet<>(a);
        both.retainAll(b);
        return both;
    }

    // --- line endings ------------------------------------------------------

    @Test
    void crlfSourceCompressesIdenticallyToLfSource() throws Exception {
        String lf = "function f(alpha){\n  var beta = alpha;\n  return beta;\n}\n";
        assertEquals(js(lf), js(lf.replace("\n", "\r\n")), "CRLF input must not change the output");

        String cssLf = "body {\n  color: #ff0000;\n}\n";
        assertEquals(css(cssLf), css(cssLf.replace("\n", "\r\n")), "CRLF input must not change the output");
    }

    @Test
    void aLoneCarriageReturnIsAlsoTreatedAsWhitespace() throws Exception {
        assertEquals("body{color:red}", css("body {\r color: #ff0000;\r}\r"));
    }

    // --- byte order marks ---------------------------------------------------

    /**
     * The two sides disagree, and both behaviours are recorded rather than
     * asserted to be right. Rhino consumes a leading U+FEFF as whitespace, so
     * JavaScript loses it; CSS has no such rule and emits it as the first
     * character of the output, where it is part of the first selector.
     *
     * <p>Reading a UTF-8 file through an {@code InputStreamReader} - which is
     * exactly what the command line does - does NOT strip the BOM, so this is
     * the reachable path, not a synthetic one. Whether the CSS side should
     * strip it is a decision for someone; that it currently does not should
     * not be a surprise.
     */
    @Test
    void aByteOrderMarkIsConsumedInJavaScriptAndPreservedInCss() throws Exception {
        String bom = "﻿";
        assertEquals("var a=1;", js(bom + "var a = 1;"), "Rhino treats a leading BOM as whitespace");

        String cssOut = css(bom + "body { color: #ff0000; }");
        assertEquals(bom + "body{color:red}", cssOut, "the CSS side passes the BOM through unchanged");
        assertEquals('﻿', cssOut.charAt(0), "and it stays the first character, inside the first selector");
    }

    @Test
    void aByteOrderMarkInsideAStringIsNeverTouched() throws Exception {
        assertEquals("a{content:\"﻿\"}", css("a{content:\"﻿\"}"));
        assertEquals("var s=\"﻿\";", js("var s = \"﻿\";"));
    }

    // --- non-ASCII ----------------------------------------------------------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            ".日本語 .b{color:#ff0000}|.日本語 .b{color:red}",
            "a::before{content:\"→\"}|a::before{content:\"→\"}",
            "a{font-family:\"ヒラギノ角ゴ\",sans-serif}|a{font-family:\"ヒラギノ角ゴ\",sans-serif}",
            "a{content:\"😀\"}|a{content:\"😀\"}",
            ":root{--余白:10px}|:root{--余白:10px}" })
    void nonAsciiCssRoundTrips(String source, String expected) throws Exception {
        assertEquals(expected, css(source));
    }

    @Test
    void aNonAsciiLocalIsMungedAndANonAsciiGlobalIsNot() throws Exception {
        assertEquals("function f(){var a=1;return a;}", js("function f(){ var ある = 1; return ある; }"));
        assertEquals("var ある=1;ある++;", js("var ある = 1; ある++;"));
    }

    @Test
    void nonAsciiAndAstralStringContentSurvivesMunging() throws Exception {
        assertEquals("function f(){var a=\"こんにちは😀\";return a;}",
                js("function f(){ var greeting = \"こんにちは😀\"; return greeting; }"));
    }

    /**
     * An astral-plane identifier - legal in ES2015, {@code var 𝑎 = 1} - is
     * rejected by Rhino 1.8.0's lexer rather than compressed. Recorded as a
     * known limitation: the important half is that it FAILS rather than
     * emitting something that parses but means something else, which is the
     * failure mode this release spent its effort on. A Rhino upgrade in
     * Release 2 should make this test fail and be replaced.
     */
    @Test
    void anAstralPlaneIdentifierIsRejectedRatherThanCorrupted() {
        assertThrows(EvaluatorException.class,
                () -> js("function f(){ var 𝑎 = 1; return 𝑎; }"));
    }

    // --- calc() tokenizer boundaries ---------------------------------------

    /**
     * {@code CssCompressor.isIdentifierChar} accepts "_", "\\" and any
     * character at or above U+0080, and none of those three had coverage -
     * removing all three from the predicate leaves the entire 526-test suite
     * green, while breaking the four cases below. Custom properties with
     * underscores and non-ASCII names are ordinary CSS.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a{width:calc(100%-var(--my_gap))}|a{width:calc(100% - var(--my_gap))}",
            "a{width:calc(var(--my_gap)*2)}|a{width:calc(var(--my_gap) * 2)}",
            "a{width:calc(100%-var(--余白))}|a{width:calc(100% - var(--余白))}",
            "a{width:calc(2*_x)}|a{width:calc(2 * _x)}" })
    void calcKeepsIdentifiersThatUseUnderscoresOrNonAsciiIntact(String source, String expected) throws Exception {
        assertEquals(expected, css(source));
    }

    @Test
    void calcTreatsTabsAndNewlinesAsWhitespace() throws Exception {
        assertEquals("a{width:calc(100% - 10px)}", css("a{width:calc(100%\t-\t10px)}"));
        assertEquals("a{width:calc(100% - 10px)}", css("a{width:calc(100%\n-\n10px)}"));
    }

    @Test
    void calcHandlesNumbersWrittenWithALeadingDot() throws Exception {
        assertEquals("a{width:calc(.5em + 1px)}", css("a{width:calc(.5em+1px)}"));
        assertEquals("a{width:calc(1px + .5em)}", css("a{width:calc(1px+.5em)}"));
    }

    @Test
    void mungingActuallyChangesSomething() throws Exception {
        // A guard on this class itself: several assertions above compare two
        // compressions against each other, which a compressor that did nothing
        // would satisfy. This one cannot be satisfied that way.
        assertNotEquals(js("function f(){ var longLocalName = 1; return longLocalName; }", false),
                js("function f(){ var longLocalName = 1; return longLocalName; }", true));
    }
}
