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

    @Test
    public void testKeepCommentPreservation() throws Exception {
        String input = "/*! Important license comment */\nvar x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Keep comment should be preserved", result.contains("Important license comment"));
        assertTrue("Code should remain", result.contains("var"));
    }

    @Test
    public void testConditionalCommentPreservation() throws Exception {
        String input = "/*@cc_on var ie = true; @*/\nvar x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Conditional comment should be preserved", result.contains("@cc_on"));
    }

    @Test
    public void testMultipleFunctionsWithMunging() throws Exception {
        String input = "function foo() { var x = 1; return x; }\n" +
                      "function bar() { var y = 2; return y; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Function foo should be preserved", result.contains("foo"));
        assertTrue("Function bar should be preserved", result.contains("bar"));
        assertFalse("Variable x should be munged", result.contains("var x"));
        assertFalse("Variable y should be munged", result.contains("var y"));
    }

    @Test
    public void testNestedScopes() throws Exception {
        String input = "function outer() { var a = 1; function inner() { var b = 2; return a + b; } return inner(); }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Function outer should be preserved", result.contains("outer"));
        assertTrue("Function inner should be preserved", result.contains("inner"));
        assertTrue("Should contain return", result.contains("return"));
    }

    @Test
    public void testFunctionParameters() throws Exception {
        String input = "function add(param1, param2) { return param1 + param2; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Function name should be preserved", result.contains("add"));
        // Parameters should be munged to shorter names
        assertFalse("param1 should be munged", result.contains("param1"));
        assertFalse("param2 should be munged", result.contains("param2"));
    }

    @Test
    public void testPropertyAccessNotMunged() throws Exception {
        String input = "var obj = {}; obj.property = 42; return obj.property;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        // Property names should not be munged
        assertTrue("Property name should not be munged", result.contains("property"));
    }

    @Test
    public void testStringLiteralsPreserved() throws Exception {
        String input = "var message = \"Hello, World!\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("String literal should be preserved", result.contains("Hello, World!"));
    }

    @Test
    public void testNumberLiteralsPreserved() throws Exception {
        String input = "var pi = 3.14159; var big = 1000000;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Number literals should be preserved",
            result.contains("3.14159") && result.contains("1000000"));
    }

    @Test
    public void testComplexExpression() throws Exception {
        String input = "var result = (a + b) * (c - d) / e;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Should contain operators",
            result.contains("+") && result.contains("*") && result.contains("/"));
    }

    @Test
    public void testArrayLiterals() throws Exception {
        String input = "var arr = [1, 2, 3, 4, 5];";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Array brackets should be preserved", result.contains("[") && result.contains("]"));
    }

    @Test
    public void testObjectLiterals() throws Exception {
        String input = "var obj = { key1: 'value1', key2: 'value2' };";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Object braces should be preserved", result.contains("{") && result.contains("}"));
        assertTrue("Keys should be preserved", result.contains("key1") && result.contains("key2"));
    }

    @Test
    public void testFunctionCallsPreserved() throws Exception {
        String input = "console.log('test'); alert('message');";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Function calls should be preserved",
            result.contains("console") && result.contains("log") && result.contains("alert"));
    }

    @Test
    public void testBlockCommentsRemoved() throws Exception {
        String input = "/* Regular block comment */\nvar x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse("Regular block comment should be removed", result.contains("Regular block comment"));
        assertTrue("Code should remain", result.contains("var"));
    }

    @Test
    public void testMultipleVariableDeclarationsMunging() throws Exception {
        String input = "var longName1 = 1, longName2 = 2, longName3 = 3;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse("Long variable names should be munged",
            result.contains("longName1") || result.contains("longName2") || result.contains("longName3"));
    }

    @Test
    public void testEmptyFunction() throws Exception {
        String input = "function empty() {}";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("Function name should be preserved", result.contains("empty"));
        assertTrue("Function should be valid", result.contains("function") && result.contains("{}"));
    }

    @Test
    public void testLetAndConstVariables() throws Exception {
        String input = "let x = 1; const Y = 2;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, false, false, false, false);

        String result = output.toString();
        assertTrue("Let keyword should be preserved", result.contains("let"));
        assertTrue("Const keyword should be preserved", result.contains("const"));
    }

    @Test
    public void testImmediatelyInvokedFunctionExpression() throws Exception {
        String input = "(function() { var x = 1; return x; })();";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue("IIFE should be preserved", result.contains("function"));
        assertTrue("Should contain parentheses", result.contains("()"));
    }
}

