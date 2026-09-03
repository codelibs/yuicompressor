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
}
