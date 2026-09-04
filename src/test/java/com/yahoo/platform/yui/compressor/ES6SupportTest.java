/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

/**
 * Test cases for ES6+ syntax support
 */
public class ES6SupportTest {

    private String compress(String source) throws Exception {
        return compress(source, true);
    }

    private String compress(String source, boolean munge) throws Exception {
        StringReader reader = new StringReader(source);
        JavaScriptCompressor compressor = new JavaScriptCompressor(reader, null);
        StringWriter writer = new StringWriter();
        compressor.compress(writer, -1, munge, false, false, false);
        return writer.toString();
    }

    private AstRoot parseSource(String source) throws Exception {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setRecordingComments(false);
        env.setLanguageVersion(Context.VERSION_ES6);
        Parser parser = new Parser(env);
        return parser.parse(new StringReader(source), null, 1);
    }

    // ===== Arrow Function Tests =====

    @Test
    public void testArrowFunctionSingleParam() throws Exception {
        String source = "const f = x => x * 2;";
        String result = compress(source);
        assertTrue(result.contains("=>"), "Should contain arrow syntax");
    }

    @Test
    public void testArrowFunctionMultipleParams() throws Exception {
        String source = "const add = (a, b) => a + b;";
        String result = compress(source);
        assertTrue(result.contains("=>"), "Should contain arrow syntax");
    }

    @Test
    public void testArrowFunctionWithBlock() throws Exception {
        String source = "const f = x => { return x * 2; };";
        String result = compress(source);
        assertTrue(result.contains("=>"), "Should contain arrow syntax");
        assertTrue(result.contains("{"), "Should contain block braces");
    }

    @Test
    public void testArrowFunctionMunging() throws Exception {
        String source = "const f = (longParamName) => longParamName * 2;";
        String result = compress(source, true);
        assertFalse(result.contains("longParamName"), "Parameter should be munged");
    }

    // ===== Template Literal Tests =====

    @Test
    public void testTemplateLiteralSimple() throws Exception {
        String source = "const s = `hello world`;";
        String result = compress(source);
        assertTrue(result.contains("`"), "Should contain template literal");
    }

    @Test
    public void testTemplateLiteralWithInterpolation() throws Exception {
        String source = "const name = \"World\"; const s = `Hello ${name}!`;";
        String result = compress(source);
        assertTrue(result.contains("${"), "Should contain template interpolation");
    }

    // ===== Let and Const Tests =====

    @Test
    public void testLetDeclaration() throws Exception {
        String source = "let x = 1; let y = 2;";
        String result = compress(source);
        assertTrue(result.contains("let "), "Should preserve let keyword");
    }

    @Test
    public void testConstDeclaration() throws Exception {
        String source = "const x = 1; const y = 2;";
        String result = compress(source);
        assertTrue(result.contains("const "), "Should preserve const keyword");
    }

    // ===== Control Flow Tests =====

    @Test
    public void testIfStatement() throws Exception {
        String source = "if (x > 0) { console.log(x); }";
        String result = compress(source);
        assertTrue(result.contains("if("), "Should contain if statement");
    }

    @Test
    public void testIfElseStatement() throws Exception {
        String source = "if (x > 0) { console.log('positive'); } else { console.log('non-positive'); }";
        String result = compress(source);
        assertTrue(result.contains("if("), "Should contain if");
        assertTrue(result.contains("else"), "Should contain else");
    }

    @Test
    public void testForLoop() throws Exception {
        String source = "for (let i = 0; i < 10; i++) { console.log(i); }";
        String result = compress(source);
        assertTrue(result.contains("for("), "Should contain for loop");
    }

    @Test
    public void testWhileLoop() throws Exception {
        String source = "while (x > 0) { x--; }";
        String result = compress(source);
        assertTrue(result.contains("while("), "Should contain while loop");
    }

    @Test
    public void testDoWhileLoop() throws Exception {
        String source = "do { x++; } while (x < 10);";
        String result = compress(source);
        assertTrue(result.contains("do"), "Should contain do");
        assertTrue(result.contains("while("), "Should contain while");
    }

    @Test
    public void testSwitchStatement() throws Exception {
        String source = "switch(x) { case 1: break; case 2: break; default: break; }";
        String result = compress(source);
        assertTrue(result.contains("switch("), "Should contain switch");
        assertTrue(result.contains("case "), "Should contain case");
        assertTrue(result.contains("default:"), "Should contain default");
    }

