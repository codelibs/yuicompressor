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
     * generation. Measured after this release's fixes, ours is 104,770 bytes
     * against a golden of 101,992, a gap of 2,778 (2.7%); it was 137,798 before
     * the ScopeBuilder traversal fix. Composition of the gap, measured over both
     * files:
     *
     * <pre>
     * contributor                  bytes   evidence
     * missing ";" before "}"      +1,259   ";}" count: golden 0, ours 1,259
     * longer munged names         +1,280   identifier chars: golden 68,520, ours 69,800
     *                                      (identifier tokens are equal: 18,037 each)
     * extra parentheses             +128   "(" count: golden 3,529, ours 3,657
     * extra spaces                   +81   " " count: golden 1,368, ours 1,449
     * fewer braces                   -62   "{" count: golden 1,917, ours 1,855
     * "/*!" banners                    0   both files: 2 banners, 537 bytes, byte-identical
     * unattributed                   ~92   other punctuation
     * </pre>
     *
     * None of these is a correctness defect. Two notes on what the numbers say:
     * upstream's documented "Remove ';' when followed by a '}'" (CHANGELOG 1.1)
     * is absent here, and our short-name allocation is genuinely worse rather
     * than merely different - the two files have exactly the same number of
     * identifier tokens, so the 1,280 extra characters are real. Both are
     * Release 2 work.
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
     * CHANGELOG 2.1) is absent, plus the ";}" difference above.
     *
     * <p><b>_string_combo2.js</b> - the ";}" difference only. The golden also
     * carries a stray ";" after the function declaration, an empty statement
     * this generator does not emit.
     *
     * <p><b>_string_combo3.js</b> - same as _string_combo2.js. The redundant
     * double brace that used to be its most visible difference is fixed, but it
     * was not the only one, so this fixture stays quarantined.
     *
     * <p><b>_syntax_error.js</b> - quote-character optimisation is absent: the
     * source's single-quoted strings stay single-quoted where the golden
     * normalises them to double quotes. Plus the ";}" difference above.
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
