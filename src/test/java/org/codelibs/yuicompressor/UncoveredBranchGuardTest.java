package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import com.yahoo.platform.yui.compressor.MungedCodeGenerator;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

/**
 * Guards for behaviour that is correct today but that no test executed, so a
 * refactor could break it silently. Every test here PASSES on this branch - they
 * are new coverage, not findings. Each one was picked because a mutation of the
 * branch it covers survived the whole 526-test suite, or because a JaCoCo run
 * showed the branch was never reached.
 */
class UncoveredBranchGuardTest {

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {}
        public void error(String m, String s, int l, String ls, int lo) {}
        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    private static String css(String input) throws IOException {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(input)).compress(out, -1);
        return out.toString();
    }

    private static String js(String input) throws IOException {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(input), SILENT)
                .compress(out, -1, true, false, false, false);
        return out.toString();
    }

    // ---- calc() identifier characters ------------------------------------------

    // isIdentifierChar accepts "_", "\" and >= 0x80, and respaceCalcOperators uses
    // it to decide where an identifier ends. Dropping any of the three splits a
    // custom-property name around the "-" and rewrites it as a subtraction. All
    // three were unreached: deleting them from isIdentifierChar left 526/526 green.

    @Test
    void anUnderscoreInsideCalcDoesNotSplitACustomPropertyName() throws Exception {
        assertEquals("a{width:calc(100% - var(--my_gap))}",
                css("a{width:calc(100% - var(--my_gap))}"));
    }

    @Test
    void aNonAsciiCustomPropertyNameSurvivesCalcRespacing() throws Exception {
        assertEquals("a{width:calc(100% - var(--余白))}",
                css("a{width:calc(100% - var(--余白))}"));
    }

    @Test
    void anEscapedHyphenInsideCalcIsNotReadAsAnOperator() throws Exception {
        assertEquals("a{width:calc(100% - var(--a\\-b))}",
                css("a{width:calc(100% - var(--a\\-b))}"));
    }

    @Test
    void respacingInsertsTheMissingSpacesAroundAnOperator() throws Exception {
        assertEquals("a{width:calc(100% - var(--my_gap))}",
                css("a{width:calc(100%-var(--my_gap))}"));
    }

    // isCssSpace covers tab, CR and FF, not just " ". Only " " was ever exercised.
    @Test
    void aTabInsideCalcIsTreatedAsWhiteSpace() throws Exception {
        assertEquals("a{width:calc(1px + 2px)}", css("a{width:calc(1px\t+\t2px)}"));
    }

    // startsNumber covers a leading "." and a signed number.
    @Test
    void aLeadingDotNumberInsideCalcIsPreserved() throws Exception {
        assertEquals("a{width:calc(.5em + 1px)}", css("a{width:calc(.5em + 1px)}"));
    }

    @Test
    void aSignedNumberInsideCalcIsNotReadAsAnOperator() throws Exception {
        assertEquals("a{width:calc(-1px + 2px)}", css("a{width:calc(-1px + 2px)}"));
        assertEquals("a{width:calc(+1px + 2px)}", css("a{width:calc(+1px + 2px)}"));
    }

    // ---- the mungemap overload -------------------------------------------------

    // ScriptOrFnScope.getFullMapping and the JavaScriptCompressor lines that call
    // it were never executed: every call site in the suite uses the 6-arg compress
    // or passes a null mungemap, so the whole mungemap writer was dead in test.
    @Test
    void theEightArgumentCompressWritesAMungeMap() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter mungemap = new StringWriter();
        new JavaScriptCompressor(
                new StringReader("function outer(){ var alpha=1, beta=2; return alpha+beta; }"),
                SILENT)
                .compress(out, mungemap, -1, true, false, false, false, false);

        assertTrue(out.toString().indexOf("alpha") < 0, "the local was not munged: " + out);
        String map = mungemap.toString();
        assertTrue(map.indexOf("alpha") >= 0,
                "the munge map does not mention the original name: " + map);
        assertTrue(map.indexOf("beta") >= 0,
                "the munge map does not mention the original name: " + map);
    }

    // ---- the symbol pool past one character ------------------------------------

    // ScriptOrFnScope.munge's fall-through past the single-character pool was never
    // reached - no fixture has a scope with more locals than there are one-letter
    // names. The uncovered lines are what keeps the generated names distinct across
    // that boundary, and a bug there merges two locals into one.
    @Test
    void aScopeLargerThanTheOneCharacterPoolStillGivesEveryLocalItsOwnName()
            throws Exception {
        int locals = 300;
        StringBuilder source = new StringBuilder("function outer(){\n");
        for (int i = 0; i < locals; i++) {
            source.append("  var localNumber").append(i).append(" = ").append(i).append(";\n");
        }
        source.append("  return [");
        for (int i = 0; i < locals; i++) {
            source.append(i == 0 ? "" : ",").append("localNumber").append(i);
        }
        source.append("];\n}");

        String compressed = js(source.toString());

        List<String> names = new ArrayList<>();
        int at = 0;
        while ((at = compressed.indexOf("var ", at)) >= 0) {
            at += 4;
            int end = at;
            while (end < compressed.length()
                    && Character.isJavaIdentifierPart(compressed.charAt(end))) {
                end++;
            }
            names.add(compressed.substring(at, end));
        }

        assertEquals(locals, names.size(), "expected one declaration per local: " + names.size());
        assertEquals(locals, new java.util.HashSet<>(names).size(),
                "two locals were munged to the same name, so one overwrites the other");
    }

    // ---- the strict-mode switch ------------------------------------------------

    // STRICT_PROPERTY is new public API in this release and nothing tested the
    // "off" side of it. The property is global mutable state, so it is restored in
    // a finally block - without that, a failure here silently changes the result of
    // every later test in the same JVM.
    @Test
    void strictModeThrowsAndTheDefaultDoesNot() throws Exception {
        String comprehension = "var x=[i*a for (i in a)];";
        String previous = System.getProperty(MungedCodeGenerator.STRICT_PROPERTY);
        try {
            System.clearProperty(MungedCodeGenerator.STRICT_PROPERTY);
            assertNotEquals("", js(comprehension), "the default path should still emit something");

            System.setProperty(MungedCodeGenerator.STRICT_PROPERTY, "true");
            assertThrows(Exception.class, () -> js(comprehension),
                    "strict mode should refuse an unhandled node type");
        } finally {
            if (previous == null) {
                System.clearProperty(MungedCodeGenerator.STRICT_PROPERTY);
            } else {
                System.setProperty(MungedCodeGenerator.STRICT_PROPERTY, previous);
            }
        }
    }

    // ---- idempotency -----------------------------------------------------------

    // compress(compress(x)) == compress(x) for CSS. It holds across every fixture
    // today, and it is the cheapest invariant that catches a pass which rewrites
    // its own output. Not applicable to JavaScript: munging reallocates names on a
    // second pass by design, so JS output is legitimately not idempotent.
    @Test
    void cssMinificationIsIdempotentAcrossEveryFixture() throws Exception {
        Path resources = Paths.get("src/test/resources");
        List<String> offenders = new ArrayList<>();
        int checked = 0;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(resources, "*.css")) {
            for (Path file : files) {
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                String once = css(source);
                String twice = css(once);
                checked++;
                if (!once.equals(twice)) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }

        assertTrue(checked > 0, "no CSS fixtures were found, so this test proved nothing");
        assertEquals(List.of(), offenders,
                "compressing an already-compressed stylesheet changed it again");
    }
}
