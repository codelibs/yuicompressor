package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * The parts of the public compressor API that no test called: the four
 * documented no-op options, the mungemap overload, and what an
 * {@link ErrorReporter} is actually handed.
 *
 * <p>Every existing call site passes {@code (out, -1, munge, false, false,
 * false)} or {@code (out, -1)} - measured across the whole suite - so
 * {@code preserveAllSemiColons}, {@code disableOptimizations}, {@code verbose}
 * and {@code preserveUnknownHints} were never once passed {@code true}, and the
 * 8-argument overload's {@code mungemap} was never non-null. That is 9 lines
 * and 3 branches of {@code JavaScriptCompressor} with no coverage, including
 * the entire mapping writer.
 */
class CompressorApiTest {

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    private static final String SOURCE = "function f(alpha){var beta=alpha;return beta;}";

    private static String compress(String source, boolean munge, boolean verbose, boolean preserveSemi,
            boolean disableOpt, boolean preserveHints) throws IOException {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT)
                .compress(out, null, -1, munge, verbose, preserveSemi, disableOpt, preserveHints);
        return out.toString();
    }

    // --- the four documented no-ops ---------------------------------------

    /**
     * {@code preserveAllSemiColons}, {@code disableOptimizations},
     * {@code verbose} and {@code preserveUnknownHints} are documented in
     * {@code JavaScriptCompressor}'s javadoc as accepted and never read. That
     * documentation is the Release 1 fix for the gap; this is the test that
     * keeps it honest. A caller who passes {@code --preserve-semi} today gets
     * exactly the output they would get without it.
     *
     * <p>When any of these is implemented, this test fails and must be replaced
     * by one asserting the new behaviour - which is the point. Nothing else in
     * the suite would notice.
     */
    @Test
    void theFourIgnoredOptionsChangeNothing() throws Exception {
        String baseline = compress(SOURCE, true, false, false, false, false);
        assertEquals(baseline, compress(SOURCE, true, true, false, false, false), "verbose changed the output");
        assertEquals(baseline, compress(SOURCE, true, false, true, false, false),
                "preserveAllSemiColons changed the output");
        assertEquals(baseline, compress(SOURCE, true, false, false, true, false),
                "disableOptimizations changed the output");
        assertEquals(baseline, compress(SOURCE, true, false, false, false, true),
                "preserveUnknownHints changed the output");
        assertEquals(baseline, compress(SOURCE, true, true, true, true, true), "all four together changed the output");
    }

    /**
     * The specific shape {@code --preserve-semi} is about, stated as output
     * rather than as sameness. The generator emits ";}" unconditionally, so
     * there is nothing for the flag to preserve - and the complementary
     * optimisation that would REMOVE that semicolon (upstream's CHANGELOG 1.1)
     * is not implemented either. {@link JsGoldenFileTest} measures the cost at
     * 1,259 occurrences and 1,259 bytes on jQuery; this pins the shape at
     * statement scale, with and without the flag.
     */
    @Test
    void theSemicolonBeforeAClosingBraceIsEmittedWithOrWithoutPreserveSemi() throws Exception {
        assertEquals("function f(){var a=1;return a;}",
                compress("function f(){ var x = 1; return x }", true, false, true, false, false));
        assertEquals("function f(){var a=1;return a;}",
                compress("function f(){ var x = 1; return x }", true, false, false, false, false));
    }

    /**
     * {@code -p} promises to keep unrecognised compiler hints. It does not, and
     * it does not strip them either - the hint string survives as a live
     * expression statement, which is what {@link JsGoldenFileTest}'s _munge.js
     * entry describes from the other end.
     */
    @Test
    void preserveHintsNeitherHonoursNorStripsANomungeHint() throws Exception {
        String withFlag = compress("function f(longParam){ 'longParam:nomunge'; return longParam; }",
                true, false, false, false, true);
        String withoutFlag = compress("function f(longParam){ 'longParam:nomunge'; return longParam; }",
                true, false, false, false, false);
        assertEquals(withoutFlag, withFlag, "the flag made no difference");
        assertTrue(withFlag.contains("longParam:nomunge"), "the hint is emitted as a live statement: " + withFlag);
        assertTrue(withFlag.contains("function f(a)"), "and the parameter is munged anyway: " + withFlag);
    }

    // --- the mungemap overload --------------------------------------------

    @Test
    void theMungemapOverloadWritesTheOriginalToMungedMapping() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter map = new StringWriter();
        new JavaScriptCompressor(new StringReader(SOURCE), SILENT)
                .compress(out, map, -1, true, false, false, false, false);
        assertEquals("function f(b){var a=b;return a;}", out.toString());
        // "f: f" is the function declaration's own name, in the global scope and so
        // never munged. It is listed because it is now a declared binding - reserving
        // it is what stops a local being munged to "f" - and the mapping reports every
        // binding it knows, renamed or not.
        assertEquals("f: f\n\ta: beta\n\tb: alpha\n", map.toString(),
                "the mapping is the only way to reverse the renaming; its exact shape is the contract");
    }

    @Test
    void theMungemapCoversNestedScopesWithDeepeningIndentation() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter map = new StringWriter();
        new JavaScriptCompressor(new StringReader(
                "function outer(alpha){ function inner(beta){ return beta + alpha; } return inner(1); }"), SILENT)
                        .compress(out, map, -1, true, false, false, false, false);
        assertTrue(map.toString().contains("\ta: alpha"), map.toString());
        assertTrue(map.toString().contains("\t\ta: beta") || map.toString().contains("\t\tb: beta"),
                "a nested scope is indented one level deeper: " + map);
    }

    @Test
    void theMungemapStaysEmptyWhenNothingIsMunged() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter map = new StringWriter();
        new JavaScriptCompressor(new StringReader(SOURCE), SILENT)
                .compress(out, map, -1, false, false, false, false, false);
        assertEquals("", map.toString(), "no munging means no mapping to write");
    }

    // --- error reporting ---------------------------------------------------

    /**
     * A recording reporter, so the test can assert what the compressor told it
     * rather than only that something went wrong.
     */
    private static final class Recorder implements ErrorReporter {
        final List<String> events = new ArrayList<>();

        public void warning(String m, String s, int line, String ls, int col) {
            events.add("warning " + line + ":" + col + " " + m);
        }

        public void error(String m, String s, int line, String ls, int col) {
            events.add("error " + line + ":" + col + " " + m);
        }

        public EvaluatorException runtimeError(String m, String s, int line, String ls, int col) {
            events.add("runtimeError " + line + ":" + col + " " + m);
            return new EvaluatorException(m);
        }
    }

    @Test
    void aSyntaxErrorIsReportedAtItsLineAndColumn() throws Exception {
        Recorder recorder = new Recorder();
        assertThrows(EvaluatorException.class,
                () -> new JavaScriptCompressor(new StringReader("var a = 1;\nvar b = ;\n"), recorder));
        assertTrue(recorder.events.contains("error 2:9 syntax error"),
                "the reporter must be given the position of the fault, not just the message: " + recorder.events);
    }

    @Test
    void aSyntaxErrorInsideAnObjectPatternIsAlsoLocated() throws Exception {
        Recorder recorder = new Recorder();
        assertThrows(EvaluatorException.class,
                () -> new JavaScriptCompressor(new StringReader("function f({ \n"), recorder));
        assertTrue(recorder.events.stream().anyMatch(e -> e.startsWith("error 1:")),
                "expected a located error, got " + recorder.events);
    }

    /**
     * A null reporter is accepted and replaced by a built-in one, which is the
     * form {@link YUICompressorTest} uses. Its error path had no coverage: the
     * suite only ever passed null for input that parses.
     */
    @Test
    void aNullReporterStillFailsLoudlyOnASyntaxError() {
        EvaluatorException failure = assertThrows(EvaluatorException.class,
                () -> new JavaScriptCompressor(new StringReader("var a = ;"), null));
        assertTrue(failure.getMessage().contains("syntax error") || failure.getMessage().contains("syntax errors"),
                failure.getMessage());
    }

    @Test
    void aNullReporterCompressesValidSourceNormally() throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(SOURCE), null).compress(out, -1, true, false, false, false);
        assertEquals("function f(b){var a=b;return a;}", out.toString());
    }

    /**
     * The CSS side has no reporter at all, so its one deliberate failure is an
     * unchecked exception. Asserted here because the message is the entire
     * diagnostic a user gets, and because the asymmetry with the JavaScript
     * side is worth having written down.
     */
    @Test
    void cssReportsAnUnterminatedCommentWithItsOffset() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
            StringWriter out = new StringWriter();
            new CssCompressor(new StringReader("a{color:red} /* oops")).compress(out, -1);
        });
        assertTrue(failure.getMessage().contains("unterminated CSS comment"), failure.getMessage());
        assertTrue(failure.getMessage().contains("offset 13"),
                "the message must locate the opener, not just name the problem: " + failure.getMessage());
    }

    @Test
    void anEmptyInputCompressesToAnEmptyOutput() throws Exception {
        StringWriter js = new StringWriter();
        new JavaScriptCompressor(new StringReader(""), SILENT).compress(js, -1, true, false, false, false);
        assertEquals("", js.toString());

        StringWriter css = new StringWriter();
        new CssCompressor(new StringReader("   \n\t  ")).compress(css, -1);
        assertEquals("", css.toString());
    }

    @Test
    void aNegativeLineBreakPositionIsTreatedAsNoLineBreaks() throws Exception {
        StringWriter js = new StringWriter();
        new JavaScriptCompressor(new StringReader("var aaaa=1;var bbbb=2;var cccc=3;"), SILENT)
                .compress(js, -5, true, false, false, false);
        assertEquals("var aaaa=1;var bbbb=2;var cccc=3;", js.toString());

        StringWriter css = new StringWriter();
        new CssCompressor(new StringReader("aaaa{color:red}bbbb{color:blue}")).compress(css, -5);
        assertEquals("aaaa{color:red}bbbb{color:blue}", css.toString());
    }

    /**
     * Zero is not "no line breaks" for CSS - it breaks after every rule - while
     * for JavaScript it is. {@link ModernCssTest#breakingStillHappensWhereItShould}
     * covers the CSS half; this pins the JavaScript half, which nothing did.
     */
    @Test
    void lineBreakZeroInsertsNoBreaksInJavaScript() throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader("var aaaa=1;var bbbb=2;var cccc=3;"), SILENT)
                .compress(out, 0, true, false, false, false);
        assertEquals("var aaaa=1;var bbbb=2;var cccc=3;", out.toString());
    }

    @Test
    void aStatementLongerThanTheLineBreakIsLeftIntact() throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader("var s='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';"), SILENT)
                .compress(out, 5, true, false, false, false);
        assertNotNull(out.toString());
        assertEquals("var s='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';", out.toString(),
                "there is no safe offset inside a single statement, so it must not be cut");
    }
}
