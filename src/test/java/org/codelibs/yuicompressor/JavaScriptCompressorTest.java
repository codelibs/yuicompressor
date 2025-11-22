package org.codelibs.yuicompressor;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Test cases for JavaScript compression
 */
public class JavaScriptCompressorTest {

    private StringWriter output;

    @Before
    public void setUp() {
        output = new StringWriter();
    }

    @Test
    public void testBasicCompression() throws Exception {
        String input = "var x = 1;\nvar y = 2;\nvar z = x + y;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should remove whitespace", result.length() < input.length());
        assertTrue("Should contain var", result.contains("var"));
    }

    @Test
    public void testVariableObfuscation() throws Exception {
        String input = "function test() { var longVariableName = 123; return longVariableName; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse("Variable should be obfuscated", result.contains("longVariableName"));
        assertTrue("Function should remain", result.contains("function test()"));
    }

    @Test
    public void testNoMunge() throws Exception {
        String input = "function test() { var myVar = 123; return myVar; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, false, false, false, false);

        String result = output.toString();
        assertTrue("Variable should not be obfuscated with nomunge", result.contains("myVar"));
    }

    @Test
    public void testCommentRemoval() throws Exception {
        String input = "// This is a comment\nvar x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse("Comments should be removed", result.contains("This is a comment"));
        assertTrue("Code should remain", result.contains("var"));
    }

    @Test
    public void testFunctionExpression() throws Exception {
        String input = "var fn = function() { return 42; };";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should contain function", result.contains("function"));
        assertTrue("Should contain return", result.contains("return"));
    }
}
