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

    @Test
    public void testLinebreakPosition() throws Exception {
        // Test that linebreaks are inserted after specified position
        String input = "body { color: red; } div { margin: 0; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, 10); // Request linebreaks after 10 chars

        String result = output.toString();
        assertTrue("Should contain linebreak", result.contains("\n"));
    }

    @Test
    public void testLinebreakNotInsideString() throws Exception {
        // Test that linebreaks are NOT inserted inside strings containing '}'
        // This tests the fix for the TODO about moving linebreak insertion after token restoration
        String input = "body { content: \"test}value\"; color: red; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, 5); // Very small linebreak position to trigger insertion

        String result = output.toString();
        // The string "test}value" should remain intact
        assertTrue("String with } should be preserved intact", result.contains("\"test}value\""));
    }

    @Test
    public void testLinebreakWithEscapedQuotes() throws Exception {
        // Test strings with escaped quotes
        String input = "body { content: \"test\\\"}\"; } div { color: blue; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, 10);

        String result = output.toString();
        // The string with escaped quote should be preserved
        assertTrue("String with escaped quote should be preserved", result.contains("\"test\\\"}\""));
    }

    @Test
    public void testLinebreakWithSingleQuoteString() throws Exception {
        // Test single-quoted strings containing '}'
        String input = "body { content: 'test}value'; } div { color: blue; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, 5);

        String result = output.toString();
        // The single-quoted string should remain intact
        assertTrue("Single-quoted string with } should be preserved", result.contains("'test}value'"));
    }

    @Test
    public void testVariable() throws Exception {
        String input = "body { --test-value: red; \n color: var(--test-value);}";
        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);
        String result = output.toString();
        assertTrue("Should preserve variable", result.contains("body{--test-value:red;color:var(--test-value)}"));
    }

    @Test
    public void testVariableInCalc() throws Exception {
        String input = "body { --test-value: 10px; \n grid-template-columns: calc(50% - (0.5 * var(--test-value))) 1fr';}";
        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);
        String result = output.toString();
        assertEquals("Should preserve variable", "body{--test-value:10px;grid-template-columns:calc(50% - (0.5 * var(--test-value))) 1fr'}", result);
    }
}
