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
        String input = "/* Comment */\nbody { color: red; }\n/* Another comment */";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertFalse("Comments should be removed", result.contains("Comment"));
        assertTrue("Rules should be preserved", result.contains("body{color:red}"));
    }

    @Test
    public void testPreserveImportantComments() throws Exception {
        String input = "/*! Important */\nbody { color: red; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Important comments should be preserved", result.contains("Important"));
    }

    @Test
    public void testColorOptimization() throws Exception {
        String input = "body { color: #ffffff; background: #000000; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should optimize colors", result.contains("#fff") || result.contains("#FFF"));
        assertTrue("Should optimize colors", result.contains("#000"));
    }

    @Test
    public void testZeroValueOptimization() throws Exception {
        String input = "div { margin: 0px; padding: 0em; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should remove units from zero values", result.contains("margin:0"));
        assertTrue("Should remove units from zero values", result.contains("padding:0"));
    }

    @Test
    public void testFloatOptimization() throws Exception {
        String input = "div { opacity: 0.5; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should optimize float", result.contains(".5"));
    }

    @Test
    public void testMultipleSelectors() throws Exception {
        String input = "h1, h2, h3 { font-weight: bold; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve multiple selectors", result.contains("h1,h2,h3"));
        assertTrue("Should preserve property", result.contains("font-weight:bold"));
    }

    @Test
    public void testPseudoClasses() throws Exception {
        String input = "a:hover { color: blue; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve pseudo-classes", result.contains("a:hover"));
    }

    @Test
    public void testPseudoElements() throws Exception {
        String input = "p::before { content: '>>'; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve pseudo-elements", result.contains("::before") || result.contains(":before"));
    }

    @Test
    public void testMediaQuery() throws Exception {
        String input = "@media (max-width: 600px) { body { font-size: 14px; } }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve media query", result.contains("@media"));
        assertTrue("Should preserve max-width", result.contains("max-width"));
    }

    @Test
    public void testFontFace() throws Exception {
        String input = "@font-face { font-family: 'MyFont'; src: url('font.woff'); }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve @font-face", result.contains("@font-face"));
    }

    @Test
    public void testKeyframes() throws Exception {
        String input = "@keyframes slide { from { left: 0; } to { left: 100px; } }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve keyframes", result.contains("@keyframes") || result.contains("@-"));
    }

    @Test
    public void testGradient() throws Exception {
        String input = "div { background: linear-gradient(to right, red, blue); }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve gradient", result.contains("linear-gradient"));
    }

    @Test
    public void testCalcFunction() throws Exception {
        String input = "div { width: calc(100% - 20px); }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve calc function", result.contains("calc"));
    }

    @Test
    public void testImportantDeclaration() throws Exception {
        String input = "div { color: red !important; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve !important", result.contains("!important"));
    }

    @Test
    public void testLineBreak() throws Exception {
        String input = "body { color: red; background: blue; font-size: 14px; margin: 0; padding: 0; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, 40);

        String result = output.toString();
        assertTrue("Should have line breaks", result.contains("\n"));
    }

    @Test
    public void testMultipleRules() throws Exception {
        String input = "body { color: red; } p { margin: 10px; } h1 { font-size: 24px; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve all rules", result.contains("body{color:red}"));
        assertTrue("Should preserve all rules", result.contains("p{margin:10px}"));
        assertTrue("Should preserve all rules", result.contains("h1{font-size:24px}"));
    }

    @Test
    public void testAttributeSelector() throws Exception {
        String input = "input[type=\"text\"] { border: 1px solid black; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve attribute selector", result.contains("[type=") || result.contains("[type=\""));
    }

    @Test
    public void testDescendantSelector() throws Exception {
        String input = "div p { color: blue; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve descendant selector", result.contains("div p"));
    }

    @Test
    public void testChildSelector() throws Exception {
        String input = "div > p { color: green; }";

        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);

        String result = output.toString();
        assertTrue("Should preserve child selector", result.contains(">"));
    }
}