    @Test
    public void testTryCatchFinally() throws Exception {
        String source = "try { foo(); } catch(e) { console.log(e); } finally { cleanup(); }";
        String result = compress(source);
        assertTrue(result.contains("try"), "Should contain try");
        assertTrue(result.contains("catch("), "Should contain catch");
        assertTrue(result.contains("finally"), "Should contain finally");
    }

    // ===== Operator Tests =====

    @Test
    public void testComparisonOperators() throws Exception {
        String source = "const a = x == y; const b = x === y; const c = x != y; const d = x !== y;";
        String result = compress(source);
        assertTrue(result.contains("=="), "Should contain ==");
        assertTrue(result.contains("==="), "Should contain ===");
        assertTrue(result.contains("!="), "Should contain !=");
        assertTrue(result.contains("!=="), "Should contain !==");
    }

    @Test
    public void testLogicalOperators() throws Exception {
        String source = "const a = x && y; const b = x || y; const c = !x;";
        String result = compress(source);
        assertTrue(result.contains("&&"), "Should contain &&");
        assertTrue(result.contains("||"), "Should contain ||");
        assertTrue(result.contains("!"), "Should contain !");
    }

    @Test
    public void testBitwiseOperators() throws Exception {
        String source = "const a = x & y; const b = x | y; const c = x ^ y; const d = ~x;";
        String result = compress(source);
        assertTrue(result.contains("&"), "Should contain &");
        assertTrue(result.contains("|"), "Should contain |");
        assertTrue(result.contains("^"), "Should contain ^");
        assertTrue(result.contains("~"), "Should contain ~");
    }

    @Test
    public void testTernaryOperator() throws Exception {
        String source = "const r = x > 0 ? 'positive' : 'non-positive';";
        String result = compress(source);
        assertTrue(result.contains("?"), "Should contain ?");
        assertTrue(result.contains(":"), "Should contain :");
    }

    @Test
    public void testIncrementDecrement() throws Exception {
        String source = "x++; ++y; x--; --y;";
        String result = compress(source);
        assertTrue(result.contains("++"), "Should contain ++");
        assertTrue(result.contains("--"), "Should contain --");
    }

    // ===== Object and Array Tests =====

    @Test
    public void testObjectLiteral() throws Exception {
        String source = "const obj = { a: 1, b: 2 };";
        String result = compress(source);
        assertTrue(result.contains("{"), "Should contain object braces");
        assertTrue(result.contains("a:"), "Should contain property");
    }

    @Test
    public void testArrayLiteral() throws Exception {
        String source = "const arr = [1, 2, 3];";
        String result = compress(source);
        assertTrue(result.contains("["), "Should contain array brackets");
    }

    @Test
    public void testElementAccess() throws Exception {
        String source = "const x = arr[0];";
        String result = compress(source);
        assertTrue(result.contains("[0]"), "Should contain element access");
    }

    // ===== Function Tests =====

    @Test
    public void testFunctionDeclaration() throws Exception {
        String source = "function foo(a, b) { return a + b; }";
        String result = compress(source);
        assertTrue(result.contains("function"), "Should contain function");
        assertTrue(result.contains("foo"), "Should contain function name");
    }

    @Test
    public void testFunctionExpression() throws Exception {
        String source = "const foo = function(a, b) { return a + b; };";
        String result = compress(source);
        assertTrue(result.contains("function"), "Should contain function");
    }

    @Test
    public void testNewExpression() throws Exception {
        String source = "const d = new Date();";
        String result = compress(source);
        assertTrue(result.contains("new "), "Should contain new");
        assertTrue(result.contains("Date"), "Should contain Date");
    }

    // ===== Keyword Tests =====

    @Test
    public void testThisKeyword() throws Exception {
        String source = "const obj = { name: 'test', getName: function() { return this.name; } };";
        String result = compress(source);
        assertTrue(result.contains("this"), "Should contain this");
    }

    @Test
    public void testTypeofOperator() throws Exception {
        String source = "const t = typeof x;";
        String result = compress(source);
        assertTrue(result.contains("typeof "), "Should contain typeof");
    }

    @Test
    public void testInstanceofOperator() throws Exception {
        String source = "const is = x instanceof Date;";
        String result = compress(source);
        assertTrue(result.contains(" instanceof "), "Should contain instanceof");
    }

    @Test
    public void testInOperator() throws Exception {
        String source = "const has = 'a' in obj;";
        String result = compress(source);
        assertTrue(result.contains(" in "), "Should contain in");
    }

