package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the command line the way a user does, by forking a JVM.
 *
 * <p>Nothing else in the suite does this. {@code YUICompressorTest} says outright
 * that it avoids {@code main()} "to avoid System.exit() issues", and
 * {@code CliOptionTest} - despite its name - calls the library API directly and
 * never touches argument parsing, exit codes, or file handling. Forking sidesteps
 * {@code System.exit} entirely and is what makes the exit code observable at all.
 */
class CommandLineProcessTest {

    private static final String MAIN = "com.yahoo.platform.yui.compressor.YUICompressor";

    private static final class Result {
        final int exitCode;
        final String stdout;
        final String stderr;

        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private static Result run(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(MAIN);
        for (String arg : args) {
            command.add(arg);
        }

        Process process = new ProcessBuilder(command).start();
        // Drain both pipes before waiting. A process that fills its stderr buffer
        // while we block in waitFor() would deadlock, and the compressor prints
        // whole stack traces there.
        byte[] out = drain(process.getInputStream());
        byte[] err = drain(process.getErrorStream());
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("the CLI did not exit within 60s: " + command);
        }
        return new Result(process.exitValue(),
                new String(out, StandardCharsets.UTF_8),
                new String(err, StandardCharsets.UTF_8));
    }

    private static byte[] drain(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int n; (n = in.read(chunk)) != -1; ) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    private static Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ---- guards that hold today ------------------------------------------------

    @Test
    void helpExitsZero() throws Exception {
        assertEquals(0, run("--help").exitCode);
    }

    @Test
    void noArgumentsExitsOne() throws Exception {
        assertEquals(1, run().exitCode);
    }

    @Test
    void aMissingInputFileExitsOne() throws Exception {
        Result result = run("/nonexistent-cli-test.js");
        assertEquals(1, result.exitCode);
    }

    @Test
    void unparseableJavaScriptExitsTwo(@TempDir Path dir) throws Exception {
        // Exit code 2 is load-bearing: the comment at YUICompressor.java:235 says
        // it is "used specifically by the web front-end".
        Path input = write(dir, "bad.js", "function ( {");
        assertEquals(2, run(input.toString()).exitCode);
    }

    @Test
    void compressingToStdoutWritesTheMinifiedResult(@TempDir Path dir) throws Exception {
        Path input = write(dir, "one.css", ".a { color: red }");
        Result result = run(input.toString());
        assertEquals(0, result.exitCode);
        assertEquals(".a{color:red}", result.stdout.trim());
    }

    @Test
    void anOutputPatternWritesEveryInputFile(@TempDir Path dir) throws Exception {
        write(dir, "a.css", ".a{color:red}");
        write(dir, "b.css", ".b{color:blue}");
        Result result = run("-o", ".css$:.min.css",
                dir.resolve("a.css").toString(), dir.resolve("b.css").toString());

        assertEquals(0, result.exitCode);
        assertEquals(".a{color:red}",
                Files.readString(dir.resolve("a.min.css")).trim());
        assertEquals(".b{color:blue}",
                Files.readString(dir.resolve("b.min.css")).trim());
    }

    // ---- a refusal must not destroy the destination ----------------------------

    // REGRESSION. collectComments now throws IllegalArgumentException on an
    // unterminated comment, which is the right call - main silently shipped an
    // internal placeholder token instead. But the writer is opened, and therefore
    // the destination truncated, BEFORE compress() runs, and YUICompressor catches
    // only IOException. So the refusal lands after the destination is already
    // empty: previous good output is gone, and with -o pointing at the input the
    // source file itself is destroyed. On main this input produced (corrupt)
    // output; it never zeroed an existing file.
    @Test
    void refusingToCompressDoesNotDestroyAnExistingOutputFile(@TempDir Path dir) throws Exception {
        Path input = write(dir, "in.css", "a{color:red}\n/* unterminated\n");
        Path output = write(dir, "out.css", "PREVIOUS GOOD OUTPUT");

        Result result = run("--type", "css", input.toString(), "-o", output.toString());

        assertTrue(result.exitCode != 0, "the refusal should be reported as a failure");
        assertEquals("PREVIOUS GOOD OUTPUT", Files.readString(output),
                "the destination was truncated before the refusal, so the previous "
                        + "output is gone; it is now " + Files.size(output) + " bytes");
    }

    @Test
    void refusingToCompressDoesNotDestroyTheSourceFile(@TempDir Path dir) throws Exception {
        Path input = write(dir, "inplace.css", "a{color:red}\n/* unterminated\n");
        long before = Files.size(input);

        run("--type", "css", input.toString(), "-o", input.toString());

        assertEquals(before, Files.size(input),
                "in-place minification destroyed the source file when the compressor "
                        + "refused: " + before + " bytes -> " + Files.size(input));
    }

    // A refusal should reach the user as a diagnostic, not as a raw Java stack
    // trace. The JavaScript side already does this - it wraps its equivalent
    // failure in IOException and prints "[ERROR] in <file>".
    @Test
    void aRefusalIsReportedWithoutAnUncaughtStackTrace(@TempDir Path dir) throws Exception {
        Path input = write(dir, "in.css", "a{color:red}\n/* unterminated\n");
        Result result = run("--type", "css", input.toString());

        assertTrue(result.stderr.indexOf("Exception in thread \"main\"") < 0,
                "the CLI let the exception escape uncaught:\n" + result.stderr);
    }

    // ---- multiple inputs to stdout ---------------------------------------------

    // PRE-EXISTING (identical on main; YUICompressor.java is untouched by this
    // release). The finally block closes the writer wrapping System.out after the
    // first file, and PrintStream swallows the resulting IOException into a flag
    // nothing checks - so every later file is discarded with exit code 0.
    @Test
    void everyInputFileReachesStdout(@TempDir Path dir) throws Exception {
        write(dir, "m1.css", ".m1{color:red}");
        write(dir, "m2.css", ".m2{color:blue}");

        Result result = run(dir.resolve("m1.css").toString(), dir.resolve("m2.css").toString());

        assertEquals(0, result.exitCode);
        assertTrue(result.stdout.contains(".m2{color:blue}"),
                "the second input was silently discarded; stdout was: " + result.stdout);
    }
}
