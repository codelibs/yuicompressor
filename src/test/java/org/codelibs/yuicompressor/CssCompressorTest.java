package org.codelibs.yuicompressor;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import com.yahoo.platform.yui.compressor.CssCompressor;

/**
 * Test cases for CSS compression
 */
public class CssCompressorTest {

    private StringWriter output;

    @Before
    public void setUp() {
        output = new StringWriter();
    }

    @Test
    public void testBasicCompression() throws Exception {
        String input = "body { color: red; }";
        String expected = "body{color:red}";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        assertEquals(expected, output.toString());
    }

    @Test
    public void testRemoveWhitespace() throws Exception {
        String input = "body    {    color  :   red  ;   }";
        String expected = "body{color:red}";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        assertEquals(expected, output.toString());
    }

    @Test
    public void testCommentRemoval() throws Exception {
        String input = "/* Comment */\nbody { color: red; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertFalse("Comments should be removed", result.contains("Comment"));
        assertTrue("Rules should be preserved", result.contains("body{color:red}"));
    }

    @Test
    public void testMultipleProperties() throws Exception {
        String input = "div { margin: 10px; padding: 5px; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should contain margin", result.contains("margin"));
        assertTrue("Should contain padding", result.contains("padding"));
    }

    @Test
    public void testMultipleSelectors() throws Exception {
        String input = "h1, h2, h3 { font-weight: bold; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve selectors", result.contains("h1") && result.contains("h2") && result.contains("h3"));
    }

    @Test
    public void testPseudoClass() throws Exception {
        String input = "a:hover { color: blue; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve pseudo-class", result.contains(":hover"));
    }

    @Test
    public void testMediaQuery() throws Exception {
        String input = "@media screen { body { color: black; } }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve media query", result.contains("@media"));
    }
}
