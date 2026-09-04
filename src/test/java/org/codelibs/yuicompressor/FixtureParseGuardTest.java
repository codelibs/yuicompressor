package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
 * A tripwire for the two silent-pass holes in {@link JsOutputSyntaxTest}.
 *
 * <p>{@code compressedOutputParses} (lines 92-97) and
 * {@code compressedOutputHasNoCommentInjection} (lines 127-131) both do
 *
 * <pre>
 * try {
 *     compressor = new JavaScriptCompressor(new StringReader(source), SILENT);
 * } catch (EvaluatorException parseFailure) {
 *     return;
 * }
 * </pre>
 *
 * <p>A bare {@code return} is reported by JUnit as <b>PASSED</b>, not skipped.
 * That is precisely the accounting problem the same class condemns six lines
 * above, where it correctly uses {@code Assumptions.assumeFalse} for the
 * node-rejects-the-source case and its comment says so: "Aborted as a SKIP, not
 * a silent pass. Returning early reported the method as PASSED, so the run
 * showed 10 node --check executions where 9 had happened."
 *
 * <p>Today 0 of the {@value #EXPECTED_FIXTURES} fixtures reach either catch, so
 * the hole is latent. It becomes real the moment a fixture stops parsing - a
 * Rhino upgrade in Release 2 tightening its grammar is the obvious way - and at
 * that moment the fixture silently drops out of BOTH the {@code node --check}
 * net and the comment-injection guard while the suite stays green and the test
 * count does not move.
 *
 * <p>This class fails instead. It asserts the precondition those two catches
 * exist to tolerate: every fixture parses. It is deliberately separate from
 * {@code JsOutputSyntaxTest} so it does not depend on node and cannot be
 * skipped for an environment reason.
 *
 * <p>The proper fix in {@code JsOutputSyntaxTest} is to replace both
 * {@code return}s with {@code Assumptions.abort(...)}, matching what the class
 * already does for its other exclusion; this guard is useful either way,
 * because a skip is still a lost execution that someone should see.
 */
class FixtureParseGuardTest {

    private static final Path RESOURCES = Paths.get("src/test/resources");

    /**
     * Pinned so that a fixture disappearing - or a filename filter quietly
     * reappearing, which this release removed once already - fails here rather
     * than shrinking the corpus unnoticed.
     */
    private static final int EXPECTED_FIXTURES = 10;

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    /** The same enumeration {@link JsOutputSyntaxTest#fixtures()} uses. */
    static Stream<String> fixtures() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".js"))
                    .sorted()
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    @Test
    void theFixtureCorpusIsTheExpectedSize() throws Exception {
        List<String> found = fixtures().collect(Collectors.toList());
        assertEquals(EXPECTED_FIXTURES, found.size(),
                "the JavaScript fixture corpus changed size; every class that enumerates it is affected: " + found);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void everyFixtureParses(String fixture) throws Exception {
        String source = new String(Files.readAllBytes(RESOURCES.resolve(fixture)), StandardCharsets.UTF_8);
        assertNotNull(new JavaScriptCompressor(new StringReader(source), SILENT),
                fixture + " no longer parses, so JsOutputSyntaxTest's two catch-and-return blocks now silently "
                        + "PASS for it and it has dropped out of the node --check and comment-injection nets");
    }

    /**
     * The same statement in one assertion, naming every casualty at once - so a
     * change that breaks several fixtures reports all of them rather than the
     * first alphabetically.
     */
    @Test
    void noFixtureIsSilentlyExcludedFromTheOutputSyntaxNet() throws Exception {
        List<String> unparseable = new ArrayList<>();
        for (String fixture : fixtures().collect(Collectors.toList())) {
            String source = new String(Files.readAllBytes(RESOURCES.resolve(fixture)), StandardCharsets.UTF_8);
            try {
                new JavaScriptCompressor(new StringReader(source), SILENT);
            } catch (EvaluatorException parseFailure) {
                unparseable.add(fixture + " (" + parseFailure.getMessage() + ")");
            }
        }
        assertEquals(List.of(), unparseable,
                "these fixtures hit JsOutputSyntaxTest's catch-and-return, which JUnit reports as PASSED");
    }
}
