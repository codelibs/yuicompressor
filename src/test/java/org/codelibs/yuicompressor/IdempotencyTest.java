package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * {@code compress(compress(x)) == compress(x)}.
 *
 * <p>A minifier's output is valid input to itself, so a second pass must be a
 * no-op. It is the cheapest whole-corpus invariant available and nothing in the
 * suite asserted it: the golden tests compare one pass against a stored file
 * and the differential tests compare behaviour, so a pass that CREATES work for
 * the next one - leaving a token it would have rewritten, or rewriting
 * something it should have left alone - is invisible to both. Unlike a golden
 * file it needs no expected output, so it covers every fixture including the
 * seven quarantined ones.
 *
 * <p>Munging is deliberately excluded from the JavaScript half. Munged names
 * are themselves valid identifiers, so a second pass re-allocates them and a
 * fixed point is not expected - see {@link #mungingIsNotAFixedPoint}, which
 * records what actually happens instead of pretending the invariant holds.
 */
class IdempotencyTest {

    private static final Path RESOURCES = Paths.get("src/test/resources");

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    private static String css(String source) throws IOException {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(source)).compress(out, -1);
        return out.toString();
    }

    private static String js(String source, boolean munge) throws IOException {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT).compress(out, -1, munge, false, false, false);
        return out.toString();
    }

    private static Stream<String> fixtures(String extension) throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(extension))
                    .sorted()
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    static Stream<String> cssFixtures() throws IOException {
        return fixtures(".css");
    }

    static Stream<String> jsFixtures() throws IOException {
        return fixtures(".js");
    }

    private static String read(String fixture) throws IOException {
        return new String(Files.readAllBytes(RESOURCES.resolve(fixture)), StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cssFixtures")
    void everyCssFixtureCompressesToAFixedPoint(String fixture) throws Exception {
        String once = css(read(fixture));
        assertEquals(once, css(once), "a second pass changed the output of " + fixture);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsFixtures")
    void everyJsFixtureCompressesToAFixedPointWithoutMunging(String fixture) throws Exception {
        String once = js(read(fixture), false);
        assertEquals(once, js(once, false), "a second pass changed the output of " + fixture);
    }

    /**
     * Shapes chosen because a first pass leaves them in a form a naive second
     * pass would mangle: preserved comments, preserved tokens, respaced calc(),
     * and the separator-bearing operator pairs.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "a{width:calc(100%-10px)}",
            "a{width:calc(var(--gap)*2)}",
            "a{background:url(data:image/png;base64,iVBORw0KGgo=)}",
            "/*! banner */a{color:#ff0000}",
            "html>/**/body{color:#ff0000}",
            ":root{--brand:#ff0000;--pad:0px}",
            "@charset \"utf-8\";a{color:#ff0000}",
            "@media screen AND (min-width:400px){a{color:#ff0000}}",
            "a{filter:progid:DXImageTransform.Microsoft.Matrix(M11=1,M12=0)}",
            "a{margin:0px 0px 0px 0px}",
            "@layer utilities{}a{color:#ff0000}" })
    void cssReachesAFixedPointOnShapesThatSurviveTheFirstPass(String source) throws Exception {
        String once = css(source);
        assertEquals(once, css(once), "a second pass changed [" + source + "]");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "var t1 = a + +b;",
            "var t2 = a - -b;",
            "var y = x / /re/.test('re') ? 1 : 2;",
            "var r = a + ++b;",
            "var r = a < !--b;",
            "for (var i=0;i<3;i++) { f(); }",
            "outer: for (var i=0;i<3;i++) { break outer; }",
            "var a = [, , b, , ];",
            "var o = { g };",
            "function f({b}){ return b; }",
            "function f(a=1, ...rest){ return a + rest.length; }",
            "function* g(){ yield* other(); }",
            "var s = `x${y}z`;",
            "var b = 10n + 0xffn;",
            "/*! banner */\nvar a = 1;",
            "var v = config.timeout ?? config.server?.timeout;",
            "do { f(); } while (x);",
            "try { g(); } catch { h(); }" })
    void javascriptReachesAFixedPointOnShapesThatSurviveTheFirstPass(String source) throws Exception {
        String once = js(source, false);
        assertEquals(once, js(once, false), "a second pass changed [" + source + "]");
    }

    /**
     * Characterisation, and a Release 2 lead rather than a defect claim.
     *
     * <p>With munging on, compression is NOT a fixed point, and the second pass
     * is not merely different - it is materially SMALLER. Re-compressing the
     * jQuery output shrinks it by another 5,296 characters, roughly 5%. Since
     * both passes run the same allocator on the same program, a single pass
     * leaving that much on the table points at the short-name allocation that
     * {@link JsGoldenFileTest}'s gap table already measures as 1,280 characters
     * worse than the golden's.
     *
     * <p>Pinned exactly, like the size assertions in that class: if a Release 2
     * allocator fix lands, this number moves and someone must look at it.
     */
    @Test
    void mungingIsNotAFixedPoint() throws Exception {
        String once = js(read("jquery-1.6.4.js"), true);
        String twice = js(once, true);
        assertEquals(104814, once.length(), "first-pass length changed; see JsGoldenFileTest's gap table");
        assertEquals(99511, twice.length(), "second-pass length changed");
        assertTrue(twice.length() < once.length(),
                "a second munging pass is expected to be smaller while allocation is unoptimised");
    }

    /**
     * The counterpart guard: with munging OFF the same file IS a fixed point,
     * so the difference above is allocation and nothing else.
     */
    @Test
    void withoutMungingJqueryIsAFixedPoint() throws Exception {
        String once = js(read("jquery-1.6.4.js"), false);
        assertEquals(once, js(once, false));
    }
}
