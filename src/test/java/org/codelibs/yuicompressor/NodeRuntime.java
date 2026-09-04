package org.codelibs.yuicompressor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Locates and validates the {@code node} executable for the tests that need it.
 *
 * <p><b>A missing tool may skip; a broken tool must fail.</b> Both availability
 * probes used to be {@code catch (Exception e) { return false; }}, so a
 * {@code node} that was present but broken, sandboxed, or hanging disabled the
 * entire differential net silently and still reported BUILD SUCCESS. That is
 * the same shape as the {@code _}-prefix fixture filter this release removed: a
 * silent exclusion that makes a green build mean less than it looks like.
 *
 * <p>So exactly one outcome skips - {@code node} is not on {@code PATH}, which
 * is a legitimate environment difference. Every other failure (present but
 * non-zero exit, wrong output, or not finishing a trivial script inside
 * {@link #TIMEOUT_SECONDS}) throws, because it means the tool is broken rather
 * than absent and the tests that depend on it are not really passing.
 */
final class NodeRuntime {

    /**
     * Generous, but bounded. Every process this class starts is given a
     * deadline: a hung {@code node} must fail the build rather than hang it
     * forever with no output.
     */
    static final int TIMEOUT_SECONDS = 60;

    private static Boolean available;

    /**
     * A cached failure from {@link #probe()}. Cached like the success is, so a
     * broken node reports once per JVM rather than re-probing for every case -
     * with a hanging node and 34 cases, re-probing spent 34 times the timeout
     * before failing.
     */
    private static AssertionError probeFailure;

    private NodeRuntime() {
    }

    /**
     * Whether {@code node} is on {@code PATH} and can run a trivial script.
     * Probed once per JVM.
     *
     * @throws AssertionError if {@code node} is present but does not work
     */
    static synchronized boolean isAvailable() {
        if (probeFailure != null) {
            throw probeFailure;
        }
        if (available == null) {
            try {
                available = probe();
            } catch (AssertionError broken) {
                probeFailure = broken;
                throw broken;
            }
        }
        return available;
    }

    /** Reason string for a skip, naming how many executions are being lost. */
    static String skipReason(int executions) {
        return "node is not on PATH, so " + executions + " real executions in this class did not run";
    }

    private static boolean probe() {
        Process process;
        try {
            process = new ProcessBuilder("node", "-e", "process.stdout.write('yui-ok')")
                    .redirectErrorStream(true).start();
        } catch (IOException notOnPath) {
            // The one legitimate skip: no such executable.
            return false;
        }
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("node is on PATH but did not finish a trivial script within "
                        + TIMEOUT_SECONDS + "s. Refusing to skip the tests that depend on it, because a "
                        + "hanging node is a broken toolchain, not an absent one.");
            }
            String output = new String(readAll(process), StandardCharsets.UTF_8);
            int status = process.exitValue();
            if (status != 0 || !output.contains("yui-ok")) {
                throw new AssertionError("node is on PATH but failed a trivial script (exit " + status
                        + ", output \"" + output.trim() + "\"). Refusing to skip the tests that depend on it, "
                        + "because a broken node is not the same as an absent one.");
            }
            return true;
        } catch (IOException | InterruptedException e) {
            process.destroyForcibly();
            throw new AssertionError("node is on PATH but could not be run: " + e, e);
        }
    }

    /**
     * Runs {@code node} on {@code code} and returns its exit status and stdout.
     *
     * <p>Both streams are redirected to files rather than left as pipes, so
     * neither can fill its buffer and deadlock a script that writes more than
     * the pipe holds, and the wait is bounded so a looping script fails instead
     * of hanging the build.
     *
     * <p>stdout and stderr go to SEPARATE files rather than being merged. The
     * hang is what needed fixing, and separate files fix it just as well as
     * merging while keeping stderr out of the returned value - a stack trace
     * carries file names and line numbers that legitimately differ between a
     * source run and a compressed run, so merging it in would make every
     * throwing script report a spurious difference.
     */
    static String run(String code) throws IOException, InterruptedException {
        File script = File.createTempFile("yui-node-", ".js");
        File out = File.createTempFile("yui-node-out-", ".txt");
        File err = File.createTempFile("yui-node-err-", ".txt");
        try {
            Files.write(script.toPath(), code.getBytes(StandardCharsets.UTF_8));
            Process node = new ProcessBuilder("node", script.getAbsolutePath())
                    .redirectOutput(out).redirectError(err).start();
            if (!node.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                node.destroyForcibly();
                throw new AssertionError("node did not finish within " + TIMEOUT_SECONDS + "s running:\n" + code);
            }
            String stdout = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            return "exit=" + node.exitValue() + "\n" + stdout;
        } finally {
            script.delete();
            out.delete();
            err.delete();
        }
    }

    /** Runs "node --check" and returns its combined output, empty when it parses. */
    static String check(String code) throws IOException, InterruptedException {
        File script = File.createTempFile("yui-check-", ".js");
        File out = File.createTempFile("yui-check-out-", ".txt");
        try {
            Files.write(script.toPath(), code.getBytes(StandardCharsets.UTF_8));
            Process node = new ProcessBuilder("node", "--check", script.getAbsolutePath())
                    .redirectErrorStream(true).redirectOutput(out).start();
            if (!node.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                node.destroyForcibly();
                throw new AssertionError("node --check did not finish within " + TIMEOUT_SECONDS + "s");
            }
            String report = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
            return node.exitValue() == 0 ? "" : report;
        } finally {
            script.delete();
            out.delete();
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
