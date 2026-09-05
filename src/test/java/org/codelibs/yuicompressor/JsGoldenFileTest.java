package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Compresses every *.js fixture that has a matching *.js.min golden file and
 * compares the result, using the same defaults as the command line tool.
 */
class JsGoldenFileTest {

    private static final Path RESOURCES = Paths.get("src/test/resources");

    /**
     * Fixtures whose current output does not match the golden file. Every entry
     * carries a measured reason; an entry whose reason is fixed must be removed
     * by the task that fixes it. There is no second, undocumented quarantine:
     * the five "_"-prefixed fixtures below were excluded by a filename filter
     * that appeared in three test classes, was documented nowhere and referenced
     * nowhere else, and hid five mismatching pairs while the CHANGELOG counted
     * the JS corpus as four. They are listed here instead.
     *
     * <p><b>issue86.js</b> - the compressor emits ".0.toString();1.3.toString();"
     * (a leading-dot numeric literal, valid on its own since it cannot be
     * confused with member access), while the golden expects
     * "(0).toString();(1.3).toString();" (parenthesized). Both are valid
     * JavaScript - node --check accepts the compressor's output, and node
     * confirms ".0.toString()" evaluates to "0" - so this is a numeric-literal
     * formatting difference, not a correctness defect.
     *
     * <p><b>jquery-1.6.4.js</b> - does not, and is not expected to, match
     * byte-for-byte: the golden was produced by a different compressor
     * generation. Ours is 104,154 bytes against a golden of 101,992, a gap of
     * 2,162 (2.1%); it was 1,476 before free references started being reserved
     * (+686, a correctness cost - see ScopeBuilder.reserveFreeReferences), 2,823
     * before the redundant ";" and the conditional parentheses were fixed, and
     * 137,798 before the ScopeBuilder traversal fix. Composition of the gap,
     * measured over both files:
     *
     * <pre>
     * contributor                  bytes   evidence
     * longer munged names         +2,011   identifier chars: golden 68,857, ours 70,868
     *                                      (identifier tokens are equal: 18,102 each)
     * extra parentheses             +84    "(" count: golden 3,529, ours 3,613
     * extra spaces                   +81   " " count: golden 1,368, ours 1,449
     * fewer braces                   -62   "{" count: golden 1,917, ours 1,855
     * missing ";" before "}"           0   ";}" count: golden 0, ours 0
     * "/*!" banners                    0   both files: 2 banners, 537 bytes, byte-identical
     * unattributed                   ~48   other punctuation
     * </pre>
     *
     * None of these is a correctness defect. What is left of the gap is almost
     * entirely short-name allocation: the two files have exactly the same number
     * of identifier tokens, so the 2,011 extra characters are real. 686 of them
     * are the price of correctness - locals no longer munged onto globals the
     * file does not declare - and the rest is Release 2 work.
     *
     * <p>There is one further difference that costs no bytes: the banners are
     * byte-identical but sit at different offsets (golden 0 and 41,566, ours 0
     * and 367). CommentPreserver.insertComments emits every preserved comment at
     * the top of the file by design - its javadoc explains that AST positions
     * cannot be reliably mapped to compressed output positions - so a bundled
     * component's licence banner ends up detached from the code it licenses.
     *
     * <p><b>_munge.js</b> - the "a:nomunge" hint is both ignored (the parameters
     * are munged anyway) and emitted into the output as a live string statement.
     * Note the golden is not merely different here, it looks wrong: it munges
     * the outer "var w = window" to "a" while preserving a parameter also named
     * "a", so its "a.alert(...)" calls alert on the parameter rather than on
     * window. Our output keeps those distinct. Hint support is Release 2 work;
     * this golden should be re-examined rather than matched.
     *
     * <p><b>_string_combo.js</b> - string-literal merging ("a"+"b"+"c" to "abc",
     * CHANGELOG 2.1) is absent.
     *
     * <p><b>_string_combo2.js</b> - the golden carries a stray ";" after the
     * function declaration, an empty statement this generator does not emit.
     *
     * <p><b>_string_combo3.js</b> - same as _string_combo2.js.
     *
     * <p><b>_syntax_error.js</b> - quote-character optimisation is absent: the
     * source's single-quoted strings stay single-quoted where the golden
     * normalises them to double quotes.
     */
    private static final List<String> KNOWN_FAILURES = List.of("issue86.js", "jquery-1.6.4.js", "_munge.js",
            "_string_combo.js", "_string_combo2.js", "_string_combo3.js", "_syntax_error.js");

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

    static Stream<String> fixtures() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".js"))
                    .filter(n -> Files.exists(RESOURCES.resolve(n + ".min")))
                    .filter(n -> !KNOWN_FAILURES.contains(n))
                    .sorted()
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    /**
     * A size and shape pin for jquery-1.6.4.js, which is quarantined above and
     * is the only large real-world fixture in the corpus.
     *
     * <p>Quarantining it removed the byte-level guard it used to provide, and
     * nothing stood in for it: reverting the D1 brace fix - which this class's
     * own javadoc measures at 2,200 bytes on this very file - left
     * {@code compressesToGoldenFile} 3/3 green. A byte count and two structural
     * counts restore a pin without pretending the golden matches.
     *
     * <p>These are exact rather than bounded on purpose. Compression output is
     * deterministic, so a change here is always something a person should look
     * at; if it is an improvement, the numbers move down and get updated along
     * with the gap table above.
     */
    @Test
    void jqueryCompressesToItsMeasuredSizeAndShape() throws Exception {
        String source = new String(Files.readAllBytes(RESOURCES.resolve("jquery-1.6.4.js")), StandardCharsets.UTF_8);
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT).compress(out, -1, true, false, false, false);
        String compressed = out.toString();

        // 104,770 until a function's own name started being reserved, then 104,815,
        // then 103,468 once the redundant ";" before "}" and the parentheses around
        // a conditional on an assignment's right-hand side were removed. Reserving
        // free references costs 686 of that back (0.7%), for the same reason and
        // the same kind of bug as reserving a function's own name: a local was
        // being munged onto a global the file does not declare.
        assertEquals(104154, compressed.getBytes(StandardCharsets.UTF_8).length,
                "jQuery output size changed; if this is an improvement, update this and the gap table above");
        assertEquals(0, count(compressed, "{{"),
                "a redundant brace pair is back; see the D1 entry in the gap table above");
        assertEquals(0, count(compressed, ";}"),
                "a ';' before a '}' is back; it is redundant and upstream removes it too");
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void compressesToGoldenFile(String fixture) throws Exception {
        String source = new String(Files.readAllBytes(RESOURCES.resolve(fixture)), StandardCharsets.UTF_8);
        String expected = new String(Files.readAllBytes(RESOURCES.resolve(fixture + ".min")), StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        JavaScriptCompressor compressor = new JavaScriptCompressor(new StringReader(source), SILENT);
        compressor.compress(out, -1, true, false, false, false);

        assertEquals(expected.trim(), out.toString().trim(), "golden mismatch: " + fixture);
    }
}
