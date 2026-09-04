package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import com.yahoo.platform.yui.compressor.MungedCodeGenerator;

/**
 * How {@code yuicompressor.strict} is READ, as opposed to what strict mode
 * does once it is on - which {@link StrictNodeCoverageTest} covers.
 *
 * <p>{@link StrictNodeCoverageTest} only ever sets the property to
 * {@code "true"} and clears it, so the one thing never asked is what any OTHER
 * value means. {@code MungedCodeGenerator.isStrict()} is
 * {@code System.getProperty(STRICT_PROPERTY) != null}, so every value means
 * "on" - see {@link #aFalsyValueMustNotEnableStrictMode}, which fails today.
 *
 * <p>This class saves and RESTORES the property rather than clearing it, so it
 * cannot destroy a value the build was started with. {@link
 * StrictNodeCoverageTest} clears it instead, which silently disables strict
 * mode for every test class scheduled after it in the same JVM when someone
 * runs {@code mvn test -Dyuicompressor.strict=true}.
 */
class StrictModePropertyTest {

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    /** Rhino 1.8.0 parses this and the generator has no case for it. */
    private static final String REACHES_THE_FALLBACK = "var a = [i*2 for (i in x)];";

    private String saved;

    @BeforeEach
    void rememberTheProperty() {
        saved = System.getProperty(MungedCodeGenerator.STRICT_PROPERTY);
    }

    @AfterEach
    void restoreTheProperty() {
        if (saved == null) {
            System.clearProperty(MungedCodeGenerator.STRICT_PROPERTY);
        } else {
            System.setProperty(MungedCodeGenerator.STRICT_PROPERTY, saved);
        }
    }

    private static String compress(String source) throws IOException {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT).compress(out, -1, true, false, false, false);
        return out.toString();
    }

    @Test
    void withThePropertyAbsentTheFallbackIsLenient() throws Exception {
        System.clearProperty(MungedCodeGenerator.STRICT_PROPERTY);
        assertEquals("var a=[i * 2 for (i in x)];", compress(REACHES_THE_FALLBACK),
                "the lenient fallback re-prints the subtree with toSource(), spacing and all");
    }

    @Test
    void withThePropertySetToTrueTheFallbackThrows() {
        System.setProperty(MungedCodeGenerator.STRICT_PROPERTY, "true");
        IOException failure = assertThrows(IOException.class, () -> compress(REACHES_THE_FALLBACK));
        assertEquals(MungedCodeGenerator.UnsupportedSyntaxException.class, rootCause(failure).getClass());
    }

    /**
     * <b>This test FAILS on the current code, and the failure is the point.</b>
     *
     * <p>{@code isStrict()} tests the property for {@code != null}, so the
     * strings a person writes to turn a flag OFF all turn it ON:
     * {@code -Dyuicompressor.strict=false} makes the compressor refuse files it
     * compresses fine by default. Worse, {@code -Dyuicompressor.strict} with no
     * value at all - which is how a shell often passes such a flag - is the
     * empty string, also non-null, also on.
     *
     * <p>Nothing in {@link StrictNodeCoverageTest} could catch this: it sets
     * the property to {@code "true"} in {@code @BeforeEach} and clears it in
     * {@code @AfterEach}, so those are the only two states it ever observes.
     *
     * <p>The fix is one line - {@code Boolean.parseBoolean(System.getProperty(
     * STRICT_PROPERTY))} - and it keeps {@code =true} working unchanged.
     */
    @ParameterizedTest
    @ValueSource(strings = { "false", "", "0", "off", "no" })
    void aFalsyValueMustNotEnableStrictMode(String value) {
        System.setProperty(MungedCodeGenerator.STRICT_PROPERTY, value);
        assertDoesNotThrow(() -> compress(REACHES_THE_FALLBACK),
                "-D" + MungedCodeGenerator.STRICT_PROPERTY + "=" + value
                        + " reads as \"enable strict mode\", because isStrict() only tests the property for null");
    }

    @Test
    void anExplicitTrueIsStillHonouredAfterAFalsyValueHasBeenSeen() {
        System.setProperty(MungedCodeGenerator.STRICT_PROPERTY, "false");
        System.setProperty(MungedCodeGenerator.STRICT_PROPERTY, "true");
        assertThrows(IOException.class, () -> compress(REACHES_THE_FALLBACK));
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
