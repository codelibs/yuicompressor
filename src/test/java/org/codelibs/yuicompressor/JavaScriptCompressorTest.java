package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Test cases for JavaScript compression
 */
public class JavaScriptCompressorTest {

    private StringWriter output;

    @BeforeEach
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
        assertTrue(result.length() < input.length(), "Should remove whitespace");
        assertTrue(result.contains("var"), "Should contain var");
    }

    @Test
    public void testVariableObfuscation() throws Exception {
        String input = "function test() { var longVariableName = 123; return longVariableName; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse(result.contains("longVariableName"), "Variable should be obfuscated");
        assertTrue(result.contains("function test()"), "Function should remain");
    }

    @Test
    public void testNoMunge() throws Exception {
        String input = "function test() { var myVar = 123; return myVar; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, false, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("myVar"), "Variable should not be obfuscated with nomunge");
    }

    @Test
    public void testCommentRemoval() throws Exception {
        String input = "// This is a comment\nvar x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse(result.contains("This is a comment"), "Comments should be removed");
        assertTrue(result.contains("var"), "Code should remain");
    }

    @Test
    public void testFunctionExpression() throws Exception {
        String input = "var fn = function() { return 42; };";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("function"), "Should contain function");
        assertTrue(result.contains("return"), "Should contain return");
    }

    @Test
    public void testKeepCommentPreservation() throws Exception {
        String input = "/*! Important license comment */\nvar x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("Important license comment"), "Keep comment should be preserved");
        assertTrue(result.contains("var"), "Code should remain");
    }

    @Test
    public void testConditionalCommentPreservation() throws Exception {
        String input = "/*@cc_on var ie = true; @*/\nvar x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("@cc_on"), "Conditional comment should be preserved");
    }

    @Test
    public void testMultipleFunctionsWithMunging() throws Exception {
        String input = "function foo() { var x = 1; return x; }\n" +
                      "function bar() { var y = 2; return y; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("foo"), "Function foo should be preserved");
        assertTrue(result.contains("bar"), "Function bar should be preserved");
        assertFalse(result.contains("var x"), "Variable x should be munged");
        assertFalse(result.contains("var y"), "Variable y should be munged");
    }

    @Test
    public void testNestedScopes() throws Exception {
        String input = "function outer() { var a = 1; function inner() { var b = 2; return a + b; } return inner(); }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("outer"), "Function outer should be preserved");
        assertTrue(result.contains("inner"), "Function inner should be preserved");
        assertTrue(result.contains("return"), "Should contain return");
    }

    @Test
    public void testFunctionParameters() throws Exception {
        String input = "function add(param1, param2) { return param1 + param2; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("add"), "Function name should be preserved");
        // Parameters should be munged to shorter names
        assertFalse(result.contains("param1"), "param1 should be munged");
        assertFalse(result.contains("param2"), "param2 should be munged");
    }

    @Test
    public void testPropertyAccessNotMunged() throws Exception {
        String input = "var obj = {}; obj.property = 42; var result = obj.property;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        // Property names should not be munged
        assertTrue(result.contains("property"), "Property name should not be munged");
    }

    @Test
    public void testStringLiteralsPreserved() throws Exception {
        String input = "var message = \"Hello, World!\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("Hello, World!"), "String literal should be preserved");
    }

    @Test
    public void testNumberLiteralsPreserved() throws Exception {
        String input = "var pi = 3.14159; var big = 1000000;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("3.14159") && result.contains("1000000"),
            "Number literals should be preserved");
    }

    @Test
    public void testComplexExpression() throws Exception {
        String input = "var result = (a + b) * (c - d) / e;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("+") && result.contains("*") && result.contains("/"),
            "Should contain operators");
    }

    @Test
    public void testArrayLiterals() throws Exception {
        String input = "var arr = [1, 2, 3, 4, 5];";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("[") && result.contains("]"), "Array brackets should be preserved");
    }

    @Test
    public void testObjectLiterals() throws Exception {
        String input = "var obj = { key1: 'value1', key2: 'value2' };";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("{") && result.contains("}"), "Object braces should be preserved");
        assertTrue(result.contains("key1") && result.contains("key2"), "Keys should be preserved");
    }

    @Test
    public void testFunctionCallsPreserved() throws Exception {
        String input = "console.log('test'); alert('message');";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("console") && result.contains("log") && result.contains("alert"),
            "Function calls should be preserved");
    }

    @Test
    public void testBlockCommentsRemoved() throws Exception {
        String input = "/* Regular block comment */\nvar x = 1;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse(result.contains("Regular block comment"), "Regular block comment should be removed");
        assertTrue(result.contains("var"), "Code should remain");
    }

    @Test
    public void testMultipleVariableDeclarationsMunging() throws Exception {
        // Wrap in function since global variables are not munged for safety
        String input = "function test() { var longName1 = 1, longName2 = 2, longName3 = 3; return longName1 + longName2 + longName3; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse(result.contains("longName1") || result.contains("longName2") || result.contains("longName3"),
            "Long variable names should be munged");
    }

    @Test
    public void testEmptyFunction() throws Exception {
        String input = "function empty() {}";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("empty"), "Function name should be preserved");
        assertTrue(result.contains("function") && result.contains("{}"), "Function should be valid");
    }

    @Test
    public void testLetAndConstVariables() throws Exception {
        String input = "let x = 1; const Y = 2;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, false, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("let"), "Let keyword should be preserved");
        assertTrue(result.contains("const"), "Const keyword should be preserved");
    }

    @Test
    public void testImmediatelyInvokedFunctionExpression() throws Exception {
        String input = "(function() { var x = 1; return x; })();";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("function"), "IIFE should be preserved");
        assertTrue(result.contains("()"), "Should contain parentheses");
    }

    // Tests for string literal protection during whitespace compression

    @Test
    public void testStringWithCommaSpace() throws Exception {
        String input = "var s = \"a, b, c\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("a, b, c"), "String with comma-space should be preserved");
    }

    @Test
    public void testStringWithSemicolonSpace() throws Exception {
        String input = "var s = \"statement; another\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("statement; another"), "String with semicolon-space should be preserved");
    }

    @Test
    public void testStringWithBracesAndSpaces() throws Exception {
        String input = "var s = \"{ key: value }\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("{ key: value }"), "String with braces and spaces should be preserved");
    }

    @Test
    public void testStringWithParenthesesAndSpaces() throws Exception {
        String input = "var s = \"call( arg )\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("call( arg )"), "String with parentheses and spaces should be preserved");
    }

    @Test
    public void testMultipleStringsInStatement() throws Exception {
        String input = "var a = \"Hello, World!\", b = \"foo; bar\", c = \"( test )\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("Hello, World!"), "First string should be preserved");
        assertTrue(result.contains("foo; bar"), "Second string should be preserved");
        assertTrue(result.contains("( test )"), "Third string should be preserved");
    }

    @Test
    public void testStringWithEscapedQuotes() throws Exception {
        String input = "var s = \"He said \\\"Hello, World!\\\"\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("He said \\\"Hello, World!\\\""),
            "String with escaped quotes should be preserved");
    }

    @Test
    public void testStringWithEscapedBackslash() throws Exception {
        String input = "var s = \"path\\\\to\\\\file\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("path\\\\to\\\\file"),
            "String with escaped backslashes should be preserved");
    }

    @Test
    public void testSingleQuotedString() throws Exception {
        String input = "var s = 'Hello, World!';";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("Hello, World!"), "Single-quoted string should be preserved");
    }

    @Test
    public void testMixedQuoteStrings() throws Exception {
        String input = "var a = \"double, quotes\", b = 'single; quotes';";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("double, quotes"), "Double-quoted string should be preserved");
        assertTrue(result.contains("single; quotes"), "Single-quoted string should be preserved");
    }

    @Test
    public void testStringWithAllCompressionPatterns() throws Exception {
        String input = "var s = \"a, b; c{ d }( e )\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("a, b; c{ d }( e )"),
            "String with all compression patterns should be preserved");
    }

    @Test
    public void testAdjacentStrings() throws Exception {
        String input = "var s = \"first, \" + \"second; \" + \"third{ }\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("first, "), "First string should be preserved");
        assertTrue(result.contains("second; "), "Second string should be preserved");
        assertTrue(result.contains("third{ }"), "Third string should be preserved");
    }

    @Test
    public void testStringInFunctionCall() throws Exception {
        String input = "console.log(\"Hello, World!\");";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("Hello, World!"), "String in function call should be preserved");
    }

    @Test
    public void testStringWithNewlineEscape() throws Exception {
        String input = "var s = \"line1\\nline2\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("line1\\nline2"), "String with newline escape should be preserved");
    }

    @Test
    public void testStringWithTabEscape() throws Exception {
        String input = "var s = \"col1\\tcol2\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("col1\\tcol2"), "String with tab escape should be preserved");
    }

    @Test
    public void testEmptyString() throws Exception {
        String input = "var s = \"\";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("\"\"") || result.contains("''"), "Empty string should be preserved");
    }

    @Test
    public void testStringWithOnlySpaces() throws Exception {
        String input = "var s = \"   \";";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("\"") && result.length() > 10,
            "String with only spaces should preserve at least some spaces");
    }

    // Tests for control flow structures and operators

    @Test
    public void testIfElseStatement() throws Exception {
        String input = "function test(x) { if (x > 0) { return true; } else { return false; } }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("function"), "Should contain function keyword");
        assertTrue(result.contains("if"), "Should contain if keyword");
        assertTrue(result.contains("else"), "Should contain else keyword");
        assertTrue(result.contains("return"), "Should contain return keyword");
    }

    @Test
    public void testForLoop() throws Exception {
        String input = "for (var i = 0; i < 10; i++) { sum += i; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("for"), "Should contain for keyword");
        assertFalse(result.isEmpty(), "Should not contain syntax errors");
    }

    @Test
    public void testWhileLoop() throws Exception {
        String input = "var i = 0; while (i < 10) { i++; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("while"), "Should contain while keyword");
        assertFalse(result.isEmpty(), "Should not be empty");
    }

    @Test
    public void testTryCatchFinally() throws Exception {
        String input = "try { riskyOp(); } catch (e) { handleError(e); } finally { cleanup(); }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("try"), "Should contain try keyword");
        assertTrue(result.contains("catch"), "Should contain catch keyword");
        assertTrue(result.contains("finally"), "Should contain finally keyword");
    }

    @Test
    public void testSwitchCase() throws Exception {
        String input = "function test(x) { switch (x) { case 1: return 'one'; case 2: return 'two'; default: return 'other'; } }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("switch"), "Should contain switch keyword");
        assertTrue(result.contains("case"), "Should contain case keyword");
        assertTrue(result.contains("default"), "Should contain default keyword");
    }

    @Test
    public void testRegexLiteral() throws Exception {
        String input = "var pattern = /[a-z]+/gi; var result = pattern.test('hello');";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("/[a-z]+/gi") || result.contains("RegExp"), "Should contain regex pattern");
        assertTrue(result.contains("test"), "Should contain test method call");
    }

    @Test
    public void testBooleanLiterals() throws Exception {
        String input = "var t = true; var f = false; var n = null;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("true"), "Should contain true");
        assertTrue(result.contains("false"), "Should contain false");
        assertTrue(result.contains("null"), "Should contain null");
    }

    @Test
    public void testTernaryOperator() throws Exception {
        String input = "var result = x > 0 ? 'positive' : 'negative';";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("?") && result.contains(":"), "Should contain ternary operator");
        assertTrue(result.contains("positive"), "Should contain positive");
        assertTrue(result.contains("negative"), "Should contain negative");
    }

    @Test
    public void testComparisonOperators() throws Exception {
        String input = "var a = x == y; var b = x != y; var c = x < y; var d = x > y; var e = x <= y; var f = x >= y;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("==") || result.contains("!=") || result.contains("<") || result.contains(">"),
            "Should contain comparison operators");
    }

    @Test
    public void testLogicalOperators() throws Exception {
        String input = "var result = a && b || c; var negated = !d;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("&&") || result.contains("||") || result.contains("!"),
            "Should contain logical operators");
    }

    @Test
    public void testNewOperator() throws Exception {
        String input = "var obj = new Object(); var arr = new Array(10);";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("new"), "Should contain new keyword");
        assertTrue(result.contains("Object"), "Should contain Object");
        assertTrue(result.contains("Array"), "Should contain Array");
    }

    @Test
    public void testThisKeyword() throws Exception {
        String input = "function MyClass() { this.value = 42; this.getValue = function() { return this.value; }; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("this"), "Should contain this keyword");
        assertTrue(result.contains("value"), "Should contain value property");
    }

    @Test
    public void testIncrementDecrement() throws Exception {
        String input = "var x = 0; x++; ++x; x--; --x;";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("++") || result.contains("--"), "Should contain increment/decrement");
    }

    @Test
    public void testComplexNestedStructure() throws Exception {
        String input = "function processData(data) { " +
                      "for (var i = 0; i < data.length; i++) { " +
                      "if (data[i].valid) { " +
                      "try { " +
                      "var result = data[i].value > 0 ? 'positive' : 'negative'; " +
                      "results.push(result); " +
                      "} catch (e) { " +
                      "console.error(e); " +
                      "} } } }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertTrue(result.contains("function"), "Should contain function keyword");
        assertTrue(result.contains("for"), "Should contain for keyword");
        assertTrue(result.contains("if"), "Should contain if keyword");
        assertTrue(result.contains("try"), "Should contain try keyword");
        assertTrue(result.contains("catch"), "Should contain catch keyword");
        assertFalse(result.isEmpty(), "Should not be empty");
    }

    // ScopeBuilder's generic child traversal walks Rhino's low-level Node
    // chain (getFirstChild()/getNext()), which is only populated for
    // list-style containers like Block. AST nodes that keep their children in
    // typed fields (FunctionCall arguments, ObjectLiteral property values,
    // ArrayLiteral elements, ...) are invisible to it, so a function
    // expression sitting in one of those positions never gets a scope and its
    // parameters are never munged.

    @Test
    public void testFunctionExpressionAsCallArgumentIsMunged() throws Exception {
        String input = "p.then(function(longParamName){ return longParamName; });";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse(result.contains("longParamName"),
            "Parameter of a function expression passed as a call argument should be munged");
    }

    @Test
    public void testFunctionExpressionAsObjectPropertyValueIsMunged() throws Exception {
        String input = "var o = { m: function(longParamName){ return longParamName; } };";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse(result.contains("longParamName"),
            "Parameter of a function expression used as an object literal property value should be munged");
    }

    @Test
    public void testFunctionExpressionAsArrayElementIsMunged() throws Exception {
        String input = "var arr = [ function(longParamName){ return longParamName; } ];";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse(result.contains("longParamName"),
            "Parameter of a function expression used as an array literal element should be munged");
    }

    @Test
    public void testVariableDeclaredInsideIfBlockIsMunged() throws Exception {
        // ScopeBuilder's traversal gap is not limited to function expressions:
        // IfStatement, WhileLoop, and most other single/fixed-arity-child AST
        // nodes also store their children in typed fields only, so a `var`
        // declared inside an if-block was never discovered either.
        String input = "function outer(x) { if (x) { var innerVariableName = 1; } return innerVariableName; }";

        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);

        String result = output.toString();
        assertFalse(result.contains("innerVariableName"),
            "Variable declared inside an if-block should be munged");
    }
}

