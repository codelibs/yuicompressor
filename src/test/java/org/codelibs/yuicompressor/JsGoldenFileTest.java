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
     * Fixtures whose current output does not match the golden file because of a
     * real defect. Each entry must be removed by the task that fixes it.
     */
    private static final List<String> KNOWN_FAILURES =
            List.of("issue86.js", "jquery-1.6.4.js", "promise-catch-finally-issue203.js");

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
                    .filter(n -> !n.startsWith("_"))
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