    // ===== Regular Expression Tests =====

    @Test
    public void testRegExpLiteral() throws Exception {
        String source = "const re = /test/gi;";
        String result = compress(source);
        assertTrue(result.contains("/test/"), "Should contain regex");
        assertTrue(result.contains("gi"), "Should contain flags");
    }

    // ===== Scope and Munging Tests =====

    @Test
    public void testVariableMunging() throws Exception {
        String source = "function test() { var longVariableName = 1; return longVariableName; }";
        String result = compress(source, true);
        assertFalse(result.contains("longVariableName"), "Variable should be munged");
    }

    @Test
    public void testPropertyNotMunged() throws Exception {
        String source = "const obj = { propertyName: 1 }; console.log(obj.propertyName);";
        String result = compress(source, true);
        assertTrue(result.contains("propertyName"), "Property should NOT be munged");
    }

    @Test
    public void testGlobalNotMunged() throws Exception {
        String source = "console.log('test'); window.alert('hello');";
        String result = compress(source, true);
        assertTrue(result.contains("console"), "console should NOT be munged");
        assertTrue(result.contains("window"), "window should NOT be munged");
    }

    // ===== AST Parsing Tests =====

    @Test
    public void testArrowFunctionSurvivesCompression() throws Exception {
        assertEquals("const f=a=>{return a*2;};", compress("const f = x => x * 2;", true).trim());
    }

    @Test
    public void testTemplateLiteralSurvivesCompression() throws Exception {
        assertEquals("const s=`hello ${name}`;", compress("const s = `hello ${name}`;", false).trim());
    }

    @Disabled("Rhino 1.8.0 does not support ES6 class syntax")
    @Test
    public void testParseClass() throws Exception {
        String source = "class Foo { constructor(x) { this.x = x; } getX() { return this.x; } }";
        AstRoot ast = parseSource(source);
        assertNotNull(ast, "Should parse class");
    }

    @Test
    public void testForOfSurvivesCompression() throws Exception {
        // Note: Rhino 1.8.0 does not support 'const' in for-of loops, using 'let' instead
        assertEquals("for(let x of arr){console.log(x);}",
                compress("for (let x of arr) { console.log(x); }", false).trim());
    }

    @Test
    public void testDestructuringSurvivesCompression() throws Exception {
        assertEquals("const [a,b]=arr;const {x,y}=obj;",
                compress("const [a, b] = arr; const {x, y} = obj;", false).trim());
    }

    @Disabled("Rhino 1.8.0 does not support spread operator in arrays")
    @Test
    public void testParseSpread() throws Exception {
        String source = "const arr2 = [...arr1, 4, 5];";
        AstRoot ast = parseSource(source);
        assertNotNull(ast, "Should parse spread operator");
    }

    // These two used to call parseSource() and assert assertNotNull(ast) -
    // they never invoked the compressor, but sat in a class named
    // ES6SupportTest among tests that do, so the file read as evidence that
    // default and rest parameters were supported. They were not: the
    // generator dropped "= 1" and "..." silently, changing what the function
    // did. Now that it reconstructs them, these can assert what their names
    // imply. See ParameterListTest for the full round-trip table.

    @Test
    public void testDefaultParamsSurviveCompression() throws Exception {
        String result = compress("function foo(x = 1, y = 2) { return x + y; }", false);
        assertEquals("function foo(x=1,y=2){return x+y;}", result.trim(),
                "dropping the defaults changes foo() from 3 to NaN: " + result);
    }

    @Test
    public void testRestParamsSurviveCompression() throws Exception {
        String result = compress("function foo(...args) { return args.length; }", false);
        assertEquals("function foo(...args){return args.length;}", result.trim(),
                "dropping the '...' turns an array of the trailing arguments into a "
                        + "single positional parameter: " + result);
    }

    // ===== Getter/Setter Tests =====

    @Test
    public void testObjectGetterMethod() throws Exception {
        String source = "var obj = { get value() { return this._value; } };";
        String result = compress(source);
        assertTrue(result.contains("get "), "Should contain 'get' keyword");
        assertTrue(result.contains("value()"), "Should contain getter method");
    }

    @Test
    public void testObjectSetterMethod() throws Exception {
        String source = "var obj = { set value(v) { this._value = v; } };";
        String result = compress(source);
        assertTrue(result.contains("set "), "Should contain 'set' keyword");
        assertTrue(result.contains("value("), "Should contain setter method");
    }

