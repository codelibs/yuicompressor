package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import com.yahoo.platform.yui.compressor.MungedCodeGenerator;

/**
 * Runs the compressor with {@link MungedCodeGenerator#STRICT_PROPERTY} set, so
 * that any node type reaching the {@code default: toSource()} fallback fails
 * the build instead of silently emitting degraded output.
 *
 * <p>Why this exists rather than "add a case for each operator we know about":
 * the fallback re-prints the ORIGINAL source of a subtree, so identifiers keep
 * their pre-munge spelling while their declarations were munged - locals become
 * globals, and "?." is dropped outright. That output parses, so neither a
 * golden comparison nor {@code node --check} catches it. Worse, the whole
 * fixture corpus is pre-ES6 and reaches the fallback zero times, so the corpus
 * structurally cannot exercise it. Release 2 upgrades Rhino, which makes more
 * syntax parse and therefore routes MORE node types into the fallback. Gating
 * a hard failure on a system property converts "a node type nobody handled"
 * from silent corruption into a build break, without anyone having to predict
 * which node type it will be.
 *
 * <p>Strict mode is deliberately opt-in: production callers keep today's
 * lenient behaviour, and this class is the one place that turns it on.
 *
 * <p>The parameterized cases assert only that compression did not throw, which
 * is vacuous in form - {@code compress()} returns a {@code StringWriter}'s
 * contents and can never be null. That is correct for a tripwire, whose whole
 * job is "did any node type reach the fallback", but it means this class must
 * not be counted as output coverage: it stays green under both the
 * echo-the-input and no-munging mutations. What the output actually says is
 * pinned by {@link ModernJsTest}, {@link ParameterListTest} and the golden
 * fixtures.
 */
class StrictNodeCoverageTest {

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

    @BeforeEach
    void enableStrictMode() {
        System.setProperty(MungedCodeGenerator.STRICT_PROPERTY, "true");
    }

    @AfterEach
    void disableStrictMode() {
        System.clearProperty(MungedCodeGenerator.STRICT_PROPERTY);
    }

