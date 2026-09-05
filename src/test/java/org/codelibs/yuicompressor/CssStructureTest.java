package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.yahoo.platform.yui.compressor.CssCompressor;

/**
 * Compressions that changed which elements a rule selects, dropped a declaration,
 * or aborted the build outright.
 */
class CssStructureTest {

    private String compress(String source) throws Exception {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(source)).compress(out, -1);
        return out.toString().trim();
    }

    // ------------------------------------------------------------------
    // The space before a pseudo-class is a descendant combinator.
    // "p :link" and "p:link" select disjoint sets of elements.
    // ------------------------------------------------------------------

    @Test
    void theFirstRuleInsideAnAtRuleKeepsItsDescendantCombinator() throws Exception {
        assertEquals("@media screen{p :link{color:red}}",
                compress("@media screen{p :link{color:red}}"));
        assertEquals("@supports (display:grid){div :hover{color:red}}",
                compress("@supports (display: grid){div :hover{color:red}}"));
        assertEquals("@layer base{ul li :first-child{color:red}}",
                compress("@layer base{ul li :first-child{color:red}}"));
        assertEquals("@container card (min-width:400px){.t :focus{color:red}}",
                compress("@container card (min-width: 400px){.t :focus{color:red}}"));
    }

    /** With CSS nesting every nested rule is the first thing after a "{" or a ";". */
    @Test
    void aNestedRuleKeepsItsDescendantCombinator() throws Exception {
        assertEquals(".a{& :hover{color:red}}", compress(".a{& :hover{color:red}}"));
        assertEquals(".a{color:red;& :hover{color:blue}}",
                compress(".a{color:red;& :hover{color:blue}}"));
    }

    @Test
    void aSelectorListKeepsItsDescendantCombinator() throws Exception {
        assertEquals("@media screen{a,p :link{color:red}}",
                compress("@media screen{a,p :link{color:red}}"));
    }

    @Test
    void aRuleAfterASiblingStillKeepsIt() throws Exception {
        assertEquals("@media screen{*{margin:0}p :link{color:red}}",
                compress("@media screen{*{margin:0}p :link{color:red}}"));
        assertEquals("p :link{color:red}", compress("p :link{color:red}"));
    }

    /** A declaration's own colon must stay unprotected, or its space survives. */
    @Test
    void aDeclarationColonIsStillTightened() throws Exception {
        assertEquals("a{color:red}", compress("a{color : red}"));
        assertEquals("a{color:red}b{color:blue}", compress("a{color : red}b{color : blue}"));
        assertEquals("a{color:red;background:blue}", compress("a{color : red;background : blue}"));
    }

    @Test
    void aPseudoClassWithoutASpaceIsUnchanged() throws Exception {
        assertEquals("a:hover{color:red}", compress("a:hover{color:red}"));
    }

    // ------------------------------------------------------------------
    // rgb() shortening must not abort the build on CSS Color 4 syntax.
    // ------------------------------------------------------------------

    /**
     * "rgb(0 0 0)" used to reach Integer.parseInt as the single token "0 0 0",
     * throwing NumberFormatException out of compress() - which its signature does
     * not declare - and failing the whole build on input every browser accepts.
     */
    @Test
    void spaceSeparatedRgbDoesNotAbortTheBuild() throws Exception {
        assertEquals("a{color:rgb(0 0 0)}", compress("a{color:rgb(0 0 0)}"));
        assertEquals("a{color:rgb(255 0 0)}", compress("a{color:rgb( 255 0 0 )}"));
        assertEquals("a{color:rgb(0 0 0 / 50%)}", compress("a{color:rgb(0 0 0 / 50%)}"));
    }

    @Test
    void commaSeparatedRgbIsStillShortened() throws Exception {
        assertEquals("a{color:#369}", compress("a{color:rgb(51,102,153)}"));
        assertEquals("a{color:#369}", compress("a{color:rgb( 51 , 102 , 153 )}"));
        assertEquals("a{color:#fff}", compress("a{color:rgb(1000,500,300)}"));
    }

    // ------------------------------------------------------------------
    // <calc-sum> needs whitespace around "+" and "-" in every math function,
    // not only in calc().
    // ------------------------------------------------------------------

    @Test
    void mathFunctionsKeepTheirOperatorSpacing() throws Exception {
        assertEquals("a{width:min(10px + 5px,5px)}", compress("a{width:min(10px + 5px, 5px)}"));
        assertEquals("a{width:max(10px + 5px,5px)}", compress("a{width:max(10px + 5px, 5px)}"));
        assertEquals("a{width:clamp(1px,10% + 5px,5px)}", compress("a{width:clamp(1px, 10% + 5px, 5px)}"));
        assertEquals("a{width:round(10px + 5px,5px)}", compress("a{width:round(10px + 5px, 5px)}"));
    }

    @Test
    void calcIsUnchanged() throws Exception {
        assertEquals("a{width:calc(10px + 5px)}", compress("a{width:calc(10px + 5px)}"));
        assertEquals("a{width:-webkit-calc(10px + 5px)}", compress("a{width:-webkit-calc(10px + 5px)}"));
        assertEquals("a{width:calc(var(--a-b) + 1px)}", compress("a{width:calc(var(--a-b) + 1px)}"));
    }

    /** "minmax" must not be taken for "max": its arguments are not a calc-sum. */
    @Test
    void minmaxIsNotTreatedAsAMathFunction() throws Exception {
        assertEquals("a{grid-template-columns:minmax(0,1fr)}",
                compress("a{grid-template-columns:minmax(0, 1fr)}"));
    }

    @Test
    void aNegativeLengthOutsideAMathFunctionIsUnchanged() throws Exception {
        assertEquals("a{margin:0 -5px}", compress("a{margin: 0 -5px}"));
    }
}
