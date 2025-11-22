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
        String expected = "var x=1;var y=2;var z=x+y;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        assertEquals(expected, output.toString());
    }

    @Test
    public void testVariableObfuscation() throws Exception {
        String input = "function test() { var longVariableName = 123; return longVariableName; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, false, false, false, false);

        String result = output.toString();
        assertFalse("Variable should be obfuscated", result.contains("longVariableName"));
        assertTrue("Function should remain", result.contains("function test()"));
    }

    @Test
    public void testNoMunge() throws Exception {
        String input = "function test() { var myVar = 123; return myVar; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Variable should not be obfuscated with nomunge", result.contains("myVar"));
    }

    @Test
    public void testLineBreak() throws Exception {
        String input = "var x = 1; var y = 2; var z = 3; var a = 4; var b = 5;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, 20, true, false, false, false);

        String result = output.toString();
        assertTrue("Should have line breaks", result.contains("\n"));
    }

    @Test
    public void testPreserveSemicolons() throws Exception {
        String input = "var x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, true, false);

        String result = output.toString();
        assertTrue("Should preserve semicolon", result.endsWith(";"));
    }

    @Test
    public void testCommentRemoval() throws Exception {
        String input = "// This is a comment\nvar x = 1;\n/* Block comment */\nvar y = 2;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse("Comments should be removed", result.contains("This is a comment"));
        assertFalse("Block comments should be removed", result.contains("Block comment"));
    }

    @Test
    public void testPreserveImportantComments() throws Exception {
        String input = "/*! Important comment */\nvar x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Important comments should be preserved", result.contains("Important comment"));
    }

    @Test
    public void testFunctionExpression() throws Exception {
        String input = "var fn = function() { return 42; };";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should compress function expression", result.contains("function()"));
        assertTrue("Should preserve return statement", result.contains("return"));
    }

    @Test
    public void testObjectLiteral() throws Exception {
        String input = "var obj = { foo: 1, bar: 2 };";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should preserve object structure", result.contains("{foo:1,bar:2}") ||
                                                       result.contains("{foo:1, bar:2}"));
    }

    @Test
    public void testArrayLiteral() throws Exception {
        String input = "var arr = [1, 2, 3, 4, 5];";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should preserve array", result.contains("[1,2,3,4,5]"));
    }

    @Test
    public void testStringLiterals() throws Exception {
        String input = "var s1 = 'single'; var s2 = \"double\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should preserve string literals",
                   result.contains("single") && result.contains("double"));
    }

    @Test
    public void testRegularExpression() throws Exception {
        String input = "var re = /test/gi;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should preserve regex", result.contains("/test/gi"));
    }

    @Test
    public void testConditionalStatement() throws Exception {
        String input = "if (x > 0) { y = 1; } else { y = 0; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should preserve if/else structure",
                   result.contains("if") && result.contains("else"));
    }

    @Test
    public void testWhileLoop() throws Exception {
        String input = "while (i < 10) { i++; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should preserve while loop", result.contains("while"));
    }

    @Test
    public void testForLoop() throws Exception {
        String input = "for (var i = 0; i < 10; i++) { sum += i; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should preserve for loop", result.contains("for"));
    }

    @Test
    public void testTryCatch() throws Exception {
        String input = "try { doSomething(); } catch (e) { handleError(e); }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should preserve try/catch",
                   result.contains("try") && result.contains("catch"));
    }
}