    /**
     * Every *.js fixture, including the "_"-prefixed ones excluded elsewhere.
     * Unlike the golden tests this asks nothing about the output's content, so
     * a fixture whose golden disagrees is still useful here.
     */
    static Stream<String> fixtures() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".js"))
                    .sorted()
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void noFixtureReachesTheToSourceFallback(String fixture) throws Exception {
        String source = new String(Files.readAllBytes(RESOURCES.resolve(fixture)), StandardCharsets.UTF_8);
        assertNotNull(compress(source), "compressing " + fixture + " under strict mode");
    }

    /**
     * Modern syntax the corpus does not contain. Each snippet must compress
     * with no node type reaching the fallback. Failing here means either a new
     * node type needs a case in {@code visitNode}, or - if Rhino cannot parse
     * the snippet at all - the snippet belongs in
     * {@link #modernSyntaxRhinoCannotParse} instead.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            // ES2020 nullish coalescing / ES2021 logical assignment: the four
            // operators that reached the fallback and silently un-munged their
            // operands, plus "**=" which did the same and was missed by the
            // original triage.
            "function f(alpha, beta) { var gamma = alpha ?? beta; return gamma; }",
            "function f(alpha, beta) { alpha ||= beta; return alpha; }",
            "function f(alpha, beta) { alpha &&= beta; return alpha; }",
            "function f(alpha, beta) { alpha ??= beta; return alpha; }",
            "function f(alpha, beta) { alpha **= beta; return alpha; }",
            "function f(alpha, beta) { return (alpha ?? beta) || 1; }",
            "function f(alpha, beta) { return alpha ?? (beta || 1); }",
            // Optional chaining, including the combination with "??" that
            // deleted the "?." outright.
            "function f(config) { return config.timeout ?? config.server?.timeout; }",
            "function f(alpha) { return alpha?.[0]; }",
            "function f(alpha) { return alpha?.(1); }",
            "function f(alpha) { return alpha?.b.c; }",
            // ES2016 exponentiation.
            "function f(alpha) { return alpha ** 2; }",
            // ES2015 core.
            "function f(alpha) { return `x${alpha}y`; }",
            "function f(alpha) { const {x: y = 2} = alpha; return y; }",
            "function f(alpha) { for (var i of alpha) { alpha = i; } return alpha; }",
            "var f = (alpha) => alpha + 1;",
            "function f(alpha=1) { return alpha; }",
            "function f(...args) { return args.length; }",
            "function* g(alpha) { yield* alpha; }",
            "function f(alpha) { let beta = alpha; return beta; }",
            "var o = { [1 + 1]: 2 };",
            "var o = { m(alpha) { return alpha; } };",
            "var o = { get x() { return 1; }, set x(v) { this.y = v; } };",
            // ES2019 optional catch binding.
            "function f(alpha) { try { return alpha(); } catch { return 0; } }",
            // ES2020 BigInt. Harmless in the lenient path (a leaf with no
            // identifiers inside), but strict mode could not compress any file
            // containing one, which matters because strict mode is what
            // Release 2 is meant to lean on.
            "function f(alpha) { return 10n + alpha; }",
            "function f(alpha) { return 0xffn + alpha; }",
            // Plain ES5 that used to reach the fallback via toSource().
            "function f(alpha) { debugger; return alpha; }" })
    void modernSyntaxDoesNotReachTheToSourceFallback(String source) throws Exception {
        assertNotNull(compress(source), "compressing under strict mode: " + source);
    }

    /**
     * Syntax Rhino 1.8.0 genuinely cannot parse. These are honest limitations,
     * not fallback leaks: the compressor rejects the input before code
     * generation, so nothing degraded is ever emitted. Recorded here so the
     * boundary of "what this compressor supports" is a testable statement, and
     * so that a Rhino upgrade in Release 2 shows up as a failure in this
     * method rather than as new silent output.
     */
    @ParameterizedTest
    @ValueSource(strings = { "class C { m() { return 1; } }", "async function f(alpha) { return await alpha; }",
            "function f(alpha) { return [...alpha]; }", "import x from 'y';", "export default 1;",
            "var f = (...rest) => rest;", "var {a, ...rest} = o;" })
    void modernSyntaxRhinoCannotParse(String source) {
        assertThrows(EvaluatorException.class,
                () -> new JavaScriptCompressor(new StringReader(source), SILENT),
                "expected a parse failure, not degraded output, for: " + source);
    }

    /**
     * Strict mode must actually fire. An array comprehension
     * ({@code Token.ARRAYCOMP}) is a node type Rhino 1.8.0 still parses and
     * this generator has no case for, so it is a live example of the hazard
     * rather than a synthetic one.
     */
    @Test
    void strictModeThrowsNamingTheUnhandledNodeType() {
        IOException failure = assertThrows(IOException.class, () -> compress("var a = [i*2 for (i in x)];"));
        Throwable cause = rootCause(failure);
        assertTrue(cause instanceof MungedCodeGenerator.UnsupportedSyntaxException,
                "expected UnsupportedSyntaxException, got " + cause);
        assertTrue(cause.getMessage().contains("ARRAYCOMP"), "message should name the node type: " + cause.getMessage());
        assertTrue(cause.getMessage().contains("ArrayComprehension"),
                "message should name the node class: " + cause.getMessage());
    }

    /**
     * The six node types that still have no handler, enumerated by sweeping
     * the constructs Rhino 1.8.0 accepts rather than sampled. All are
     * Rhino/E4X legacy no browser supports, and all are harmless in the
     * lenient path, so they are recorded here instead of fixed - see
     * {@link MungedCodeGenerator#STRICT_PROPERTY}. A seventh appearing, or one
     * of these starting to compress, means a real change rather than a gap in
     * the probe, and should fail here rather than be discovered later.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "function f(alpha) { return [i*alpha for (i in alpha)]; }",
            "function f(alpha) { return (i*alpha for (i in alpha)); }",
            "function f(alpha) { return <a b=\"1\">{alpha}</a>; }",
            "function f(alpha) { return alpha::b; }",
            "function f(alpha) { return alpha..b; }",
            "function f(alpha) { return alpha.@b; }" })
    void knownUnhandledNodeTypesThrowUnderStrictMode(String source) {
        IOException failure = assertThrows(IOException.class, () -> compress(source),
                "if this now compresses, move it into the supported table above: " + source);
        assertTrue(rootCause(failure) instanceof MungedCodeGenerator.UnsupportedSyntaxException,
                "expected UnsupportedSyntaxException, got " + rootCause(failure));
    }

    /**
     * The same input without strict mode still compresses, so turning the
     * property off restores today's lenient behaviour exactly. This is what
     * makes the strict flag safe to leave in the shipped jar.
     */
    @Test
    void withoutStrictModeTheFallbackStillEmitsOutput() throws Exception {
        System.clearProperty(MungedCodeGenerator.STRICT_PROPERTY);
        assertNotNull(compress("var a = [i*2 for (i in x)];"));
    }

    /**
     * The concrete corruption strict mode exists to prevent, pinned as a
     * characterisation test: with the fallback lenient, an unhandled node type
     * re-prints its subtree from the original source, so an identifier whose
     * declaration was munged keeps its old spelling and silently becomes a
     * global reference.
     */
    @Test
    void lenientFallbackReprintsUnmungedIdentifiers() throws Exception {
        System.clearProperty(MungedCodeGenerator.STRICT_PROPERTY);
        String out = compress("function f(alpha) { var beta = [i*alpha for (i in alpha)]; return beta; }");
        assertTrue(out.contains("alpha"),
                "the fallback is expected to leak the pre-munge name; if this now fails the node type "
                        + "gained a handler and this test should be replaced: " + out);
    }

    private static String compress(String source) throws IOException {
        JavaScriptCompressor compressor = new JavaScriptCompressor(new StringReader(source), SILENT);
        StringWriter out = new StringWriter();
        compressor.compress(out, -1, true, false, false, false);
        return out.toString();
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
