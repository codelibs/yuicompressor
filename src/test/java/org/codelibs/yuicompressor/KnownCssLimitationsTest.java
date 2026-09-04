package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.yahoo.platform.yui.compressor.CssCompressor;

/**
 * Pre-existing {@link CssCompressor} limitations, pinned in the style this
 * release already uses for its deferred {@code calc(} and escaped-quote
 * defects: <b>these tests assert WRONG output, and say so.</b>
 *
 * <p>Every case here is byte-identical at the release base 070bdd7 - verified
 * by compiling {@code main}'s {@code CssCompressor} on its own and running the
 * same inputs through it - so none is a regression from this PR. They are
 * recorded rather than accepted: a test that fails when one is fixed is how the
 * fix gets noticed and this file gets updated.
 */
class KnownCssLimitationsTest {

    private static String css(String source) throws IOException {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(source)).compress(out, -1);
        return out.toString();
    }

    // ------------------------------------------------------------------
    // A. Zero-percentage stripping produces invalid CSS.
    //
    // CssCompressor.java (identical on main):
    //   Pattern.compile("(?i)\\( ?((?:[0-9a-z-.]+[ ,])*)?(?:0?\\.)?0(?:px|em|%|in|cm|mm|pc|pt|ex|deg|g?rad|m?s|k?hz)")
    //
    // The rule is "a zero <length> may drop its unit", which is true, and it is
    // applied to "%" as well - but a <percentage> is a distinct type, not a
    // length, and several grammars accept ONLY a percentage in the position
    // this rewrites. The rule fires on anything inside a "(", so it reaches
    // colour functions and math functions it was never meant for.
    // ------------------------------------------------------------------

    /**
     * {@code color-mix()} takes {@code <percentage [0,100]>}. A bare {@code 0}
     * is not a percentage, so the whole declaration is dropped by a browser -
     * the element loses its colour entirely. The cleanest case of the family:
     * no legacy/modern grammar ambiguity to argue about.
     */
    @Test
    void colorMixPercentageLosesItsUnit_knownDefect() {
        assertDoesNotRoundTrip("a{color:color-mix(in srgb, red 0%, blue)}",
                "a{color:color-mix(in srgb,red 0,blue)}");
    }

    /**
     * Legacy comma-separated {@code rgb()} is
     * {@code rgb(<percentage>#{3})} or {@code rgb(<number>#{3})} - the two may
     * not be mixed. Stripping one unit produces exactly that mixture.
     */
    @Test
    void rgbPercentageChannelLosesItsUnitAndMixesTypes_knownDefect() {
        assertDoesNotRoundTrip("a{color:rgb(0%,50%,100%)}", "a{color:rgb(0,50%,100%)}");
    }

    /**
     * Legacy comma-separated {@code hsl()}/{@code hsla()} require
     * {@code <percentage>} for saturation and lightness.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a{color:hsl(27,0%,50%)}|a{color:hsl(27,0,50%)}",
            "a{color:hsl(27, 0%, 50%)}|a{color:hsl(27,0,50%)}",
            "a{color:hsla(27,0%,50%,0.5)}|a{color:hsla(27,0,50%,0.5)}" })
    void hslSaturationLosesItsUnit_knownDefect(String source, String wrongOutput) throws Exception {
        assertEquals(wrongOutput, css(source), "if this now keeps the unit, the defect is fixed - delete this row");
    }

    /**
     * CSS math functions are type-checked: {@code min()} may not compare a
     * {@code <number>} with a {@code <length>}. Stripping the "%" turns a legal
     * comparison into an invalid one.
     */
    @Test
    void aMathFunctionArgumentLosesItsPercentageAndBreaksTypeChecking_knownDefect() {
        assertDoesNotRoundTrip("a{width:min(0%, 10px)}", "a{width:min(0,10px)}");
    }

    /**
     * The same rule strips {@code <time>} units, but only inside a
     * parenthesised group. {@link ModernCssTest#zeroTimeValuesKeepTheirUnit}
     * pins the outside-a-group case as correct - "a zero {@code <time>} must
     * keep its unit" - and it is, on both main and HEAD. The group path never
     * got the same treatment, so the invariant that class states holds in one
     * position and not the other.
     *
     * <p>Recorded here because the two behaviours contradict each other and a
     * reader of {@code zeroTimeValuesKeepTheirUnit} would reasonably assume
     * otherwise.
     */
    @Test
    void aZeroTimeInsideAGroupStillLosesItsUnit_knownDefect() throws Exception {
        assertEquals("a{animation-timing-function:steps(4,0)}",
                css("a{animation-timing-function:steps(4, 0s)}"),
                "inside a group the <time> unit is stripped, contradicting zeroTimeValuesKeepTheirUnit");

        // The control: outside a group the unit survives, on both main and HEAD.
        assertEquals("a{transition-duration:0s}", css("a{transition-duration:0s}"));
        assertEquals("a{transition:opacity 0s}", css("a{transition:opacity 0s}"));
    }

    /**
     * The cases the rule gets RIGHT, kept so a fix cannot be made by simply
     * disabling it. A zero {@code <length>} may drop its unit, and
     * {@code opacity} accepts a number as readily as a percentage. {@code <zero>}
     * (CSS Values 4) is why {@code rotate(0)} is legal for an {@code <angle>}.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a{width:0%}|a{width:0}",
            "a{margin:0px}|a{margin:0}",
            "a{opacity:0%}|a{opacity:0}",
            "a{transform:rotate(0deg)}|a{transform:rotate(0)}",
            "a{grid-template-columns:minmax(0%,1fr)}|a{grid-template-columns:minmax(0,1fr)}" })
    void zeroUnitStrippingIsStillCorrectWhereTheTypeAllowsIt(String source, String expected) throws Exception {
        assertEquals(expected, css(source));
    }

    private static void assertDoesNotRoundTrip(String source, String wrongOutput) {
        try {
            assertEquals(wrongOutput, css(source),
                    "if this now preserves the percentage, the defect is fixed - delete this test");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------
    // B. preserveToken crashes with StringIndexOutOfBoundsException.
    //
    // CssCompressor.preserveToken, identical on main:
    //
    //   } else if ((endIndex > 0) && (css.charAt(endIndex-1) != '\\')) {
    //       foundTerminator = true;
    //       if (!")".equals(terminator)) {
    //           endIndex = css.indexOf(")", endIndex);   // may be -1
    //       }
    //   }
    //   ...
    //   if (foundTerminator) {
    //       String token = css.substring(startIndex, endIndex);   // -1 -> throw
    //
    // foundTerminator is set BEFORE the second search, so the -1 is never
    // re-checked. Trigger: one of the three preserved constructs opens with a
    // quote whose closing quote has no ")" after it anywhere in the rest of
    // the stylesheet.
    // ------------------------------------------------------------------

    /**
     * <b>The important one: this input is VALID CSS.</b> {@code content} holds
     * an ordinary double-quoted string that happens to contain the text
     * {@code calc('x'}. The {@code calc(} regex matches inside the string, takes
     * the {@code '} as its terminator, finds no {@code ")"} after the closing
     * quote, and indexes with -1.
     *
     * <p>So this is not only a malformed-input concern: a legal stylesheet
     * aborts the compressor with an exception that names nothing useful.
     */
    @Test
    void aValidStringContainingCalcAndAQuoteCrashesTheCompiler_knownDefect() {
        StringIndexOutOfBoundsException failure = assertThrows(StringIndexOutOfBoundsException.class,
                () -> css("a{content:\"calc('x'\"}"));
        assertTrue(failure.getMessage().contains("-1"),
                "the -1 comes from indexOf(\")\") after foundTerminator was already set: " + failure.getMessage());
    }

    /** All three {@code preserveToken} call sites share the defect. */
    @ParameterizedTest
    @ValueSource(strings = {
            "a{width:calc('x' }",
            "a{width:calc(\"x\" }",
            "a{width:calc('x'}",
            "a{background:url('data:x' }",
            "a{background:url(\"data:x\" }",
            "a{filter:progid:DXImageTransform.Microsoft.Matrix 'x' }" })
    void anUnclosedPreservedTokenCrashesRatherThanReporting_knownDefect(String source) {
        assertThrows(StringIndexOutOfBoundsException.class, () -> css(source),
                "if this now reports an error or compresses, the defect is fixed: " + source);
    }

    /** Controls: the same constructs closed properly must keep working. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a{width:calc('x')}|a{width:calc('x')}",
            "a{width:calc('a')}b{width:calc('b')}|a{width:calc('a')}b{width:calc('b')}",
            "a{background:url('data:image/png,AAA')}|a{background:url('data:image/png,AAA')}" })
    void aProperlyClosedPreservedTokenIsUnaffected(String source, String expected) throws Exception {
        assertEquals(expected, css(source));
    }

    // ------------------------------------------------------------------
    // C. The declared exception contract.
    // ------------------------------------------------------------------

    /**
     * {@code CssCompressor.compress(Writer, int)} is declared
     * {@code throws IOException} and carries no {@code @throws} javadoc for
     * anything else, yet it throws two different UNCHECKED exceptions:
     * {@link IllegalArgumentException} for an unterminated comment (added by
     * this release, deliberately, and a real improvement - main emitted
     * internal scaffolding with exit 0 instead) and
     * {@link StringIndexOutOfBoundsException} from section B above.
     *
     * <p>The JavaScript side wraps its equivalent failure in an
     * {@code IOException} ({@code "Error compressing JavaScript: ..."}), so a
     * caller that handles {@code IOException} - which is the only thing either
     * signature promises - is protected on one side and not the other. That
     * asymmetry is what this test pins; whether the fix is to wrap, to declare,
     * or to document is a decision, but it should be a decision.
     */
    @Test
    void cssThrowsUncheckedExceptionsFromAMethodThatOnlyDeclaresIOException() {
        assertThrows(IllegalArgumentException.class, () -> css("a{color:red} /* oops"),
                "unterminated comment: deliberate, but unchecked and undeclared");
        assertThrows(StringIndexOutOfBoundsException.class, () -> css("a{width:calc('x' }"),
                "unclosed preserved token: not deliberate, and unchecked");
    }

    /**
     * The JavaScript side, for contrast: its generation failure IS an
     * {@code IOException}, which its signature declares.
     */
    @Test
    void javascriptWrapsItsEquivalentFailureInIOException() {
        System.setProperty(com.yahoo.platform.yui.compressor.MungedCodeGenerator.STRICT_PROPERTY, "true");
        try {
            assertThrows(IOException.class, () -> {
                StringWriter out = new StringWriter();
                new com.yahoo.platform.yui.compressor.JavaScriptCompressor(
                        new StringReader("var a = [i*2 for (i in x)];"), null)
                                .compress(out, -1, true, false, false, false);
            });
        } finally {
            System.clearProperty(com.yahoo.platform.yui.compressor.MungedCodeGenerator.STRICT_PROPERTY);
        }
    }

    /**
     * Malformed input that does NOT crash, so the boundary between "degrades
     * quietly" and "throws" is written down rather than discovered.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a{background:url(|a{background:url(",
            "a{content:\"oops|a{content:\"oops",
            "a{|a{",
            "}}}|}}}" })
    void otherMalformedInputDegradesQuietlyRatherThanThrowing(String source, String expected) throws Exception {
        assertEquals(expected, css(source));
    }
}