    @Test
    public void testObjectGetterSetterCombined() throws Exception {
        String source = "var obj = { get x() { return this._x; }, set x(v) { this._x = v; } };";
        String result = compress(source);
        assertTrue(result.contains("get "), "Should contain 'get' keyword");
        assertTrue(result.contains("set "), "Should contain 'set' keyword");
    }

    // ===== For-in Loop Tests =====

    @Test
    public void testForInLoop() throws Exception {
        String source = "for (var key in obj) { console.log(key); }";
        String result = compress(source);
        assertTrue(result.contains("for("), "Should contain 'for' keyword");
        assertTrue(result.contains(" in "), "Should contain 'in' keyword");
    }

    @Test
    public void testForInLoopWithLet() throws Exception {
        String source = "for (let key in obj) { console.log(key); }";
        String result = compress(source);
        assertTrue(result.contains("for("), "Should contain 'for' keyword");
        assertTrue(result.contains(" in "), "Should contain 'in' keyword");
    }

    // ===== For-of Loop Tests =====

    @Test
    public void testForOfLoopWithVar() throws Exception {
        String source = "for (var item of arr) { console.log(item); }";
        String result = compress(source);
        assertTrue(result.contains("for("), "Should contain 'for' keyword");
        assertTrue(result.contains(" of "), "Should contain 'of' keyword");
    }

    @Test
    public void testForOfLoopWithLet() throws Exception {
        String source = "for (let item of arr) { console.log(item); }";
        String result = compress(source);
        assertTrue(result.contains("for("), "Should contain 'for' keyword");
        assertTrue(result.contains(" of "), "Should contain 'of' keyword");
    }

    @Test
    public void testForOfLoopMunging() throws Exception {
        // Note: Global scope variables are not munged (could be referenced externally)
        // Wrap in a function to test munging behavior
        String source = "function test() { for (let longItemName of arr) { console.log(longItemName); } }";
        String result = compress(source, true);
        assertFalse(result.contains("longItemName"), "Loop variable should be munged inside function");
    }

    // ===== Scope Block Tests =====

    @Test
    public void testBlockScopeWithLet() throws Exception {
        String source = "{ let blockVar = 1; console.log(blockVar); }";
        String result = compress(source);
        assertTrue(result.contains("{"), "Should contain block braces");
        assertTrue(result.contains("let "), "Should contain 'let' keyword");
    }

    @Test
    public void testBlockScopeWithConst() throws Exception {
        String source = "{ const BLOCK_CONST = 42; console.log(BLOCK_CONST); }";
        String result = compress(source);
        assertTrue(result.contains("{"), "Should contain block braces");
        assertTrue(result.contains("const "), "Should contain 'const' keyword");
    }

    @Test
    public void testNestedBlockScopes() throws Exception {
        String source = "{ let outer = 1; { let inner = 2; console.log(inner); } console.log(outer); }";
        String result = compress(source);
        assertNotNull(result, "Should compress nested blocks");
        assertTrue(result.contains("let "), "Should contain 'let' keyword");
    }

    // ===== Array Destructuring with Empty Elements =====

    @Test
    public void testArrayDestructuringWithEmptyElement() throws Exception {
        // Test: const [a, , b] = arr; (middle element is empty). The hole must
        // survive: dropping it would shift "third" onto the second slot.
        String result = compress("const [first, , third] = [1, 2, 3];", false);
        assertEquals("const [first,,third]=[1,2,3];", result.trim(), result);
    }

    @Test
    public void testFunctionParameterDestructuringWithEmptyElement() throws Exception {
        // Test: function([a, , b]) where middle element is skipped. Both
        // bindings munge; the hole between them keeps its position.
        String result = compress("function test([first, , third]) { return first + third; }");
        assertEquals("function test([b,,a]){return b+a;}", result.trim(), result);
    }

    // ===== Combined ES6 Features =====

    @Test
    public void testForOfWithDestructuring() throws Exception {
        String source = "for (let [key, value] of entries) { console.log(key, value); }";
        String result = compress(source);
        assertTrue(result.contains("for("), "Should contain 'for' keyword");
        assertTrue(result.contains(" of "), "Should contain 'of' keyword");
    }

    @Test
    public void testArrowFunctionInForOf() throws Exception {
        String source = "for (let x of arr) { const fn = y => y * x; console.log(fn(2)); }";
        String result = compress(source);
        assertTrue(result.contains("=>"), "Should contain arrow syntax");
        assertTrue(result.contains(" of "), "Should contain 'of' keyword");
    }
}
