package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The command line OPTIONS that decide how input is read and where output goes:
 * {@code --charset}, {@code --type} inference, {@code -m}, and
 * {@code --line-break} parsing.
 *
 * <p>Deliberately scoped to complement {@code CommandLineProcessTest}, which
 * covers the process contract itself - {@code --help}, no arguments, a missing
 * file, an unparseable script, stdout compression and the {@code -o} pattern.
 * Nothing here repeats those; this class is about the options that select a
 * decoder, a file type and a break position, each of which is a distinct branch
 * in {@code YUICompressor.run()} and none of which any test executed.
 *
 * <p>{@code System.exit} is observed by running the entry point in a child JVM
 * on this JVM's classpath. The coverage agent, when present, is forwarded to
 * the child so these executions are counted rather than invisible.
 */
class CliOptionsProcessTest {

    private static final class Result {
        final int exit;
        final String out;
        final String err;

        Result(int exit, String out, String err) {
            this.exit = exit;
            this.out = out;
            this.err = err;
        }

        @Override
        public String toString() {
            return "exit=" + exit + "\nstdout=[" + out + "]\nstderr=[" + err + "]";
        }
    }

    private static Result run(String stdin, String... args) throws IOException, InterruptedException {
        return run(stdin, StandardCharsets.UTF_8, args);
    }

    private static Result run(String stdin, Charset outputCharset, String... args)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.addAll(coverageAgentArgs());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.yahoo.platform.yui.compressor.YUICompressor");
        command.addAll(Arrays.asList(args));

