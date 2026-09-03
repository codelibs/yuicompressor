package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Feeds the compressed output of every JS fixture to "node --check". An expected
 * value comparison only protects cases somebody thought of; this catches any
 * output that is not valid JavaScript at all.
 */
class JsOutputSyntaxTest {

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

    static boolean nodeAvailable() {
        try {
            return new ProcessBuilder("node", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static Stream<String> fixtures() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".js"))
                    .filter(n -> !n.startsWith("_"))
                    .sorted()
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    @EnabledIf("nodeAvailable")
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void compressedOutputParses(String fixture) throws Exception {
        String source = new String(Files.readAllBytes(RESOURCES.resolve(fixture)), StandardCharsets.UTF_8);

        // Some fixtures are not valid JavaScript to begin with. Rhino is lenient
        // enough to accept them, but the compressor cannot be asked to turn
        // invalid input into valid output, so they are not this test's business.
        // promise-catch-finally-issue203.js is one: it contains
        // "new Promise(resolve, reject) {}", which node rejects.
        if (nodeRejects(source)) {
            return;
        }

        StringWriter out = new StringWriter();
        try {
            new JavaScriptCompressor(new StringReader(source), SILENT)
                    .compress(out, -1, true, false, false, false);
        } catch (Exception parseFailure) {
            // Input the compressor cannot parse is a separate concern; this test
            // only asserts that whatever it does emit is valid JavaScript.
            return;
        }

        String report = nodeCheck(out.toString());
        assertEquals("", report, "node rejected the compressed output of " + fixture);
    }

    /** Returns true when node refuses to parse the given source. */
    private static boolean nodeRejects(String code) throws Exception {
        return !nodeCheck(code).isEmpty();
    }

    /** Runs "node --check" and returns its combined output, empty when it parses. */
    private static String nodeCheck(String code) throws Exception {
        File temp = File.createTempFile("yui-syntax-", ".js");
        try {
            Files.write(temp.toPath(), code.getBytes(StandardCharsets.UTF_8));
            Process check = new ProcessBuilder("node", "--check", temp.getAbsolutePath())
                    .redirectErrorStream(true).start();
            String output = new String(readAll(check), StandardCharsets.UTF_8);
            return check.waitFor() == 0 ? "" : output;
        } finally {
            temp.delete();
        }
    }

    private static byte[] readAll(Process process) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = process.getInputStream().read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
