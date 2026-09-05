package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.yahoo.platform.yui.compressor.CssCompressor;

/**
 * The zero-value unit stripping ("0px" -&gt; "0") must not touch a "%" that its
 * colour function requires.
 *
 * <p>hsl(), hsla(), rgb(), rgba() and color-mix() take {@code <percentage>}
 * arguments in their comma-separated legacy form; a bare "0" there is not a value
 * any browser accepts, so the declaration is dropped and the colour silently
 * disappears from the page.
 */
class CssColorFunctionTest {

    private String compress(String source) throws Exception {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(source)).compress(out, -1);
        return out.toString().trim();
    }

    @Test
    void hslaKeepsZeroPercentSaturation() throws Exception {
        assertEquals("a{color:hsla(0,0%,100%,0.5)}", compress("a{color:hsla(0,0%,100%,0.5)}"));
    }

    @Test
    void hslKeepsZeroPercentSaturation() throws Exception {
        assertEquals("a{color:hsl(0,0%,100%)}", compress("a{color:hsl(0,0%,100%)}"));
    }

    @Test
    void hslaKeepsBothZeroPercentArguments() throws Exception {
        assertEquals("a{color:hsla(120,0%,0%,1)}", compress("a{color:hsla(120,0%,0%,1)}"));
    }

    @Test
    void rgbKeepsZeroPercentChannel() throws Exception {
        assertEquals("a{color:rgb(0%,50%,0%)}", compress("a{color:rgb(0%,50%,0%)}"));
    }

    @Test
    void rgbaKeepsZeroPercentChannel() throws Exception {
        assertEquals("a{color:rgba(0%,50%,50%,0.4)}", compress("a{color:rgba(0%,50%,50%,0.4)}"));
    }

    @Test
    void colorMixKeepsZeroPercent() throws Exception {
        assertEquals("a{color:color-mix(in srgb,red 0%,blue)}",
                compress("a{color:color-mix(in srgb, red 0%, blue)}"));
    }

    /**
     * A nested colour function keeps its percentages. The stop that follows it
     * keeps its own "%" too - the argument-prefix part of the pattern cannot
     * step over the nested parentheses to reach it - which is a missed byte,
     * not a correctness problem.
     */
    @Test
    void nestedColorFunctionKeepsItsPercentages() throws Exception {
        assertEquals("a{background:linear-gradient(hsla(0,0%,50%,1) 0%,red 100%)}",
                compress("a{background:linear-gradient(hsla(0,0%,50%,1) 0%, red 100%)}"));
    }

    @Test
    void gradientColorStopStillLosesItsPercent() throws Exception {
        assertEquals("a{background:linear-gradient(red 0,blue 100%)}",
                compress("a{background:linear-gradient(red 0%, blue 100%)}"));
    }

    @Test
    void minKeepsZeroPercentSoItsArgumentsStayTypeCompatible() throws Exception {
        assertEquals("a{width:min(0%,10px)}", compress("a{width:min(0%, 10px)}"));
    }

    @Test
    void maxKeepsZeroPercent() throws Exception {
        assertEquals("a{width:max(0%,10px)}", compress("a{width:max(0%, 10px)}"));
    }

    @Test
    void clampKeepsZeroPercent() throws Exception {
        assertEquals("a{width:clamp(0%,50%,100%)}", compress("a{width:clamp(0%, 50%, 100%)}"));
    }

    /** "minmax" must not be caught by the "min" entry: a zero there may lose its unit. */
    @Test
    void nonColorFunctionStillLosesItsPercent() throws Exception {
        assertEquals("a{grid-template-columns:minmax(0,1fr)}",
                compress("a{grid-template-columns:minmax(0%,1fr)}"));
    }

    @Test
    void lengthUnitsInsideGroupsAreStillStripped() throws Exception {
        assertEquals("a{transform:translate(0,10px)}", compress("a{transform:translate(0px,10px)}"));
    }

    // ------------------------------------------------------------------
    // A run of zeroes may only collapse to one where the property is a
    // box-model shorthand.
    // ------------------------------------------------------------------

    @Test
    void boxShadowKeepsBothOffsets() throws Exception {
        assertEquals("a{box-shadow:0 0}", compress("a{box-shadow:0 0}"));
    }

    @Test
    void boxShadowWithSpreadIsNotCollapsed() throws Exception {
        assertEquals("a{box-shadow:0 0 0 0}", compress("a{box-shadow:0 0 0 0}"));
    }

    @Test
    void vendorPrefixedBoxShadowIsNotCollapsed() throws Exception {
        assertEquals("a{-webkit-box-shadow:0 0 0 0}", compress("a{-webkit-box-shadow:0 0 0 0}"));
    }

    @Test
    void textShadowKeepsBothOffsets() throws Exception {
        assertEquals("a{text-shadow:0 0 0}", compress("a{text-shadow:0 0 0}"));
    }

    /** "perspective-origin:0" means "0 center", which is not "0 0". */
    @Test
    void perspectiveOriginKeepsBothAxes() throws Exception {
        assertEquals("a{perspective-origin:0 0}", compress("a{perspective-origin:0 0}"));
    }

    @Test
    void flexIsStillNotCollapsed() throws Exception {
        assertEquals("a{flex:0 0}", compress("a{flex:0 0}"));
    }

    @Test
    void boxModelShorthandsStillCollapse() throws Exception {
        assertEquals("a{margin:0}", compress("a{margin:0 0 0 0}"));
        assertEquals("a{padding:0}", compress("a{padding:0 0}"));
        assertEquals("a{border-radius:0}", compress("a{border-radius:0 0}"));
        assertEquals("a{gap:0}", compress("a{gap:0 0}"));
    }

    @Test
    void backgroundPositionIsStillNormalisedToTwoAxes() throws Exception {
        assertEquals("a{background-position:0 0}", compress("a{background-position:0 0}"));
        assertEquals("a{transform-origin:0 0}", compress("a{transform-origin:0 0}"));
    }
}
