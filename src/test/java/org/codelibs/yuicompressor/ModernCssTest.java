package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.yahoo.platform.yui.compressor.CssCompressor;

/** Regression tests for modern CSS syntax. */
class ModernCssTest {

    private String compress(String source) throws Exception {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(source)).compress(out, -1);
        return out.toString().trim();
    }

    @Test
    void containerNameKeepsSpaceBeforeCondition() throws Exception {
        String result = compress("@container card (min-width: 400px) { .t { color: red } }");
        assertTrue(result.contains("card ("),
                "an identifier followed directly by '(' becomes a function token: " + result);
    }

    @Test
    void supportsNotKeepsSpaceBeforeCondition() throws Exception {
        String result = compress("@supports (display: grid) and (not (display: inline-grid)) { .g { color: red } }");
        assertTrue(result.contains("not ("),
                "'not(' would be parsed as a function token: " + result);
    }

    @Test
    void scopeToKeepsSpaceBeforeCondition() throws Exception {
        String result = compress("@scope (.a) to (.b) { .c { color: red } }");
        assertTrue(result.contains("to ("),
                "'to(' would be parsed as a function token: " + result);
    }

    @Test
    void mediaQueryKeywordIsLowercased() throws Exception {
        String result = compress("@media screen AND (min-width: 400px) { .m { color: red } }");
        assertEquals("@media screen and (min-width:400px){.m{color:red}}", result);
    }

    @Test
    void customPropertyColorIsNotRewritten() throws Exception {
        assertEquals(":root{--main-color:#ff0000}", compress(":root { --main-color: #ff0000; }"));
    }

    @Test
    void customPropertyInnerWhitespaceIsPreserved() throws Exception {
        String result = compress(":root { --txt: hello  world; }");
        assertTrue(result.contains("hello  world"),
                "a custom property value is a token stream and must be kept verbatim: " + result);
    }

    @Test
    void propertyAtRuleInitialValueIsNotRewritten() throws Exception {
        String result = compress("@property --c { syntax: '<color>'; inherits: false; initial-value: #ff0000; }");
        assertTrue(result.contains("#ff0000"),
                "an @property initial-value must be kept verbatim: " + result);
    }

    @Test
    void ordinaryColorIsStillOptimised() throws Exception {
        assertEquals("a{color:red}", compress("a { color: #ff0000; }"));
    }

    @Test
    void customPropertyKeywordCaseIsNotFolded() throws Exception {
        assertEquals(":root{--my-op:NOT(x)}", compress(":root { --my-op: NOT(x); }"));
    }

    @Test
    void commentMentioningPropertyDoesNotSwallowFollowingRule() throws Exception {
        String result = compress("/* TODO use @property here */\na { color: #ff0000; }");
        assertEquals("a{color:red}", result);
        assertTrue(result.indexOf("___YUICSSMIN") < 0,
                "no internal placeholder should ever leak into the output: " + result);
    }

    @Test
    void propertyAtRuleDescriptorStringContainingBraceDoesNotMisleadBlockScan() throws Exception {
        String result = compress("@property --c { syntax: \"}\"; inherits: false; initial-value: #ff0000; }");
        assertTrue(result.contains("#ff0000"),
                "a '}' inside a quoted descriptor value must not be read as the block's closing brace: " + result);

        String result2 = compress("@property --c { syntax: '{'; inherits: false; initial-value: #ff0000; }");
        assertTrue(result2.contains("#ff0000"),
                "a '{' inside a quoted descriptor value must not be read as a nested block: " + result2);
    }

    @Test
    void emptyLayerDeclarationIsKept() throws Exception {
        String result = compress("@layer utilities {} .a { color: red }");
        assertTrue(result.contains("@layer utilities"),
                "an empty @layer still declares layer order and must be kept: " + result);
    }

    @Test
    void emptyPlainRuleIsStillRemoved() throws Exception {
        assertEquals("a{color:red}", compress(".empty {} a { color: red }"));
    }

    @Test
    void modernAtRuleNameIsLowercased() throws Exception {
        String result = compress("@LAYER base { a { color: red } }");
        assertTrue(result.startsWith("@layer"), "the at-rule name should be lowercased: " + result);
    }

    @Test
    void emptyMediaBlockIsStillRemoved() throws Exception {
        assertEquals("a{color:red}", compress("@media screen {} a { color: red }"));
    }

    @Test
    void compressedOutputNeverLeaksInternalPlaceholder() throws Exception {
        String[] inputs = {
                "a { content: 'a string value' }",
                "a { background: url(data:image/png;base64,iVBORw0KGgo=) }",
                "a { width: calc(100% - 10px) }",
                "/*! preserved comment */\na { color: red }",
                "/* ordinary comment */\na { color: red }",
                ":root { --main-color: #ff0000; }",
                "@property --c { syntax: '<color>'; inherits: false; initial-value: #ff0000; }",
        };
        for (String input : inputs) {
            String result = compress(input);
            assertTrue(result.indexOf("___YUICSSMIN") < 0,
                    "no internal placeholder should ever leak into the output for input [" + input + "]: " + result);
        }
    }

    @Test
    void zeroTimeValuesKeepTheirUnit() throws Exception {
        // CSS Values and Units Level 3 allows omitting the unit only for zero
        // <length>. <time> has no such exemption, so "0s" must not become "0".
        // zeros.css.min expects otherwise; that golden encodes invalid CSS and is
        // deliberately left quarantined rather than matched.
        String result = compress("a { transition-duration: 0s; transition-delay: 0ms; }");
        assertTrue(result.contains("0s"), "a zero <time> must keep its unit: " + result);
        assertTrue(result.contains("0ms"), "a zero <time> must keep its unit: " + result);
    }

    @Test
    void zeroLengthValuesStillDropTheirUnit() throws Exception {
        // The <length> exemption does apply, so this optimisation must be kept.
        assertEquals("a{width:0;height:0}", compress("a { width: 0%; height: 0px; }"));
    }

    // The at-rule and declaration matchers below all used to fire on their
    // literal text wherever it appeared, with no regard for whether an at-rule
    // or declaration could actually start there. Requiring a real boundary
    // ("{", "}", ";", the start of the stylesheet, or a preserved-token
    // placeholder) closes the whole class; each test names one instance.

    @Test
    void atPropertyTextInsideAUrlDoesNotDisableTheFollowingRule() throws Exception {
        // "@property" was located context-free, so this match preserved
        // everything up to the next balanced "}" verbatim - which meant the
        // rule AFTER it was emitted completely unminified.
        String result = compress("a { background: url(/img/@property.png) } b { color: #ff0000; margin: 0px }");
        assertEquals("a{background:url(/img/@property.png)}b{color:red;margin:0}", result, result);
    }

    @Test
    void atDirectiveTextInsideAUrlKeepsItsCase() throws Exception {
        // The at-directive lowercasing pass was context-free too. URL paths are
        // case-sensitive on essentially every server, so rewriting one is a
        // broken stylesheet, not a smaller one.
        String result = compress("a { background: url(/img/@MEDIA.png) } B { COLOR: #ff0000 }");
        assertEquals("a{background:url(/img/@MEDIA.png)}B{COLOR:red}", result, result);
    }

    @Test
    void atDirectiveAfterAPreservedCommentIsStillLowercased() throws Exception {
        // The boundary check must not lose the legitimate case: after comment
        // preservation an at-rule can be preceded by a placeholder rather than
        // by "{", "}" or ";".
        String result = compress("/*! keep */@MEDIA screen{b{color:#00ff00}}");
        assertEquals("/*! keep */@media screen{b{color:#0f0}}", result, result);
    }

    @Test
    void atCharsetTextInsideAUrlIsNotHoistedToTheTop() throws Exception {
        String result = compress("a { background: url(/x/@charset \"y\";) } b{color:#ff0000}");
        assertEquals("a{background:url(/x/@charset \"y\";)}b{color:red}", result, result);
    }

    @Test
    void aRealAtCharsetIsStillHoistedAndLowercased() throws Exception {
        assertEquals("@charset \"utf-8\";a{color:red}", compress("@charset \"utf-8\"; a { color: #ff0000 }"));
    }

    @Test
    void customPropertyValueIsPreservedAfterAPreservedComment() throws Exception {
        // The declaration matcher accepted only "{" or ";" as the preceding
        // character, so a preserved "/*!" banner between the "{" and the
        // declaration made the value ordinary again and the colour optimiser
        // rewrote it.
        String result = compress(":root{/*! v1 */--brand:#ff0000}");
        assertEquals(":root{/*! v1 */--brand:#ff0000}", result, result);
    }

    @Test
    void customPropertyUnitIsPreservedAfterAPreservedComment() throws Exception {
        // The damaging half of the same defect: calc(var(--pad) + 1px) needs
        // the unit, and "0" is not a length there.
        String result = compress(":root{/*! x */--pad:0px}");
        assertEquals(":root{/*! x */--pad:0px}", result, result);
    }

    @Test
    void aDoubleDashInsideAValueIsStillNotTreatedAsACustomProperty() throws Exception {
        // The boundary check must keep excluding this: "--2px" here is a value,
        // not a declaration.
        assertEquals("a{width:calc(1px - -2px)}", compress("a{width:calc(1px --2px)}"));
    }
}
