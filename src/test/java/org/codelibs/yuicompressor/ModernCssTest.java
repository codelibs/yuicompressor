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
}