        File out = File.createTempFile("yui-cli-out-", ".txt");
        File err = File.createTempFile("yui-cli-err-", ".txt");
        try {
            Process process = new ProcessBuilder(command).redirectOutput(out).redirectError(err).start();
            if (stdin != null) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("the compressor did not finish within 60s for " + command);
            }
            return new Result(process.exitValue(),
                    new String(Files.readAllBytes(out.toPath()), outputCharset),
                    new String(Files.readAllBytes(err.toPath()), StandardCharsets.UTF_8));
        } finally {
            out.delete();
            err.delete();
        }
    }

    /**
     * Forwards this JVM's coverage agent to the child, appending to the same
     * data file. Without it the entry point runs where no instrumentation can
     * see it and reports 0% while being fully exercised. A no-op when the suite
     * runs without a coverage agent.
     */
    private static List<String> coverageAgentArgs() {
        List<String> forwarded = new ArrayList<>();
        for (String argument : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:") && argument.contains("jacoco")) {
                forwarded.add(argument.contains("append=") ? argument : argument + ",append=true");
            }
        }
        return forwarded;
    }

    private static Path write(Path dir, String name, String content) throws IOException {
        return write(dir, name, content, StandardCharsets.UTF_8);
    }

    private static Path write(Path dir, String name, String content, Charset charset) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, content.getBytes(charset));
        return file;
    }

    private static final String JS = "function f(alpha){var beta=alpha;return beta}";
    private static final String JS_MIN = "function f(b){var a=b;return a}";

    // --- --type inference --------------------------------------------------

    @Test
    void aJsExtensionIsEnoughToInferTheType(@TempDir Path dir) throws Exception {
        Result r = run(null, write(dir, "a.js", JS).toString());
        assertEquals(0, r.exit, r.toString());
        assertEquals(JS_MIN, r.out, r.toString());
    }

    @Test
    void aCssExtensionSelectsTheCssCompressor(@TempDir Path dir) throws Exception {
        Result r = run(null, write(dir, "a.css", "body { color: #ff0000; }").toString());
        assertEquals(0, r.exit, r.toString());
        assertEquals("body{color:red}", r.out, r.toString());
    }

    @Test
    void anExplicitTypeOverridesAnUnrecognisedExtension(@TempDir Path dir) throws Exception {
        Result r = run(null, "--type", "css", write(dir, "a.txt", "body { color: #ff0000; }").toString());
        assertEquals(0, r.exit, r.toString());
        assertEquals("body{color:red}", r.out, r.toString());
    }

    @Test
    void anUnrecognisedExtensionWithoutATypeIsAUsageError(@TempDir Path dir) throws Exception {
        Result r = run(null, write(dir, "a.txt", "body { color: red }").toString());
        assertEquals(1, r.exit, r.toString());
        assertTrue(r.err.contains("Usage: java -jar"), r.toString());
        assertEquals("", r.out, "nothing may reach stdout when the type could not be determined: " + r);
    }

    @Test
    void anInvalidTypeIsAUsageErrorEvenWhenTheExtensionWouldHaveWorked(@TempDir Path dir) throws Exception {
        Result r = run(null, "--type", "xml", write(dir, "a.js", JS).toString());
        assertEquals(1, r.exit, r.toString());
        assertTrue(r.err.contains("Usage: java -jar"), r.toString());
    }

    @Test
    void stdinWithAnExplicitCssTypeSelectsTheCssCompressor() throws Exception {
        Result r = run("body { color: #ff0000; }", "--type", "css");
        assertEquals(0, r.exit, r.toString());
        assertEquals("body{color:red}", r.out, r.toString());
    }

    // --- --charset ---------------------------------------------------------

    @Test
    void anExplicitCharsetIsUsedForBothReadingAndWriting(@TempDir Path dir) throws Exception {
        Charset sjis = Charset.forName("Shift_JIS");
        Path file = write(dir, "a.css", "/*! 日本語 */body { color: #ff0000; }", sjis);
        Result r = run(null, sjis, "--charset", "Shift_JIS", file.toString());
        assertEquals(0, r.exit, r.toString());
        assertEquals("/*! 日本語 */body{color:red}", r.out, r.toString());
    }

    @Test
    void nonAsciiSurvivesTheDefaultUtf8Charset(@TempDir Path dir) throws Exception {
        Result r = run(null, write(dir, "a.js", "var s='こんにちは😀';").toString());
        assertEquals(0, r.exit, r.toString());
        assertEquals("var s='こんにちは😀';", r.out, r.toString());
    }

    /**
     * Characterisation, not endorsement. {@code YUICompressor.run()} does
     * {@code if (charset == null || !Charset.isSupported(charset)) charset = "UTF-8"},
     * so an unsupported name is silently replaced and the run succeeds. A typo
     * in a build script therefore decodes the file with the wrong encoding and
     * exits 0, and the only notice is behind {@code -v}, which nobody passes.
     * If this is ever changed to fail, this test should change with it, so the
     * change is deliberate.
     */
    @Test
    void anUnsupportedCharsetIsSilentlyReplacedByUtf8(@TempDir Path dir) throws Exception {
        Result r = run(null, "--charset", "No-Such-Charset", write(dir, "a.css", "body { color: red }").toString());
        assertEquals(0, r.exit, r.toString());
        assertEquals("body{color:red}", r.out, r.toString());
        assertEquals("", r.err, "nothing warns the user that their charset was ignored: " + r);
    }

    @Test
    void verboseAnnouncesTheCharsetFallback(@TempDir Path dir) throws Exception {
        Result r = run(null, "-v", "--charset", "No-Such-Charset",
                write(dir, "a.css", "body { color: red }").toString());
        assertEquals(0, r.exit, r.toString());
        assertTrue(r.err.contains("Using charset UTF-8"), r.toString());
    }

    // --- -m (mungemap) -----------------------------------------------------

    @Test
    void mungemapFileRecordsTheOriginalToMungedMapping(@TempDir Path dir) throws Exception {
        Path map = dir.resolve("map.txt");
        Result r = run(null, "-m", map.toString(), write(dir, "a.js", JS).toString());
        assertEquals(0, r.exit, r.toString());
        // The leading "f: f" is the function's own name; see CompressorApiTest's
        // theMungemapOverloadWritesTheOriginalToMungedMapping for why it is listed.
        assertEquals("f: f\n\ta: beta\n\tb: alpha\n", new String(Files.readAllBytes(map), StandardCharsets.UTF_8),
                "the mungemap is the only way a caller can reverse the renaming, so its exact shape is a contract");
    }

    @Test
    void mungemapIsEmptyWhenNothingIsMunged(@TempDir Path dir) throws Exception {
        Path map = dir.resolve("map.txt");
        Result r = run(null, "-m", map.toString(), "--nomunge", write(dir, "a.js", JS).toString());
        assertEquals(0, r.exit, r.toString());
        assertEquals("", new String(Files.readAllBytes(map), StandardCharsets.UTF_8), r.toString());
    }

    // --- --line-break ------------------------------------------------------

    @Test
    void lineBreakInsertsBreaksAtStatementBoundaries(@TempDir Path dir) throws Exception {
        Result r = run(null, "--line-break", "10",
                write(dir, "a.js", "var aaaa=1;var bbbb=2;var cccc=3;").toString());
        assertEquals(0, r.exit, r.toString());
        assertEquals("var aaaa=1;\nvar bbbb=2;\nvar cccc=3;", r.out, r.toString());
    }

    @Test
    void lineBreakZeroAndNegativeInsertNoBreaks(@TempDir Path dir) throws Exception {
        Path file = write(dir, "a.js", "var aaaa=1;var bbbb=2;var cccc=3;");
        for (String value : new String[] { "0", "-1", "-5" }) {
            Result r = run(null, "--line-break", value, file.toString());
            assertEquals(0, r.exit, "--line-break " + value + ": " + r);
            assertEquals("var aaaa=1;var bbbb=2;var cccc=3;", r.out, "--line-break " + value + ": " + r);
        }
    }

    @Test
    void aNonNumericLineBreakIsAUsageErrorRatherThanADefault(@TempDir Path dir) throws Exception {
        Result r = run(null, "--line-break", "abc", write(dir, "a.js", JS).toString());
        assertEquals(1, r.exit, r.toString());
        assertTrue(r.err.contains("Usage: java -jar"), r.toString());
        assertEquals("", r.out, "a bad break position must not silently compress with the default: " + r);
    }

    // --- remaining option plumbing -----------------------------------------

    @Test
    void nomungeKeepsTheOriginalLocalNames(@TempDir Path dir) throws Exception {
        Result r = run(null, "--nomunge", write(dir, "a.js", JS).toString());
        assertEquals(0, r.exit, r.toString());
        assertEquals(JS, r.out, r.toString());
    }

    @Test
    void anUnknownFlagNamesItselfBeforeTheUsage() throws Exception {
        Result r = run(null, "--bogus", "--type", "js");
        assertEquals(1, r.exit, r.toString());
        assertTrue(r.err.contains("--bogus"), "the message must name the flag that was rejected: " + r);
    }

    @Test
    void versionPrintsToStderrAndExitsZero() throws Exception {
        Result r = run(null, "-V");
        assertEquals(0, r.exit, r.toString());
        assertEquals("", r.out, "version goes to stderr, so stdout stays clean for piped output: " + r);
        assertFalse(r.err.trim().isEmpty(), r.toString());
    }

    /**
     * Not the exit code - {@code CommandLineProcessTest} owns that - but the
     * DIAGNOSTIC. The error reporter installed by {@code run()} formats
     * {@code line + ':' + lineOffset + ':' + message} and names the file; both
     * were unexecuted, and a minifier that says only "it failed" is not usable.
     */
    @Test
    void aJavaScriptSyntaxErrorIsReportedWithItsFileLineAndColumn(@TempDir Path dir) throws Exception {
        Result r = run(null, write(dir, "a.js", "var a = 1;\nvar b = ;\n").toString());
        assertTrue(r.err.contains("2:9:"), "the diagnostic must carry line:column, and the error is at 2:9: " + r);
        assertTrue(r.err.contains("a.js"), "the diagnostic must name the file: " + r);
    }
}
